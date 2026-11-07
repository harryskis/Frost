package com.frost.mixin;

import com.frost.dungeon.LeapFix;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets Leap Fix turn the player the moment a teleport is applied, once the game has taken the
 * packet's position and confirmed it to the server - the same point RSM's Rotate Fix hooks.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(
        method = "handleMovePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockStatePredictionHandler()Lnet/minecraft/client/multiplayer/prediction/BlockStatePredictionHandler;"
        )
    )
    private void frost$afterTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        LeapFix.onTeleport(packet);
    }
}
