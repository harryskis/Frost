package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DungeonGridTest {
    @Test
    fun `players apart from the main group are credited`() {
        val cells = mapOf(
            "Alice" to GridPos(0, 0),
            "Bob" to GridPos(0, 0),
            "Carl" to GridPos(0, 0),
            "Dana" to GridPos(1, 1),
        )

        val credited = DungeonGrid.playersApartFromMainGroup(cells)

        assertEquals(setOf("Dana"), credited)
    }

    @Test
    fun `returns empty when everyone is in the same room`() {
        val cells = mapOf(
            "Alice" to GridPos(0, 0),
            "Bob" to GridPos(0, 0),
        )

        assertTrue(DungeonGrid.playersApartFromMainGroup(cells).isEmpty())
    }

    @Test
    fun `physicalRoomCorner matches the reference implementation's 32-block-plus-8-offset grid`() {
        // Confirmed against Skyblocker's DungeonMapUtils.getPhysicalRoomPos: shift by 8.5,
        // floor to the nearest 32-block boundary, shift back by 8.
        assertEquals(-8 to -8, DungeonGrid.physicalRoomCorner(0.0, 0.0))
        assertEquals(-8 to -8, DungeonGrid.physicalRoomCorner(23.0, 10.0))
        assertEquals(24 to -8, DungeonGrid.physicalRoomCorner(24.0, 0.0))
    }

    @Test
    fun `worldToRoomTile and roomTileWorldCenter are calibrated inverses of each other`() {
        val corner = -8 to -8
        val entrance = GridPos(3, 5)
        val center = DungeonGrid.roomTileWorldCenter(GridPos(3, 4), corner, entrance)
        val tile = DungeonGrid.worldToRoomTile(center.first, center.second, corner, entrance)
        assertEquals(GridPos(3, 4), tile)
    }

    @Test
    fun `worldToRoomTile places the entrance-anchoring position back at the entrance tile`() {
        val corner = DungeonGrid.physicalRoomCorner(-57.5, -27.9)
        val entrance = GridPos(4, 5)
        assertEquals(entrance, DungeonGrid.worldToRoomTile(-57.5, -27.9, corner, entrance))
        // Moving a full room-width away lands one tile over.
        assertEquals(GridPos(5, 5), DungeonGrid.worldToRoomTile(-57.5 + 32, -27.9, corner, entrance))
    }
}
