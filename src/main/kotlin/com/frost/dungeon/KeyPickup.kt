package com.frost.dungeon

import com.frost.Frost
import com.frost.config.ModConfig
import com.frost.gui.KeyPickupMoveScreen
import com.frost.parsing.ChatParser
import com.frost.parsing.DungeonKey
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import java.util.EnumMap

/**
 * Key Pickup: when a wither or blood key is picked up, shows "Wither Key Picked Up" (or Blood)
 * on screen - Pickup Notification - and plays a chosen sound - Pickup Sound. Either can be
 * turned off. Both go by the chat line Hypixel sends for every pickup by anyone in the party.
 */
object KeyPickup {
    private val HUD_ID = Identifier.fromNamespaceAndPath(Frost.MOD_ID, "key_pickup")
    private const val PERCENT = 100f
    private const val MILLIS_PER_SECOND = 1000L

    private val lastPickup = EnumMap<DungeonKey, Long>(DungeonKey::class.java)
    private var shown: DungeonKey? = null
    private var shownAt = 0L

    fun register() {
        HudElementRegistry.addLast(HUD_ID) { context, _ -> drawHud(context) }
    }

    /** Fed every chat line by Frost's own listener. */
    fun onChat(text: String) {
        val key = ChatParser.parseKeyPickup(text) ?: return
        if (!ModConfig.isKeyPickupEnabled()) return
        val now = System.currentTimeMillis()
        if (KeyPickupTiming.isSamePickup(lastPickup[key], now)) return
        lastPickup[key] = now
        if (ModConfig.isKeyNotificationEnabled()) {
            shown = key
            shownAt = now
        }
        if (ModConfig.isKeySoundEnabled()) playSound()
    }

    fun text(key: DungeonKey): String = "${key.label} Picked Up"

    fun color(key: DungeonKey): Int = when (key) {
        DungeonKey.WITHER -> ModConfig.getWitherKeyColor()
        DungeonKey.BLOOD -> ModConfig.getBloodKeyColor()
    }

    /** Every sound in the game by ID, sorted, for the sound picker. */
    val allSounds: List<String> by lazy { BuiltInRegistries.SOUND_EVENT.keySet().map { it.toString() }.sorted() }

    /** The sound event saved by [id], or null if the game has no such sound. */
    fun soundEvent(id: String): SoundEvent? =
        Identifier.tryParse(id)?.let { BuiltInRegistries.SOUND_EVENT.getOptional(it).orElse(null) }

    /** The chosen sound at its chosen volume and pitch. Also the test button in the menu. */
    fun playSound() {
        val sound = soundEvent(ModConfig.getKeySound()) ?: return
        val volume = ModConfig.getKeySoundVolume() / PERCENT
        val pitch = ModConfig.getKeySoundPitch() / PERCENT
        if (volume <= 0f) return
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(sound, pitch, volume))
    }

    private fun drawHud(context: GuiGraphicsExtractor) {
        val key = shown ?: return
        val client = Minecraft.getInstance()
        // The move screen draws its own copy where it's being dragged.
        if (client.gui.screen() is KeyPickupMoveScreen) return
        val duration = ModConfig.getKeyNotificationSeconds() * MILLIS_PER_SECOND
        val opacity = KeyPickupTiming.opacity(System.currentTimeMillis() - shownAt, duration)
        if (opacity <= 0f) {
            shown = null
            return
        }
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        drawText(
            context,
            text(key),
            KeyPickupTiming.withOpacity(color(key), opacity),
            ModConfig.getKeyNotificationX() * width,
            ModConfig.getKeyNotificationY() * height,
            ModConfig.getKeyNotificationSize(),
        )
    }

    /** [text] centered on ([centerX], [centerY]) at [sizePercent] of normal text size. */
    fun drawText(context: GuiGraphicsExtractor, text: String, color: Int, centerX: Float, centerY: Float, sizePercent: Int) {
        val font = Minecraft.getInstance().font
        val scale = sizePercent / PERCENT
        context.pose().pushMatrix()
        context.pose().translate(centerX, centerY)
        context.pose().scale(scale, scale)
        context.centeredText(font, text, 0, -font.lineHeight / 2, color)
        context.pose().popMatrix()
    }

    /** How wide and tall [text] is on screen at [sizePercent]. */
    fun textSize(text: String, sizePercent: Int): Pair<Float, Float> {
        val font = Minecraft.getInstance().font
        val scale = sizePercent / PERCENT
        return font.width(text) * scale to font.lineHeight * scale
    }
}
