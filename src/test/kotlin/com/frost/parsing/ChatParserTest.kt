package com.frost.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatParserTest {
    @Test
    fun `key pickups are read from either of Hypixel's messages, with or without a rank`() {
        assertEquals(DungeonKey.WITHER, ChatParser.parseKeyPickup("[MVP+] Steve has obtained Wither Key!"))
        assertEquals(DungeonKey.WITHER, ChatParser.parseKeyPickup("Steve has obtained Wither Key!"))
        assertEquals(DungeonKey.BLOOD, ChatParser.parseKeyPickup("[VIP] alex_2 has obtained Blood Key!"))
        assertEquals(DungeonKey.WITHER, ChatParser.parseKeyPickup("A Wither Key was picked up!"))
        assertEquals(DungeonKey.BLOOD, ChatParser.parseKeyPickup("  A Blood Key was picked up!  "))
    }

    @Test
    fun `a prince kill is Hypixel's own line, or a party's mod message on its own`() {
        assertTrue(PrinceKill.isKill("A Prince falls. +1 Bonus Score"))
        assertTrue(PrinceKill.isKill("Party > [MVP+] Steve: Prince Killed!"))
        assertTrue(PrinceKill.isKill("Party > Steve: prince dead"))
        assertTrue(PrinceKill.isKill("Party > [VIP] [Frost] alex_2: Prince Slain"))
        assertTrue(PrinceKill.isKill("Party > Steve: \$skytils-dungeon-score-prince\$"))
    }

    @Test
    fun `only Hypixel's own line is this player's prince kill`() {
        assertTrue(PrinceKill.isOwnKill("A Prince falls. +1 Bonus Score"))
        assertFalse(PrinceKill.isOwnKill("Party > Steve: Prince Killed!"))
    }

    @Test
    fun `a charmed Mimic counts, and so does a party saying it's dead in any mod's words`() {
        assertTrue(MimicKill.isCharmed("CHARM You charmed a Mimic and captured 2 shards from it."))
        assertTrue(MimicKill.isCharmed("CHARM You charmed a Mimic and captured its Shard."))
        assertTrue(MimicKill.isAnnounced("Party > [MVP+] Steve: Mimic Killed!"))
        assertTrue(MimicKill.isAnnounced("Party > Steve: child destroyed!"))
        assertTrue(MimicKill.isAnnounced("Party > Steve: \$skytils-dungeon-score-mimic\$"))
        assertFalse(MimicKill.isAnnounced("Party > Steve: did anyone kill the mimic killed thing"))
        assertFalse(MimicKill.isAnnounced("[MVP+] Steve: Mimic Killed!"))
    }

    @Test
    fun `a bat kill is Hypixel's own line`() {
        assertTrue(BatKill.isOwnKill("A Bat has been slain. +1 Bonus Score"))
        assertFalse(BatKill.isOwnKill("Party > Steve: Bat Killed!"))
    }

    @Test
    fun `chat that only mentions a prince isn't a kill`() {
        assertFalse(PrinceKill.isKill("Party > Steve: is the prince killed yet?"))
        assertFalse(PrinceKill.isKill("[MVP+] Steve: Prince Killed!"))
        assertFalse(PrinceKill.isKill("Steve: A Prince falls. +1 Bonus Score"))
        assertFalse(PrinceKill.isKill("Mimic Killed!"))
    }

    @Test
    fun `a player typing a key message doesn't count as a pickup`() {
        assertEquals(null, ChatParser.parseKeyPickup("Party > [MVP+] Steve: A Wither Key was picked up!"))
        assertEquals(null, ChatParser.parseKeyPickup("[MVP+] Steve: Alex has obtained Blood Key!"))
        assertEquals(null, ChatParser.parseKeyPickup("Steve has obtained Superboom TNT!"))
    }

    @Test
    fun `isBloodDoorOpenedMessage matches the announcement, but not someone typing it`() {
        assertTrue(ChatParser.isBloodDoorOpenedMessage("The BLOOD DOOR has been opened!"))
        assertFalse(ChatParser.isBloodDoorOpenedMessage("Someone: The BLOOD DOOR has been opened!"))
    }

    @Test
    fun `isRunSummaryMessage matches the centered heading, but not someone typing it`() {
        assertTrue(ChatParser.isRunSummaryMessage("                             > EXTRA STATS <"))
        assertFalse(ChatParser.isRunSummaryMessage("[MVP+] Someone: > EXTRA STATS <"))
    }

    @Test
    fun `parseLeapTarget reads the name from the teleport line, but not from someone typing it`() {
        assertEquals("weepcore", ChatParser.parseLeapTarget("You have teleported to weepcore!"))
        assertNull(ChatParser.parseLeapTarget("[MVP+] Someone: You have teleported to weepcore!"))
        assertNull(ChatParser.parseLeapTarget("Party > Someone: Leaped to weepcore!"))
    }

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
        assertNull(ChatParser.parseQuizBuffFinder("DUNGEON BUFF! Skyseeker found a Blessing of Stone I!"))
    }

    @Test
    fun `returns null for chat that isn't a dungeon buff message`() {
        assertNull(ChatParser.parseQuizBuffFinder("You found a Blessing of Time V!"))
    }
}
