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
 * Minimal Hypixel public API client: snapshotting a player's lifetime `secrets_found` count
 * at the start and end of a run (the diff is the run's secrets), plus on-demand lookups of a
 * player's dungeon personal-best time for /pb. The Hypixel API has no live in-progress
 * dungeon data, so this is the only piece of this mod that talks to it. Requires a personal
 * API key from developer.hypixel.net, stored via [com.frost.config.ModConfig] — never
 * hardcode or commit one.
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

    sealed class BestTimeResult {
        data class Success(val elapsedMillis: Long) : BestTimeResult()
        data class Error(val message: String) : BestTimeResult()
    }

    /** Fetches [uuid]'s personal-best time for one dungeon floor, for /pb. [dungeonType] is
     * "catacombs" or "master_catacombs", [floorIndex] is the floor's key within that
     * bucket's record maps ("1".."7"), and [recordField] is "fastest_time_s_plus" (default,
     * S+ score) or "fastest_time" (any completion). [floorLabel]/[recordLabel] are only used
     * to word the "no record yet" message. */
    fun fetchDungeonBestTimeAsync(
        uuid: UUID,
        apiKey: String,
        dungeonType: String,
        floorIndex: String,
        recordField: String,
        floorLabel: String,
        recordLabel: String,
    ): CompletableFuture<BestTimeResult> {
        val undashed = uuid.toString().replace("-", "")
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.hypixel.net/v2/skyblock/profiles?uuid=$undashed"))
            .header("API-Key", apiKey)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                parseDungeonBestTime(response.statusCode(), response.body(), undashed, dungeonType, floorIndex, recordField, floorLabel, recordLabel)
            }
            .exceptionally { e -> BestTimeResult.Error(e.message ?: "request failed") }
    }

    internal fun parseDungeonBestTime(
        statusCode: Int,
        body: String,
        undashedUuid: String,
        dungeonType: String,
        floorIndex: String,
        recordField: String,
        floorLabel: String,
        recordLabel: String,
    ): BestTimeResult {
        if (statusCode != 200) return BestTimeResult.Error("HTTP $statusCode")
        val json = try {
            JsonParser.parseString(body).asJsonObject
        } catch (e: Exception) {
            return BestTimeResult.Error("invalid response body")
        }

        val success = json.get("success")?.asBoolean ?: false
        if (!success) {
            return BestTimeResult.Error(json.get("cause")?.asString ?: "request unsuccessful")
        }

        val profiles = json.getAsJsonArray("profiles")
        if (profiles == null || profiles.size() == 0) return BestTimeResult.Error("no profiles found")

        val selected = profiles.firstOrNull { it.asJsonObject.get("selected")?.asBoolean == true }?.asJsonObject
            ?: return BestTimeResult.Error("no selected profile")

        val member = selected.getAsJsonObject("members")?.getAsJsonObject(undashedUuid)
            ?: return BestTimeResult.Error("player not found in selected profile (private API access?)")

        val record = member.getAsJsonObject("dungeons")
            ?.getAsJsonObject("dungeon_types")
            ?.getAsJsonObject(dungeonType)
            ?.getAsJsonObject(recordField)
            ?.get(floorIndex)

        if (record == null || !record.isJsonPrimitive) {
            return BestTimeResult.Error("no $recordLabel record for $floorLabel")
        }
        return BestTimeResult.Success(record.asLong)
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
