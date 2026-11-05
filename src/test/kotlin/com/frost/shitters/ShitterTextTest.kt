package com.frost.shitters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShitterTextTest {
    private val listed = setOf("steve", "bad_player1")
    private fun find(raw: String) = ShitterText.findNames(raw) { it.lowercase() in listed }

    @Test
    fun `names are found as whole words, whatever their case`() {
        assertEquals(listOf(ShitterText.Span(15, 20, "STEVE")), find("Party Finder > STEVE joined the dungeon group! (Mage Level 40)"))
        assertEquals(emptyList(), find("Steve123 and Steven aren't Steve_ either"))
        assertEquals(listOf("Bad_Player1"), find("[MVP+] Bad_Player1: hi").map { it.name })
    }

    @Test
    fun `positions are in the raw text, color codes and all`() {
        val raw = " §5Steve§f: §eMage§b (§e42§b)"
        val span = find(raw).single()
        assertEquals("Steve", raw.substring(span.start, span.end))
    }

    @Test
    fun `a name with a code in the middle is still found, and its span covers the code`() {
        val raw = "§aSte§lve"
        val span = find(raw).single()
        assertEquals("Ste§lve", raw.substring(span.start, span.end))
        assertEquals("Steve", span.name)
    }

    @Test
    fun `a party's name in Party Finder finds its leader`() {
        assertEquals(listOf("Steve"), find("Steve's Party").map { it.name })
    }

    @Test
    fun `the codes in effect anywhere can be brought back`() {
        val raw = "§5§lBold purple §cred §lbold red §rplain"
        assertEquals("", ShitterText.activeCodes(raw, 0))
        assertEquals("§5§l", ShitterText.activeCodes(raw, raw.indexOf("Bold")))
        // A color code clears formatting.
        assertEquals("§c", ShitterText.activeCodes(raw, raw.indexOf("red")))
        assertEquals("§c§l", ShitterText.activeCodes(raw, raw.indexOf("bold red")))
        assertEquals("", ShitterText.activeCodes(raw, raw.indexOf("plain")))
        assertEquals("§l", ShitterText.activeFormats(raw, raw.indexOf("bold red")))
    }

    @Test
    fun `usernames are 1 to 16 letters, digits or underscores`() {
        assertTrue(ShitterText.isUsername("Bad_Player1"))
        assertTrue(ShitterText.isUsername("a"))
        assertFalse(ShitterText.isUsername("seventeen_letters"))
        assertFalse(ShitterText.isUsername("has space"))
        assertFalse(ShitterText.isUsername(""))
    }
}
