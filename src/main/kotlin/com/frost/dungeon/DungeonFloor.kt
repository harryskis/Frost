package com.frost.dungeon

/**
 * Parses a /pb command's floor argument ("f1".."f7" for normal Catacombs, "m1".."m7" for
 * Master Mode, case-insensitive) into the pieces needed to look a personal-best time up in
 * Hypixel's API and display it back: which `dungeon_types` bucket it lives under, the
 * floor's key within that bucket's per-floor record maps ("1".."7"), and a canonical
 * display label.
 */
object DungeonFloor {
    private val ROMAN = mapOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "VII" to 7)

    /** The floor number in the scoreboard's floor, like "M6", "VII" or "F5" - null for the entrance
     * ("E") or anything else. Normal and Master Mode come out the same. */
    fun scoreboardNumber(raw: String): Int? =
        ROMAN[raw] ?: raw.takeIf { it.startsWith("M") || it.startsWith("F") }?.substring(1)?.toIntOrNull()

    data class Parsed(val dungeonType: String, val floorIndex: String, val label: String)

    fun parse(raw: String): Parsed? {
        val trimmed = raw.trim()
        if (trimmed.length < 2) return null
        val prefix = trimmed[0].uppercaseChar()
        val number = trimmed.substring(1).toIntOrNull() ?: return null
        if (number !in 1..7) return null
        return when (prefix) {
            'F' -> Parsed("catacombs", number.toString(), "F$number")
            'M' -> Parsed("master_catacombs", number.toString(), "M$number")
            else -> null
        }
    }
}
