package com.frost.friends

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FriendPresenceParserTest {
    @Test
    fun `parses a friend logging off`() {
        assertEquals(FriendPresenceChange("HauTF", online = false), FriendPresenceParser.parse("Friend > HauTF left."))
    }

    @Test
    fun `parses a friend logging on`() {
        assertEquals(FriendPresenceChange("HauTF", online = true), FriendPresenceParser.parse("Friend > HauTF joined."))
    }

    @Test
    fun `does not match an unrelated message`() {
        assertNull(FriendPresenceParser.parse("HauTF left the party."))
        assertNull(FriendPresenceParser.parse("Party Finder > HauTF joined the dungeon group! (Tank Level 45)"))
    }

    @Test
    fun `does not match without the trailing period`() {
        assertNull(FriendPresenceParser.parse("Friend > HauTF joined"))
    }
}
