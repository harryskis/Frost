package com.frost.gui

import com.frost.config.ModConfig
import com.frost.util.BrowserUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/** Sets/updates the Hypixel API key (from developer.hypixel.net) from a text field,
 * instead of the /frost apikey <key> chat command - opened directly from the main menu's
 * "Developer" button (there's only ever this one thing here, so it skips the extra landing
 * screen Dungeons/QoL/Misc each have). */
class ApiKeyScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Developer")) {
    private lateinit var keyField: EditBox

    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun init() {
        val fieldWidth = 220
        val fieldHeight = 20
        val panelPadding = 16
        val contentHeight = fieldHeight + 15 + 30 + 22 // field + hint gap + button row + link row

        panelWidth = fieldWidth + panelPadding * 2
        panelHeight = contentHeight + panelPadding * 2 + 15 // extra room for the hint label above the field
        panelX = (width - panelWidth) / 2
        val wordmarkBottom = FrostTheme.sectionHeaderContentStartY(font)
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        val y = panelY + panelPadding + 15
        keyField = EditBox(font, (width - fieldWidth) / 2, y, fieldWidth, fieldHeight, Component.literal("API key"))
        keyField.setMaxLength(64)
        keyField.value = ModConfig.get().hypixelApiKey.orEmpty()
        addRenderableWidget(keyField)
        setInitialFocus(keyField)

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
        addRenderableWidget(
            Button.builder(Component.literal("Open developer.hypixel.net")) { BrowserUtils.tryOpenBrowser("https://developer.hypixel.net/") }
                .bounds((width - fieldWidth) / 2, y + 56, fieldWidth, 20)
                .build(),
        )
    }

    private fun save() {
        val key = keyField.value.trim()
        if (key.isNotEmpty()) {
            ModConfig.setApiKey(key)
        }
        onClose()
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        FrostTheme.drawPanel(context, panelX, panelY, panelWidth, panelHeight)
        super.extractRenderState(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, font, width, "Developer", mouseX, mouseY)
        context.centeredText(
            font,
            "Hypixel API key (get one below)",
            width / 2,
            keyField.y - 15,
            FrostTheme.HINT,
        )
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
