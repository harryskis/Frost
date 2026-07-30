package com.frost.friends

/**
 * Builds the "Join my [floor] party! We are [x]/5 missing [classes]." message sent when a
 * friend's invite icon is clicked - [filledClasses] is whichever classes the current
 * recruiting party already has (the user included), so the party size and the missing list
 * are both derived from the same set rather than tracked separately.
 */
object PartyInviteMessage {
    fun build(floorLabel: String, filledClasses: Set<DungeonClass>): String {
        val partySize = filledClasses.size
        val missing = DungeonClass.entries.filterNot { it in filledClasses }
        return if (missing.isEmpty()) {
            "Join my $floorLabel party! We are $partySize/5."
        } else {
            val missingText = if (missing.size == 1) {
                missing[0].abbreviation
            } else {
                missing.dropLast(1).joinToString(", ") { it.abbreviation } + " and " + missing.last().abbreviation
            }
            "Join my $floorLabel party! We are $partySize/5 missing $missingText."
        }
    }
}
