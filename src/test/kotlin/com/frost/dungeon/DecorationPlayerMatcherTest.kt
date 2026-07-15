package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals

class DecorationPlayerMatcherTest {
    @Test
    fun `assigns the self decoration to the local player`() {
        val decorations = listOf(DecorationPlayerMatcher.Decoration(isSelf = true, tile = GridPos(1, 1)))

        val result = DecorationPlayerMatcher.match(decorations, "Alice", listOf("Alice", "Bob"))

        assertEquals(GridPos(1, 1), result["Alice"])
    }

    @Test
    fun `assigns non-self decorations to the next party member in order, skipping self`() {
        val decorations = listOf(
            DecorationPlayerMatcher.Decoration(isSelf = true, tile = GridPos(0, 0)),
            DecorationPlayerMatcher.Decoration(isSelf = false, tile = GridPos(1, 1)),
            DecorationPlayerMatcher.Decoration(isSelf = false, tile = GridPos(2, 2)),
        )

        val result = DecorationPlayerMatcher.match(decorations, "Alice", listOf("Alice", "Bob", "Carl"))

        assertEquals(GridPos(0, 0), result["Alice"])
        assertEquals(GridPos(1, 1), result["Bob"])
        assertEquals(GridPos(2, 2), result["Carl"])
    }

    @Test
    fun `ignores extra decorations once every party member is assigned`() {
        val decorations = listOf(
            DecorationPlayerMatcher.Decoration(isSelf = false, tile = GridPos(1, 1)),
            DecorationPlayerMatcher.Decoration(isSelf = false, tile = GridPos(2, 2)),
        )

        val result = DecorationPlayerMatcher.match(decorations, "Alice", listOf("Alice", "Bob"))

        assertEquals(GridPos(1, 1), result["Bob"])
        assertEquals(1, result.size)
    }

    @Test
    fun `works regardless of where the self decoration appears in the list`() {
        val decorations = listOf(
            DecorationPlayerMatcher.Decoration(isSelf = false, tile = GridPos(1, 1)),
            DecorationPlayerMatcher.Decoration(isSelf = true, tile = GridPos(0, 0)),
        )

        val result = DecorationPlayerMatcher.match(decorations, "Alice", listOf("Alice", "Bob"))

        assertEquals(GridPos(0, 0), result["Alice"])
        assertEquals(GridPos(1, 1), result["Bob"])
    }
}
