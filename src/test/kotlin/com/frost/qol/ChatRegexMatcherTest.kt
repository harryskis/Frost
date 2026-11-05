package com.frost.qol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChatRegexMatcherTest {
    private fun matches(pattern: String, message: String, regex: Boolean = false, matchCase: Boolean = false) =
        ChatRegexMatcher.compile(pattern, regex, matchCase)?.find(message) != null

    @Test
    fun `plain text matches anywhere in the line, ignoring case`() {
        assertEquals(true, matches("A wither key was picked up", "A Wither Key was picked up!"))
        assertEquals(true, matches("picked up", "A Blood Key was picked up!"))
        assertEquals(false, matches("A wither key was picked up", "A Blood Key was picked up!"))
    }

    @Test
    fun `plain text is taken literally, even with regex symbols in it`() {
        assertEquals(true, matches("[BOSS] Maxor:", "[BOSS] Maxor: WELL WELL WELL"))
        assertEquals(false, matches("a.c", "abc"))
    }

    @Test
    fun `match case makes capitals count`() {
        assertEquals(false, matches("wither key", "A Wither Key was picked up!", matchCase = true))
        assertEquals(true, matches("Wither Key", "A Wither Key was picked up!", matchCase = true))
    }

    @Test
    fun `a regex can be anchored, and a broken one or a blank message matches nothing`() {
        assertEquals(true, matches("^\\[BOSS] \\w+:", "[BOSS] Maxor: hi", regex = true))
        assertEquals(false, matches("^\\[BOSS] \\w+:", "Party > [BOSS] Maxor: hi", regex = true))
        assertNull(ChatRegexMatcher.compile("(unclosed", regex = true, matchCase = false))
        assertNull(ChatRegexMatcher.compile("   ", regex = false, matchCase = false))
    }

    @Test
    fun `the text shown fills in regex groups and color codes, or is the chat line when blank`() {
        val message = "[MVP+] Steve has obtained Wither Key!"
        val match = assertNotNull(ChatRegexMatcher.compile("(\\w+) has obtained (\\w+) Key!", regex = true, matchCase = false)?.find(message))
        assertEquals("§cSteve got the Wither key", ChatRegexMatcher.displayText("&c$1 got the $2 key", match, message))
        assertEquals("Steve has obtained Wither Key!", ChatRegexMatcher.displayText("$0", match, message))
        assertEquals("missing: ", ChatRegexMatcher.displayText("missing: $7", match, message))
        assertEquals(message, ChatRegexMatcher.displayText("  ", match, message))
    }
}
