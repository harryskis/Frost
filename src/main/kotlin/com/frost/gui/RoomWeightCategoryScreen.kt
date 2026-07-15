package com.frost.gui

import com.frost.dungeon.RoomDatabase
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

/**
 * One room category's worth of weight editing, opened from [RoomWeightsScreen] - a
 * scrollable [RoomWeightListWidget] of every room in that [category] (one of
 * [RoomDatabase.SHAPES] or [RoomDatabase.MISC_CATEGORY]), plus a Done button that commits
 * every row's edited weight to [com.frost.config.ModConfig] in one batch.
 */
class RoomWeightCategoryScreen(private val parent: Screen?, private val category: String) :
    Screen(Text.literal("Frost - ${RoomDatabase.categoryLabel(category)}")) {

    // Vanilla's list widget (EntryListWidget/ContainerWidget) already draws its own
    // bordered background texture across its full bounding box - confirmed as the actual
    // cause of stray full-width lines on a live run: the widget was sized nearly as wide
    // as the whole screen (to let its row-centering math do the work) while a SEPARATE,
    // much narrower custom card was drawn behind it, so the widget's own native border
    // rendered far outside that narrower card and looked like a rendering bug. Fix: size
    // the widget itself to the card's width and reposition it, and drop the redundant
    // custom panel entirely - the vanilla background already IS the card.
    private val cardWidth = 340

    private lateinit var list: RoomWeightListWidget

    override fun init() {
        val listTop = FrostTheme.sectionHeaderContentStartY(textRenderer) + 8
        val listBottom = height - 32

        list = RoomWeightListWidget(
            client!!,
            (width - cardWidth) / 2,
            cardWidth,
            listBottom - listTop,
            listTop,
            RoomDatabase.roomsInCategory(category),
        )
        addDrawableChild(list)

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Done")) { close() }
                .dimensions((width - 150) / 2, height - 26, 150, 20)
                .build(),
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        super.render(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, textRenderer, width, RoomDatabase.categoryLabel(category), mouseX, mouseY)
    }

    override fun close() {
        list.saveAll()
        client?.setScreen(parent)
    }

    override fun shouldPause(): Boolean = false
}
