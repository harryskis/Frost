package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomClearTrackerTest {
    @Test
    fun `detects a room newly turning white as cleared`() {
        val previous = mapOf(GridPos(0, 0) to MapCheckmark.UNDISCOVERED)
        val current = mapOf(GridPos(0, 0) to MapCheckmark.WHITE)

        val cleared = RoomClearTracker.detectNewlyClearedRooms(previous, current)

        assertEquals(1, cleared.size)
        assertEquals(setOf(GridPos(0, 0)), cleared.single())
    }

    @Test
    fun `does not re-report a room that goes from white to green`() {
        // GREEN means that room's secrets are also 100% found - a stricter follow-on
        // state, not a separate "cleared" event; the room was already credited at WHITE.
        val previous = mapOf(GridPos(0, 0) to MapCheckmark.WHITE)
        val current = mapOf(GridPos(0, 0) to MapCheckmark.GREEN)

        val cleared = RoomClearTracker.detectNewlyClearedRooms(previous, current)

        assertTrue(cleared.isEmpty())
    }

    @Test
    fun `groups adjacent newly-cleared cells into one room`() {
        val previous = mapOf(
            GridPos(0, 0) to MapCheckmark.UNDISCOVERED,
            GridPos(1, 0) to MapCheckmark.UNDISCOVERED,
        )
        val current = mapOf(
            GridPos(0, 0) to MapCheckmark.WHITE,
            GridPos(1, 0) to MapCheckmark.WHITE,
        )

        val cleared = RoomClearTracker.detectNewlyClearedRooms(previous, current)

        assertEquals(1, cleared.size)
        assertEquals(setOf(GridPos(0, 0), GridPos(1, 0)), cleared.single())
    }

    @Test
    fun `does not re-report a room that was already cleared`() {
        val previous = mapOf(GridPos(0, 0) to MapCheckmark.WHITE)
        val current = mapOf(GridPos(0, 0) to MapCheckmark.WHITE)

        val cleared = RoomClearTracker.detectNewlyClearedRooms(previous, current)

        assertTrue(cleared.isEmpty())
    }

    @Test
    fun `treats non-adjacent newly-cleared cells as separate rooms`() {
        val previous = emptyMap<GridPos, MapCheckmark>()
        val current = mapOf(
            GridPos(0, 0) to MapCheckmark.WHITE,
            GridPos(5, 5) to MapCheckmark.GREEN,
        )

        val cleared = RoomClearTracker.detectNewlyClearedRooms(previous, current)

        assertEquals(2, cleared.size)
    }

    @Test
    fun `does not merge two adjacent newly-cleared cells of different room types`() {
        // Confirmed on a live run: a 1x1 TRAP room and a wholly separate NORMAL room next
        // to it on the grid both happened to clear within the same ~250ms poll window -
        // grid-adjacency plus same-tick timing alone can't tell that apart from a genuine
        // 2-tile room, so it needs the room type too (a real multi-cell room reports the
        // same type at every one of its cells; two coincidentally-simultaneous separate
        // rooms essentially never do).
        val previous = mapOf(
            GridPos(0, 0) to MapCheckmark.UNDISCOVERED,
            GridPos(1, 0) to MapCheckmark.UNDISCOVERED,
        )
        val current = mapOf(
            GridPos(0, 0) to MapCheckmark.WHITE,
            GridPos(1, 0) to MapCheckmark.WHITE,
        )
        val roomTypes = mapOf(GridPos(0, 0) to RoomType.TRAP, GridPos(1, 0) to RoomType.NORMAL)

        val cleared = RoomClearTracker.detectNewlyClearedRooms(previous, current, roomTypes)

        assertEquals(2, cleared.size)
        assertTrue(setOf(GridPos(0, 0)) in cleared)
        assertTrue(setOf(GridPos(1, 0)) in cleared)
    }

    @Test
    fun `does not merge a newly-cleared cell into an already-cleared neighboring room`() {
        // Confirmed on a live run: once enough of the dungeon is cleared, a brand new room
        // clear next to an OLD cleared room (a different room entirely - they're just
        // adjacent on the grid) was being flood-filled together into a single reported
        // "room", collapsing what should have been many separate teamRoomsCleared/credit
        // events into one and badly undercounting both.
        val previous = mapOf(
            GridPos(0, 0) to MapCheckmark.WHITE, // cleared several ticks ago
            GridPos(1, 0) to MapCheckmark.UNDISCOVERED,
        )
        val current = mapOf(
            GridPos(0, 0) to MapCheckmark.WHITE,
            GridPos(1, 0) to MapCheckmark.WHITE, // just cleared this tick
        )

        val cleared = RoomClearTracker.detectNewlyClearedRooms(previous, current)

        assertEquals(1, cleared.size)
        assertEquals(setOf(GridPos(1, 0)), cleared.single())
    }
}
