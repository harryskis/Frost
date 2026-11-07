package com.frost.dungeon

import com.frost.config.ModConfig
import com.frost.parsing.BatKill
import com.frost.parsing.MimicKill
import com.frost.parsing.PrinceKill
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.zombie.Zombie

/**
 * Mimic: tells the party in party chat when this player kills the Mimic, a prince or a bat -
 * "Mimic Killed!", "Prince Killed!" and "Bat Killed!", the messages other dungeon mods send and
 * listen for, so the whole party's score and maps know.
 *
 * A prince or bat kill is Hypixel's own "+1 Bonus Score" line, which only the killer sees. The
 * Mimic, on floors 6 and 7, has no such line: like Odin, it's spotted dying in the room clear, as
 * it's the only baby zombie there - or by the charm message, when it's charmed. Each of the Mimic
 * and the prince is only announced once a run, and not at all if a party member has already said.
 */
object Mimic {
    private const val MIMIC_MESSAGE = "Mimic Killed!"
    private const val PRINCE_MESSAGE = "Prince Killed!"
    private const val BAT_MESSAGE = "Bat Killed!"
    private val MIMIC_FLOORS = 6..7

    private var floor: Int? = null
    private var mimicDone = false
    private var princeDone = false
    private var level: ClientLevel? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        // Hypixel can take the Mimic away the moment it dies, before a tick sees it dying, so
        // it's checked on the way out too.
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ -> checkForMimic(entity) }
    }

    /** Fed the floor from the scoreboard, like "M6" or "VII". */
    fun onFloor(raw: String) {
        floor = DungeonFloor.scoreboardNumber(raw)
    }

    /** Fed every chat line by Frost's own listener. */
    fun onChat(text: String) {
        if (!FunnyMap.inCatacombs) return
        when {
            MimicKill.isAnnounced(text) -> mimicDone = true
            MimicKill.isCharmed(text) -> mimicKilled()
            PrinceKill.isOwnKill(text) -> {
                if (!princeDone) announce(PRINCE_MESSAGE, ModConfig.isPrinceMessageEnabled())
                princeDone = true
            }
            PrinceKill.isKill(text) -> princeDone = true
            BatKill.isOwnKill(text) -> announce(BAT_MESSAGE, ModConfig.isBatMessageEnabled())
        }
    }

    private fun onTick(client: Minecraft) {
        val current = client.level
        if (current !== level) {
            // A new level is a new run.
            level = current
            floor = null
            mimicDone = false
            princeDone = false
        }
        if (current == null || !watchingForMimic()) return
        for (entity in current.entitiesForRendering()) checkForMimic(entity)
    }

    /** Only in the room clear of floors 6 and 7 - the boss rooms have baby zombies of their own. */
    private fun watchingForMimic(): Boolean {
        if (mimicDone || !FunnyMap.inCatacombs || floor !in MIMIC_FLOORS) return false
        val player = Minecraft.getInstance().player ?: return false
        val inGrid = 0 until FunnyMapLayout.GRID_SIZE
        return FunnyMapLayout.worldToTile(player.x) in inGrid && FunnyMapLayout.worldToTile(player.z) in inGrid
    }

    private fun checkForMimic(entity: Entity) {
        if (entity is Zombie && entity.isBaby && entity.isDeadOrDying && watchingForMimic()) mimicKilled()
    }

    private fun mimicKilled() {
        if (mimicDone) return
        mimicDone = true
        announce(MIMIC_MESSAGE, ModConfig.isMimicMessageEnabled())
    }

    private fun announce(message: String, enabled: Boolean) {
        if (!ModConfig.isMimicEnabled() || !enabled) return
        Minecraft.getInstance().player?.connection?.sendCommand("pc $message")
    }
}
