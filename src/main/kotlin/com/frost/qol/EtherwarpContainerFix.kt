package com.frost.qol

import com.frost.config.ModConfig
import com.frost.util.SkyblockItems
import net.minecraft.client.player.LocalPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.AbstractSkullBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.BlockHitResult

/**
 * Etherwarp Container Fix: etherwarping onto a block close enough to reach that does something
 * when right-clicked - a crafting table, hopper, cauldron, furnace and so on - used that block
 * instead of etherwarping. With an etherwarp item ready (sneaking, or any time with an Etherwarp
 * Conduit), the block is skipped and the click goes through as if aimed at the air, which is
 * what etherwarps. Hooked in by [com.frost.mixin.MinecraftMixin], like RSM's Cancel Interact (BSD
 * 3-Clause, see /THIRD_PARTY_NOTICES.md at the repo root).
 *
 * Levers, skulls, chests and buttons are left alone: they're dungeon secrets, and right-clicking
 * them while holding the item has to keep working.
 */
object EtherwarpContainerFix {
    @JvmStatic
    fun shouldSkipBlockUse(player: LocalPlayer, hand: InteractionHand, hit: BlockHitResult): Boolean {
        if (!ModConfig.isEtherwarpContainerFixEnabled()) return false
        val item = player.getItemInHand(hand)
        val ready = SkyblockItems.isEtherwarpConduit(item) || (player.isShiftKeyDown && SkyblockItems.canEtherwarp(item))
        if (!ready) return false
        val state = player.level().getBlockState(hit.blockPos)
        val block = state.block
        val secret = block is LeverBlock || block is AbstractSkullBlock || block is ChestBlock ||
            state.`is`(BlockTags.BUTTONS) || state.`is`(BlockTags.COPPER_CHESTS)
        return !secret
    }
}
