package com.frost.spotify

import com.frost.config.ModConfig
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * Spotify's PKCE Authorization Code flow
 * (https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow) - no
 * client secret is ever stored or transmitted, only the app's public Client ID plus a
 * refresh token obtained from a one-time browser consent. Login only ever runs when the
 * player explicitly types `/frost spotify login`; nothing here fires automatically.
 */
object SpotifyAuth {
    private val logger = LoggerFactory.getLogger("frost.spotify")
    private const val REDIRECT_PORT = 39562
    const val REDIRECT_URI = "http://127.0.0.1:$REDIRECT_PORT/callback"
    private val SCOPES = listOf(
        "user-modify-playback-state",
        "user-read-playback-state",
        "user-read-currently-playing",
    ).joinToString(" ")

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    sealed class TokenResult {
        data class Success(val accessToken: String, val refreshToken: String?, val expiresInSeconds: Long) : TokenResult()
        data class Error(val message: String) : TokenResult()
    }

    private sealed class CallbackResult {
        data class Success(val code: String) : CallbackResult()
        data class Error(val message: String) : CallbackResult()
    }

    /** A fresh, URL-safe random verifier well within PKCE's required 43-128 char range. */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun generateState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun buildAuthorizeUrl(clientId: String, state: String, codeChallenge: String): String {
        fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8)
        return "https://accounts.spotify.com/authorize" +
            "?response_type=code" +
            "&client_id=${enc(clientId)}" +
            "&redirect_uri=${enc(REDIRECT_URI)}" +
            "&code_challenge_method=S256" +
            "&code_challenge=${enc(codeChallenge)}" +
            "&state=${enc(state)}" +
            "&scope=${enc(SCOPES)}"
    }

    /**
     * Runs the full login flow: opens the system browser to Spotify's consent page, waits
     * for the loopback redirect (up to 3 minutes), exchanges the resulting code for tokens,
     * and saves the refresh token to [ModConfig]. [onResult] fires exactly once with a
     * user-facing outcome message - on whatever thread the async chain happens to land on,
     * so callers that touch the game must hop back via `MinecraftClient.execute {}`
     * themselves (see Frost.kt's command handlers).
     */
    fun startLogin(clientId: String, onResult: (success: Boolean, message: String) -> Unit) {
        val verifier = generateCodeVerifier()
        val challenge = codeChallenge(verifier)
        val state = generateState()
        val authorizeUrl = buildAuthorizeUrl(clientId, state, challenge)

        val server = try {
            HttpServer.create(InetSocketAddress("127.0.0.1", REDIRECT_PORT), 0)
        } catch (e: Exception) {
            onResult(false, "Could not start local callback server on port $REDIRECT_PORT (already in use?): ${e.message}")
            return
        }

        val callbackFuture = CompletableFuture<CallbackResult>()
        server.createContext("/callback") { exchange ->
            try {
                val params = parseQuery(exchange.requestURI.rawQuery ?: "")
                val result = when {
                    params["error"] != null -> CallbackResult.Error("Spotify returned an error: ${params["error"]}")
                    params["state"] != state -> CallbackResult.Error("State mismatch on callback - ignoring (possible spoofed request).")
                    params["code"] == null -> CallbackResult.Error("No authorization code in callback.")
                    else -> CallbackResult.Success(params.getValue("code"))
                }
                val responseBody = "<html><body>Frost: you can close this tab and return to Minecraft.</body></html>"
                    .toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, responseBody.size.toLong())
                exchange.responseBody.use { it.write(responseBody) }
                exchange.close()
                // Completed only AFTER the response is fully written and the exchange
                // closed - whenComplete() below runs synchronously on THIS thread (nothing
                // else has claimed the future yet), and it calls server.stop(), which was
                // confirmed live to tear down the connection out from under an in-flight
                // response if fired any earlier: the browser reached /callback (a real
                // Spotify code in the query string) but got ERR_EMPTY_RESPONSE because
                // stop() ran while sendResponseHeaders/write were still in flight.
                callbackFuture.complete(result)
            } catch (e: Exception) {
                callbackFuture.completeExceptionally(e)
            }
        }
        server.start()

        try {
            Desktop.getDesktop().browse(URI.create(authorizeUrl))
        } catch (e: Exception) {
            server.stop(0)
            onResult(false, "Could not open your browser automatically - open this URL manually: $authorizeUrl")
            return
        }

        callbackFuture.orTimeout(3, TimeUnit.MINUTES).whenComplete { callbackResult, timeoutError ->
            server.stop(1)
            when {
                timeoutError != null -> onResult(false, "Spotify login timed out or failed: ${timeoutError.message}")
                callbackResult is CallbackResult.Error -> onResult(false, callbackResult.message)
                callbackResult is CallbackResult.Success -> {
                    exchangeCodeForToken(callbackResult.code, verifier, clientId).whenComplete { tokenResult, tokenError ->
                        when {
                            tokenError != null -> onResult(false, "Token exchange failed: ${tokenError.message}")
                            tokenResult is TokenResult.Success -> {
                                ModConfig.setSpotifyRefreshToken(tokenResult.refreshToken)
                                SpotifyApiClient.cacheAccessToken(tokenResult.accessToken, tokenResult.expiresInSeconds)
                                onResult(true, "Spotify connected.")
                            }
                            tokenResult is TokenResult.Error -> onResult(false, "Token exchange failed: ${tokenResult.message}")
                            else -> onResult(false, "Unexpected token exchange result.")
                        }
                    }
                }
                else -> onResult(false, "Unexpected login result.")
            }
        }
    }

    fun exchangeCodeForToken(code: String, verifier: String, clientId: String): CompletableFuture<TokenResult> =
        postToken(
            formEncode(
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to REDIRECT_URI,
                    "client_id" to clientId,
                    "code_verifier" to verifier,
                ),
            ),
        )

    fun refreshAccessToken(refreshToken: String, clientId: String): CompletableFuture<TokenResult> =
        postToken(
            formEncode(
                mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                    "client_id" to clientId,
                ),
            ),
        )

    private fun postToken(form: String): CompletableFuture<TokenResult> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://accounts.spotify.com/api/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response -> parseTokenResponse(response.statusCode(), response.body()) }
            .exceptionally { e ->
                logger.warn("Spotify token request failed", e)
                TokenResult.Error(e.message ?: "request failed")
            }
    }

    internal fun parseTokenResponse(statusCode: Int, body: String): TokenResult {
        val json = try {
            JsonParser.parseString(body).asJsonObject
        } catch (e: Exception) {
            return TokenResult.Error("invalid response body")
        }
        if (statusCode != 200) {
            val message = json.get("error_description")?.asString ?: json.get("error")?.asString ?: "HTTP $statusCode"
            return TokenResult.Error(message)
        }
        val accessToken = json.get("access_token")?.asString ?: return TokenResult.Error("missing access_token in response")
        val expiresIn = json.get("expires_in")?.asLong ?: 3600L
        // Spotify only sometimes rotates the refresh token on refresh calls - absent means
        // "keep using the one you already have", not "the session is now unauthenticated".
        val refreshToken = json.get("refresh_token")?.asString
        return TokenResult.Success(accessToken, refreshToken, expiresIn)
    }

    internal fun parseQuery(query: String): Map<String, String> =
        query.split("&").filter { it.isNotBlank() }.associate { pair ->
            val parts = pair.split("=", limit = 2)
            val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8)
            val value = if (parts.size > 1) URLDecoder.decode(parts[1], StandardCharsets.UTF_8) else ""
            key to value
        }

    private fun formEncode(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
        }
}
