package com.frost.parsing

enum class PuzzleStatus {
    INCOMPLETE,
    COMPLETED,
    FAILED,
}

data class PuzzleStatusUpdate(val puzzleName: String, val status: PuzzleStatus, val solvedBy: String?)

/**
 * Parses the puzzle status panel entries of the in-dungeon tab list. Cross-referenced
 * against known open-source Hypixel dungeon mods' tab list regexes rather than guessed
 * from scratch, but tab list formatting is one of the more fragile surfaces Hypixel can
 * tweak — verify against a live run.
 */
object TabListParser {
    // e.g. "Water Board: [✔] (Steve)" or "Higher Or Lower: [✔]" (real entries can carry
    // extra trailing whitespace Hypixel pads the tab list with - trim before matching).
    private val PUZZLE_LINE = Regex("^(\\w+(?: \\w+)*|\\?\\?\\?): \\[([✖✔✦])]\\s*(?:\\((\\w+)\\))?$")

    fun parsePuzzleLine(line: String): PuzzleStatusUpdate? {
        val match = PUZZLE_LINE.find(line.trim()) ?: return null
        val (name, symbol, player) = match.destructured
        val status = when (symbol) {
            "✔" -> PuzzleStatus.COMPLETED // ✔
            "✖" -> PuzzleStatus.FAILED // ✖
            else -> PuzzleStatus.INCOMPLETE // ✦
        }
        return PuzzleStatusUpdate(name, status, player.ifBlank { null })
    }

    // e.g. "Puzzles: (3)" - the dungeon's TOTAL puzzle count for the run, confirmed to stay
    // constant the whole time (not a completed/remaining counter). Used as a hard ceiling
    // on total credited puzzles, since multiple independent credit paths (chat, tab-list
    // presence fallback) can otherwise double-count the same real completion.
    private val PUZZLE_COUNT = Regex("Puzzles: \\((\\d+)\\)")

    fun parsePuzzleCount(line: String): Int? = PUZZLE_COUNT.find(line)?.groupValues?.get(1)?.toIntOrNull()

    // e.g. "[417] Bdlt ⛃ (Healer XLIII)" - the party roster panel's per-player line,
    // giving a username and their current dungeon class in one place. Requires the
    // leading "[level] " and the ⛃ dungeon icon specifically (not just any parenthesized
    // roman numeral) since Hypixel's tab list carries other decorative/fake entries that
    // could otherwise false-match a looser pattern.
    private val PLAYER_CLASS_LINE = Regex("^\\[\\d+]\\s+(\\w+)\\s+⛃\\s*\\((\\w+)\\s+[IVXLCDM]+\\)$")

    data class PlayerClassUpdate(val username: String, val className: String)

    fun parsePlayerClassLine(line: String): PlayerClassUpdate? {
        val match = PLAYER_CLASS_LINE.find(line.trim()) ?: return null
        val (username, className) = match.destructured
        return PlayerClassUpdate(username, className)
    }

    /** Single-letter abbreviation for a dungeon class name, e.g. "Healer" -> 'H'. */
    fun classLetter(className: String): Char? = when (className) {
        "Healer" -> 'H'
        "Mage" -> 'M'
        "Berserk" -> 'B' // tab list shows "Berserk", not the full "Berserker"
        "Archer" -> 'A'
        "Tank" -> 'T'
        else -> null
    }
}
