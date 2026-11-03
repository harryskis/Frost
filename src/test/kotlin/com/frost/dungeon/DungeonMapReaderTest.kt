package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DungeonMapReaderTest {
    @Test
    fun `a marker in the middle of a tile's cell reads as that tile's world center`() {
        val layout = DungeonMapReader.PixelLayout(roomSize = 16, startX = 5, startZ = 25)
        // Cell centers are at pixel start + tile * 20 + 8, and raw values are (pixel - 64) * 2.
        val marker = DungeonMapReader.readMarker((-102).toByte(), (-22 + 40).toByte(), 4, layout)
        assertEquals(-185.0, marker.worldX) // tile x 0
        assertEquals(-185.0 + 2 * 32, marker.worldZ) // tile z 2
        assertEquals(90f, marker.yaw)
    }

    private val mapSize = 128
    private val entrance = RoomType.ENTRANCE.colorId
    private val normal = RoomType.NORMAL.colorId
    private val gray = RoomType.UNKNOWN.colorId

    private fun paint(colors: ByteArray, layout: DungeonMapReader.PixelLayout, tile: GridPos, color: Byte) {
        val originX = layout.startX + tile.x * layout.pitch
        val originZ = layout.startZ + tile.z * layout.pitch
        for (z in originZ until originZ + layout.roomSize) {
            for (x in originX until originX + layout.roomSize) colors[z * mapSize + x] = color
        }
    }

    private fun mapWith(layout: DungeonMapReader.PixelLayout, vararg rooms: Pair<GridPos, Byte>): ByteArray {
        val colors = ByteArray(mapSize * mapSize)
        for ((tile, color) in rooms) paint(colors, layout, tile, color)
        return colors
    }

    @Test
    fun `measures the layout of a full-size map from its entrance room`() {
        val truth = DungeonMapReader.PixelLayout(16, 5, 5)
        assertEquals(truth, DungeonMapReader.findLayout(mapWith(truth, GridPos(2, 5) to entrance)))
    }

    @Test
    fun `measures the larger rooms and offset margins of smaller floors`() {
        val truth = DungeonMapReader.PixelLayout(18, 11, 11)
        assertEquals(truth, DungeonMapReader.findLayout(mapWith(truth, GridPos(1, 3) to entrance)))
    }

    @Test
    fun `a margin measuring zero means the smallest layout's 22 pixels`() {
        // Entrance at the 22-pixel margin on tile 0 reads as 22 % 22 = 0 on that axis.
        val truth = DungeonMapReader.PixelLayout(18, 22, 11)
        assertEquals(truth, DungeonMapReader.findLayout(mapWith(truth, GridPos(0, 2) to entrance)))
    }

    @Test
    fun `no entrance yet or the wrong size of map gives no layout`() {
        assertNull(DungeonMapReader.findLayout(ByteArray(mapSize * mapSize)))
        assertNull(DungeonMapReader.findLayout(ByteArray(10)))
    }

    @Test
    fun `a map with a painted top-left corner isn't a dungeon map`() {
        val layout = DungeonMapReader.PixelLayout(16, 5, 5)
        val colors = mapWith(layout, GridPos(2, 5) to entrance)
        colors[0] = normal
        assertNull(DungeonMapReader.findLayout(colors))
    }

    @Test
    fun `a stray run of entrance color that isn't room-sized is skipped`() {
        val truth = DungeonMapReader.PixelLayout(16, 5, 5)
        val colors = mapWith(truth, GridPos(2, 5) to entrance)
        for (x in 1..3) colors[x] = entrance
        assertEquals(truth, DungeonMapReader.findLayout(colors))
    }

    @Test
    fun `rooms with a real color count as opened, gray and blank ones don't`() {
        val layout = DungeonMapReader.PixelLayout(16, 5, 5)
        val colors = mapWith(layout, GridPos(2, 5) to entrance, GridPos(2, 4) to normal, GridPos(1, 4) to gray)
        assertEquals(setOf(GridPos(2, 5), GridPos(2, 4)), DungeonMapReader.openedTiles(colors, layout))
    }

    private fun paintIcon(colors: ByteArray, layout: DungeonMapReader.PixelLayout, tile: GridPos, offsetX: Int, offsetZ: Int, color: Byte) {
        val x = layout.startX + tile.x * layout.pitch + offsetX
        val z = layout.startZ + tile.z * layout.pitch + offsetZ
        colors[z * mapSize + x] = color
    }

    @Test
    fun `checkmarks are found anywhere in a cell, and green beats white`() {
        val layout = DungeonMapReader.PixelLayout(16, 5, 5)
        val white = GridPos(1, 1)
        val green = GridPos(2, 1)
        val both = GridPos(3, 1)
        val untouched = GridPos(4, 1)
        val colors = mapWith(layout, white to normal, green to normal, both to normal, untouched to normal)
        paintIcon(colors, layout, white, 2, 13, MapCheckmark.WHITE.colorId)
        paintIcon(colors, layout, green, 12, 3, MapCheckmark.GREEN.colorId)
        paintIcon(colors, layout, both, 8, 8, MapCheckmark.GREEN.colorId)
        paintIcon(colors, layout, both, 9, 8, MapCheckmark.WHITE.colorId)
        assertEquals(
            mapOf(white to MapCheckmark.WHITE, green to MapCheckmark.GREEN, both to MapCheckmark.GREEN),
            DungeonMapReader.checkmarks(colors, layout),
        )
    }

    @Test
    fun `the entrance's own green fill isn't a checkmark`() {
        val layout = DungeonMapReader.PixelLayout(16, 5, 5)
        assertEquals(emptyMap(), DungeonMapReader.checkmarks(mapWith(layout, GridPos(2, 5) to entrance), layout))
    }

    @Test
    fun `tiles that fall off the edge of a small floor's map are just not opened`() {
        val layout = DungeonMapReader.PixelLayout(18, 22, 22)
        val colors = mapWith(layout, GridPos(0, 0) to entrance)
        assertEquals(setOf(GridPos(0, 0)), assertNotNull(DungeonMapReader.openedTiles(colors, layout)))
    }
}
