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
}
