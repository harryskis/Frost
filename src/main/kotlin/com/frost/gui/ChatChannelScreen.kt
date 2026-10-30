package com.frost.gui

import com.frost.config.ModConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * Configures which chat channel (if any) the weighted-score line gets actually SENT to as
 * a real message - e.g. "/pc" for party chat, "/ac" for all chat - instead of only being
 * displayed locally. Left blank, the announcement stays local-only: this mod never guesses
 * or defaults to broadcasting into whatever chat channel happens to be active.
 */
class ChatChannelScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Chat Channel")) {
    private lateinit var channelField: EditBox

    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun init() {
        val fieldWidth = 220
        val fieldHeight = 20
        val panelPadding = 16
        val contentHeight = fieldHeight + 30 // field + button row

        panelWidth = fieldWidth + panelPadding * 2
        panelHeight = contentHeight + panelPadding * 2 + 15 // extra room for the hint label above the field
        panelX = (width - panelWidth) / 2
        val wordmarkBottom = FrostTheme.sectionHeaderContentStartY(font)
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        val y = panelY + panelPadding + 15
        channelField = EditBox(font, (width - fieldWidth) / 2, y, fieldWidth, fieldHeight, Component.literal("Channel"))
        channelField.setMaxLength(16)
        channelField.value = ModConfig.get().chatAnnounceChannel.orEmpty()
        addRenderableWidget(channelField)
        setInitialFocus(channelField)

        addRenderableWidget(
            Button.builder(Component.literal("Save")) { save() }
                .bounds((width - 200) / 2, y + 30, 95, 20)
                .build(),
        )
        addRenderableWidget(
            Button.builder(Component.literal("Back")) { onClose() }
                .bounds((width - 200) / 2 + 105, y + 30, 95, 20)
                .build(),
        )
    }

    private fun save() {
        ModConfig.setChatAnnounceChannel(channelField.value.trim())
        onClose()
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        FrostTheme.drawPanel(context, panelX, panelY, panelWidth, panelHeight)
        super.extractRenderState(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, font, width, "Chat Channel", mouseX, mouseY)
        context.centeredText(
            font,
            "/pc party, /ac all chat - blank = local only",
            width / 2,
            channelField.y - 15,
            FrostTheme.HINT,
        )
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
