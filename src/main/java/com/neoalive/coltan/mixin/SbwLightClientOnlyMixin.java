package com.neoalive.coltan.mixin;

import com.atsuishio.superbwarfare.client.lighting.LightPositionRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.neoalive.coltan.ColtanConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SBW's light mixins sit in its client list, but in singleplayer they also patch the integrated
 * server's BlockLightEngine (one class, one JVM). The server lighting thread then reads the client
 * spark map and bakes spark light into the saved world, where nothing ever clears it. Both mixins
 * read sparks only through getLevel, so answering "no spark" off the render thread fences them.
 */
@Mixin(value = LightPositionRegistry.class, remap = false)
public abstract class SbwLightClientOnlyMixin {
    @Inject(method = "getLevel(J)I", at = @At("HEAD"), cancellable = true, remap = false)
    private static void coltan$clientOnly(long packedPos, CallbackInfoReturnable<Integer> cir) {
        if (ColtanConfig.sbwLightClientOnly && !RenderSystem.isOnRenderThread()) {
            cir.setReturnValue(-1);
        }
    }
}
