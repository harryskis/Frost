package com.frost.dungeon

import com.frost.Frost
import com.frost.config.ModConfig
import com.frost.gui.BigAlert
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.resources.Identifier

/**
 * Warns when the dungeon has a short blood rush. Fed by [FunnyMap]'s scan, whose chunks arrive in
 * bursts, so nothing is judged until the layout has stopped changing for a moment - otherwise
 * every dungeon would look like a possible short rush for the first second, with nothing loaded.
 * Each message only shows once per dungeon, and none at all once the run has started.
 */
object BloodRushAlert {
    private val HUD_ID = Identifier.fromNamespaceAndPath(Frost.MOD_ID, "blood_rush_alert")

    private const val ALERT_MILLIS = 5000L
    private const val SETTLE_MILLIS = 2000L

    // The last dungeon's sidebar can linger for a moment on arriving in a new one. Shorter than
    // SETTLE_MILLIS, so joining a run already in progress still can't get an alert in first.
    private const val STALE_SIDEBAR_MILLIS = 1500L
    private const val SHORT_RUSH = 2
    private const val MEDIUM_RUSH = 3

    private val RED = 0xFFFF2020.toInt()
    private val YELLOW = 0xFFFFE14D.toInt()

    private val alerted = mutableSetOf<String>()
    private var layoutSize = -1
    private var settledAt = 0L
    private var text = ""
    private var color = 0
    private var showUntil = 0L
    private var runStarted = false
    private var resetAt = 0L

    fun register() {
        HudElementRegistry.addLast(HUD_ID) { context, _ ->
            if (System.currentTimeMillis() < showUntil) BigAlert.draw(context, text, color)
        }
    }

    fun reset() {
        alerted.clear()
        layoutSize = -1
        showUntil = 0
        runStarted = false
        resetAt = System.currentTimeMillis()
    }

    /** Fed by Frost's own scoreboard polling and chat listener. Clears anything still on screen. */
    fun onRunStarted() {
        if (System.currentTimeMillis() - resetAt < STALE_SIDEBAR_MILLIS) return
        runStarted = true
        showUntil = 0
    }

    /** [unknownGaps] are the gaps that haven't loaded yet, which could still hold a door. */
    fun update(rooms: Map<GridPos, RoomDatabase.RoomInfo>, doors: Map<MapGap, DoorKind>, unknownGaps: Set<MapGap>) {
        if (runStarted) return
        val now = System.currentTimeMillis()
        val size = rooms.size + doors.size + unknownGaps.size
        if (size != layoutSize) {
            layoutSize = size
            settledAt = now + SETTLE_MILLIS
        }
        if (now < settledAt) return

        val known = FunnyMapLayout.bloodRushRoomCount(rooms, doors, unknownGaps)
        if (known != null) {
            alertFor(known, "")
        } else if (ModConfig.isAlertOnPossibleEnabled()) {
            // The fewest rooms it could still turn out to be, so a rush that could be 2 rooms is
            // also one that could be 3 - the shorter one is what's worth saying.
            FunnyMapLayout.possibleBloodRushRoomCount(rooms, doors, unknownGaps)?.let { alertFor(it.coerceAtLeast(SHORT_RUSH), "?") }
        }
    }

    private fun alertFor(roomCount: Int, suffix: String) {
        when {
            roomCount <= SHORT_RUSH -> show("$roomCount ${if (roomCount == 1) "ROOM" else "ROOMS"}$suffix", RED)
            roomCount == MEDIUM_RUSH && ModConfig.isAlertThreeRoomEnabled() -> show("$roomCount ROOMS$suffix", YELLOW)
        }
    }

    private fun show(message: String, messageColor: Int) {
        if (!alerted.add(message)) return
        text = message
        color = messageColor
        showUntil = System.currentTimeMillis() + ALERT_MILLIS
    }
}
