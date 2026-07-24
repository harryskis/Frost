package com.frost.gui

import com.frost.dungeon.RoomDatabase
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/**
 * The /frost roomweights landing menu - one button per room category
 * ([RoomDatabase.CATEGORIES]: the 6 shapes plus the special Misc bucket for
 * puzzle/entrance/fairy/blood/trap rooms), each opening [RoomWeightCategoryScreen].
 */
class RoomWeightsScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Room Weights")) {
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun init() {
        val buttonWidth = 150
        val buttonHeight = 20
        val spacing = 6
        val categories = RoomDatabase.CATEGORIES
        val rowCount = categories.size + 1 // +1 for "Done"
        val contentHeight = rowCount * buttonHeight + (rowCount - 1) * spacing

        val panelPadding = 16
        val wordmarkBottom = FrostTheme.sectionHeaderContentStartY(font)
        panelWidth = buttonWidth + panelPadding * 2
        panelHeight = contentHeight + panelPadding * 2
        panelX = (width - panelWidth) / 2
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        var y = panelY + panelPadding
        val x = (width - buttonWidth) / 2

        for (category in RoomDatabase.CATEGORIES) {
            addRenderableWidget(
                Button.builder(Component.literal(RoomDatabase.categoryLabel(category))) {
                    minecraft?.setScreen(RoomWeightCategoryScreen(this, category))
                }
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
        FrostTheme.drawSectionHeader(context, font, width, "Room Weights", mouseX, mouseY)
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
