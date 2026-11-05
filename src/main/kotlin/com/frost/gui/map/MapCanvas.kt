package com.frost.gui.map

import com.frost.dungeon.MapStyle

/** The checkmark icons the NA, Odin and LCM styles draw on rooms. */
enum class MapIcon(val fileName: String) {
    GREEN_CHECK("green_check"),
    WHITE_CHECK("white_check"),
    CROSS("cross"),
    QUESTION("question"),
    ;

    /** Where [style]'s copy of this icon is, under the mod's assets. */
    fun texturePath(style: MapStyle): String = "textures/map/${style.name.lowercase()}/$fileName.png"
}

/**
 * What [MapPainter] draws onto, in the map style's own pixels. In game it's the HUD (see
 * [GuiMapCanvas]); the tests draw onto an image instead, which is how the styles get checked
 * without running the game.
 */
interface MapCanvas {
    /** The font's line height at full size. */
    val lineHeight: Int

    /** How wide [text] is at full size. */
    fun textWidth(text: String): Int

    fun fill(left: Int, top: Int, right: Int, bottom: Int, color: Int)

    /** [text] centered on [centerX] with its top at [top], at [scale] times full size. */
    fun centeredText(text: String, centerX: Float, top: Float, scale: Float, color: Int)

    /** [style]'s copy of [icon], [size] pixels square with its top-left corner at ([left], [top]). */
    fun icon(icon: MapIcon, style: MapStyle, left: Float, top: Float, size: Float)
}
