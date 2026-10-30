package com.frost.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

// The room-weight values a fresh install starts with, and what /frost roomweights reset
// restores - the developer's own tuned values (rooms not listed here are neutral at 1.0,
// same as any other unconfigured room - see ModConfig.getRoomWeight - so only entries that
// actually differ from that neutral default need to be listed).
val DEFAULT_ROOM_WEIGHTS: Map<String, Double> = mapOf(
    "Slime" to 3.0,
    "Altar" to 3.0,
    "Chambers" to 2.0,
    "Lava Ravine" to 2.0,
    "Layers" to 3.0,
    "Pirate" to 2.0,
    "Withermancer" to 2.0,
    "Bridges" to 2.0,
    "Crypt" to 4.0,
    "Gold" to 2.0,
    "Mage" to 2.0,
    "Pedestal" to 2.0,
    "Skull" to 2.0,
    "Pipes" to 2.0,
    "Pit" to 4.0,
    "Waterfall" to 2.0,
    "Atlas" to 2.0,
    "Mines" to 2.0,
    "Blood" to 0.0,
    "Entrance" to 0.0,
    "Fairy" to 0.0,
    "Higher Blaze" to 4.0,
    "Ice Fill" to 9.0,
    "Ice Path" to 4.0,
    "Lower Blaze" to 4.0,
    "New Trap" to 2.0,
    "Quiz" to 5.0,
    "Teleport Maze" to 2.0,
    "Tic Tac Toe" to 5.0,
    "Water Board" to 6.0,
)

data class ModConfigData(
    var hypixelApiKey: String? = null,
    // Per-room difficulty weight, keyed by the room's real name (e.g. "Crypt", "Ice
    // Fill") - a room absent from this map hasn't been customized yet and defaults to 1.0
    // (see ModConfig.getRoomWeight), not 0, so an unconfigured room still counts normally.
    // Starts pre-filled with DEFAULT_ROOM_WEIGHTS rather than empty, so a fresh install
    // already reflects real tuning instead of every room counting equally.
    var roomWeights: MutableMap<String, Double> = DEFAULT_ROOM_WEIGHTS.toMutableMap(),
    // The chat channel the weighted-score line gets actually SENT to (e.g. "/pc", "/ac"),
    // typed exactly as the player would type it themselves. Null/blank means "don't send
    // it to real chat" - the safe default until the player explicitly opts in, since
    // guessing a channel could broadcast into whatever chat is currently active.
    var chatAnnounceChannel: String? = null,
    // Whether the first-launch welcome/API-key-reminder onboarding flow has already been
    // shown - it should only ever appear once, the very first time the player joins a
    // server with Frost installed, not on every join.
    var hasSeenWelcome: Boolean = false,
    // User-defined "press this key, run this command" bindings - see KeybindsScreen.
    var customKeybinds: MutableList<CustomKeybind> = mutableListOf(),
    // Spotify app Client ID, from the player's own free Spotify Developer app - not
    // secret on its own (it's a public identifier by design in PKCE flows), but stored
    // here anyway since it's needed alongside the refresh token below.
    var spotifyClientId: String? = null,
    // Long-lived Spotify refresh token obtained via the PKCE authorization flow (see
    // com.frost.spotify.SpotifyAuth). Unlike the short-lived access token (kept in memory
    // only), this is the actual secret worth protecting - same treatment as the Hypixel
    // API key above: local file only, already covered by .gitignore, never transmitted
    // anywhere except to accounts.spotify.com/api/token.
    var spotifyRefreshToken: String? = null,
    // User-defined friend tags for the Friends chat relay's tag tabs - see
    // FriendTagsScreen. Tag-centric (name -> member usernames), not the other way around,
    // to match how the screen edits it: create a tag, then assign friends to it. Renamed
    // from the old per-friend map (friendTags: username -> tag names) when the UI moved
    // from chat commands to this screen - old data is intentionally not migrated, a clean
    // slate per the user's explicit request when this changed.
    var friendTagList: MutableList<FriendTag> = mutableListOf(),
    // Per-feature on/off state for the main menu's feature buttons (see
    // com.frost.gui.FeatureButton) - left-click there toggles these, right-click opens the
    // matching settings screen regardless of on/off state. All default false - a fresh
    // install should do nothing until the player opts into each feature themselves, rather
    // than a new user immediately having every feature active with no idea what's running.
    var dungeonContributionsEnabled: Boolean = false,
    var betterFriendsListEnabled: Boolean = false,
    var keybindsEnabled: Boolean = false,
    var spotifyLinkEnabled: Boolean = false,
    // Separate from betterFriendsListEnabled - recruit state (floor + filled classes, the
    // Party Finder auto-fill, and the [Recruit] button in /fl) is its own feature under the
    // Dungeons tab, so it can be toggled independently of the rest of the friends list.
    var recruitEnabled: Boolean = false,
    // Pulls the crosshair toward Odin's etherwarp waypoints while sneaking with an Aspect of
    // the Void - see com.frost.dungeon.AimAssist. Strength is a 1-100 percent.
    var aimAssistEnabled: Boolean = false,
    var aimAssistStrength: Int = DEFAULT_AIM_ASSIST_STRENGTH,
    // Per-waypoint aim anchor overrides set in Aim Assist's edit mode, keyed by
    // "<Odin room name>|<x>,<y>,<z>" in that room's RELATIVE coordinates (so it applies no
    // matter where or which way the room spawns), valued by a com.frost.dungeon.AimAnchor
    // name - also room-relative for North/South/East/West. A waypoint absent from this map
    // is aimed at its center (the default).
    var aimAssistAnchors: MutableMap<String, String> = mutableMapOf(),
)

const val DEFAULT_AIM_ASSIST_STRENGTH = 50
const val MIN_AIM_ASSIST_STRENGTH = 1
const val MAX_AIM_ASSIST_STRENGTH = 100

/** One user-defined friend tag: [name] plus which friends are in it - see
 * [com.frost.gui.FriendTagsScreen]. [members] can contain a blank/duplicate entry mid-edit
 * (a freshly-added row nobody's typed a username into yet) - callers should ignore blanks
 * rather than treating them as an error. [color] is a [net.minecraft.ChatFormatting] enum
 * name (e.g. "AQUA"), or null for no override (default green) - stored as a plain string
 * rather than the enum itself so this data class doesn't need a Minecraft import, matching
 * the rest of this file's plain-JVM-testable config model. [keyCode] is a raw GLFW keysym
 * (see [CustomKeybind]), -1 meaning "not bound" - hovering a friend's line in chat and
 * pressing this key adds them to this tag directly, without opening any screen. */
data class FriendTag(
    var name: String = "",
    val members: MutableList<String> = mutableListOf(),
    var color: String? = null,
    var keyCode: Int = -1,
)

/**
 * One user-configured "press [keyCode], run [command]" binding. [keyCode] is a raw GLFW
 * keysym (see [net.minecraft.client.util.InputUtil.Type.KEYSYM]); -1 means "not bound yet"
 * (a freshly-added row before the player has pressed a key for it). [command] is sent
 * exactly as typed - a leading "/" is treated as a real command (e.g. "/pc gg"), anything
 * else as plain chat.
 */
data class CustomKeybind(var command: String = "", var keyCode: Int = -1)

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

    /** /frost roomweights reset - replaces the whole map wholesale (not a merge, unlike
     * [setRoomWeights]) with [DEFAULT_ROOM_WEIGHTS], so a room customized away from default
     * and then removed from DEFAULT_ROOM_WEIGHTS in some future update doesn't linger. */
    fun resetRoomWeightsToDefault() {
        val config = get()
        config.roomWeights = DEFAULT_ROOM_WEIGHTS.toMutableMap()
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

    fun isDungeonContributionsEnabled(): Boolean = get().dungeonContributionsEnabled
    fun setDungeonContributionsEnabled(enabled: Boolean) {
        val config = get()
        config.dungeonContributionsEnabled = enabled
        save(config)
    }

    fun isBetterFriendsListEnabled(): Boolean = get().betterFriendsListEnabled
    fun setBetterFriendsListEnabled(enabled: Boolean) {
        val config = get()
        config.betterFriendsListEnabled = enabled
        save(config)
    }

    fun isKeybindsEnabled(): Boolean = get().keybindsEnabled
    fun setKeybindsEnabled(enabled: Boolean) {
        val config = get()
        config.keybindsEnabled = enabled
        save(config)
    }

    fun isSpotifyLinkEnabled(): Boolean = get().spotifyLinkEnabled
    fun setSpotifyLinkEnabled(enabled: Boolean) {
        val config = get()
        config.spotifyLinkEnabled = enabled
        save(config)
    }

    fun isRecruitEnabled(): Boolean = get().recruitEnabled
    fun setRecruitEnabled(enabled: Boolean) {
        val config = get()
        config.recruitEnabled = enabled
        save(config)
    }

    fun isAimAssistEnabled(): Boolean = get().aimAssistEnabled
    fun setAimAssistEnabled(enabled: Boolean) {
        val config = get()
        config.aimAssistEnabled = enabled
        save(config)
    }

    /** Clamped on read too, so a hand-edited out-of-range value in frost.json can't make the
     * assist snap harder than the slider allows. */
    fun getAimAssistStrength(): Int =
        get().aimAssistStrength.coerceIn(MIN_AIM_ASSIST_STRENGTH, MAX_AIM_ASSIST_STRENGTH)
    fun setAimAssistStrength(strength: Int) {
        val config = get()
        config.aimAssistStrength = strength.coerceIn(MIN_AIM_ASSIST_STRENGTH, MAX_AIM_ASSIST_STRENGTH)
        save(config)
    }

    /** Raw stored anchor name for [key] (see [ModConfigData.aimAssistAnchors]), or null if
     * that waypoint was never edited. */
    fun getAimAssistAnchor(key: String): String? = get().aimAssistAnchors[key]

    /** Saves [anchorName] for [key]; null (the default, center) removes the entry instead of
     * storing it, so the file only ever holds waypoints that actually differ from default. */
    fun setAimAssistAnchor(key: String, anchorName: String?) {
        val config = get()
        if (anchorName == null) config.aimAssistAnchors.remove(key) else config.aimAssistAnchors[key] = anchorName
        save(config)
    }

    /** Replaces the whole custom-keybinds list and saves once - the Keybinds screen calls
     * this on close with its full current list rather than saving per-edit. */
    fun setCustomKeybinds(keybinds: List<CustomKeybind>) {
        val config = get()
        config.customKeybinds = keybinds.toMutableList()
        save(config)
    }

    fun setSpotifyClientId(clientId: String) {
        val config = get()
        config.spotifyClientId = clientId
        save(config)
    }

    fun setSpotifyRefreshToken(refreshToken: String?) {
        val config = get()
        config.spotifyRefreshToken = refreshToken
        save(config)
    }

    fun getFriendTags(): List<FriendTag> = get().friendTagList

    /** Replaces the whole tag list in one save - FriendTagsScreen calls this on close with
     * its full current state, matching setCustomKeybinds's save-once-on-close pattern.
     * Blank-named rows (an added-but-never-named tag) are dropped rather than saved. */
    fun setFriendTags(tags: List<FriendTag>) {
        val config = get()
        config.friendTagList = tags.filter { it.name.isNotBlank() }.toMutableList()
        save(config)
    }

    /** Every (non-blank) tag name [username] belongs to - used when rendering their line in
     * the friends chat relay. */
    fun tagsForFriend(username: String): Set<String> =
        get().friendTagList.filter { it.name.isNotBlank() && username in it.members }.map { it.name }.toSet()

    /** Every defined tag's name, for building the Friends chat relay's tab row. */
    fun allFriendTagNames(): List<String> = get().friendTagList.map { it.name }.filter { it.isNotBlank() }

    /** Adds [username] to [tag]'s members (if not already present) and saves - [tag] is
     * expected to be one of the live records from [getFriendTags], since the hotkey feature
     * that calls this already has that exact reference in hand from its own polling. */
    fun addMemberToTag(tag: FriendTag, username: String) {
        if (username in tag.members) return
        tag.members.add(username)
        save(get())
    }
}
