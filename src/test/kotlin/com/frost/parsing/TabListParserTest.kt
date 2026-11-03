package com.frost.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabListParserTest {
    @Test
    fun `parseMemberClass reads a class in either level style, but only once one's picked`() {
        assertEquals("Tank", TabListParser.parseMemberClass("[443] Bdlt ☃ (Tank XLIX)"))
        assertEquals("Healer", TabListParser.parseMemberClass("[443] Bdlt ☃ (Healer XLVI)"))
        assertEquals("Archer", TabListParser.parseMemberClass("[100] Bdlt (Archer 50)"))
        assertNull(TabListParser.parseMemberClass("[443] Bdlt ☃ (EMPTY)"))
        assertNull(TabListParser.parseMemberClass("[443] Bdlt ☃ (DEAD)"))
        assertNull(TabListParser.parseMemberClass("[443] Bdlt ☃"))
    }

    @Test
    fun `parseUnclaimedChests reads the count from the Dungeon Hub's line`() {
        assertEquals(26, TabListParser.parseUnclaimedChests(" Unclaimed chests: 26"))
        assertEquals(0, TabListParser.parseUnclaimedChests(" Unclaimed chests: 0"))
        assertNull(TabListParser.parseUnclaimedChests(" Fairy Souls: 7/7"))
        assertNull(TabListParser.parseUnclaimedChests("[443] Bdlt Unclaimed chests: 26"))
    }

    @Test
    fun `isMemberDead only matches a dead member's line`() {
        assertEquals(true, TabListParser.isMemberDead("[443] Bdlt ☃ (DEAD)"))
        assertEquals(false, TabListParser.isMemberDead("[443] Bdlt ☃ (Berserk XLVII)"))
        assertEquals(false, TabListParser.isMemberDead("[443] Bdlt ☃ (EMPTY)"))
    }

    @Test
    fun `member names read from every tab format seen, with or without a class`() {
        assertEquals("Bdlt", TabListParser.parseMemberName("[417] Bdlt ⛃ (Healer XLIII)"))
        assertEquals("Bdlt", TabListParser.parseMemberName("[100] Bdlt (Archer 50)"))
        assertEquals("schrottsoftware", TabListParser.parseMemberName("[403] schrottsoftware ♲"))
        assertEquals("Mhas_90210", TabListParser.parseMemberName(" [50] Mhas_90210 "))
    }

    @Test
    fun `other tab lines aren't member names`() {
        for (line in listOf("Bank: 2.8B", "Secrets Found: 0%", "Score: 260 (A)", "Water Board: [✔] (Steve)", "", "[MVP+] Bdlt")) {
            assertNull(TabListParser.parseMemberName(line), line)
        }
    }

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
    fun `tells the secrets count and percent lines apart`() {
        assertEquals(12, TabListParser.parseSecretsFound(" Secrets Found: 12"))
        assertNull(TabListParser.parseSecretsFound(" Secrets Found: 26.1%"))
        assertEquals(26.1, TabListParser.parseSecretsPercent(" Secrets Found: 26.1%"))
        assertEquals(2.0, TabListParser.parseSecretsPercent(" Secrets Found: 2%"))
        assertNull(TabListParser.parseSecretsPercent(" Secrets Found: 12"))
        assertNull(TabListParser.parseSecretsPercent(" Crypts: 2"))
    }

    @Test
    fun `returns null for roster lines missing the dungeon icon`() {
        // Confirmed necessary on a live run: Hypixel's tab list has other decorative/fake
        // entries with a similar "(Word Numeral)" shape that aren't real player rows.
        assertNull(TabListParser.parsePlayerClassLine("[417] Bdlt (Healer XLIII)"))
    }
}
