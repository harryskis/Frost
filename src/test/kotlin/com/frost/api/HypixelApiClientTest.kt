package com.frost.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HypixelApiClientTest {
    private val uuid = "11111111111111111111111111111111"

    @Test
    fun `extracts secrets from the selected profile's member entry`() {
        val body = """
            {
              "success": true,
              "profiles": [
                { "selected": false, "members": { "$uuid": { "dungeons": { "secrets": 999 } } } },
                { "selected": true, "members": { "$uuid": { "dungeons": { "secrets": 42 } } } }
              ]
            }
        """.trimIndent()

        val result = HypixelApiClient.parseSecretsFound(200, body, uuid)

        assertTrue(result is HypixelApiClient.Result.Success)
        assertEquals(42, result.secretsFound)
    }

    @Test
    fun `reports an error for a non-200 status`() {
        val result = HypixelApiClient.parseSecretsFound(403, "{}", uuid)
        assertTrue(result is HypixelApiClient.Result.Error)
    }

    @Test
    fun `reports an error when the API reports failure`() {
        val body = """{ "success": false, "cause": "Invalid API key" }"""
        val result = HypixelApiClient.parseSecretsFound(200, body, uuid)
        assertTrue(result is HypixelApiClient.Result.Error)
        assertEquals("Invalid API key", result.message)
    }

    @Test
    fun `reports an error when the player isn't in the selected profile`() {
        val body = """
            {
              "success": true,
              "profiles": [
                { "selected": true, "members": { "someone-else": { "dungeons": { "secrets": 5 } } } }
              ]
            }
        """.trimIndent()

        val result = HypixelApiClient.parseSecretsFound(200, body, uuid)

        assertTrue(result is HypixelApiClient.Result.Error)
    }

    @Test
    fun `extracts a floor's S+ best time from dungeon_types`() {
        val body = """
            {
              "success": true,
              "profiles": [
                { "selected": true, "members": { "$uuid": { "dungeons": { "dungeon_types": {
                  "catacombs": { "fastest_time_s_plus": { "7": 238000 } }
                } } } } }
              ]
            }
        """.trimIndent()

        val result = HypixelApiClient.parseDungeonBestTime(200, body, uuid, "catacombs", "7", "fastest_time_s_plus", "F7", "S+")

        assertTrue(result is HypixelApiClient.BestTimeResult.Success)
        assertEquals(238000L, result.elapsedMillis)
    }

    @Test
    fun `extracts a master mode floor's completion best time`() {
        val body = """
            {
              "success": true,
              "profiles": [
                { "selected": true, "members": { "$uuid": { "dungeons": { "dungeon_types": {
                  "master_catacombs": { "fastest_time": { "5": 199000 } }
                } } } } }
              ]
            }
        """.trimIndent()

        val result = HypixelApiClient.parseDungeonBestTime(200, body, uuid, "master_catacombs", "5", "fastest_time", "M5", "completion")

        assertTrue(result is HypixelApiClient.BestTimeResult.Success)
        assertEquals(199000L, result.elapsedMillis)
    }

    @Test
    fun `reports no record when the floor has never been S+ cleared`() {
        val body = """
            {
              "success": true,
              "profiles": [
                { "selected": true, "members": { "$uuid": { "dungeons": { "dungeon_types": {
                  "catacombs": { "fastest_time_s_plus": { "6": 200000 } }
                } } } } }
              ]
            }
        """.trimIndent()

        val result = HypixelApiClient.parseDungeonBestTime(200, body, uuid, "catacombs", "7", "fastest_time_s_plus", "F7", "S+")

        assertTrue(result is HypixelApiClient.BestTimeResult.Error)
        assertTrue(result.message.contains("F7"))
    }

    @Test
    fun `reports an error for a non-200 status on the best-time lookup`() {
        val result = HypixelApiClient.parseDungeonBestTime(403, "{}", uuid, "catacombs", "7", "fastest_time_s_plus", "F7", "S+")
        assertTrue(result is HypixelApiClient.BestTimeResult.Error)
    }

    @Test
    fun `reports an error when the best-time player isn't in the selected profile`() {
        val body = """
            {
              "success": true,
              "profiles": [
                { "selected": true, "members": { "someone-else": { "dungeons": {} } } }
              ]
            }
        """.trimIndent()

        val result = HypixelApiClient.parseDungeonBestTime(200, body, uuid, "catacombs", "7", "fastest_time_s_plus", "F7", "S+")

        assertTrue(result is HypixelApiClient.BestTimeResult.Error)
    }
}
