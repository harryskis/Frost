package com.frost.gui

import com.frost.Frost
import com.frost.dungeon.KeyPickupTiming
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier

/**
 * Text that pops up on screen for a few seconds and fades out - Key Pickup's notification and
 * Chat Regex's. Each comes from a source, and a new one from the same source replaces the last;
 * different sources show at the same time, each where it was placed. A position is the text's
 * center as a fraction of the screen, so it stays put at any window size.
 */
object TextAlerts {
    private val HUD_ID = Identifier.fromNamespaceAndPath(Frost.MOD_ID, "text_alerts")
    private const val PERCENT = 100f

    private class Alert(
        val text: String,
        val color: Int,
        val fractionX: Float,
        val fractionY: Float,
        val sizePercent: Int,
        val durationMillis: Long,
        val shownAt: Long,
    )

    private val active = LinkedHashMap<Any, Alert>()

    fun register() {
        HudElementRegistry.addLast(HUD_ID) { context, _ -> draw(context) }
    }

    fun show(source: Any, text: String, color: Int, fractionX: Float, fractionY: Float, sizePercent: Int, durationMillis: Long) {
        active.remove(source)
        active[source] = Alert(text, color, fractionX, fractionY, sizePercent, durationMillis, System.currentTimeMillis())
    }

    private fun draw(context: GuiGraphicsExtractor) {
        if (active.isEmpty()) return
        val client = Minecraft.getInstance()
        // The move screen draws its own copy where it's being dragged.
        if (client.gui.screen() is TextMoveScreen) return
        val now = System.currentTimeMillis()
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val iterator = active.values.iterator()
        while (iterator.hasNext()) {
            val alert = iterator.next()
            val opacity = KeyPickupTiming.opacity(now - alert.shownAt, alert.durationMillis)
            if (opacity <= 0f) {
                iterator.remove()
                continue
            }
            drawText(context, alert.text, KeyPickupTiming.withOpacity(alert.color, opacity), alert.fractionX * width, alert.fractionY * height, alert.sizePercent)
        }
    }

    /** [text] centered on ([centerX], [centerY]) at [sizePercent] of normal text size. */
    fun drawText(context: GuiGraphicsExtractor, text: String, color: Int, centerX: Float, centerY: Float, sizePercent: Int) {
        val font = Minecraft.getInstance().font
        val scale = sizePercent / PERCENT
        context.pose().pushMatrix()
        context.pose().translate(centerX, centerY)
        context.pose().scale(scale, scale)
        context.centeredText(font, text, 0, -font.lineHeight / 2, color)
        context.pose().popMatrix()
    }

    /** How wide and tall [text] is on screen at [sizePercent]. */
    fun textSize(text: String, sizePercent: Int): Pair<Float, Float> {
        val font = Minecraft.getInstance().font
        val scale = sizePercent / PERCENT
        return font.width(text) * scale to font.lineHeight * scale
    }
}
