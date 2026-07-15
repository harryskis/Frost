package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals

class WorldRoomScannerTest {
    @Test
    fun `tileToScanColumn matches the reference implementation's fixed chunk-position formula`() {
        // Odin's WorldScan scans at (chunkPosition * 16) + 7, where chunkPosition maps to
        // tile via (chunkPosition / 2) + 6 - tile (0, 0) is chunk (-12, -12), giving scan
        // position (-192 + 7, -192 + 7) = (-185, -185). Do not change this without
        // re-deriving new core hashes against the bundled rooms.json. Confirmed directly
        // against Odin's real source: WorldScan.scanChunk hard-codes chunk positions in
        // -12..-2 for both axes, with no per-run calibration at all.
        assertEquals(-185 to -185, WorldRoomScanner.tileToScanColumn(GridPos(0, 0)))
        // Tile (5, 5) is chunk (-2, -2): (-32 + 7, -32 + 7) = (-25, -25).
        assertEquals(-25 to -25, WorldRoomScanner.tileToScanColumn(GridPos(5, 5)))
    }

    @Test
    fun `tileToScanColumn moves by exactly one room size per tile`() {
        val a = WorldRoomScanner.tileToScanColumn(GridPos(2, 3))
        val b = WorldRoomScanner.tileToScanColumn(GridPos(3, 4))

        assertEquals(a.first + 32, b.first)
        assertEquals(a.second + 32, b.second)
    }
}
