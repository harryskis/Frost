package com.frost.gui

import com.frost.config.ModConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/**
 * The /frost menu's "Dungeons" landing screen - a home for every dungeon-related feature so
 * the main [FrostScreen] menu doesn't have to grow a new top-level button for each one. Both
 * Contributions and Recruit are [FeatureButton]s - left-click turns the feature on or off,
 * right-click opens its settings screen regardless of on/off state. Recruit (the floor +
 * filled-classes state used to build each friend's invite message, plus the Party Finder
 * auto-fill) lives here rather than under Better Friends List - it's a dungeon-team concern,
 * not a friends-list one, even though its [Recruit] button surfaces inside /fl. Aim Assist
 * (a light pull toward Odin's etherwarp waypoints) opens [AimAssistScreen] for its strength.
 */
class DungeonsScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Dungeons")) {
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun init() {
        val buttonWidth = 150
        val buttonHeight = 20
        val spacing = 6
        val rowCount = 4 // Contributions + Recruit + Aim Assist + Done
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
                "Contributions",
                ModConfig.isDungeonContributionsEnabled(),
                onToggle = { ModConfig.setDungeonContributionsEnabled(it) },
                onOpenSettings = { minecraft.gui.setScreen(DungeonContributionsScreen(this)) },
            ),
        )
        y += buttonHeight + spacing

        addRenderableWidget(
            FeatureButton(
                x, y, buttonWidth, buttonHeight,
                "Recruit",
                ModConfig.isRecruitEnabled(),
                onToggle = { ModConfig.setRecruitEnabled(it) },
                onOpenSettings = { minecraft.gui.setScreen(RecruitScreen(this)) },
            ),
        )
        y += buttonHeight + spacing

        addRenderableWidget(
            FeatureButton(
                x, y, buttonWidth, buttonHeight,
                "Aim Assist",
                ModConfig.isAimAssistEnabled(),
                onToggle = { ModConfig.setAimAssistEnabled(it) },
                onOpenSettings = { minecraft.gui.setScreen(AimAssistScreen(this)) },
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
        FrostTheme.drawSectionHeader(context, font, width, "Dungeons", mouseX, mouseY)
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
