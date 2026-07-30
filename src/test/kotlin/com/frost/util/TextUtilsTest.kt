package com.frost.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TextUtilsTest {
    @Test
    fun `strips interleaved legacy formatting codes`() {
        assertEquals(
            "[NPC] Mort: Good luck.",
            TextUtils.stripFormatting("§e[NPC] §bMort§f: Good luck."),
        )
    }

    @Test
    fun `leaves plain text untouched`() {
        assertEquals("Secrets Found: 7", TextUtils.stripFormatting("Secrets Found: 7"))
    }

    @Test
    fun `strips reset and bold codes too`() {
        assertEquals("Dungeon Stats", TextUtils.stripFormatting("§6§lDungeon Stats§r"))
    }

    @Test
    fun `formats a sub-minute duration`() {
        assertEquals("0:45", TextUtils.formatDuration(45_000))
    }

    @Test
    fun `formats a duration with zero-padded seconds`() {
        assertEquals("3:58", TextUtils.formatDuration(238_000))
    }

    @Test
    fun `pads single-digit seconds`() {
        assertEquals("1:05", TextUtils.formatDuration(65_000))
    }

    @Test
    fun `formats a duration past one hour with hours included`() {
        assertEquals("1:01:05", TextUtils.formatDuration(3_665_000))
    }
}
