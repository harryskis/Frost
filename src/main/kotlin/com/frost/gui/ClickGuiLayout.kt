package com.frost.gui

import kotlin.math.roundToInt

/**
 * The numbers and small calculations behind the click GUI, kept free of Minecraft types so
 * they're unit-testable. Everything is in "virtual" pixels: the GUI draws at its own fixed
 * scale ([UI_SCALE]) whatever the game's GUI scale is, so the columns always fit - at GUI scale
 * 3 or 4 they'd otherwise be far too wide for a 1080p screen.
 */
object ClickGuiLayout {
    const val UI_SCALE = 2f

    private val HEX_COLOR = Regex("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")
    private const val HEX_RADIX = 16
    private const val RGB_DIGITS = 6
    private const val OPAQUE = 0xFF shl 24
    private const val MAX_CHANNEL = 255f
    private const val HUE_SECTORS = 6f

    const val PANEL_WIDTH = 150
    const val HEADER_HEIGHT = 22
    const val ROW_HEIGHT = 21
    const val SUB_ROW_HEIGHT = 18
    const val SLIDER_ROW_HEIGHT = 30
    const val CAP_HEIGHT = 4
    const val PADDING = 6
    const val SUB_INDENT = 8

    private const val PANEL_GAP = 10
    private const val SCREEN_MARGIN = 4
    private const val DEFAULT_PANEL_Y = 62
    const val SCROLL_STEP = 20

    /** Where a column starts by default: all of them in one centered row, under the wordmark. */
    fun defaultPanelX(index: Int, panelCount: Int, screenWidth: Int): Int {
        val total = panelCount * PANEL_WIDTH + (panelCount - 1) * PANEL_GAP
        val start = ((screenWidth - total) / 2).coerceAtLeast(SCREEN_MARGIN)
        return start + index * (PANEL_WIDTH + PANEL_GAP)
    }

    val defaultPanelY: Int get() = DEFAULT_PANEL_Y

    /** Keeps a column's header reachable, including after the window shrinks. */
    fun clampPanelX(x: Int, screenWidth: Int): Int = x.coerceIn(0, (screenWidth - PANEL_WIDTH).coerceAtLeast(0))
    fun clampPanelY(y: Int, screenHeight: Int): Int = y.coerceIn(0, (screenHeight - HEADER_HEIGHT).coerceAtLeast(0))

    /** How much of a column's content fits on screen below its header. */
    fun visibleBodyHeight(contentHeight: Int, panelY: Int, screenHeight: Int): Int {
        val available = screenHeight - panelY - HEADER_HEIGHT - CAP_HEIGHT - SCREEN_MARGIN
        return contentHeight.coerceAtMost(available.coerceAtLeast(0))
    }

    fun clampScroll(scroll: Int, contentHeight: Int, visibleHeight: Int): Int =
        scroll.coerceIn(0, (contentHeight - visibleHeight).coerceAtLeast(0))

    /** Reads a color typed as RRGGBB or RRGGBBAA, with or without a "#", as ARGB. Without its alpha
     * digits it's opaque, and it's null until it's complete and valid. */
    fun parseHexColor(text: String): Int? {
        val digits = text.trim().removePrefix("#")
        if (!HEX_COLOR.matches(digits)) return null
        val rgb = digits.take(RGB_DIGITS).toInt(HEX_RADIX)
        val alpha = digits.drop(RGB_DIGITS).takeIf { it.isNotEmpty() }?.toInt(HEX_RADIX) ?: 0xFF
        return (alpha shl 24) or rgb
    }

    /** An ARGB color as RRGGBBAA, the way the color picker shows it. */
    fun formatHexColor(argb: Int): String = "%06X%02X".format(argb and 0xFFFFFF, argb ushr 24)

    /** An ARGB color's hue, saturation and brightness, each from 0 to 1. */
    fun rgbToHsv(argb: Int): Triple<Float, Float, Float> {
        val red = ((argb shr 16) and 0xFF) / MAX_CHANNEL
        val green = ((argb shr 8) and 0xFF) / MAX_CHANNEL
        val blue = (argb and 0xFF) / MAX_CHANNEL
        val max = maxOf(red, green, blue)
        val range = max - minOf(red, green, blue)
        val sector = when {
            range == 0f -> 0f
            max == red -> (green - blue) / range
            max == green -> (blue - red) / range + 2f
            else -> (red - green) / range + 4f
        }
        val hue = ((sector + HUE_SECTORS) % HUE_SECTORS) / HUE_SECTORS
        return Triple(hue, if (max == 0f) 0f else range / max, max)
    }

    /** The ARGB color for a hue, saturation and brightness from 0 to 1. Opaque unless given [alpha]. */
    fun hsvToArgb(hue: Float, saturation: Float, brightness: Float, alpha: Int = 0xFF): Int {
        val sector = hue * HUE_SECTORS
        fun channel(offset: Float): Int {
            val position = (offset + sector) % HUE_SECTORS
            val value = brightness * (1f - saturation * minOf(position, 4f - position, 1f).coerceAtLeast(0f))
            return (value * MAX_CHANNEL).roundToInt()
        }
        return (alpha shl 24) or (channel(5f) shl 16) or (channel(3f) shl 8) or channel(1f)
    }

    /** The same color with no transparency, for showing it at full strength. */
    fun opaque(argb: Int): Int = argb or OPAQUE

    /** How far a mouse position is along a bar, from 0 to 1, clamped past both ends. */
    fun barFraction(mouse: Double, start: Int, size: Int): Float = ((mouse - start) / size).coerceIn(0.0, 1.0).toFloat()

    /** A slider's value for a mouse position, snapped to whole numbers and kept in range. */
    fun sliderValue(mouseX: Double, trackX: Int, trackWidth: Int, min: Int, max: Int): Int {
        val fraction = ((mouseX - trackX) / trackWidth).coerceIn(0.0, 1.0)
        return (min + fraction * (max - min)).roundToInt()
    }

    /** How far along its track a slider's value sits, from 0 to 1. */
    fun sliderFraction(value: Int, min: Int, max: Int): Float =
        if (max <= min) 0f else ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)

    /** A feature shows in search if its own name or any of its settings' names match, so typing
     * "blood" still finds Funny Map. */
    fun matchesSearch(query: String, name: String, settingNames: List<String>): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        return name.contains(trimmed, ignoreCase = true) || settingNames.any { it.contains(trimmed, ignoreCase = true) }
    }

    /** Blends two ARGB colors, with [t] from 0 (all [from]) to 1 (all [to]). */
    fun lerpColor(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from ushr shift) and 0xFF
            val b = (to ushr shift) and 0xFF
            return (a + (b - a) * t).roundToInt().coerceIn(0, 255) shl shift
        }
        return channel(24) or channel(16) or channel(8) or channel(0)
    }

    /** Moves an animation's linear progress toward its target, for one frame of [dtMillis]. */
    fun stepAnimation(progress: Float, opening: Boolean, dtMillis: Float, durationMillis: Float): Float {
        val step = dtMillis / durationMillis
        return (if (opening) progress + step else progress - step).coerceIn(0f, 1f)
    }

    /** Eases linear progress so an animation starts and ends gently. */
    fun ease(progress: Float): Float = progress * progress * (3f - 2f * progress)
}
