package com.frost.gui.map

import com.frost.dungeon.MapStyle
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** Loads an image from the test classpath: the mod's own resources, or the game's. */
fun loadImage(path: String): BufferedImage =
    ImageMapCanvas::class.java.getResourceAsStream(path)?.use { ImageIO.read(it) } ?: error("Missing $path on the classpath")

/**
 * Minecraft's default bitmap font, read from the game's own ascii.png, measured and spaced the
 * way the game does: a glyph is as wide as its furthest lit column, plus a pixel's gap, and a
 * space is 4. Close enough to the real thing to judge how labels fit.
 */
object MinecraftFont {
    private val sheet = loadImage("/assets/minecraft/textures/font/ascii.png")
    private const val CELL = 8
    private const val SPACE_ADVANCE = 4
    const val LINE_HEIGHT = 9

    private fun glyphWidth(char: Char): Int {
        val col = char.code % 16
        val row = char.code / 16
        for (x in CELL - 1 downTo 0) {
            for (y in 0 until CELL) if ((sheet.getRGB(col * CELL + x, row * CELL + y) ushr 24) != 0) return x + 1
        }
        return 0
    }

    fun advance(char: Char): Int = if (char == ' ') SPACE_ADVANCE else glyphWidth(char) + 1

    fun width(text: String): Int = text.sumOf { advance(it) }

    /** [text]'s lit pixels, as offsets from its top-left corner. */
    fun pixels(text: String): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var penX = 0
        for (char in text) {
            if (char != ' ' && char.code < 256) {
                val col = char.code % 16
                val row = char.code / 16
                for (y in 0 until CELL) for (x in 0 until glyphWidth(char)) {
                    if ((sheet.getRGB(col * CELL + x, row * CELL + y) ushr 24) != 0) result += penX + x to y
                }
            }
            penX += advance(char)
        }
        return result
    }
}

/**
 * Draws onto [graphics], with the map's top-left corner at the current origin and each map pixel
 * [pixel] image pixels across. Text gets the game's drop shadow, a quarter as bright one pixel
 * down and to the right.
 */
class ImageMapCanvas(private val graphics: Graphics2D, private val pixel: Int) : MapCanvas {
    override val lineHeight = MinecraftFont.LINE_HEIGHT

    override fun textWidth(text: String) = MinecraftFont.width(text)

    init {
        graphics.composite = AlphaComposite.SrcOver
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
    }

    override fun fill(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        if (right <= left || bottom <= top) return
        graphics.color = Color(color, true)
        graphics.fillRect(left * pixel, top * pixel, (right - left) * pixel, (bottom - top) * pixel)
    }

    override fun centeredText(text: String, centerX: Float, top: Float, scale: Float, color: Int) {
        // As the game's centeredText(font, text, 0, 0): moved left by half its whole-pixel width.
        val left = centerX - (MinecraftFont.width(text) / 2) * scale
        drawText(text, left + scale, top + scale, scale, scaleRgb(color, SHADOW_BRIGHTNESS))
        drawText(text, left, top, scale, color)
    }

    private fun drawText(text: String, left: Float, top: Float, scale: Float, color: Int) {
        graphics.color = Color(color, true)
        val size = scale * pixel
        for ((x, y) in MinecraftFont.pixels(text)) {
            val drawX = (left + x * scale) * pixel
            val drawY = (top + y * scale) * pixel
            graphics.fill(java.awt.geom.Rectangle2D.Float(drawX, drawY, size, size))
        }
    }

    override fun icon(icon: MapIcon, style: MapStyle, left: Float, top: Float, size: Float) {
        val image = loadImage("/assets/frost/${icon.texturePath(style)}")
        val transform = AffineTransform()
        transform.translate((left * pixel).toDouble(), (top * pixel).toDouble())
        transform.scale((size * pixel / image.width).toDouble(), (size * pixel / image.height).toDouble())
        graphics.drawImage(image, transform, null)
    }

    override fun crown(left: Float, top: Float, scale: Float) {
        val image = loadImage("/assets/frost/${PrinceCrown.TEXTURE}")
        val transform = AffineTransform()
        transform.translate((left * pixel).toDouble(), (top * pixel).toDouble())
        transform.scale((scale * pixel).toDouble(), (scale * pixel).toDouble())
        graphics.drawImage(image, transform, null)
    }

    private companion object {
        const val SHADOW_BRIGHTNESS = 0.25f
    }
}
