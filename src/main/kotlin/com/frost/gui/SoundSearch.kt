package com.frost.gui

/** Searching and showing Minecraft sound IDs, like "minecraft:block.note_block.pling". */
object SoundSearch {
    private const val VANILLA = "minecraft:"
    private val SEPARATORS = Regex("[._:/\\s]+")

    /** The ID as shown: a vanilla sound without its "minecraft:". */
    fun displayName(id: String): String = id.removePrefix(VANILLA)

    /** Whether every word typed is somewhere in the ID, in any order, so "orb pick" finds
     * "entity.experience_orb.pickup". */
    fun matches(id: String, query: String): Boolean {
        val words = query.lowercase().split(SEPARATORS).filter { it.isNotEmpty() }
        val name = id.lowercase()
        return words.all { it in name }
    }

    /** [text] cut from the front with ".." until [width] says it fits in [maxWidth] - the end of a
     * sound's ID says the most about it. */
    fun fitEnd(text: String, maxWidth: Int, width: (String) -> Int): String {
        if (width(text) <= maxWidth) return text
        var start = 1
        while (start < text.length && width(".." + text.substring(start)) > maxWidth) start++
        return ".." + text.substring(start)
    }
}
