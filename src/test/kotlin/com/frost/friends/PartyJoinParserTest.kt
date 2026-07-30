package com.frost.friends

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PartyJoinParserTest {
    @Test
    fun `parses the confirmed live party finder join message`() {
        val join = PartyJoinParser.parse("Party Finder > Bdlt joined the dungeon group! (Tank Level 45)")
        assertEquals(PartyJoin("Bdlt", DungeonClass.TANK, 45), join)
    }

    @Test
    fun `accepts the shortened Berserk spelling alongside Berserker`() {
        assertEquals(DungeonClass.BERSERKER, PartyJoinParser.parse("Party Finder > Foo joined the dungeon group! (Berserk Level 10)")?.dungeonClass)
        assertEquals(DungeonClass.BERSERKER, PartyJoinParser.parse("Party Finder > Foo joined the dungeon group! (Berserker Level 10)")?.dungeonClass)
    }

    @Test
    fun `matches every class`() {
        assertEquals(DungeonClass.HEALER, PartyJoinParser.parse("Party Finder > Foo joined the dungeon group! (Healer Level 1)")?.dungeonClass)
        assertEquals(DungeonClass.MAGE, PartyJoinParser.parse("Party Finder > Foo joined the dungeon group! (Mage Level 1)")?.dungeonClass)
        assertEquals(DungeonClass.ARCHER, PartyJoinParser.parse("Party Finder > Foo joined the dungeon group! (Archer Level 1)")?.dungeonClass)
    }

    @Test
    fun `does not match an unrelated chat message`() {
        assertNull(PartyJoinParser.parse("Bdlt joined the party."))
        assertNull(PartyJoinParser.parse("Party Finder > Bdlt left the dungeon group!"))
    }

    @Test
    fun `does not match an unrecognized class name`() {
        assertNull(PartyJoinParser.parse("Party Finder > Bdlt joined the dungeon group! (Ninja Level 45)"))
    }
}
