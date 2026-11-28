package com.frost.util

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

/** Recognizing specific Skyblock items, shared by every feature that cares what's held. */
object SkyblockItems {
    private const val ASPECT_OF_THE_VOID_ID = "ASPECT_OF_THE_VOID"
    private const val ASPECT_OF_THE_VOID_NAME = "Aspect of the Void"
    private val SPIRIT_LEAP_IDS = setOf("SPIRIT_LEAP", "INFINITE_SPIRIT_LEAP")
    private const val SPIRIT_LEAP_NAME = "Spirit Leap"

    /** Hypixel's internal Skyblock item ID from custom data, or null if the item has none. */
    fun skyblockId(stack: ItemStack): String? =
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "")?.takeIf { it.isNotEmpty() }

    /** Hypixel tags every Skyblock item with its internal ID in custom data, which (unlike
     * the display name) survives reforges, stars, and renames - that's checked first. The name
     * fallback covers practice servers (e.g. Clear Hub) whose items are plain vanilla items
     * named like Skyblock ones, with no Skyblock ID attached; "contains" so a reforge prefix
     * like "Heroic" doesn't break it. */
    fun isAspectOfTheVoid(stack: ItemStack): Boolean {
        skyblockId(stack)?.let { return it == ASPECT_OF_THE_VOID_ID }
        return TextUtils.stripFormatting(stack.hoverName.string).contains(ASPECT_OF_THE_VOID_NAME, ignoreCase = true)
    }

    /** A Spirit Leap or Infinileap, by ID - or by name on servers whose items have none. */
    fun isSpiritLeap(stack: ItemStack): Boolean {
        skyblockId(stack)?.let { return it in SPIRIT_LEAP_IDS }
        val name = TextUtils.stripFormatting(stack.hoverName.string)
        return name.contains(SPIRIT_LEAP_NAME, ignoreCase = true) || name.contains("Infinileap", ignoreCase = true)
    }

    private const val ETHERWARP_CONDUIT_ID = "ETHERWARP_CONDUIT"

    /** The Etherwarp Conduit, which etherwarps on any right-click, sneaking or not. */
    fun isEtherwarpConduit(stack: ItemStack): Boolean = skyblockId(stack) == ETHERWARP_CONDUIT_ID

    /** An item that etherwarps when right-clicked while sneaking: one with Etherwarp merged into it
     * (an Aspect of the Void, usually), or the Etherwarp Conduit - the same check Odin makes. */
    fun canEtherwarp(stack: ItemStack): Boolean {
        if (isEtherwarpConduit(stack)) return true
        val data = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return false
        return data.getIntOr("ethermerge", 0) == 1
    }
}
