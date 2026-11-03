package com.frost.dungeon

import com.frost.config.ModConfig
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/**
 * Turns the door out of the entrance room, and optionally wither doors, into glass. Only the
 * client's copy of the world changes, and glass is as solid as what it replaces, so the doors
 * still block the player and still open normally: when the server removes a door its update
 * simply overwrites the glass.
 *
 * Polled rather than hooked on chunk loads, so a door the server sends again later gets caught
 * too. Anything reading door blocks should ask [originalBlock] first - see FunnyMap.scanDoor.
 */
object GlassDoors {
    // Often enough that a freshly loaded door doesn't visibly sit there as coal.
    private const val SCAN_INTERVAL_TICKS = 5

    // Known shape, so swapping a block doesn't reconnect the fences and panes around a door.
    private const val SET_BLOCK_FLAGS = Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE

    private val GLASS: BlockState = Blocks.GLASS.defaultBlockState()

    private val replaced = HashMap<BlockPos, BlockState>()
    private var entranceTile: GridPos? = null
    private var scannedLevel: ClientLevel? = null
    private var tickCounter = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> onTick(client) }
    }

    /** The block the server really has at [pos], or null if this hasn't replaced it. */
    fun originalBlock(pos: BlockPos): Block? = replaced[pos]?.block

    private fun onTick(client: Minecraft) {
        val level = client.level
        if (level !== scannedLevel) {
            // A new level means a new dungeon (or none) - the old positions no longer apply.
            scannedLevel = level
            replaced.clear()
            entranceTile = null
        }
        if (level == null || ++tickCounter % SCAN_INTERVAL_TICKS != 0) return
        restoreDisabled(level)
        if (!ModConfig.isGlassDoorsEnabled() || !FunnyMap.inCatacombs) return
        if (ModConfig.isGlassWitherDoorsEnabled()) glazeWitherDoors(level)
        glazeEntranceDoor(level)
    }

    /** Forgets anything the server has since overwritten, and puts back whatever's been
     * switched off - runs regardless of being in a dungeon, so toggling off always takes. */
    private fun restoreDisabled(level: ClientLevel) {
        val glassDoors = ModConfig.isGlassDoorsEnabled()
        val witherDoors = glassDoors && ModConfig.isGlassWitherDoorsEnabled()
        val entries = replaced.entries.iterator()
        while (entries.hasNext()) {
            val (pos, original) = entries.next()
            if (level.getBlockState(pos).block !== Blocks.GLASS) {
                entries.remove()
            } else if (!(if (original.block === Blocks.COAL_BLOCK) witherDoors else glassDoors)) {
                level.setBlock(pos, original, SET_BLOCK_FLAGS)
                entries.remove()
            }
        }
    }

    private fun glazeWitherDoors(level: ClientLevel) {
        val isDoorBlock = { _: BlockPos, state: BlockState -> state.block === Blocks.COAL_BLOCK }
        for (gap in FunnyMapLayout.gaps) {
            if (middleHasDoor(level, gap, isDoorBlock)) glaze(level, gap, isDoorBlock)
        }
    }

    private fun glazeEntranceDoor(level: ClientLevel) {
        val tile = entranceTile ?: findEntrance(level)?.also { entranceTile = it } ?: return
        val isDoorBlock = { pos: BlockPos, state: BlockState -> isEntranceDoorBlock(level, pos, state) }
        for (gap in FunnyMapLayout.gapsAround(tile)) {
            if (middleHasDoor(level, gap, isDoorBlock)) glaze(level, gap, isDoorBlock)
        }
    }

    /** Any solid block rather than one specific kind - seen in-game as infested chiseled stone
     * bricks - since nothing else can be standing in a door's opening. Barriers are skipped: the
     * server also uses them here, and they're already invisible. Wither doors have their own
     * setting, and blood doors stay as they are. */
    private fun isEntranceDoorBlock(level: ClientLevel, pos: BlockPos, state: BlockState): Boolean {
        val block = state.block
        if (block === Blocks.GLASS || block === Blocks.BARRIER) return false
        if (block === Blocks.COAL_BLOCK || block === Blocks.DYED_TERRACOTTA.red()) return false
        return state.isCollisionShapeFullBlock(level, pos)
    }

    private fun findEntrance(level: ClientLevel): GridPos? =
        FunnyMapLayout.tiles.firstOrNull { WorldRoomScanner.scanTileIfLoaded(level, it)?.type == "ENTRANCE" }

    /** Whether a door stands in [gap]. An opening also takes in the edges of the rooms either
     * side, which are plain wall where there's no door, so only the gap's own middle column
     * can tell - it's air unless a door fills it. */
    private fun middleHasDoor(level: ClientLevel, gap: MapGap, isDoorBlock: (BlockPos, BlockState) -> Boolean): Boolean {
        val (x, z) = FunnyMapLayout.gapColumn(gap)
        return FunnyMapLayout.DOOR_OPENING_Y.any { y ->
            val pos = BlockPos(x, y, z)
            isDoorBlock(pos, level.getBlockState(pos))
        }
    }

    /** Replaces the matching blocks in [gap]'s opening. */
    private fun glaze(level: ClientLevel, gap: MapGap, isDoorBlock: (BlockPos, BlockState) -> Boolean) {
        for ((x, y, z) in FunnyMapLayout.doorOpening(gap)) {
            val pos = BlockPos(x, y, z)
            val state = level.getBlockState(pos)
            if (!isDoorBlock(pos, state)) continue
            replaced[pos] = state
            level.setBlock(pos, GLASS, SET_BLOCK_FLAGS)
        }
    }
}
