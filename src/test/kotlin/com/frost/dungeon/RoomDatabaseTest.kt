package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomDatabaseTest {
    @Test
    fun `looks up a known room by its core hash`() {
        // "Admin" in the bundled rooms.json has a single core hash of 518379920.
        val room = RoomDatabase.lookup(518379920)

        assertEquals("Admin", room?.name)
        assertEquals("NORMAL", room?.type)
        assertEquals("1x1", room?.shape)
    }

    @Test
    fun `returns null for an unknown core hash`() {
        assertNull(RoomDatabase.lookup(0))
    }

    @Test
    fun `loads a substantial number of rooms from the bundled database`() {
        // Sanity check that the resource actually loaded rather than silently falling
        // back to an empty map (e.g. a wrong resource path) - the real file has ~140.
        val knownCores = listOf(518379920, -1701988142, 259768244)
        assertTrue(knownCores.all { RoomDatabase.lookup(it) != null })
    }

    @Test
    fun `allRooms has exactly one entry per room, not one per core hash`() {
        // "Ice Fill" and several others have multiple cores (one per map variant) all
        // pointing at the same room - allRooms must dedupe those back down to one entry.
        val iceFillEntries = RoomDatabase.allRooms.filter { it.name == "Ice Fill" }
        assertEquals(1, iceFillEntries.size)
    }

    @Test
    fun `allRooms is sorted alphabetically by name`() {
        val names = RoomDatabase.allRooms.map { it.name }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun `roomsByShape only returns rooms of that exact shape`() {
        val oneByOnes = RoomDatabase.roomsByShape("1x1")

        assertTrue(oneByOnes.isNotEmpty())
        assertTrue(oneByOnes.all { it.shape == "1x1" })
        assertTrue(oneByOnes.none { it.shape == "L" })
    }

    @Test
    fun `every SHAPES category has at least one room in the bundled database`() {
        for (shape in RoomDatabase.SHAPES) {
            assertTrue(RoomDatabase.roomsByShape(shape).isNotEmpty(), "expected at least one room for shape $shape")
        }
    }

    @Test
    fun `roomsByShape excludes misc-type rooms even though they're all shape 1x1`() {
        val oneByOnes = RoomDatabase.roomsByShape("1x1")

        assertTrue(oneByOnes.none { it.name == "Ice Fill" }) // PUZZLE
        assertTrue(oneByOnes.none { it.name == "Old Trap" }) // TRAP
        assertTrue(oneByOnes.none { it.name == "Entrance" }) // ENTRANCE
        assertTrue(oneByOnes.none { it.name == "Fairy" }) // FAIRY
        assertTrue(oneByOnes.none { it.name == "Blood" }) // BLOOD
    }

    @Test
    fun `roomsInCategory MISC returns puzzle, entrance, fairy, blood, and trap rooms`() {
        val misc = RoomDatabase.roomsInCategory(RoomDatabase.MISC_CATEGORY)

        assertTrue(misc.any { it.name == "Ice Fill" })
        assertTrue(misc.any { it.name == "Old Trap" })
        assertTrue(misc.any { it.name == "Entrance" })
        assertTrue(misc.any { it.name == "Fairy" })
        assertTrue(misc.any { it.name == "Blood" })
        assertTrue(misc.all { it.type in setOf("PUZZLE", "ENTRANCE", "FAIRY", "BLOOD", "TRAP") })
    }

    @Test
    fun `CATEGORIES includes all 6 shapes plus Misc`() {
        assertEquals(RoomDatabase.SHAPES + RoomDatabase.MISC_CATEGORY, RoomDatabase.CATEGORIES)
    }

    @Test
    fun `categoryLabel formats L and Misc specially`() {
        assertEquals("L Rooms", RoomDatabase.categoryLabel("L"))
        assertEquals("Misc. Rooms", RoomDatabase.categoryLabel(RoomDatabase.MISC_CATEGORY))
        assertEquals("1x2 Rooms", RoomDatabase.categoryLabel("1x2"))
    }
}
