package com.frost.dungeon

/**
 * Where everything sits on one map style: the room cells, the gaps between them, how far in
 * from the map's edge the grid starts, and the map's whole width and height. All in that style's
 * own GUI pixels, before the map's scale is applied.
 */
data class MapGeometry(val cellSize: Int, val gapSize: Int, val inset: Int, val size: Int) {
    val pitch: Int get() = cellSize + gapSize

    /** The left/top edge of a tile's cell. */
    fun cellStart(index: Int): Int = inset + index * pitch

    /** A position in tile units, where a tile's center is its index + 0.5, in map pixels - so a
     * tile's center lands on its cell's center, and a whole number on the middle of a gap. */
    fun tileUnitsToMap(units: Double): Double = inset + units * pitch - gapSize / 2.0

    /** Where a world x or z coordinate lands on the map. */
    fun worldToMap(coordinate: Double): Double = tileUnitsToMap(FunnyMapLayout.worldToTileUnits(coordinate))
}

/**
 * How a player's head looks on the map at 100% head size: the face, and a square of
 * [backdropColor] behind it that's [backdropSize] across, or nothing if 0. With [arrow] it's the
 * vanilla green map pointer instead of a face.
 */
data class HeadLook(val faceSize: Int, val backdropSize: Int, val backdropColor: Int, val arrow: Boolean = false)

/**
 * The look Dungeon Map is drawn in. Frost is the mod's own; the rest replicate the dungeon maps
 * of other mods, with their default settings: NoammAddons (NA), Odin, and dtMap - the map from
 * LegitCatMod (LCM). Their colors, sizes and layouts come from each mod's own source - see
 * /THIRD_PARTY_NOTICES.md at the repo root.
 */
enum class MapStyle(val label: String, val geometry: MapGeometry, val iconTextureSize: Int) {
    FROST(
        "Frost",
        MapGeometry(FunnyMapLayout.CELL_SIZE, FunnyMapLayout.GAP_SIZE, FunnyMapLayout.GAP_SIZE / 2, FunnyMapLayout.MAP_SIZE),
        0,
    ),

    // The real maps are 128 pixels with 16-pixel rooms 4 apart. Where the grid starts differs a
    // little between them, as does the frame around it - dtMap's is 5 pixels all round.
    NA("NA", MapGeometry(16, 4, 6, 128), 100),
    ODIN("Odin", MapGeometry(16, 4, 5, 128), 10),
    LCM("LCM", MapGeometry(16, 4, 5, 126), 10),
    ;

    /** Odin and dtMap both write player names under the heads while a spirit leap is held. */
    val namesWhileLeaping: Boolean get() = this == ODIN || this == LCM

    /** How [self] or a teammate playing [className] (null if unknown) looks on this style. */
    fun headLook(self: Boolean, className: String?): HeadLook = when (this) {
        FROST -> if (self) HeadLook(10, 12, FROST_SELF_OUTLINE) else HeadLook(8, 10, BLACK)
        NA -> HeadLook(12, 14, BLACK)
        ODIN -> if (self) HeadLook(0, 0, 0, arrow = true) else HeadLook(8, 10, odinClassColor(className))
        LCM -> HeadLook(10, 12, lcmClassColor(className))
    }

    companion object {
        /** The style saved as [name], or Frost's own for anything unrecognized. */
        fun fromName(name: String?): MapStyle = entries.firstOrNull { it.name == name } ?: FROST

        const val BLACK = 0xFF000000.toInt()
        val FROST_SELF_OUTLINE = 0xFF5AC8FA.toInt()

        /** The color player names are written in on Odin's and dtMap's maps. */
        val LEAP_NAME_COLOR = 0xFF464646.toInt()
        const val LEAP_NAME_SCALE = 0.75f
        const val LEAP_NAME_OFFSET = 8

        // Odin: Minecraft's gold, dark red, light purple, aqua and dark green, white for none.
        private fun odinClassColor(className: String?): Int = when (className) {
            "Archer" -> 0xFFFFAA00.toInt()
            "Berserk" -> 0xFFAA0000.toInt()
            "Healer" -> 0xFFFF55FF.toInt()
            "Mage" -> 0xFF55FFFF.toInt()
            "Tank" -> 0xFF00AA00.toInt()
            else -> 0xFFFFFFFF.toInt()
        }

        // dtMap: its own class colors, and its 70% black outline for anyone without one.
        private fun lcmClassColor(className: String?): Int = when (className) {
            "Archer" -> 0xFFFFAA00.toInt()
            "Berserk" -> 0xFFFF5555.toInt()
            "Healer" -> 0xFFFF55FF.toInt()
            "Mage" -> 0xFF55FFFF.toInt()
            "Tank" -> 0xFF00AA00.toInt()
            else -> 0xB3000000.toInt()
        }
    }
}
