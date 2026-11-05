package com.frost.shitters

import kotlin.test.Test
import kotlin.test.assertEquals

class ShitterDatabaseTest {
    private fun parse(text: String): Map<String, String> =
        ShitterDatabase.parse(text).mapNotNull { record -> record.name?.let { it to record.reason } }.toMap()

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

    private val dashed = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
    private val plain = "069a79f444e94726a5befca90e38aaf5"
    private val other = "853c80ef3c3749fdaa49938b674adae6"

    private fun byUuid(text: String): Map<String, String> =
        ShitterDatabase.parse(text).mapNotNull { record -> record.uuid?.let { it to record.reason } }.toMap()

    @Test
    fun `players listed by UUID, with or without dashes, in any of the usual shapes`() {
        val expected = mapOf(plain to "Left at P3", other to "Griefed")
        assertEquals(expected, byUuid("""[{"uuid": "$dashed", "reason": "Left at P3"}, {"id": "$other", "note": "Griefed"}]"""))
        assertEquals(expected, byUuid("""{"$dashed": "Left at P3", "$other": {"reason": "Griefed"}}"""))
        assertEquals(expected, byUuid("""{"players": [["$dashed", "Left at P3"], ["$other", "Griefed"]]}"""))
        assertEquals(expected, byUuid("$dashed - Left at P3\n$other, Griefed"))
        assertEquals(mapOf(plain to ShitterDatabase.NO_REASON), byUuid("""["$dashed"]"""))
    }

    @Test
    fun `a name given alongside a UUID is kept, and reasons in a list are joined`() {
        val record = ShitterDatabase.parse("""[{"uuid": "$plain", "name": "Steve", "reasons": ["Left", "Lied"]}]""").single()
        assertEquals(ShitterRecord("Steve", plain, "Left; Lied"), record)
    }

    @Test
    fun `a spreadsheet with a UUID column`() {
        assertEquals(mapOf(plain to "Left"), byUuid("UUID,Reason\n$dashed,Left"))
    }

    @Test
    fun `the same UUID twice is kept once, dashes or not`() {
        assertEquals(1, ShitterDatabase.parse("""["$dashed", "$plain", "${plain.uppercase()}"]""").size)
    }

    @Test
    fun `UUIDs are normalized to lowercase without dashes, and anything else isn't one`() {
        assertEquals(plain, ShitterDatabase.normalizeUuid(dashed.uppercase()))
        assertEquals(plain, ShitterDatabase.normalizeUuid(plain))
        assertEquals(null, ShitterDatabase.normalizeUuid("Steve"))
        assertEquals(null, ShitterDatabase.normalizeUuid("069a79f4-44e9-4726-a5be"))
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
