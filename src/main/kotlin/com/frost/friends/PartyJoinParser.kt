package com.frost.friends

/** A player joining your party through the party finder, with the dungeon class they
 * queued as. */
data class PartyJoin(val username: String, val dungeonClass: DungeonClass, val level: Int)

/**
 * Parses Hypixel's party-finder join broadcast - confirmed live text: "Party Finder > Bdlt
 * joined the dungeon group! (Tank Level 45)" - used to auto-fill [FriendsChatRelay]'s
 * recruit state as party members actually join, instead of only ever setting it by hand via
 * /frost recruit.
 */
object PartyJoinParser {
    private val PATTERN = Regex("^Party Finder > (\\w{1,16}) joined the dungeon group! \\(([A-Za-z]+) Level (\\d+)\\)$")

    fun parse(text: String): PartyJoin? {
        val match = PATTERN.find(text.trim()) ?: return null
        val username = match.groupValues[1]
        val dungeonClass = DungeonClass.matchByDisplayName(match.groupValues[2]) ?: return null
        val level = match.groupValues[3].toIntOrNull() ?: return null
        return PartyJoin(username, dungeonClass, level)
    }
}
