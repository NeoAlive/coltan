package com.neoalive.coltan.mixin;

import com.atsuishio.superbwarfare.client.lighting.LightPositionRegistry;
import com.neoalive.coltan.ColtanConfig;
import com.neoalive.coltan.client.compat.sbw.light.SbwLightGuard;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional limits on how many SBW sparks exist: camera distance and area light radius. */
@Mixin(value = LightPositionRegistry.class, remap = false)
public abstract class SbwLightSparkLimitsMixin {
    @Inject(method = "putSpark(JIII)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void coltan$distance(
            long packedPos, int level, int minLevel, int duration, CallbackInfo ci) {
        if (SbwLightGuard.tooFar(
                BlockPos.getX(packedPos), BlockPos.getY(packedPos), BlockPos.getZ(packedPos))) {
            ci.cancel();
        }
    }

    @Inject(
            method = "putSparkRadius(Lnet/minecraft/core/BlockPos;IIII)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void coltan$radiusDistance(
            BlockPos center, int level, int minLevel, int duration, int radius, CallbackInfo ci) {
        if (SbwLightGuard.tooFar(center.getX(), center.getY(), center.getZ())) {
            ci.cancel();
        }
    }

    @ModifyVariable(
            method = "putSparkRadius(Lnet/minecraft/core/BlockPos;IIII)V",
            at = @At("HEAD"),
            ordinal = 3,
            argsOnly = true,
            remap = false)
    private static int coltan$radiusCap(int radius) {
        int cap = ColtanConfig.sbwLightRadiusCap;
        return cap >= 0 && radius > cap ? cap : radius;
    }
}
