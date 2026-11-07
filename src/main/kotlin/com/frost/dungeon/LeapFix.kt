package com.frost.dungeon

import com.frost.config.ModConfig
import com.frost.mixin.LocalPlayerAccessor
import com.frost.util.TextUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative

/**
 * Leap Fix: after a spirit leap, faces the way the teammate leaped to was facing - RSM's Rotate
 * Fix (BSD 3-Clause, see /THIRD_PARTY_NOTICES.md at the repo root), ported.
 *
 * Their direction is taken the moment their head is clicked in the leap menu - from their player
 * if it's loaded, or their marker on the in-game map, looking level, if they're too far away. The
 * player is turned the moment the teleport is applied, not a tick or more later, so they never
 * see the old direction from the new spot. Hooked in by [com.frost.mixin.MultiPlayerGameModeMixin]
 * and [com.frost.mixin.ClientPacketListenerMixin].
 */
object LeapFix {
    private var pitch: Float? = null
    private var yaw: Float? = null
    private var clickedAt = 0L
    private var ticks = 0L

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { ticks++ }
    }

    /** A slot clicked in any menu; only a teammate's head in the leap menu counts. */
    @JvmStatic
    fun onContainerClick(containerId: Int, slotId: Int) {
        if (!ModConfig.isLeapFixEnabled() || slotId !in LeapFixRules.TEAMMATE_SLOTS) return
        val client = Minecraft.getInstance()
        val screen = client.gui.screen() as? AbstractContainerScreen<*> ?: return
        if (screen.menu.containerId != containerId) return
        if (TextUtils.stripFormatting(screen.title.string) !in LeapFixRules.MENU_TITLES) return
        val name = LeapFixRules.targetName(screen.menu.getSlot(slotId).item.hoverName.string) ?: return
        val teammate = client.level?.players()?.firstOrNull { it.gameProfile.name == name }
        if (teammate != null) {
            pitch = teammate.xRot
            yaw = teammate.yRot
        } else {
            val marker = FunnyMap.markerOf(name) ?: return
            pitch = 0f
            yaw = marker.yaw
        }
        clickedAt = ticks
    }

    /** A teleport, just applied. */
    @JvmStatic
    fun onTeleport(packet: ClientboundPlayerPositionPacket) {
        val player = Minecraft.getInstance().player ?: return
        val newPitch = pitch ?: return
        val newYaw = yaw ?: return
        pitch = null
        yaw = null
        val relatives = packet.relatives()
        val change = packet.change()
        val relativeRotation = Relative.X_ROT in relatives && Relative.Y_ROT in relatives
        val rotationChanged = change.xRot() != 0f || change.yRot() != 0f
        if (!LeapFixRules.shouldTurn(ticks - clickedAt, ModConfig.getLeapFixTimeout(), relativeRotation, rotationChanged)) return
        player.xRot = newPitch
        player.yRot = newYaw
        // As RSM does: what the game last told the server is set to the new direction too.
        val applied = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(player), change, relatives)
        val accessor = player as LocalPlayerAccessor
        accessor.`frost$setYRotLast`(applied.yRot())
        accessor.`frost$setXRotLast`(applied.xRot())
    }
}
