package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LRoomLayoutsTest {
    // An L's tiles by core: 10 the corner, 20 and 30 its arms.
    private val corner = 10
    private val armA = 20
    private val armB = 30

    /** The L with its corner at [at], turned [quarterTurns] times. */
    private fun lRoom(at: GridPos, quarterTurns: Int): Map<GridPos, Int> {
        var a = 1 to 0
        var b = 0 to 1
        repeat(quarterTurns) {
            a = -a.second to a.first
            b = -b.second to b.first
        }
        return mapOf(at to corner, GridPos(at.x + a.first, at.z + a.second) to armA, GridPos(at.x + b.first, at.z + b.second) to armB)
    }

    @Test
    fun `once seen whole, any two of an L's tiles place the third, whichever way it's turned`() {
        val layout = LRoomLayouts.learn(lRoom(GridPos(2, 2), 0))!!
        assertEquals(corner, layout.corner)
        for (turns in 0..3) {
            val room = lRoom(GridPos(2, 2), turns)
            // Learning it turned any other way gives the same layout.
            assertEquals(layout, LRoomLayouts.learn(room), "turned $turns")
            for (missing in room.keys) {
                assertEquals(missing, LRoomLayouts.missingTile(room - missing, layout), "turned $turns, missing $missing")
            }
        }
    }

    @Test
    fun `tiles that aren't an L, or don't match its layout, place nothing`() {
        assertNull(LRoomLayouts.learn(mapOf(GridPos(0, 0) to 1, GridPos(1, 0) to 2, GridPos(2, 0) to 3)))
        val layout = LRoomLayout(corner, armA, armB)
        assertNull(LRoomLayouts.missingTile(mapOf(GridPos(0, 0) to 99, GridPos(1, 0) to 98), layout))
    }

    @Test
    fun `Withermancer's last tile goes under its corner once its layout is known`() {
        // As in game: its two top tiles loaded, Supertall above them, nothing below loaded yet.
        val withermancer = RoomDatabase.RoomInfo("Withermancer", "NORMAL", "L")
        val supertall = RoomDatabase.RoomInfo("Supertall", "NORMAL", "2x2")
        val rooms = mapOf(
            GridPos(4, 2) to supertall, GridPos(5, 2) to supertall, GridPos(4, 3) to supertall, GridPos(5, 3) to supertall,
            GridPos(4, 4) to withermancer, GridPos(5, 4) to withermancer,
        )
        // Seen whole in an earlier run, turned another way: corner 10 with arms 20 and 30.
        val layout = LRoomLayouts.learn(lRoom(GridPos(1, 1), 2))!!
        val cores = mapOf(GridPos(4, 4) to 10, GridPos(5, 4) to 20)
        val completed = LRoomLayouts.complete(rooms, cores) { if (it == "Withermancer") layout else null }
        val third = completed.filterValues { it == withermancer }.keys - setOf(GridPos(4, 4), GridPos(5, 4))
        assertEquals(1, third.size)
        // Never seen whole: nothing is guessed.
        assertEquals(rooms, LRoomLayouts.complete(rooms, cores) { null })
    }
}
