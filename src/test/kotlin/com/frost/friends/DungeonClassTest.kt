package com.frost.friends

import kotlin.test.Test
import kotlin.test.assertEquals

class DungeonClassTest {
    @Test
    fun `parses comma-separated abbreviations`() {
        val (filled, unknown) = DungeonClass.parseList("heal,tank")
        assertEquals(setOf(DungeonClass.HEALER, DungeonClass.TANK), filled)
        assertEquals(emptyList(), unknown)
    }

    @Test
    fun `parses space-separated display names regardless of case`() {
        val (filled, unknown) = DungeonClass.parseList("Mage Archer")
        assertEquals(setOf(DungeonClass.MAGE, DungeonClass.ARCHER), filled)
        assertEquals(emptyList(), unknown)
    }

    @Test
    fun `reports unrecognized tokens without dropping the valid ones`() {
        val (filled, unknown) = DungeonClass.parseList("heal,ninja,tank")
        assertEquals(setOf(DungeonClass.HEALER, DungeonClass.TANK), filled)
        assertEquals(listOf("ninja"), unknown)
    }

    @Test
    fun `blank input yields nothing filled and nothing unrecognized`() {
        val (filled, unknown) = DungeonClass.parseList("")
        assertEquals(emptySet(), filled)
        assertEquals(emptyList(), unknown)
    }
}
