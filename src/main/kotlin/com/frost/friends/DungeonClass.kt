package com.frost.friends

/**
 * The 5 dungeon classes, in the order the /pb-style [com.frost.dungeon.DungeonFloor] and
 * party-invite features expect them listed in - this specific order is what makes a
 * multi-class "missing" list (see [PartyInviteMessage]) read the same way every time.
 */
enum class DungeonClass(val displayName: String, val abbreviation: String) {
    HEALER("Healer", "HEAL"),
    MAGE("Mage", "MAGE"),
    TANK("Tank", "TANK"),
    BERSERKER("Berserker", "BERS"),
    ARCHER("Archer", "ARCH"),
    ;

    companion object {
        /** Parses a comma/space-separated list of class names or abbreviations (either
         * case) from /frost recruit's command argument - e.g. "heal,tank" or "Healer Tank".
         * Returns the classes actually recognized, plus any tokens that weren't (so the
         * caller can tell the player what got ignored instead of silently dropping it). */
        fun parseList(raw: String): Pair<Set<DungeonClass>, List<String>> {
            val tokens = raw.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
            val filled = mutableSetOf<DungeonClass>()
            val unknown = mutableListOf<String>()
            for (token in tokens) {
                val match = matchByDisplayName(token) ?: entries.find { it.abbreviation.equals(token, ignoreCase = true) }
                if (match != null) filled.add(match) else unknown.add(token)
            }
            return filled to unknown
        }

        /** Matches a single class name as it appears in live game text (Party Finder
         * tooltips, the party-join chat message) - not abbreviations, which never show up
         * there. "Berserk" is accepted alongside "Berserker": confirmed live in a Party
         * Finder tooltip that Hypixel actually shows the shortened form, matching what the
         * tab list was already known to do (see TabListParser.classLetter). */
        fun matchByDisplayName(name: String): DungeonClass? = when (name.trim().lowercase()) {
            "healer" -> HEALER
            "mage" -> MAGE
            "tank" -> TANK
            "berserker", "berserk" -> BERSERKER
            "archer" -> ARCHER
            else -> null
        }
    }
}
