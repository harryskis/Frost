package com.frost.summary

import com.frost.dungeon.GridPos
import com.frost.parsing.TabListParser
import com.frost.session.DungeonSession
import com.frost.util.TextUtils
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text

/**
 * Formats the end-of-run breakdown of exactly which rooms each player cleared, plus their
 * secrets-found diff. One compact line per player; the room list lives in a hover tooltip
 * rather than inline, since a full run can clear a dozen+ rooms per person.
 */
object RunSummary {
    private fun resolveName(tiles: Set<GridPos>, roomNames: Map<GridPos, String>): String {
        val identified = tiles.firstNotNullOfOrNull { roomNames[it] }
        if (identified != null) return identified
        val anyTile = tiles.minByOrNull { it.x * 6 + it.z } ?: return "Unknown room"
        return "Unknown (${anyTile.x},${anyTile.z})"
    }

    /**
     * One line per party member: "Name (Class): X rooms, Y secrets". Hovering the line shows
     * the solo/stack room breakdown. [roomNames] maps a grid tile to that room's real name
     * (from the world-block scanner); tiles missing from it fall back to an "Unknown (x,z)"
     * label. Secrets show as "?" when the API snapshot never completed (no key configured,
     * request failed, etc.) rather than a misleading 0.
     */
    fun formatRoomBreakdown(session: DungeonSession, roomNames: Map<GridPos, String>): List<Text> {
        val lines = mutableListOf<Text>()
        lines += Text.literal("§6§l-----------------------------------------------------")
        lines += Text.literal("§e§lRooms Cleared §7- §f${session.floor ?: "?"}")

        for (username in session.party.keys.sorted()) {
            val classLetter = session.playerClass[username]?.let { TabListParser.classLetter(it) }
            val displayName = if (classLetter != null) "$username ($classLetter)" else username

            // Resolve + dedupe by name (a room clearing across two ticks can otherwise
            // produce two entries). Solo wins over stack if somehow both are recorded.
            val solo = (session.soloClearedRooms[username] ?: emptyList())
                .map { resolveName(it, roomNames) }.toCollection(LinkedHashSet())
            val stack = (session.stackClearedRooms[username] ?: emptyList())
                .map { resolveName(it, roomNames) }.toCollection(LinkedHashSet())
                .minus(solo)

            val total = solo.size + stack.size
            val secretsLabel = session.secretsFoundThisRun(username)?.toString() ?: "?"

            val line: MutableText = Text.literal("§b$displayName§7: §f$total rooms§7, §f$secretsLabel secrets")
            if (solo.isNotEmpty() || stack.isNotEmpty()) {
                line.styled { it.withHoverEvent(HoverEvent.ShowText(roomHoverText(solo, stack))) }
            }
            lines += line
        }

        lines += Text.literal("§6§l-----------------------------------------------------")
        return lines
    }

    /**
     * A single compact line summarizing every party member's weighted score:
     * "[Frost] Contributions this run: Name (Class): X | Name2 (Class2): Y | ...". A room
     * credited to a player counts at its full configured weight regardless of solo/stack -
     * the same "no fractional credit" rule [formatRoomBreakdown]'s plain room count already
     * uses. [weightOf] resolves a room's real name to its configured weight (falling back
     * to 1.0 for anything unconfigured, including an "Unknown (x,z)" label) - passed in
     * rather than read from ModConfig directly so this stays plain-JVM testable, same as
     * [roomNames]. Secrets found this run each add a flat 1 weight - a player whose secrets
     * snapshot never completed (shows "?" in [formatRoomBreakdown]) contributes 0 from
     * secrets rather than making the whole score unknown. Plain, uncolored text - this line
     * is meant to be sent as a real chat message (see Frost.kt's announceToChatChannel),
     * which can't carry §-formatting codes.
     */
    fun formatWeightedScores(session: DungeonSession, roomNames: Map<GridPos, String>, weightOf: (String) -> Double): Text {
        val parts = session.party.keys.sorted().map { username ->
            val classLetter = session.playerClass[username]?.let { TabListParser.classLetter(it) }
            val displayName = if (classLetter != null) "$username ($classLetter)" else username

            val solo = (session.soloClearedRooms[username] ?: emptyList()).map { resolveName(it, roomNames) }
            val stack = (session.stackClearedRooms[username] ?: emptyList()).map { resolveName(it, roomNames) }
            val roomWeight = (solo + stack).toSet().sumOf(weightOf)
            val secretsWeight = (session.secretsFoundThisRun(username) ?: 0).toDouble()
            val totalWeight = roomWeight + secretsWeight

            "$displayName: ${TextUtils.formatWeight(totalWeight)}"
        }
        return Text.literal("[Frost] Contributions this run: " + parts.joinToString(" | "))
    }

    private fun roomHoverText(solo: Set<String>, stack: Set<String>): Text {
        val hover = Text.literal("")
        if (solo.isNotEmpty()) {
            hover.append(Text.literal("§a§lSolo§r§7: §f" + solo.joinToString(", ")))
        }
        if (stack.isNotEmpty()) {
            if (solo.isNotEmpty()) hover.append(Text.literal("\n"))
            hover.append(Text.literal("§e§lStack§r§7: §f" + stack.joinToString(", ")))
        }
        return hover
    }
}
