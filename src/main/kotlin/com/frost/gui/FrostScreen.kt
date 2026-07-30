package com.frost.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/**
 * The /frost command's main menu - just a vertical list of buttons, each opening a
 * sub-screen. Kept as a simple (label, screen-factory) list so adding another entry later
 * (the whole point of this menu existing) is a one-line change, not a layout rewrite.
 *
 * Every actual feature lives one level down, under Dungeons/Quality of Life/Misc, as a
 * [FeatureButton] (left-click on/off, right-click for its settings) - Developer is the one
 * exception, since API key entry isn't a togglable feature, just persistent config.
 *
 * Friends' roster/invite/recruit behavior stays chat-only (see
 * com.frost.friends.FriendsChatRelay) - "Better Friends List" under Quality of Life only
 * turns that on/off and links to its settings, it's not a chat replacement itself.
 */
class FrostScreen(private val parent: Screen?) : Screen(Component.literal("Frost")) {
    private val entries: List<Pair<String, (Screen?) -> Screen>> = listOf(
        "Dungeons" to { p: Screen? -> DungeonsScreen(p) },
        "Quality of Life" to { p: Screen? -> QualityOfLifeScreen(p) },
        "Misc" to { p: Screen? -> MiscScreen(p) },
        "Developer" to { p: Screen? -> ApiKeyScreen(p) },
    )

    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun init() {
        val buttonWidth = 150
        val buttonHeight = 20
        val spacing = 6
        val rowCount = entries.size + 1 // +1 for "Done"
        val contentHeight = rowCount * buttonHeight + (rowCount - 1) * spacing

        val panelPadding = 16
        val wordmarkBottom = FrostTheme.wordmarkContentStartY(font)
        panelWidth = buttonWidth + panelPadding * 2
        panelHeight = contentHeight + panelPadding * 2
        panelX = (width - panelWidth) / 2
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        var y = panelY + panelPadding
        val x = (width - buttonWidth) / 2

        for ((label, openScreen) in entries) {
            addRenderableWidget(
                Button.builder(Component.literal(label)) { minecraft?.setScreen(openScreen(this)) }
                    .bounds(x, y, buttonWidth, buttonHeight)
                    .build(),
            )
            y += buttonHeight + spacing
        }

        addRenderableWidget(
            Button.builder(Component.literal("Done")) { onClose() }
                .bounds(x, y, buttonWidth, buttonHeight)
                .build(),
        )
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        FrostTheme.drawPanel(context, panelX, panelY, panelWidth, panelHeight)
        super.extractRenderState(context, mouseX, mouseY, delta)
        FrostTheme.drawWordmark(context, font, width, mouseX, mouseY)
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
