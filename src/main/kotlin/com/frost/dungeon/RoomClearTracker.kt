package com.frost.dungeon

/**
 * Pure grid-diffing logic: given the room-checkmark grid from the previous tick and the
 * current tick, finds newly-cleared rooms as connected regions of cleared cells. Kept
 * free of any Minecraft/Fabric API so it's trivially unit-testable.
 *
 * "Cleared" means [MapCheckmark.isCleared] (WHITE or GREEN) - WHITE is reached first (mobs
 * dead), GREEN only once that room's secrets are also 100% found. The original version of
 * this only counted GREEN, which is why rooms/room-credit never incremented in practice:
 * GREEN is rare (needs every secret in that specific room), WHITE is the actual "cleared"
 * signal.
 *
 * The flood-fill only ever grows through cells that are THEMSELVES newly-cleared this same
 * tick (the seed set), never through cells that were already cleared on a previous tick.
 * Confirmed as a real bug on a live run: flooding through any already-cleared neighbor
 * (regardless of when it cleared) merges a brand new room clear into whatever large,
 * already-cleared, merely-ADJACENT region happens to touch it - by mid-run most of the
 * dungeon's cleared cells are all interconnected via corridors, so this collapsed what
 * should have been many separate room-clear/credit events into one, badly undercounting
 * both teamRoomsCleared and room credit. Restricting growth to the seed set still handles
 * genuine multi-cell rooms correctly (all of a 2x1/2x2 room's cells clear on the same tick,
 * so they're all seeds together), without swallowing unrelated older neighbors.
 *
 * A second, subtler version of the same bug: two entirely SEPARATE rooms that are grid-
 * adjacent can each independently clear within the same ~250ms poll window (this only
 * polls ~4x/second), which still looks like "two newly-cleared, adjacent seed cells" to
 * the flood-fill above. Confirmed on a live run: a 1x1 TRAP room and an unrelated NORMAL
 * room next to it cleared on the same poll and got merged into one fake 2-tile "room",
 * pooling credit from both real rooms together and misattributing the TRAP room's actual
 * solo clearer. [roomTypes] guards against this: flooding only continues into a neighbor
 * that reports the SAME room type as the seed it grew from, since a genuine multi-cell
 * room reports that same type at every one of its cells, but two coincidentally-
 * simultaneous separate rooms essentially never share one.
 */
object RoomClearTracker {

    /** Returns each newly-cleared room as the full set of grid cells belonging to it.
     * [roomTypes] should cover every cell in [current] - see the class doc for why it's
     * needed to avoid merging two unrelated rooms that clear on the same poll. */
    fun detectNewlyClearedRooms(
        previous: Map<GridPos, MapCheckmark>,
        current: Map<GridPos, MapCheckmark>,
        roomTypes: Map<GridPos, RoomType> = emptyMap(),
    ): List<Set<GridPos>> {
        val seeds = current.entries
            .filter { (pos, state) -> state.isCleared() && previous[pos]?.isCleared() != true }
            .map { it.key }
            .toSet()

        val visited = HashSet<GridPos>()
        val regions = mutableListOf<Set<GridPos>>()

        for (seed in seeds) {
            if (seed in visited) continue
            val region = floodFillWithinSeeds(seed, seeds, roomTypes, visited)
            if (region.isNotEmpty()) regions.add(region)
        }
        return regions
    }

    private fun floodFillWithinSeeds(
        start: GridPos,
        seeds: Set<GridPos>,
        roomTypes: Map<GridPos, RoomType>,
        visited: MutableSet<GridPos>,
    ): Set<GridPos> {
        val expectedType = roomTypes[start]
        val region = mutableSetOf<GridPos>()
        val stack = ArrayDeque<GridPos>()
        stack.addLast(start)
        while (stack.isNotEmpty()) {
            val pos = stack.removeLast()
            if (pos in visited) continue
            if (pos !in seeds) continue
            if (roomTypes[pos] != expectedType) continue
            visited.add(pos)
            region.add(pos)
            stack.addLast(GridPos(pos.x + 1, pos.z))
            stack.addLast(GridPos(pos.x - 1, pos.z))
            stack.addLast(GridPos(pos.x, pos.z + 1))
            stack.addLast(GridPos(pos.x, pos.z - 1))
        }
        return region
    }
}
