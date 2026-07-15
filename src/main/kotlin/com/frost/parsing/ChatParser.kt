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

    fun isDungeonStartMessage(rawText: String): Boolean = DUNGEON_START.matches(rawText.trim())
    fun isBossDefeatedMessage(rawText: String): Boolean = BOSS_DEFEATED.matches(rawText.trim())
    fun parsePuzzleSolvedSolver(rawText: String): String? = PUZZLE_SOLVED_CHAT.find(rawText.trim())?.groupValues?.get(1)
    fun parseQuizBuffFinder(rawText: String): String? = QUIZ_BUFF_FOUND.find(rawText.trim())?.groupValues?.get(1)
}
