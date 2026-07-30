package com.frost.friends

/** One member line from a Party Finder listing's tooltip - the level is parsed but unused
 * for now (kept since it's already right there in the text; may be useful later). */
data class PartyFinderMember(val username: String, val dungeonClass: DungeonClass, val level: Int)

/** The current party's floor (null if it couldn't be determined) and member/class roster,
 * read from the Party Finder listing marked "You are in this party!". */
data class PartyFinderInfo(val floorLabel: String?, val members: List<PartyFinderMember>)

/**
 * Parses a Party Finder listing item's tooltip lines - confirmed live text (one listing's
 * full tooltip, already newline-split and formatting-stripped by the caller):
 * ```
 * Bdlt's Party
 * Dungeon: Master Mode The Catacombs
 * Floor: Floor V
 * Note: 2
 * Dungeon Level Required: 45
 *
 * Members:
 *  Bdlt: Tank 46
 *  Stormcaller: Berserk 46
 *  Blazehunter: Mage 48
 *  Moonrunner: Healer 49
 *  Empty
 *
 * You are in this party!
 *
 * Missing: Archer
 * ```
 * Only the listing carrying "You are in this party!" is meant to be parsed - Party Finder
 * shows many OTHER groups' listings too (the whole point of the screen is browsing them),
 * and this exact line is the one reliable marker for "this one is mine" (see
 * [com.frost.friends.FriendsChatRelay.scanPartyFinderScreen], which is expected to check
 * every listing and only pass the matching one's lines in here).
 */
object PartyFinderParser {
    private const val YOUR_PARTY_MARKER = "You are in this party!"
    private val DUNGEON_LINE = Regex("^Dungeon: (.+)$")
    private val FLOOR_LINE = Regex("^Floor: Floor ([IVX]+)$")
    private val MEMBER_LINE = Regex("^(\\w{1,16}): ([A-Za-z]+) (\\d+)$")

    private val ROMAN_NUMERALS = mapOf(
        "I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "VII" to 7,
    )

    /** Returns null if [tooltipLines] isn't the player's own party listing (no "You are in
     * this party!" line found) - a floor that couldn't be determined (unrecognized dungeon/
     * floor text) still returns a result with [PartyFinderInfo.floorLabel] null rather than
     * failing the whole parse, since the member list is still useful on its own. */
    fun parseOwnPartyListing(tooltipLines: List<String>): PartyFinderInfo? {
        val trimmedLines = tooltipLines.map { it.trim() }
        if (YOUR_PARTY_MARKER !in trimmedLines) return null

        var dungeonName: String? = null
        var floorRoman: String? = null
        val members = mutableListOf<PartyFinderMember>()

        for (line in trimmedLines) {
            DUNGEON_LINE.find(line)?.let { dungeonName = it.groupValues[1] }
            FLOOR_LINE.find(line)?.let { floorRoman = it.groupValues[1] }
            MEMBER_LINE.find(line)?.let { match ->
                val dungeonClass = DungeonClass.matchByDisplayName(match.groupValues[2]) ?: return@let
                val level = match.groupValues[3].toIntOrNull() ?: return@let
                members.add(PartyFinderMember(match.groupValues[1], dungeonClass, level))
            }
        }

        return PartyFinderInfo(floorLabel(dungeonName, floorRoman), members)
    }

    private fun floorLabel(dungeonName: String?, floorRoman: String?): String? {
        val floorNumber = floorRoman?.let { ROMAN_NUMERALS[it] } ?: return null
        val isMaster = dungeonName?.contains("Master Mode", ignoreCase = true) == true
        return (if (isMaster) "M" else "F") + floorNumber
    }
}
