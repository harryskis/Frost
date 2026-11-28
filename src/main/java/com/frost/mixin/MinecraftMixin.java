package com.frost.mixin;

import com.frost.qol.EtherwarpContainerFix;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets Etherwarp Container Fix skip a right-click's use of the block being looked at, so the click
 * goes on to use the item as if aimed at the air - the same hook as RSM's Cancel Interact.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @WrapOperation(
        method = "startUseItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"
        )
    )
    private InteractionResult frost$skipBlockUse(
        MultiPlayerGameMode gameMode, LocalPlayer player, InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original
    ) {
        if (EtherwarpContainerFix.shouldSkipBlockUse(player, hand, hit)) return InteractionResult.PASS;
        return original.call(gameMode, player, hand, hit);
    }
}
