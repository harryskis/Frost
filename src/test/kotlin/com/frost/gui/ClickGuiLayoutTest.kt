package com.frost.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClickGuiLayoutTest {
    private val width = ClickGuiLayout.PANEL_WIDTH

    @Test
    fun `hex colors read with or without the hash, in either case`() {
        assertEquals(0xFFFF8800.toInt(), ClickGuiLayout.parseHexColor("#FF8800"))
        assertEquals(0xFFFF8800.toInt(), ClickGuiLayout.parseHexColor("ff8800"))
        assertEquals(0xFF00EFFF.toInt(), ClickGuiLayout.parseHexColor(" #00efff "))
    }

    @Test
    fun `hex colors end in their alpha if they have one`() {
        assertEquals(0x6600FF00, ClickGuiLayout.parseHexColor("00FF0066"))
        assertEquals(0x0000FF00, ClickGuiLayout.parseHexColor("#00FF0000"))
    }

    @Test
    fun `an incomplete or invalid hex color reads as nothing`() {
        assertEquals(null, ClickGuiLayout.parseHexColor("#FF88"))
        assertEquals(null, ClickGuiLayout.parseHexColor("#GGGGGG"))
        assertEquals(null, ClickGuiLayout.parseHexColor(""))
        assertEquals(null, ClickGuiLayout.parseHexColor("#-12345"))
        assertEquals(null, ClickGuiLayout.parseHexColor("00FF006"))
    }

    @Test
    fun `hex colors print the way they're typed, alpha last`() {
        assertEquals("00FF0066", ClickGuiLayout.formatHexColor(0x6600FF00))
        assertEquals(0xB0080B10.toInt(), ClickGuiLayout.parseHexColor(ClickGuiLayout.formatHexColor(0xB0080B10.toInt())))
    }

    @Test
    fun `hue, saturation and brightness convert to the expected colors`() {
        assertEquals(0xFFFF0000.toInt(), ClickGuiLayout.hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), ClickGuiLayout.hsvToArgb(1f / 3f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), ClickGuiLayout.hsvToArgb(2f / 3f, 1f, 1f))
        assertEquals(0xFFFF0000.toInt(), ClickGuiLayout.hsvToArgb(1f, 1f, 1f))
        assertEquals(0xFFFFFFFF.toInt(), ClickGuiLayout.hsvToArgb(0.4f, 0f, 1f))
        assertEquals(0xFF000000.toInt(), ClickGuiLayout.hsvToArgb(0.4f, 1f, 0f))
        assertEquals(0x6600FF00, ClickGuiLayout.hsvToArgb(1f / 3f, 1f, 1f, 0x66))
    }

    @Test
    fun `every color survives a trip through hue, saturation and brightness`() {
        for (color in listOf(0xFFFFC21A.toInt(), 0xFF2E96DC.toInt(), 0xFF080B10.toInt(), 0xFF7A3E9D.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF808080.toInt())) {
            val (hue, saturation, brightness) = ClickGuiLayout.rgbToHsv(color)
            assertEquals(color, ClickGuiLayout.hsvToArgb(hue, saturation, brightness))
        }
    }

    @Test
    fun `a gray has no saturation, and black no brightness`() {
        val (_, graySaturation, grayBrightness) = ClickGuiLayout.rgbToHsv(0xFF808080.toInt())
        assertEquals(0f, graySaturation)
        assertTrue(grayBrightness > 0f)
        assertEquals(Triple(0f, 0f, 0f), ClickGuiLayout.rgbToHsv(0xFF000000.toInt()))
    }

    @Test
    fun `a color bar's position is clamped past both ends`() {
        assertEquals(0f, ClickGuiLayout.barFraction(5.0, 10, 100))
        assertEquals(0.5f, ClickGuiLayout.barFraction(60.0, 10, 100))
        assertEquals(1f, ClickGuiLayout.barFraction(500.0, 10, 100))
    }

    @Test
    fun `default columns sit in one centered row without overlapping`() {
        val xs = (0 until 4).map { ClickGuiLayout.defaultPanelX(it, 4, 960) }
        assertEquals(960 - (xs.last() + width), xs.first())
        xs.zipWithNext().forEach { (a, b) -> assertTrue(b >= a + width) }
    }

    @Test
    fun `four columns fit on a 720p window at the fixed scale`() {
        val last = ClickGuiLayout.defaultPanelX(3, 4, 640)
        assertTrue(last + width <= 640)
    }

    @Test
    fun `columns never start off the left edge, even when they can't all fit`() {
        assertTrue(ClickGuiLayout.defaultPanelX(0, 4, 200) >= 0)
    }

    @Test
    fun `a column is kept reachable after the window shrinks`() {
        assertEquals(960 - width, ClickGuiLayout.clampPanelX(5000, 960))
        assertEquals(0, ClickGuiLayout.clampPanelX(-20, 960))
        assertEquals(540 - ClickGuiLayout.HEADER_HEIGHT, ClickGuiLayout.clampPanelY(5000, 540))
        assertEquals(0, ClickGuiLayout.clampPanelY(-20, 540))
    }

    @Test
    fun `a column's body shows everything that fits, and only that`() {
        assertEquals(100, ClickGuiLayout.visibleBodyHeight(100, 60, 540))
        val cramped = ClickGuiLayout.visibleBodyHeight(1000, 60, 300)
        assertTrue(cramped < 300 - 60)
        assertTrue(60 + ClickGuiLayout.HEADER_HEIGHT + cramped + ClickGuiLayout.CAP_HEIGHT <= 300)
        assertEquals(0, ClickGuiLayout.visibleBodyHeight(100, 500, 300))
    }

    @Test
    fun `scrolling stops at both ends, and does nothing when everything fits`() {
        assertEquals(0, ClickGuiLayout.clampScroll(-50, 300, 100))
        assertEquals(200, ClickGuiLayout.clampScroll(999, 300, 100))
        assertEquals(50, ClickGuiLayout.clampScroll(50, 300, 100))
        assertEquals(0, ClickGuiLayout.clampScroll(50, 100, 100))
    }

    @Test
    fun `sliders map the mouse to whole values, clamped past both ends of the track`() {
        assertEquals(1, ClickGuiLayout.sliderValue(10.0, 10, 100, 1, 100))
        assertEquals(100, ClickGuiLayout.sliderValue(110.0, 10, 100, 1, 100))
        assertEquals(51, ClickGuiLayout.sliderValue(60.0, 10, 100, 1, 101))
        assertEquals(1, ClickGuiLayout.sliderValue(-500.0, 10, 100, 1, 100))
        assertEquals(100, ClickGuiLayout.sliderValue(5000.0, 10, 100, 1, 100))
    }

    @Test
    fun `slider position matches its value`() {
        assertEquals(0f, ClickGuiLayout.sliderFraction(1, 1, 101))
        assertEquals(0.5f, ClickGuiLayout.sliderFraction(51, 1, 101))
        assertEquals(1f, ClickGuiLayout.sliderFraction(101, 1, 101))
        assertEquals(0f, ClickGuiLayout.sliderFraction(5, 5, 5))
    }

    @Test
    fun `search matches feature and setting names, ignoring case and stray spaces`() {
        val settings = listOf("Blood Rush Path", "Edit Map")
        assertTrue(ClickGuiLayout.matchesSearch("", "Funny Map", settings))
        assertTrue(ClickGuiLayout.matchesSearch("   ", "Funny Map", settings))
        assertTrue(ClickGuiLayout.matchesSearch("funny", "Funny Map", settings))
        assertTrue(ClickGuiLayout.matchesSearch(" BLOOD ", "Funny Map", settings))
        assertFalse(ClickGuiLayout.matchesSearch("spotify", "Funny Map", settings))
    }

    @Test
    fun `colors blend per channel, including alpha`() {
        val from = 0x00000000
        val to = 0xFFFFFFFF.toInt()
        assertEquals(from, ClickGuiLayout.lerpColor(from, to, 0f))
        assertEquals(to, ClickGuiLayout.lerpColor(from, to, 1f))
        assertEquals(0x80808080.toInt(), ClickGuiLayout.lerpColor(from, to, 0.5f))
    }

    @Test
    fun `animations step toward their target and stop there`() {
        assertEquals(0.5f, ClickGuiLayout.stepAnimation(0f, true, 50f, 100f))
        assertEquals(1f, ClickGuiLayout.stepAnimation(0.9f, true, 50f, 100f))
        assertEquals(0f, ClickGuiLayout.stepAnimation(0.1f, false, 50f, 100f))
    }

    @Test
    fun `easing keeps its endpoints and midpoint`() {
        assertEquals(0f, ClickGuiLayout.ease(0f))
        assertEquals(0.5f, ClickGuiLayout.ease(0.5f))
        assertEquals(1f, ClickGuiLayout.ease(1f))
    }
}
