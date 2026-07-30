package com.frost.friends

import com.frost.util.TextUtils

/** One line of Hypixel's own `/friend list` chat output, before any tagging is applied.
 * [isOnline] is set from which parser matched the line ([parseLine] vs [parseFallbackLine]),
 * not by inspecting the status text - more robust than checking whether [status] happens to
 * read "Offline", since the real offline line format isn't fully confirmed yet. [nameColor] is
 * the legacy color code (e.g. '6', 'b', 'a', '7') Hypixel itself rendered this friend's
 * username in - their rank color - read off the message's raw formatting codes before they get
 * stripped (see [FriendsParser.parseMessage]); null if none could be found (e.g. in tests that
 * construct a [FriendEntry] directly from plain text with no codes at all). */
data class FriendEntry(val username: String, val status: String, val isOnline: Boolean = true, val nameColor: Char? = null)

/**
 * Parses Hypixel's `/friend list` chat output (confirmed against a live screenshot: a
 * dashed "Friends (Page X of Y)" header line, then one "<username> is in <status>" line per
 * online friend). Used to both learn how many pages exist and to collect every friend line
 * across all of them - see [com.frost.friends.FriendsFetcher].
 */
object FriendsParser {
    private val HEADER = Regex("Friends \\(Page (\\d+) of (\\d+)\\)")

    // Only real color codes (0-9a-f), not bold/italic/etc modifier codes (k-o) or reset (r) -
    // a modifier doesn't change what color the text actually renders in, so it shouldn't be
    // mistaken for one.
    private val COLOR_CODE = Regex("§([0-9a-f])", RegexOption.IGNORE_CASE)

    // Deliberately narrow (must contain the literal "is in" right after the username) rather
    // than a loose "first word, rest of line" fallback: this gets checked against every
    // incoming chat/game message while a fetch is in progress, and a broad pattern risks
    // swallowing unrelated chat that happens to arrive in that same window.
    private val LINE = Regex("^(\\w{1,16}) is in (.+)$")

    /** Returns (currentPage, totalPages) if [text] is the friends-list page header, else null. */
    fun parseHeader(text: String): Pair<Int, Int>? {
        val match = HEADER.find(text) ?: return null
        val current = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null
        return current to total
    }

    /** Returns one friend's roster line ("Username is in <status>"), or null if [text]
     * doesn't look like one - e.g. an offline friend's line, whose exact format hasn't been
     * confirmed yet, or an unrelated chat message. */
    fun parseLine(text: String): FriendEntry? {
        val match = LINE.find(text.trim()) ?: return null
        return FriendEntry(match.groupValues[1], match.groupValues[2], isOnline = true)
    }

    /** Fallback for anything that isn't the confirmed online "is in" pattern - covers
     * offline friends, whose exact Hypixel line format isn't confirmed yet. Deliberately
     * more permissive than [parseLine] (just "first token as username, rest as status"),
     * which is only safe because [com.frost.friends.FriendsChatRelay] only calls this
     * inside a short window right after the player's own /fl or /friend list command, not
     * on arbitrary chat. */
    fun parseFallbackLine(text: String): FriendEntry? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        val username = parts[0]
        if (!username.matches(Regex("\\w{1,16}"))) return null
        return FriendEntry(username, if (parts.size > 1) parts[1] else "Offline", isOnline = false)
    }

    /** Parses a whole incoming chat/game message that may contain the header, one or more
     * friend lines, or both, joined by embedded newlines - a live test confirmed Hypixel
     * sends a full friends-list page this way, header and every friend line together in
     * ONE message, not one message per line, which is why this splits first rather than
     * treating [text] as a single line.
     *
     * [parseFallbackLine] is only tried on a line when [text] ALSO contains the header
     * somewhere in it - a live test showed unrelated messages arriving in the same capture
     * window (a guild member joining, for instance) were getting misread as an "offline
     * friend" line, since that fallback is deliberately permissive (just "first word looks
     * like a username"). Since the confirmed format bundles header+body into one message,
     * requiring the header's presence to unlock the fallback keeps it scoped to an actual
     * friends-list page instead of arbitrary chat that happens to arrive at the same time.
     * [parseLine] (the strict "is in" pattern) still applies to every message regardless,
     * in case a future page ever does arrive split across more than one message. */
    fun parseMessage(text: String): Pair<Pair<Int, Int>?, List<FriendEntry>> {
        val rawLines = text.split("\n")
        val strippedLines = rawLines.map { TextUtils.stripFormatting(it) }
        val header = strippedLines.firstNotNullOfOrNull { parseHeader(it) }
        val entries = mutableListOf<FriendEntry>()
        for (i in rawLines.indices) {
            val line = strippedLines[i]
            if (parseHeader(line) != null) continue
            val entry = parseLine(line) ?: (if (header != null) parseFallbackLine(line) else null)
            if (entry != null) entries.add(entry.copy(nameColor = extractNameColor(rawLines[i])))
        }
        return header to entries
    }

    /** Reads the username's rank color straight off [rawLine]'s own formatting codes, before
     * [TextUtils.stripFormatting] (applied everywhere else in this mod) throws that
     * information away. Returns the first real color code found anywhere in the line -
     * in every confirmed live sample the username is the very first thing printed, so its
     * color code is naturally the first one encountered; null if the line carries no color
     * codes at all (plain-text test fixtures, or a format Hypixel changed). */
    private fun extractNameColor(rawLine: String): Char? =
        COLOR_CODE.find(rawLine)?.groupValues?.get(1)?.lowercase()?.firstOrNull()
}
