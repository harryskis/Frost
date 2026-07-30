package com.frost.gui

import com.frost.config.ModConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * Settings screen for the "Spotify Link" feature (opened by right-clicking its
 * [FeatureButton] on [MiscScreen]) - paste your own Spotify app's Client ID here, then run
 * `/frost spotify login` in chat to actually connect (the PKCE browser flow isn't something
 * a menu button can drive - it needs a real browser window).
 */
class SpotifyLinkScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Spotify Link")) {
    private lateinit var clientIdField: EditBox

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
        clientIdField = EditBox(font, (width - fieldWidth) / 2, y, fieldWidth, fieldHeight, Component.literal("Client ID"))
        clientIdField.setMaxLength(64)
        clientIdField.value = ModConfig.get().spotifyClientId.orEmpty()
        addRenderableWidget(clientIdField)
        setInitialFocus(clientIdField)

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
        val id = clientIdField.value.trim()
        if (id.isNotEmpty()) ModConfig.setSpotifyClientId(id)
        onClose()
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        FrostTheme.drawPanel(context, panelX, panelY, panelWidth, panelHeight)
        super.extractRenderState(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, font, width, "Spotify Link", mouseX, mouseY)
        context.centeredText(
            font,
            "Then run /frost spotify login in chat",
            width / 2,
            clientIdField.y - 15,
            FrostTheme.HINT,
        )
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
