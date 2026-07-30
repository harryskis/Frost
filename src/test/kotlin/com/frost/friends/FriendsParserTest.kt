package com.frost.friends

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FriendsParserTest {
    @Test
    fun `parses the page header regardless of surrounding decoration`() {
        val text = "--------------------- Friends (Page 1 of 15) >>----------------------"
        assertEquals(1 to 15, FriendsParser.parseHeader(text))
    }

    @Test
    fun `parses a friend line with a simple location`() {
        assertEquals(
            FriendEntry("Ducky_", "SkyBlock - Private Island"),
            FriendsParser.parseLine("Ducky_ is in SkyBlock - Private Island"),
        )
    }

    @Test
    fun `parses a friend line with a dungeon floor location`() {
        assertEquals(
            FriendEntry("Nightowl3", "SkyBlock - The Catacombs - Floor VII"),
            FriendsParser.parseLine("Nightowl3 is in SkyBlock - The Catacombs - Floor VII"),
        )
    }

    @Test
    fun `parses a friend line with a master mode floor location`() {
        assertEquals(
            FriendEntry("Pixelbear7", "SkyBlock - Master Mode The Catacombs - [MM] Floor VII"),
            FriendsParser.parseLine("Pixelbear7 is in SkyBlock - Master Mode The Catacombs - [MM] Floor VII"),
        )
    }

    @Test
    fun `parses a friend line with a one-word location`() {
        assertEquals(FriendEntry("Emberfox", "Limbo"), FriendsParser.parseLine("Emberfox is in Limbo"))
    }

    @Test
    fun `does not match an unrelated sentence`() {
        assertNull(FriendsParser.parseLine("Anyone else think this run is easy?"))
    }

    @Test
    fun `does not match a bare username with no status`() {
        assertNull(FriendsParser.parseLine("Ducky_"))
    }

    @Test
    fun `header parsing does not match a friend line`() {
        assertNull(FriendsParser.parseHeader("Ducky_ is in SkyBlock - Private Island"))
    }

    @Test
    fun `fallback parses a bare username with no status as offline`() {
        assertEquals(FriendEntry("SomeFriend", "Offline", isOnline = false), FriendsParser.parseFallbackLine("SomeFriend"))
    }

    @Test
    fun `fallback keeps whatever text follows the username as the status`() {
        assertEquals(
            FriendEntry("SomeFriend", "Last seen 3 days ago", isOnline = false),
            FriendsParser.parseFallbackLine("SomeFriend Last seen 3 days ago"),
        )
    }

    @Test
    fun `fallback also matches the online format, just without splitting out is in`() {
        assertEquals(
            FriendEntry("Ducky_", "is in SkyBlock - Private Island", isOnline = false),
            FriendsParser.parseFallbackLine("Ducky_ is in SkyBlock - Private Island"),
        )
    }

    @Test
    fun `fallback rejects an empty line`() {
        assertNull(FriendsParser.parseFallbackLine(""))
        assertNull(FriendsParser.parseFallbackLine("   "))
    }

    @Test
    fun `parseMessage extracts the header and every friend line from one multi-line message`() {
        val text = listOf(
            "--------------------- Friends (Page 1 of 15) >>----------------------",
            "Ducky_ is in SkyBlock - Private Island",
            "Fluffytail is in SkyBlock - Crimson Isle",
            "Emberfox is in Limbo",
        ).joinToString("\n")

        val (header, entries) = FriendsParser.parseMessage(text)

        assertEquals(1 to 15, header)
        assertEquals(
            listOf(
                FriendEntry("Ducky_", "SkyBlock - Private Island"),
                FriendEntry("Fluffytail", "SkyBlock - Crimson Isle"),
                FriendEntry("Emberfox", "Limbo"),
            ),
            entries,
        )
    }

    @Test
    fun `parseMessage also works when each line arrives as its own separate message`() {
        val (header1, entries1) = FriendsParser.parseMessage("--- Friends (Page 1 of 15) >>---")
        val (header2, entries2) = FriendsParser.parseMessage("Ducky_ is in SkyBlock - Private Island")

        assertEquals(1 to 15, header1)
        assertEquals(emptyList(), entries1)
        assertNull(header2)
        assertEquals(listOf(FriendEntry("Ducky_", "SkyBlock - Private Island")), entries2)
    }

    @Test
    fun `parseMessage falls back to the offline format for lines that do not match is in`() {
        val text = listOf(
            "--- Friends (Page 1 of 15) >>---",
            "Ducky_ is in SkyBlock - Private Island",
            "OfflineFriend",
        ).joinToString("\n")

        val (_, entries) = FriendsParser.parseMessage(text)

        assertEquals(
            listOf(
                FriendEntry("Ducky_", "SkyBlock - Private Island"),
                FriendEntry("OfflineFriend", "Offline", isOnline = false),
            ),
            entries,
        )
    }

    @Test
    fun `parseMessage reads a friend's rank color off the raw formatting codes`() {
        val text = listOf(
            "--- Friends (Page 1 of 15) >>---",
            "§6Ducky_§7 is in SkyBlock - Private Island",
        ).joinToString("\n")

        val (_, entries) = FriendsParser.parseMessage(text)

        assertEquals('6', entries.single().nameColor)
    }

    @Test
    fun `parseMessage skips bold-italic-etc modifier codes when finding the rank color`() {
        val text = listOf(
            "--- Friends (Page 1 of 15) >>---",
            "§l§bDucky_§r§7 is in SkyBlock - Private Island",
        ).joinToString("\n")

        val (_, entries) = FriendsParser.parseMessage(text)

        assertEquals('b', entries.single().nameColor)
    }

    @Test
    fun `parseMessage leaves nameColor null when the line has no color codes at all`() {
        val text = listOf(
            "--- Friends (Page 1 of 15) >>---",
            "Ducky_ is in SkyBlock - Private Island",
        ).joinToString("\n")

        val (_, entries) = FriendsParser.parseMessage(text)

        assertNull(entries.single().nameColor)
    }

    @Test
    fun `parseMessage does not mistake an unrelated message with no header for an offline friend`() {
        // Regression case: a guild-join notice ("Notch joined the guild!") arriving in the
        // same capture window as a real /fl was getting misread as an offline friend line,
        // since the permissive fallback alone can't tell the two apart. Requiring the
        // header to be present in THIS SAME message before the fallback applies fixes it.
        val (header, entries) = FriendsParser.parseMessage("Notch joined the guild!")
        assertNull(header)
        assertEquals(emptyList(), entries)
    }
}
