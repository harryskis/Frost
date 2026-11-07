package com.frost.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The rotation the game last sent the server, which Leap Fix sets after turning the player. */
@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    @Accessor("yRotLast")
    void frost$setYRotLast(float yRotLast);

    @Accessor("xRotLast")
    void frost$setXRotLast(float xRotLast);
}
