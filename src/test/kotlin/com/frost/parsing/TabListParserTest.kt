package com.frost.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabListParserTest {
    @Test
    fun `parses a puzzle line with the solver's name`() {
        val update = TabListParser.parsePuzzleLine(" Water Board: [✔] (Steve)")
        assertEquals(PuzzleStatusUpdate("Water Board", PuzzleStatus.COMPLETED, "Steve"), update)
    }

    @Test
    fun `parses a failed puzzle line with no solver`() {
        val update = TabListParser.parsePuzzleLine(" Silverfish: [✖]")
        assertEquals(PuzzleStatusUpdate("Silverfish", PuzzleStatus.FAILED, null), update)
    }

    @Test
    fun `parses an incomplete puzzle line`() {
        val update = TabListParser.parsePuzzleLine(" Ice Fill: [✦]")
        assertEquals(PuzzleStatusUpdate("Ice Fill", PuzzleStatus.INCOMPLETE, null), update)
    }

    @Test
    fun `returns null for unrelated lines`() {
        assertNull(TabListParser.parsePuzzleLine("Purse: 1,234"))
    }

    @Test
    fun `tolerates the extra trailing whitespace real tab list entries carry`() {
        // Confirmed from a live run: Hypixel pads these with two trailing spaces, not one,
        // which the original regex's single optional trailing space didn't tolerate.
        val update = TabListParser.parsePuzzleLine("Higher Or Lower: [✔]  ")
        assertEquals(PuzzleStatusUpdate("Higher Or Lower", PuzzleStatus.COMPLETED, null), update)
    }

    @Test
    fun `parses the dungeon's total puzzle count`() {
        assertEquals(3, TabListParser.parsePuzzleCount(" Puzzles: (3) "))
    }

    @Test
    fun `returns null puzzle count for unrelated lines`() {
        assertNull(TabListParser.parsePuzzleCount("Purse: 1,234"))
    }

    @Test
    fun `parses a player's username and dungeon class from their roster line`() {
        val update = TabListParser.parsePlayerClassLine("[417] Bdlt ⛃ (Healer XLIII)")
        assertEquals(TabListParser.PlayerClassUpdate("Bdlt", "Healer"), update)
    }

    @Test
    fun `parses every real dungeon class abbreviation`() {
        assertEquals('H', TabListParser.classLetter("Healer"))
        assertEquals('M', TabListParser.classLetter("Mage"))
        assertEquals('B', TabListParser.classLetter("Berserk"))
        assertEquals('A', TabListParser.classLetter("Archer"))
        assertEquals('T', TabListParser.classLetter("Tank"))
        assertNull(TabListParser.classLetter("Unknown"))
    }

    @Test
    fun `returns null for roster lines missing the dungeon icon`() {
        // Confirmed necessary on a live run: Hypixel's tab list has other decorative/fake
        // entries with a similar "(Word Numeral)" shape that aren't real player rows.
        assertNull(TabListParser.parsePlayerClassLine("[417] Bdlt (Healer XLIII)"))
    }
}
