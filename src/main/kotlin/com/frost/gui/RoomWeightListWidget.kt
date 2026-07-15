package com.frost.gui

import com.frost.config.ModConfig
import com.frost.dungeon.RoomDatabase
import com.frost.util.TextUtils
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.widget.ElementListWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text

/**
 * Scrollable list of every room in one shape category, each row showing the room's real
 * name and an editable weight field pre-filled with its currently configured weight (or
 * "1.0" if never customized). Edits are held in each row's own text field and only
 * persisted to [ModConfig] in one batch when [saveAll] is called (the owning screen does
 * this on close) - not on every keystroke, since a full category can be dozens of rows.
 */
class RoomWeightListWidget(
    client: MinecraftClient,
    x: Int,
    width: Int,
    height: Int,
    y: Int,
    rooms: List<RoomDatabase.RoomInfo>,
) : ElementListWidget<RoomWeightListWidget.RoomEntry>(client, width, height, y, 24) {

    init {
        // Each row's horizontal position is computed from the list's OWN x the moment
        // addEntry() runs and is never recalculated afterward - confirmed as the actual
        // cause of room names silently disappearing on a live run: this widget used to be
        // constructed at the default x=0, entries got added (baking in a position based on
        // that), and only THEN was the widget moved to its real, centered x - which shifted
        // where the widget itself (and its clip/scroll bounds) rendered, but never touched
        // each entry's already-cached position. The room name label (drawn at that stale,
        // near-zero x) ended up outside the widget's own visible area and got clipped,
        // while the weight field - repositioned relative to that same stale x every frame
        // in RoomEntry.render - happened to still land inside the visible area. Setting the
        // real x here, before any entry is added, avoids the whole class of bug.
        this.x = x
        for (room in rooms) {
            addEntry(RoomEntry(client, room))
        }
    }

    override fun getRowWidth(): Int = 280

    /** Parses every row's current text as a weight and writes the valid ones to
     * [ModConfig] in a single save - a row left blank or non-numeric is skipped, leaving
     * whatever was previously configured (or the 1.0 default) untouched. */
    fun saveAll() {
        val updates = children().mapNotNull { entry ->
            entry.weightField.text.trim().toDoubleOrNull()?.let { entry.room.name to it }
        }.toMap()
        if (updates.isNotEmpty()) ModConfig.setRoomWeights(updates)
    }

    inner class RoomEntry(client: MinecraftClient, val room: RoomDatabase.RoomInfo) : Entry<RoomEntry>() {
        val weightField = TextFieldWidget(client.textRenderer, 0, 0, 60, 20, Text.literal(room.name))

        init {
            weightField.setMaxLength(8)
            weightField.text = TextUtils.formatWeight(ModConfig.getRoomWeight(room.name))
            // Loose numeric filter: digits, at most one leading '-' and one '.' - good
            // enough to keep obviously-invalid input out without being a strict parser
            // (saveAll re-parses properly and just skips anything that still isn't a
            // valid number, e.g. a lone "-" or "." left mid-edit).
            weightField.setTextPredicate { it.matches(Regex("-?\\d*\\.?\\d*")) }
        }

        override fun render(context: DrawContext, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {
            context.drawTextWithShadow(
                client.textRenderer,
                room.name,
                x,
                y + (height - 8) / 2,
                FrostTheme.TEXT_PRIMARY,
            )
            weightField.x = x + contentWidth - weightField.width
            weightField.y = y + (height - weightField.height) / 2
            weightField.render(context, mouseX, mouseY, tickDelta)
        }

        override fun children(): MutableList<out Element> = mutableListOf(weightField)

        override fun selectableChildren(): MutableList<out Selectable> = mutableListOf(weightField)
    }
}
