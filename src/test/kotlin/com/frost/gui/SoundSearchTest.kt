package com.frost.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoundSearchTest {
    private val orb = "minecraft:entity.experience_orb.pickup"

    @Test
    fun `vanilla sounds are shown without their namespace`() {
        assertEquals("entity.experience_orb.pickup", SoundSearch.displayName(orb))
        assertEquals("othermod:ding", SoundSearch.displayName("othermod:ding"))
    }

    @Test
    fun `every word typed has to be in the ID, in any order and any case`() {
        assertTrue(SoundSearch.matches(orb, ""))
        assertTrue(SoundSearch.matches(orb, "orb pick"))
        assertTrue(SoundSearch.matches(orb, "PICKUP experience"))
        assertTrue(SoundSearch.matches(orb, "experience_orb"))
        assertFalse(SoundSearch.matches(orb, "orb pling"))
    }

    @Test
    fun `a long ID is cut from the front to fit, keeping its end`() {
        val width = { text: String -> text.length * 6 }
        assertEquals("pling", SoundSearch.fitEnd("pling", 60, width))
        val fitted = SoundSearch.fitEnd("entity.experience_orb.pickup", 60, width)
        assertEquals("..b.pickup", fitted)
        assertTrue(width(fitted) <= 60)
    }
}
