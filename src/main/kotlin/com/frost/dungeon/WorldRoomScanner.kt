package com.frost.dungeon

import net.minecraft.block.Blocks
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.chunk.WorldChunk

/**
 * Identifies a dungeon room's real Hypixel name by scanning one fixed block column and
 * hashing its contents, then looking that hash up in [RoomDatabase]. Both the scan
 * algorithm and the room database are a direct, deliberately faithful port of Odin's
 * `WorldScan.getRoomCore`/`RoomData` (BSD 3-Clause - see /THIRD_PARTY_NOTICES.md at the
 * repo root) - the exact block-by-block control flow matters, since scanning even
 * slightly differently produces a different hash that won't match the bundled database.
 *
 * Several earlier attempts here tried to per-run "calibrate" this against
 * [DungeonGrid.physicalRoomCorner]/entranceCorner, on the theory that dungeon instances
 * spawn at varying absolute world coordinates. That theory was wrong: pulling Odin's own
 * real source directly confirmed `WorldScan.scanChunk` hard-codes a FIXED, universal chunk
 * range (`chunkPosition.x in -12..-2 && chunkPosition.z in -12..-2`) with no per-run
 * calibration at all - dungeon instances always occupy the exact same absolute world
 * coordinates. The floor-dependent constants elsewhere in Odin's code (`DungeonScan`'s
 * `startX`/`startY`) are for map-PIXEL rendering only, a cosmetic concern unrelated to
 * world-block coordinates - conflating the two was the root of the earlier false
 * diagnosis. This is now a direct, uncalibrated port of Odin's fixed formula.
 */
object WorldRoomScanner {
    private const val ROOM_SIZE = 32
    private const val ORIGIN_OFFSET = -185

    /**
     * The fixed world (x, z) Odin's own database was fingerprinted against for a given
     * room-grid tile - do not change this formula without re-deriving new core hashes.
     *
     * Odin scans at `chunkPosition * 16 + 7`, where `chunkPosition = (tile - 6) * 2`. That
     * expands to `tile * 32 - 185` - the "+7" is already folded into -185, it is NOT added
     * again on top.
     */
    fun tileToScanColumn(tile: GridPos): Pair<Int, Int> {
        val worldX = tile.x * ROOM_SIZE + ORIGIN_OFFSET
        val worldZ = tile.z * ROOM_SIZE + ORIGIN_OFFSET
        return worldX to worldZ
    }

    /**
     * Scans the block column at world (x, z) top-down and returns its "core" hash - the
     * same fingerprint [RoomDatabase] is keyed by, plus the highest non-air block found
     * (useful later for rotation/orientation work, currently unused).
     */
    fun scanCore(chunk: WorldChunk, worldX: Int, worldZ: Int): Pair<Int, Int> {
        val sb = StringBuilder(256)
        var foundHighest = false
        var highestBlock = 0
        var bedrock = 0

        for (y in 140 downTo 12) {
            val state = chunk.getBlockState(BlockPos(worldX, y, worldZ))

            if (!foundHighest) {
                if (!state.isAir && state.block !== Blocks.GOLD_BLOCK) {
                    foundHighest = true
                    highestBlock = y
                } else {
                    sb.append('0')
                }
            }

            if (foundHighest) {
                if (state.isAir && bedrock >= 2 && y < 69) {
                    repeat(y - 11) { sb.append('0') }
                    break
                }
                if (state.block === Blocks.BEDROCK) {
                    bedrock++
                } else {
                    bedrock = 0
                    if (state.block === Blocks.OAK_PLANKS || state.block === Blocks.TRAPPED_CHEST || state.block === Blocks.CHEST) {
                        continue
                    }
                }
                sb.append(state.block)
            }
        }

        return sb.toString().hashCode() to highestBlock
    }

    /**
     * Identifies the room at [tile] by scanning its block column - but only if that
     * column's chunk is currently loaded on the client (returns null otherwise). Driven
     * from a per-tick sweep in the mod's map polling rather than a one-shot chunk-load
     * event: chunks that were already loaded when the run started (near spawn / the top of
     * the map) never re-fire a load event, and rooms cleared faster than the player reaches
     * them wouldn't be scanned in time either. Polling every loaded-but-unidentified tile
     * catches both, at the cost of a cheap column read per unknown tile per tick.
     */
    fun scanTileIfLoaded(world: ClientWorld, tile: GridPos): RoomDatabase.RoomInfo? {
        val (scanX, scanZ) = tileToScanColumn(tile)
        val chunk = world.chunkManager.getWorldChunk(scanX shr 4, scanZ shr 4) ?: return null
        val (core, _) = scanCore(chunk, scanX, scanZ)
        return RoomDatabase.lookup(core)
    }
}
