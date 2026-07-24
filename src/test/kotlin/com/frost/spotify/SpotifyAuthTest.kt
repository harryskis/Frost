package com.frost.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpotifyAuthTest {
    @Test
    fun `code challenge matches the RFC 7636 Appendix B test vector`() {
        // https://datatracker.ietf.org/doc/html/rfc7636#appendix-B
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", SpotifyAuth.codeChallenge(verifier))
    }

    @Test
    fun `generated verifiers are URL-safe and within the PKCE length range`() {
        val verifier = SpotifyAuth.generateCodeVerifier()
        assertTrue(verifier.length in 43..128)
        assertTrue(verifier.none { it == '+' || it == '/' || it == '=' })
    }

    @Test
    fun `generated verifiers and states are not reused across calls`() {
        assertTrue(SpotifyAuth.generateCodeVerifier() != SpotifyAuth.generateCodeVerifier())
        assertTrue(SpotifyAuth.generateState() != SpotifyAuth.generateState())
    }

    @Test
    fun `authorize URL carries the required PKCE and scope parameters`() {
        val url = SpotifyAuth.buildAuthorizeUrl("my client id", "my state", "my challenge")
        assertTrue(url.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=my+client+id") || url.contains("client_id=my%20client%20id"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge=my+challenge") || url.contains("code_challenge=my%20challenge"))
        assertTrue(url.contains("state=my+state") || url.contains("state=my%20state"))
        assertTrue(url.contains("redirect_uri="))
        assertTrue(url.contains("user-modify-playback-state"))
    }

    @Test
    fun `parseQuery decodes a typical Spotify redirect callback`() {
        val params = SpotifyAuth.parseQuery("code=AQD_abc123&state=xyz")
        assertEquals("AQD_abc123", params["code"])
        assertEquals("xyz", params["state"])
    }

    @Test
    fun `parseQuery decodes an error callback`() {
        val params = SpotifyAuth.parseQuery("error=access_denied&state=xyz")
        assertEquals("access_denied", params["error"])
    }

    @Test
    fun `parseQuery URL-decodes values`() {
        val params = SpotifyAuth.parseQuery("state=a%20b%2Bc")
        assertEquals("a b+c", params["state"])
    }

    @Test
    fun `parseTokenResponse extracts a successful token grant`() {
        val body = """{"access_token": "AT", "token_type": "Bearer", "expires_in": 3600, "refresh_token": "RT"}"""
        val result = SpotifyAuth.parseTokenResponse(200, body)
        assertTrue(result is SpotifyAuth.TokenResult.Success)
        assertEquals("AT", result.accessToken)
        assertEquals("RT", result.refreshToken)
        assertEquals(3600L, result.expiresInSeconds)
    }

    @Test
    fun `parseTokenResponse treats a missing refresh_token as token rotation not happening`() {
        val body = """{"access_token": "AT", "token_type": "Bearer", "expires_in": 3600}"""
        val result = SpotifyAuth.parseTokenResponse(200, body)
        assertTrue(result is SpotifyAuth.TokenResult.Success)
        assertEquals(null, result.refreshToken)
    }

    @Test
    fun `parseTokenResponse reports an error for an invalid_grant response`() {
        val body = """{"error": "invalid_grant", "error_description": "Authorization code expired"}"""
        val result = SpotifyAuth.parseTokenResponse(400, body)
        assertTrue(result is SpotifyAuth.TokenResult.Error)
        assertEquals("Authorization code expired", result.message)
    }

    @Test
    fun `parseTokenResponse reports an error for an unparseable body`() {
        val result = SpotifyAuth.parseTokenResponse(200, "not json")
        assertTrue(result is SpotifyAuth.TokenResult.Error)
    }
}
