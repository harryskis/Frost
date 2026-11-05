package com.frost.gui

import com.frost.config.DEFAULT_BLOOD_KEY_COLOR
import com.frost.config.DEFAULT_FILL_COMPLETED_COLOR
import com.frost.config.DEFAULT_WITHER_KEY_COLOR
import com.frost.config.MAX_KEY_NOTIFICATION_SECONDS
import com.frost.config.MAX_KEY_NOTIFICATION_SIZE
import com.frost.config.MAX_SOUND_PITCH
import com.frost.config.MAX_SOUND_VOLUME
import com.frost.config.MIN_KEY_NOTIFICATION_SECONDS
import com.frost.config.MIN_KEY_NOTIFICATION_SIZE
import com.frost.config.MIN_SOUND_PITCH
import com.frost.config.DEFAULT_MAP_BACKGROUND_COLOR
import com.frost.config.DEFAULT_STAR_MOB_ESP_COLOR
import com.frost.config.MAX_AIM_ASSIST_STRENGTH
import com.frost.config.MAX_MAP_ELEMENT_SIZE
import com.frost.config.MIN_AIM_ASSIST_STRENGTH
import com.frost.config.MIN_MAP_ELEMENT_SIZE
import com.frost.config.ModConfig
import com.frost.dungeon.AimAssist
import com.frost.dungeon.FunnyMapLayout
import com.frost.config.ChatRegexRule
import com.frost.dungeon.KeyPickup
import com.frost.qol.ChatRegex
import com.frost.parsing.DungeonKey
import com.frost.util.Sounds
import com.frost.dungeon.MapStyle
import com.frost.gui.map.NaRoomText
import com.frost.shitters.M5Shitters
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component

/**
 * The /frost menu, in the style of Odin's and NoammAddons' click GUIs: a draggable column per
 * category, with each feature a row that left-click toggles and right-click drops its settings
 * open beneath. There's also a search bar along the bottom, and tooltips on hover.
 *
 * Everything is drawn in "virtual" pixels at a fixed scale, regardless of the game's GUI scale
 * - see [ClickGuiLayout]. Mouse positions are converted on the way in, and nothing here is a
 * vanilla widget except the [EditBox]es doing the typing.
 */
class ClickGuiScreen : Screen(Component.literal("Frost")) {
    private val panels: MutableList<GuiPanel> = buildPanels().toMutableList()
    private var query = ""
    private var searchBox: EditBox? = null
    private var focusedBox: EditBox? = null
    private var dragSetting: GuiSetting? = null
    private var capturingKey: KeybindSetting? = null
    private var lastFrameNanos = 0L

    private var tooltipOwner: Any? = null
    private var tooltipText = ""
    private var tooltipNextOwner: Any? = null
    private var tooltipNextText = ""
    private var tooltipHoverStart = 0L

    private val uiScale: Float get() = ClickGuiLayout.UI_SCALE / minecraft.window.guiScale
    private val virtualWidth: Int get() = (width / uiScale).toInt()
    private val virtualHeight: Int get() = (height / uiScale).toInt()

    override fun init() {
        val all = panels.toList()
        all.forEachIndexed { index, panel -> panel.restoreOrPlace(index, all.size, virtualWidth) }
        val box = searchBox ?: EditBox(font, 0, 0, SEARCH_WIDTH, SEARCH_TEXT_HEIGHT, Component.literal("Search")).also {
            it.setBordered(false)
            it.setMaxLength(SEARCH_MAX_LENGTH)
            it.setResponder { text -> query = text }
            searchBox = it
        }
        focusTextBox(box)
    }

    // Deliberately no blur: a light dim keeps the game (and the map being configured) visible.
    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        context.fillGradient(0, 0, width, height, BACKDROP_TOP, BACKDROP_BOTTOM)
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val now = System.nanoTime()
        val dtMillis = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1_000_000f).coerceAtMost(MAX_FRAME_MILLIS)
        lastFrameNanos = now

        val scale = uiScale
        val virtualMouseX = (mouseX / scale).toInt()
        val virtualMouseY = (mouseY / scale).toInt()
        tooltipNextOwner = null

        context.pose().pushMatrix()
        context.pose().scale(scale, scale)

        val frame = GuiFrame(context, font, virtualMouseX, virtualMouseY, dtMillis, this)
        drawSearchBar(frame)
        // Only the topmost column under the mouse reacts to it. Panels draw back-to-front, so
        // the last one in the list is the one on top.
        val topPanel = panels.lastOrNull { it.isOver(virtualMouseX.toDouble(), virtualMouseY.toDouble()) }
        for (panel in panels) panel.draw(if (panel === topPanel) frame else frame.withoutMouse(), query, virtualWidth, virtualHeight)
        // Last, so its "Contact me" hover card sits on top of any column dragged underneath.
        FrostTheme.drawWordmark(context, font, virtualWidth, virtualMouseX, virtualMouseY)
        drawTooltip(frame, now)

        context.pose().popMatrix()
    }

    private fun drawSearchBar(frame: GuiFrame) {
        val box = searchBox ?: return
        val left = (virtualWidth - SEARCH_WIDTH) / 2
        val top = virtualHeight - SEARCH_BOTTOM_MARGIN
        val context = frame.context
        context.softFill(left, top, left + SEARCH_WIDTH, top + SEARCH_HEIGHT, ClickGuiColors.HEADER)
        context.fill(left, top + SEARCH_HEIGHT - 1, left + SEARCH_WIDTH, top + SEARCH_HEIGHT, if (box.isFocused) ClickGuiColors.ACCENT else ClickGuiColors.SURFACE)
        box.x = left + SEARCH_TEXT_INSET
        box.y = top + (SEARCH_HEIGHT - 8) / 2
        box.width = SEARCH_WIDTH - SEARCH_TEXT_INSET * 2
        box.extractRenderState(context, frame.mouseX, frame.mouseY, 0f)
        if (query.isEmpty() && !box.isFocused) {
            context.text(font, SEARCH_PLACEHOLDER, left + (SEARCH_WIDTH - font.width(SEARCH_PLACEHOLDER)) / 2, box.y, ClickGuiColors.TEXT_DIM, false)
        }
    }

    /** Called while drawing by whatever the mouse is over; a tooltip shows after a short rest
     * on the same thing, so they don't flicker up while the mouse is just passing through. */
    fun hoverTooltip(owner: Any, text: String) {
        if (text.isEmpty()) return
        tooltipNextOwner = owner
        tooltipNextText = text
    }

    private fun drawTooltip(frame: GuiFrame, now: Long) {
        if (tooltipNextOwner !== tooltipOwner) {
            tooltipOwner = tooltipNextOwner
            tooltipText = tooltipNextText
            tooltipHoverStart = now
        }
        if (tooltipOwner == null || now - tooltipHoverStart < TOOLTIP_DELAY_NANOS) return

        val lines = tooltipText.split('\n').flatMap { paragraph ->
            if (paragraph.isEmpty()) listOf(Component.empty().visualOrderText) else font.split(Component.literal(paragraph), TOOLTIP_WIDTH)
        }
        val boxWidth = lines.maxOf { font.width(it) } + TOOLTIP_PADDING * 2
        val boxHeight = lines.size * font.lineHeight + TOOLTIP_PADDING * 2
        val left = (frame.mouseX + TOOLTIP_CURSOR_GAP).coerceAtMost((virtualWidth - boxWidth).coerceAtLeast(0))
        val top = frame.mouseY.coerceAtMost((virtualHeight - boxHeight).coerceAtLeast(0))
        val context = frame.context
        context.softFill(left - 1, top - 1, left + boxWidth + 1, top + boxHeight + 1, ClickGuiColors.ACCENT)
        context.softFill(left, top, left + boxWidth, top + boxHeight, ClickGuiColors.HEADER)
        lines.forEachIndexed { index, line ->
            context.text(font, line, left + TOOLTIP_PADDING, top + TOOLTIP_PADDING + index * font.lineHeight, ClickGuiColors.TEXT, false)
        }
    }

    private fun toVirtual(event: MouseButtonEvent): MouseButtonEvent =
        MouseButtonEvent(event.x() / uiScale, event.y() / uiScale, MouseButtonInfo(event.button(), event.modifiers()))

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val virtual = toVirtual(event)
        focusTextBox(null)
        capturingKey?.capturing = false
        capturingKey = null
        val panel = panels.lastOrNull { it.isOver(virtual.x(), virtual.y()) }
        if (panel != null) {
            // Raised first, so a column dragged or clicked ends up on top of the others.
            panels.remove(panel)
            panels.add(panel)
            panel.mouseClicked(virtual, query, this)
            return true
        }
        searchBox?.let { box ->
            if (isOverSearch(virtual.x(), virtual.y()) && isLeft(virtual)) {
                focusTextBox(box)
                box.mouseClicked(virtual, doubleClick)
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    private fun isOverSearch(x: Double, y: Double): Boolean {
        val left = (virtualWidth - SEARCH_WIDTH) / 2
        val top = virtualHeight - SEARCH_BOTTOM_MARGIN
        return x >= left && x < left + SEARCH_WIDTH && y >= top && y < top + SEARCH_HEIGHT
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val x = event.x() / uiScale
        val y = event.y() / uiScale
        dragSetting?.let {
            it.mouseDragged(x, y)
            return true
        }
        if (panels.any { it.mouseDragged(x, y) }) return true
        return super.mouseDragged(event, deltaX, deltaY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragSetting?.mouseReleased()
        dragSetting = null
        panels.forEach { it.mouseReleased() }
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val panel = panels.lastOrNull { it.isOver(mouseX / uiScale, mouseY / uiScale) } ?: return false
        panel.mouseScrolled(mouseX / uiScale, mouseY / uiScale, query, scrollY)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        capturingKey?.let {
            it.capture(event.key())
            it.capturing = false
            capturingKey = null
            return true
        }
        val box = focusedBox
        if (box != null) {
            if (event.key() == InputConstants.KEY_RETURN) {
                focusTextBox(null)
                return true
            }
            if (box.keyPressed(event)) return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean = focusedBox?.charTyped(event) ?: false

    fun focusTextBox(box: EditBox?) {
        focusedBox?.setFocused(false)
        focusedBox = box
        box?.setFocused(true)
    }

    fun beginKeyCapture(setting: KeybindSetting) {
        capturingKey = setting
        setting.capturing = true
    }

    fun beginDrag(setting: GuiSetting) {
        dragSetting = setting
    }

    fun isLeft(event: MouseButtonEvent) = event.button() == InputConstants.MOUSE_BUTTON_LEFT

    fun playClick() = AbstractWidget.playButtonClickSound(minecraft.soundManager)

    override fun isPauseScreen(): Boolean = false

    private fun open(screen: Screen) {
        Minecraft.getInstance().gui.setScreen(screen)
    }

    private fun buildPanels(): List<GuiPanel> = listOf(
        GuiPanel(
            "Dungeons",
            listOf(contributionsFeature(), aimAssistFeature(), dungeonMapFeature(), funnyMapFeature(), glassDoorsFeature(), starMobEspFeature(), fairyDoorEspFeature(), m5AutoUltFeature(), keyPickupFeature(), m5ShittersFeature(), leapFixFeature(), croesusFeature()),
        ),
        GuiPanel(
            "QoL",
            listOf(
                GuiFeature(
                    "Keybinds",
                    "Press a key to run any command or chat message.",
                    ModConfig::isKeybindsEnabled,
                    ModConfig::setKeybindsEnabled,
                    listOf(ActionSetting("Edit Keybinds", "Add, change or remove key bindings.") { open(KeybindsScreen(this)) }),
                ),
                chatRegexFeature(),
            ),
        ),
        GuiPanel(
            "Misc",
            listOf(
                GuiFeature(
                    "Spotify Link",
                    "Control Spotify playback from in-game with /frost spotify.",
                    ModConfig::isSpotifyLinkEnabled,
                    ModConfig::setSpotifyLinkEnabled,
                    listOf(
                        TextSetting(
                            "Client ID",
                            "From your own Spotify Developer app. Afterwards, run /frost spotify login in chat.",
                            SPOTIFY_ID_MAX_LENGTH,
                            { ModConfig.get().spotifyClientId.orEmpty() },
                            { value -> value.trim().takeIf { it.isNotEmpty() }?.let(ModConfig::setSpotifyClientId) },
                        ),
                    ),
                ),
            ),
        ),
        GuiPanel(
            "Developer",
            listOf(
                GuiFeature(
                    "Hypixel API",
                    "Your own API key, used to read each player's secrets for the run summary.",
                    null,
                    null,
                    listOf(ActionSetting("Set API Key", "Enter or change your Hypixel API key.") { open(ApiKeyScreen(this)) }),
                ),
            ),
        ),
    )

    private fun contributionsFeature() = GuiFeature(
        "Contributions",
        "Tracks each player's rooms and secrets, and scores the run when it ends.",
        ModConfig::isDungeonContributionsEnabled,
        ModConfig::setDungeonContributionsEnabled,
        listOf(
            ActionSetting("Room Weights", "How much each room is worth toward the score.") { open(RoomWeightsScreen(this)) },
            TextSetting(
                "Chat Channel",
                "Where the score gets sent, such as /pc or /ac. Blank keeps it local to you.",
                CHAT_CHANNEL_MAX_LENGTH,
                { ModConfig.get().chatAnnounceChannel.orEmpty() },
                { ModConfig.setChatAnnounceChannel(it.trim()) },
            ),
        ),
    )

    private fun aimAssistFeature() = GuiFeature(
        "Aim Assist",
        "Pulls your aim toward Odin's etherwarp waypoints while sneaking with an Aspect of the Void.",
        ModConfig::isAimAssistEnabled,
        ModConfig::setAimAssistEnabled,
        listOf(
            SliderSetting(
                "Strength",
                "How strongly your aim gets pulled.",
                MIN_AIM_ASSIST_STRENGTH,
                MAX_AIM_ASSIST_STRENGTH,
                "%",
                ModConfig::getAimAssistStrength,
                ModConfig::setAimAssistStrength,
            ),
            ToggleSetting(
                "Edit",
                "Left-click a waypoint in-world to change which part of it the assist aims at. Also /frost aim edit.",
                { AimAssist.editMode },
                { AimAssist.editMode = it },
            ),
        ),
    )

    private fun dungeonMapFeature(): GuiFeature {
        val specialRoomToggles = FunnyMapLayout.SPECIAL_ROOM_TYPES.map { (label, type) ->
            ToggleSetting(
                label,
                "Show this room type's name on the map.",
                { ModConfig.isRoomNameShown(type) },
                { ModConfig.setRoomNameShown(type, it) },
                indent = ClickGuiLayout.SUB_INDENT,
                height = ClickGuiLayout.SUB_ROW_HEIGHT,
            )
        }
        val fillRoomToggles = FunnyMapLayout.FILL_ROOM_TYPES.map { (label, type) ->
            ToggleSetting(
                label,
                "Fill this room type too once it's completed.",
                { ModConfig.isRoomTypeFilled(type) },
                { ModConfig.setRoomTypeFilled(type, it) },
                indent = ClickGuiLayout.SUB_INDENT,
                height = ClickGuiLayout.SUB_ROW_HEIGHT,
            )
        }
        return GuiFeature(
            "Dungeon Map",
            "A movable dungeon map on your HUD, showing the rooms that have been opened.",
            ModConfig::isFunnyMapEnabled,
            ModConfig::setFunnyMapEnabled,
            listOf(
                ActionSetting("Edit Map", "Drag the map to move it. Hold it and scroll to resize.") { open(FunnyMapMoveScreen(this)) },
                SelectorSetting(
                    "Map Style",
                    "How the map looks. Frost is Frost's own; NA, Odin and LCM look like NoammAddons', Odin's and LegitCatMod's dungeon maps. Works with Funny Map on or off.",
                    MapStyle.entries.map { it.label },
                    { ModConfig.getMapStyle().ordinal },
                    { ModConfig.setMapStyle(MapStyle.entries[it]) },
                ),
                SelectorSetting(
                    "NA Room Text",
                    "What the NA style writes on rooms, as NoammAddons' own Checkmark Style setting: checkmarks, secrets, names, or names and secrets.",
                    NaRoomText.entries.map { it.label },
                    { ModConfig.getNaRoomText().ordinal },
                    { ModConfig.setNaRoomText(NaRoomText.entries[it]) },
                ),
                ToggleSetting(
                    "Blood Rush Path",
                    "A line following the wither doors toward blood. Green reaches the blood door. Yellow goes as far as what's loaded so far.",
                    ModConfig::isBloodRushPathEnabled,
                    ModConfig::setBloodRushPathEnabled,
                ),
                ToggleSetting(
                    "Total Secrets",
                    "A line under the map showing the team's secrets out of the dungeon's total, worked out from the tab list. A ~ means it could still be off by one or two.",
                    ModConfig::isTotalSecretsEnabled,
                    ModConfig::setTotalSecretsEnabled,
                ),
                ToggleSetting(
                    "Limit Room Names",
                    "On: long names shrink to fit their rooms. Off: every name is the same size and can spill out. Frost style only - the others keep their own mod's text.",
                    ModConfig::isLimitRoomNamesEnabled,
                    ModConfig::setLimitRoomNamesEnabled,
                ),
                DropdownSetting("Special Room Names", "Choose which special rooms show their names.", specialRoomToggles),
                ToggleSetting(
                    "Fill Completed Rooms",
                    "Fills a room with a solid color once it's cleared and all its secrets are found. Frost style only.",
                    ModConfig::isFillCompletedRoomsEnabled,
                    ModConfig::setFillCompletedRoomsEnabled,
                ),
                DropdownSetting("Filled Room Types", "Choose which special rooms get filled as well.", fillRoomToggles),
                ColorSetting(
                    "Fill Color",
                    "The color completed rooms get filled with.",
                    DEFAULT_FILL_COMPLETED_COLOR,
                    ModConfig::getFillCompletedColor,
                    ModConfig::setFillCompletedColor,
                ),
                ColorSetting(
                    "Background Color",
                    "The color behind the map's rooms. Frost style only - the others use their own mod's background.",
                    DEFAULT_MAP_BACKGROUND_COLOR,
                    ModConfig::getMapBackgroundColor,
                    ModConfig::setMapBackgroundColor,
                ),
                SliderSetting(
                    "Player Head Size",
                    "How big player heads are on the map.",
                    MIN_MAP_ELEMENT_SIZE,
                    MAX_MAP_ELEMENT_SIZE,
                    "%",
                    ModConfig::getFunnyMapHeadSize,
                    ModConfig::setFunnyMapHeadSize,
                ),
                SliderSetting(
                    "Room Text Size",
                    "How big room names, secret counts and checkmarks are on the map.",
                    MIN_MAP_ELEMENT_SIZE,
                    MAX_MAP_ELEMENT_SIZE,
                    "%",
                    ModConfig::getFunnyMapTextSize,
                    ModConfig::setFunnyMapTextSize,
                ),
            ),
        )
    }

    private fun funnyMapFeature() = GuiFeature(
        "Funny Map",
        "Shows every room on Dungeon Map before it's been discovered, and turns on the features in here.",
        ModConfig::isFunnyModeEnabled,
        ModConfig::setFunnyModeEnabled,
        listOf(
            KeybindSetting(
                "Toggle Funny",
                "A key that turns Funny Map on and off. Click to set it, Esc to unbind.",
                ModConfig::getFunnyMapModeKey,
                ModConfig::setFunnyMapModeKey,
            ),
            ToggleSetting(
                "Star Mobs",
                "Marks where the starred mobs in your current room are on the map.",
                ModConfig::isStarMobsEnabled,
                ModConfig::setStarMobsEnabled,
            ),
            ToggleSetting(
                "Blood Rush Alert",
                "Big red text when the blood rush only goes through 2 rooms, not counting entrance, fairy and blood.",
                ModConfig::isBloodRushAlertEnabled,
                ModConfig::setBloodRushAlertEnabled,
            ),
            ToggleSetting(
                "Alert 3 Room",
                "Blood Rush Alert also warns of 3 rooms, in yellow.",
                ModConfig::isAlertThreeRoomEnabled,
                ModConfig::setAlertThreeRoomEnabled,
            ),
            ToggleSetting(
                "Alert on Possible",
                "Blood Rush Alert also warns when the part of the dungeon that hasn't loaded could make it that short. These end in a question mark.",
                ModConfig::isAlertOnPossibleEnabled,
                ModConfig::setAlertOnPossibleEnabled,
            ),
        ),
    )

    private fun starMobEspFeature() = GuiFeature(
        "Star Mob ESP",
        "Highlights every starred mob through walls.",
        ModConfig::isStarMobEspEnabled,
        ModConfig::setStarMobEspEnabled,
        listOf(
            ColorSetting(
                "Color",
                "The highlight's color.",
                ClickGuiLayout.opaque(DEFAULT_STAR_MOB_ESP_COLOR),
                ModConfig::getStarMobEspColor,
                ModConfig::setStarMobEspColor,
            ),
            ChoiceSetting(
                "Mode",
                "Outline draws just the edges of each box. Filled colors it in as well.",
                listOf("Outline", "Filled"),
                { if (ModConfig.isStarMobEspFilled()) 1 else 0 },
                { ModConfig.setStarMobEspFilled(it == 1) },
            ),
        ),
    )

    private fun m5AutoUltFeature() = GuiFeature(
        "M5 Auto Ult",
        "As Healer or Tank, presses your drop key once about 10 seconds after entering the M5 boss, to use your ultimate.",
        ModConfig::isM5AutoUltEnabled,
        ModConfig::setM5AutoUltEnabled,
        emptyList(),
    )

    /** Chat Regex's rows: one dropdown per rule, then a button to add another. Adding or deleting
     * a rule swaps the feature's rows, keeping the dropdowns of rules that are still there - and
     * so whether each is open. */
    private fun chatRegexFeature(): GuiFeature {
        val rows = java.util.IdentityHashMap<ChatRegexRule, GuiSetting>()
        val feature = GuiFeature(
            "Chat Regex",
            "Reacts to chat messages you choose: shows text on screen and plays a sound when one comes in. Add a rule for each message.",
            ModConfig::isChatRegexEnabled,
            ModConfig::setChatRegexEnabled,
            emptyList(),
        )
        fun rebuild(added: ChatRegexRule? = null) {
            val rules = ModConfig.getChatRegexRules()
            rows.keys.removeIf { row -> rules.none { it === row } }
            val addButton = ActionSetting("Add Rule", "Adds a new rule. Type the chat message it should react to into it.") {
                rebuild(ModConfig.addChatRegexRule())
            }
            feature.settings = rules.map { rule -> rows.getOrPut(rule) { chatRegexRule(rule, open = rule === added) { rebuild() } } } + addButton
        }
        rebuild()
        return feature
    }

    private fun chatRegexRule(rule: ChatRegexRule, open: Boolean, onDelete: () -> Unit): GuiSetting {
        fun changed() = ModConfig.saveCurrent()
        val children = listOf(
            ToggleSetting("Enabled", "Turns this rule on or off.", { rule.enabled }, { rule.enabled = it; changed() }),
            TextSetting(
                "Message",
                "The chat message to react to. Matches anywhere in a line, ignoring case and color codes - like \"A Wither Key was picked up\".",
                CHAT_REGEX_MAX_LENGTH,
                { rule.pattern },
                { rule.pattern = it; changed() },
            ),
            ToggleSetting(
                "Regex",
                "Treats Message as a regular expression, like ^\\[BOSS\\] .+: or (\\w+) has obtained (\\w+) Key!. Its groups can go in Text as \$1, \$2...",
                { rule.regex },
                { rule.regex = it; changed() },
            ),
            ToggleSetting("Match Case", "Only matches with the same capitals.", { rule.matchCase }, { rule.matchCase = it; changed() }),
            ToggleSetting("Show Text", "Shows text on screen when the message comes in.", { rule.showText }, { rule.showText = it; changed() }),
            TextSetting(
                "Text",
                "What to show. Blank shows the chat message itself. \$1, \$2... put in a regex's groups, and & codes like &c work.",
                CHAT_REGEX_MAX_LENGTH,
                { rule.text },
                { rule.text = it; changed() },
            ),
            ColorSetting("Color", "The text's color.", 0xFFFFFFFF.toInt(), { rule.color }, { rule.color = it }),
            SliderSetting("Size", "How big the text is.", MIN_KEY_NOTIFICATION_SIZE, MAX_KEY_NOTIFICATION_SIZE, "%", { rule.size }, { rule.size = it; changed() }),
            SliderSetting(
                "Duration",
                "How long the text stays on screen.",
                MIN_KEY_NOTIFICATION_SECONDS,
                MAX_KEY_NOTIFICATION_SECONDS,
                "s",
                { rule.seconds },
                { rule.seconds = it; changed() },
            ),
            ActionSetting("Move Text", "Drag the text to move it. Hold it and scroll to resize.") {
                open(
                    TextMoveScreen(this, ChatRegex.sampleText(rule), rule.color, rule.x, rule.y, rule.size) { x, y, size ->
                        rule.x = x
                        rule.y = y
                        rule.size = size
                        changed()
                    },
                )
            },
            ToggleSetting("Play Sound", "Plays a sound when the message comes in.", { rule.playSound }, { rule.playSound = it; changed() }),
            SoundSetting(
                "Sound",
                "Which Minecraft sound to play. Click one in the list to choose it and hear it.",
                { Sounds.all },
                { rule.sound },
                { rule.sound = it; changed() },
            ) { Sounds.play(rule.sound, rule.volume, rule.pitch) },
            SliderSetting("Volume", "How loud the sound is.", 0, MAX_SOUND_VOLUME, "%", { rule.volume }, { rule.volume = it; changed() }),
            SliderSetting("Pitch", "How high the sound is. 100% is its normal pitch.", MIN_SOUND_PITCH, MAX_SOUND_PITCH, "%", { rule.pitch }, { rule.pitch = it; changed() }),
            ActionSetting("Test", "Shows the text and plays the sound as if the message had come in.") { ChatRegex.test(rule) },
            ActionSetting("Delete Rule", "Removes this rule.") {
                ModConfig.removeChatRegexRule(rule)
                onDelete()
            },
        )
        return DropdownSetting("Rule", "A chat message to react to, and what to do when it comes in.", children, open) {
            val number = ModConfig.getChatRegexRules().indexOfFirst { it === rule } + 1
            val message = when {
                rule.pattern.isBlank() -> "(no message yet)"
                ChatRegex.isBroken(rule) -> "(invalid regex)"
                rule.pattern.length > RULE_LABEL_LENGTH -> rule.pattern.take(RULE_LABEL_LENGTH) + ".."
                else -> rule.pattern
            }
            "$number. $message"
        }
    }

    private fun keyPickupFeature() = GuiFeature(
        "Key Pickup",
        "When a wither or blood key is picked up, shows it on screen and plays a sound. Each can be turned off in here.",
        ModConfig::isKeyPickupEnabled,
        ModConfig::setKeyPickupEnabled,
        listOf(
            ToggleSetting(
                "Pickup Notification",
                "Shows \"Wither Key Picked Up\" or \"Blood Key Picked Up\" on screen.",
                ModConfig::isKeyNotificationEnabled,
                ModConfig::setKeyNotificationEnabled,
            ),
            ActionSetting("Move Notification", "Drag the notification to move it. Hold it and scroll to resize.") {
                open(
                    TextMoveScreen(
                        this,
                        KeyPickup.text(DungeonKey.WITHER),
                        ModConfig.getWitherKeyColor(),
                        ModConfig.getKeyNotificationX(),
                        ModConfig.getKeyNotificationY(),
                        ModConfig.getKeyNotificationSize(),
                    ) { x, y, size ->
                        ModConfig.setKeyNotificationPosition(x, y)
                        ModConfig.setKeyNotificationSize(size)
                    },
                )
            },
            SliderSetting(
                "Size",
                "How big the notification is.",
                MIN_KEY_NOTIFICATION_SIZE,
                MAX_KEY_NOTIFICATION_SIZE,
                "%",
                ModConfig::getKeyNotificationSize,
                ModConfig::setKeyNotificationSize,
            ),
            SliderSetting(
                "Duration",
                "How long the notification stays on screen.",
                MIN_KEY_NOTIFICATION_SECONDS,
                MAX_KEY_NOTIFICATION_SECONDS,
                "s",
                ModConfig::getKeyNotificationSeconds,
                ModConfig::setKeyNotificationSeconds,
            ),
            ColorSetting(
                "Wither Key Color",
                "The color of \"Wither Key Picked Up\".",
                DEFAULT_WITHER_KEY_COLOR,
                ModConfig::getWitherKeyColor,
                ModConfig::setWitherKeyColor,
            ),
            ColorSetting(
                "Blood Key Color",
                "The color of \"Blood Key Picked Up\".",
                DEFAULT_BLOOD_KEY_COLOR,
                ModConfig::getBloodKeyColor,
                ModConfig::setBloodKeyColor,
            ),
            ToggleSetting(
                "Pickup Sound",
                "Plays a sound when a key is picked up.",
                ModConfig::isKeySoundEnabled,
                ModConfig::setKeySoundEnabled,
            ),
            SoundSetting(
                "Sound",
                "Which Minecraft sound to play. Click one in the list to choose it and hear it; scroll the list or search it.",
                { Sounds.all },
                ModConfig::getKeySound,
                ModConfig::setKeySound,
                KeyPickup::playSound,
            ),
            SliderSetting("Volume", "How loud the sound is.", 0, MAX_SOUND_VOLUME, "%", ModConfig::getKeySoundVolume, ModConfig::setKeySoundVolume),
            SliderSetting(
                "Pitch",
                "How high the sound is. 100% is its normal pitch.",
                MIN_SOUND_PITCH,
                MAX_SOUND_PITCH,
                "%",
                ModConfig::getKeySoundPitch,
                ModConfig::setKeySoundPitch,
            ),
            ActionSetting("Play Sound", "Plays the sound as it's set up now.") { KeyPickup.playSound() },
        ),
    )

    private fun m5ShittersFeature() = GuiFeature(
        "M5 Shitters",
        "Shows the names of players on the M5 Shitters list in red, in chat, Party Finder and the tab list. Click a party in Party Finder, or a red name in chat, to see why they're on it.",
        ModConfig::isM5ShittersEnabled,
        ModConfig::setM5ShittersEnabled,
        listOf(
            TextSetting(
                "Database",
                "A link to the list, by username or UUID: the M5 Shitters database to start with, or a Google Sheet, Pastebin, GitHub file, or any JSON, CSV or text. Clear it to use only players added with /frost shitters add.",
                SHITTERS_URL_MAX_LENGTH,
                ModConfig::getM5ShittersUrl,
            ) { url ->
                ModConfig.setM5ShittersUrl(url)
                M5Shitters.onUrlEdited()
            },
            ActionSetting("Reload List", "Fetches the list again now. It also updates on its own every 15 minutes.") { M5Shitters.reload() },
        ),
    )

    private fun leapFixFeature() = GuiFeature(
        "Leap Fix",
        "After a spirit leap, turns you to face the same way as the player you leaped to.",
        ModConfig::isLeapFixEnabled,
        ModConfig::setLeapFixEnabled,
        emptyList(),
    )

    private fun glassDoorsFeature() = GuiFeature(
        "Glass Doors",
        "Turns the door out of the entrance room into glass.",
        ModConfig::isGlassDoorsEnabled,
        ModConfig::setGlassDoorsEnabled,
        listOf(
            ToggleSetting(
                "Wither Doors",
                "Turns wither doors into glass too.",
                ModConfig::isGlassWitherDoorsEnabled,
                ModConfig::setGlassWitherDoorsEnabled,
            ),
        ),
    )

    private fun fairyDoorEspFeature() = GuiFeature(
        "Fairy Door ESP",
        "Highlights the wither door out of the fairy room through walls, so you can see which way to go before you enter.",
        ModConfig::isFairyDoorEspEnabled,
        ModConfig::setFairyDoorEspEnabled,
        emptyList(),
    )

    private fun croesusFeature() = GuiFeature(
        "Croesus",
        "Tracks your unopened Croesus chests, and warns you when you reach 55. The count updates in the Dungeon Hub.",
        ModConfig::isCroesusEnabled,
        ModConfig::setCroesusEnabled,
        listOf(
            ToggleSetting(
                "Chest Count HUD",
                "Shows how many unopened chests you have on your HUD.",
                ModConfig::isCroesusHudEnabled,
                ModConfig::setCroesusHudEnabled,
            ),
            ActionSetting("Move HUD", "Drag the chest count to move it.") { open(CroesusHudMoveScreen(this)) },
        ),
    )

    companion object {
        private const val SEARCH_WIDTH = 120
        private const val SEARCH_HEIGHT = 18
        private const val SEARCH_TEXT_HEIGHT = 10
        private const val SEARCH_TEXT_INSET = 4
        private const val SEARCH_BOTTOM_MARGIN = 30
        private const val SEARCH_MAX_LENGTH = 18
        private const val SEARCH_PLACEHOLDER = "Search..."

        private const val BACKDROP_TOP = 0x64000000
        private const val BACKDROP_BOTTOM = 0x96000000.toInt()
        private const val MAX_FRAME_MILLIS = 100f

        private const val TOOLTIP_DELAY_NANOS = 500_000_000L
        private const val TOOLTIP_WIDTH = 180
        private const val TOOLTIP_PADDING = 5
        private const val TOOLTIP_CURSOR_GAP = 10

        private const val SPOTIFY_ID_MAX_LENGTH = 64
        private const val CHAT_CHANNEL_MAX_LENGTH = 16
        private const val SHITTERS_URL_MAX_LENGTH = 512
        private const val CHAT_REGEX_MAX_LENGTH = 256
        private const val RULE_LABEL_LENGTH = 16
    }
}
