package com.frost.gui

import com.frost.config.ModConfig
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text

/**
 * Configures which chat channel (if any) the weighted-score line gets actually SENT to as
 * a real message - e.g. "/pc" for party chat, "/ac" for all chat - instead of only being
 * displayed locally. Left blank, the announcement stays local-only: this mod never guesses
 * or defaults to broadcasting into whatever chat channel happens to be active.
 */
class ChatChannelScreen(private val parent: Screen?) : Screen(Text.literal("Frost - Chat Channel")) {
    private lateinit var channelField: TextFieldWidget

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
        val wordmarkBottom = FrostTheme.sectionHeaderContentStartY(textRenderer)
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        val y = panelY + panelPadding + 15
        channelField = TextFieldWidget(textRenderer, (width - fieldWidth) / 2, y, fieldWidth, fieldHeight, Text.literal("Channel"))
        channelField.setMaxLength(16)
        channelField.text = ModConfig.get().chatAnnounceChannel.orEmpty()
        addDrawableChild(channelField)
        setInitialFocus(channelField)

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Save")) { save() }
                .dimensions((width - 200) / 2, y + 30, 95, 20)
                .build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Back")) { close() }
                .dimensions((width - 200) / 2 + 105, y + 30, 95, 20)
                .build(),
        )
    }

    private fun save() {
        ModConfig.setChatAnnounceChannel(channelField.text.trim())
        close()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        FrostTheme.drawPanel(context, panelX, panelY, panelWidth, panelHeight)
        super.render(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, textRenderer, width, "Chat Channel", mouseX, mouseY)
        context.drawCenteredTextWithShadow(
            textRenderer,
            "/pc party, /ac all chat - blank = local only",
            width / 2,
            channelField.y - 15,
            FrostTheme.HINT,
        )
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun shouldPause(): Boolean = false
}
