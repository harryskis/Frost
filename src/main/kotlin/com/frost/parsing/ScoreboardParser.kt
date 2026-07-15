package com.frost.parsing

/**
 * Parses lines of the sidebar scoreboard shown during a dungeon run. Secrets Found here is
 * a TEAM total (Hypixel doesn't attribute it per player on the scoreboard — per-player
 * secrets come from the Hypixel API snapshot-diff instead, see HypixelApiClient), and
 * "Cleared: 100%" is used as the run-completion signal.
 */
object ScoreboardParser {
    private val SECRETS_FOUND_COUNT = Regex("^ ?Secrets Found: (\\d+)$")
    private val SECRETS_FOUND_PERCENT = Regex("^ ?Secrets Found: [\\d.]+%$")
    private val CLEARED_PERCENT = Regex("^ ?Cleared: (\\d+)% \\(\\d+\\)$")
    private val FLOOR_LINE = Regex("The Catacombs \\((\\w+)\\)")

    fun parseSecretsFoundTeamTotal(line: String): Int? =
        SECRETS_FOUND_COUNT.find(line)?.groupValues?.get(1)?.toIntOrNull()

    fun isSecretsFoundPercentLine(line: String): Boolean = SECRETS_FOUND_PERCENT.matches(line)

    fun parseClearedPercent(line: String): Int? =
        CLEARED_PERCENT.find(line)?.groupValues?.get(1)?.toIntOrNull()

    fun parseFloor(line: String): String? =
        FLOOR_LINE.find(line)?.groupValues?.get(1)
}
