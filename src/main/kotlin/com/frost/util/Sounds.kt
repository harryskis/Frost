package com.frost.util

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent

/** Playing a Minecraft sound picked in the menu, by its ID, like "minecraft:block.note_block.pling". */
object Sounds {
    private const val PERCENT = 100f

    /** Every sound in the game by ID, sorted, for the sound picker. */
    val all: List<String> by lazy { BuiltInRegistries.SOUND_EVENT.keySet().map { it.toString() }.sorted() }

    /** The sound event saved as [id], or null if the game has no such sound. */
    fun event(id: String): SoundEvent? =
        Identifier.tryParse(id)?.let { BuiltInRegistries.SOUND_EVENT.getOptional(it).orElse(null) }

    /** Plays [id] for this player only, at [volumePercent] and [pitchPercent] (100 is normal). */
    fun play(id: String, volumePercent: Int, pitchPercent: Int) {
        val sound = event(id) ?: return
        if (volumePercent <= 0) return
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(sound, pitchPercent / PERCENT, volumePercent / PERCENT))
    }
}
