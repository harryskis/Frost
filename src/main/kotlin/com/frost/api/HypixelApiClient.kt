package com.frost.api

import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Minimal Hypixel public API client used only to snapshot a player's lifetime
 * `secrets_found` count at the start and end of a run (the diff is the run's secrets).
 * The Hypixel API has no live in-progress dungeon data, so this is the only piece of
 * this mod that talks to it. Requires a personal API key from developer.hypixel.net,
 * stored via [com.frost.config.ModConfig] — never hardcode or commit one.
 */
object HypixelApiClient {
    private val logger = LoggerFactory.getLogger("frost.api")
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    sealed class Result {
        data class Success(val secretsFound: Int) : Result()
        data class Error(val message: String) : Result()
    }

    fun fetchSecretsFoundAsync(uuid: UUID, apiKey: String): CompletableFuture<Result> {
        val undashed = uuid.toString().replace("-", "")
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.hypixel.net/v2/skyblock/profiles?uuid=$undashed"))
            .header("API-Key", apiKey)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response -> parseSecretsFound(response.statusCode(), response.body(), undashed) }
            .exceptionally { e -> Result.Error(e.message ?: "request failed") }
    }

    internal fun parseSecretsFound(statusCode: Int, body: String, undashedUuid: String): Result {
        if (statusCode != 200) return Result.Error("HTTP $statusCode")
        val json = try {
            JsonParser.parseString(body).asJsonObject
        } catch (e: Exception) {
            return Result.Error("invalid response body")
        }

        val success = json.get("success")?.asBoolean ?: false
        if (!success) {
            return Result.Error(json.get("cause")?.asString ?: "request unsuccessful")
        }

        val profiles = json.getAsJsonArray("profiles")
        if (profiles == null || profiles.size() == 0) return Result.Error("no profiles found")

        val selected = profiles.firstOrNull { it.asJsonObject.get("selected")?.asBoolean == true }?.asJsonObject
            ?: return Result.Error("no selected profile")

        val member = selected.getAsJsonObject("members")?.getAsJsonObject(undashedUuid)
            ?: return Result.Error("player not found in selected profile (private API access?)")

        // "last_dungeon_run" might be a direct per-run snapshot rather than the lifetime
        // counter we have to diff (which has shown timing/staleness issues) - log its
        // structure so we can tell if it's a better source for secrets-this-run.
        member.getAsJsonObject("dungeons")?.get("last_dungeon_run")?.let {
            logger.info("dungeons.last_dungeon_run for $undashedUuid: $it")
        }

        // Confirmed against a live API response: the field is "dungeons.secrets", a plain
        // number - not "secrets_found" as originally guessed.
        val dungeons = member.getAsJsonObject("dungeons")
        val secretsField = dungeons?.get("secrets")
        if (secretsField == null || !secretsField.isJsonPrimitive) {
            logger.warn(
                "Unexpected 'secrets' field at dungeons.secrets: $secretsField. " +
                    "dungeons keys: ${dungeons?.keySet() ?: "dungeons object itself is missing"}",
            )
            return Result.Success(0)
        }
        return Result.Success(secretsField.asInt)
    }

    /** Fallback UUID lookup for a username not currently visible in the client's tab list. */
    fun resolveUuidAsync(username: String): CompletableFuture<UUID?> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/$username"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() != 200) return@thenApply null
                val json = JsonParser.parseString(response.body()).asJsonObject
                val idRaw = json.get("id")?.asString ?: return@thenApply null
                undashedToUuid(idRaw)
            }
            .exceptionally { null }
    }

    private fun undashedToUuid(raw: String): UUID? {
        if (raw.length != 32) return null
        val dashed = buildString {
            append(raw, 0, 8).append('-')
            append(raw, 8, 12).append('-')
            append(raw, 12, 16).append('-')
            append(raw, 16, 20).append('-')
            append(raw, 20, 32)
        }
        return try {
            UUID.fromString(dashed)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
