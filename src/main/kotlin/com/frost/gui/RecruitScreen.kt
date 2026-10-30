package com.frost.gui

import com.frost.dungeon.DungeonFloor
import com.frost.friends.DungeonClass
import com.frost.friends.FriendsChatRelay
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * /frost recruit's screen - the floor + "which classes are already filled" state used to
 * build each friend's invite message (see [FriendsChatRelay]/[com.frost.friends.PartyInviteMessage]).
 * Every change here applies immediately (no separate Save step): toggling a class updates
 * [FriendsChatRelay] on the spot, and the floor field re-parses and applies on every
 * keystroke, silently keeping the last valid value if what's currently typed isn't one yet
 * (e.g. mid-typing "m") rather than erroring on every partial keystroke.
 *
 * Pre-fills from whatever's already set in [FriendsChatRelay], so reopening this after a
 * party-join auto-fill (once that's wired up) shows the current real state, not blank.
 */
class RecruitScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Recruit")) {
    private var floorText: String = FriendsChatRelay.currentFloorLabel() ?: ""
    private val filledClasses: MutableSet<DungeonClass> = FriendsChatRelay.currentFilledClasses().toMutableSet()

    private lateinit var floorField: EditBox
    private val classButtons: MutableMap<DungeonClass, Button> = mutableMapOf()

    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun init() {
        classButtons.clear()
        val panelPadding = 16
        val fieldWidth = 60
        val fieldHeight = 20
        val classButtonWidth = 90
        val classButtonHeight = 20
        val spacing = 6

        val contentHeight = fieldHeight + 10 + DungeonClass.entries.size * (classButtonHeight + spacing) + 10 + 20
        panelWidth = maxOf(fieldWidth, classButtonWidth) + panelPadding * 2 + 40
        panelHeight = contentHeight + panelPadding * 2 + 15 // + hint label above the field

        val wordmarkBottom = FrostTheme.sectionHeaderContentStartY(font)
        panelX = (width - panelWidth) / 2
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        var y = panelY + panelPadding + 15
        floorField = EditBox(font, (width - fieldWidth) / 2, y, fieldWidth, fieldHeight, Component.literal("Floor"))
        floorField.setMaxLength(3)
        floorField.value = floorText
        floorField.setResponder {
            floorText = it
            applyState()
        }
        addRenderableWidget(floorField)
        setInitialFocus(floorField)
        y += fieldHeight + 10

        for (cls in DungeonClass.entries) {
            val button = Button.builder(classLabel(cls)) { toggleClass(cls) }
                .bounds((width - classButtonWidth) / 2, y, classButtonWidth, classButtonHeight)
                .build()
            classButtons[cls] = button
            addRenderableWidget(button)
            y += classButtonHeight + spacing
        }
        y += 4

        addRenderableWidget(
            Button.builder(Component.literal("Done")) { onClose() }
                .bounds((width - 150) / 2, y, 150, 20)
                .build(),
        )
    }

    private fun classLabel(cls: DungeonClass): Component {
        val marker = if (cls in filledClasses) "[x] " else "[ ] "
        return Component.literal(marker + cls.abbreviation)
    }

    private fun toggleClass(cls: DungeonClass) {
        if (!filledClasses.add(cls)) filledClasses.remove(cls)
        classButtons[cls]?.message = classLabel(cls)
        applyState()
    }

    /** Re-applies the current floor + filled classes to [FriendsChatRelay] - a no-op on the
     * floor if [floorText] isn't currently a valid one (keeps whatever was last valid,
     * rather than clearing it out mid-edit). Toggling a class before any valid floor has
     * ever been entered just updates the local UI state - there's nothing to apply to
     * FriendsChatRelay yet, since a recruit state without a floor isn't meaningful, but
     * those toggles are picked up as soon as a valid floor is typed. */
    private fun applyState() {
        val floor = DungeonFloor.parse(floorText.trim())?.label ?: FriendsChatRelay.currentFloorLabel() ?: return
        FriendsChatRelay.setRecruitState(floor, filledClasses)
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        FrostTheme.drawPanel(context, panelX, panelY, panelWidth, panelHeight)
        super.extractRenderState(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, font, width, "Recruit", mouseX, mouseY)
        context.centeredText(font, "Floor", width / 2, floorField.y - 15, FrostTheme.HINT)
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
