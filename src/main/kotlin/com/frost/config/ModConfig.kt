package com.frost.config

import com.frost.dungeon.FunnyMapLayout
import com.frost.dungeon.MapStyle
import com.frost.gui.map.NaRoomText
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
    // Per-feature on/off state for the rows of the click GUI (see com.frost.gui.ClickGuiScreen)
    // - left-click there toggles these, right-click opens the matching settings regardless of
    // on/off state. All default false - a fresh
    // install should do nothing until the player opts into each feature themselves, rather
    // than a new user immediately having every feature active with no idea what's running.
    var dungeonContributionsEnabled: Boolean = false,
    var keybindsEnabled: Boolean = false,
    var spotifyLinkEnabled: Boolean = false,
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
    // Tracks unopened Croesus chests - see com.frost.dungeon.Croesus. The count is saved because
    // it can only be read in the Dungeon Hub, and is -1 until it's been read once. The HUD's
    // position is in GUI pixels from the top-left of the screen, starting just below the map.
    var croesusEnabled: Boolean = false,
    var croesusHudEnabled: Boolean = true,
    var croesusChests: Int = -1,
    var croesusHudX: Int = 4,
    var croesusHudY: Int = 176,
    // A dungeon map scanned from the world, drawn on the HUD at this position (GUI pixels from
    // the top-left of the screen) - see com.frost.dungeon.FunnyMap.
    var funnyMapEnabled: Boolean = false,
    var funnyMapX: Int = 4,
    var funnyMapY: Int = 4,
    var funnyMapScale: Float = FunnyMapLayout.DEFAULT_SCALE,
    // Which look the map is drawn in, by com.frost.dungeon.MapStyle name: Frost's own, or one
    // replicating another mod's map.
    var mapStyle: String = MapStyle.FROST.name,
    // Show Princes: a crown on rooms a prince can be in, hidden once one dies if hidePrincesOnKill,
    // in the room's top-left corner or behind its name if princeIconCenter.
    var showPrinces: Boolean = false,
    var hidePrincesOnKill: Boolean = true,
    var princeIconCenter: Boolean = false,
    var princeIconSize: Int = DEFAULT_MAP_ELEMENT_SIZE,
    // What the NA style writes on rooms, by com.frost.gui.map.NaRoomText name.
    var naRoomText: String = NaRoomText.NAME_AND_SECRETS.name,
    // Draws the route from the entrance to the blood door on Funny Map.
    var bloodRushPathEnabled: Boolean = false,
    // On: a long room name shrinks to fit its room. Off: every name is the same size, even if
    // that means it spills out of its room.
    var limitRoomNamesEnabled: Boolean = true,
    // Room types (see FunnyMapLayout.SPECIAL_ROOM_TYPES) whose names are hidden on Funny Map.
    // Stored as what's hidden, so a fresh install or a new type shows its name by default.
    var hiddenRoomNameTypes: MutableSet<String> = mutableSetOf(),
    // Player head and room name sizes on Funny Map, as a percent of their normal size.
    var funnyMapHeadSize: Int = DEFAULT_MAP_ELEMENT_SIZE,
    var funnyMapTextSize: Int = DEFAULT_MAP_ELEMENT_SIZE,
    // Fills completed rooms on the map with a solid color, ARGB like the map's background. The
    // room types here are the optional ones from FunnyMapLayout.FILL_ROOM_TYPES that have been
    // turned on, and Blood starts on as it has always filled.
    var fillCompletedRoomsEnabled: Boolean = true,
    var fillCompletedRoomTypes: MutableSet<String> = mutableSetOf("BLOOD"),
    var fillCompletedColor: Int = DEFAULT_FILL_COMPLETED_COLOR,
    var mapBackgroundColor: Int = DEFAULT_MAP_BACKGROUND_COLOR,
    // A line under the map with the team's secrets out of the dungeon's total.
    var totalSecretsEnabled: Boolean = false,
    // Boxes around starred mobs through walls - see com.frost.dungeon.DungeonEsp. The color is
    // plain RGB, with no alpha byte, as older configs saved it that way - its alpha is separate.
    // Filled boxes rather than just outlines if set.
    var starMobEspEnabled: Boolean = false,
    var starMobEspColor: Int = DEFAULT_STAR_MOB_ESP_COLOR,
    var starMobEspAlpha: Int = OPAQUE_ALPHA,
    var starMobEspFilled: Boolean = false,
    // The color picker's saved colors, shared by every picker: ARGB, by slot.
    var favoriteColors: MutableMap<Int, Int> = mutableMapOf(),
    // Warns of a short blood rush - see com.frost.dungeon.BloodRushAlert. Part of Funny Map.
    var bloodRushAlertEnabled: Boolean = false,
    var alertThreeRoomEnabled: Boolean = false,
    var alertOnPossibleEnabled: Boolean = false,
    // Presses the drop key shortly after entering the M5 boss - see com.frost.dungeon.M5AutoUlt.
    var m5AutoUltEnabled: Boolean = false,
    // Faces the way a spirit leap's target is facing - see com.frost.dungeon.LeapFix.
    var leapFixEnabled: Boolean = false,
    // A box through walls around the fairy room's way out - also com.frost.dungeon.DungeonEsp.
    var fairyDoorEspEnabled: Boolean = false,
    // Marks the starred mobs in the player's current room on Funny Map.
    var starMobsEnabled: Boolean = false,
    // On: Funny Map only identifies rooms that have been opened. Off: it shows every room.
    // funnyMapModeKey is the raw GLFW keysym that switches between them (see CustomKeybind), -1
    // meaning "not bound".
    var funnyMapLegit: Boolean = true,
    var funnyMapModeKey: Int = -1,
    // Turns the door out of the entrance room into glass - see com.frost.dungeon.GlassDoors. The
    // second setting extends that to wither doors.
    var glassDoorsEnabled: Boolean = false,
    var glassWitherDoorsEnabled: Boolean = false,
    // Players flagged on the M5 Shitters list get their names shown in red - see
    // com.frost.shitters.M5Shitters. The list comes from the database at m5ShittersUrl, plus
    // any added here by hand with /frost shitters add, by name, each with its reason. An empty
    // URL means no database, rather than going back to the default.
    var m5ShittersEnabled: Boolean = false,
    var m5ShittersUrl: String? = DEFAULT_M5_SHITTERS_URL,
    var m5ShittersLocal: MutableMap<String, String> = mutableMapOf(),
    // Key Pickup - see com.frost.dungeon.KeyPickup. Its notification is centered on a point given
    // as a fraction of the screen's width and height, so it stays put across window sizes, at a
    // size in percent of normal text. The sound is a Minecraft sound event's ID, with its
    // volume and pitch in percent.
    var keyPickupEnabled: Boolean = false,
    var keyNotificationEnabled: Boolean = true,
    var keyNotificationX: Float = DEFAULT_KEY_NOTIFICATION_X,
    var keyNotificationY: Float = DEFAULT_KEY_NOTIFICATION_Y,
    var keyNotificationSize: Int = DEFAULT_KEY_NOTIFICATION_SIZE,
    var keyNotificationSeconds: Int = DEFAULT_KEY_NOTIFICATION_SECONDS,
    var witherKeyColor: Int = DEFAULT_WITHER_KEY_COLOR,
    var bloodKeyColor: Int = DEFAULT_BLOOD_KEY_COLOR,
    var keySoundEnabled: Boolean = true,
    var keySound: String = DEFAULT_KEY_SOUND,
    var keySoundVolume: Int = DEFAULT_SOUND_PERCENT,
    var keySoundPitch: Int = DEFAULT_SOUND_PERCENT,
    // Chat Regex - see com.frost.qol.ChatRegex. Each rule matches chat lines and shows text on
    // screen and/or plays a sound.
    var chatRegexEnabled: Boolean = false,
    var chatRegexRules: MutableList<ChatRegexRule> = mutableListOf(),
    // Where each click GUI column has been dragged to, by column name. A column not listed here
    // hasn't been moved, and goes to its default spot.
    var clickGuiPanels: MutableMap<String, ClickGuiPanelState> = mutableMapOf(),
)

/**
 * One Chat Regex rule: when a chat line matches [pattern] - plain text, or a regular expression
 * with [regex] - it can show [text] on screen (the chat line itself if blank) and play [sound].
 * The text's position is its center as a fraction of the screen, its size a percent of normal
 * text, and the sound's volume and pitch percents. [id] tells rules apart, even identical ones.
 */
data class ChatRegexRule(
    var id: String = java.util.UUID.randomUUID().toString(),
    var enabled: Boolean = true,
    var pattern: String = "",
    var regex: Boolean = false,
    var matchCase: Boolean = false,
    var showText: Boolean = true,
    var text: String = "",
    var color: Int = 0xFFFFFFFF.toInt(),
    var x: Float = DEFAULT_KEY_NOTIFICATION_X,
    var y: Float = 0.25f,
    var size: Int = DEFAULT_CHAT_REGEX_SIZE,
    var seconds: Int = DEFAULT_KEY_NOTIFICATION_SECONDS,
    var playSound: Boolean = true,
    var sound: String = DEFAULT_KEY_SOUND,
    var volume: Int = DEFAULT_SOUND_PERCENT,
    var pitch: Int = DEFAULT_SOUND_PERCENT,
)

const val DEFAULT_CHAT_REGEX_SIZE = 200

/** A click GUI column's saved position, in the GUI's own fixed-scale pixels. */
data class ClickGuiPanelState(var x: Int = 0, var y: Int = 0, var extended: Boolean = true)

const val DEFAULT_MAP_ELEMENT_SIZE = 100
const val MIN_MAP_ELEMENT_SIZE = 50
const val MAX_MAP_ELEMENT_SIZE = 200
const val DEFAULT_STAR_MOB_ESP_COLOR = 0xFFC21A
const val OPAQUE_ALPHA = 0xFF
const val DEFAULT_FILL_COMPLETED_COLOR = 0xFF2E96DC.toInt()
const val DEFAULT_MAP_BACKGROUND_COLOR = 0xB0080B10.toInt()
const val DEFAULT_AIM_ASSIST_STRENGTH = 50
const val DEFAULT_KEY_NOTIFICATION_X = 0.5f
const val DEFAULT_KEY_NOTIFICATION_Y = 0.3f
const val DEFAULT_KEY_NOTIFICATION_SIZE = 250
const val MIN_KEY_NOTIFICATION_SIZE = 50
const val MAX_KEY_NOTIFICATION_SIZE = 500
const val DEFAULT_KEY_NOTIFICATION_SECONDS = 2
const val MIN_KEY_NOTIFICATION_SECONDS = 1
const val MAX_KEY_NOTIFICATION_SECONDS = 10
const val DEFAULT_WITHER_KEY_COLOR = 0xFFC8C8C8.toInt()
const val DEFAULT_BLOOD_KEY_COLOR = 0xFFFF5555.toInt()
const val DEFAULT_KEY_SOUND = "minecraft:block.note_block.pling"
const val DEFAULT_SOUND_PERCENT = 100
const val MAX_SOUND_VOLUME = 100
const val MIN_SOUND_PITCH = 50
const val MAX_SOUND_PITCH = 200
const val DEFAULT_M5_SHITTERS_URL = "https://jsonhosting.com/api/json/e7619cc9/raw"
const val MIN_AIM_ASSIST_STRENGTH = 1
const val MAX_AIM_ASSIST_STRENGTH = 100


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


    // Columns that have been renamed, by their new name: where the old one was saved still counts.
    private val RENAMED_PANELS = mapOf("QoL" to "Quality of Life")

    fun getClickGuiPanel(name: String): ClickGuiPanelState? =
        get().clickGuiPanels[name] ?: RENAMED_PANELS[name]?.let { get().clickGuiPanels[it] }
    fun setClickGuiPanel(name: String, x: Int, y: Int, extended: Boolean) {
        val config = get()
        config.clickGuiPanels[name] = ClickGuiPanelState(x, y, extended)
        save(config)
    }

    fun isLimitRoomNamesEnabled(): Boolean = get().limitRoomNamesEnabled
    fun setLimitRoomNamesEnabled(enabled: Boolean) {
        val config = get()
        config.limitRoomNamesEnabled = enabled
        save(config)
    }

    fun isRoomNameShown(roomType: String): Boolean = roomType !in get().hiddenRoomNameTypes
    fun setRoomNameShown(roomType: String, shown: Boolean) {
        val config = get()
        if (shown) config.hiddenRoomNameTypes.remove(roomType) else config.hiddenRoomNameTypes.add(roomType)
        save(config)
    }

    fun isBloodRushPathEnabled(): Boolean = get().bloodRushPathEnabled
    fun setBloodRushPathEnabled(enabled: Boolean) {
        val config = get()
        config.bloodRushPathEnabled = enabled
        save(config)
    }

    fun isStarMobsEnabled(): Boolean = get().starMobsEnabled
    fun setStarMobsEnabled(enabled: Boolean) {
        val config = get()
        config.starMobsEnabled = enabled
        save(config)
    }

    /** The Funny Map feature in the menu. Off is what makes Dungeon Map legit. */
    fun isFunnyModeEnabled(): Boolean = !get().funnyMapLegit
    fun setFunnyModeEnabled(enabled: Boolean) {
        val config = get()
        config.funnyMapLegit = !enabled
        save(config)
    }

    fun isBloodRushAlertEnabled(): Boolean = get().bloodRushAlertEnabled
    fun setBloodRushAlertEnabled(enabled: Boolean) {
        val config = get()
        config.bloodRushAlertEnabled = enabled
        save(config)
    }

    fun isAlertThreeRoomEnabled(): Boolean = get().alertThreeRoomEnabled
    fun setAlertThreeRoomEnabled(enabled: Boolean) {
        val config = get()
        config.alertThreeRoomEnabled = enabled
        save(config)
    }

    fun isAlertOnPossibleEnabled(): Boolean = get().alertOnPossibleEnabled
    fun setAlertOnPossibleEnabled(enabled: Boolean) {
        val config = get()
        config.alertOnPossibleEnabled = enabled
        save(config)
    }

    fun isM5AutoUltEnabled(): Boolean = get().m5AutoUltEnabled
    fun setM5AutoUltEnabled(enabled: Boolean) {
        val config = get()
        config.m5AutoUltEnabled = enabled
        save(config)
    }

    fun isLeapFixEnabled(): Boolean = get().leapFixEnabled
    fun setLeapFixEnabled(enabled: Boolean) {
        val config = get()
        config.leapFixEnabled = enabled
        save(config)
    }

    fun isStarMobEspFilled(): Boolean = get().starMobEspFilled
    fun setStarMobEspFilled(filled: Boolean) {
        val config = get()
        config.starMobEspFilled = filled
        save(config)
    }

    fun getFunnyMapModeKey(): Int = get().funnyMapModeKey
    fun setFunnyMapModeKey(keyCode: Int) {
        val config = get()
        config.funnyMapModeKey = keyCode
        save(config)
    }

    /** Clamped on read, so a hand-edited value in frost.json can't make these absurdly large. */
    fun getFunnyMapHeadSize(): Int = get().funnyMapHeadSize.coerceIn(MIN_MAP_ELEMENT_SIZE, MAX_MAP_ELEMENT_SIZE)
    fun setFunnyMapHeadSize(size: Int) {
        val config = get()
        config.funnyMapHeadSize = size
        save(config)
    }

    fun getFunnyMapTextSize(): Int = get().funnyMapTextSize.coerceIn(MIN_MAP_ELEMENT_SIZE, MAX_MAP_ELEMENT_SIZE)
    fun setFunnyMapTextSize(size: Int) {
        val config = get()
        config.funnyMapTextSize = size
        save(config)
    }

    fun isStarMobEspEnabled(): Boolean = get().starMobEspEnabled
    fun setStarMobEspEnabled(enabled: Boolean) {
        val config = get()
        config.starMobEspEnabled = enabled
        save(config)
    }

    /** The color pickers' setters only change the color in memory, as they run on every step of a
     * drag. [saveCurrent] writes it once the drag ends. */
    fun saveCurrent() = save(get())

    fun getStarMobEspColor(): Int = (get().starMobEspAlpha.coerceIn(0, OPAQUE_ALPHA) shl 24) or (get().starMobEspColor and 0xFFFFFF)
    fun setStarMobEspColor(argb: Int) {
        val config = get()
        config.starMobEspColor = argb and 0xFFFFFF
        config.starMobEspAlpha = argb ushr 24
    }

    fun isFillCompletedRoomsEnabled(): Boolean = get().fillCompletedRoomsEnabled
    fun setFillCompletedRoomsEnabled(enabled: Boolean) {
        val config = get()
        config.fillCompletedRoomsEnabled = enabled
        save(config)
    }

    fun getFilledRoomTypes(): Set<String> = get().fillCompletedRoomTypes
    fun isRoomTypeFilled(roomType: String): Boolean = roomType in get().fillCompletedRoomTypes
    fun setRoomTypeFilled(roomType: String, filled: Boolean) {
        val config = get()
        if (filled) config.fillCompletedRoomTypes.add(roomType) else config.fillCompletedRoomTypes.remove(roomType)
        save(config)
    }

    fun isTotalSecretsEnabled(): Boolean = get().totalSecretsEnabled
    fun setTotalSecretsEnabled(enabled: Boolean) {
        val config = get()
        config.totalSecretsEnabled = enabled
        save(config)
    }

    fun getFillCompletedColor(): Int = get().fillCompletedColor
    fun setFillCompletedColor(argb: Int) {
        get().fillCompletedColor = argb
    }

    fun getMapBackgroundColor(): Int = get().mapBackgroundColor
    fun setMapBackgroundColor(argb: Int) {
        get().mapBackgroundColor = argb
    }

    const val FAVORITE_COLOR_SLOTS = 4

    fun getFavoriteColor(slot: Int): Int? = get().favoriteColors[slot]
    fun setFavoriteColor(slot: Int, argb: Int) {
        val config = get()
        config.favoriteColors[slot] = argb
        save(config)
    }

    fun isFairyDoorEspEnabled(): Boolean = get().fairyDoorEspEnabled
    fun setFairyDoorEspEnabled(enabled: Boolean) {
        val config = get()
        config.fairyDoorEspEnabled = enabled
        save(config)
    }

    fun isChatRegexEnabled(): Boolean = get().chatRegexEnabled
    fun setChatRegexEnabled(enabled: Boolean) = update { it.chatRegexEnabled = enabled }

    fun getChatRegexRules(): List<ChatRegexRule> = get().chatRegexRules

    fun addChatRegexRule(): ChatRegexRule = ChatRegexRule().also { rule -> update { it.chatRegexRules += rule } }

    fun removeChatRegexRule(rule: ChatRegexRule) = update { config -> config.chatRegexRules.removeIf { it.id == rule.id } }

    fun isKeyPickupEnabled(): Boolean = get().keyPickupEnabled
    fun setKeyPickupEnabled(enabled: Boolean) = update { it.keyPickupEnabled = enabled }

    fun isKeyNotificationEnabled(): Boolean = get().keyNotificationEnabled
    fun setKeyNotificationEnabled(enabled: Boolean) = update { it.keyNotificationEnabled = enabled }

    fun getKeyNotificationX(): Float = get().keyNotificationX.coerceIn(0f, 1f)
    fun getKeyNotificationY(): Float = get().keyNotificationY.coerceIn(0f, 1f)
    fun setKeyNotificationPosition(x: Float, y: Float) = update {
        it.keyNotificationX = x.coerceIn(0f, 1f)
        it.keyNotificationY = y.coerceIn(0f, 1f)
    }

    fun getKeyNotificationSize(): Int = get().keyNotificationSize.coerceIn(MIN_KEY_NOTIFICATION_SIZE, MAX_KEY_NOTIFICATION_SIZE)
    fun setKeyNotificationSize(size: Int) = update { it.keyNotificationSize = size.coerceIn(MIN_KEY_NOTIFICATION_SIZE, MAX_KEY_NOTIFICATION_SIZE) }

    fun getKeyNotificationSeconds(): Int = get().keyNotificationSeconds.coerceIn(MIN_KEY_NOTIFICATION_SECONDS, MAX_KEY_NOTIFICATION_SECONDS)
    fun setKeyNotificationSeconds(seconds: Int) = update { it.keyNotificationSeconds = seconds }

    // Like the other color pickers, these only change memory; the picker saves on release.
    fun getWitherKeyColor(): Int = get().witherKeyColor
    fun setWitherKeyColor(argb: Int) {
        get().witherKeyColor = argb
    }

    fun getBloodKeyColor(): Int = get().bloodKeyColor
    fun setBloodKeyColor(argb: Int) {
        get().bloodKeyColor = argb
    }

    fun isKeySoundEnabled(): Boolean = get().keySoundEnabled
    fun setKeySoundEnabled(enabled: Boolean) = update { it.keySoundEnabled = enabled }

    fun getKeySound(): String = get().keySound.ifBlank { DEFAULT_KEY_SOUND }
    fun setKeySound(id: String) = update { it.keySound = id }

    fun getKeySoundVolume(): Int = get().keySoundVolume.coerceIn(0, MAX_SOUND_VOLUME)
    fun setKeySoundVolume(volume: Int) = update { it.keySoundVolume = volume }

    fun getKeySoundPitch(): Int = get().keySoundPitch.coerceIn(MIN_SOUND_PITCH, MAX_SOUND_PITCH)
    fun setKeySoundPitch(pitch: Int) = update { it.keySoundPitch = pitch }

    private inline fun update(change: (ModConfigData) -> Unit) {
        val config = get()
        change(config)
        save(config)
    }

    fun isM5ShittersEnabled(): Boolean = get().m5ShittersEnabled
    fun setM5ShittersEnabled(enabled: Boolean) {
        val config = get()
        config.m5ShittersEnabled = enabled
        save(config)
    }

    fun getM5ShittersUrl(): String = get().m5ShittersUrl.orEmpty()
    fun setM5ShittersUrl(url: String) {
        val config = get()
        config.m5ShittersUrl = url.trim()
        save(config)
    }

    /** The players added by hand, by name, with their reasons. */
    fun getLocalShitters(): Map<String, String> = get().m5ShittersLocal

    fun addLocalShitter(name: String, reason: String) {
        val config = get()
        config.m5ShittersLocal.keys.filter { it.equals(name, ignoreCase = true) }.forEach(config.m5ShittersLocal::remove)
        config.m5ShittersLocal[name] = reason
        save(config)
    }

    /** Returns whether [name] was on the hand-added list. */
    fun removeLocalShitter(name: String): Boolean {
        val config = get()
        val removed = config.m5ShittersLocal.keys.filter { it.equals(name, ignoreCase = true) }
        removed.forEach(config.m5ShittersLocal::remove)
        if (removed.isNotEmpty()) save(config)
        return removed.isNotEmpty()
    }

    fun isShowPrincesEnabled(): Boolean = get().showPrinces
    fun setShowPrincesEnabled(enabled: Boolean) = update { it.showPrinces = enabled }

    fun isHidePrincesOnKillEnabled(): Boolean = get().hidePrincesOnKill
    fun setHidePrincesOnKillEnabled(enabled: Boolean) = update { it.hidePrincesOnKill = enabled }

    fun getPrinceIconSize(): Int = get().princeIconSize.coerceIn(MIN_MAP_ELEMENT_SIZE, MAX_MAP_ELEMENT_SIZE)
    fun setPrinceIconSize(size: Int) = update { it.princeIconSize = size }

    fun isPrinceIconCenter(): Boolean = get().princeIconCenter
    fun setPrinceIconCenter(center: Boolean) = update { it.princeIconCenter = center }

    fun getNaRoomText(): NaRoomText = NaRoomText.fromName(get().naRoomText)
    fun setNaRoomText(text: NaRoomText) = update { it.naRoomText = text.name }

    fun getMapStyle(): MapStyle = MapStyle.fromName(get().mapStyle)
    fun setMapStyle(style: MapStyle) {
        val config = get()
        config.mapStyle = style.name
        save(config)
    }

    fun isFunnyMapEnabled(): Boolean = get().funnyMapEnabled
    fun setFunnyMapEnabled(enabled: Boolean) {
        val config = get()
        config.funnyMapEnabled = enabled
        save(config)
    }

    fun getFunnyMapX(): Int = get().funnyMapX
    fun getFunnyMapY(): Int = get().funnyMapY
    fun getFunnyMapScale(): Float = get().funnyMapScale.coerceIn(FunnyMapLayout.MIN_SCALE, FunnyMapLayout.MAX_SCALE)
    fun setFunnyMapPlacement(x: Int, y: Int, scale: Float) {
        val config = get()
        config.funnyMapX = x
        config.funnyMapY = y
        config.funnyMapScale = scale
        save(config)
    }

    fun isCroesusEnabled(): Boolean = get().croesusEnabled
    fun setCroesusEnabled(enabled: Boolean) {
        val config = get()
        config.croesusEnabled = enabled
        save(config)
    }

    fun isCroesusHudEnabled(): Boolean = get().croesusHudEnabled
    fun setCroesusHudEnabled(enabled: Boolean) {
        val config = get()
        config.croesusHudEnabled = enabled
        save(config)
    }

    fun getCroesusChests(): Int = get().croesusChests
    fun setCroesusChests(count: Int) {
        val config = get()
        config.croesusChests = count
        save(config)
    }

    fun getCroesusHudX(): Int = get().croesusHudX
    fun getCroesusHudY(): Int = get().croesusHudY
    fun setCroesusHudPosition(x: Int, y: Int) {
        val config = get()
        config.croesusHudX = x
        config.croesusHudY = y
        save(config)
    }

    fun isGlassDoorsEnabled(): Boolean = get().glassDoorsEnabled
    fun setGlassDoorsEnabled(enabled: Boolean) {
        val config = get()
        config.glassDoorsEnabled = enabled
        save(config)
    }

    fun isGlassWitherDoorsEnabled(): Boolean = get().glassWitherDoorsEnabled
    fun setGlassWitherDoorsEnabled(enabled: Boolean) {
        val config = get()
        config.glassWitherDoorsEnabled = enabled
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
}
