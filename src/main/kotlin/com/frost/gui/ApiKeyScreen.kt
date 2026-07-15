package com.frost.gui

import com.frost.config.ModConfig
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text

/** Sets/updates the Hypixel API key (from developer.hypixel.net) from a text field,
 * instead of the /frost apikey <key> chat command. */
class ApiKeyScreen(private val parent: Screen?) : Screen(Text.literal("Frost - API Key")) {
    private lateinit var keyField: TextFieldWidget

    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun init() {
        val fieldWidth = 220
        val fieldHeight = 20
        val panelPadding = 16
        val contentHeight = fieldHeight + 15 + 30 // field + hint gap + button row

        panelWidth = fieldWidth + panelPadding * 2
        panelHeight = contentHeight + panelPadding * 2 + 15 // extra room for the hint label above the field
        panelX = (width - panelWidth) / 2
        val wordmarkBottom = FrostTheme.sectionHeaderContentStartY(textRenderer)
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        val y = panelY + panelPadding + 15
        keyField = TextFieldWidget(textRenderer, (width - fieldWidth) / 2, y, fieldWidth, fieldHeight, Text.literal("API key"))
        keyField.setMaxLength(64)
        keyField.text = ModConfig.get().hypixelApiKey.orEmpty()
        addDrawableChild(keyField)
        setInitialFocus(keyField)

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
        val key = keyField.text.trim()
        if (key.isNotEmpty()) {
            ModConfig.setApiKey(key)
        }
        close()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        FrostTheme.drawPanel(context, panelX, panelY, panelWidth, panelHeight)
        super.render(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, textRenderer, width, "API Key", mouseX, mouseY)
        context.drawCenteredTextWithShadow(
            textRenderer,
            "Hypixel API key (from developer.hypixel.net)",
            width / 2,
            keyField.y - 15,
            FrostTheme.HINT,
        )
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun shouldPause(): Boolean = false
}
