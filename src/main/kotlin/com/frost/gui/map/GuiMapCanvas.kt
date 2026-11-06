package com.frost.gui.map

import com.frost.Frost
import com.frost.dungeon.MapStyle
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

/** [MapCanvas] onto the HUD, already moved and scaled to where the map goes. */
class GuiMapCanvas(private val context: GuiGraphicsExtractor, private val font: Font) : MapCanvas {
    override val lineHeight: Int get() = font.lineHeight

    override fun textWidth(text: String): Int = font.width(text)

    override fun fill(left: Int, top: Int, right: Int, bottom: Int, color: Int) = context.fill(left, top, right, bottom, color)

    override fun centeredText(text: String, centerX: Float, top: Float, scale: Float, color: Int) {
        context.pose().pushMatrix()
        context.pose().translate(centerX, top)
        context.pose().scale(scale, scale)
        context.centeredText(font, text, 0, 0, color)
        context.pose().popMatrix()
    }

    override fun icon(icon: MapIcon, style: MapStyle, left: Float, top: Float, size: Float) {
        val textureSize = style.iconTextureSize
        if (textureSize <= 0) return
        val texture = TEXTURES.getOrPut(style to icon) { Identifier.fromNamespaceAndPath(Frost.MOD_ID, icon.texturePath(style)) }
        context.pose().pushMatrix()
        context.pose().translate(left, top)
        context.pose().scale(size / textureSize, size / textureSize)
        context.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, textureSize, textureSize, textureSize, textureSize)
        context.pose().popMatrix()
    }

    override fun crown(left: Float, top: Float, scale: Float) {
        context.pose().pushMatrix()
        context.pose().translate(left, top)
        context.pose().scale(scale, scale)
        context.blit(RenderPipelines.GUI_TEXTURED, CROWN, 0, 0, 0f, 0f, PrinceCrown.WIDTH, PrinceCrown.HEIGHT, PrinceCrown.WIDTH, PrinceCrown.HEIGHT)
        context.pose().popMatrix()
    }

    private companion object {
        val TEXTURES = HashMap<Pair<MapStyle, MapIcon>, Identifier>()
        val CROWN: Identifier = Identifier.fromNamespaceAndPath(Frost.MOD_ID, PrinceCrown.TEXTURE)
    }
}
