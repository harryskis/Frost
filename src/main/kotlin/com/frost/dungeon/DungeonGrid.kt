package com.frost.dungeon

/**
 * [worldToRoomTile]/[roomTileWorldCenter] convert world positions to/from room-grid tiles
 * using a per-run calibration anchor, not a hardcoded universal origin constant - a fixed
 * origin was confirmed unreliable across dungeon instances (see [WorldRoomScanner] for the
 * same lesson relearned independently for its world-scan). Reading a reference
 * implementation (Skyblocker) confirmed why: dungeons ARE laid out on a 32-block grid, but
 * offset by a plain +8 (their comment: "Hypixel offset dungeons by 8
 * blocks in Skyblock 0.12.3") - see [physicalRoomCorner], their exact formula. Rather than
 * assume that or any other constant generalizes to every instance forever, Skyblocker
 * calibrates PER RUN: it reads Mort's (the NPC who says "Good luck.") physical position at
 * dungeon start as the entrance's world corner, then converts any other physical position
 * to/from the map's pixel grid relative to that one known anchor. This mod does the same
 * thing, just anchored on the LOCAL PLAYER's own position at that exact moment instead of
 * finding Mort's entity - the player is guaranteed to still be standing in the entrance
 * room when that chat line fires, so their room corner *is* the entrance's.
 */
object DungeonGrid {
    private const val ROOM_SIZE = 32
    private const val PHYSICAL_OFFSET = 8

    // Same tile or an adjacent one (including diagonals) - anything farther means a
    // "closest of whoever we could find a position for" candidate is more likely just
    // whoever happened to be trackable, not someone plausibly near the room/puzzle in
    // question. Shared by RoomCreditTracker and the puzzle presence fallback so both use
    // the same standard for "close enough to actually credit."
    const val MAX_FALLBACK_TILE_DISTANCE_SQ = 2

    fun tileDistanceSq(a: GridPos, b: GridPos): Int {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz
    }

    /**
     * The world-space position of the northwest corner of the 32x32 room cell containing
     * (worldX, worldZ). Matches Skyblocker's `DungeonMapUtils.getPhysicalRoomPos`: shift by
     * 8.5 so room borders split evenly, floor-mod 32 to find the offset within the cell,
     * then shift back by 8 to land on the true corner.
     */
    fun physicalRoomCorner(worldX: Double, worldZ: Double): Pair<Int, Int> {
        val ix = (worldX + PHYSICAL_OFFSET + 0.5).toInt()
        val iz = (worldZ + PHYSICAL_OFFSET + 0.5).toInt()
        val x = ix - Math.floorMod(ix, ROOM_SIZE) - PHYSICAL_OFFSET
        val z = iz - Math.floorMod(iz, ROOM_SIZE) - PHYSICAL_OFFSET
        return x to z
    }

    /**
     * Converts a world position into the same 0-5 tile index RoomGrid's pixel decode
     * produces, given the run's calibration anchor: the entrance room's physical corner
     * ([physicalRoomCorner] of the local player's position when the run started) paired
     * with the entrance's tile index (from [RoomGrid.findRoomOfType]). Every other room is
     * just a fixed number of 32-block cells away from that one known point.
     */
    fun worldToRoomTile(worldX: Double, worldZ: Double, entranceCorner: Pair<Int, Int>, entranceTile: GridPos): GridPos {
        val (cornerX, cornerZ) = physicalRoomCorner(worldX, worldZ)
        val dx = Math.floorDiv(cornerX - entranceCorner.first, ROOM_SIZE)
        val dz = Math.floorDiv(cornerZ - entranceCorner.second, ROOM_SIZE)
        return GridPos(entranceTile.x + dx, entranceTile.z + dz)
    }

    /** Inverse of [worldToRoomTile]: the approximate world-space center of a tile. */
    fun roomTileWorldCenter(pos: GridPos, entranceCorner: Pair<Int, Int>, entranceTile: GridPos): Pair<Double, Double> {
        val worldX = entranceCorner.first + (pos.x - entranceTile.x) * ROOM_SIZE + ROOM_SIZE / 2.0
        val worldZ = entranceCorner.second + (pos.z - entranceTile.z) * ROOM_SIZE + ROOM_SIZE / 2.0
        return worldX to worldZ
    }

    /**
     * Buckets players by grid cell and returns everyone NOT in the largest cluster - i.e.
     * whoever split off from the main group. Used as a presence-based fallback credit for
     * puzzles Hypixel doesn't attach a solver name to. Not exact (misattributes if the
     * party splits 3-ways, or if the "main group" itself is who solved it), but far more
     * grounded than a fabricated guess since the grid math itself is confirmed accurate.
     */
    fun playersApartFromMainGroup(playerCells: Map<String, GridPos>): Set<String> {
        if (playerCells.size <= 1) return emptySet()
        val groups = playerCells.entries.groupBy({ it.value }, { it.key })
        if (groups.size <= 1) return emptySet() // everyone's in the same room - can't distinguish
        val mainGroupCell = groups.maxBy { it.value.size }.key
        return groups.filterKeys { it != mainGroupCell }.values.flatten().toSet()
    }
}
