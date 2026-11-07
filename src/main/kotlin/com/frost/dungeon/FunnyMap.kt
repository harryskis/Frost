package com.frost.dungeon

import com.frost.Frost
import com.frost.config.ModConfig
import com.frost.gui.FunnyMapMoveScreen
import com.frost.gui.FunnyMapRenderer
import com.frost.parsing.ActionBarParser
import com.frost.parsing.TabListParser
import com.frost.util.SkyblockItems
import com.frost.util.TextUtils
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.saveddata.maps.MapDecoration
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes

/**
 * A dungeon map built by reading the world's blocks instead of the in-game map item, so it can
 * show every room as soon as its chunk reaches the client - before the run starts and before
 * anyone has opened a door. Rooms come from [WorldRoomScanner]'s fingerprint scan; doors are
 * read from the gap columns between tiles (see [scanDoor]). Results are kept once found, since
 * a chunk unloading later doesn't change what was in it.
 *
 * Which rooms have actually been opened comes from the in-game map when there is one (see
 * [DungeonMapReader]), or from where the party has walked when there isn't - before the run
 * hands out the map, or on a server that doesn't give one.
 *
 * Only as complete as what the client has loaded: chunks are limited by render distance, so a
 * low setting leaves far rooms blank until the player walks closer.
 */
object FunnyMap {
    private val HUD_ID = Identifier.fromNamespaceAndPath(Frost.MOD_ID, "funny_map")

    // Chunks arrive in bursts and rooms never change, so twice a second is plenty.
    private const val SCAN_INTERVAL_TICKS = 10

    // The column range a room/gap can occupy - matches WorldRoomScanner.scanCore.
    private const val COLUMN_TOP_Y = 140
    private const val COLUMN_BOTTOM_Y = 12

    // Where a door's blocks sit. Wither doors are coal and blood doors are red terracotta.
    private val DOOR_Y_RANGE = 66..74

    /** What marks a mob as starred, in the floating name above it. */
    const val STAR = "✯"

    // Shown where the blood room has to be, until its chunk loads and the scan finds the real one.
    private val WORKED_OUT_BLOOD = RoomDatabase.RoomInfo("Blood", "BLOOD", "1x1")

    private val rooms = mutableMapOf<GridPos, RoomDatabase.RoomInfo>()

    // The scanned rooms plus what their shapes prove about tiles that haven't loaded. Rebuilt on
    // each scan rather than added to rooms, so a real scan of those tiles always has the last word.
    private var layoutRooms: Map<GridPos, RoomDatabase.RoomInfo> = emptyMap()
    private val doors = mutableMapOf<MapGap, DoorKind>()
    private val opened = mutableSetOf<GridPos>()
    private val tileCheckmarks = mutableMapOf<GridPos, MapCheckmark>()
    private val foundSecrets = mutableMapOf<RoomDatabase.RoomInfo, Int>()
    private var teamSecrets = 0
    private var secretsPercent = 0.0
    private var bloodRush: BloodRushPath? = null
    private var bloodDoorOpened = false
    private var princeKilled = false
    private val loadedGaps = mutableSetOf<MapGap>()
    private val joinedGaps = mutableSetOf<MapGap>()

    /** The fairy room's unopened ways out, for Fairy Door ESP. */
    var fairyExitDoors: List<MapGap> = emptyList()
        private set

    private var mapLayout: DungeonMapReader.PixelLayout? = null
    private var scannedLevel: ClientLevel? = null
    private var tickCounter = 0
    private var modeKeyHeld = false

    // The world (x, z) of each starred mob in the player's current room.
    private val starMobs = mutableListOf<Pair<Double, Double>>()

    /** Set from the sidebar scoreboard by Frost's own polling - the Catacombs line shows from
     * the moment the player loads in, well before the run itself starts. */
    var inCatacombs = false

    /** Real party members, by username. Hypixel's dungeon mobs are fake player entities that
     * even have player-list entries, so being a player entity proves nothing - but only real
     * members get a visible "[level] name" line in the tab list. */
    var partyNames: Set<String> = emptySet()
        private set

    // The living party in tab list order, and where the in-game map says each teammate is.
    private var aliveParty: List<String> = emptyList()
    private var teammateMarkers: Map<String, DungeonMapReader.Marker> = emptyMap()
    private val teammateSkins = HashMap<String, PlayerSkin>()

    // Each member's class by name, the local player's included, for the styles that color heads by it.
    private val partyClasses = HashMap<String, String>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> onTick(client) }
        HudElementRegistry.addLast(HUD_ID) { context, _ -> drawHud(context) }
    }

    /** Fed the visible tab list lines, in the order they're displayed, by Frost's own polling. */
    fun onTabList(lines: List<String>) {
        if (!inCatacombs) return
        partyNames = lines.mapNotNullTo(mutableSetOf()) { TabListParser.parseMemberName(it) }
        aliveParty = lines.filterNot(TabListParser::isMemberDead).mapNotNull { TabListParser.parseMemberName(it) }
        for (line in lines) {
            val name = TabListParser.parseMemberName(line) ?: continue
            TabListParser.parseMemberClass(line)?.let { partyClasses[name] = it }
        }
        lines.firstNotNullOfOrNull(TabListParser::parseSecretsFound)?.let { teamSecrets = it }
        lines.firstNotNullOfOrNull(TabListParser::parseSecretsPercent)?.let { secretsPercent = it }
        val connection = Minecraft.getInstance().connection ?: return
        for (name in partyNames) connection.getPlayerInfo(name)?.let { teammateSkins[name] = it.skin }
    }

    /** Fed the in-game map by Frost's own polling, whenever it has it. */
    fun onDungeonMap(colors: ByteArray, decorations: Iterable<MapDecoration>) {
        if (!ModConfig.isFunnyMapEnabled() || !inCatacombs) return
        val layout = mapLayout ?: DungeonMapReader.findLayout(colors)?.also { mapLayout = it } ?: return
        teammateMarkers = matchMarkers(decorations, layout)
        opened += DungeonMapReader.openedTiles(colors, layout)
        // A room never un-finishes, so a tile that has shown green keeps it.
        for ((tile, checkmark) in DungeonMapReader.checkmarks(colors, layout)) {
            if (tileCheckmarks[tile] != MapCheckmark.GREEN) tileCheckmarks[tile] = checkmark
        }
    }

    /**
     * Works out whose each map marker is. Markers carry no name, so this goes by order, as Odin
     * does: the local player's is the frame, and the rest follow the living teammates in tab
     * list order, which is why the dead have to be left out.
     */
    private fun matchMarkers(decorations: Iterable<MapDecoration>, layout: DungeonMapReader.PixelLayout): Map<String, DungeonMapReader.Marker> {
        val self = Minecraft.getInstance().player?.gameProfile?.name
        val markers = decorations.filter { it.type().value() != MapDecorationTypes.FRAME.value() }
            .map { DungeonMapReader.readMarker(it.x(), it.y(), it.rot(), layout) }
        return aliveParty.filter { it != self }.zip(markers).toMap()
    }

    /** Fed by Frost's own chat listener. Chat rather than the map, since it says so the moment it
     * happens, and the blood rush line has no more use from then on. */
    /** Fed by Frost's own chat listener when a prince dies - see [com.frost.parsing.PrinceKill]. */
    fun onPrinceKilled() {
        princeKilled = true
    }

    fun onBloodDoorOpened() {
        bloodDoorOpened = true
    }

    /** The skin to draw for [name], remembered in case they've since dropped off the player list. */
    fun skinOf(name: String): PlayerSkin = teammateSkins[name] ?: DefaultPlayerSkin.getDefaultSkin()

    /**
     * Fed every action bar message by Frost's own chat listener. The bar reports the local
     * player's own room, so it's credited to the room they're standing in - but only if the
     * total agrees, since the bar can lag a moment behind when crossing into a new room.
     */
    fun onActionBar(text: String) {
        if (!ModConfig.isFunnyMapEnabled() || !inCatacombs) return
        val (found, total) = ActionBarParser.parseRoomSecrets(text) ?: return
        val player = Minecraft.getInstance().player ?: return
        val room = rooms[GridPos(FunnyMapLayout.worldToTile(player.x), FunnyMapLayout.worldToTile(player.z))] ?: return
        if (room.secrets != total) return
        foundSecrets[room] = maxOf(foundSecrets[room] ?: 0, found)
    }

    /** Skipped while a screen is open, so typing or binding the key can't also fire it. */
    private fun checkModeKey(client: Minecraft) {
        val key = ModConfig.getFunnyMapModeKey()
        val down = key >= 0 && client.gui.screen() == null && InputConstants.isKeyDown(key)
        if (down && !modeKeyHeld) ModConfig.setFunnyModeEnabled(!ModConfig.isFunnyModeEnabled())
        modeKeyHeld = down
    }

    /** Tracked every tick rather than on the slower scan, since mobs move. The floating name
     * above a mob is its own armor stand, and the star is only ever in that name. A cleared room
     * shows none at all, since a dead mob's name can linger after the room's already done. */
    private fun updateStarMobs(level: ClientLevel, client: Minecraft) {
        starMobs.clear()
        val player = client.player
        if (player == null || !ModConfig.isStarMobsEnabled()) return
        val currentRoom = FunnyMapLayout.roomTiles(tileAt(player.x, player.z), rooms)
        if (currentRoom.any { tileCheckmarks[it]?.isCleared() == true }) return
        for (entity in level.entitiesForRendering()) {
            if (entity !is ArmorStand || tileAt(entity.x, entity.z) !in currentRoom) continue
            if (STAR in entity.name.string) starMobs += entity.x to entity.z
        }
    }

    private fun tileAt(x: Double, z: Double) = GridPos(FunnyMapLayout.worldToTile(x), FunnyMapLayout.worldToTile(z))

    private fun onTick(client: Minecraft) {
        checkModeKey(client)
        val level = client.level
        if (level !== scannedLevel) {
            // A new level means a new dungeon (or none) - the old layout no longer applies.
            scannedLevel = level
            rooms.clear()
            layoutRooms = emptyMap()
            doors.clear()
            opened.clear()
            tileCheckmarks.clear()
            foundSecrets.clear()
            teamSecrets = 0
            secretsPercent = 0.0
            bloodRush = null
            bloodDoorOpened = false
            princeKilled = false
            fairyExitDoors = emptyList()
            loadedGaps.clear()
            joinedGaps.clear()
            BloodRushAlert.reset()
            mapLayout = null
            partyNames = emptySet()
            aliveParty = emptyList()
            teammateMarkers = emptyMap()
            partyClasses.clear()
            inCatacombs = false
            starMobs.clear()
        }
        if (level == null || !inCatacombs) return
        // Fairy Door ESP and Blood Rush Alert read the same layout, so the scan has to run for
        // either of those alone too.
        val mapEnabled = ModConfig.isFunnyMapEnabled()
        if (mapEnabled) updateStarMobs(level, client)
        if (!mapEnabled && !ModConfig.isFairyDoorEspEnabled() && !isBloodRushAlertActive()) return
        if (++tickCounter % SCAN_INTERVAL_TICKS != 0) return
        scan(level)
        // Once the real map is available it's the authority - a dead teammate's ghost can fly
        // through rooms nobody has opened, which shouldn't reveal them.
        if (mapLayout == null) markRoomsWithPlayersOpened(level, client)
    }

    private fun markRoomsWithPlayersOpened(level: ClientLevel, client: Minecraft) {
        for (player in level.players()) {
            if (player !== client.player && player.gameProfile.name !in partyNames) continue
            val tile = GridPos(FunnyMapLayout.worldToTile(player.x), FunnyMapLayout.worldToTile(player.z))
            if (tile in FunnyMapLayout.tiles) opened += tile
        }
    }

    private fun isBloodRushAlertActive() = ModConfig.isFunnyModeEnabled() && ModConfig.isBloodRushAlertEnabled()

    private fun scan(level: ClientLevel) {
        for (tile in FunnyMapLayout.tiles) {
            if (tile in rooms) continue
            WorldRoomScanner.scanTileIfLoaded(level, tile)?.let { rooms[tile] = it }
        }
        for (gap in FunnyMapLayout.gaps) {
            val (x, z) = FunnyMapLayout.gapColumn(gap)
            if (WorldRoomScanner.isColumnLoaded(level, x, z)) loadedGaps += gap
            // Part of a room rather than a door, even where a room's middle is air or happens to
            // have door-colored blocks in it.
            if (FunnyMapLayout.isJoinedGap(rooms[gap.tile], rooms[gap.other], gap in joinedGaps) { spansRoom(level, gap) }) {
                joinedGaps += gap
                doors[gap] = DoorKind.NORMAL
                continue
            }
            joinedGaps -= gap
            // A special door is final. A normal one might still be upgraded if it was first
            // read before its blocks arrived, so it's worth re-reading.
            if (doors[gap].let { it != null && it != DoorKind.NORMAL }) continue
            scanDoor(level, gap)?.let { doors[gap] = it }
        }
        val unknownGaps = FunnyMapLayout.gaps.toSet() - loadedGaps
        layoutRooms = FunnyMapLayout.withInferredTiles(rooms, joinedGaps, unknownGaps)
        bloodRush = FunnyMapLayout.bloodRushPath(layoutRooms, doors, unknownGaps)
        fairyExitDoors = FunnyMapLayout.fairyExitDoors(layoutRooms, doors).filter { isWitherDoorClosed(level, it) }
        if (isBloodRushAlertActive() && !bloodDoorOpened) {
            BloodRushAlert.update(layoutRooms, doors, unknownGaps)
        }
    }

    /** A door keeps its kind once read, so whether it's since been opened needs a fresh look. */
    private fun isWitherDoorClosed(level: ClientLevel, gap: MapGap): Boolean {
        val (x, z) = FunnyMapLayout.gapColumn(gap)
        return FunnyMapLayout.DOOR_OPENING_Y.any { y ->
            val pos = BlockPos(x, y, z)
            (GlassDoors.originalBlock(pos) ?: level.getBlockState(pos).block) === Blocks.COAL_BLOCK
        }
    }

    private fun spansRoom(level: ClientLevel, gap: MapGap): Boolean =
        FunnyMapLayout.gapSideColumns(gap).any { (x, z) ->
            level.chunkSource.getChunk(x shr 4, z shr 4, ChunkStatus.FULL, false)?.let { columnHasBlocks(it, x, z) } == true
        }

    /**
     * Reads one gap column, or returns null if it's empty or not loaded. Unconnected rooms have
     * pure air between them, so any block here means the two sides are joined - by a door, or
     * as part of one multi-tile room, which [FunnyMapLayout.isMerged] tells apart once both
     * sides are identified. Opening a door removes its blocks but leaves the frame, so an
     * opened door still reads as a connection, just no longer a special one.
     */
    private fun scanDoor(level: ClientLevel, gap: MapGap): DoorKind? {
        val (x, z) = FunnyMapLayout.gapColumn(gap)
        val chunk = level.chunkSource.getChunk(x shr 4, z shr 4, ChunkStatus.FULL, false) ?: return null
        if (!columnHasBlocks(chunk, x, z)) return null
        for (y in DOOR_Y_RANGE) {
            val pos = BlockPos(x, y, z)
            // Glass Doors may have swapped this block on the client, which hides what door it is.
            val block = GlassDoors.originalBlock(pos) ?: chunk.getBlockState(pos).block
            if (block === Blocks.COAL_BLOCK) return DoorKind.WITHER
            if (block === Blocks.DYED_TERRACOTTA.red()) return DoorKind.BLOOD
        }
        return DoorKind.NORMAL
    }

    private fun columnHasBlocks(chunk: LevelChunk, x: Int, z: Int): Boolean =
        (COLUMN_TOP_Y downTo COLUMN_BOTTOM_Y).any { y -> !chunk.getBlockState(BlockPos(x, y, z)).isAir }

    private fun drawHud(context: GuiGraphicsExtractor) {
        if (!ModConfig.isFunnyMapEnabled() || !inCatacombs) return
        val client = Minecraft.getInstance()
        // The move screen draws its own copy at the position being dragged.
        if (client.gui.screen() is FunnyMapMoveScreen) return
        val level = client.level ?: return
        val player = client.player ?: return
        // Boss rooms are off the grid, and a room map is no use there.
        if (FunnyMapLayout.worldToTile(player.x) !in 0 until FunnyMapLayout.GRID_SIZE) return
        if (FunnyMapLayout.worldToTile(player.z) !in 0 until FunnyMapLayout.GRID_SIZE) return

        val scale = ModConfig.getFunnyMapScale()
        val (x, y) = FunnyMapRenderer.clampToScreen(ModConfig.getFunnyMapX(), ModConfig.getFunnyMapY(), scale, client)
        val current = snapshot()
        FunnyMapRenderer.draw(context, client.font, x, y, scale, current)
        if (ModConfig.isFunnyModeEnabled()) FunnyMapRenderer.drawStarMobs(context, x, y, scale, starMobs)
        val party = FunnyMapRenderer.Party(partyNames, teammateMarkers, partyClasses, ModConfig.getMapStyle().namesWhileLeaping && isLeaping(client))
        FunnyMapRenderer.drawPlayers(context, client.font, x, y, scale, level, player, party)
    }

    /** Holding a spirit leap, or in its menu - when Odin and dtMap write names under the heads. */
    private fun isLeaping(client: Minecraft): Boolean {
        val title = client.gui.screen()?.title?.string?.let(TextUtils::stripFormatting)
        if (title == "Spirit Leap" || title == "Teleport to Player") return true
        return client.player?.mainHandItem?.let(SkyblockItems::isSpiritLeap) == true
    }

    private fun snapshot(): FunnyMapSnapshot {
        val shownRooms = bloodRush?.bloodTile?.let { layoutRooms + (it to WORKED_OUT_BLOOD) } ?: layoutRooms
        val full = FunnyMapSnapshot(
            shownRooms,
            doors,
            FunnyMapLayout.expandToWholeRooms(opened, shownRooms),
            FunnyMapLayout.roomCheckmarks(tileCheckmarks, shownRooms),
            foundSecrets,
            bloodRush.takeUnless { bloodDoorOpened },
            teamSecrets,
            FunnyMapLayout.totalSecrets(teamSecrets, secretsPercent),
            princeKilled,
        )
        return if (ModConfig.isFunnyModeEnabled()) full else FunnyMapLayout.legitView(full)
    }

    /** The current layout for the move screen, or null if nothing's been scanned yet. */
    fun currentSnapshot(): FunnyMapSnapshot? = if (layoutRooms.isEmpty()) null else snapshot()
}
