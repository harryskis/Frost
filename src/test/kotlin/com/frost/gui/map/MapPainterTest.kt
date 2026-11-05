package com.frost.gui.map

import com.frost.dungeon.FunnyMapLayout
import com.frost.dungeon.FunnyMapSnapshot
import com.frost.dungeon.GridPos
import com.frost.dungeon.MapGap
import com.frost.dungeon.MapStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapPainterTest {
    private fun paint(style: MapStyle, map: FunnyMapSnapshot, options: MapPaintOptions = MapPaintOptions()) =
        RecordingMapCanvas().also { MapPainter.paint(it, style, map, options) }

    private fun rgb(red: Int, green: Int, blue: Int) = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    private fun center(style: MapStyle, index: Int) = style.geometry.cellStart(index) + style.geometry.cellSize / 2

    private fun colorAt(canvas: RecordingMapCanvas, style: MapStyle, tile: GridPos) =
        canvas.colorAt(center(style, tile.x), center(style, tile.z))

    /** The middle of the door in [gap]. */
    private fun doorColor(canvas: RecordingMapCanvas, style: MapStyle, gap: MapGap): Int? {
        val geometry = style.geometry
        val across = geometry.cellStart(if (gap.horizontal) gap.tile.x else gap.tile.z) + geometry.cellSize + geometry.gapSize / 2
        return if (gap.horizontal) canvas.colorAt(across, center(style, gap.tile.z)) else canvas.colorAt(center(style, gap.tile.x), across)
    }

    private val modes = listOf(PreviewDungeon.legit, PreviewDungeon.funny)
    private val withExtras = MapPaintOptions(bloodRushPath = true, totalSecrets = true)

    @Test
    fun `everything every style draws stays inside the map`() {
        for (style in MapStyle.entries) for (map in modes) for (options in listOf(MapPaintOptions(), withExtras)) {
            val canvas = paint(style, map, options)
            val width = style.geometry.size
            val height = MapPainter.height(style, options)
            for (fill in canvas.fills) {
                assertTrue(fill.left >= 0 && fill.top >= 0 && fill.right <= width && fill.bottom <= height, "$style drew $fill off its $width x $height map")
            }
            for (icon in canvas.icons) {
                assertTrue(icon.left >= 0 && icon.top >= 0 && icon.left + icon.size <= width && icon.top + icon.size <= height, "$style put $icon off the map")
            }
        }
    }

    @Test
    fun `the map is taller by the secrets line only while it's on`() {
        for (style in MapStyle.entries) {
            assertEquals(style.geometry.size, MapPainter.height(style, MapPaintOptions()))
            assertEquals(style.geometry.size + MapPainter.SECRETS_LINE_HEIGHT, MapPainter.height(style, withExtras))
        }
    }

    @Test
    fun `each style puts a tile's world center on its cell's center`() {
        val tileCenter = FunnyMapLayout.tileToWorld(2.0)
        for (style in MapStyle.entries) assertEquals(center(style, 2).toDouble(), style.geometry.worldToMap(tileCenter), 0.001, "$style")
        assertEquals(FunnyMapLayout.worldToMap(-150.0), MapStyle.FROST.geometry.worldToMap(-150.0), 0.001)
    }

    @Test
    fun `legit shows nothing of a room nobody has reached in any style`() {
        val waterfall = GridPos(0, 0)
        for (style in MapStyle.entries) {
            val canvas = paint(style, PreviewDungeon.legit)
            assertEquals(canvas.fills.first().color, colorAt(canvas, style, waterfall), "$style")
            assertTrue(canvas.texts.none { it.text == "Waterfall" }, "$style")
        }
    }

    @Test
    fun `funny dims rooms not yet opened, each style its own way`() {
        val quiz = GridPos(4, 0)
        val expected = mapOf(
            MapStyle.FROST to scaleRgb(0xFF7A3E9D.toInt(), 0.45f),
            MapStyle.NA to scaleRgb(rgb(178, 76, 216), 0.5f),
            MapStyle.ODIN to scaleRgb(rgb(117, 0, 133), 0.5f),
            MapStyle.LCM to scaleRgb(rgb(117, 0, 133), 0.4f),
        )
        for ((style, color) in expected) assertEquals(color, colorAt(paint(style, PreviewDungeon.funny), style, quiz), "$style")
    }

    @Test
    fun `opened rooms are each mod's own colors`() {
        val trap = GridPos(1, 2)
        val rare = GridPos(1, 5)
        for (map in modes) {
            assertEquals(rgb(216, 127, 51), colorAt(paint(MapStyle.NA, map), MapStyle.NA, trap))
            assertEquals(rgb(216, 127, 51), colorAt(paint(MapStyle.ODIN, map), MapStyle.ODIN, trap))
            // dtMap has rare rooms gold, the others draw them as normal rooms.
            assertEquals(rgb(255, 203, 89), colorAt(paint(MapStyle.LCM, map), MapStyle.LCM, rare))
            assertEquals(rgb(107, 58, 17), colorAt(paint(MapStyle.ODIN, map), MapStyle.ODIN, rare))
            assertEquals(rgb(114, 67, 27), colorAt(paint(MapStyle.NA, map), MapStyle.NA, rare))
        }
    }

    @Test
    fun `a room behind a door nobody has opened is hinted at each mod's way`() {
        val archway = GridPos(0, 2)
        val expected = mapOf(
            MapStyle.FROST to 0xFF3A3F47.toInt(),
            MapStyle.NA to rgb(65, 65, 65),
            MapStyle.ODIN to rgb(40, 40, 40),
            MapStyle.LCM to rgb(30, 30, 30),
        )
        for ((style, color) in expected) {
            val canvas = paint(style, PreviewDungeon.legit)
            assertEquals(color, colorAt(canvas, style, archway), "$style")
            val question = canvas.icons.filter { it.icon == MapIcon.QUESTION }
            val onArchway = question.any { it.centerX.toInt() == center(style, archway.x) && it.centerY.toInt() == center(style, archway.z) }
            // Odin and NA mark these with a question mark; dtMap's are off by default.
            assertEquals(style == MapStyle.NA || style == MapStyle.ODIN, onArchway, "$style")
        }
    }

    @Test
    fun `wither doors stay dark until opened, then take the colors around them`() {
        val lockedWither = MapGap(GridPos(3, 1), horizontal = true) // Mines into Shadow Assassin
        val openedWither = MapGap(GridPos(3, 3), horizontal = false) // Fairy into Slime
        val black = rgb(0, 0, 0)
        assertEquals(black, doorColor(paint(MapStyle.ODIN, PreviewDungeon.legit), MapStyle.ODIN, lockedWither))
        assertEquals(black, doorColor(paint(MapStyle.LCM, PreviewDungeon.legit), MapStyle.LCM, lockedWither))
        assertEquals(rgb(16, 16, 16), doorColor(paint(MapStyle.NA, PreviewDungeon.legit), MapStyle.NA, lockedWither))

        assertEquals(scaleRgb(rgb(224, 0, 255), 0.7f), doorColor(paint(MapStyle.ODIN, PreviewDungeon.legit), MapStyle.ODIN, openedWither))
        assertEquals(rgb(244, 19, 139), doorColor(paint(MapStyle.LCM, PreviewDungeon.legit), MapStyle.LCM, openedWither))
        assertEquals(rgb(114, 67, 27), doorColor(paint(MapStyle.NA, PreviewDungeon.legit), MapStyle.NA, openedWither))
    }

    @Test
    fun `doors take the color of the special room they lead into`() {
        // Entrance into Beams. (Between Lava Pit and Entrance, Odin and dtMap both go by whichever
        // non-normal room comes first, and count a rare room as one, so that door isn't green.)
        val entranceDoor = MapGap(GridPos(2, 5), horizontal = true)
        assertEquals(rgb(20, 133, 0), doorColor(paint(MapStyle.ODIN, PreviewDungeon.legit), MapStyle.ODIN, entranceDoor))
        assertEquals(rgb(20, 133, 0), doorColor(paint(MapStyle.LCM, PreviewDungeon.legit), MapStyle.LCM, entranceDoor))
        assertEquals(rgb(0, 124, 0), doorColor(paint(MapStyle.NA, PreviewDungeon.legit), MapStyle.NA, entranceDoor))
    }

    private val naCheckmarks = MapPaintOptions(naRoomText = NaRoomText.CHECKMARKS)

    @Test
    fun `only Frost draws no icons, and NA draws names unless set to checkmarks`() {
        for (map in modes) {
            assertTrue(paint(MapStyle.FROST, map).icons.isEmpty())
            assertTrue(paint(MapStyle.NA, map, naCheckmarks).texts.isEmpty())
            for (style in MapStyle.entries) assertTrue(paint(style, map).texts.any { it.text == "Crypt" }, "$style")
        }
    }

    @Test
    fun `NA writes names and secrets by default, shrunk to fit, colored by progress`() {
        val canvas = paint(MapStyle.NA, PreviewDungeon.legit)
        val crypt = canvas.textsSaying("Crypt").single()
        assertEquals(rgb(255, 255, 255), crypt.color)
        assertEquals(crypt.scale, canvas.textsSaying("3/5").single().scale)
        // "Crypt" and "3/5" at 6 pixels a letter, in a 1x2 room 36 pixels wide and 16 tall: the
        // height is what limits it - two lines of 9.
        assertEquals(16f / 18, crypt.scale, 0.001f)
        assertEquals(rgb(85, 255, 85), canvas.textsSaying("Admin").single().color)
        assertEquals(rgb(255, 0, 0), canvas.textsSaying("Ice").single().color)
        assertEquals(rgb(170, 170, 170), canvas.textsSaying("Mines").single().color)
        assertTrue(canvas.texts.none { it.text == "Entrance" || it.text == "Fairy" })
        // Question marks still go on rooms behind unopened doors, and the gray names on Funny's.
        assertTrue(canvas.icons.any { it.icon == MapIcon.QUESTION })
        assertEquals(rgb(170, 170, 170), paint(MapStyle.NA, PreviewDungeon.funny).textsSaying("Waterfall").single().color)
    }

    @Test
    fun `NA's other modes write just names, just secrets, or secrets except on puzzles`() {
        val names = paint(MapStyle.NA, PreviewDungeon.legit, MapPaintOptions(naRoomText = NaRoomText.ROOM_NAME))
        assertTrue(names.texts.any { it.text == "Crypt" } && names.texts.none { it.text == "3/5" })
        val secrets = paint(MapStyle.NA, PreviewDungeon.legit, MapPaintOptions(naRoomText = NaRoomText.SECRETS))
        assertTrue(secrets.texts.any { it.text == "3/5" } && secrets.texts.none { it.text == "Crypt" })
        assertEquals(listOf("0"), secrets.texts.filter { it.text == "0" }.map { it.text }.distinct())
        val mixed = paint(MapStyle.NA, PreviewDungeon.legit, MapPaintOptions(naRoomText = NaRoomText.SECRETS_AND_PUZZLE_NAME))
        assertTrue(mixed.texts.any { it.text == "3/5" } && mixed.texts.any { it.text == "Ice" } && mixed.texts.none { it.text == "Crypt" })
    }

    @Test
    fun `NA marks finished rooms with a checkmark in their middle, and a failed puzzle with a cross`() {
        val canvas = paint(MapStyle.NA, PreviewDungeon.legit, naCheckmarks)
        val iceFill = GridPos(0, 3)
        val cross = canvas.icons.single { it.icon == MapIcon.CROSS }
        assertEquals(center(MapStyle.NA, iceFill.x).toFloat(), cross.centerX)
        assertEquals(center(MapStyle.NA, iceFill.z).toFloat(), cross.centerY)
        assertEquals(10f, cross.size)
        // Slime, Andesite and Admin are green; Fairy is too, but NA leaves the fairy room bare.
        assertEquals(3, canvas.icons.count { it.icon == MapIcon.GREEN_CHECK })
        assertEquals(4, canvas.icons.count { it.icon == MapIcon.WHITE_CHECK })
        // Nothing on rooms in Funny that nobody has found yet.
        assertEquals(canvas.icons.count { it.icon != MapIcon.QUESTION }, paint(MapStyle.NA, PreviewDungeon.funny, naCheckmarks).icons.count { it.icon != MapIcon.QUESTION })
    }

    @Test
    fun `Odin writes names and secrets, gray until cleared, then white, then green`() {
        val legit = paint(MapStyle.ODIN, PreviewDungeon.legit)
        assertEquals(rgb(255, 255, 255), legit.textsSaying("3/5").single().color) // Crypt, cleared
        assertEquals(rgb(100, 100, 100), legit.textsSaying("4/10").single().color) // Mines, in progress
        assertEquals(rgb(85, 255, 85), legit.textsSaying("Admin").single().color)
        assertEquals(rgb(255, 85, 85), legit.textsSaying("Ice").single().color) // failed
        assertEquals(0.45f, legit.textsSaying("Crypt").single().scale)
        assertTrue(legit.texts.none { it.text in setOf("Entrance", "Fairy", "Blood") })
        // A secret count nobody has reported is a question mark.
        assertEquals(rgb(100, 100, 100), paint(MapStyle.ODIN, PreviewDungeon.funny).textsSaying("?/8").single().color)
    }

    @Test
    fun `a room type with its name hidden shows its checkmark instead on Odin and LCM`() {
        val hideTraps = MapPaintOptions(hiddenNameTypes = setOf("TRAP"))
        for (style in listOf(MapStyle.ODIN, MapStyle.LCM)) {
            val canvas = paint(style, PreviewDungeon.legit, hideTraps)
            assertTrue(canvas.texts.none { it.text == "Old" || it.text == "Trap" }, "$style")
            // Old Trap is cleared, and the only room drawn with an icon rather than words.
            assertEquals(MapIcon.WHITE_CHECK, canvas.icons.filter { it.icon != MapIcon.QUESTION }.single().icon, "$style")
        }
    }

    @Test
    fun `LCM writes room names alone, colored by progress, and white for unopened rooms in Funny`() {
        val legit = paint(MapStyle.LCM, PreviewDungeon.legit)
        assertEquals(rgb(0, 255, 0), legit.textsSaying("Admin").single().color)
        assertEquals(rgb(255, 255, 255), legit.textsSaying("Crypt").single().color)
        assertEquals(rgb(100, 100, 100), legit.textsSaying("Mines").single().color)
        assertEquals(rgb(255, 0, 0), legit.textsSaying("Ice").single().color)
        assertTrue(legit.texts.none { it.text.contains('/') })
        assertTrue(legit.icons.isEmpty())
        assertEquals(rgb(255, 255, 255), paint(MapStyle.LCM, PreviewDungeon.funny).textsSaying("Waterfall").single().color)
    }

    @Test
    fun `Frost fills finished rooms and keeps its own text`() {
        val options = MapPaintOptions()
        val canvas = paint(MapStyle.FROST, PreviewDungeon.legit, options)
        assertEquals(options.fillColor, colorAt(canvas, MapStyle.FROST, GridPos(2, 4))) // Slime: green, every secret
        assertEquals(0xFF6B4423.toInt(), colorAt(paint(MapStyle.FROST, PreviewDungeon.legit, options.copy(fillCompletedRooms = false)), MapStyle.FROST, GridPos(2, 4)))
        assertEquals(0xFF90EE90.toInt(), canvas.textsSaying("Crypt").single().color)
        assertEquals(0xFFD3D3D3.toInt(), canvas.textsSaying("3/5").single().color)
    }

    @Test
    fun `Frost-only settings leave the other styles alone`() {
        val frostTweaks = MapPaintOptions(fillCompletedRooms = true, backgroundColor = 0xFF123456.toInt(), fillColor = 0xFF00FFFF.toInt())
        for (style in listOf(MapStyle.NA, MapStyle.ODIN, MapStyle.LCM)) {
            val fills = paint(style, PreviewDungeon.legit, frostTweaks).fills.map { it.color }
            assertTrue(0xFF123456.toInt() !in fills && 0xFF00FFFF.toInt() !in fills, "$style")
        }
    }
}
