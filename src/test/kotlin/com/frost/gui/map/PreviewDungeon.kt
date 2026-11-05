package com.frost.gui.map

import com.frost.dungeon.DoorKind
import com.frost.dungeon.FunnyMapLayout
import com.frost.dungeon.FunnyMapSnapshot
import com.frost.dungeon.GridPos
import com.frost.dungeon.MapCheckmark
import com.frost.dungeon.MapGap
import com.frost.dungeon.RoomDatabase

/**
 * A whole 6x6 dungeon partway through a run, with every room shape and type, a failed puzzle,
 * a rare room, and a wither door chain to blood - for the map style tests and preview images.
 *
 * ```
 *       0          1          2          3          4          5
 * 0  Waterfall  Waterfall  Waterfall  Waterfall  Quiz       Blood
 * 1  Archway    Sand Drgn  Mines      Mines      Shadow A.  Banners
 * 2  Archway    Old Trap   Mines      Mines      Altar      Altar
 * 3  Ice Fill   Crypt      Crypt      Fairy      Red Flag   Altar
 * 4  Admin      Slime      Slime      Slime      Atlas      Atlas
 * 5  Andesite   Lava Pit   Entrance   Beams      Atlas      Atlas
 * ```
 */
object PreviewDungeon {
    private fun room(name: String, type: String, shape: String, secrets: Int = 0) = RoomDatabase.RoomInfo(name, type, shape, secrets = secrets)

    val entrance = room("Entrance", "ENTRANCE", "1x1")
    val slime = room("Slime", "NORMAL", "1x3", 5)
    val lavaPit = room("Lava Pit", "RARE", "1x1", 3)
    val andesite = room("Andesite", "NORMAL", "1x1", 2)
    val beams = room("Beams", "NORMAL", "1x1", 2)
    val atlas = room("Atlas", "NORMAL", "2x2", 6)
    val admin = room("Admin", "NORMAL", "1x1", 1)
    val iceFill = room("Ice Fill", "PUZZLE", "1x1")
    val crypt = room("Crypt", "NORMAL", "1x2", 5)
    val fairy = room("Fairy", "FAIRY", "1x1")
    val mines = room("Mines", "NORMAL", "2x2", 10)
    val oldTrap = room("Old Trap", "TRAP", "1x1", 4)
    val archway = room("Archway", "NORMAL", "1x2", 3)
    val sandDragon = room("Sand Dragon", "RARE", "1x1", 1)
    val waterfall = room("Waterfall", "NORMAL", "1x4", 8)
    val quiz = room("Quiz", "PUZZLE", "1x1")
    val shadowAssassin = room("Shadow Assassin", "CHAMPION", "1x1")
    val banners = room("Banners", "NORMAL", "1x1", 1)
    val blood = room("Blood", "BLOOD", "1x1")
    val altar = room("Altar", "NORMAL", "L", 6)
    val redFlag = room("Big Red Flag", "NORMAL", "1x1", 2)

    private val grid = listOf(
        listOf(waterfall, waterfall, waterfall, waterfall, quiz, blood),
        listOf(archway, sandDragon, mines, mines, shadowAssassin, banners),
        listOf(archway, oldTrap, mines, mines, altar, altar),
        listOf(iceFill, crypt, crypt, fairy, redFlag, altar),
        listOf(admin, slime, slime, slime, atlas, atlas),
        listOf(andesite, lavaPit, entrance, beams, atlas, atlas),
    )

    val rooms: Map<GridPos, RoomDatabase.RoomInfo> =
        grid.flatMapIndexed { z, row -> row.mapIndexed { x, room -> GridPos(x, z) to room } }.toMap()

    private fun east(x: Int, z: Int) = MapGap(GridPos(x, z), horizontal = true)
    private fun south(x: Int, z: Int) = MapGap(GridPos(x, z), horizontal = false)

    val doors: Map<MapGap, DoorKind> = mapOf(
        // The wither chain: out of the entrance, through the fairy room, to blood.
        south(2, 4) to DoorKind.NORMAL,
        south(3, 3) to DoorKind.WITHER,
        south(3, 2) to DoorKind.WITHER,
        east(3, 1) to DoorKind.WITHER,
        east(4, 1) to DoorKind.WITHER,
        south(5, 0) to DoorKind.BLOOD,
        // Everything else.
        east(1, 5) to DoorKind.NORMAL,
        east(0, 5) to DoorKind.NORMAL,
        east(2, 5) to DoorKind.NORMAL,
        east(3, 5) to DoorKind.NORMAL,
        east(0, 4) to DoorKind.NORMAL,
        south(0, 3) to DoorKind.NORMAL,
        south(1, 3) to DoorKind.NORMAL,
        south(1, 2) to DoorKind.NORMAL,
        east(0, 2) to DoorKind.NORMAL,
        east(0, 1) to DoorKind.NORMAL,
        south(0, 0) to DoorKind.NORMAL,
        east(3, 0) to DoorKind.NORMAL,
        east(3, 3) to DoorKind.NORMAL,
        south(4, 2) to DoorKind.NORMAL,
    )

    private val openedRooms = setOf(entrance, slime, lavaPit, andesite, beams, atlas, admin, iceFill, crypt, fairy, mines, oldTrap)

    val opened: Set<GridPos> = rooms.filterValues { it in openedRooms }.keys

    val checkmarks: Map<RoomDatabase.RoomInfo, MapCheckmark> = mapOf(
        slime to MapCheckmark.GREEN,
        andesite to MapCheckmark.GREEN,
        admin to MapCheckmark.GREEN,
        fairy to MapCheckmark.GREEN,
        lavaPit to MapCheckmark.WHITE,
        beams to MapCheckmark.WHITE,
        crypt to MapCheckmark.WHITE,
        oldTrap to MapCheckmark.WHITE,
        iceFill to MapCheckmark.RED,
    )

    val foundSecrets: Map<RoomDatabase.RoomInfo, Int> = mapOf(crypt to 3, atlas to 2, mines to 4, lavaPit to 1, beams to 1, oldTrap to 2)

    /** Everything, as Funny Map sees it. */
    val funny = FunnyMapSnapshot(
        rooms,
        doors,
        opened,
        checkmarks,
        foundSecrets,
        FunnyMapLayout.bloodRushPath(rooms, doors),
        teamSecrets = 19,
        totalSecrets = FunnyMapLayout.totalSecrets(19, 31.1),
    )

    /** What a player without the world scan would know. */
    val legit = FunnyMapLayout.legitView(funny)

    /** Where everyone is, in tile units (a tile's center is its index + 0.5), with their class
     * and which way they're facing. The first is the local player. */
    data class Player(val name: String, val className: String, val tileX: Double, val tileZ: Double, val yaw: Float)

    val players = listOf(
        Player("You", "Mage", 2.9, 1.8, 200f),
        Player("Archie", "Archer", 4.6, 4.9, 90f),
        Player("Tanky", "Tank", 1.4, 3.5, -30f),
        Player("Healz", "Healer", 2.5, 4.4, 160f),
    )
}
