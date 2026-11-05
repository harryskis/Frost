package com.frost.gui

import com.frost.config.MAX_KEY_NOTIFICATION_SIZE
import com.frost.config.MIN_KEY_NOTIFICATION_SIZE
import com.frost.config.ModConfig
import com.frost.dungeon.KeyPickup
import com.frost.parsing.DungeonKey
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * "Move Notification" under Key Pickup in [ClickGuiScreen]: drag the notification to place it,
 * and scroll while holding it to resize, like the map's own move screen. Its position is kept as
 * a fraction of the screen, so it stays in the same place at any window size. Saved on close.
 */
class KeyPickupMoveScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Move Key Notification")) {
    // Kept as fractions of the screen, so a window resize mid-drag keeps it in place.
    private var fractionX = ModConfig.getKeyNotificationX()
    private var fractionY = ModConfig.getKeyNotificationY()
    private val centerX get() = fractionX * width
    private val centerY get() = fractionY * height
    private var size = ModConfig.getKeyNotificationSize()
    private var dragging = false
    private var grabOffsetX = 0.0
    private var grabOffsetY = 0.0
    private val sample = KeyPickup.text(DungeonKey.WITHER)

    override fun init() {
        addRenderableWidget(
            Button.builder(Component.literal("Done")) { onClose() }
                .bounds((width - DONE_WIDTH) / 2, height - DONE_MARGIN, DONE_WIDTH, DONE_HEIGHT)
                .build(),
        )
    }

    private fun isOverText(x: Double, y: Double): Boolean {
        val (textWidth, textHeight) = KeyPickup.textSize(sample, size)
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
        KeyPickup.drawText(context, sample, ModConfig.getWitherKeyColor(), centerX, centerY, size)
        super.extractRenderState(context, mouseX, mouseY, delta)
        val hint = "Drag to move, hold and scroll to resize ($size%)"
        context.centeredText(font, hint, width / 2, height - DONE_MARGIN - font.lineHeight - HINT_GAP, FrostTheme.TEXT_PRIMARY)
    }

    override fun onClose() {
        ModConfig.setKeyNotificationPosition(fractionX, fractionY)
        ModConfig.setKeyNotificationSize(size)
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
