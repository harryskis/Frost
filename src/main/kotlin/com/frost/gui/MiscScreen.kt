package com.frost.gui

import com.frost.config.ModConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/**
 * The /frost menu's "Misc" landing screen - home for features that don't fit Dungeons or
 * Quality of Life. Spotify Link is a [FeatureButton]: left-click turns the `/frost spotify`
 * commands on/off, right-click opens [SpotifyLinkScreen] regardless of on/off state.
 */
class MiscScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Misc")) {
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun init() {
        val buttonWidth = 150
        val buttonHeight = 20
        val spacing = 6
        val rowCount = 2 // Spotify Link + Done
        val contentHeight = rowCount * buttonHeight + (rowCount - 1) * spacing

        val panelPadding = 16
        val wordmarkBottom = FrostTheme.sectionHeaderContentStartY(font)
        panelWidth = buttonWidth + panelPadding * 2
        panelHeight = contentHeight + panelPadding * 2
        panelX = (width - panelWidth) / 2
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        var y = panelY + panelPadding
        val x = (width - buttonWidth) / 2

        addRenderableWidget(
            FeatureButton(
                x, y, buttonWidth, buttonHeight,
                "Spotify Link",
                ModConfig.isSpotifyLinkEnabled(),
                onToggle = { ModConfig.setSpotifyLinkEnabled(it) },
                onOpenSettings = { minecraft.gui.setScreen(SpotifyLinkScreen(this)) },
            ),
        )
        y += buttonHeight + spacing

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
        FrostTheme.drawSectionHeader(context, font, width, "Misc", mouseX, mouseY)
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
