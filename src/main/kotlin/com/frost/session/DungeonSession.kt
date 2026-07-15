package com.frost.session

import com.frost.dungeon.GridPos
import com.frost.parsing.PuzzleStatus
import com.frost.parsing.PuzzleStatusUpdate

enum class SessionState {
    IDLE,
    ACTIVE,
    ENDED,
}

/**
 * Holds all state for a single dungeon run, from the "Good luck." start line
 * through the end-of-run summary. A fresh instance is created for every run.
 */
class DungeonSession {
    var state: SessionState = SessionState.IDLE
        private set

    var floor: String? = null
    val party: MutableMap<String, PartyMember> = LinkedHashMap()

    // secrets_found via Hypixel API, keyed by username
    val secretsBaseline: MutableMap<String, Int> = HashMap()
    val secretsFinal: MutableMap<String, Int> = HashMap()

    // puzzle solves attributed via chat/tablist, keyed by username
    val puzzlesSolved: MutableMap<String, Int> = HashMap()

    // Which rooms each player cleared, split the same way Noamm's mod does it: SOLO
    // (exactly one player present when it cleared) vs STACK (multiple present) - a real
    // per-room list instead of a single blended fractional number. Each entry is the SET of
    // grid tiles the room occupies (a room can span several) rather than a resolved name -
    // the world-block scanner often identifies a room's name AFTER it's already been
    // cleared (a fast team clears the checkmark before anyone's close enough to scan it),
    // so the name is resolved lazily at display time from whatever's been identified by
    // then, keyed off any tile in this set.
    val soloClearedRooms: MutableMap<String, MutableList<Set<GridPos>>> = HashMap()
    val stackClearedRooms: MutableMap<String, MutableList<Set<GridPos>>> = HashMap()

    // dungeon class name (e.g. "Healer", "Mage") per username, from the tab list's roster
    // panel - shown next to each player's name in the summary since it's useful context
    // (a Mage isn't expected to rack up secrets/rooms the way other classes are).
    val playerClass: MutableMap<String, String> = HashMap()

    var teamRoomsCleared: Int = 0
        private set
    var teamRoomsTotal: Int? = null
    var teamSecretsFound: Int? = null

    // The dungeon's real total puzzle count, from the tab list's "Puzzles: (N)" line - a
    // hard ceiling on total credits, since chat credits and the tab-list presence fallback
    // are independent paths that can otherwise both fire for the same real completion
    // (confirmed on a live run: 3 real puzzles ended up with 4 total credits).
    var totalPuzzles: Int? = null

    // last-seen status per puzzle name, used to only count a COMPLETED transition once
    val lastPuzzleStatus: MutableMap<String, PuzzleStatus> = HashMap()

    fun start(partyMembers: Collection<PartyMember>) {
        state = SessionState.ACTIVE
        floor = null
        party.clear()
        secretsBaseline.clear()
        secretsFinal.clear()
        puzzlesSolved.clear()
        soloClearedRooms.clear()
        stackClearedRooms.clear()
        playerClass.clear()
        teamRoomsCleared = 0
        teamRoomsTotal = null
        teamSecretsFound = null
        totalPuzzles = null
        lastPuzzleStatus.clear()
        partyMembers.forEach { party[it.username] = it }
    }

    fun isActive(): Boolean = state == SessionState.ACTIVE

    fun totalPuzzlesCredited(): Int = puzzlesSolved.values.sum()

    /** Returns false (and records nothing) once [totalPuzzles] credits have already been
     * handed out - the run's real puzzle count is a hard ceiling no credit path may exceed. */
    fun recordPuzzleSolved(username: String): Boolean {
        if (!isActive()) return false
        val cap = totalPuzzles
        if (cap != null && totalPuzzlesCredited() >= cap) return false
        puzzlesSolved[username] = (puzzlesSolved[username] ?: 0) + 1
        return true
    }

    sealed class PuzzleUpdateResult {
        object NoOp : PuzzleUpdateResult()
        data class CreditedNamed(val username: String) : PuzzleUpdateResult()

        /** Hypixel didn't attach a solver name; caller should fall back to presence-based credit. */
        object NeedsPresenceFallback : PuzzleUpdateResult()
    }

    /** Only credits a puzzle the tick it transitions INTO completed, not on every re-poll. */
    fun handlePuzzleUpdate(update: PuzzleStatusUpdate): PuzzleUpdateResult {
        if (!isActive()) return PuzzleUpdateResult.NoOp
        val previous = lastPuzzleStatus[update.puzzleName]
        lastPuzzleStatus[update.puzzleName] = update.status
        if (update.status != PuzzleStatus.COMPLETED || previous == PuzzleStatus.COMPLETED) {
            return PuzzleUpdateResult.NoOp
        }
        val solver = update.solvedBy
        return if (solver != null) {
            recordPuzzleSolved(solver)
            PuzzleUpdateResult.CreditedNamed(solver)
        } else {
            PuzzleUpdateResult.NeedsPresenceFallback
        }
    }

    /** Records a room clear against every player present, filed as SOLO if exactly one
     * player was there or STACK if there were multiple - always increments
     * [teamRoomsCleared] regardless (that still counts every clear, credited or not).
     * [roomTiles] is the set of grid tiles the room occupies, resolved to a name later. */
    fun recordRoomCleared(playersPresent: Collection<String>, roomTiles: Set<GridPos>) {
        if (!isActive()) return
        teamRoomsCleared++
        if (playersPresent.isEmpty()) return
        val target = if (playersPresent.size == 1) soloClearedRooms else stackClearedRooms
        for (username in playersPresent) {
            target.getOrPut(username) { mutableListOf() }.add(roomTiles)
        }
    }

    /** Marks the run as finished; secrets diffing still needs [secretsFinal] populated by the caller. */
    fun end() {
        if (state != SessionState.ACTIVE) return
        state = SessionState.ENDED
    }

    fun secretsFoundThisRun(username: String): Int? {
        val start = secretsBaseline[username] ?: return null
        val end = secretsFinal[username] ?: return null
        return (end - start).coerceAtLeast(0)
    }
}
