package com.frost.gui.map

import com.frost.dungeon.BloodRushPath
import com.frost.dungeon.DoorKind
import com.frost.dungeon.FunnyMapLayout
import com.frost.dungeon.FunnyMapSnapshot
import com.frost.dungeon.GridPos
import com.frost.dungeon.MapCheckmark
import com.frost.dungeon.MapGap
import com.frost.dungeon.MapStyle
import com.frost.dungeon.RoomDatabase

/** The Dungeon Map settings that change how the map is drawn, read from the config in game. */
data class MapPaintOptions(
    val bloodRushPath: Boolean = false,
    val totalSecrets: Boolean = false,
    val limitRoomNames: Boolean = true,
    val textSize: Float = 1f,
    val hiddenNameTypes: Set<String> = emptySet(),
    val fillCompletedRooms: Boolean = true,
    val filledRoomTypes: Set<String> = setOf("BLOOD"),
    val fillColor: Int = 0xFF2E96DC.toInt(),
    val backgroundColor: Int = 0xB0080B10.toInt(),
    val naRoomText: NaRoomText = NaRoomText.NAME_AND_SECRETS,
    val showPrinces: Boolean = false,
    val hidePrincesOnKill: Boolean = true,
    val princeIconCenter: Boolean = false,
    val princeIconSize: Float = 1f,
) {
    fun showsName(roomType: String): Boolean = roomType !in hiddenNameTypes
}

/** What the NA style writes on rooms - NoammAddons' "Checkmark Style" choices. */
enum class NaRoomText(val label: String) {
    CHECKMARKS("Checkmarks"),
    SECRETS("Secrets"),
    ROOM_NAME("Room Name"),
    NAME_AND_SECRETS("Room Name + Secrets"),
    SECRETS_AND_PUZZLE_NAME("Secrets + Puzzle Name"),
    ;

    companion object {
        fun fromName(name: String?): NaRoomText = entries.firstOrNull { it.name == name } ?: NAME_AND_SECRETS
    }
}

/**
 * Draws a [FunnyMapSnapshot] in any [MapStyle]. Legit and Funny need nothing different here:
 * Legit hands over a snapshot already cut down to what a player without the world scan would know
 * (see [FunnyMapLayout.legitView]), so a room that's known but not opened only ever turns up in
 * Funny, and a tile only hinted at by a door mostly in Legit. Each style draws both its own way.
 *
 * Kept free of Minecraft types, so it can be drawn onto an image in the tests.
 */
object MapPainter {
    const val SECRETS_LINE_HEIGHT = 11

    /** The map's height in [style]'s own pixels, including the secrets line under it if it's on. */
    fun height(style: MapStyle, options: MapPaintOptions): Int =
        style.geometry.size + if (options.totalSecrets) SECRETS_LINE_HEIGHT else 0

    fun paint(canvas: MapCanvas, style: MapStyle, map: FunnyMapSnapshot, options: MapPaintOptions) {
        val painter = when (style) {
            MapStyle.FROST -> FrostPainter
            MapStyle.NA -> NaPainter
            MapStyle.ODIN -> OdinPainter
            MapStyle.LCM -> LcmPainter
        }
        painter.paint(canvas, map, options)
    }
}

/** [argb] with its red, green and blue each multiplied by [factor], keeping its alpha. Odin's,
 * NA's and dtMap's ways of darkening all come down to this. */
internal fun scaleRgb(argb: Int, factor: Float): Int {
    val alpha = argb and 0xFF000000.toInt()
    val red = (((argb shr 16) and 0xFF) * factor).toInt()
    val green = (((argb shr 8) and 0xFF) * factor).toInt()
    val blue = ((argb and 0xFF) * factor).toInt()
    return alpha or (red shl 16) or (green shl 8) or blue
}

private fun rgb(red: Int, green: Int, blue: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

/** A door, and what's on either side of it. A side with no room is a tile only this door hints at. */
internal class DoorSides(
    val kind: DoorKind,
    val first: RoomDatabase.RoomInfo?,
    val second: RoomDatabase.RoomInfo?,
    val firstOpened: Boolean,
    val secondOpened: Boolean,
) {
    val anyOpened: Boolean get() = firstOpened || secondOpened

    /** Both rooms opened, which means the door between them has been too. */
    val bothOpened: Boolean get() = firstOpened && secondOpened
    val hasUnknownSide: Boolean get() = first == null || second == null
    val sideTypes: List<String> get() = listOfNotNull(first?.type, second?.type)
}

/** What every style draws the same way - where things go - leaving each style its colors and
 * how it labels rooms. */
internal abstract class StylePainter(val style: MapStyle) {
    protected val geometry = style.geometry

    /** How wide a door is across the gap it sits in. */
    protected abstract val doorWidth: Int

    protected abstract fun drawBackground(canvas: MapCanvas, options: MapPaintOptions)

    protected abstract fun backgroundColor(options: MapPaintOptions): Int

    /** A known room's color, given whether it's been [opened]. */
    protected abstract fun roomColor(room: RoomDatabase.RoomInfo, opened: Boolean, map: FunnyMapSnapshot, options: MapPaintOptions): Int

    /** The color of a tile nothing is known about but that a door leads into, which is the
     * blood room if [behindBloodDoor]. */
    protected abstract fun hintColor(behindBloodDoor: Boolean): Int

    protected abstract fun doorColor(door: DoorSides): Int

    /** Room names, secret counts and checkmarks. [hints] are the tiles only a door hints at. */
    protected abstract fun drawLabels(canvas: MapCanvas, map: FunnyMapSnapshot, options: MapPaintOptions, hints: Set<GridPos>)

    fun paint(canvas: MapCanvas, map: FunnyMapSnapshot, options: MapPaintOptions) {
        drawBackground(canvas, options)
        val hints = hintedTiles(map, options)
        drawRooms(canvas, map, options)
        for ((tile, behindBloodDoor) in hints) fillCell(canvas, tile, hintColor(behindBloodDoor))
        drawDoors(canvas, map)
        // Under the labels, so a name crossing the line stays readable.
        if (options.bloodRushPath) map.bloodRush?.let { drawBloodRushPath(canvas, it) }
        // A centered crown sits behind the room's name, so it goes down first; one in the corner
        // goes on top of everything.
        val crowns = options.showPrinces && !(options.hidePrincesOnKill && map.princeKilled)
        if (crowns && options.princeIconCenter) drawCrowns(canvas, map, options)
        drawLabels(canvas, map, options, hints.keys)
        if (crowns && !options.princeIconCenter) drawCrowns(canvas, map, options)
        if (options.totalSecrets) drawSecretsLine(canvas, map, options)
    }

    /** A crown on every room on the map a prince can be in: in its top-left corner, or centered
     * behind its name. Legit only has the rooms that have been opened, so their crowns come as
     * they open. */
    private fun drawCrowns(canvas: MapCanvas, map: FunnyMapSnapshot, options: MapPaintOptions) {
        val scale = (if (style == MapStyle.FROST) 1f else SMALL_CROWN) * options.princeIconSize
        val width = PrinceCrown.WIDTH * scale
        val height = PrinceCrown.HEIGHT * scale
        for ((room, tiles) in FunnyMapLayout.groupRooms(map.rooms)) {
            if (!RoomDatabase.hasPrince(room)) continue
            if (options.princeIconCenter) {
                val (centerX, centerY) = labelCenter(tiles)
                canvas.crown(centerX - width / 2, centerY - height / 2, scale)
            } else {
                // The room's top row, then its leftmost tile there - an L's corner can be empty.
                val topRow = tiles.minOf { it.z }
                val corner = tiles.filter { it.z == topRow }.minBy { it.x }
                canvas.crown(geometry.cellStart(corner.x) - CROWN_OVERHANG, geometry.cellStart(corner.z) - CROWN_OVERHANG, scale)
            }
        }
    }

    /** Unknown tiles a door leads into, and whether it's the blood door. The blood rush line can
     * also end at a way out that's been worked out rather than seen. */
    private fun hintedTiles(map: FunnyMapSnapshot, options: MapPaintOptions): Map<GridPos, Boolean> {
        val rushExit = map.bloodRush?.door?.takeIf { options.bloodRushPath }
        val result = LinkedHashMap<GridPos, Boolean>()
        for (gap in FunnyMapLayout.gaps) {
            val door = FunnyMapLayout.doorIn(gap, map.rooms, map.doors)
            if (door == null && gap != rushExit) continue
            for (tile in listOf(gap.tile, gap.other)) {
                if (tile in map.rooms) continue
                result[tile] = result[tile] == true || door == DoorKind.BLOOD
            }
        }
        return result
    }

    private fun tileColor(tile: GridPos, map: FunnyMapSnapshot, options: MapPaintOptions): Int =
        roomColor(map.rooms.getValue(tile), tile in map.opened, map, options)

    /** Each tile, the gaps joining a room's tiles, and the hole in the middle of a 2x2 room,
     * which no gap covers - together they fill the room's whole shape. */
    private fun drawRooms(canvas: MapCanvas, map: FunnyMapSnapshot, options: MapPaintOptions) {
        for (tile in FunnyMapLayout.tiles) {
            if (tile in map.rooms) fillCell(canvas, tile, tileColor(tile, map, options))
        }
        for (gap in FunnyMapLayout.gaps) {
            if (FunnyMapLayout.isMerged(gap, map.rooms)) fillGap(canvas, gap, geometry.cellSize, tileColor(gap.tile, map, options))
        }
        for (tile in FunnyMapLayout.tiles) {
            val room = map.rooms[tile] ?: continue
            val isTwoByTwoCorner = listOf(GridPos(tile.x + 1, tile.z), GridPos(tile.x, tile.z + 1), GridPos(tile.x + 1, tile.z + 1)).all { map.rooms[it] == room }
            if (!isTwoByTwoCorner) continue
            val left = geometry.cellStart(tile.x) + geometry.cellSize
            val top = geometry.cellStart(tile.z) + geometry.cellSize
            canvas.fill(left, top, left + geometry.gapSize, top + geometry.gapSize, tileColor(tile, map, options))
        }
    }

    private fun drawDoors(canvas: MapCanvas, map: FunnyMapSnapshot) {
        for (gap in FunnyMapLayout.gaps) {
            val kind = FunnyMapLayout.doorIn(gap, map.rooms, map.doors) ?: continue
            val sides = DoorSides(kind, map.rooms[gap.tile], map.rooms[gap.other], gap.tile in map.opened, gap.other in map.opened)
            fillGap(canvas, gap, doorWidth, doorColor(sides))
        }
    }

    protected fun fillCell(canvas: MapCanvas, tile: GridPos, color: Int) {
        val left = geometry.cellStart(tile.x)
        val top = geometry.cellStart(tile.z)
        canvas.fill(left, top, left + geometry.cellSize, top + geometry.cellSize, color)
    }

    /** Fills [span] pixels across the middle of [gap]: a whole room's width for a gap inside a
     * room, or a door's. */
    private fun fillGap(canvas: MapCanvas, gap: MapGap, span: Int, color: Int) {
        val inset = (geometry.cellSize - span) / 2
        if (gap.horizontal) {
            val left = geometry.cellStart(gap.tile.x) + geometry.cellSize
            val top = geometry.cellStart(gap.tile.z) + inset
            canvas.fill(left, top, left + geometry.gapSize, top + span, color)
        } else {
            val left = geometry.cellStart(gap.tile.x) + inset
            val top = geometry.cellStart(gap.tile.z) + geometry.cellSize
            canvas.fill(left, top, left + span, top + geometry.gapSize, color)
        }
    }

    /** A thin line from the middle of each tile to the next, ending at its door if it has one,
     * in a different color while the route doesn't reach blood yet. Grid neighbors are always
     * straight across or down, so each segment is a simple 1px fill. */
    private fun drawBloodRushPath(canvas: MapCanvas, path: BloodRushPath) {
        fun center(tileIndex: Int) = geometry.cellStart(tileIndex) + geometry.cellSize / 2
        fun gapMiddle(tileIndex: Int) = geometry.cellStart(tileIndex) + geometry.cellSize + geometry.gapSize / 2

        val door = path.door
        val doorPoint = door?.let { if (it.horizontal) gapMiddle(it.tile.x) to center(it.tile.z) else center(it.tile.x) to gapMiddle(it.tile.z) }
        val points = path.tiles.map { center(it.x) to center(it.z) } + listOfNotNull(doorPoint)
        val color = if (path.reachesBlood) BLOOD_RUSH_LINE else BLOOD_RUSH_UNCONFIRMED
        for ((from, to) in points.zipWithNext()) {
            val left = minOf(from.first, to.first)
            val top = minOf(from.second, to.second)
            val right = maxOf(from.first, to.first) + 1
            val bottom = maxOf(from.second, to.second) + 1
            canvas.fill(left, top, right, bottom, color)
        }
    }

    /** The team's secrets out of the dungeon's total, just below the map on its own background. */
    private fun drawSecretsLine(canvas: MapCanvas, map: FunnyMapSnapshot, options: MapPaintOptions) {
        val top = geometry.size
        canvas.fill(0, top, geometry.size, top + MapPainter.SECRETS_LINE_HEIGHT, backgroundColor(options))
        val total = map.totalSecrets
        val allFound = total != null && total.exact && map.teamSecrets >= total.count
        val text = "Secrets: ${map.teamSecrets}/${total?.label ?: "?"}"
        canvas.centeredText(text, (geometry.size / 2).toFloat(), (top + SECRETS_LINE_INSET).toFloat(), 1f, if (allFound) TEXT_DONE else TEXT_PENDING)
    }

    /** Where to center a room's label, in map pixels - see [FunnyMapLayout.labelCenter]. */
    protected fun labelCenter(tiles: Set<GridPos>): Pair<Float, Float> {
        val (x, z) = FunnyMapLayout.labelCenter(tiles)
        return geometry.tileUnitsToMap(x).toFloat() to geometry.tileUnitsToMap(z).toFloat()
    }

    /** [lines] stacked and centered on ([centerX], [centerY]). */
    protected fun drawLines(canvas: MapCanvas, lines: List<Pair<String, Int>>, centerX: Float, centerY: Float, scale: Float) {
        val lineHeight = canvas.lineHeight * scale
        var lineY = centerY - lines.size * lineHeight / 2
        for ((text, color) in lines) {
            canvas.centeredText(text, centerX, lineY, scale, color)
            lineY += lineHeight
        }
    }

    /** [icon], [size] pixels square, centered on ([centerX], [centerY]). */
    protected fun drawIcon(canvas: MapCanvas, icon: MapIcon, centerX: Float, centerY: Float, size: Float) {
        canvas.icon(icon, style, centerX - size / 2, centerY - size / 2, size)
    }

    protected fun cellCenter(tile: GridPos): Pair<Float, Float> =
        geometry.tileUnitsToMap(tile.x + 0.5).toFloat() to geometry.tileUnitsToMap(tile.z + 0.5).toFloat()

    protected fun checkmarkIcon(checkmark: MapCheckmark?): MapIcon? = when (checkmark) {
        MapCheckmark.GREEN -> MapIcon.GREEN_CHECK
        MapCheckmark.WHITE -> MapIcon.WHITE_CHECK
        MapCheckmark.RED -> MapIcon.CROSS
        else -> null
    }

    protected fun isOpened(tiles: Set<GridPos>, map: FunnyMapSnapshot): Boolean = tiles.any { it in map.opened }

    companion object {
        val BLOOD_RUSH_LINE = 0xFF39FF14.toInt()
        val BLOOD_RUSH_UNCONFIRMED = 0xFFFFE14D.toInt()
        val TEXT_PENDING = 0xFFD3D3D3.toInt()
        val TEXT_DONE = 0xFF90EE90.toInt()
        const val SECRETS_LINE_INSET = 2
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()

        // The crown is drawn at full size on Frost's big rooms and smaller on the others' 16-pixel ones.
        const val SMALL_CROWN = 0.7f
        const val CROWN_OVERHANG = 1f
    }
}

/** Frost's own look. */
internal object FrostPainter : StylePainter(MapStyle.FROST) {
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

    override val doorWidth = 8

    override fun drawBackground(canvas: MapCanvas, options: MapPaintOptions) =
        canvas.fill(0, 0, geometry.size, geometry.size, options.backgroundColor)

    override fun backgroundColor(options: MapPaintOptions) = options.backgroundColor

    override fun roomColor(room: RoomDatabase.RoomInfo, opened: Boolean, map: FunnyMapSnapshot, options: MapPaintOptions): Int {
        val filled = options.fillCompletedRooms && FunnyMapLayout.fillsWhenCompleted(room.type, options.filledRoomTypes) && map.isCompleted(room)
        if (filled) return options.fillColor
        val color = when (room.type) {
            "RARE" -> ROOM_RARE
            "CHAMPION" -> ROOM_CHAMPION
            "PUZZLE" -> ROOM_PUZZLE
            "TRAP" -> ROOM_TRAP
            "ENTRANCE" -> ROOM_ENTRANCE
            "FAIRY" -> ROOM_FAIRY
            "BLOOD" -> ROOM_BLOOD
            else -> ROOM_NORMAL
        }
        return if (opened) color else scaleRgb(color, UNOPENED_BRIGHTNESS)
    }

    override fun hintColor(behindBloodDoor: Boolean) = ROOM_UNKNOWN

    /** Wither and blood doors keep their color even unopened - where they are matters most
     * before anyone reaches them. */
    override fun doorColor(door: DoorSides): Int = when (door.kind) {
        DoorKind.NORMAL -> if (door.anyOpened) DOOR_NORMAL else scaleRgb(DOOR_NORMAL, UNOPENED_BRIGHTNESS)
        DoorKind.WITHER -> DOOR_WITHER
        DoorKind.BLOOD -> DOOR_BLOOD
    }

    /** Each room's name once, word-wrapped and sized to fit the room, with its found/total
     * secrets underneath when it has any. Each turns green independently: the name when the
     * room is cleared, the count when its secrets are all done. */
    override fun drawLabels(canvas: MapCanvas, map: FunnyMapSnapshot, options: MapPaintOptions, hints: Set<GridPos>) {
        for ((room, tiles) in FunnyMapLayout.groupRooms(map.rooms)) {
            val nameColor = if (map.isCleared(room)) TEXT_DONE else TEXT_PENDING
            val found = map.secretsFound(room)
            val secretsColor = if (found >= room.secrets) TEXT_DONE else TEXT_PENDING
            val nameLines = if (options.showsName(room.type)) room.name.split(' ').map { it to nameColor } else emptyList()
            val lines = nameLines + listOfNotNull(room.secrets.takeIf { it > 0 }?.let { "$found/$it" to secretsColor })
            if (lines.isEmpty()) continue
            val scale = FunnyMapLayout.labelScale(
                lines.maxOf { canvas.textWidth(it.first) },
                FunnyMapLayout.labelWidth(tiles),
                options.limitRoomNames,
                options.textSize,
            )
            val (centerX, centerY) = labelCenter(tiles)
            drawLines(canvas, lines, centerX, centerY, scale)
        }
    }
}

/**
 * Odin's Dungeon Map: brown rooms with a faint black background and outline, small room names
 * and secret counts that go white then green, gray question marks on rooms a door leads into,
 * and doors in the color of the special room they lead to. Odin only ever shows opened rooms,
 * so Funny's unopened ones are drawn the way it draws its guesses: at half brightness.
 */
internal object OdinPainter : StylePainter(MapStyle.ODIN) {
    private val NORMAL = rgb(107, 58, 17)
    private val PUZZLE = rgb(117, 0, 133)
    private val TRAP = rgb(216, 127, 51)
    private val BLOOD = rgb(255, 0, 0)
    private val ENTRANCE = rgb(20, 133, 0)
    private val FAIRY = rgb(224, 0, 255)
    private val CHAMPION = rgb(254, 223, 0)
    private val OTHER = rgb(60, 60, 60)
    private val UNKNOWN_ROOM = rgb(40, 40, 40)

    // Odin's Color.darker() defaults to 70% brightness.
    private const val DOOR_DARKEN = 0.7f
    private const val GUESS_DARKEN = 0.5f
    private val NORMAL_DOOR = scaleRgb(NORMAL, DOOR_DARKEN)
    private val FAIRY_DOOR = scaleRgb(FAIRY, DOOR_DARKEN)
    private val UNKNOWN_DOOR = scaleRgb(UNKNOWN_ROOM, DOOR_DARKEN)

    private const val BACKGROUND = 0x1A000000
    private const val TEXT_SCALE = 0.45f
    private const val ICON_SIZE = 12f
    private val TEXT_GREEN = rgb(85, 255, 85)
    private val TEXT_FAILED = rgb(255, 85, 85)
    private val TEXT_PENDING_ODIN = rgb(100, 100, 100)
    private val NO_TEXT_TYPES = setOf("ENTRANCE", "FAIRY", "BLOOD")

    override val doorWidth = 8

    override fun drawBackground(canvas: MapCanvas, options: MapPaintOptions) {
        val size = geometry.size
        canvas.fill(0, 0, size, size, BACKGROUND)
        canvas.fill(0, 0, size, 1, BLACK)
        canvas.fill(0, size - 1, size, size, BLACK)
        canvas.fill(0, 1, 1, size - 1, BLACK)
        canvas.fill(size - 1, 1, size, size - 1, BLACK)
    }

    override fun backgroundColor(options: MapPaintOptions) = BACKGROUND

    private fun typeColor(type: String): Int = when (type) {
        "NORMAL", "RARE" -> NORMAL
        "PUZZLE" -> PUZZLE
        "TRAP" -> TRAP
        "BLOOD" -> BLOOD
        "ENTRANCE" -> ENTRANCE
        "FAIRY" -> FAIRY
        "CHAMPION" -> CHAMPION
        else -> OTHER
    }

    override fun roomColor(room: RoomDatabase.RoomInfo, opened: Boolean, map: FunnyMapSnapshot, options: MapPaintOptions): Int {
        val color = typeColor(room.type)
        return if (opened) color else scaleRgb(color, GUESS_DARKEN)
    }

    override fun hintColor(behindBloodDoor: Boolean) = if (behindBloodDoor) scaleRgb(BLOOD, GUESS_DARKEN) else UNKNOWN_ROOM

    /** A locked wither door is black, and a blood door dim until the blood room's open. Any other
     * door, wither doors once opened included, takes the color of the special room it leads to. */
    override fun doorColor(door: DoorSides): Int {
        if (door.kind == DoorKind.WITHER && !door.bothOpened) return BLACK
        if (door.kind == DoorKind.BLOOD) return if (door.bothOpened) BLOOD else scaleRgb(BLOOD, GUESS_DARKEN)
        if (door.hasUnknownSide) return UNKNOWN_DOOR
        val special = door.sideTypes.firstOrNull { it != "NORMAL" && it != "FAIRY" }
        val color = special?.let(::typeColor) ?: if ("FAIRY" in door.sideTypes) FAIRY_DOOR else NORMAL_DOOR
        return if (door.bothOpened) color else scaleRgb(color, GUESS_DARKEN)
    }

    override fun drawLabels(canvas: MapCanvas, map: FunnyMapSnapshot, options: MapPaintOptions, hints: Set<GridPos>) {
        val iconSize = ICON_SIZE * options.textSize
        for ((room, tiles) in FunnyMapLayout.groupRooms(map.rooms)) {
            if (room.type in NO_TEXT_TYPES) continue
            val checkmark = map.checkmarks[room]
            // A room without its name shows just its checkmark, in its top-left tile.
            if (!options.showsName(room.type)) {
                val icon = checkmarkIcon(checkmark) ?: continue
                val (x, z) = cellCenter(GridPos(tiles.minOf { it.x }, tiles.minOf { it.z }))
                drawIcon(canvas, icon, x, z, iconSize)
                continue
            }
            val color = when (checkmark) {
                MapCheckmark.GREEN -> TEXT_GREEN
                MapCheckmark.WHITE -> WHITE
                MapCheckmark.RED -> TEXT_FAILED
                else -> TEXT_PENDING_ODIN
            }
            val found = if (checkmark == MapCheckmark.GREEN) room.secrets.toString() else map.foundSecrets[room]?.toString() ?: "?"
            val secrets = if (room.secrets > 0) " $found/${room.secrets}" else ""
            val lines = "${room.name}$secrets".trim().split(' ').map { it to color }
            val (centerX, centerY) = labelCenter(tiles)
            drawLines(canvas, lines, centerX, centerY, TEXT_SCALE * options.textSize)
        }
        for (tile in hints) {
            val (x, z) = cellCenter(tile)
            drawIcon(canvas, MapIcon.QUESTION, x, z, iconSize)
        }
    }
}

/**
 * NoammAddons' Dungeon Map: a see-through white background with a white border, checkmark
 * icons in the middle of each room, gray rooms with a question mark behind doors not yet opened,
 * and its own room colors. Funny is drawn the way its Cheater Map draws rooms nobody's found yet:
 * halfway to black, with no icon.
 */
internal object NaPainter : StylePainter(MapStyle.NA) {
    private val NORMAL = rgb(114, 67, 27)
    private val BLOOD = rgb(255, 0, 0)
    private val FAIRY = rgb(242, 127, 165)
    private val CHAMPION = rgb(229, 229, 51)
    private val PUZZLE = rgb(178, 76, 216)
    private val TRAP = rgb(216, 127, 51)
    private val ENTRANCE = rgb(0, 124, 0)
    private val UNOPENED = rgb(65, 65, 65)
    private val WITHER_DOOR = rgb(16, 16, 16)

    private const val BACKGROUND = 0x32FFFFFF
    private const val UNDISCOVERED_TINT = 0.5f
    private const val ICON_SIZE = 10f

    override val doorWidth = 6

    override fun drawBackground(canvas: MapCanvas, options: MapPaintOptions) {
        val size = geometry.size
        canvas.fill(0, 0, size, size, BACKGROUND)
        canvas.fill(0, 0, size, 1, WHITE)
        canvas.fill(0, size - 1, size, size, WHITE)
        canvas.fill(0, 1, 1, size - 1, WHITE)
        canvas.fill(size - 1, 1, size, size - 1, WHITE)
    }

    override fun backgroundColor(options: MapPaintOptions) = BACKGROUND

    // NA only marks a room rare by a map color Hypixel doesn't give these rooms, so they're
    // normal rooms here.
    private fun typeColor(type: String): Int = when (type) {
        "BLOOD" -> BLOOD
        "FAIRY" -> FAIRY
        "CHAMPION" -> CHAMPION
        "PUZZLE" -> PUZZLE
        "TRAP" -> TRAP
        "ENTRANCE" -> ENTRANCE
        else -> NORMAL
    }

    override fun roomColor(room: RoomDatabase.RoomInfo, opened: Boolean, map: FunnyMapSnapshot, options: MapPaintOptions): Int {
        val color = typeColor(room.type)
        return if (opened) color else scaleRgb(color, UNDISCOVERED_TINT)
    }

    override fun hintColor(behindBloodDoor: Boolean) = UNOPENED

    /** Blood doors are red and locked wither doors near-black; a door into a room not yet opened
     * is gray. Otherwise a door is the color of the special room it leads to - the one that
     * isn't fairy if it's between two - or a normal room's brown. */
    override fun doorColor(door: DoorSides): Int {
        val darken = !door.hasUnknownSide && !door.bothOpened
        val color = when {
            door.kind == DoorKind.BLOOD -> BLOOD
            door.kind == DoorKind.WITHER -> return if (door.bothOpened) NORMAL else WITHER_DOOR
            door.hasUnknownSide -> return UNOPENED
            else -> {
                val special = door.sideTypes.filter { typeColor(it) != NORMAL }
                val pick = if (special.size == 2) special.firstOrNull { it != "FAIRY" } else special.firstOrNull()
                pick?.let(::typeColor) ?: NORMAL
            }
        }
        return if (darken) scaleRgb(color, UNDISCOVERED_TINT) else color
    }

    private val TEXT_GREEN = rgb(85, 255, 85)
    private val TEXT_FAILED = rgb(255, 0, 0)
    private val TEXT_PENDING_NA = rgb(170, 170, 170)

    // NA shrinks names to fit their rooms, but no further than this.
    private const val MIN_TEXT_SCALE = 0.39f

    override fun drawLabels(canvas: MapCanvas, map: FunnyMapSnapshot, options: MapPaintOptions, hints: Set<GridPos>) {
        val iconSize = ICON_SIZE * options.textSize
        for ((room, tiles) in FunnyMapLayout.groupRooms(map.rooms)) {
            if (room.type == "ENTRANCE" || room.type == "FAIRY") continue
            val opened = isOpened(tiles, map)
            val (x, z) = labelCenter(tiles)
            val mode = when (options.naRoomText) {
                NaRoomText.SECRETS_AND_PUZZLE_NAME -> if (room.type == "PUZZLE" || room.secrets == 0) NaRoomText.ROOM_NAME else NaRoomText.SECRETS
                else -> options.naRoomText
            }
            if (mode == NaRoomText.CHECKMARKS) {
                // Nothing for rooms nobody has found yet, as on NA's Cheater Map.
                if (opened) checkmarkIcon(map.checkmarks[room])?.let { drawIcon(canvas, it, x, z, iconSize) }
                continue
            }
            // Its text modes write on unopened rooms in Funny too, in gray.
            val color = when (map.checkmarks[room]?.takeIf { opened }) {
                MapCheckmark.GREEN -> TEXT_GREEN
                MapCheckmark.RED -> TEXT_FAILED
                MapCheckmark.WHITE -> WHITE
                else -> TEXT_PENDING_NA
            }
            val secrets = "${map.secretsFound(room)}/${room.secrets}"
            if (mode == NaRoomText.SECRETS) {
                drawLines(canvas, listOf((if (room.secrets == 0) "0" else secrets) to color), x, z, options.textSize)
                continue
            }
            val lines = room.name.split(' ') + listOfNotNull(secrets.takeIf { mode == NaRoomText.NAME_AND_SECRETS && room.secrets > 0 })
            drawLines(canvas, lines.map { it to color }, x, z, textScale(canvas, lines, tiles, options.textSize))
        }
        for (tile in hints) {
            val (x, z) = cellCenter(tile)
            drawIcon(canvas, MapIcon.QUESTION, x, z, iconSize)
        }
    }

    /** As big as [maxScale], shrunk until every line fits the room across and all of them fit
     * down it. Like NA, an L is two rooms wide and one tall, and a 2x2 two by two, gaps left out. */
    private fun textScale(canvas: MapCanvas, lines: List<String>, tiles: Set<GridPos>, maxScale: Float): Float {
        val cell = geometry.cellSize
        val spanX = tiles.maxOf { it.x } - tiles.minOf { it.x } + 1
        val spanZ = tiles.maxOf { it.z } - tiles.minOf { it.z } + 1
        val (maxWidth, maxHeight) = when {
            tiles.size == 3 && spanX == 2 && spanZ == 2 -> cell * 2 to cell
            spanX == 2 && spanZ == 2 -> cell * 2 to cell * 2
            else -> spanX * cell + (spanX - 1) * geometry.gapSize to spanZ * cell + (spanZ - 1) * geometry.gapSize
        }
        val widest = lines.maxOf { canvas.textWidth(it) }.toFloat()
        val tall = (lines.size * canvas.lineHeight).toFloat()
        if (widest <= 0f || tall <= 0f) return maxScale
        return minOf(maxWidth / widest, maxHeight / tall).coerceIn(MIN_TEXT_SCALE, maxScale)
    }
}

/**
 * dtMap's - LegitCatMod's - Dungeon Map: a darker see-through background with no border, room
 * names in white, gray until cleared and green once done, thick doors, and nearly black rooms
 * behind doors not yet opened. Funny is drawn the way its non-legit mode draws rooms nobody's
 * opened: at 40% brightness, names still showing.
 */
internal object LcmPainter : StylePainter(MapStyle.LCM) {
    private val UNOPENED = rgb(30, 30, 30)
    private val BLOOD = rgb(255, 0, 0)
    private val NORMAL = rgb(107, 58, 17)
    private val PUZZLE = rgb(117, 0, 133)
    private val CHAMPION = rgb(254, 223, 0)
    private val TRAP = rgb(216, 127, 51)
    private val ENTRANCE = rgb(20, 133, 0)
    private val FAIRY = rgb(244, 19, 139)
    private val RARE = rgb(255, 203, 89)

    private const val BACKGROUND = 0x46000000
    private const val DARKEN = 0.4f
    private const val TEXT_SCALE = 0.45f
    private const val ICON_SIZE = 12f
    private val TEXT_GREEN = rgb(0, 255, 0)
    private val TEXT_FAILED = rgb(255, 0, 0)
    private val TEXT_PENDING_LCM = rgb(100, 100, 100)
    private val NO_TEXT_TYPES = setOf("ENTRANCE", "BLOOD", "FAIRY")

    override val doorWidth = 9

    override fun drawBackground(canvas: MapCanvas, options: MapPaintOptions) =
        canvas.fill(0, 0, geometry.size, geometry.size, BACKGROUND)

    override fun backgroundColor(options: MapPaintOptions) = BACKGROUND

    private fun typeColor(type: String): Int = when (type) {
        "BLOOD" -> BLOOD
        "PUZZLE" -> PUZZLE
        "CHAMPION" -> CHAMPION
        "TRAP" -> TRAP
        "ENTRANCE" -> ENTRANCE
        "FAIRY" -> FAIRY
        "RARE" -> RARE
        else -> NORMAL
    }

    override fun roomColor(room: RoomDatabase.RoomInfo, opened: Boolean, map: FunnyMapSnapshot, options: MapPaintOptions): Int {
        val color = typeColor(room.type)
        return if (opened) color else scaleRgb(color, DARKEN)
    }

    override fun hintColor(behindBloodDoor: Boolean) = if (behindBloodDoor) scaleRgb(BLOOD, DARKEN) else UNOPENED

    /** Doors share their rooms' colors. A locked wither door is black, and one opened next to
     * the fairy room turns pink. Until both sides are open, a door toward an unknown room is
     * near-black (blood stays a dim red) and one toward a known room is darkened. */
    override fun doorColor(door: DoorSides): Int {
        val locked = !door.bothOpened
        val color = when (door.kind) {
            DoorKind.BLOOD -> BLOOD
            DoorKind.WITHER -> when {
                locked -> BLACK
                door.sideTypes.firstOrNull { it != "NORMAL" } == "FAIRY" -> FAIRY
                else -> NORMAL
            }
            DoorKind.NORMAL -> door.sideTypes.firstOrNull { it != "NORMAL" && it != "FAIRY" }?.let(::typeColor) ?: NORMAL
        }
        if (door.bothOpened) return color
        if (!door.hasUnknownSide) return scaleRgb(color, DARKEN)
        return when (door.kind) {
            DoorKind.BLOOD -> scaleRgb(BLOOD, DARKEN)
            DoorKind.WITHER -> BLACK
            DoorKind.NORMAL -> UNOPENED
        }
    }

    override fun drawLabels(canvas: MapCanvas, map: FunnyMapSnapshot, options: MapPaintOptions, hints: Set<GridPos>) {
        for ((room, tiles) in FunnyMapLayout.groupRooms(map.rooms)) {
            if (room.type in NO_TEXT_TYPES) continue
            val opened = isOpened(tiles, map)
            val checkmark = map.checkmarks[room]
            val (centerX, centerY) = labelCenter(tiles)
            if (!options.showsName(room.type)) {
                val icon = checkmarkIcon(checkmark)?.takeIf { opened } ?: continue
                drawIcon(canvas, icon, centerX, centerY, ICON_SIZE * options.textSize)
                continue
            }
            val color = when {
                !opened -> WHITE
                checkmark == MapCheckmark.GREEN -> TEXT_GREEN
                checkmark == MapCheckmark.WHITE -> WHITE
                checkmark == MapCheckmark.RED -> TEXT_FAILED
                else -> TEXT_PENDING_LCM
            }
            drawLines(canvas, room.name.split(' ').map { it to color }, centerX, centerY, TEXT_SCALE * options.textSize)
        }
    }
}
