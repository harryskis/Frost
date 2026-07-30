package com.frost.friends

/** A friend logging on or off, from Hypixel's own live notification - not part of the
 * `/fl` page output itself, a separate broadcast that can arrive any time. */
data class FriendPresenceChange(val username: String, val online: Boolean)

/**
 * Parses Hypixel's friend online/offline notification - confirmed live text: "Friend > HauTF
 * joined." / "Friend > HauTF left." Used to keep [FriendsChatRelay]'s cache fresh between
 * `/fl` views instead of only ever updating it when a page is actually viewed.
 */
object FriendPresenceParser {
    private val PATTERN = Regex("^Friend > (\\w{1,16}) (joined|left)\\.$")

    fun parse(text: String): FriendPresenceChange? {
        val match = PATTERN.find(text.trim()) ?: return null
        val username = match.groupValues[1]
        val online = match.groupValues[2] == "joined"
        return FriendPresenceChange(username, online)
    }
}
