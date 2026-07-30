package com.frost.gui

import com.frost.config.ModConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/**
 * The /frost menu's "Quality of Life" landing screen - Better Friends List and Keybinds are
 * both [FeatureButton]s: left-click toggles the feature on/off, right-click opens its
 * settings screen regardless of on/off state.
 */
class QualityOfLifeScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Quality of Life")) {
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun init() {
        val buttonWidth = 150
        val buttonHeight = 20
        val spacing = 6
        val rowCount = 3 // Better Friends List + Keybinds + Done
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
                "Better Friends List",
                ModConfig.isBetterFriendsListEnabled(),
                onToggle = { ModConfig.setBetterFriendsListEnabled(it) },
                onOpenSettings = { minecraft?.setScreen(BetterFriendsListScreen(this)) },
            ),
        )
        y += buttonHeight + spacing

        addRenderableWidget(
            FeatureButton(
                x, y, buttonWidth, buttonHeight,
                "Keybinds",
                ModConfig.isKeybindsEnabled(),
                onToggle = { ModConfig.setKeybindsEnabled(it) },
                onOpenSettings = { minecraft?.setScreen(KeybindsScreen(this)) },
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
        FrostTheme.drawSectionHeader(context, font, width, "Quality of Life", mouseX, mouseY)
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
