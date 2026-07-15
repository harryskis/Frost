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
}
