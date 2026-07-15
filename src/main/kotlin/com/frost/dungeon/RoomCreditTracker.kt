package com.frost.dungeon

/**
 * Ties [RoomClearTracker]'s grid-diffing to a presence check: on each update, finds which
 * room(s) newly cleared and which party members were standing in them (or, failing that,
 * whoever's tile-distance-closest). Pure detector - no session/recording side effects, so
 * the caller decides what to do with each [ClearEvent] (currently: look up the room's real
 * name via the world-block scanner and record it against whoever was present).
 *
 * Confirmed on a live (fast M5 speed-clear) run: EVERY room clear that whole floor had
 * zero players standing in it at the exact tick it was detected - a fast-clearing party
 * is routinely already a room or two ahead by the time the map's checkmark pixel actually
 * updates and this code polls it, so exact presence alone lost credit for the entire
 * floor. Falls back to whoever's tile-distance-closest to the room when nobody's exactly
 * in it, mirroring the same fallback already used for puzzles - but ONLY within
 * [DungeonGrid.MAX_FALLBACK_TILE_DISTANCE_SQ]. Confirmed as necessary on another live run:
 * teammates' entities frequently aren't loaded into the client's world at all (out of
 * tracking range in a large dungeon), while the LOCAL player's position is always known -
 * so an unbounded "closest of whoever we have a position for" degenerates into "whoever's
 * trackable," which in practice means it kept picking the local player even when they
 * were 5+ rooms away from the room that actually cleared. Requiring the candidate to
 * actually be near the room means an implausible match is dropped (no credit) rather
 * than wrongly attributed.
 *
 * A second failure mode showed up on a later live run: a player who solo-cleared a small
 * (2-tile) room, then immediately walked on to the next one, got ZERO credit for it - two
 * teammates who happened to be walking through that same room a moment later (after the
 * real clear, before this code's next poll caught the checkmark flip) got credited
 * instead. Exact-presence-right-now isn't just sometimes empty, it can be flat-out wrong
 * about WHO. [recentPresence] keeps a short rolling window of who was seen in each tile on
 * recent polls, and a clear's credited set is the union of "exactly present now" and
 * "present at some point in that window" - so a clearer who's already moved on by
 * detection time is still caught, instead of being silently replaced by whoever wandered
 * in after them.
 */
class RoomCreditTracker {
    private var previousGrid: Map<GridPos, MapCheckmark> = emptyMap()

    // ~3s of history at the ~4Hz poll rate this is called at (see Frost.kt's pollDungeonMap
    // throttle) - long enough to cover the poll-to-poll gap between a fast clear and this
    // code detecting it, short enough that a room from several rooms ago has long since
    // rolled off and won't bleed into a later clear's credit.
    private val recentPresence: ArrayDeque<Map<String, GridPos>> = ArrayDeque()
    private val presenceHistorySize = 12

    /** One newly-cleared room's cells and who was credited for it - returned purely so the
     * caller can log it; nothing here depends on Minecraft/logging APIs. */
    data class ClearEvent(val room: Set<GridPos>, val playersPresent: Set<String>)

    /** [playerPositions] should include every currently-tracked party member's grid cell.
     * [roomTypes] should cover every cell in [currentGrid] - see [RoomClearTracker] for why
     * it's needed to avoid merging two unrelated rooms that clear on the same poll. */
    fun update(
        currentGrid: Map<GridPos, MapCheckmark>,
        roomTypes: Map<GridPos, RoomType>,
        playerPositions: Map<String, GridPos>,
    ): List<ClearEvent> {
        val clearedRooms = RoomClearTracker.detectNewlyClearedRooms(previousGrid, currentGrid, roomTypes)
        val events = clearedRooms.map { room -> ClearEvent(room, resolvePresence(room, playerPositions)) }

        recentPresence.addLast(playerPositions)
        while (recentPresence.size > presenceHistorySize) recentPresence.removeFirst()
        previousGrid = currentGrid
        return events
    }

    private fun resolvePresence(room: Set<GridPos>, currentPositions: Map<String, GridPos>): Set<String> {
        val exactlyPresent = currentPositions.filterValues { it in room }.keys
        val recentlyPresent = recentPresence.flatMap { snapshot -> snapshot.filterValues { it in room }.keys }
        val present = (exactlyPresent + recentlyPresent).toSet()
        if (present.isNotEmpty()) return present

        return closestPlayer(room, currentPositions)?.let { setOf(it) } ?: emptySet()
    }

    private fun closestPlayer(room: Set<GridPos>, playerPositions: Map<String, GridPos>): String? =
        playerPositions.entries
            .map { (username, pos) -> username to room.minOf { cell -> DungeonGrid.tileDistanceSq(pos, cell) } }
            .filter { (_, distSq) -> distSq <= DungeonGrid.MAX_FALLBACK_TILE_DISTANCE_SQ }
            .minByOrNull { (_, distSq) -> distSq }
            ?.first
}
