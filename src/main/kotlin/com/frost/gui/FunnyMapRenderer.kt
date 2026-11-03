package com.frost.gui

import com.frost.config.ModConfig
import com.frost.dungeon.BloodRushPath
import com.frost.dungeon.DoorKind
import com.frost.dungeon.DungeonMapReader
import com.frost.dungeon.FunnyMap
import com.frost.dungeon.FunnyMapLayout
import com.frost.dungeon.FunnyMapSnapshot
import com.frost.dungeon.GridPos
import com.frost.dungeon.RoomDatabase
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.PlayerSkin
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Draws [com.frost.dungeon.FunnyMap]'s layout, shared by the HUD and the move screen. Everything
 * is laid out at its base size and scaled as a whole, so text and heads grow with the map.
 */
object FunnyMapRenderer {
    private val ROOM_NORMAL = 0xFF6B4423.toInt()
    private val ROOM_RARE = 0xFF8C6A2F.toInt()
    private val ROOM_CHAMPION = 0xFFC9A227.toInt()
    private val ROOM_PUZZLE = 0xFF7A3E9D.toInt()
    private val ROOM_TRAP = 0xFFFFA64D.toInt()
    private val ROOM_ENTRANCE = 0xFF2E8B3D.toInt()
    private val ROOM_FAIRY = 0xFFE27BB8.toInt()
    private val ROOM_BLOOD = 0xFFB3261E.toInt()

    // A tile with a door leading into it, but no room identified yet.
    private val ROOM_UNKNOWN = 0xFF3A3F47.toInt()

    // How bright an unopened room is compared to its real color.
    private const val UNOPENED_BRIGHTNESS = 0.45f

    private val DOOR_NORMAL = 0xFF9C7A4F.toInt()
    private val DOOR_WITHER = 0xFFB8B8B8.toInt()
    private val DOOR_BLOOD = 0xFFE5392F.toInt()

    private val TEXT_PENDING = 0xFFD3D3D3.toInt()
    private val TEXT_DONE = 0xFF90EE90.toInt()
    private val HEAD_OUTLINE = 0xFF000000.toInt()
    private val SELF_OUTLINE = 0xFF5AC8FA.toInt()
    private val BLOOD_RUSH_LINE = 0xFF39FF14.toInt()
    private val BLOOD_RUSH_UNCONFIRMED = 0xFFFFE14D.toInt()
    private val STAR_MOB = 0xFFFFC21A.toInt()

    private const val DOOR_WIDTH = 8
    private const val STAR_MOB_SIZE = 3
    private const val PERCENT = 100f
    private const val SELF_HEAD_SIZE = 10
    private const val TEAMMATE_HEAD_SIZE = 8
    private const val SECRETS_LINE_HEIGHT = 11
    private const val SECRETS_LINE_INSET = 2

    /** The map's on-screen width and height at [scale]. */
    fun scaledSize(scale: Float): Int = ceil(FunnyMapLayout.MAP_SIZE * scale).toInt()

    /** The on-screen height at [scale], which includes the secrets line under the map if it's on. */
    fun scaledHeight(scale: Float): Int {
        val line = if (ModConfig.isTotalSecretsEnabled()) SECRETS_LINE_HEIGHT else 0
        return ceil((FunnyMapLayout.MAP_SIZE + line) * scale).toInt()
    }

    /** Keeps the map fully on screen, including after the window or GUI scale shrinks. */
    fun clampToScreen(x: Int, y: Int, scale: Float, client: Minecraft): Pair<Int, Int> {
        val maxX = (client.window.guiScaledWidth - scaledSize(scale)).coerceAtLeast(0)
        val maxY = (client.window.guiScaledHeight - scaledHeight(scale)).coerceAtLeast(0)
        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
    }

    private inline fun inMapSpace(context: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, draw: () -> Unit) {
        context.pose().pushMatrix()
        context.pose().translate(x.toFloat(), y.toFloat())
        context.pose().scale(scale, scale)
        draw()
        context.pose().popMatrix()
    }

    fun draw(context: GuiGraphicsExtractor, font: Font, x: Int, y: Int, scale: Float, map: FunnyMapSnapshot) {
        inMapSpace(context, x, y, scale) {
            context.fill(0, 0, FunnyMapLayout.MAP_SIZE, FunnyMapLayout.MAP_SIZE, ModConfig.getMapBackgroundColor())
            drawTiles(context, map)
            drawGaps(context, map)
            // Under the labels, so a name crossing the line stays readable.
            if (ModConfig.isBloodRushPathEnabled()) map.bloodRush?.let { drawBloodRushPath(context, it) }
            drawLabels(context, font, map)
            if (ModConfig.isTotalSecretsEnabled()) drawSecretsLine(context, font, map)
        }
    }

    /** The team's secrets out of the dungeon's total, just below the map on its own background. */
    private fun drawSecretsLine(context: GuiGraphicsExtractor, font: Font, map: FunnyMapSnapshot) {
        val top = FunnyMapLayout.MAP_SIZE
        context.fill(0, top, FunnyMapLayout.MAP_SIZE, top + SECRETS_LINE_HEIGHT, ModConfig.getMapBackgroundColor())
        val total = map.totalSecrets
        val allFound = total != null && total.exact && map.teamSecrets >= total.count
        val text = "Secrets: ${map.teamSecrets}/${total?.label ?: "?"}"
        context.centeredText(font, text, FunnyMapLayout.MAP_SIZE / 2, top + SECRETS_LINE_INSET, if (allFound) TEXT_DONE else TEXT_PENDING)
    }

    /** A thin line from the middle of each tile to the next, ending at its door if it has one,
     * in a different color while the route doesn't reach blood yet. Grid neighbors are always
     * straight across or down, so each segment is a simple 1px fill. */
    private fun drawBloodRushPath(context: GuiGraphicsExtractor, path: BloodRushPath) {
        fun center(tileIndex: Int) = FunnyMapLayout.cellStart(tileIndex) + FunnyMapLayout.CELL_SIZE / 2
        fun gapMiddle(tileIndex: Int) = FunnyMapLayout.cellStart(tileIndex) + FunnyMapLayout.CELL_SIZE + FunnyMapLayout.GAP_SIZE / 2

        val door = path.door
        val doorPoint = door?.let { if (it.horizontal) gapMiddle(it.tile.x) to center(it.tile.z) else center(it.tile.x) to gapMiddle(it.tile.z) }
        val points = path.tiles.map { center(it.x) to center(it.z) } + listOfNotNull(doorPoint)
        val color = if (path.reachesBlood) BLOOD_RUSH_LINE else BLOOD_RUSH_UNCONFIRMED
        for ((from, to) in points.zipWithNext()) {
            val left = minOf(from.first, to.first)
            val top = minOf(from.second, to.second)
            val right = maxOf(from.first, to.first) + 1
            val bottom = maxOf(from.second, to.second) + 1
            context.fill(left, top, right, bottom, color)
        }
    }

    private fun drawTiles(context: GuiGraphicsExtractor, map: FunnyMapSnapshot) {
        // The blood rush line can end at a way out that's been worked out rather than seen.
        val rushExit = map.bloodRush?.door?.takeIf { ModConfig.isBloodRushPathEnabled() }
        val hintedByDoor = (FunnyMapLayout.gaps.filter { FunnyMapLayout.doorIn(it, map.rooms, map.doors) != null } + listOfNotNull(rushExit))
            .flatMap { listOf(it.tile, it.other) }.toSet()
        for (tile in FunnyMapLayout.tiles) {
            if (tile !in map.rooms && tile !in hintedByDoor) continue
            val left = FunnyMapLayout.cellStart(tile.x)
            val top = FunnyMapLayout.cellStart(tile.z)
            context.fill(left, top, left + FunnyMapLayout.CELL_SIZE, top + FunnyMapLayout.CELL_SIZE, tileColor(tile, map))
        }
        // The hole in the middle of a 2x2 room, which no gap covers.
        for (tile in FunnyMapLayout.tiles) {
            val room = map.rooms[tile] ?: continue
            val isTwoByTwoCorner = listOf(GridPos(tile.x + 1, tile.z), GridPos(tile.x, tile.z + 1), GridPos(tile.x + 1, tile.z + 1)).all { map.rooms[it] == room }
            if (!isTwoByTwoCorner) continue
            val left = FunnyMapLayout.cellStart(tile.x) + FunnyMapLayout.CELL_SIZE
            val top = FunnyMapLayout.cellStart(tile.z) + FunnyMapLayout.CELL_SIZE
            context.fill(left, top, left + FunnyMapLayout.GAP_SIZE, top + FunnyMapLayout.GAP_SIZE, tileColor(tile, map))
        }
    }

    private fun drawGaps(context: GuiGraphicsExtractor, map: FunnyMapSnapshot) {
        for (gap in FunnyMapLayout.gaps) {
            val door = FunnyMapLayout.doorIn(gap, map.rooms, map.doors)
            val merged = FunnyMapLayout.isMerged(gap, map.rooms)
            if (door == null && !merged) continue

            // A merged gap spans the room's full width so it reads as one space; a door is a
            // short connector in the middle.
            val span = if (merged) FunnyMapLayout.CELL_SIZE else DOOR_WIDTH
            val inset = (FunnyMapLayout.CELL_SIZE - span) / 2
            val color = if (merged) {
                tileColor(gap.tile, map)
            } else {
                doorColor(door!!, opened = gap.tile in map.opened || gap.other in map.opened)
            }
            if (gap.horizontal) {
                val left = FunnyMapLayout.cellStart(gap.tile.x) + FunnyMapLayout.CELL_SIZE
                val top = FunnyMapLayout.cellStart(gap.tile.z) + inset
                context.fill(left, top, left + FunnyMapLayout.GAP_SIZE, top + span, color)
            } else {
                val left = FunnyMapLayout.cellStart(gap.tile.x) + inset
                val top = FunnyMapLayout.cellStart(gap.tile.z) + FunnyMapLayout.CELL_SIZE
                context.fill(left, top, left + span, top + FunnyMapLayout.GAP_SIZE, color)
            }
        }
    }

    /** Each room's name once, word-wrapped and sized to fit the room, with its found/total
     * secrets underneath when it has any. Each turns green independently: the name when the
     * room is cleared, the count when its secrets are all done. */
    private fun drawLabels(context: GuiGraphicsExtractor, font: Font, map: FunnyMapSnapshot) {
        for ((room, tiles) in FunnyMapLayout.groupRooms(map.rooms)) {
            val (tileX, tileZ) = FunnyMapLayout.labelCenter(tiles)
            val nameColor = if (map.isCleared(room)) TEXT_DONE else TEXT_PENDING
            val found = map.secretsFound(room)
            val secretsColor = if (found >= room.secrets) TEXT_DONE else TEXT_PENDING
            val nameLines = if (ModConfig.isRoomNameShown(room.type)) room.name.split(' ').map { it to nameColor } else emptyList()
            val lines = nameLines + listOfNotNull(room.secrets.takeIf { it > 0 }?.let { "$found/$it" to secretsColor })
            if (lines.isEmpty()) continue
            val labelScale = FunnyMapLayout.labelScale(
                lines.maxOf { font.width(it.first) },
                FunnyMapLayout.labelWidth(tiles),
                ModConfig.isLimitRoomNamesEnabled(),
                ModConfig.getFunnyMapTextSize() / PERCENT,
            )
            val lineHeight = font.lineHeight * labelScale
            val centerX = tileX * FunnyMapLayout.TILE_PITCH
            var lineY = tileZ * FunnyMapLayout.TILE_PITCH - lines.size * lineHeight / 2
            for ((text, color) in lines) {
                context.pose().pushMatrix()
                context.pose().translate(centerX.toFloat(), lineY.toFloat())
                context.pose().scale(labelScale, labelScale)
                context.centeredText(font, text, 0, 0, color)
                context.pose().popMatrix()
                lineY += lineHeight
            }
        }
    }

    /** A small gold square at each of [mobs]' world (x, z), outlined so it shows on any room color. */
    fun drawStarMobs(context: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, mobs: List<Pair<Double, Double>>) {
        inMapSpace(context, x, y, scale) {
            for ((worldX, worldZ) in mobs) {
                val left = FunnyMapLayout.worldToMap(worldX).toInt() - STAR_MOB_SIZE / 2
                val top = FunnyMapLayout.worldToMap(worldZ).toInt() - STAR_MOB_SIZE / 2
                context.fill(left - 1, top - 1, left + STAR_MOB_SIZE + 1, top + STAR_MOB_SIZE + 1, HEAD_OUTLINE)
                context.fill(left, top, left + STAR_MOB_SIZE, top + STAR_MOB_SIZE, STAR_MOB)
            }
        }
    }

    /** Each party member's skin head, turned to face the way they're looking, with the local
     * player's larger and outlined in the accent color so it's easy to find. A teammate too far
     * away for the client to have loaded is placed by their [markers] entry from the in-game map
     * instead, which is coarser but always there. */
    fun drawPlayers(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        scale: Float,
        level: ClientLevel,
        self: LocalPlayer,
        partyNames: Set<String>,
        markers: Map<String, DungeonMapReader.Marker>,
    ) {
        val headScale = ModConfig.getFunnyMapHeadSize() / PERCENT
        val teammateSize = (TEAMMATE_HEAD_SIZE * headScale).roundToInt()
        val selfSize = (SELF_HEAD_SIZE * headScale).roundToInt()
        inMapSpace(context, x, y, scale) {
            val loaded = level.players().filter { it !== self && it.gameProfile.name in partyNames }
            for (entity in loaded) {
                drawHead(context, entity.skin, entity.x, entity.z, entity.yHeadRot, teammateSize, HEAD_OUTLINE)
            }
            val loadedNames = loaded.map { it.gameProfile.name }
            for ((name, marker) in markers) {
                if (name in loadedNames) continue
                drawHead(context, FunnyMap.skinOf(name), marker.worldX, marker.worldZ, marker.yaw, teammateSize, HEAD_OUTLINE)
            }
            // The local player's own yRot rather than yHeadRot: it updates with the mouse every
            // frame, so their head turns smoothly instead of stepping at the tick rate.
            drawHead(context, self.skin, self.x, self.z, self.yRot, selfSize, SELF_OUTLINE)
        }
    }

    private fun drawHead(context: GuiGraphicsExtractor, skin: PlayerSkin, worldX: Double, worldZ: Double, yaw: Float, size: Int, outline: Int) {
        val (centerX, centerZ) = mapPosition(worldX, worldZ) ?: return
        val half = size / 2
        context.pose().pushMatrix()
        context.pose().translate(centerX.toFloat(), centerZ.toFloat())
        context.pose().rotate(Math.toRadians(FunnyMapLayout.headRotationDegrees(yaw).toDouble()).toFloat())
        context.fill(-half - 1, -half - 1, size - half + 1, size - half + 1, outline)
        PlayerFaceExtractor.extractRenderState(context, skin, -half, -half, size)
        context.pose().popMatrix()
    }

    /** Where a world position sits on the map, or null if it's off the grid. */
    private fun mapPosition(worldX: Double, worldZ: Double): Pair<Int, Int>? {
        val mapX = FunnyMapLayout.worldToMap(worldX)
        val mapZ = FunnyMapLayout.worldToMap(worldZ)
        val onMap = 0.0..FunnyMapLayout.MAP_SIZE.toDouble()
        if (mapX !in onMap || mapZ !in onMap) return null
        return mapX.toInt() to mapZ.toInt()
    }

    private fun tileColor(tile: GridPos, map: FunnyMapSnapshot): Int {
        val room = map.rooms[tile] ?: return ROOM_UNKNOWN
        if (isFilled(room, map)) return ModConfig.getFillCompletedColor()
        val color = roomColor(room.type)
        return if (tile in map.opened) color else darken(color)
    }

    private fun isFilled(room: RoomDatabase.RoomInfo, map: FunnyMapSnapshot): Boolean =
        ModConfig.isFillCompletedRoomsEnabled() &&
            FunnyMapLayout.fillsWhenCompleted(room.type, ModConfig.getFilledRoomTypes()) &&
            map.isCompleted(room)

    private fun roomColor(type: String): Int = when (type) {
        "RARE" -> ROOM_RARE
        "CHAMPION" -> ROOM_CHAMPION
        "PUZZLE" -> ROOM_PUZZLE
        "TRAP" -> ROOM_TRAP
        "ENTRANCE" -> ROOM_ENTRANCE
        "FAIRY" -> ROOM_FAIRY
        "BLOOD" -> ROOM_BLOOD
        else -> ROOM_NORMAL
    }

    /** Wither and blood doors keep their color even unopened - where they are matters most
     * before anyone reaches them. */
    private fun doorColor(kind: DoorKind, opened: Boolean): Int = when (kind) {
        DoorKind.NORMAL -> if (opened) DOOR_NORMAL else darken(DOOR_NORMAL)
        DoorKind.WITHER -> DOOR_WITHER
        DoorKind.BLOOD -> DOOR_BLOOD
    }

    private fun darken(argb: Int): Int {
        val alpha = argb and 0xFF000000.toInt()
        val red = (((argb shr 16) and 0xFF) * UNOPENED_BRIGHTNESS).toInt()
        val green = (((argb shr 8) and 0xFF) * UNOPENED_BRIGHTNESS).toInt()
        val blue = ((argb and 0xFF) * UNOPENED_BRIGHTNESS).toInt()
        return alpha or (red shl 16) or (green shl 8) or blue
    }
}
