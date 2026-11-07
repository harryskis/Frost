package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DungeonFloorTest {
    @Test
    fun `the scoreboard's floor reads as a number, whether Normal or Master Mode`() {
        assertEquals(7, DungeonFloor.scoreboardNumber("VII"))
        assertEquals(6, DungeonFloor.scoreboardNumber("VI"))
        assertEquals(6, DungeonFloor.scoreboardNumber("M6"))
        assertEquals(5, DungeonFloor.scoreboardNumber("F5"))
        assertEquals(null, DungeonFloor.scoreboardNumber("E"))
    }

    @Test
    fun `parses a normal floor into the catacombs bucket`() {
        val parsed = DungeonFloor.parse("f7")
        assertEquals(DungeonFloor.Parsed("catacombs", "7", "F7"), parsed)
    }

    @Test
    fun `parses a master mode floor into the master_catacombs bucket`() {
        val parsed = DungeonFloor.parse("m5")
        assertEquals(DungeonFloor.Parsed("master_catacombs", "5", "M5"), parsed)
    }

    @Test
    fun `is case-insensitive on the prefix`() {
        assertEquals(DungeonFloor.Parsed("catacombs", "3", "F3"), DungeonFloor.parse("F3"))
        assertEquals(DungeonFloor.Parsed("master_catacombs", "1", "M1"), DungeonFloor.parse("M1"))
    }

    @Test
    fun `rejects a floor number outside 1 to 7`() {
        assertNull(DungeonFloor.parse("f0"))
        assertNull(DungeonFloor.parse("f8"))
        assertNull(DungeonFloor.parse("m9"))
    }

    @Test
    fun `rejects an unknown prefix`() {
        assertNull(DungeonFloor.parse("x5"))
    }

    @Test
    fun `rejects garbage input`() {
        assertNull(DungeonFloor.parse(""))
        assertNull(DungeonFloor.parse("f"))
        assertNull(DungeonFloor.parse("floor7"))
    }
}
