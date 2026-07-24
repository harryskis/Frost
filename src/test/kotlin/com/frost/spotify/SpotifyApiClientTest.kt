package com.frost.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpotifyApiClientTest {
    @Test
    fun `action response treats 204 No Content as success`() {
        val result = SpotifyApiClient.parseActionResponse(204, "")
        assertTrue(result is SpotifyApiClient.Result.Success)
    }

    @Test
    fun `action response treats 200 and 202 as success too`() {
        assertTrue(SpotifyApiClient.parseActionResponse(200, "") is SpotifyApiClient.Result.Success)
        assertTrue(SpotifyApiClient.parseActionResponse(202, "") is SpotifyApiClient.Result.Success)
    }

    @Test
    fun `action response reports no active device for 404`() {
        val result = SpotifyApiClient.parseActionResponse(404, "{}")
        assertTrue(result is SpotifyApiClient.Result.Error)
        assertTrue(result.message.contains("device", ignoreCase = true))
    }

    @Test
    fun `action response reports Premium requirement for 403`() {
        val result = SpotifyApiClient.parseActionResponse(403, "{}")
        assertTrue(result is SpotifyApiClient.Result.Error)
        assertTrue(result.message.contains("Premium", ignoreCase = true))
    }

    @Test
    fun `action response surfaces Spotify's own error message on failure`() {
        val body = """{"error": {"status": 401, "message": "The access token expired"}}"""
        val result = SpotifyApiClient.parseActionResponse(401, body)
        assertTrue(result is SpotifyApiClient.Result.Error)
        assertEquals("The access token expired", result.message)
    }

    @Test
    fun `now-playing response parses track name, artist, and play state`() {
        val body = """
            {
              "is_playing": true,
              "item": {
                "name": "Song Title",
                "artists": [{"name": "Artist One"}, {"name": "Artist Two"}]
              }
            }
        """.trimIndent()
        val result = SpotifyApiClient.parseNowPlaying(200, body)
        assertTrue(result is SpotifyApiClient.Result.NowPlaying)
        assertEquals("Song Title", result.trackName)
        assertEquals("Artist One", result.artistName)
        assertTrue(result.isPlaying)
    }

    @Test
    fun `now-playing response treats 204 as nothing playing`() {
        val result = SpotifyApiClient.parseNowPlaying(204, "")
        assertTrue(result is SpotifyApiClient.Result.Error)
    }

    @Test
    fun `now-playing response handles a malformed body gracefully`() {
        val result = SpotifyApiClient.parseNowPlaying(200, "not json")
        assertTrue(result is SpotifyApiClient.Result.Error)
    }
}
