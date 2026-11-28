package com.frost.dungeon

/**
 * How an L-shaped room's three tiles sit: which tile is its [corner], and which of its arms is
 * [first] - the one that, turned a quarter around the corner, lands on the [second]. Each is
 * a tile's core hash, which tells the tiles of a room apart. A room is only ever rotated,
 * never mirrored, so this holds whichever way it's turned.
 */
data class LRoomLayout(val corner: Int, val first: Int, val second: Int)

/**
 * Placing an L room's third tile from the two that have loaded, which its shape alone can't
 * do when the rest of the area hasn't loaded either. The layout comes from having seen the
 * room whole once - see [learn] - and [com.frost.dungeon.LRoomMemory] keeps it between runs.
 */
object LRoomLayouts {
    private const val L_TILES = 3

    // A quarter turn, always the same way round.
    private fun turn(x: Int, z: Int) = -z to x

    private fun unturn(x: Int, z: Int) = z to -x

    private fun adjacent(a: GridPos, b: GridPos) = Math.abs(a.x - b.x) + Math.abs(a.z - b.z) == 1

    /** The layout of a whole L room, from its three tiles' cores, or null if they aren't an L. */
    fun learn(cores: Map<GridPos, Int>): LRoomLayout? {
        if (cores.size != L_TILES) return null
        val tiles = cores.keys.toList()
        val corner = tiles.singleOrNull { tile -> tiles.count { it != tile && adjacent(it, tile) } == 2 } ?: return null
        val (a, b) = tiles.filter { it != corner }
        fun turnedFrom(arm: GridPos) = turn(arm.x - corner.x, arm.z - corner.z).let { (x, z) -> GridPos(corner.x + x, corner.z + z) }
        // A quarter turn takes one arm onto the other - three tiles in a row aren't an L.
        val (first, second) = when {
            turnedFrom(a) == b -> a to b
            turnedFrom(b) == a -> b to a
            else -> return null
        }
        return LRoomLayout(cores.getValue(corner), cores.getValue(first), cores.getValue(second))
    }

    /** The missing tile of an L room two of whose tiles are known by [cores], or null if they
     * don't fit [layout] - a different variant of the room, say. */
    fun missingTile(cores: Map<GridPos, Int>, layout: LRoomLayout): GridPos? {
        if (cores.size != 2) return null
        val byCore = cores.entries.associate { (tile, core) -> core to tile }
        val corner = byCore[layout.corner]
        val first = byCore[layout.first]
        val second = byCore[layout.second]
        return when {
            corner != null && first != null -> turn(first.x - corner.x, first.z - corner.z).let { (x, z) -> GridPos(corner.x + x, corner.z + z) }
            corner != null && second != null -> unturn(second.x - corner.x, second.z - corner.z).let { (x, z) -> GridPos(corner.x + x, corner.z + z) }
            first != null && second != null -> {
                // first = corner + v and second = corner + turn(v), so second - first = turn(v) - v.
                val dx = second.x - first.x
                val dz = second.z - first.z
                if ((dz - dx) % 2 != 0) return null
                val vx = (dz - dx) / 2
                val vz = (-dx - dz) / 2
                GridPos(first.x - vx, first.z - vz)
            }
            else -> null
        }
    }

    /** [rooms] with the third tile of every L room that has two loaded, where its [layoutOf] is
     * known and the tile it gives is on the grid and free. */
    fun complete(
        rooms: Map<GridPos, RoomDatabase.RoomInfo>,
        cores: Map<GridPos, Int>,
        layoutOf: (String) -> LRoomLayout?,
    ): Map<GridPos, RoomDatabase.RoomInfo> {
        val result = rooms.toMutableMap()
        for ((room, tiles) in FunnyMapLayout.groupRooms(rooms)) {
            if (room.shape != "L" || tiles.size != 2) continue
            val layout = layoutOf(room.name) ?: continue
            val known = tiles.mapNotNull { tile -> cores[tile]?.let { tile to it } }.toMap()
            val missing = missingTile(known, layout) ?: continue
            if (missing in FunnyMapLayout.tiles && missing !in rooms) result[missing] = room
        }
        return result
    }
}
