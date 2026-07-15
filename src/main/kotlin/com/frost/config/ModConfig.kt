package com.frost.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

data class ModConfigData(
    var hypixelApiKey: String? = null,
    // Per-room difficulty weight, keyed by the room's real name (e.g. "Crypt", "Ice
    // Fill") - a room absent from this map hasn't been customized yet and defaults to 1.0
    // (see ModConfig.getRoomWeight), not 0, so an unconfigured room still counts normally.
    var roomWeights: MutableMap<String, Double> = mutableMapOf(),
    // The chat channel the weighted-score line gets actually SENT to (e.g. "/pc", "/ac"),
    // typed exactly as the player would type it themselves. Null/blank means "don't send
    // it to real chat" - the safe default until the player explicitly opts in, since
    // guessing a channel could broadcast into whatever chat is currently active.
    var chatAnnounceChannel: String? = null,
    // Whether the first-launch welcome/API-key-reminder onboarding flow has already been
    // shown - it should only ever appear once, the very first time the player joins a
    // server with Frost installed, not on every join.
    var hasSeenWelcome: Boolean = false,
)

/**
 * Loads/saves [ModConfigData] as JSON under the Fabric config directory. The API key is
 * a personal secret from developer.hypixel.net — it lives only in this local file
 * (already covered by .gitignore) and is never bundled, hardcoded, or transmitted
 * anywhere except in requests to api.hypixel.net.
 */
object ModConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path: Path = FabricLoader.getInstance().configDir.resolve("frost.json")

    // The mod used to be called "Dungeon Stats" - migrate a config saved under its old
    // filename once, so renaming the mod doesn't silently lose an already-saved API key.
    private val legacyPath: Path = FabricLoader.getInstance().configDir.resolve("dungeonstats.json")

    private var cached: ModConfigData? = null

    fun get(): ModConfigData {
        cached?.let { return it }
        val loaded = if (Files.exists(path)) {
            readFrom(path) ?: ModConfigData()
        } else if (Files.exists(legacyPath)) {
            (readFrom(legacyPath) ?: ModConfigData()).also { save(it) }
        } else {
            ModConfigData()
        }
        cached = loaded
        return loaded
    }

    private fun readFrom(file: Path): ModConfigData? = try {
        Files.newBufferedReader(file).use { gson.fromJson(it, ModConfigData::class.java) }
    } catch (e: Exception) {
        null
    }

    fun save(config: ModConfigData) {
        cached = config
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path).use { gson.toJson(config, it) }
    }

    fun setApiKey(key: String) {
        val config = get()
        config.hypixelApiKey = key
        save(config)
    }

    /** A room nobody's customized yet is worth exactly as much as any other (1.0), not 0 -
     * weights only ever make a room worth MORE or LESS relative to that neutral default. */
    fun getRoomWeight(roomName: String): Double = get().roomWeights[roomName] ?: 1.0

    /** Merges every entry in [weights] into the saved config with a single file write,
     * rather than one write per room - a whole category screen's worth of edits (up to 93
     * rooms for 1x1) shouldn't mean 93 disk writes on close. */
    fun setRoomWeights(weights: Map<String, Double>) {
        val config = get()
        config.roomWeights.putAll(weights)
        save(config)
    }

    fun setChatAnnounceChannel(channel: String) {
        val config = get()
        config.chatAnnounceChannel = channel
        save(config)
    }

    fun markWelcomeSeen() {
        val config = get()
        config.hasSeenWelcome = true
        save(config)
    }
}
