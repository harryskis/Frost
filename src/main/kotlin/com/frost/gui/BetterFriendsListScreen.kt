package com.frost.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/**
 * Settings landing page for the "Better Friends List" feature (opened by right-clicking its
 * [FeatureButton] on [QualityOfLifeScreen]) - Tags (create/color/hotkey/assign friends).
 * Recruit lives under the Dungeons tab instead (see [com.frost.gui.DungeonsScreen]) - it's a
 * separate feature from Better Friends List, even though its [Recruit] button surfaces
 * inside /fl.
 */
class BetterFriendsListScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Better Friends List")) {
    private val entries: List<Pair<String, (Screen?) -> Screen>> = listOf(
        "Tags" to { p: Screen? -> FriendTagsScreen(p) },
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
        val wordmarkBottom = FrostTheme.sectionHeaderContentStartY(font)
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
        FrostTheme.drawSectionHeader(context, font, width, "Better Friends List", mouseX, mouseY)
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
