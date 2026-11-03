package com.frost.dungeon

/**
 * Reads which rooms have been opened from the in-game dungeon map's raw pixels. Hypixel only
 * paints a room's real color once it's opened - until then its tile is blank, or gray for a
 * room seen through a door but not yet entered.
 *
 * The map's room size and margin vary by floor, so rather than assume them, [findLayout]
 * measures them from the entrance room's own pixels. Adapted from Odin's `MapScan.initLayout`
 * (BSD 3-Clause - see /THIRD_PARTY_NOTICES.md at the repo root).
 */
object DungeonMapReader {
    private const val MAP_SIZE = 128
    private const val CELL_GAP = 4
    private const val EMPTY: Byte = 0
    private val ROOM_SIZES = setOf(16, 18)

    // When a margin measures as 0 the true one is a full room plus gap further in: 22 pixels,
    // on the smallest layouts (Odin confirms this for both axes).
    private const val FALLBACK_MARGIN = 22

    private const val MARKER_DEGREES_PER_STEP = 360f / 16

    data class PixelLayout(val roomSize: Int, val startX: Int, val startZ: Int) {
        val pitch: Int get() = roomSize + CELL_GAP
    }

    /** Measures the map's layout, or null if this isn't a dungeon map with an entrance yet. A
     * real dungeon map's top-left corner is always blank, which also rules out other maps. */
    fun findLayout(colors: ByteArray): PixelLayout? {
        if (colors.size < MAP_SIZE * MAP_SIZE || colors[0] != EMPTY) return null
        val entrance = RoomType.ENTRANCE.colorId
        var index = 0
        while (index < colors.size) {
            if (colors[index] != entrance) {
                index++
                continue
            }
            var end = index
            while (end < colors.size && colors[end] == entrance) end++
            val roomSize = end - index
            if (roomSize in ROOM_SIZES) {
                val pitch = roomSize + CELL_GAP
                val startX = (index % MAP_SIZE) % pitch
                val startZ = (index / MAP_SIZE) % pitch
                return PixelLayout(
                    roomSize,
                    if (startX == 0) FALLBACK_MARGIN else startX,
                    if (startZ == 0) FALLBACK_MARGIN else startZ,
                )
            }
            index = end
        }
        return null
    }

    /** A player marker on the map, in world terms. It's as coarse as the map's own pixels, and
     * [yaw] only comes in 16 steps, but it's there however far away the player is. */
    data class Marker(val worldX: Double, val worldZ: Double, val yaw: Float)

    /** Reads a map decoration's raw values, which run -128..127 across the map's 128 pixels. */
    fun readMarker(x: Byte, y: Byte, rotation: Byte, layout: PixelLayout): Marker {
        fun toWorld(raw: Byte, start: Int): Double {
            val pixel = raw / 2.0 + MAP_SIZE / 2
            return FunnyMapLayout.tileToWorld((pixel - start - layout.roomSize / 2.0) / layout.pitch)
        }
        return Marker(toWorld(x, layout.startX), toWorld(y, layout.startZ), rotation * MARKER_DEGREES_PER_STEP)
    }

    /** Every tile whose corner pixel shows a real room color - gray means seen but not opened. */
    fun openedTiles(colors: ByteArray, layout: PixelLayout): Set<GridPos> =
        FunnyMapLayout.tiles.filterTo(mutableSetOf()) { tile ->
            val x = layout.startX + tile.x * layout.pitch
            val z = layout.startZ + tile.z * layout.pitch
            val pixel = z * MAP_SIZE + x
            if (x >= MAP_SIZE || pixel >= colors.size) return@filterTo false
            val type = RoomType.fromColorId(colors[pixel])
            type != RoomType.UNDISCOVERED && type != RoomType.UNKNOWN
        }

    /**
     * Every tile showing a white (cleared) or green (secrets done too) checkmark. The icon's
     * position within a cell varies - see [RoomGrid.decode] - so the whole cell is searched.
     * Pixels matching the room's own color are skipped, because green shares its color with the
     * entrance room's fill, which would otherwise read as permanently finished.
     */
    fun checkmarks(colors: ByteArray, layout: PixelLayout): Map<GridPos, MapCheckmark> {
        val result = HashMap<GridPos, MapCheckmark>()
        for (tile in FunnyMapLayout.tiles) {
            val originX = layout.startX + tile.x * layout.pitch
            val originZ = layout.startZ + tile.z * layout.pitch
            if (originX + layout.roomSize > MAP_SIZE || originZ + layout.roomSize > MAP_SIZE) continue
            val roomColor = colors[originZ * MAP_SIZE + originX]
            var best: MapCheckmark? = null
            for (z in originZ until originZ + layout.roomSize) {
                for (x in originX until originX + layout.roomSize) {
                    val pixel = colors[z * MAP_SIZE + x]
                    if (pixel == roomColor) continue
                    if (pixel == MapCheckmark.GREEN.colorId) best = MapCheckmark.GREEN
                    if (pixel == MapCheckmark.WHITE.colorId && best == null) best = MapCheckmark.WHITE
                }
            }
            best?.let { result[tile] = it }
        }
        return result
    }
}
