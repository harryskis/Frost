package com.frost.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreboardParserTest {
    @Test
    fun `parses a raw secrets found count`() {
        assertEquals(7, ScoreboardParser.parseSecretsFoundTeamTotal(" Secrets Found: 7"))
    }

    @Test
    fun `recognizes a secrets found percent line without extracting it as a count`() {
        assertTrue(ScoreboardParser.isSecretsFoundPercentLine(" Secrets Found: 58.3%"))
        assertNull(ScoreboardParser.parseSecretsFoundTeamTotal(" Secrets Found: 58.3%"))
    }

    @Test
    fun `parses the cleared percent line`() {
        assertEquals(100, ScoreboardParser.parseClearedPercent("Cleared: 100% (12)"))
        assertEquals(42, ScoreboardParser.parseClearedPercent("Cleared: 42% (5)"))
    }

    @Test
    fun `parses the floor from a title-style line`() {
        assertEquals("VII", ScoreboardParser.parseFloor("The Catacombs (VII)"))
    }

    @Test
    fun `returns null for unrelated lines`() {
        assertNull(ScoreboardParser.parseSecretsFoundTeamTotal("Purse: 1,234"))
        assertNull(ScoreboardParser.parseClearedPercent("Purse: 1,234"))
        assertNull(ScoreboardParser.parseFloor("Purse: 1,234"))
    }
}
