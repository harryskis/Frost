package com.frost.gui.map

import com.frost.dungeon.MapStyle

/** Keeps everything drawn on it, for tests to look through. Text is a fixed 6 pixels a letter. */
class RecordingMapCanvas : MapCanvas {
    data class Fill(val left: Int, val top: Int, val right: Int, val bottom: Int, val color: Int) {
        fun contains(x: Int, y: Int) = x in left until right && y in top until bottom
    }

    data class Text(val text: String, val centerX: Float, val top: Float, val scale: Float, val color: Int)

    data class Icon(val icon: MapIcon, val style: MapStyle, val left: Float, val top: Float, val size: Float) {
        val centerX get() = left + size / 2
        val centerY get() = top + size / 2
    }

    data class Crown(val left: Float, val top: Float, val scale: Float) {
        val width get() = PrinceCrown.WIDTH * scale
        val height get() = PrinceCrown.HEIGHT * scale
    }

    val crowns = mutableListOf<Crown>()
    val fills = mutableListOf<Fill>()
    val texts = mutableListOf<Text>()
    val icons = mutableListOf<Icon>()

    override val lineHeight = 9

    override fun textWidth(text: String) = text.length * 6

    override fun fill(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        fills += Fill(left, top, right, bottom, color)
    }

    override fun centeredText(text: String, centerX: Float, top: Float, scale: Float, color: Int) {
        texts += Text(text, centerX, top, scale, color)
    }

    override fun icon(icon: MapIcon, style: MapStyle, left: Float, top: Float, size: Float) {
        icons += Icon(icon, style, left, top, size)
    }

    override fun crown(left: Float, top: Float, scale: Float) {
        crowns += Crown(left, top, scale)
    }

    /** The color last filled at a pixel - what shows there, ignoring transparency. */
    fun colorAt(x: Int, y: Int): Int? = fills.lastOrNull { it.contains(x, y) }?.color

    fun textsSaying(text: String) = texts.filter { it.text == text }
}
