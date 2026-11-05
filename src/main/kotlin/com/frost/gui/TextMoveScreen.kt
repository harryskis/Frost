package com.frost.gui

import com.frost.config.MAX_KEY_NOTIFICATION_SIZE
import com.frost.config.MIN_KEY_NOTIFICATION_SIZE
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * Placing a [TextAlerts] text: drag it to move it, and scroll while holding it to resize, like the
 * map's own move screen. The position is kept as a fraction of the screen, so it stays in the same
 * place at any window size. [save] gets the position and size on close.
 */
class TextMoveScreen(
    private val parent: Screen?,
    private val sample: String,
    private val color: Int,
    startX: Float,
    startY: Float,
    startSize: Int,
    private val save: (x: Float, y: Float, size: Int) -> Unit,
) : Screen(Component.literal("Frost - Move Text")) {
    // Kept as fractions of the screen, so a window resize mid-drag keeps it in place.
    private var fractionX = startX
    private var fractionY = startY
    private val centerX get() = fractionX * width
    private val centerY get() = fractionY * height
    private var size = startSize
    private var dragging = false
    private var grabOffsetX = 0.0
    private var grabOffsetY = 0.0

    override fun init() {
        addRenderableWidget(
            Button.builder(Component.literal("Done")) { onClose() }
                .bounds((width - DONE_WIDTH) / 2, height - DONE_MARGIN, DONE_WIDTH, DONE_HEIGHT)
                .build(),
        )
    }

    private fun isOverText(x: Double, y: Double): Boolean {
        val (textWidth, textHeight) = TextAlerts.textSize(sample, size)
        return x >= centerX - textWidth / 2 && x < centerX + textWidth / 2 && y >= centerY - textHeight / 2 && y < centerY + textHeight / 2
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !isOverText(event.x(), event.y())) return false
        dragging = true
        grabOffsetX = event.x() - centerX
        grabOffsetY = event.y() - centerY
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (!dragging) return super.mouseDragged(event, deltaX, deltaY)
        if (width > 0) fractionX = ((event.x() - grabOffsetX) / width).toFloat().coerceIn(0f, 1f)
        if (height > 0) fractionY = ((event.y() - grabOffsetY) / height).toFloat().coerceIn(0f, 1f)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) dragging = false
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!dragging || scrollY == 0.0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        size = (size + if (scrollY > 0) SIZE_STEP else -SIZE_STEP).coerceIn(MIN_KEY_NOTIFICATION_SIZE, MAX_KEY_NOTIFICATION_SIZE)
        return true
    }

    // Deliberately empty - skips the default menu blur so the game stays visible behind it.
    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {}

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        TextAlerts.drawText(context, sample, color, centerX, centerY, size)
        super.extractRenderState(context, mouseX, mouseY, delta)
        val hint = "Drag to move, hold and scroll to resize ($size%)"
        context.centeredText(font, hint, width / 2, height - DONE_MARGIN - font.lineHeight - HINT_GAP, FrostTheme.TEXT_PRIMARY)
    }

    override fun onClose() {
        save(fractionX, fractionY, size)
        minecraft.gui.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        const val DONE_WIDTH = 100
        const val DONE_HEIGHT = 20
        const val DONE_MARGIN = 28
        const val HINT_GAP = 4
        const val SIZE_STEP = 10
    }
}
