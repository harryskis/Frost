package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomCreditTrackerTest {
    @Test
    fun `credits everyone present in the cleared room`() {
        val tracker = RoomCreditTracker()

        val positions = mapOf(
            "Alice" to GridPos(0, 0),
            "Bob" to GridPos(0, 0),
            "Carl" to GridPos(9, 9), // elsewhere, not in the room that clears
        )

        val events = tracker.update(
            mapOf(GridPos(0, 0) to MapCheckmark.GREEN),
            mapOf(GridPos(0, 0) to RoomType.NORMAL),
            positions,
        )

        assertEquals(1, events.size)
        assertEquals(setOf(GridPos(0, 0)), events[0].room)
        assertEquals(setOf("Alice", "Bob"), events[0].playersPresent)
    }

    @Test
    fun `reports a separate event for each newly-cleared room across multiple updates`() {
        val tracker = RoomCreditTracker()
        val roomTypes = mapOf(GridPos(0, 0) to RoomType.NORMAL, GridPos(1, 1) to RoomType.NORMAL)

        tracker.update(mapOf(GridPos(0, 0) to MapCheckmark.GREEN), roomTypes, mapOf("Alice" to GridPos(0, 0)))
        val events = tracker.update(
            mapOf(GridPos(0, 0) to MapCheckmark.GREEN, GridPos(1, 1) to MapCheckmark.GREEN),
            roomTypes,
            mapOf("Alice" to GridPos(1, 1)),
        )

        assertEquals(1, events.size) // GridPos(0,0) already cleared last update, not reported again
        assertEquals(setOf(GridPos(1, 1)), events[0].room)
        assertEquals(setOf("Alice"), events[0].playersPresent)
    }

    @Test
    fun `credits the tile-closest player when nobody is exactly in the cleared room`() {
        // Confirmed on a live (fast M5 speed-clear) run: a fast-clearing party is routinely
        // already a room or two ahead by the time a clear is detected, so exact presence
        // alone came up empty for every single room that whole floor.
        val tracker = RoomCreditTracker()

        val positions = mapOf(
            "Alice" to GridPos(5, 5), // far away
            "Bob" to GridPos(1, 0), // one tile from the cleared room
        )
        val events = tracker.update(
            mapOf(GridPos(0, 0) to MapCheckmark.WHITE),
            mapOf(GridPos(0, 0) to RoomType.NORMAL),
            positions,
        )

        assertEquals(setOf("Bob"), events.single().playersPresent)
    }

    @Test
    fun `reports no credited players but still reports the clear when no positions are known`() {
        val tracker = RoomCreditTracker()

        val events = tracker.update(
            mapOf(GridPos(0, 0) to MapCheckmark.WHITE),
            mapOf(GridPos(0, 0) to RoomType.NORMAL),
            emptyMap(),
        )

        assertEquals(1, events.size)
        assertTrue(events[0].playersPresent.isEmpty())
    }

    @Test
    fun `still credits a solo clearer who already left the room by the time the clear is detected`() {
        // Confirmed on a live run: a player solo-cleared a small room then immediately
        // walked on, and by the poll where the checkmark flip was actually detected, two
        // unrelated teammates happened to be passing through that same room instead -
        // exact-presence-right-now alone credited THEM, not the real solo clearer.
        val tracker = RoomCreditTracker()
        val roomTypes = mapOf(GridPos(0, 0) to RoomType.NORMAL)

        // Poll 1: Alice is alone in the room that's about to clear (still uncleared).
        tracker.update(mapOf(GridPos(0, 0) to MapCheckmark.UNDISCOVERED), roomTypes, mapOf("Alice" to GridPos(0, 0)))

        // Poll 2: Alice has moved on; Bob and Carl are now standing in that room instead,
        // and this poll is the one where the checkmark flip is first observed.
        val events = tracker.update(
            mapOf(GridPos(0, 0) to MapCheckmark.GREEN),
            roomTypes,
            mapOf("Alice" to GridPos(1, 1), "Bob" to GridPos(0, 0), "Carl" to GridPos(0, 0)),
        )

        assertEquals(setOf("Alice", "Bob", "Carl"), events.single().playersPresent)
    }

    @Test
    fun `does not merge two unrelated rooms of different types that clear on the same poll`() {
        // Confirmed on a live run: a 1x1 TRAP room and an adjacent, entirely separate
        // NORMAL room both cleared within the same ~250ms poll window, and grid-adjacency
        // alone treated that as one 2-tile room - pooling credit from both real rooms
        // together and dropping the TRAP room's actual (solo) clearer, since the other
        // room's occupants ended up counted as "present" for the merged blob instead.
        val tracker = RoomCreditTracker()

        val events = tracker.update(
            mapOf(GridPos(0, 0) to MapCheckmark.WHITE, GridPos(1, 0) to MapCheckmark.WHITE),
            mapOf(GridPos(0, 0) to RoomType.TRAP, GridPos(1, 0) to RoomType.NORMAL),
            mapOf("Alice" to GridPos(0, 0), "Bob" to GridPos(1, 0)),
        )

        assertEquals(2, events.size)
        val trapEvent = events.single { it.room == setOf(GridPos(0, 0)) }
        val normalEvent = events.single { it.room == setOf(GridPos(1, 0)) }
        assertEquals(setOf("Alice"), trapEvent.playersPresent)
        assertEquals(setOf("Bob"), normalEvent.playersPresent)
    }

    @Test
    fun `does not credit the only trackable player when they are nowhere near the room`() {
        // Confirmed on a live run: teammates' entities are frequently untracked entirely
        // (out of range in a large dungeon) while the LOCAL player's position is always
        // known, so an unbounded "closest of whoever we have" fallback kept crediting the
        // local player even when they were 5+ rooms away from the room that actually
        // cleared. A too-far-away sole candidate should mean "unknown", not "them by
        // default".
        val tracker = RoomCreditTracker()

        val events = tracker.update(
            mapOf(GridPos(0, 0) to MapCheckmark.WHITE),
            mapOf(GridPos(0, 0) to RoomType.NORMAL),
            mapOf("Alice" to GridPos(5, 5)),
        )

        assertTrue(events.single().playersPresent.isEmpty())
    }
}
