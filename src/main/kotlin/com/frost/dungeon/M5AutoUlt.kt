package com.frost.dungeon

import com.frost.config.ModConfig
import com.frost.parsing.TabListParser
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel

/**
 * Presses the drop key once, a set time after entering the M5 boss room, for a Healer or Tank -
 * that key is what activates a class's ultimate. It goes through the player's own key binding,
 * so it's handled exactly as if they'd pressed it. It waits for any open screen to close first,
 * since a key press doesn't do anything while one is up.
 *
 * Entering the boss is told by position rather than chat, since boss dialogue can be hidden.
 */
object M5AutoUlt {
    private const val FLOOR = "M5"
    private const val ULT_DELAY_TICKS = 200

    // Where the floor 5 boss room begins - past the dungeon grid, so nothing else lands here.
    // The same bounds Odin uses.
    private const val BOSS_MIN_X = -39.0
    private const val BOSS_MIN_Z = -7.0

    private val ULT_CLASSES = setOf("Healer", "Tank")

    private var lastLevel: ClientLevel? = null
    private var onM5 = false
    private var playerClass: String? = null
    private var ticksInBoss = -1
    private var pressed = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> onTick(client) }
    }

    /** Fed the floor by Frost's own scoreboard polling. Sticks for the rest of the level, since
     * this only has to survive until the ult, and only a new level means a new floor. */
    fun onFloor(floor: String) {
        if (floor == FLOOR) onM5 = true
    }

    /** Fed the visible tab list lines by Frost's own polling. A dead player's line shows no
     * class, so the last one seen is kept. */
    fun onTabList(lines: List<String>) {
        val self = Minecraft.getInstance().player?.gameProfile?.name ?: return
        val line = lines.firstOrNull { TabListParser.parseMemberName(it) == self } ?: return
        TabListParser.parseMemberClass(line)?.let { playerClass = it }
    }

    private fun onTick(client: Minecraft) {
        val level = client.level
        if (level !== lastLevel) {
            lastLevel = level
            onM5 = false
            playerClass = null
            ticksInBoss = -1
            pressed = false
        }
        val player = client.player
        if (player == null || !onM5 || pressed || !ModConfig.isM5AutoUltEnabled()) return
        if (ticksInBoss < 0 && (player.x <= BOSS_MIN_X || player.z <= BOSS_MIN_Z)) return
        if (++ticksInBoss < ULT_DELAY_TICKS || client.gui.screen() != null) return
        pressed = true
        if (playerClass in ULT_CLASSES) KeyMapping.click(InputConstants.getKey(client.options.keyDrop.saveString()))
    }
}
