package com.frost.summary

import com.frost.dungeon.GridPos
import com.frost.session.DungeonSession
import com.frost.session.PartyMember
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunSummaryTest {
    /** Plain text of whatever the line's hover tooltip would show, or null if it has none. */
    private fun hoverText(line: Text): String? {
        val hover = line.style.hoverEvent ?: return null
        return (hover as HoverEvent.ShowText).value().string
    }

    private fun secretsSnapshot(session: DungeonSession, username: String, baseline: Int, found: Int) {
        session.secretsBaseline[username] = baseline
        session.secretsFinal[username] = baseline + found
    }

    @Test
    fun `line shows room count and secrets count for a player`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0)))
        secretsSnapshot(session, "Alice", 100, 11)

        val lines = RunSummary.formatRoomBreakdown(session, mapOf(GridPos(0, 0) to "Andesite"))

        val aliceLine = lines.single { it.string.contains("Alice") }
        assertTrue(aliceLine.string.contains("1 rooms"))
        assertTrue(aliceLine.string.contains("11 secrets"))
    }

    @Test
    fun `secrets show as unknown when no snapshot was ever taken`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))

        val lines = RunSummary.formatRoomBreakdown(session, emptyMap())

        val aliceLine = lines.single { it.string.contains("Alice") }
        assertTrue(aliceLine.string.contains("? secrets"))
    }

    @Test
    fun `hovering a player's line shows their solo-cleared rooms by resolved name`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0)))

        val lines = RunSummary.formatRoomBreakdown(session, mapOf(GridPos(0, 0) to "Andesite"))
        val aliceLine = lines.single { it.string.contains("Alice") }

        val hover = hoverText(aliceLine)
        assertTrue(hover != null && hover.contains("Andesite"))
    }

    @Test
    fun `falls back to a tile label when the world-scan never identified the room`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(2, 3)))

        val lines = RunSummary.formatRoomBreakdown(session, emptyMap())
        val aliceLine = lines.single { it.string.contains("Alice") }

        assertTrue(hoverText(aliceLine)?.contains("Unknown (2,3)") == true)
    }

    @Test
    fun `hover splits rooms into solo vs stack depending on how many players were present`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice"), PartyMember("Bob")))
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0)))
        session.recordRoomCleared(listOf("Alice", "Bob"), setOf(GridPos(1, 1)))

        val roomNames = mapOf(GridPos(0, 0) to "Solo Room", GridPos(1, 1) to "Stack Room")
        val lines = RunSummary.formatRoomBreakdown(session, roomNames)
        val aliceHover = hoverText(lines.single { it.string.contains("Alice") })

        assertTrue(aliceHover != null)
        assertTrue(aliceHover.contains("Solo") && aliceHover.contains("Solo Room"))
        assertTrue(aliceHover.contains("Stack") && aliceHover.contains("Stack Room"))
    }

    @Test
    fun `a player with zero rooms cleared has no hover tooltip`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice"), PartyMember("Bob")))
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0)))

        val lines = RunSummary.formatRoomBreakdown(session, mapOf(GridPos(0, 0) to "Andesite"))
        val bobLine = lines.single { it.string.contains("Bob") }

        assertTrue(bobLine.string.contains("0 rooms"))
        assertNull(hoverText(bobLine))
    }

    @Test
    fun `shows a player's class letter next to their name when known`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Bdlt"), PartyMember("Ostad")))
        session.playerClass["Bdlt"] = "Healer"

        val lines = RunSummary.formatRoomBreakdown(session, emptyMap())

        assertTrue(lines.any { it.string.contains("Bdlt (H)") })
        assertTrue(lines.any { it.string.contains("Ostad") && !it.string.contains("Ostad (") })
    }

    @Test
    fun `deduplicates a room resolved to the same name across multiple tile sets`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        // Same physical multi-tile room recorded twice (e.g. two ticks of the same clear).
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0), GridPos(0, 1)))
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0), GridPos(0, 1)))

        val roomNames = mapOf(GridPos(0, 0) to "Archway", GridPos(0, 1) to "Archway")
        val lines = RunSummary.formatRoomBreakdown(session, roomNames)

        val aliceLine = lines.single { it.string.contains("Alice") }
        assertTrue(aliceLine.string.contains("1 rooms"))
    }

    @Test
    fun `mentions every party member even with zero rooms cleared`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice"), PartyMember("Bob")))

        val formatted = RunSummary.formatRoomBreakdown(session, emptyMap()).joinToString("\n") { it.string }

        assertTrue(formatted.contains("Alice"))
        assertTrue(formatted.contains("Bob"))
    }

    @Test
    fun `party members are listed in alphabetical order`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Zed"), PartyMember("Alice")))

        val lines = RunSummary.formatRoomBreakdown(session, emptyMap())
        val zedIndex = lines.indexOfFirst { it.string.contains("Zed") }
        val aliceIndex = lines.indexOfFirst { it.string.contains("Alice") }

        assertTrue(aliceIndex < zedIndex)
    }

    @Test
    fun `weighted score message starts with the Frost contributions prefix`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))

        val line = RunSummary.formatWeightedScores(session, emptyMap()) { 1.0 }

        assertTrue(line.string.startsWith("[Frost] Contributions this run: "))
    }

    @Test
    fun `weighted score sums each cleared room's configured weight`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0)))
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(1, 1)))

        val roomNames = mapOf(GridPos(0, 0) to "Crypt", GridPos(1, 1) to "Ice Fill")
        val weights = mapOf("Crypt" to 3.0, "Ice Fill" to 2.5)

        val line = RunSummary.formatWeightedScores(session, roomNames) { weights[it] ?: 1.0 }

        assertTrue(line.string.contains("Alice: 5.5"))
    }

    @Test
    fun `unconfigured rooms default to a weight of 1 in the weighted score`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0)))

        val line = RunSummary.formatWeightedScores(session, mapOf(GridPos(0, 0) to "Raccoon")) { 1.0 }

        assertTrue(line.string.contains("Alice: 1"))
    }

    @Test
    fun `weighted score counts a stack room at full weight for everyone present`() {
        // Matches the plain room count's existing "no fractional credit" behavior - a
        // stack clear isn't split between participants there either.
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice"), PartyMember("Bob")))
        session.recordRoomCleared(listOf("Alice", "Bob"), setOf(GridPos(0, 0)))

        val line = RunSummary.formatWeightedScores(session, mapOf(GridPos(0, 0) to "Crypt")) { 4.0 }

        // Both players were "present" for the stack clear, so both get the full weight -
        // not half each - matching the plain count's existing behavior.
        assertTrue(line.string.contains("Alice: 4"))
        assertTrue(line.string.contains("Bob: 4"))
    }

    @Test
    fun `weighted score adds 1 per secret found this run`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0)))
        secretsSnapshot(session, "Alice", 100, 4)

        val line = RunSummary.formatWeightedScores(session, mapOf(GridPos(0, 0) to "Crypt")) { 3.0 }

        assertTrue(line.string.contains("Alice: 7")) // 3 (room) + 4 (secrets)
    }

    @Test
    fun `weighted score counts secrets even with zero rooms cleared`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        secretsSnapshot(session, "Alice", 50, 6)

        val line = RunSummary.formatWeightedScores(session, emptyMap()) { 1.0 }

        assertTrue(line.string.contains("Alice: 6"))
    }

    @Test
    fun `weighted score treats an unknown secrets snapshot as 0, not a crash`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0)))
        // No secretsSnapshot call - baseline/final never recorded, secretsFoundThisRun is null.

        val line = RunSummary.formatWeightedScores(session, mapOf(GridPos(0, 0) to "Crypt")) { 5.0 }

        assertTrue(line.string.contains("Alice: 5"))
    }

    @Test
    fun `weighted score line lists every party member separated by a pipe`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice"), PartyMember("Bob")))
        session.recordRoomCleared(listOf("Alice"), setOf(GridPos(0, 0)))

        val line = RunSummary.formatWeightedScores(session, mapOf(GridPos(0, 0) to "Crypt")) { 1.0 }

        assertTrue(line.string.contains("|"))
        assertTrue(line.string.contains("Alice"))
        assertTrue(line.string.contains("Bob"))
    }

    @Test
    fun `header includes the floor label`() {
        val session = DungeonSession()
        session.start(listOf(PartyMember("Alice")))
        session.floor = "M6"

        val lines = RunSummary.formatRoomBreakdown(session, emptyMap())

        assertTrue(lines.any { it.string.contains("M6") })
        assertEquals(true, lines.first().string.startsWith("§6§l-"))
        assertEquals(true, lines.last().string.startsWith("§6§l-"))
    }
}
