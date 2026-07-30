package com.frost.friends

import kotlin.test.Test
import kotlin.test.assertEquals

class PartyInviteMessageTest {
    @Test
    fun `matches the exact example message`() {
        val filled = setOf(DungeonClass.MAGE, DungeonClass.HEALER, DungeonClass.TANK)
        assertEquals(
            "Join my M5 party! We are 3/5 missing BERS and ARCH.",
            PartyInviteMessage.build("M5", filled),
        )
    }

    @Test
    fun `lists three or more missing classes with a comma and a trailing and`() {
        val filled = setOf(DungeonClass.MAGE)
        assertEquals(
            "Join my F7 party! We are 1/5 missing HEAL, TANK, BERS and ARCH.",
            PartyInviteMessage.build("F7", filled),
        )
    }

    @Test
    fun `omits the missing clause entirely once the party is full`() {
        assertEquals(
            "Join my M7 party! We are 5/5.",
            PartyInviteMessage.build("M7", DungeonClass.entries.toSet()),
        )
    }

    @Test
    fun `names the single missing class with no connective word`() {
        val filled = setOf(DungeonClass.MAGE, DungeonClass.HEALER, DungeonClass.TANK, DungeonClass.BERSERKER)
        assertEquals(
            "Join my M5 party! We are 4/5 missing ARCH.",
            PartyInviteMessage.build("M5", filled),
        )
    }
}
