package com.frost.gui

import com.frost.config.ModConfig
import com.frost.dungeon.FunnyMap
import com.frost.dungeon.FunnyMapLayout
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/**
 * "Edit Map" under Dungeon Map in [ClickGuiScreen]: drag the map to place it, and scroll while holding it to
 * resize. Shows the real layout when in a dungeon, or a
 * sample otherwise, so it can be set up from anywhere. No background, so it can be judged
 * against the actual game. Saved once on close, matching the other settings screens.
 */
class FunnyMapMoveScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Move Dungeon Map")) {
    private var mapX = ModConfig.getFunnyMapX()
    private var mapY = ModConfig.getFunnyMapY()
    private var mapScale = ModConfig.getFunnyMapScale()
    private var dragging = false
    private var grabOffsetX = 0.0
    private var grabOffsetY = 0.0

    override fun init() {
        clampMap()
        addRenderableWidget(
            Button.builder(Component.literal("Done")) { onClose() }
                .bounds((width - DONE_WIDTH) / 2, height - DONE_MARGIN, DONE_WIDTH, DONE_HEIGHT)
                .build(),
        )
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !isOverMap(event.x(), event.y())) return false
        dragging = true
        grabOffsetX = event.x() - mapX
        grabOffsetY = event.y() - mapY
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (!dragging) return super.mouseDragged(event, deltaX, deltaY)
        mapX = (event.x() - grabOffsetX).toInt()
        mapY = (event.y() - grabOffsetY).toInt()
        clampMap()
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) dragging = false
        return super.mouseReleased(event)
    }

    /** Resizes while the map is held, keeping its center in place so it grows and shrinks
     * evenly under the cursor rather than from its top-left corner. */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!dragging || scrollY == 0.0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val oldSize = FunnyMapRenderer.scaledSize(mapScale)
        mapScale = FunnyMapLayout.steppedScale(mapScale, scrollY)
        val newSize = FunnyMapRenderer.scaledSize(mapScale)
        mapX += (oldSize - newSize) / 2
        mapY += (oldSize - newSize) / 2
        clampMap()
        // The map's edges moved under the held cursor, so re-grab or the next drag would jump.
        grabOffsetX = mouseX - mapX
        grabOffsetY = mouseY - mapY
        return true
    }

    private fun isOverMap(x: Double, y: Double): Boolean {
        val size = FunnyMapRenderer.scaledSize(mapScale)
        return x >= mapX && x < mapX + size && y >= mapY && y < mapY + FunnyMapRenderer.scaledHeight(mapScale)
    }

    private fun clampMap() {
        val (x, y) = FunnyMapRenderer.clampToScreen(mapX, mapY, mapScale, minecraft)
        mapX = x
        mapY = y
    }

    // Deliberately empty - skips the default menu blur so the game stays visible behind the map.
    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {}

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        FunnyMapRenderer.draw(context, font, mapX, mapY, mapScale, FunnyMap.currentSnapshot() ?: FunnyMapLayout.sample)
        super.extractRenderState(context, mouseX, mouseY, delta)
        val hint = "Drag to move, hold and scroll to resize (${(mapScale * 100).roundToInt()}%)"
        context.centeredText(font, hint, width / 2, height - DONE_MARGIN - font.lineHeight - HINT_GAP, FrostTheme.TEXT_PRIMARY)
    }

    override fun onClose() {
        ModConfig.setFunnyMapPlacement(mapX, mapY, mapScale)
        minecraft.gui.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        const val DONE_WIDTH = 100
        const val DONE_HEIGHT = 20
        const val DONE_MARGIN = 28
        const val HINT_GAP = 4
    }
}
