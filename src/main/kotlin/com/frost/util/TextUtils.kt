package com.frost.util

/**
 * Hypixel sends a lot of text (chat, scoreboard, tab list) as legacy-formatted strings
 * where `§` color/style codes are embedded directly in the text content rather than as
 * separate Style data, so `Text.getString()` returns them inline (e.g. `"§e[NPC] §bMort§f:
 * Good luck."`). Every parser in this mod expects plain text, so strip codes at the one
 * point text leaves the Minecraft API rather than in each regex.
 */
object TextUtils {
    private val FORMATTING_CODE = Regex("§[0-9a-fk-or]", RegexOption.IGNORE_CASE)

    fun stripFormatting(raw: String): String = raw.replace(FORMATTING_CODE, "")

    /** Whole numbers print without a trailing ".0" (e.g. "3" not "3.0"); anything else
     * keeps its exact value's default string form. Shared between the room-weights config
     * screen and the weighted-score chat line so both read the same way. */
    fun formatWeight(weight: Double): String =
        if (weight == weight.toLong().toDouble()) weight.toLong().toString() else weight.toString()
}
