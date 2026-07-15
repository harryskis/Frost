package com.frost.gui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

/**
 * The /frost command's main menu - just a vertical list of buttons, each opening a
 * sub-screen. Kept as a simple (label, screen-factory) list so adding another entry later
 * (the whole point of this menu existing) is a one-line change, not a layout rewrite.
 */
class FrostScreen(private val parent: Screen?) : Screen(Text.literal("Frost")) {
    private val entries: List<Pair<String, (Screen?) -> Screen>> = listOf(
        "API Key" to { p: Screen? -> ApiKeyScreen(p) },
        "Room Weights" to { p: Screen? -> RoomWeightsScreen(p) },
        "Chat Channel" to { p: Screen? -> ChatChannelScreen(p) },
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
        val wordmarkBottom = FrostTheme.wordmarkContentStartY(textRenderer)
        panelWidth = buttonWidth + panelPadding * 2
        panelHeight = contentHeight + panelPadding * 2
        panelX = (width - panelWidth) / 2
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        var y = panelY + panelPadding
        val x = (width - buttonWidth) / 2

        for ((label, openScreen) in entries) {
            addDrawableChild(
                ButtonWidget.builder(Text.literal(label)) { client?.setScreen(openScreen(this)) }
                    .dimensions(x, y, buttonWidth, buttonHeight)
                    .build(),
            )
            y += buttonHeight + spacing
        }

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Done")) { close() }
                .dimensions(x, y, buttonWidth, buttonHeight)
                .build(),
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        FrostTheme.drawPanel(context, panelX, panelY, panelWidth, panelHeight)
        super.render(context, mouseX, mouseY, delta)
        FrostTheme.drawWordmark(context, textRenderer, width, mouseX, mouseY)
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun shouldPause(): Boolean = false
}
