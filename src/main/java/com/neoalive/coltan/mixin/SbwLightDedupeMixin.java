package com.neoalive.coltan.mixin;

import com.atsuishio.superbwarfare.client.lighting.LightPositionRegistry;
import com.neoalive.coltan.client.compat.sbw.light.SbwLightRelight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SBW re-queues a block light check for every live spark on every client tick, although a spark
 * only changes brightness about fifteen times in its life. Ordinal 0 is the live spark check,
 * ordinal 1 the final check for an expired spark, which is never deferred. Each redirect comes
 * twice, for the dev name and for the SRG name m_7174_ of checkBlock.
 */
@Mixin(value = LightPositionRegistry.class, remap = false)
public abstract class SbwLightDedupeMixin {
    private static final String CHECK_DEV =
            "Lnet/minecraft/world/level/lighting/LevelLightEngine;checkBlock(Lnet/minecraft/core/BlockPos;)V";
    private static final String CHECK_SRG =
            "Lnet/minecraft/world/level/lighting/LevelLightEngine;m_7174_(Lnet/minecraft/core/BlockPos;)V";

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private static void coltan$beginTick(CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            SbwLightRelight.beginTick(level.getLightEngine());
        }
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = CHECK_SRG, ordinal = 0, remap = false),
            remap = false)
    private static void coltan$liveSrg(LevelLightEngine engine, BlockPos pos) {
        live(engine, pos);
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = CHECK_DEV, ordinal = 0, remap = false),
            remap = false)
    private static void coltan$liveDev(LevelLightEngine engine, BlockPos pos) {
        live(engine, pos);
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = CHECK_SRG, ordinal = 1, remap = false),
            remap = false)
    private static void coltan$expiredSrg(LevelLightEngine engine, BlockPos pos) {
        expired(engine, pos);
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = CHECK_DEV, ordinal = 1, remap = false),
            remap = false)
    private static void coltan$expiredDev(LevelLightEngine engine, BlockPos pos) {
        expired(engine, pos);
    }

    private static void live(LevelLightEngine engine, BlockPos pos) {
        if (SbwLightRelight.changed(pos.asLong())) {
            SbwLightRelight.queueLive(engine, pos);
        }
    }

    private static void expired(LevelLightEngine engine, BlockPos pos) {
        SbwLightRelight.forget(pos.asLong());
        engine.checkBlock(pos);
    }
}
