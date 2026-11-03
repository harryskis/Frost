package com.frost.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/** The big line of text just above the crosshair that the mod's warnings use. */
object BigAlert {
    private const val SCALE = 3f
    private const val ABOVE_CENTER = 30
    private const val MARGIN = 10

    fun draw(context: GuiGraphicsExtractor, text: String, color: Int) {
        val client = Minecraft.getInstance()
        val width = client.window.guiScaledWidth
        val scale = SCALE.coerceAtMost((width - MARGIN * 2f) / client.font.width(text))
        context.pose().pushMatrix()
        context.pose().translate(width / 2f, client.window.guiScaledHeight / 2f - ABOVE_CENTER)
        context.pose().scale(scale, scale)
        context.centeredText(client.font, text, 0, -client.font.lineHeight / 2, color)
        context.pose().popMatrix()
    }
}
