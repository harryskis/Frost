package com.frost.dungeon

import com.frost.config.ModConfig
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

/**
 * Turns the player to face the way the teammate they just spirit leaped to is facing.
 *
 * The chat line can arrive before the teleport does, and a teammate who was out of range only
 * loads in after it, so the turn waits until the teammate is loaded and close by.
 */
object LeapFix {
    private const val WAIT_TICKS = 40
    private const val ARRIVED_DISTANCE = 10.0

    private var targetName: String? = null
    private var ticksLeft = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> onTick(client) }
    }

    fun onLeap(name: String) {
        if (!ModConfig.isLeapFixEnabled()) return
        targetName = name
        ticksLeft = WAIT_TICKS
    }

    private fun onTick(client: Minecraft) {
        val name = targetName ?: return
        if (--ticksLeft < 0) {
            targetName = null
            return
        }
        val player = client.player ?: return
        val target = client.level?.players()?.firstOrNull { it.gameProfile.name == name } ?: return
        if (target === player || player.distanceToSqr(target) > ARRIVED_DISTANCE * ARRIVED_DISTANCE) return
        // The head rather than yRot, since that's the way a teammate is actually looking.
        player.absSnapRotationTo(target.yHeadRot, target.xRot)
        targetName = null
    }
}
