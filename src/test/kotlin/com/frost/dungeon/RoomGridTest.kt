package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomGridTest {
    @Test
    fun `parses master mode floor labels`() {
        assertEquals(5, RoomGrid.parseFloorNumber("M5"))
        assertEquals(7, RoomGrid.parseFloorNumber("M7"))
    }

    @Test
    fun `parses roman numeral floor labels`() {
        assertEquals(7, RoomGrid.parseFloorNumber("VII"))
        assertEquals(1, RoomGrid.parseFloorNumber("I"))
    }

    @Test
    fun `unknown floor labels return null`() {
        assertNull(RoomGrid.parseFloorNumber("Entrance"))
        assertNull(RoomGrid.parseFloorNumber(null))
    }

    @Test
    fun `decodes a puzzle room from its corner pixel and locates it`() {
        val colors = ByteArray(128 * 128) { -1 }
        // Floor 5 -> roomSize 16, gap 20, margin (128-(16*6+4*5))/2 = 6.
        // Cell (2, 1)'s corner sits at (6 + 2*20, 6 + 1*20) = (46, 26).
        val cornerX = 6 + 2 * 20
        val cornerZ = 6 + 1 * 20
        colors[cornerZ * 128 + cornerX] = RoomType.PUZZLE.colorId
        colors[(cornerZ + 8) * 128 + (cornerX + 8)] = MapCheckmark.GREEN.colorId

        val grid = RoomGrid()
        val cells = grid.decode(colors, floorNumber = 5)

        val puzzleRoom = grid.findRoomOfType(cells, RoomType.PUZZLE)
        assertEquals(GridPos(2, 1), puzzleRoom)
        assertEquals(MapCheckmark.GREEN, cells[GridPos(2, 1)]?.checkmark)
    }

    @Test
    fun `returns null when no room of the requested type exists`() {
        val colors = ByteArray(128 * 128) { -1 }
        val grid = RoomGrid()
        val cells = grid.decode(colors, floorNumber = 5)
        assertNull(grid.findRoomOfType(cells, RoomType.PUZZLE))
    }

    @Test
    fun `dumps a full cell's pixel block at the right offset`() {
        val colors = ByteArray(128 * 128) { -1 }
        val cornerX = 6 // margin for floor 5
        val cornerZ = 6
        colors[cornerZ * 128 + cornerX] = 42 // top-left of cell (0,0)

        val grid = RoomGrid()
        val dump = grid.dumpCellPixels(colors, floorNumber = 5, pos = GridPos(0, 0))

        val firstRow = dump.substringBefore(" / ")
        assertEquals("42", firstRow.substringBefore(","))
    }

    @Test
    fun `finds a checkmark pixel away from the assumed center`() {
        val colors = ByteArray(128 * 128) { -1 }
        val originX = 6 // margin for floor 5, cell (0,0)
        val originZ = 6
        // Plant a WHITE pixel near the top of the cell, not at the geometric center.
        colors[originZ * 128 + (originX + 8)] = MapCheckmark.WHITE.colorId

        val grid = RoomGrid()
        val hits = grid.findCheckmarkPixels(colors, floorNumber = 5, pos = GridPos(0, 0))

        assertEquals(listOf("(row=0,col=8)=WHITE(34)"), hits)
    }

    @Test
    fun `pixelToTile matches decode's own per-cell origin math`() {
        val grid = RoomGrid()
        // Floor 5 -> roomSize 16, gap 20, margin 6. Cell (2, 1)'s corner sits at (46, 26)
        // per the earlier test - any pixel within that cell's 16x16 block should map back
        // to tile (2, 1).
        assertEquals(GridPos(2, 1), grid.pixelToTile(46, 26, floorNumber = 5))
        assertEquals(GridPos(2, 1), grid.pixelToTile(50, 30, floorNumber = 5))
        assertEquals(GridPos(0, 0), grid.pixelToTile(6, 6, floorNumber = 5))
    }
}
