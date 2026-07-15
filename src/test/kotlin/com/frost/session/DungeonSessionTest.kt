package com.frost.session

import com.frost.dungeon.GridPos
import com.frost.parsing.PuzzleStatus
import com.frost.parsing.PuzzleStatusUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DungeonSessionTest {
    @Test
    fun `secrets found is the diff between baseline and final snapshots`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        session.secretsBaseline["Alice"] = 100
        session.secretsFinal["Alice"] = 107

        assertEquals(7, session.secretsFoundThisRun("Alice"))
    }

    @Test
    fun `secrets found is null when either snapshot is missing`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        session.secretsBaseline["Alice"] = 100

        assertNull(session.secretsFoundThisRun("Alice"))
    }

    @Test
    fun `puzzle solved is only credited on the transition into completed`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))

        session.handlePuzzleUpdate(PuzzleStatusUpdate("Water Board", PuzzleStatus.INCOMPLETE, null))
        session.handlePuzzleUpdate(PuzzleStatusUpdate("Water Board", PuzzleStatus.COMPLETED, "Alice"))
        // re-polled the same completed state on later ticks; must not double-count
        session.handlePuzzleUpdate(PuzzleStatusUpdate("Water Board", PuzzleStatus.COMPLETED, "Alice"))
        session.handlePuzzleUpdate(PuzzleStatusUpdate("Water Board", PuzzleStatus.COMPLETED, "Alice"))

        assertEquals(1, session.puzzlesSolved["Alice"])
    }

    @Test
    fun `puzzle credits never exceed the dungeon's real total puzzle count`() {
        // Confirmed on a live run: independent credit paths (chat, tab-list presence
        // fallback) can both fire for the same real completion, so the run's actual total
        // (from the tab list's "Puzzles: (N)") is a hard ceiling no path may exceed.
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice"), PartyMember("Bob")))
        session.totalPuzzles = 2

        assertEquals(true, session.recordPuzzleSolved("Alice"))
        assertEquals(true, session.recordPuzzleSolved("Bob"))
        assertEquals(false, session.recordPuzzleSolved("Alice")) // would be a 3rd credit - refused

        assertEquals(1, session.puzzlesSolved["Alice"])
        assertEquals(1, session.puzzlesSolved["Bob"])
        assertEquals(2, session.totalPuzzlesCredited())
    }

    @Test
    fun `puzzle credits are unbounded when the total puzzle count is unknown`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))

        repeat(5) { session.recordPuzzleSolved("Alice") }

        assertEquals(5, session.puzzlesSolved["Alice"])
    }

    @Test
    fun `state resets cleanly when a new run starts`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        session.recordPuzzleSolved("Alice")
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0)))
        session.end()

        session.start(listOf(PartyMember("Bob")))

        assertEquals(0, session.puzzlesSolved.size)
        assertEquals(0, session.teamRoomsCleared)
        assertEquals(0, session.soloClearedRooms.size)
        assertEquals(0, session.stackClearedRooms.size)
        assertEquals(SessionState.ACTIVE, session.state)
    }

    @Test
    fun `a room cleared by exactly one player is filed as solo`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice"), PartyMember("Bob")))

        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0)))

        assertEquals<List<Set<GridPos>>?>(listOf(setOf(GridPos(0, 0))), session.soloClearedRooms["Alice"])
        assertNull(session.stackClearedRooms["Alice"])
        assertNull(session.soloClearedRooms["Bob"])
    }

    @Test
    fun `a room cleared by multiple players is filed as stack for all of them`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice"), PartyMember("Bob")))

        session.recordRoomCleared(listOf("Alice", "Bob"), setOf(GridPos(1, 1)))

        assertEquals<List<Set<GridPos>>?>(listOf(setOf(GridPos(1, 1))), session.stackClearedRooms["Alice"])
        assertEquals<List<Set<GridPos>>?>(listOf(setOf(GridPos(1, 1))), session.stackClearedRooms["Bob"])
        assertNull(session.soloClearedRooms["Alice"])
    }

    @Test
    fun `teamRoomsCleared counts every clear even when nobody was credited`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))

        session.recordRoomCleared(emptyList(), setOf(GridPos(0, 0)))

        assertEquals(1, session.teamRoomsCleared)
        assertEquals(0, session.soloClearedRooms.size)
    }

    @Test
    fun `a multi-tile room is stored as one entry covering all its tiles`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))

        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0), GridPos(0, 1)))

        assertEquals<List<Set<GridPos>>?>(listOf(setOf(GridPos(0, 0), GridPos(0, 1))), session.soloClearedRooms["Alice"])
    }
}
