package com.neoalive.coltan.mixin;

import com.neoalive.coltan.client.compat.sbw.particle.SbwParticleBridge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * remap = false and both names: no SRG refmap. Dev matches addParticle. Production matches m_7106_
 * and m_6493_.
 */
@Mixin(value = ClientLevel.class, remap = false)
public abstract class SbwClientLevelParticleMixin {
    @Inject(
            method = {
                "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
                "m_7106_(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void coltan$divertParticle(
            ParticleOptions options,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            CallbackInfo ci) {
        if (SbwParticleBridge.tryDivert(options, x, y, z, xSpeed, ySpeed, zSpeed)) {
            ci.cancel();
        }
    }

    @Inject(
            method = {
                "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V",
                "m_6493_(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void coltan$divertParticleForced(
            ParticleOptions options,
            boolean force,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            CallbackInfo ci) {
        if (SbwParticleBridge.tryDivert(options, x, y, z, xSpeed, ySpeed, zSpeed)) {
            ci.cancel();
        }
    }
}
