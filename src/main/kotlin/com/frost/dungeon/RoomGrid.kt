package com.frost.dungeon

data class GridPos(val x: Int, val z: Int)
data class RoomCell(val type: RoomType, val checkmark: MapCheckmark, val rawCheckmarkByte: Byte)

/**
 * Decodes a dungeon map item's raw 128x128 palette-index byte array into a 6x6 grid of
 * rooms (confirmed grid size, cross-referenced against a live reference implementation).
 * Each cell's TYPE (puzzle/normal/trap/etc.) is sampled from its top-left corner pixel,
 * and its cleared/checkmark STATE from its center pixel - these are separate readings,
 * not the same value, which is why some color IDs collide between [RoomType] and
 * [MapCheckmark] without conflict.
 *
 * The per-room pixel size varies by floor (18px for floors 1-3, 16px for floors 4+,
 * with a 4px gap between cells) - also confirmed, not guessed. [ROOM_MARGIN] is derived
 * from those (the leftover space once 6 rooms + 5 gaps are laid out in a 128px map), not
 * independently confirmed - worth double-checking alignment against a live run.
 */
class RoomGrid {
    companion object {
        private const val GRID_SIZE = 6
        private const val MAP_SIZE = 128
        private const val CELL_GAP = 4

        fun roomPixelSize(floorNumber: Int?): Int = if (floorNumber != null && floorNumber <= 3) 18 else 16

        private fun margin(roomSize: Int): Int =
            (MAP_SIZE - (roomSize * GRID_SIZE + CELL_GAP * (GRID_SIZE - 1))) / 2

        /** Parses a floor label like "M5" (Master Mode) or "VII" (normal mode roman numeral). */
        fun parseFloorNumber(floor: String?): Int? {
            if (floor == null) return null
            val masterMode = floor.removePrefix("M").toIntOrNull()
            if (floor.startsWith("M") && masterMode != null) return masterMode
            val romanValues = linkedMapOf(
                "VII" to 7, "VI" to 6, "V" to 5, "IV" to 4, "III" to 3, "II" to 2, "I" to 1,
            )
            return romanValues[floor]
        }
    }

    private fun getPixel(colors: ByteArray, x: Int, z: Int): Byte {
        val idx = z * MAP_SIZE + x
        if (idx < 0 || idx >= colors.size) return -1
        return colors[idx]
    }

    // Highest-priority (most "complete") state wins when scanning a whole cell for
    // checkmark-colored pixels - GREEN (secrets done) outranks WHITE (just cleared).
    private val CHECKMARK_PRIORITY = listOf(MapCheckmark.GREEN, MapCheckmark.WHITE, MapCheckmark.RED, MapCheckmark.QUESTION_MARK)

    fun decode(mapColors: ByteArray, floorNumber: Int?): Map<GridPos, RoomCell> {
        val roomSize = roomPixelSize(floorNumber)
        val gap = roomSize + CELL_GAP
        val margin = margin(roomSize)

        val result = HashMap<GridPos, RoomCell>()
        for (tileZ in 0 until GRID_SIZE) {
            for (tileX in 0 until GRID_SIZE) {
                val originX = margin + tileX * gap
                val originZ = margin + tileZ * gap
                val type = RoomType.fromColorId(getPixel(mapColors, originX, originZ))
                if (type == RoomType.UNDISCOVERED) continue
                val (checkmark, rawCheckmarkByte) = findBestCheckmark(mapColors, originX, originZ, roomSize)
                result[GridPos(tileX, tileZ)] = RoomCell(type, checkmark, rawCheckmarkByte)
            }
        }
        return result
    }

    /**
     * The checkmark icon is NOT at a fixed offset within a cell - confirmed on a live run,
     * two different cleared rooms showed their GREEN pixels in completely different
     * relative positions (one top-left, one bottom-right), consistent with its placement
     * depending on which side the room connects to a neighbor. So scan the whole cell for
     * the highest-priority checkmark color found anywhere, rather than sampling one point.
     */
    private fun findBestCheckmark(mapColors: ByteArray, originX: Int, originZ: Int, roomSize: Int): Pair<MapCheckmark, Byte> {
        var best: MapCheckmark? = null
        var bestByte: Byte = -1
        for (row in 0 until roomSize) {
            for (col in 0 until roomSize) {
                val byte = getPixel(mapColors, originX + col, originZ + row)
                val checkmark = MapCheckmark.fromColorId(byte)
                if (checkmark == MapCheckmark.UNDISCOVERED) continue
                if (best == null || CHECKMARK_PRIORITY.indexOf(checkmark) < CHECKMARK_PRIORITY.indexOf(best)) {
                    best = checkmark
                    bestByte = byte
                }
            }
        }
        return (best ?: MapCheckmark.UNDISCOVERED) to bestByte
    }

    fun findRoomOfType(cells: Map<GridPos, RoomCell>, type: RoomType): GridPos? =
        cells.entries.find { it.value.type == type }?.key

    /**
     * Converts a raw map-pixel coordinate (0-127) into the same 0-5 tile index [decode]
     * produces - the inverse of the per-cell origin math used there. Used to place a
     * teammate's map decoration marker into the same tile space as decoded rooms, since
     * the decoration itself carries no player-identifying key and gives only a raw pixel
     * position (see [DecorationPlayerMatcher]).
     */
    fun pixelToTile(pixelX: Int, pixelZ: Int, floorNumber: Int?): GridPos {
        val roomSize = roomPixelSize(floorNumber)
        val gap = roomSize + CELL_GAP
        val margin = margin(roomSize)
        val tileX = Math.floorDiv(pixelX - margin, gap)
        val tileZ = Math.floorDiv(pixelZ - margin, gap)
        return GridPos(tileX, tileZ)
    }

    /** Full pixel block for one cell, for when a single sampled point isn't enough to tell
     * where the real checkmark icon lives within the cell. */
    fun dumpCellPixels(mapColors: ByteArray, floorNumber: Int?, pos: GridPos): String {
        val roomSize = roomPixelSize(floorNumber)
        val gap = roomSize + CELL_GAP
        val margin = margin(roomSize)
        val originX = margin + pos.x * gap
        val originZ = margin + pos.z * gap
        return (0 until roomSize).joinToString(" / ") { row ->
            (0 until roomSize).joinToString(",") { col -> getPixel(mapColors, originX + col, originZ + row).toString() }
        }
    }

    /**
     * Scans a whole cell for pixels matching any known [MapCheckmark] value and reports
     * their (row, col) position - a single sampled point (assumed center) turned out to
     * miss a real WHITE(34) pixel sitting nearer the top of a live cell, so rather than
     * guess another offset, find every occurrence directly.
     */
    fun findCheckmarkPixels(mapColors: ByteArray, floorNumber: Int?, pos: GridPos): List<String> {
        val roomSize = roomPixelSize(floorNumber)
        val gap = roomSize + CELL_GAP
        val margin = margin(roomSize)
        val originX = margin + pos.x * gap
        val originZ = margin + pos.z * gap
        val hits = mutableListOf<String>()
        for (row in 0 until roomSize) {
            for (col in 0 until roomSize) {
                val byte = getPixel(mapColors, originX + col, originZ + row)
                val checkmark = MapCheckmark.fromColorId(byte)
                if (checkmark != MapCheckmark.UNDISCOVERED) {
                    hits.add("(row=$row,col=$col)=${checkmark.name}($byte)")
                }
            }
        }
        return hits
    }
}
