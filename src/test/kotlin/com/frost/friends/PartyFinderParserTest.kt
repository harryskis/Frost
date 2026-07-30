package com.frost.friends

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PartyFinderParserTest {
    // Exact confirmed live tooltip text from a Party Finder listing.
    private val ownPartyTooltip = listOf(
        "Bdlt's Party",
        "Dungeon: Master Mode The Catacombs",
        "Floor: Floor V",
        "Note: 2",
        "Dungeon Level Required: 45",
        "",
        "Members:",
        " Bdlt: Tank 46",
        " Stormcaller: Berserk 46",
        " Blazehunter: Mage 48",
        " Moonrunner: Healer 49",
        " Empty",
        "",
        "You are in this party!",
        "",
        "Missing: Archer",
    )

    @Test
    fun `parses the floor as master mode from Dungeon and Floor lines`() {
        val info = PartyFinderParser.parseOwnPartyListing(ownPartyTooltip)
        assertEquals("M5", info?.floorLabel)
    }

    @Test
    fun `parses every real member, skipping the Empty slot`() {
        val info = PartyFinderParser.parseOwnPartyListing(ownPartyTooltip)
        assertEquals(
            listOf(
                PartyFinderMember("Bdlt", DungeonClass.TANK, 46),
                PartyFinderMember("Stormcaller", DungeonClass.BERSERKER, 46),
                PartyFinderMember("Blazehunter", DungeonClass.MAGE, 48),
                PartyFinderMember("Moonrunner", DungeonClass.HEALER, 49),
            ),
            info?.members,
        )
    }

    @Test
    fun `returns null for a listing that is not the player's own party`() {
        val someoneElsesListing = ownPartyTooltip.filterNot { it == "You are in this party!" }
        assertNull(PartyFinderParser.parseOwnPartyListing(someoneElsesListing))
    }

    @Test
    fun `parses a normal-mode floor without the Master prefix`() {
        val text = listOf(
            "Someone's Party",
            "Dungeon: The Catacombs",
            "Floor: Floor III",
            "Members:",
            " Someone: Mage 20",
            "You are in this party!",
        )
        assertEquals("F3", PartyFinderParser.parseOwnPartyListing(text)?.floorLabel)
    }

    @Test
    fun `still returns the member list when the floor cannot be determined`() {
        val text = listOf(
            "Weird Listing",
            "Members:",
            " Someone: Mage 20",
            "You are in this party!",
        )
        val info = PartyFinderParser.parseOwnPartyListing(text)
        assertNull(info?.floorLabel)
        assertEquals(listOf(PartyFinderMember("Someone", DungeonClass.MAGE, 20)), info?.members)
    }
}
