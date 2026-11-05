package com.frost.shitters

import kotlin.test.Test
import kotlin.test.assertEquals

class ShitterDatabaseTest {
    private fun parse(text: String) = ShitterDatabase.parse(text).associate { it.name to it.reason }

    @Test
    fun `a JSON list of players, by any of the usual key names`() {
        val json = """[{"name": "Steve", "reason": "Left at P3"}, {"ign": "Alex", "note": "Griefed"}, {"username": "Bob"}]"""
        assertEquals(mapOf("Steve" to "Left at P3", "Alex" to "Griefed", "Bob" to ShitterDatabase.NO_REASON), parse(json))
    }

    @Test
    fun `a JSON object of names to reasons, or holding the list`() {
        assertEquals(mapOf("Steve" to "Left", "Alex" to "Trolled"), parse("""{"Steve": "Left", "Alex": {"reason": "Trolled"}}"""))
        assertEquals(mapOf("Steve" to "Left"), parse("""{"players": [{"name": "Steve", "reason": "Left"}]}"""))
        assertEquals(mapOf("Steve" to "Left"), parse("""{"name": "Steve", "reason": "Left"}"""))
    }

    @Test
    fun `a JSON list of names, or of name and reason pairs`() {
        assertEquals(mapOf("Steve" to ShitterDatabase.NO_REASON, "Alex" to "Afk"), parse("""["Steve", ["Alex", "Afk"]]"""))
    }

    @Test
    fun `a spreadsheet's CSV, by its header, with quoted cells and extra columns`() {
        val csv = """
            Date,IGN,Reason,Added By
            2026-10-01,Steve,"Left at P3, then blamed healer",Frosty
            2026-10-02,Alex,Griefed,Frosty
            2026-10-03,not a name,whatever,Frosty
        """.trimIndent()
        assertEquals(mapOf("Steve" to "Left at P3, then blamed healer", "Alex" to "Griefed"), parse(csv))
    }

    @Test
    fun `plain text, one player a line, with comments and blank lines skipped`() {
        val text = """
            # The list
            Steve - Left at P3
            Alex: Griefed

            Bob
            // done
        """.trimIndent()
        assertEquals(mapOf("Steve" to "Left at P3", "Alex" to "Griefed", "Bob" to ShitterDatabase.NO_REASON), parse(text))
    }

    @Test
    fun `the same player twice is kept once, whatever the case`() {
        assertEquals(listOf("Steve"), ShitterDatabase.parse("Steve, one\nSTEVE, two").map { it.name })
    }

    @Test
    fun `links to pages showing a list are turned into links to the data`() {
        assertEquals(
            "https://docs.google.com/spreadsheets/d/abc_123-XYZ/export?format=csv&gid=42",
            ShitterDatabase.directUrl("https://docs.google.com/spreadsheets/d/abc_123-XYZ/edit#gid=42"),
        )
        assertEquals(
            "https://docs.google.com/spreadsheets/d/abc/export?format=csv",
            ShitterDatabase.directUrl("https://docs.google.com/spreadsheets/d/abc/edit?usp=sharing"),
        )
        assertEquals("https://pastebin.com/raw/Ab12Cd", ShitterDatabase.directUrl("https://pastebin.com/Ab12Cd"))
        assertEquals(
            "https://raw.githubusercontent.com/someone/lists/main/m5.json",
            ShitterDatabase.directUrl("https://github.com/someone/lists/blob/main/m5.json"),
        )
        val direct = "https://example.com/api/shitters.json"
        assertEquals(direct, ShitterDatabase.directUrl(direct))
        assertEquals("https://pastebin.com/raw/Ab12Cd", ShitterDatabase.directUrl("https://pastebin.com/raw/Ab12Cd"))
    }
}
