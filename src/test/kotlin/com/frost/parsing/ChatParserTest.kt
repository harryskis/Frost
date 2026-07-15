package com.frost.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatParserTest {
    @Test
    fun `recognizes the Mort good luck start line`() {
        assertTrue(ChatParser.isDungeonStartMessage("[NPC] Mort: Good luck."))
    }

    @Test
    fun `ignores unrelated chat lines`() {
        assertFalse(ChatParser.isDungeonStartMessage("Party > Steve: good luck everyone"))
        assertFalse(ChatParser.isDungeonStartMessage("[NPC] Mort: Welcome to the dungeon hub!"))
    }

    @Test
    fun `recognizes the boss-defeated line regardless of boss name or time`() {
        assertTrue(ChatParser.isBossDefeatedMessage("                       ☠ Defeated Livid in 02m 17s"))
        assertTrue(ChatParser.isBossDefeatedMessage("☠ Defeated Necron in 05m 03s"))
    }

    @Test
    fun `does not mistake other chat for the boss-defeated line`() {
        assertFalse(ChatParser.isBossDefeatedMessage("Team Score: 301 (S+)"))
        assertFalse(ChatParser.isBossDefeatedMessage("[BOSS] Livid: I'll be your undoing."))
    }

    @Test
    fun `extracts the solver name from puzzle-solved chat regardless of flavor text`() {
        assertEquals("Bdlt", ChatParser.parsePuzzleSolvedSolver("PUZZLE SOLVED! Bdlt wasn't fooled by Melrose! Good job!"))
        assertEquals("Bdlt", ChatParser.parsePuzzleSolvedSolver("PUZZLE SOLVED! Bdlt tied Tic Tac Toe! Good job!"))
    }

    @Test
    fun `returns null for chat that isn't a puzzle-solved message`() {
        assertNull(ChatParser.parsePuzzleSolvedSolver("PUZZLE FAILED! Nobody could figure it out."))
    }

    @Test
    fun `extracts the finder from the Quiz dungeon buff message`() {
        assertEquals("RemDoggyDog", ChatParser.parseQuizBuffFinder("DUNGEON BUFF! RemDoggyDog found a Blessing of Time V!"))
        assertEquals("You", ChatParser.parseQuizBuffFinder("DUNGEON BUFF! You found a Blessing of Time V!"))
    }

    @Test
    fun `tolerates the elapsed-time suffix shown to other party members`() {
        assertEquals(
            "_hakimi",
            ChatParser.parseQuizBuffFinder("DUNGEON BUFF! _hakimi found a Blessing of Time V! (01m 41s)"),
        )
    }

    @Test
    fun `does not match unrelated blessing types - confirmed to cause massive over-crediting`() {
        assertNull(ChatParser.parseQuizBuffFinder("DUNGEON BUFF! ManSui found a Blessing of Wisdom V! (04s)"))
        assertNull(ChatParser.parseQuizBuffFinder("DUNGEON BUFF! You found a Blessing of Life I!"))
        assertNull(ChatParser.parseQuizBuffFinder("DUNGEON BUFF! Ostad found a Blessing of Stone I!"))
    }

    @Test
    fun `returns null for chat that isn't a dungeon buff message`() {
        assertNull(ChatParser.parseQuizBuffFinder("You found a Blessing of Time V!"))
    }
}
