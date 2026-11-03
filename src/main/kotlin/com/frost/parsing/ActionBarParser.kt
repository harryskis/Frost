package com.frost.parsing

import com.frost.util.TextUtils

/** Parses the action bar, where Hypixel shows live stats for the room the player is in. */
object ActionBarParser {
    // e.g. "§7 3/10 Secrets" - one segment among health, mana and so on.
    private val ROOM_SECRETS = Regex("(\\d+)/(\\d+) Secrets")

    /** The (found, total) secret count for the player's current room, if the bar shows one. */
    fun parseRoomSecrets(actionBar: String): Pair<Int, Int>? {
        val match = ROOM_SECRETS.find(TextUtils.stripFormatting(actionBar)) ?: return null
        val found = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null
        return found to total
    }
}
