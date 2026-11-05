package com.frost.dungeon

import com.frost.config.ModConfig
import com.frost.gui.TextAlerts
import com.frost.parsing.ChatParser
import com.frost.parsing.DungeonKey
import com.frost.util.Sounds
import java.util.EnumMap

/**
 * Key Pickup: when a wither or blood key is picked up, shows "Wither Key Picked Up" (or Blood)
 * on screen - Pickup Notification - and plays a chosen sound - Pickup Sound. Either can be
 * turned off. Both go by the chat line Hypixel sends for every pickup by anyone in the party.
 */
object KeyPickup {
    private const val MILLIS_PER_SECOND = 1000L

    private val lastPickup = EnumMap<DungeonKey, Long>(DungeonKey::class.java)

    /** Fed every chat line by Frost's own listener. */
    fun onChat(text: String) {
        val key = ChatParser.parseKeyPickup(text) ?: return
        if (!ModConfig.isKeyPickupEnabled()) return
        val now = System.currentTimeMillis()
        if (KeyPickupTiming.isSamePickup(lastPickup[key], now)) return
        lastPickup[key] = now
        if (ModConfig.isKeyNotificationEnabled()) {
            TextAlerts.show(
                this,
                text(key),
                color(key),
                ModConfig.getKeyNotificationX(),
                ModConfig.getKeyNotificationY(),
                ModConfig.getKeyNotificationSize(),
                ModConfig.getKeyNotificationSeconds() * MILLIS_PER_SECOND,
            )
        }
        if (ModConfig.isKeySoundEnabled()) playSound()
    }

    fun text(key: DungeonKey): String = "${key.label} Picked Up"

    fun color(key: DungeonKey): Int = when (key) {
        DungeonKey.WITHER -> ModConfig.getWitherKeyColor()
        DungeonKey.BLOOD -> ModConfig.getBloodKeyColor()
    }

    /** The chosen sound at its chosen volume and pitch. Also the test button in the menu. */
    fun playSound() = Sounds.play(ModConfig.getKeySound(), ModConfig.getKeySoundVolume(), ModConfig.getKeySoundPitch())
}
