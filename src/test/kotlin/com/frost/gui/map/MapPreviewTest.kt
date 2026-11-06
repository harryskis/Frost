package com.frost.gui.map

import com.frost.dungeon.FunnyMapLayout
import com.frost.dungeon.FunnyMapSnapshot
import com.frost.dungeon.MapStyle
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Draws every map style in Legit and in Funny onto one image, build/map-previews/map-styles.png,
 * with the game's own font, skins and map pointer - a way to see the styles without the game.
 */
class MapPreviewTest {
    private val pixel = 3
    private val margin = 16
    private val headerHeight = 34
    private val rowLabelWidth = 150
    private val background = Color(0x2E, 0x3A, 0x2C)

    private class Row(val label: String, val map: FunnyMapSnapshot, val options: MapPaintOptions)

    private val rows = listOf(
        Row("Legit", PreviewDungeon.legit, MapPaintOptions()),
        Row("Funny", PreviewDungeon.funny, MapPaintOptions(showPrinces = true)),
        Row("Funny, crowns centered", PreviewDungeon.funny, MapPaintOptions(showPrinces = true, princeIconCenter = true)),
        Row("Legit + extras", PreviewDungeon.legit, MapPaintOptions(bloodRushPath = true, totalSecrets = true)),
    )

    @Test
    fun `draws every style in every mode`() {
        val cellWidth = MapStyle.FROST.geometry.size * pixel + margin
        val cellHeight = (MapStyle.FROST.geometry.size + MapPainter.SECRETS_LINE_HEIGHT) * pixel + margin
        val image = BufferedImage(
            rowLabelWidth + MapStyle.entries.size * cellWidth + margin,
            headerHeight + rows.size * cellHeight + margin,
            BufferedImage.TYPE_INT_ARGB,
        )
        val graphics = image.createGraphics()
        graphics.color = background
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 22)
        graphics.color = Color.WHITE

        MapStyle.entries.forEachIndexed { column, style ->
            graphics.drawString(style.label, rowLabelWidth + column * cellWidth, headerHeight - 10)
        }
        rows.forEachIndexed { rowIndex, row ->
            val top = headerHeight + rowIndex * cellHeight
            graphics.color = Color.WHITE
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
            graphics.drawString(row.label, margin, top + 30)
            MapStyle.entries.forEachIndexed { column, style ->
                val mapGraphics = graphics.create() as Graphics2D
                mapGraphics.translate(rowLabelWidth + column * cellWidth, top)
                MapPainter.paint(ImageMapCanvas(mapGraphics, pixel), style, row.map, row.options)
                drawPlayers(mapGraphics, style)
                mapGraphics.dispose()
            }
        }
        graphics.dispose()

        val output = File("build/map-previews/map-styles.png")
        output.parentFile.mkdirs()
        ImageIO.write(image, "png", output)
        assertTrue(output.length() > 0)
    }

    private val steve = loadImage("/assets/minecraft/textures/entity/player/wide/steve.png")
    private val alex = loadImage("/assets/minecraft/textures/entity/player/wide/alex.png")
    private val pointer = loadImage("/assets/minecraft/textures/map/decorations/frame.png")

    /** The heads as [com.frost.gui.FunnyMapRenderer] draws them, the local player's last. */
    private fun drawPlayers(graphics: Graphics2D, style: MapStyle) {
        val geometry = style.geometry
        val players = PreviewDungeon.players
        for (index in players.indices.reversed()) {
            val player = players[index]
            val self = index == 0
            val look = style.headLook(self, player.className)
            val saved = graphics.transform
            graphics.translate(
                (geometry.tileUnitsToMap(player.tileX).toInt() * pixel).toDouble(),
                (geometry.tileUnitsToMap(player.tileZ).toInt() * pixel).toDouble(),
            )
            graphics.rotate(Math.toRadians(FunnyMapLayout.headRotationDegrees(player.yaw).toDouble()))
            graphics.scale(pixel.toDouble(), pixel.toDouble())
            if (look.arrow) {
                graphics.translate(-0.5, -0.5)
                val arrow = pointer.getSubimage(2, 0, 5, 7)
                graphics.drawImage(arrow, AffineTransform.getTranslateInstance(-2.0, -4.0), null)
            } else {
                val face = look.faceSize
                val half = face / 2
                val border = (look.backdropSize - look.faceSize) / 2
                if (look.backdropSize > 0) {
                    graphics.color = Color(look.backdropColor, true)
                    graphics.fillRect(-half - border, -half - border, face + border * 2, face + border * 2)
                }
                val skin = if (index % 2 == 0) steve else alex
                val scale = face / 8.0
                val faceTransform = AffineTransform.getTranslateInstance(-half.toDouble(), -half.toDouble()).apply { scale(scale, scale) }
                graphics.drawImage(skin.getSubimage(8, 8, 8, 8), faceTransform, null)
                graphics.drawImage(skin.getSubimage(40, 8, 8, 8), faceTransform, null)
            }
            graphics.transform = saved
        }
    }
}
