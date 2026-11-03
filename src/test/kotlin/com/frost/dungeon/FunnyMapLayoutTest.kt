package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FunnyMapLayoutTest {
    private fun room(name: String) = RoomDatabase.RoomInfo(name, "NORMAL", "1x1")
    private val a = GridPos(0, 0)
    private val b = GridPos(1, 0)
    private val gapAB = MapGap(a, horizontal = true)

    @Test
    fun `every adjacent tile pair on the 6x6 grid has exactly one gap`() {
        assertEquals(60, FunnyMapLayout.gaps.size)
        assertEquals(60, FunnyMapLayout.gaps.toSet().size)
        assertTrue(FunnyMapLayout.gaps.all { it.other.x < 6 && it.other.z < 6 })
    }

    @Test
    fun `gap columns sit halfway between the two tiles scan columns`() {
        // Matches Odin's own door position: (tile - 6) * 32 + 7 + 16 along the door's axis.
        assertEquals(-169 to -185, FunnyMapLayout.gapColumn(gapAB))
        assertEquals(-185 to -169, FunnyMapLayout.gapColumn(MapGap(a, horizontal = false)))
        assertEquals(-105 to -89, FunnyMapLayout.gapColumn(MapGap(GridPos(2, 3), horizontal = true)))
    }

    @Test
    fun `roomTiles spans a whole multi-tile room, or just the tile if it's unknown`() {
        val crypt = room("Crypt")
        val rooms = mapOf(a to crypt, b to crypt, GridPos(2, 0) to room("Mines"))
        assertEquals(setOf(a, b), FunnyMapLayout.roomTiles(b, rooms))
        assertEquals(setOf(GridPos(5, 5)), FunnyMapLayout.roomTiles(GridPos(5, 5), rooms))
    }

    @Test
    fun `legitView only identifies opened rooms, leaving their doors leading somewhere blank`() {
        val c = GridPos(2, 0)
        val gapBC = MapGap(b, horizontal = true)
        val rooms = mapOf(
            a to RoomDatabase.RoomInfo("Entrance", "ENTRANCE", "1x1"),
            b to room("Crypt"),
            c to RoomDatabase.RoomInfo("Blood", "BLOOD", "1x1"),
        )
        val doors = mapOf(gapAB to DoorKind.WITHER, gapBC to DoorKind.BLOOD)
        val full = FunnyMapSnapshot(rooms, doors, setOf(a), bloodRush = FunnyMapLayout.bloodRushPath(rooms, doors))
        assertNotNull(full.bloodRush)

        val atEntrance = FunnyMapLayout.legitView(full)
        assertEquals(setOf(a), atEntrance.rooms.keys)
        assertEquals(setOf(gapAB), atEntrance.doors.keys)
        assertEquals(DoorKind.WITHER, FunnyMapLayout.doorIn(gapAB, atEntrance.rooms, atEntrance.doors))
        assertEquals(BloodRushPath(listOf(a), gapAB), atEntrance.bloodRush)

        val oneOpened = FunnyMapLayout.legitView(full.copy(opened = setOf(a, b)))
        assertEquals(BloodRushPath(listOf(a, b), gapBC), oneOpened.bloodRush)

        val allOpened = FunnyMapLayout.legitView(full.copy(opened = setOf(a, b, c)))
        assertEquals(rooms, allOpened.rooms)
        assertEquals(doors, allOpened.doors)
        assertEquals(full.bloodRush, allOpened.bloodRush)

        assertNull(FunnyMapLayout.legitView(full.copy(opened = emptySet())).bloodRush)
    }

    @Test
    fun `a room is completed once it's cleared and all its secrets are found`() {
        val crypt = RoomDatabase.RoomInfo("Crypt", "NORMAL", "1x2", secrets = 3)
        val empty = RoomDatabase.RoomInfo("Empty", "NORMAL", "1x1", secrets = 0)
        fun map(checkmark: MapCheckmark, found: Int) = FunnyMapSnapshot(
            mapOf(a to crypt, b to empty), emptyMap(), setOf(a, b),
            mapOf(crypt to checkmark, empty to checkmark), mapOf(crypt to found),
        )
        assertFalse(map(MapCheckmark.WHITE, 2).isCompleted(crypt))
        assertTrue(map(MapCheckmark.WHITE, 3).isCompleted(crypt))
        assertTrue(map(MapCheckmark.GREEN, 0).isCompleted(crypt))
        assertTrue(map(MapCheckmark.WHITE, 0).isCompleted(empty))
        assertFalse(FunnyMapSnapshot(mapOf(b to empty), emptyMap(), setOf(b)).isCompleted(empty))
    }

    @Test
    fun `the total secrets are unknown until one has been found`() {
        assertNull(FunnyMapLayout.totalSecrets(0, 0.0))
        assertNull(FunnyMapLayout.totalSecrets(1, 0.0))
    }

    // Real readings from logged runs, whose totals were confirmed by later readings in each run.
    @Test
    fun `the total secrets come out right once only one total fits the percent`() {
        assertEquals(SecretTotal(48, exact = true), FunnyMapLayout.totalSecrets(15, 31.2))
        assertEquals(SecretTotal(48, exact = true), FunnyMapLayout.totalSecrets(3, 6.2))
        assertEquals(SecretTotal(56, exact = true), FunnyMapLayout.totalSecrets(10, 17.9))
        assertEquals(SecretTotal(44, exact = true), FunnyMapLayout.totalSecrets(11, 25.0))
        assertEquals(SecretTotal(49, exact = true), FunnyMapLayout.totalSecrets(49, 100.0))
    }

    @Test
    fun `the total secrets are only an estimate while several totals fit the percent`() {
        assertEquals(SecretTotal(43, exact = false), FunnyMapLayout.totalSecrets(1, 2.3))
        assertEquals(SecretTotal(50, exact = false), FunnyMapLayout.totalSecrets(1, 2.0))
        assertEquals("~50", SecretTotal(50, exact = false).label)
        assertEquals("50", SecretTotal(50, exact = true).label)
    }

    @Test
    fun `the optional room types only fill when they've been turned on`() {
        for ((_, type) in FunnyMapLayout.FILL_ROOM_TYPES) {
            assertFalse(FunnyMapLayout.fillsWhenCompleted(type, emptySet()), type)
            assertTrue(FunnyMapLayout.fillsWhenCompleted(type, setOf(type)), type)
            assertFalse(FunnyMapLayout.fillsWhenCompleted(type, setOf("SOMETHING ELSE")), type)
        }
    }

    @Test
    fun `fairy rooms never fill, and other rooms always do`() {
        val allTypes = FunnyMapLayout.FILL_ROOM_TYPES.map { it.second }.toSet() + "FAIRY"
        assertFalse(FunnyMapLayout.fillsWhenCompleted("FAIRY", allTypes))
        for (type in listOf("NORMAL", "RARE", "ENTRANCE")) {
            assertTrue(FunnyMapLayout.fillsWhenCompleted(type, emptySet()), type)
        }
    }

    private val tile2 = GridPos(2, 0)
    private val tile3 = GridPos(3, 0)
    private val tile4 = GridPos(4, 0)
    private val below = GridPos(0, 1)
    private fun rightOf(tile: GridPos) = MapGap(tile, horizontal = true)
    private fun under(tile: GridPos) = MapGap(tile, horizontal = false)

    // A row of rooms from the entrance: its own normal door, then wither doors, then a blood door.
    private fun chainRooms(vararg middle: RoomDatabase.RoomInfo): Map<GridPos, RoomDatabase.RoomInfo> =
        (listOf(entrance) + middle + blood).withIndex().associate { (x, room) -> GridPos(x, 0) to room }

    private fun chainDoors(rooms: Int): Map<MapGap, DoorKind> =
        (0..rooms).associate { x ->
            val kind = when (x) {
                0 -> DoorKind.NORMAL
                rooms -> DoorKind.BLOOD
                else -> DoorKind.WITHER
            }
            rightOf(GridPos(x, 0)) to kind
        }

    @Test
    fun `blood rush follows the wither doors, not a shorter way through normal doors`() {
        // Along the top: entrance, then wither, wither, blood. Underneath, normal doors run
        // straight from the entrance's other door to a room right beside blood.
        val rooms = chainRooms(room("One"), room("Two")) + mapOf(below to room("Side"), GridPos(1, 1) to room("Side 2"), GridPos(2, 1) to room("Side 3"))
        val doors = chainDoors(2) + mapOf(
            under(a) to DoorKind.NORMAL,
            rightOf(below) to DoorKind.NORMAL,
            rightOf(GridPos(1, 1)) to DoorKind.NORMAL,
            MapGap(GridPos(3, 0), horizontal = false) to DoorKind.NORMAL,
        )
        assertEquals(BloodRushPath(listOf(a, b, tile2), rightOf(tile2)), FunnyMapLayout.bloodRushPath(rooms, doors))
    }

    @Test
    fun `blood rush doesn't leave a room by a normal door, except the entrance`() {
        val rooms = mapOf(a to entrance, b to room("One"), tile2 to room("Side"))
        val doors = mapOf(rightOf(a) to DoorKind.NORMAL, rightOf(b) to DoorKind.NORMAL)
        assertEquals(BloodRushPath(listOf(a, b), null, reachesBlood = false), FunnyMapLayout.bloodRushPath(rooms, doors))
    }

    @Test
    fun `blood rush goes as far as what's loaded, ending at a wither door into the unknown`() {
        val rooms = mapOf(a to entrance, b to room("One"))
        val doors = mapOf(rightOf(a) to DoorKind.NORMAL, rightOf(b) to DoorKind.WITHER)
        assertEquals(BloodRushPath(listOf(a, b), rightOf(b), reachesBlood = false), FunnyMapLayout.bloodRushPath(rooms, doors))
    }

    @Test
    fun `blood rush walks through a multi-tile room between its doors`() {
        val long = room("Long")
        val rooms = mapOf(a to entrance, b to long, tile2 to long, tile3 to blood)
        val doors = mapOf(rightOf(a) to DoorKind.NORMAL, rightOf(tile2) to DoorKind.BLOOD)
        assertEquals(BloodRushPath(listOf(a, b, tile2), rightOf(tile2)), FunnyMapLayout.bloodRushPath(rooms, doors))
    }

    @Test
    fun `where the wither doors branch, blood rush takes the branch to blood even if it's longer`() {
        val rooms = chainRooms(room("One"), room("Two"), room("Three")) + mapOf(GridPos(1, 1) to room("Dead End"))
        val doors = chainDoors(3) + mapOf(under(b) to DoorKind.WITHER)
        assertEquals(listOf(a, b, tile2, tile3), FunnyMapLayout.bloodRushPath(rooms, doors)?.tiles)
    }

    @Test
    fun `where the wither doors branch and neither reaches blood, it takes the one that goes furthest`() {
        val rooms = mapOf(a to entrance, b to room("One"), GridPos(1, 1) to room("Short"), tile2 to room("Long"), tile3 to room("Longer"))
        val doors = mapOf(
            rightOf(a) to DoorKind.NORMAL,
            under(b) to DoorKind.WITHER,
            rightOf(b) to DoorKind.WITHER,
            rightOf(tile2) to DoorKind.WITHER,
        )
        assertEquals(listOf(a, b, tile2, tile3), FunnyMapLayout.bloodRushPath(rooms, doors)?.tiles)
    }

    @Test
    fun `no blood rush line until there's a door out of a known entrance`() {
        assertNull(FunnyMapLayout.bloodRushPath(mapOf(a to entrance), emptyMap()))
        assertNull(FunnyMapLayout.bloodRushPath(mapOf(b to blood), mapOf(gapAB to DoorKind.BLOOD)))
        assertNull(FunnyMapLayout.bloodRushPath(mapOf(a to entrance, b to blood), emptyMap()))
    }

    @Test
    fun `room count leaves out entrance, fairy and blood`() {
        val rooms = chainRooms(room("One"), typed("Fairy", "FAIRY"), room("Two"))
        assertEquals(2, FunnyMapLayout.bloodRushRoomCount(rooms, chainDoors(3)))
    }

    @Test
    fun `room count counts a multi-tile room once`() {
        val long = room("Long")
        val rooms = mapOf(a to entrance, b to long, tile2 to long, tile3 to room("Two"), tile4 to blood)
        val doors = mapOf(rightOf(a) to DoorKind.NORMAL, rightOf(tile2) to DoorKind.WITHER, rightOf(tile3) to DoorKind.BLOOD)
        assertEquals(2, FunnyMapLayout.bloodRushRoomCount(rooms, doors))
    }

    @Test
    fun `room count needs the chain to reach blood`() {
        val rooms = mapOf(a to entrance, b to room("One"))
        assertNull(FunnyMapLayout.bloodRushRoomCount(rooms, mapOf(rightOf(a) to DoorKind.NORMAL, rightOf(b) to DoorKind.WITHER)))
    }

    @Test
    fun `the possible count already includes every room on the chain so far`() {
        val rooms = mapOf(a to entrance, b to room("One"), tile2 to room("Two"), tile3 to room("Three"))
        val doors = mapOf(
            rightOf(a) to DoorKind.NORMAL,
            rightOf(b) to DoorKind.WITHER,
            rightOf(tile2) to DoorKind.WITHER,
            rightOf(tile3) to DoorKind.WITHER,
        )
        assertEquals(3, FunnyMapLayout.possibleBloodRushRoomCount(rooms, doors, emptySet()))
    }

    @Test
    fun `a wither door on means another room, once it can't be the fairy room`() {
        val doors = mapOf(rightOf(a) to DoorKind.NORMAL, rightOf(b) to DoorKind.WITHER)
        val fairyUnknown = mapOf(a to entrance, b to room("One"))
        assertEquals(1, FunnyMapLayout.possibleBloodRushRoomCount(fairyUnknown, doors, emptySet()))
        val fairyKnown = fairyUnknown + (GridPos(5, 5) to typed("Fairy", "FAIRY"))
        assertEquals(2, FunnyMapLayout.possibleBloodRushRoomCount(fairyKnown, doors, emptySet()))
    }

    @Test
    fun `with no door on, it's only possible if the last room still has a gap to load`() {
        val rooms = mapOf(a to entrance, b to room("One"))
        val doors = mapOf(rightOf(a) to DoorKind.NORMAL)
        assertEquals(1, FunnyMapLayout.possibleBloodRushRoomCount(rooms, doors, setOf(rightOf(b))))
        assertNull(FunnyMapLayout.possibleBloodRushRoomCount(rooms, doors, setOf(rightOf(tile3))))
    }

    @Test
    fun `a chain that's reached blood isn't a possible count any more`() {
        assertNull(FunnyMapLayout.possibleBloodRushRoomCount(chainRooms(room("One")), chainDoors(1), emptySet()))
    }

    private val longRoom = RoomDatabase.RoomInfo("Long", "NORMAL", "1x3")
    private val longTop = GridPos(0, 0)
    private val longMiddle = GridPos(0, 1)
    private val longBottom = GridPos(0, 2)
    private val underLong = GridPos(0, 3)

    @Test
    fun `a room's missing tile is filled in when its shape only fits one way`() {
        val rooms = mapOf(longMiddle to longRoom, longBottom to longRoom, underLong to room("Other"))
        val inferred = FunnyMapLayout.withInferredTiles(rooms, setOf(under(longTop)), emptySet())
        assertEquals(longRoom, inferred[longTop])
        assertEquals(4, inferred.size)
    }

    @Test
    fun `a room's missing tile stays unknown while its shape could fit either way`() {
        val rooms = mapOf(longMiddle to longRoom, longBottom to longRoom)
        val unknown = setOf(under(longTop), under(longMiddle), under(longBottom))
        assertEquals(rooms, FunnyMapLayout.withInferredTiles(rooms, emptySet(), unknown))
    }

    @Test
    fun `a room can't extend across a loaded gap that isn't part of a room`() {
        val rooms = mapOf(longMiddle to longRoom, longBottom to longRoom)
        val inferred = FunnyMapLayout.withInferredTiles(rooms, emptySet(), setOf(under(longBottom)))
        assertEquals(longRoom, inferred[underLong])
        assertNull(inferred[longTop])
    }

    @Test
    fun `a joined gap settles which way a room goes when both ends are free`() {
        val rooms = mapOf(longMiddle to longRoom, longBottom to longRoom)
        val inferred = FunnyMapLayout.withInferredTiles(rooms, setOf(under(longTop)), setOf(under(longBottom)))
        assertEquals(longRoom, inferred[longTop])
        assertNull(inferred[underLong])
    }

    @Test
    fun `a room can be filled in even with air in the gap between its known tiles`() {
        val rooms = mapOf(longMiddle to longRoom, longBottom to longRoom, underLong to room("Other"))
        val inferred = FunnyMapLayout.withInferredTiles(rooms, emptySet(), setOf(under(longTop)))
        assertEquals(longRoom, inferred[longTop])
    }

    @Test
    fun `gap side columns run along the gap, clear of the door`() {
        val horizontal = MapGap(GridPos(0, 0), horizontal = true)
        val (x, z) = FunnyMapLayout.gapColumn(horizontal)
        assertTrue(FunnyMapLayout.gapSideColumns(horizontal).all { it.first == x && kotlin.math.abs(it.second - z) > 2 })
        val vertical = MapGap(GridPos(0, 0), horizontal = false)
        val (vx, vz) = FunnyMapLayout.gapColumn(vertical)
        assertTrue(FunnyMapLayout.gapSideColumns(vertical).all { it.second == vz && kotlin.math.abs(it.first - vx) > 2 })
    }

    // Entrance, then a fairy room, then a wither door into the long room, whose top hasn't loaded.
    private val rushEntrance = GridPos(1, 3)
    private val rushRooms get() = mapOf(
        rushEntrance to entrance,
        underLong to typed("Fairy", "FAIRY"),
        longBottom to longRoom,
        longMiddle to longRoom,
        longTop to longRoom,
    )
    private val rushDoors = mapOf(
        rightOf(underLong) to DoorKind.NORMAL,
        under(longBottom) to DoorKind.WITHER,
        under(longTop) to DoorKind.NORMAL,
        under(longMiddle) to DoorKind.NORMAL,
    )

    private val pastExit = GridPos(1, 0)
    private val rushRoute = listOf(rushEntrance, underLong, longBottom, longMiddle, longTop)

    @Test
    fun `the tile past the only way out is blood when the chain can't go on from it`() {
        assertEquals(
            BloodRushPath(rushRoute, rightOf(longTop), reachesBlood = true, bloodTile = pastExit),
            FunnyMapLayout.bloodRushPath(rushRooms, rushDoors, setOf(rightOf(longTop))),
        )
    }

    @Test
    fun `an unloaded side doesn't stop that tile being blood if a normal door already leads there`() {
        val unknown = setOf(rightOf(longTop), rightOf(pastExit))
        val fromBelow = rushRooms + (GridPos(2, 1) to room("Cell"))
        val doors = rushDoors + (under(tile2) to DoorKind.NORMAL)
        assertEquals(pastExit, FunnyMapLayout.bloodRushPath(fromBelow, doors, unknown)?.bloodTile)
    }

    @Test
    fun `blood rush only ends at the way out while a complete unknown lies past it`() {
        assertEquals(
            BloodRushPath(rushRoute, rightOf(longTop), reachesBlood = false),
            FunnyMapLayout.bloodRushPath(rushRooms, rushDoors, setOf(rightOf(longTop), rightOf(pastExit))),
        )
    }

    @Test
    fun `the tile past the way out isn't blood while the fairy room could still be it`() {
        val noFairy = rushRooms + (underLong to room("Not Fairy"))
        val path = FunnyMapLayout.bloodRushPath(noFairy, rushDoors, setOf(rightOf(longTop)))
        assertEquals(rightOf(longTop), path?.door)
        assertNull(path?.bloodTile)
    }

    @Test
    fun `the tile past the way out isn't blood if another door leads into it`() {
        val doors = rushDoors + (under(pastExit) to DoorKind.NORMAL)
        assertNull(FunnyMapLayout.bloodRushPath(rushRooms, doors, setOf(rightOf(longTop)))?.bloodTile)
    }

    @Test
    fun `a blood room that's been worked out gives a definite count`() {
        assertEquals(1, FunnyMapLayout.bloodRushRoomCount(rushRooms, rushDoors, setOf(rightOf(longTop))))
        assertNull(FunnyMapLayout.bloodRushRoomCount(rushRooms, rushDoors, setOf(rightOf(longTop), rightOf(pastExit))))
    }

    @Test
    fun `blood rush doesn't guess between two possible ways out`() {
        val path = FunnyMapLayout.bloodRushPath(rushRooms, rushDoors, setOf(rightOf(longTop), rightOf(longMiddle)))
        assertNull(path?.door)
    }

    @Test
    fun `a way out that's only worked out could lead straight to blood, so adds no room to the count`() {
        assertEquals(1, FunnyMapLayout.possibleBloodRushRoomCount(rushRooms, rushDoors, setOf(rightOf(longTop))))
    }

    @Test
    fun `a lone wither door on the fairy room is its way out`() {
        val fairy = typed("Fairy", "FAIRY")
        val rooms = mapOf(a to entrance, b to fairy)
        assertEquals(listOf(MapGap(b, horizontal = true)), FunnyMapLayout.fairyExitDoors(rooms, mapOf(MapGap(b, horizontal = true) to DoorKind.WITHER, gapAB to DoorKind.NORMAL)))
    }

    @Test
    fun `with several wither doors on the fairy room, the way in from the entrance is left out`() {
        val fairy = typed("Fairy", "FAIRY")
        val out = MapGap(b, horizontal = true)
        val rooms = mapOf(a to entrance, b to fairy, GridPos(2, 0) to room("Next"))
        assertEquals(listOf(out), FunnyMapLayout.fairyExitDoors(rooms, mapOf(gapAB to DoorKind.WITHER, out to DoorKind.WITHER)))
    }

    @Test
    fun `with no route to the fairy room known, all of its wither doors count`() {
        val fairy = typed("Fairy", "FAIRY")
        val left = MapGap(a, horizontal = true)
        val right = MapGap(b, horizontal = true)
        val rooms = mapOf(b to fairy)
        assertEquals(setOf(left, right), FunnyMapLayout.fairyExitDoors(rooms, mapOf(left to DoorKind.WITHER, right to DoorKind.WITHER)).toSet())
    }

    @Test
    fun `no fairy room or wither door means nothing to highlight`() {
        assertEquals(emptyList(), FunnyMapLayout.fairyExitDoors(mapOf(a to entrance), mapOf(gapAB to DoorKind.WITHER)))
        assertEquals(emptyList(), FunnyMapLayout.fairyExitDoors(mapOf(b to typed("Fairy", "FAIRY")), mapOf(gapAB to DoorKind.NORMAL)))
    }

    @Test
    fun `the text size slider moves both a label's full size and its floor`() {
        val normalFull = FunnyMapLayout.labelScale(10, 24)
        assertEquals(normalFull * 2, FunnyMapLayout.labelScale(10, 24, sizeFactor = 2f))
        val normalFloor = FunnyMapLayout.labelScale(10000, 24)
        assertEquals(normalFloor * 2, FunnyMapLayout.labelScale(10000, 24, sizeFactor = 2f))
    }

    @Test
    fun `gapsAround finds all four sides, and fewer at the edge of the grid`() {
        val middle = GridPos(2, 3)
        assertEquals(
            setOf(
                MapGap(GridPos(2, 3), horizontal = true),
                MapGap(GridPos(2, 3), horizontal = false),
                MapGap(GridPos(1, 3), horizontal = true),
                MapGap(GridPos(2, 2), horizontal = false),
            ),
            FunnyMapLayout.gapsAround(middle).toSet(),
        )
        assertEquals(setOf(gapAB, MapGap(a, horizontal = false)), FunnyMapLayout.gapsAround(a).toSet())
    }

    @Test
    fun `doorOpening is a 3 by 3 by 4 block centered on the gap column`() {
        val opening = FunnyMapLayout.doorOpening(gapAB)
        assertEquals(36, opening.toSet().size)
        assertEquals(-170..-168, opening.minOf { it.first }..opening.maxOf { it.first })
        assertEquals(69..72, opening.minOf { it.second }..opening.maxOf { it.second })
        assertEquals(-186..-184, opening.minOf { it.third }..opening.maxOf { it.third })
    }

    @Test
    fun `worldToTile matches the grid, including just off each edge`() {
        assertEquals(0, FunnyMapLayout.worldToTile(-185.0))
        assertEquals(0, FunnyMapLayout.worldToTile(-200.5))
        assertEquals(1, FunnyMapLayout.worldToTile(-169.0))
        assertEquals(5, FunnyMapLayout.worldToTile(-25.0))
        assertEquals(-1, FunnyMapLayout.worldToTile(-210.0))
        assertEquals(6, FunnyMapLayout.worldToTile(-5.0))
    }

    @Test
    fun `a tile's world center lands on its cell's center`() {
        val tileCenterWorld = WorldRoomScanner.tileToScanColumn(GridPos(2, 0)).first.toDouble()
        val cellCenter = FunnyMapLayout.cellStart(2) + FunnyMapLayout.CELL_SIZE / 2.0
        assertEquals(cellCenter, FunnyMapLayout.worldToMap(tileCenterWorld), 1.0)
    }

    @Test
    fun `two tiles of the same room merge, and never show a door between them`() {
        val crypt = room("Crypt")
        val rooms = mapOf(a to crypt, b to crypt)
        assertTrue(FunnyMapLayout.isMerged(gapAB, rooms))
        assertNull(FunnyMapLayout.doorIn(gapAB, rooms, mapOf(gapAB to DoorKind.NORMAL)))
    }

    @Test
    fun `different rooms with a connected gap show its door`() {
        val rooms = mapOf(a to room("Crypt"), b to room("Mines"))
        assertFalse(FunnyMapLayout.isMerged(gapAB, rooms))
        assertEquals(DoorKind.WITHER, FunnyMapLayout.doorIn(gapAB, rooms, mapOf(gapAB to DoorKind.WITHER)))
    }

    @Test
    fun `different rooms with an empty gap have no door`() {
        val rooms = mapOf(a to room("Crypt"), b to room("Mines"))
        assertNull(FunnyMapLayout.doorIn(gapAB, rooms, emptyMap()))
    }

    @Test
    fun `a door only needs a known room on one side`() {
        assertEquals(DoorKind.NORMAL, FunnyMapLayout.doorIn(gapAB, mapOf(a to room("Crypt")), mapOf(gapAB to DoorKind.NORMAL)))
    }

    @Test
    fun `a door with no known room on either side is hidden`() {
        assertNull(FunnyMapLayout.doorIn(gapAB, emptyMap(), mapOf(gapAB to DoorKind.NORMAL)))
    }

    @Test
    fun `an unknown tile never merges with another unknown tile`() {
        assertFalse(FunnyMapLayout.isMerged(gapAB, emptyMap()))
    }

    @Test
    fun `rooms group by identity so a multi-tile room is listed once`() {
        val crypt = room("Crypt")
        val mines = room("Mines")
        val grouped = FunnyMapLayout.groupRooms(mapOf(a to crypt, b to crypt, GridPos(3, 3) to mines))
        assertEquals(setOf(a, b), grouped[crypt])
        assertEquals(setOf(GridPos(3, 3)), grouped[mines])
    }

    @Test
    fun `rectangular rooms label at their true middle`() {
        assertEquals(0.5 to 0.5, FunnyMapLayout.labelCenter(setOf(a)))
        assertEquals(1.0 to 0.5, FunnyMapLayout.labelCenter(setOf(a, b)))
        assertEquals(1.0 to 1.0, FunnyMapLayout.labelCenter(setOf(a, b, GridPos(0, 1), GridPos(1, 1))))
        assertEquals(2.0 to 0.5, FunnyMapLayout.labelCenter(setOf(a, b, GridPos(2, 0), GridPos(3, 0))))
    }

    @Test
    fun `an L room labels on its corner tile, not the empty space its middle falls on`() {
        assertEquals(1.5 to 1.5, FunnyMapLayout.labelCenter(setOf(GridPos(1, 1), GridPos(0, 1), GridPos(1, 0))))
    }

    @Test
    fun `opening any tile of a room opens all of it, and only that room`() {
        val crypt = room("Crypt")
        val rooms = mapOf(a to crypt, b to crypt, GridPos(3, 3) to room("Mines"))
        assertEquals(setOf(a, b), FunnyMapLayout.expandToWholeRooms(setOf(b), rooms))
    }

    @Test
    fun `an opened tile with no identified room still counts as opened`() {
        assertEquals(setOf(a), FunnyMapLayout.expandToWholeRooms(setOf(a), emptyMap()))
    }

    @Test
    fun `labels get the room's full span, or one cell for an L`() {
        assertEquals(FunnyMapLayout.CELL_SIZE, FunnyMapLayout.labelWidth(setOf(a)))
        assertEquals(FunnyMapLayout.CELL_SIZE * 2 + FunnyMapLayout.GAP_SIZE, FunnyMapLayout.labelWidth(setOf(a, b)))
        assertEquals(FunnyMapLayout.CELL_SIZE, FunnyMapLayout.labelWidth(setOf(a, b, GridPos(1, 1))))
    }

    @Test
    fun `short labels use the full size, long ones shrink to fit, down to a floor`() {
        assertEquals(0.65f, FunnyMapLayout.labelScale(10, 24), 0.001f)
        assertEquals(0.5f, FunnyMapLayout.labelScale(48, 24), 0.001f)
        assertEquals(0.4f, FunnyMapLayout.labelScale(500, 24), 0.001f)
    }

    @Test
    fun `scrolling steps the size up and down without drifting, within its limits`() {
        assertEquals(1.1f, FunnyMapLayout.steppedScale(1f, 1.0), 0.0001f)
        assertEquals(0.9f, FunnyMapLayout.steppedScale(1f, -1.0), 0.0001f)
        var scale = 1f
        repeat(5) { scale = FunnyMapLayout.steppedScale(scale, 1.0) }
        repeat(5) { scale = FunnyMapLayout.steppedScale(scale, -1.0) }
        assertEquals(1f, scale)
        assertEquals(FunnyMapLayout.MAX_SCALE, FunnyMapLayout.steppedScale(FunnyMapLayout.MAX_SCALE, 1.0))
        assertEquals(FunnyMapLayout.MIN_SCALE, FunnyMapLayout.steppedScale(FunnyMapLayout.MIN_SCALE, -1.0))
    }

    private fun typed(name: String, type: String) = RoomDatabase.RoomInfo(name, type, "1x1")
    private val entrance = typed("Entrance", "ENTRANCE")
    private val blood = typed("Blood", "BLOOD")
    private fun door(x: Int, z: Int, horizontal: Boolean) = MapGap(GridPos(x, z), horizontal)

    @Test
    fun `blood rush walks the rooms in order and ends at the blood door`() {
        val rooms = mapOf(GridPos(0, 0) to entrance, GridPos(1, 0) to room("Crypt"), GridPos(2, 0) to blood)
        val doors = mapOf(door(0, 0, true) to DoorKind.NORMAL, door(1, 0, true) to DoorKind.BLOOD)
        assertEquals(
            BloodRushPath(listOf(GridPos(0, 0), GridPos(1, 0)), door(1, 0, true)),
            FunnyMapLayout.bloodRushPath(rooms, doors),
        )
    }

    @Test
    fun `the sample map has a blood rush path`() {
        assertNotNull(FunnyMapLayout.sample.bloodRush)
    }

    @Test
    fun `a checkmark on any tile applies to the whole room, and green outranks white`() {
        val crypt = room("Crypt")
        val rooms = mapOf(a to crypt, b to crypt)
        assertEquals(mapOf(crypt to MapCheckmark.WHITE), FunnyMapLayout.roomCheckmarks(mapOf(a to MapCheckmark.WHITE), rooms))
        assertEquals(
            mapOf(crypt to MapCheckmark.GREEN),
            FunnyMapLayout.roomCheckmarks(linkedMapOf(a to MapCheckmark.WHITE, b to MapCheckmark.GREEN), rooms),
        )
        assertEquals(
            mapOf(crypt to MapCheckmark.GREEN),
            FunnyMapLayout.roomCheckmarks(linkedMapOf(b to MapCheckmark.GREEN, a to MapCheckmark.WHITE), rooms),
        )
    }

    @Test
    fun `secret counts come from the action bar, capped at the total, until green proves them all`() {
        val mines = RoomDatabase.RoomInfo("Mines", "NORMAL", "2x2", secrets = 10)
        fun snapshot(found: Int?, checkmark: MapCheckmark?) = FunnyMapSnapshot(
            emptyMap(),
            emptyMap(),
            emptySet(),
            checkmark?.let { mapOf(mines to it) } ?: emptyMap(),
            found?.let { mapOf(mines to it) } ?: emptyMap(),
        )
        assertEquals(0, snapshot(null, null).secretsFound(mines))
        assertEquals(4, snapshot(4, MapCheckmark.WHITE).secretsFound(mines))
        assertEquals(10, snapshot(99, null).secretsFound(mines))
        assertEquals(10, snapshot(2, MapCheckmark.GREEN).secretsFound(mines))
    }

    @Test
    fun `a white or green checkmark means cleared, anything else doesn't`() {
        val mines = room("Mines")
        fun cleared(checkmark: MapCheckmark?) =
            FunnyMapSnapshot(emptyMap(), emptyMap(), emptySet(), checkmark?.let { mapOf(mines to it) } ?: emptyMap()).isCleared(mines)
        assertTrue(cleared(MapCheckmark.WHITE))
        assertTrue(cleared(MapCheckmark.GREEN))
        assertFalse(cleared(MapCheckmark.RED))
        assertFalse(cleared(null))
    }

    @Test
    fun `a head points the way its player is facing`() {
        assertEquals(0f, FunnyMapLayout.headRotationDegrees(180f), 0.001f) // north = up
        assertEquals(180f, FunnyMapLayout.headRotationDegrees(0f), 0.001f) // south = down
        assertEquals(90f, FunnyMapLayout.headRotationDegrees(-90f), 0.001f) // east = right
        assertEquals(270f, FunnyMapLayout.headRotationDegrees(90f), 0.001f) // west = left
        assertEquals(270f, FunnyMapLayout.headRotationDegrees(90f + 720f), 0.001f) // yRot is unbounded
        assertEquals(90f, FunnyMapLayout.headRotationDegrees(-90f - 720f), 0.001f)
    }

    @Test
    fun `with the room limit off every label is the same size, however long`() {
        val full = FunnyMapLayout.labelScale(10, 24, limitToRoom = false)
        assertEquals(full, FunnyMapLayout.labelScale(48, 24, limitToRoom = false))
        assertEquals(full, FunnyMapLayout.labelScale(500, 24, limitToRoom = false))
        assertEquals(FunnyMapLayout.labelScale(10, 24), full)
    }

    @Test
    fun `every special room type really exists in the room database`() {
        val types = RoomDatabase.allRooms.map { it.type }.toSet()
        for ((label, type) in FunnyMapLayout.SPECIAL_ROOM_TYPES) assertTrue(type in types, "$label uses unknown type $type")
    }

    @Test
    fun `the sample map only uses tiles on the grid, and its doors join adjacent known rooms`() {
        assertTrue(FunnyMapLayout.sampleRooms.keys.all { it.x in 0..5 && it.z in 0..5 })
        assertTrue(FunnyMapLayout.sampleDoors.keys.all { it in FunnyMapLayout.gaps })
        assertTrue(FunnyMapLayout.sampleDoors.keys.all { FunnyMapLayout.doorIn(it, FunnyMapLayout.sampleRooms, FunnyMapLayout.sampleDoors) != null })
    }
}
