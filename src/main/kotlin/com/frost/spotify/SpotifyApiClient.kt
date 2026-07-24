package com.frost.spotify

import com.frost.config.ModConfig
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import org.slf4j.LoggerFactory

/**
 * Async client for the handful of Spotify Web API playback endpoints Frost needs
 * (next/previous/pause/play/currently-playing). Mirrors [com.frost.api.HypixelApiClient]'s
 * shape - a plain `java.net.http.HttpClient`, `CompletableFuture`-based, a sealed `Result`.
 * Access tokens live in memory only (never persisted) and are refreshed on demand from the
 * saved refresh token; a request that comes back 401 is retried exactly once after a
 * forced refresh, since Spotify access tokens expire hourly independent of actual use.
 */
object SpotifyApiClient {
    private val logger = LoggerFactory.getLogger("frost.spotify")
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    sealed class Result {
        data object Success : Result()
        data class NowPlaying(val trackName: String, val artistName: String, val isPlaying: Boolean) : Result()
        data class Error(val message: String) : Result()
    }

    @Volatile private var accessToken: String? = null
    @Volatile private var accessTokenExpiresAt: Instant? = null

    fun cacheAccessToken(token: String, expiresInSeconds: Long) {
        accessToken = token
        accessTokenExpiresAt = Instant.now().plusSeconds(expiresInSeconds)
    }

    fun isLoggedIn(): Boolean = !ModConfig.get().spotifyRefreshToken.isNullOrBlank()

    fun skipNext(): CompletableFuture<Result> = authorizedRequest(
        buildRequest = { token -> requestBuilder("https://api.spotify.com/v1/me/player/next", token).POST(HttpRequest.BodyPublishers.noBody()).build() },
        parse = ::parseActionResponse,
    )

    fun skipPrevious(): CompletableFuture<Result> = authorizedRequest(
        buildRequest = { token -> requestBuilder("https://api.spotify.com/v1/me/player/previous", token).POST(HttpRequest.BodyPublishers.noBody()).build() },
        parse = ::parseActionResponse,
    )

    fun pause(): CompletableFuture<Result> = authorizedRequest(
        buildRequest = { token -> requestBuilder("https://api.spotify.com/v1/me/player/pause", token).PUT(HttpRequest.BodyPublishers.noBody()).build() },
        parse = ::parseActionResponse,
    )

    fun play(): CompletableFuture<Result> = authorizedRequest(
        buildRequest = { token -> requestBuilder("https://api.spotify.com/v1/me/player/play", token).PUT(HttpRequest.BodyPublishers.noBody()).build() },
        parse = ::parseActionResponse,
    )

    fun currentlyPlaying(): CompletableFuture<Result> = authorizedRequest(
        buildRequest = { token -> requestBuilder("https://api.spotify.com/v1/me/player/currently-playing", token).GET().build() },
        parse = ::parseNowPlaying,
    )

    /** Frost holds no local play/pause state of its own (nothing polls playback state
     * continuously), so toggling means reading the current state right before acting
     * rather than trusting a possibly-stale cached flag. */
    fun togglePlayPause(): CompletableFuture<Result> =
        currentlyPlaying().thenCompose { result ->
            when (result) {
                is Result.NowPlaying -> if (result.isPlaying) pause() else play()
                else -> CompletableFuture.completedFuture(result)
            }
        }

    private fun requestBuilder(url: String, token: String): HttpRequest.Builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .timeout(Duration.ofSeconds(10))

    private fun authorizedRequest(
        buildRequest: (token: String) -> HttpRequest,
        parse: (statusCode: Int, body: String) -> Result,
    ): CompletableFuture<Result> {
        val notLoggedIn = Result.Error("Not connected to Spotify - run /frost spotify login first.")
        return ensureAccessToken(forceRefresh = false).thenCompose { token ->
            if (token == null) {
                CompletableFuture.completedFuture(notLoggedIn)
            } else {
                client.sendAsync(buildRequest(token), HttpResponse.BodyHandlers.ofString()).thenCompose { response ->
                    if (response.statusCode() == 401) {
                        ensureAccessToken(forceRefresh = true).thenCompose { retryToken ->
                            if (retryToken == null) {
                                CompletableFuture.completedFuture(notLoggedIn)
                            } else {
                                client.sendAsync(buildRequest(retryToken), HttpResponse.BodyHandlers.ofString())
                                    .thenApply { retryResponse -> parse(retryResponse.statusCode(), retryResponse.body()) }
                            }
                        }
                    } else {
                        CompletableFuture.completedFuture(parse(response.statusCode(), response.body()))
                    }
                }
            }
        }.exceptionally { e ->
            logger.warn("Spotify API request failed", e)
            Result.Error(e.message ?: "request failed")
        }
    }

    /** Returns a currently-valid access token, refreshing it first if it's missing, close
     * to expiry, or [forceRefresh] is set (used for the one-shot retry after a 401). Null
     * means no refresh token is saved yet, or the refresh itself failed. */
    private fun ensureAccessToken(forceRefresh: Boolean): CompletableFuture<String?> {
        val cachedToken = accessToken
        val expiresAt = accessTokenExpiresAt
        if (!forceRefresh && cachedToken != null && expiresAt != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
            return CompletableFuture.completedFuture(cachedToken)
        }

        val config = ModConfig.get()
        val refreshToken = config.spotifyRefreshToken
        val clientId = config.spotifyClientId
        if (refreshToken.isNullOrBlank() || clientId.isNullOrBlank()) {
            return CompletableFuture.completedFuture(null)
        }

        return SpotifyAuth.refreshAccessToken(refreshToken, clientId).thenApply { result ->
            when (result) {
                is SpotifyAuth.TokenResult.Success -> {
                    cacheAccessToken(result.accessToken, result.expiresInSeconds)
                    if (result.refreshToken != null) ModConfig.setSpotifyRefreshToken(result.refreshToken)
                    result.accessToken
                }
                is SpotifyAuth.TokenResult.Error -> {
                    logger.warn("Spotify token refresh failed: ${result.message}")
                    null
                }
            }
        }
    }

    internal fun parseActionResponse(statusCode: Int, body: String): Result = when (statusCode) {
        200, 202, 204 -> Result.Success
        404 -> Result.Error("No active Spotify device found - start playing on a device first.")
        403 -> Result.Error("Spotify Premium is required for playback control.")
        429 -> Result.Error("Rate limited by Spotify - try again in a moment.")
        else -> Result.Error(spotifyErrorMessage(statusCode, body))
    }

    internal fun parseNowPlaying(statusCode: Int, body: String): Result = when (statusCode) {
        204 -> Result.Error("Nothing is currently playing.")
        200 -> try {
            val json = JsonParser.parseString(body).asJsonObject
            val isPlaying = json.get("is_playing")?.asBoolean ?: false
            val item = json.getAsJsonObject("item")
            val trackName = item?.get("name")?.asString ?: "Unknown track"
            val artistName = item?.getAsJsonArray("artists")
                ?.firstOrNull()?.asJsonObject?.get("name")?.asString ?: "Unknown artist"
            Result.NowPlaying(trackName, artistName, isPlaying)
        } catch (e: Exception) {
            Result.Error("Could not parse now-playing response")
        }
        404 -> Result.Error("No active Spotify device found - start playing on a device first.")
        else -> Result.Error(spotifyErrorMessage(statusCode, body))
    }

    private fun spotifyErrorMessage(statusCode: Int, body: String): String = try {
        JsonParser.parseString(body).asJsonObject
            .getAsJsonObject("error")?.get("message")?.asString ?: "HTTP $statusCode"
    } catch (e: Exception) {
        "HTTP $statusCode"
    }
}
