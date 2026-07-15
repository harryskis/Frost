package com.frost.gui

import com.frost.dungeon.RoomDatabase
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

/**
 * The /frost roomweights landing menu - one button per room category
 * ([RoomDatabase.CATEGORIES]: the 6 shapes plus the special Misc bucket for
 * puzzle/entrance/fairy/blood/trap rooms), each opening [RoomWeightCategoryScreen].
 */
class RoomWeightsScreen(private val parent: Screen?) : Screen(Text.literal("Frost - Room Weights")) {
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
        val wordmarkBottom = FrostTheme.sectionHeaderContentStartY(textRenderer)
        panelWidth = buttonWidth + panelPadding * 2
        panelHeight = contentHeight + panelPadding * 2
        panelX = (width - panelWidth) / 2
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        var y = panelY + panelPadding
        val x = (width - buttonWidth) / 2

        for (category in RoomDatabase.CATEGORIES) {
            addDrawableChild(
                ButtonWidget.builder(Text.literal(RoomDatabase.categoryLabel(category))) {
                    client?.setScreen(RoomWeightCategoryScreen(this, category))
                }
                    .dimensions(x, y, buttonWidth, buttonHeight)
                    .build(),
            )
            y += buttonHeight + spacing
        }

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Done")) { close() }
                .dimensions(x, y, buttonWidth, buttonHeight)
                .build(),
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        FrostTheme.drawPanel(context, panelX, panelY, panelWidth, panelHeight)
        super.render(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, textRenderer, width, "Room Weights", mouseX, mouseY)
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun shouldPause(): Boolean = false
}
