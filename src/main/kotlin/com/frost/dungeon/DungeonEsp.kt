package com.frost.dungeon

import com.frost.config.ModConfig
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

/**
 * The boxes drawn through walls: Star Mob ESP around starred mobs, and Fairy Door ESP around the
 * fairy room's way out. Mobs are found once a tick, the same way Odin does it - the star is only
 * in the floating name, which is its own armor stand, so the mob is whatever's just below that.
 * The drawing happens every frame, so the boxes glide with the mobs instead of stepping.
 */
object DungeonEsp {
    private const val STROKE_WIDTH = 2f

    // Pink, like the fairy room on the map, and a see-through version to fill the door with.
    private val FAIRY_DOOR_STROKE = 0xFFFF6EC7.toInt()
    private val FAIRY_DOOR_FILL = 0x50FF6EC7

    private val starMobs = mutableListOf<LivingEntity>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> onTick(client) }
        LevelRenderEvents.START_MAIN.register { onFrame() }
    }

    private fun onTick(client: Minecraft) {
        starMobs.clear()
        val level = client.level
        if (level == null || !FunnyMap.inCatacombs || !ModConfig.isStarMobEspEnabled()) return
        for (entity in level.entitiesForRendering()) {
            if (entity !is ArmorStand || !entity.isAlive || FunnyMap.STAR !in entity.name.string) continue
            findMobBelow(level, entity, client)?.let { if (it !in starMobs) starMobs += it }
        }
    }

    private fun findMobBelow(level: ClientLevel, nameTag: ArmorStand, client: Minecraft): LivingEntity? =
        level.getEntities(nameTag, nameTag.boundingBox.move(0.0, -1.0, 0.0)) { isMob(it, client) }.firstOrNull() as LivingEntity?

    /** Hypixel's mobs can be fake players, so being a player doesn't rule one out - only being a
     * real party member does. */
    private fun isMob(entity: Entity, client: Minecraft): Boolean {
        if (entity !is LivingEntity || entity is ArmorStand || !entity.isAlive || entity === client.player) return false
        return entity !is Player || entity.gameProfile.name !in FunnyMap.partyNames
    }

    private fun onFrame() {
        val client = Minecraft.getInstance()
        if (client.level == null || !FunnyMap.inCatacombs) return
        val doors = if (ModConfig.isFairyDoorEspEnabled()) FunnyMap.fairyExitDoors else emptyList()
        if (starMobs.isEmpty() && doors.isEmpty()) return
        val partialTick = client.deltaTracker.getGameTimeDeltaPartialTick(false)
        val color = ModConfig.getStarMobEspColor()
        val mobStyle = if (ModConfig.isStarMobEspFilled()) {
            // Half as solid as the outline, so a filled box doesn't hide the mob inside it.
            val fillAlpha = (color ushr 24) / 2
            GizmoStyle.strokeAndFill(color, STROKE_WIDTH, (color and 0xFFFFFF) or (fillAlpha shl 24))
        } else {
            GizmoStyle.stroke(color, STROKE_WIDTH)
        }
        val doorStyle = GizmoStyle.strokeAndFill(FAIRY_DOOR_STROKE, STROKE_WIDTH, FAIRY_DOOR_FILL)
        // See AimAssist for why this is explicitly scoped to the renderer's per-frame collector.
        client.levelRenderer.collectPerFrameRenderThreadGizmos().use {
            for (mob in starMobs) {
                if (mob.isAlive) Gizmos.cuboid(smoothedBox(mob, partialTick), mobStyle).setAlwaysOnTop()
            }
            for (door in doors) Gizmos.cuboid(doorBox(door), doorStyle).setAlwaysOnTop()
        }
    }

    private fun smoothedBox(entity: Entity, partialTick: Float): AABB {
        val offset = entity.getPosition(partialTick).subtract(entity.position())
        return entity.boundingBox.move(offset)
    }

    private fun doorBox(gap: MapGap): AABB {
        val (x, z) = FunnyMapLayout.gapColumn(gap)
        val y = FunnyMapLayout.DOOR_OPENING_Y
        return AABB(x - 1.0, y.first.toDouble(), z - 1.0, x + 2.0, y.last + 1.0, z + 2.0)
    }
}
