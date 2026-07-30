package com.frost.util

/**
 * Hypixel sends a lot of text (chat, scoreboard, tab list) as legacy-formatted strings
 * where `§` color/style codes are embedded directly in the text content rather than as
 * separate Style data, so `Text.getString()` returns them inline (e.g. `"§e[NPC] §bMort§f:
 * Good luck."`). Every parser in this mod expects plain text, so strip codes at the one
 * point text leaves the Minecraft API rather than in each regex.
 */
object TextUtils {
    /** The consistent tag every local (never sent to other players - real chat can't carry
     * §-codes anyway) status/confirmation/error message from this mod leads with. Shared
     * here rather than duplicated per call site so every feature's output looks the same. */
    const val FROST_TAG = "§b§l[Frost]§r "

    private val FORMATTING_CODE = Regex("§[0-9a-fk-or]", RegexOption.IGNORE_CASE)

    fun stripFormatting(raw: String): String = raw.replace(FORMATTING_CODE, "")

    /** Whole numbers print without a trailing ".0" (e.g. "3" not "3.0"); anything else
     * keeps its exact value's default string form. Shared between the room-weights config
     * screen and the weighted-score chat line so both read the same way. */
    fun formatWeight(weight: Double): String =
        if (weight == weight.toLong().toDouble()) weight.toLong().toString() else weight.toString()

    /** Formats a Hypixel dungeon time (milliseconds, as returned by the API's
     * fastest_time/fastest_time_s_plus fields) as "m:ss", or "h:mm:ss" past one hour - the
     * same format Hypixel's own UI and other dungeon mods show run/PB times in. */
    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
