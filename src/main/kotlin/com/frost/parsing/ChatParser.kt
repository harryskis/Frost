package com.frost.parsing

/**
 * Regex-based handlers for plain chat lines. Kept intentionally small: puzzle attribution
 * and secrets/room state come from the tab list and scoreboard instead (see
 * [TabListParser]/[ScoreboardParser]), since Hypixel doesn't broadcast those to chat.
 */
object ChatParser {
    // Sent by the NPC "Mort" the moment the dungeon door opens and the run officially starts.
    private val DUNGEON_START = Regex("^\\[NPC] Mort: Good luck\\.$")

    // Sent the exact moment the floor boss dies - confirmed against a live run
    // ("☠ Defeated Livid in 02m 17s"). This is the real end-of-run signal: "Cleared: 100%"
    // on the scoreboard fires when rooms hit 100%, which is well before the boss fight
    // even starts, and boss fight length varies a lot by floor (M5 ~31s, others ~3min).
    private val BOSS_DEFEATED = Regex("^☠ Defeated .+ in .+$")

    // Sent to the whole party when certain puzzle types are solved, with the solver's name
    // - confirmed on a live run ("PUZZLE SOLVED! Bdlt wasn't fooled by Melrose! Good job!",
    // "PUZZLE SOLVED! Bdlt tied Tic Tac Toe! Good job!"). More reliable than the tab list's
    // "(PlayerName)" suffix, which isn't present for every puzzle type. Not every puzzle
    // sends this message either, so this is an additional signal, not a full replacement.
    private val PUZZLE_SOLVED_CHAT = Regex("^PUZZLE SOLVED! (\\w+) .+$")

    // The Quiz puzzle doesn't send a "PUZZLE SOLVED!" message and its room often turns
    // green after the solver has already walked out (it has a lot of trailing dialogue),
    // so neither the chat nor the room-presence signal catches it reliably. "Blessing of
    // Time" specifically is the unique Quiz reward - confirmed on a live run ("DUNGEON
    // BUFF! RemDoggyDog found a Blessing of Time V!"). Blessings of Wisdom/Life/Stone/Power
    // etc. are NOT puzzle-related at all - they're common pickups scattered throughout the
    // whole dungeon (confirmed: a single run produced 12+ of those, one per pickup, which
    // is why matching "found a .+!" broadly caused massive over-crediting).
    private val QUIZ_BUFF_FOUND = Regex("^DUNGEON BUFF! (\\w+) found a Blessing of Time [IVXLCDM]+!.*$")

    // Sent after a spirit leap - confirmed on a live run ("You have teleported to weepcore!").
    private val LEAP_TELEPORT = Regex("^You have teleported to (\\w{1,16})!$")

    fun isDungeonStartMessage(rawText: String): Boolean = DUNGEON_START.matches(rawText.trim())
    fun isBossDefeatedMessage(rawText: String): Boolean = BOSS_DEFEATED.matches(rawText.trim())

    /** The heading of the summary shown when a run is finished. Matched whole, so a player can't
     * trigger it by typing it, since their name would come first. */
    fun isRunSummaryMessage(rawText: String): Boolean = rawText.trim() == "> EXTRA STATS <"

    fun isBloodDoorOpenedMessage(rawText: String): Boolean = rawText.trim() == "The BLOOD DOOR has been opened!"

    fun parsePuzzleSolvedSolver(rawText: String): String? = PUZZLE_SOLVED_CHAT.find(rawText.trim())?.groupValues?.get(1)
    fun parseQuizBuffFinder(rawText: String): String? = QUIZ_BUFF_FOUND.find(rawText.trim())?.groupValues?.get(1)
    fun parseLeapTarget(rawText: String): String? = LEAP_TELEPORT.find(rawText.trim())?.groupValues?.get(1)

    // A door key being picked up comes as "[MVP+] Name has obtained Wither Key!", or "A Wither
    // Key was picked up!" - the same patterns Odin and dtMap go by. Anchored at the start, so
    // a player typing either can't set it off, as their own name would come first.
    private val KEY_OBTAINED = Regex("^(?:\\[[^]]*?] ?)?\\w{1,16} has obtained (Wither|Blood) Key!$")
    private val KEY_PICKED_UP = Regex("^A (Wither|Blood) Key was picked up!$")

    /** Which key a chat line says was picked up, or null if it isn't one. */
    fun parseKeyPickup(rawText: String): DungeonKey? {
        val text = rawText.trim()
        val type = (KEY_OBTAINED.find(text) ?: KEY_PICKED_UP.find(text))?.groupValues?.get(1) ?: return null
        return if (type == "Blood") DungeonKey.BLOOD else DungeonKey.WITHER
    }
}

/** Prince kills: Hypixel's own line, seen by whoever lands the kill, and the party messages the
 * common mods send for everyone else - matched whole, the way Odin and NoammAddons do, so ordinary
 * chat that happens to mention a prince doesn't count. */
object PrinceKill {
    private const val HYPIXEL_LINE = "A Prince falls. +1 Bonus Score"
    private val PARTY_MESSAGE = Regex("^Party > (?:\\[[^]]+] )*\\w{1,16}: (.+)$")
    private val MOD_MESSAGES = setOf(
        "prince killed", "prince killed!", "prince slain", "prince dead", "prince dead!", "\$skytils-dungeon-score-prince\$",
    )

    fun isKill(rawText: String): Boolean = isOwnKill(rawText) || partySays(rawText, MOD_MESSAGES)

    /** Hypixel's own line, which only the player who killed the prince sees. */
    fun isOwnKill(rawText: String): Boolean = rawText.trim() == HYPIXEL_LINE

    /** Whether [rawText] is a party message saying exactly one of [messages], in any case. */
    fun partySays(rawText: String, messages: Set<String>): Boolean {
        val said = PARTY_MESSAGE.find(rawText.trim())?.groupValues?.get(1) ?: return false
        return said.trim().lowercase() in messages
    }
}

/** Mimic kills. The Mimic has no chat line of its own when it dies, so [com.frost.dungeon.Mimic]
 * spots its death in the world; this is the charm message for killing it with a charm, and the
 * party messages other mods send, from NoammAddons' list. */
object MimicKill {
    private val CHARM = Regex("^CHARM You charmed a Mimic and captured (?:\\d+ shards from it|its Shard)\\.$", RegexOption.IGNORE_CASE)
    private val MOD_MESSAGES = setOf(
        "mimic killed", "mimic killed!", "mimic slain", "mimic dead", "mimic dead!", "\$skytils-dungeon-score-mimic\$",
        "child destroyed!", "mimic obliterated!", "mimic exorcised!", "mimic destroyed!", "mimic annhilated!",
        "breefing killed", "breefing dead",
    )

    fun isCharmed(rawText: String): Boolean = CHARM.matches(rawText.trim())

    /** A party member - or this player, coming back through party chat - has said the Mimic is dead. */
    fun isAnnounced(rawText: String): Boolean = PrinceKill.partySays(rawText, MOD_MESSAGES)
}

/** Hypixel's line for killing a bat, which only the player who killed it sees. */
object BatKill {
    fun isOwnKill(rawText: String): Boolean = rawText.trim() == "A Bat has been slain. +1 Bonus Score"
}

/** The two keys that open a dungeon's doors. */
enum class DungeonKey(val label: String) {
    WITHER("Wither Key"),
    BLOOD("Blood Key"),
}
