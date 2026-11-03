package com.frost.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionBarParserTest {
    @Test
    fun `reads a room's secrets out of the full action bar, with or without formatting`() {
        assertEquals(3 to 10, ActionBarParser.parseRoomSecrets("§c2000/2000❤     §a500§a❈ Defense     §b800/800✎ Mana    §73/10 Secrets"))
        assertEquals(0 to 4, ActionBarParser.parseRoomSecrets("2000/2000❤   0/4 Secrets   800/800✎ Mana"))
    }

    @Test
    fun `other action bars have no secret count, even with similar-looking stats`() {
        assertNull(ActionBarParser.parseRoomSecrets("§c2000/2000❤     §b800/800✎ Mana"))
        assertNull(ActionBarParser.parseRoomSecrets(""))
    }
}
