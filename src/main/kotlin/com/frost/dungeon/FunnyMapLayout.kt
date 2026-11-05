package com.frost.dungeon

import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** What kind of door sits in a gap - see [FunnyMap] for how each is read from the world. */
enum class DoorKind { NORMAL, WITHER, BLOOD }

/**
 * The space between [tile] and its neighbor one step east ([horizontal]) or south. Doors, and
 * the walkable joins inside a multi-tile room, both live in these gaps.
 */
data class MapGap(val tile: GridPos, val horizontal: Boolean) {
    val other: GridPos get() = if (horizontal) GridPos(tile.x + 1, tile.z) else GridPos(tile.x, tile.z + 1)
}

/** The route from the entrance toward the blood room: the [tiles] walked through in order, then
 * the [door] where the line ends, if it ends at one. Unless it [reachesBlood], it's only heading
 * for the unloaded part of the dungeon where the blood room must be. [bloodTile] is where the blood
 * room has to be when that's been worked out rather than seen. */
data class BloodRushPath(val tiles: List<GridPos>, val door: MapGap?, val reachesBlood: Boolean = true, val bloodTile: GridPos? = null)

/** How many secrets the whole dungeon has, as worked out by [FunnyMapLayout.totalSecrets]. Not
 * [exact] while more than one total would show the same percent, which is common on the first
 * secret or two. */
data class SecretTotal(val count: Int, val exact: Boolean) {
    val label: String get() = if (exact) "$count" else "~$count"
}

/** Everything needed to draw the map: what's there, and how far the run has got through it. */
data class FunnyMapSnapshot(
    val rooms: Map<GridPos, RoomDatabase.RoomInfo>,
    val doors: Map<MapGap, DoorKind>,
    val opened: Set<GridPos>,
    val checkmarks: Map<RoomDatabase.RoomInfo, MapCheckmark> = emptyMap(),
    val foundSecrets: Map<RoomDatabase.RoomInfo, Int> = emptyMap(),
    val bloodRush: BloodRushPath? = null,
    val teamSecrets: Int = 0,
    val totalSecrets: SecretTotal? = null,
) {
    fun isCleared(room: RoomDatabase.RoomInfo): Boolean = checkmarks[room]?.isCleared() == true

    /** Nothing left to do in [room]: every mob dead and every secret found. */
    fun isCompleted(room: RoomDatabase.RoomInfo): Boolean = isCleared(room) && secretsFound(room) >= room.secrets

    /** Secrets found in [room]. A green checkmark proves all of them, even for a room only a
     * teammate has been in - the action bar only ever reports the local player's own room. */
    fun secretsFound(room: RoomDatabase.RoomInfo): Int =
        if (checkmarks[room] == MapCheckmark.GREEN) room.secrets else (foundSecrets[room] ?: 0).coerceAtMost(room.secrets)
}

/**
 * The geometry and room-merging rules behind [FunnyMap], kept free of Minecraft types so
 * they're unit-testable. Rooms sit on a fixed 6x6 grid, 32 world blocks apart - the same
 * universal origin [WorldRoomScanner] scans (see its doc comment) - with a one-block gap
 * column between neighbors.
 */
object FunnyMapLayout {
    const val GRID_SIZE = 6
    const val CELL_SIZE = 24
    const val GAP_SIZE = 4
    const val TILE_PITCH = CELL_SIZE + GAP_SIZE

    /** Width and height of the whole map, in GUI pixels. */
    const val MAP_SIZE = TILE_PITCH * GRID_SIZE

    const val DEFAULT_SCALE = 1f
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 2.5f
    private const val SCALE_STEP = 0.1f

    private const val MAX_LABEL_SCALE = 0.65f
    private const val MIN_LABEL_SCALE = 0.4f

    /** The room types whose names can be hidden, as (menu label, database type). */
    val SPECIAL_ROOM_TYPES = listOf(
        "Blood" to "BLOOD",
        "Fairy" to "FAIRY",
        "Entrance" to "ENTRANCE",
        "Miniboss" to "CHAMPION",
        "Trap" to "TRAP",
    )

    /** The room types Fill Completed Rooms lets you choose between, by the name shown for each. */
    val FILL_ROOM_TYPES = listOf(
        "Blood" to "BLOOD",
        "Miniboss" to "CHAMPION",
        "Trap" to "TRAP",
        "Puzzles" to "PUZZLE",
    )

    private val OPTIONAL_FILL_TYPES = FILL_ROOM_TYPES.map { it.second }.toSet()

    // The tab list rounds its percent to one decimal. Just over half of that, so a total landing
    // exactly on the edge (15 secrets of 48 is 31.25%, shown as 31.2%) isn't lost to floating point.
    private const val PERCENT_ROUNDING = 0.0501

    /**
     * The dungeon's total secrets from the team's [found] count and the tab list's [percent] of
     * them, the way Odin does it: 1 secret at 2% means 50. Null until a secret has been found.
     * Checked against real runs' logs, and it settles on the right total within a few secrets.
     */
    fun totalSecrets(found: Int, percent: Double): SecretTotal? {
        if (found <= 0 || percent <= PERCENT_ROUNDING) return null
        val secrets = found * 100.0
        val lowest = ceil(secrets / (percent + PERCENT_ROUNDING)).toInt()
        val highest = floor(secrets / (percent - PERCENT_ROUNDING)).toInt()
        return if (lowest == highest) SecretTotal(lowest, exact = true) else SecretTotal((secrets / percent).roundToInt(), exact = false)
    }

    /** Whether a completed room of [type] gets filled, given the optional types turned on in
     * [filledTypes]. Fairy never does, and every other room always does. */
    fun fillsWhenCompleted(type: String, filledTypes: Set<String>): Boolean = when (type) {
        "FAIRY" -> false
        in OPTIONAL_FILL_TYPES -> type in filledTypes
        else -> true
    }

    private fun straightRoom(length: Int) = listOf(
        (0 until length).map { GridPos(it, 0) },
        (0 until length).map { GridPos(0, it) },
    )

    private val SQUARE = listOf(GridPos(0, 0), GridPos(1, 0), GridPos(0, 1), GridPos(1, 1))

    /** The tiles of each room shape, as offsets, in each way it can be turned. */
    private val SHAPE_ROTATIONS = mapOf(
        "1x2" to straightRoom(2),
        "1x3" to straightRoom(3),
        "1x4" to straightRoom(4),
        "2x2" to listOf(SQUARE),
        "L" to SQUARE.map { missing -> SQUARE - missing },
    )

    private const val TILE_COST = 1
    private const val DOOR_COST = 1000

    private const val WORLD_TILE_SIZE = 32
    private const val WORLD_ORIGIN = -201

    private val GAP_SIDE_OFFSETS = listOf(6, 9, 12, 15)

    /** The heights a door's blocks fill, floor to ceiling. */
    val DOOR_OPENING_Y = 69..72

    val tiles: List<GridPos> = (0 until GRID_SIZE).flatMap { z -> (0 until GRID_SIZE).map { x -> GridPos(x, z) } }

    /** Every gap between two adjacent tiles on the grid. */
    val gaps: List<MapGap> = tiles.flatMap { tile ->
        listOfNotNull(
            MapGap(tile, horizontal = true).takeIf { tile.x < GRID_SIZE - 1 },
            MapGap(tile, horizontal = false).takeIf { tile.z < GRID_SIZE - 1 },
        )
    }

    /** The world (x, z) column running through the middle of [gap] - halfway between the two
     * tiles' scan columns, 16 blocks along whichever axis they're neighbors on. */
    fun gapColumn(gap: MapGap): Pair<Int, Int> {
        val (x, z) = WorldRoomScanner.tileToScanColumn(gap.tile)
        return if (gap.horizontal) (x + WORLD_TILE_SIZE / 2) to z else x to (z + WORLD_TILE_SIZE / 2)
    }

    /** World (x, z) columns along [gap], either side of where a door and its frame could be. Only
     * a room spanning the gap has blocks out here. */
    fun gapSideColumns(gap: MapGap): List<Pair<Int, Int>> {
        val (x, z) = gapColumn(gap)
        return GAP_SIDE_OFFSETS.flatMap { listOf(it, -it) }.map { if (gap.horizontal) x to (z + it) else (x + it) to z }
    }

    /** The gaps on all four sides of [tile], leaving out any past the edge of the grid. */
    fun gapsAround(tile: GridPos): List<MapGap> =
        gaps.filter { it.tile == tile || it.other == tile }

    /** Every world (x, y, z) a door in [gap] fills: 3 wide, 3 deep and 4 tall, around its
     * [gapColumn]. Matches the box Odin's door highlight draws. */
    fun doorOpening(gap: MapGap): List<Triple<Int, Int, Int>> {
        val (x, z) = gapColumn(gap)
        return DOOR_OPENING_Y.flatMap { y ->
            (-1..1).flatMap { dx -> (-1..1).map { dz -> Triple(x + dx, y, z + dz) } }
        }
    }

    /** Which tile a world x or z coordinate falls in - outside 0..5 means off the grid. */
    fun worldToTile(coordinate: Double): Int = Math.floorDiv(Math.floor(coordinate).toInt() - WORLD_ORIGIN, WORLD_TILE_SIZE)

    /** The world x or z of a position in tile units, where a whole number is that tile's center. */
    fun tileToWorld(tile: Double): Double = tile * WORLD_TILE_SIZE + WORLD_ORIGIN + WORLD_TILE_SIZE / 2

    /** A world x or z coordinate in tile units, where a tile's center is its index + 0.5. */
    fun worldToTileUnits(coordinate: Double): Double = (coordinate - WORLD_ORIGIN) / WORLD_TILE_SIZE

    /** Where a world x or z coordinate lands on the map, in GUI pixels from the map's edge. */
    fun worldToMap(coordinate: Double): Double = worldToTileUnits(coordinate) * TILE_PITCH

    /** The left/top edge of a tile's cell, in GUI pixels from the map's edge - half a gap in,
     * so a tile's world center lands on its cell's center. */
    fun cellStart(tileIndex: Int): Int = tileIndex * TILE_PITCH + GAP_SIZE / 2

    /** Two tiles scanned as the same room are one multi-tile room, so their gap is floor
     * rather than a door. A room only exists once per dungeon, so this can't false-match. */
    fun isMerged(gap: MapGap, rooms: Map<GridPos, RoomDatabase.RoomInfo>): Boolean {
        val room = rooms[gap.tile] ?: return false
        return room == rooms[gap.other]
    }

    /** The door to draw in [gap], or null if there isn't one. A door needs a known room on at
     * least one side - a lone filled gap with nothing identified next to it tells the player
     * nothing, and is usually part of a room that just hasn't been scanned yet. */
    fun doorIn(gap: MapGap, rooms: Map<GridPos, RoomDatabase.RoomInfo>, doors: Map<MapGap, DoorKind>): DoorKind? {
        val door = doors[gap] ?: return null
        if (isMerged(gap, rooms)) return null
        if (gap.tile !in rooms && gap.other !in rooms) return null
        return door
    }

    /** Groups tiles into whole rooms, so each one is labelled once however many tiles it spans. */
    fun groupRooms(rooms: Map<GridPos, RoomDatabase.RoomInfo>): Map<RoomDatabase.RoomInfo, Set<GridPos>> =
        rooms.entries.groupBy({ it.value }, { it.key }).mapValues { it.value.toSet() }

    /**
     * Where to center a room's label, in tile units (a tile's center is its index + 0.5).
     * Rectangles use their true middle. An L's middle falls outside the room, on empty space,
     * so it uses whichever of its own tiles is closest to that point instead - its corner.
     */
    fun labelCenter(tiles: Set<GridPos>): Pair<Double, Double> {
        val minX = tiles.minOf { it.x }
        val maxX = tiles.maxOf { it.x }
        val minZ = tiles.minOf { it.z }
        val maxZ = tiles.maxOf { it.z }
        val middleX = (minX + maxX + 1) / 2.0
        val middleZ = (minZ + maxZ + 1) / 2.0
        if (tiles.size == (maxX - minX + 1) * (maxZ - minZ + 1)) return middleX to middleZ
        val averageX = tiles.map { it.x + 0.5 }.average()
        val averageZ = tiles.map { it.z + 0.5 }.average()
        val corner = tiles.minBy { (it.x + 0.5 - averageX) * (it.x + 0.5 - averageX) + (it.z + 0.5 - averageZ) * (it.z + 0.5 - averageZ) }
        return corner.x + 0.5 to corner.z + 0.5
    }

    /** How wide a room's label may be, in GUI pixels: its full span for a rectangle, or just
     * the corner cell it's centered on for an L. */
    fun labelWidth(tiles: Set<GridPos>): Int {
        val spanX = tiles.maxOf { it.x } - tiles.minOf { it.x } + 1
        val spanZ = tiles.maxOf { it.z } - tiles.minOf { it.z } + 1
        return if (tiles.size == spanX * spanZ) spanX * TILE_PITCH - GAP_SIZE else CELL_SIZE
    }

    /** The text size for a label whose longest line is [textWidth] wide at full size: as big
     * as allowed, shrunk just enough to fit [availableWidth], down to a still-readable floor.
     * With [limitToRoom] off every label is simply the full size, fitting or not. [sizeFactor]
     * is the player's own adjustment, which moves both the full size and the floor. */
    fun labelScale(textWidth: Int, availableWidth: Int, limitToRoom: Boolean = true, sizeFactor: Float = 1f): Float {
        val max = MAX_LABEL_SCALE * sizeFactor
        if (!limitToRoom || textWidth <= 0) return max
        return (availableWidth.toFloat() / textWidth).coerceIn(MIN_LABEL_SCALE * sizeFactor, max)
    }

    /** One scroll-wheel step of map size, clamped, and rounded so repeated steps don't drift. */
    fun steppedScale(scale: Float, scrollDelta: Double): Float {
        val stepped = scale + SCALE_STEP * (if (scrollDelta > 0) 1 else -1)
        return (Math.round(stepped * 10) / 10f).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /** Spreads "opened" across whole rooms - the map or a player may only have touched one tile
     * of a large room, but opening any part of it opens all of it. */
    fun expandToWholeRooms(opened: Set<GridPos>, rooms: Map<GridPos, RoomDatabase.RoomInfo>): Set<GridPos> {
        val openedRooms = opened.mapNotNullTo(mutableSetOf()) { rooms[it] }
        return opened + rooms.filterValues { it in openedRooms }.keys
    }

    /** Spreads per-tile checkmarks across whole rooms, since a multi-tile room only shows its
     * icon on some of its tiles. Green outranks white when a room's tiles disagree. */
    fun roomCheckmarks(
        tileCheckmarks: Map<GridPos, MapCheckmark>,
        rooms: Map<GridPos, RoomDatabase.RoomInfo>,
    ): Map<RoomDatabase.RoomInfo, MapCheckmark> {
        val result = HashMap<RoomDatabase.RoomInfo, MapCheckmark>()
        for ((tile, checkmark) in tileCheckmarks) {
            val room = rooms[tile] ?: continue
            if (result[room] != MapCheckmark.GREEN) result[room] = checkmark
        }
        return result
    }

    /** Every tile of the room [tile] is part of - just [tile] itself if it isn't identified. */
    fun roomTiles(tile: GridPos, rooms: Map<GridPos, RoomDatabase.RoomInfo>): Set<GridPos> {
        val room = rooms[tile] ?: return setOf(tile)
        return rooms.filterValues { it == room }.keys
    }

    /**
     * [map] cut down to what a player without the world scan would know: only opened rooms are
     * identified, and a room behind one of their doors is just a blank tile, as it is on the
     * real map. The blood rush line only shows as far as the next door to open.
     */
    fun legitView(map: FunnyMapSnapshot): FunnyMapSnapshot {
        val rooms = map.rooms.filterKeys { it in map.opened }
        val doors = map.doors.filterKeys { it.tile in map.opened || it.other in map.opened }
        return map.copy(rooms = rooms, doors = doors, bloodRush = map.bloodRush?.let { openedPartOf(it, map.opened) })
    }

    /**
     * The way to blood. Dungeons lead there through a chain of wither doors, so that's all this
     * follows: out of the entrance room by whichever door, then wither door to wither door, until
     * a blood door. Where a room has several, it takes the branch that gets furthest. Until the
     * chain reaches a blood door it's not [BloodRushPath.reachesBlood], and just goes as far as
     * what's loaded shows, ending at the door leading on if there's one. With no door seen, it
     * ends at the last room's way out when only one of its gaps in [unknownGaps] could be that,
     * and counts as reaching blood if the chain can't go on past there. Null if that's nowhere.
     */
    fun bloodRushPath(
        rooms: Map<GridPos, RoomDatabase.RoomInfo>,
        doors: Map<MapGap, DoorKind>,
        unknownGaps: Set<MapGap> = emptySet(),
    ): BloodRushPath? {
        val route = witherRoute(rooms, doors)?.takeUnless { it.tiles.size == 1 && it.door == null } ?: return null
        if (route.reachesBlood || route.door != null) return route
        val lastRoom = roomTiles(route.tiles.last(), rooms)
        val exit = lastRoom.flatMap(::gapsAround).distinct()
            .filter { it in unknownGaps && !isMerged(it, rooms) }.singleOrNull() ?: return route
        val walk = walkWitherDoors(rooms, doors) ?: return route
        val (inside, next) = if (exit.tile in lastRoom) exit.tile to exit.other else exit.other to exit.tile
        val routed = route.copy(tiles = walk.routeTo(inside), door = exit)
        return if (mustBeBlood(next, routed, rooms, doors, unknownGaps)) routed.copy(reachesBlood = true, bloodTile = next) else routed
    }

    /**
     * Whether the chain has nowhere left to go past [tile], the unidentified one its [route] leads
     * into - which, as a chain ends at blood, makes that the blood room. The fairy room has to
     * be behind it already, as it could otherwise be this. Blood has just the one door, so any
     * other door into [tile] rules it out. An unloaded side only leaves a way on if what's there
     * is still a complete unknown: a known room, or a tile a normal door already leads into from
     * one, is part of the dungeon that's reached some other way.
     */
    private fun mustBeBlood(
        tile: GridPos,
        route: BloodRushPath,
        rooms: Map<GridPos, RoomDatabase.RoomInfo>,
        doors: Map<MapGap, DoorKind>,
        unknownGaps: Set<MapGap>,
    ): Boolean {
        if (tile in rooms || rooms.values.any { it.type == "BLOOD" }) return false
        if (route.tiles.none { rooms[it]?.type == "FAIRY" }) return false
        return gapsAround(tile).filter { it != route.door }.all { gap ->
            val beyond = if (gap.tile == tile) gap.other else gap.tile
            if (gap !in unknownGaps) {
                gap !in doors
            } else {
                beyond in rooms || gapsAround(beyond).any { doors[it] == DoorKind.NORMAL && (it.tile in rooms || it.other in rooms) }
            }
        }
    }

    /**
     * [rooms] with the tiles that haven't loaded yet filled in, for any room whose shape only fits
     * the grid one way around what's known of it. [joinedGaps] are the gaps seen to be part of a
     * room, so a fit can't stop at one leading to an unknown tile, and can only reach a new tile
     * across one of those or a gap that hasn't loaded.
     */
    fun withInferredTiles(
        rooms: Map<GridPos, RoomDatabase.RoomInfo>,
        joinedGaps: Set<MapGap>,
        unknownGaps: Set<MapGap>,
    ): Map<GridPos, RoomDatabase.RoomInfo> {
        val result = rooms.toMutableMap()
        for ((room, known) in groupRooms(rooms)) {
            val fits = placements(room.shape).filter { placed ->
                val added = placed - known
                placed.containsAll(known) && added.none { it in rooms } && gaps.all { gap ->
                    val inTile = gap.tile in placed
                    val inOther = gap.other in placed
                    when {
                        inTile && inOther -> (gap.tile in known && gap.other in known) || gap in joinedGaps || gap in unknownGaps
                        inTile || inOther -> gap !in joinedGaps || gap.tile in rooms && gap.other in rooms
                        else -> true
                    }
                }
            }
            fits.singleOrNull()?.forEach { result[it] = room }
        }
        return result
    }

    /** Every way a room shape can sit on the grid. */
    private fun placements(shape: String): List<Set<GridPos>> =
        SHAPE_ROTATIONS[shape].orEmpty().flatMap { offsets ->
            tiles.map { origin -> offsets.map { GridPos(origin.x + it.x, origin.z + it.z) }.toSet() }
                .filter { placed -> placed.all { it in tiles } }
        }

    private fun witherRoute(rooms: Map<GridPos, RoomDatabase.RoomInfo>, doors: Map<MapGap, DoorKind>): BloodRushPath? {
        val walk = walkWitherDoors(rooms, doors) ?: return null
        for (tile in walk.order) {
            val bloodDoor = gapsAround(tile).firstOrNull { doors[it] == DoorKind.BLOOD } ?: continue
            return BloodRushPath(walk.routeTo(tile), bloodDoor)
        }
        var end = walk.order.first()
        var endDoor: MapGap? = null
        var furthest = -1
        for (tile in walk.order) {
            val cost = walk.costs.getValue(tile)
            if (cost >= furthest) {
                furthest = cost
                end = tile
                endDoor = null
            }
            for (gap in gapsAround(tile)) {
                val next = if (gap.tile == tile) gap.other else gap.tile
                if (next in rooms || !walk.canFollow(gap, doors) || cost + DOOR_COST < furthest) continue
                furthest = cost + DOOR_COST
                end = tile
                endDoor = gap
            }
        }
        return BloodRushPath(walk.routeTo(end), endDoor, reachesBlood = false)
    }

    private class WitherWalk(
        val order: List<GridPos>,
        val costs: Map<GridPos, Int>,
        val cameFrom: Map<GridPos, GridPos>,
        val entranceRoom: Set<GridPos>,
    ) {
        fun routeTo(tile: GridPos): List<GridPos> = generateSequence(tile) { cameFrom[it] }.toList().reversed()

        /** The entrance's own door is a normal one, so any door out of there will do. */
        fun canFollow(gap: MapGap, doors: Map<MapGap, DoorKind>): Boolean {
            val kind = doors[gap] ?: return false
            return kind != DoorKind.NORMAL || gap.tile in entranceRoom || gap.other in entranceRoom
        }
    }

    /** Every tile the wither door chain reaches from the entrance, fewest doors first, or null
     * if the entrance isn't known yet. */
    private fun walkWitherDoors(rooms: Map<GridPos, RoomDatabase.RoomInfo>, doors: Map<MapGap, DoorKind>): WitherWalk? {
        val start = rooms.entries.firstOrNull { it.value.type == "ENTRANCE" }?.key ?: return null
        val order = ArrayList<GridPos>()
        val costs = hashMapOf(start to 0)
        val cameFrom = HashMap<GridPos, GridPos>()
        val walk = WitherWalk(order, costs, cameFrom, roomTiles(start, rooms))
        val queue = PriorityQueue<Pair<GridPos, Int>>(compareBy { it.second })
        queue.add(start to 0)
        while (queue.isNotEmpty()) {
            val (tile, cost) = queue.poll()
            if (cost > costs.getValue(tile)) continue
            order += tile
            for (gap in gapsAround(tile)) {
                val next = if (gap.tile == tile) gap.other else gap.tile
                if (next !in rooms) continue
                val step = when {
                    isMerged(gap, rooms) -> TILE_COST
                    walk.canFollow(gap, doors) -> DOOR_COST
                    else -> continue
                }
                val nextCost = cost + step
                if (nextCost >= (costs[next] ?: Int.MAX_VALUE)) continue
                costs[next] = nextCost
                cameFrom[next] = tile
                queue.add(next to nextCost)
            }
        }
        return walk
    }

    /** How many rooms the blood rush goes through, not counting the entrance, fairy and blood
     * rooms, or null until the chain reaches blood. A multi-tile room counts once. */
    fun bloodRushRoomCount(
        rooms: Map<GridPos, RoomDatabase.RoomInfo>,
        doors: Map<MapGap, DoorKind>,
        unknownGaps: Set<MapGap> = emptySet(),
    ): Int? = bloodRushPath(rooms, doors, unknownGaps)?.takeIf { it.reachesBlood }?.let { roomsOn(it, rooms) }

    /**
     * The fewest rooms the blood rush could still turn out to go through while the chain hasn't
     * reached blood: at least everything it's been through so far. A door leading on adds one
     * more, since only a blood door opens into blood - unless that next room could be the fairy
     * room, which doesn't count. Otherwise it only counts as unfinished if the last room has a
     * gap in [unknownGaps] that a door could still turn up in. Null if it can't be going on.
     */
    fun possibleBloodRushRoomCount(
        rooms: Map<GridPos, RoomDatabase.RoomInfo>,
        doors: Map<MapGap, DoorKind>,
        unknownGaps: Set<MapGap>,
    ): Int? {
        val route = witherRoute(rooms, doors)?.takeUnless { it.reachesBlood } ?: return null
        val soFar = roomsOn(route, rooms)
        if (route.door != null) return soFar + if (rooms.values.any { it.type == "FAIRY" }) 1 else 0
        val lastRoom = roomTiles(route.tiles.last(), rooms)
        return soFar.takeIf { lastRoom.any { tile -> gapsAround(tile).any { it in unknownGaps } } }
    }

    private fun roomsOn(path: BloodRushPath, rooms: Map<GridPos, RoomDatabase.RoomInfo>): Int =
        path.tiles.mapNotNull { rooms[it] }.distinct().count { it.type != "ENTRANCE" && it.type != "FAIRY" && it.type != "BLOOD" }

    /**
     * The wither doors out of the fairy room, which is what shows the way on from it. When it
     * has several, the one the chain from the entrance comes in by is left out, since that's the
     * way in rather than the way on. If that isn't known yet, they all count.
     */
    fun fairyExitDoors(rooms: Map<GridPos, RoomDatabase.RoomInfo>, doors: Map<MapGap, DoorKind>): List<MapGap> {
        val fairy = rooms.entries.firstOrNull { it.value.type == "FAIRY" }?.key ?: return emptyList()
        val witherDoors = gapsAround(fairy).filter { doors[it] == DoorKind.WITHER }
        if (witherDoors.size < 2) return witherDoors
        val previous = walkWitherDoors(rooms, doors)?.cameFrom?.get(fairy) ?: return witherDoors
        return witherDoors - gapBetween(previous, fairy)
    }

    private fun gapBetween(first: GridPos, second: GridPos): MapGap =
        gaps.first { (it.tile == first && it.other == second) || (it.tile == second && it.other == first) }

    /** [path] only as far as [opened] rooms go, ending at the door of the first unopened room on
     * it, so it extends as rooms open without giving away the rest of the route. */
    private fun openedPartOf(path: BloodRushPath, opened: Set<GridPos>): BloodRushPath? {
        val walked = path.tiles.takeWhile { it in opened }
        if (walked.size == path.tiles.size) return path
        val last = walked.lastOrNull() ?: return null
        return path.copy(tiles = walked, door = gapBetween(last, path.tiles[walked.size]))
    }

    /** How far to turn a head so its top points the way a player with this yaw is facing. Heads
     * are drawn with their top toward the map's north, which is yaw 180. Yaw grows clockwise
     * seen from above, the same way as a clockwise on-screen rotation. */
    fun headRotationDegrees(yaw: Float): Float = ((yaw + 180f) % 360f + 360f) % 360f

    /** A made-up dungeon for the move screen's preview, so there's a real-looking map to drag
     * around even when not in a dungeon. */
    val sampleRooms: Map<GridPos, RoomDatabase.RoomInfo> = run {
        fun room(name: String, type: String, shape: String, secrets: Int) = RoomDatabase.RoomInfo(name, type, shape, secrets = secrets)
        val crypt = room("Crypt", "NORMAL", "1x2", 5)
        val mines = room("Mines", "NORMAL", "2x2", 7)
        val altar = room("Altar", "NORMAL", "L", 6)
        mapOf(
            GridPos(2, 5) to room("Entrance", "ENTRANCE", "1x1", 0),
            GridPos(2, 4) to crypt,
            GridPos(3, 4) to crypt,
            GridPos(1, 4) to room("Ice Fill", "PUZZLE", "1x1", 0),
            GridPos(3, 3) to mines,
            GridPos(4, 3) to mines,
            GridPos(3, 2) to mines,
            GridPos(4, 2) to mines,
            GridPos(2, 3) to room("Andesite", "NORMAL", "1x1", 2),
            GridPos(1, 2) to altar,
            GridPos(2, 2) to altar,
            GridPos(1, 1) to altar,
            GridPos(0, 2) to room("New Trap", "TRAP", "1x1", 3),
            GridPos(3, 1) to room("Spider", "RARE", "1x1", 4),
            GridPos(2, 1) to room("Fairy", "FAIRY", "1x1", 0),
            GridPos(3, 0) to room("Blood", "BLOOD", "1x1", 0),
        )
    }

    val sampleDoors: Map<MapGap, DoorKind> = mapOf(
        MapGap(GridPos(2, 4), horizontal = false) to DoorKind.NORMAL,
        MapGap(GridPos(1, 4), horizontal = true) to DoorKind.NORMAL,
        MapGap(GridPos(3, 3), horizontal = false) to DoorKind.WITHER,
        MapGap(GridPos(2, 3), horizontal = true) to DoorKind.NORMAL,
        MapGap(GridPos(2, 2), horizontal = true) to DoorKind.NORMAL,
        MapGap(GridPos(0, 2), horizontal = true) to DoorKind.NORMAL,
        MapGap(GridPos(3, 1), horizontal = false) to DoorKind.WITHER,
        MapGap(GridPos(2, 1), horizontal = true) to DoorKind.NORMAL,
        MapGap(GridPos(3, 0), horizontal = false) to DoorKind.BLOOD,
    )

    /** The sample, with a run in progress so it shows both opened and unopened rooms. */
    val sample = FunnyMapSnapshot(
        sampleRooms,
        sampleDoors,
        expandToWholeRooms(setOf(GridPos(2, 5), GridPos(2, 4), GridPos(1, 4), GridPos(2, 3)), sampleRooms),
        roomCheckmarks(mapOf(GridPos(2, 4) to MapCheckmark.WHITE, GridPos(1, 4) to MapCheckmark.GREEN, GridPos(2, 3) to MapCheckmark.GREEN), sampleRooms),
        mapOf(sampleRooms.getValue(GridPos(2, 4)) to 3),
        bloodRushPath(sampleRooms, sampleDoors),
        teamSecrets = 12,
        totalSecrets = SecretTotal(46, exact = true),
    )
}
