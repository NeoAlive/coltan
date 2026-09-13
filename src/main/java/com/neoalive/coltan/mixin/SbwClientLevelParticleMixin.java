package com.neoalive.coltan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.neoalive.coltan.client.compat.particle.SbwParticleBridge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;

/**
 * Divert SBW soft-sheet ParticleOptions (CustomFlare / Cloud / Smoke / CannonMuzzle) onto GemRender
 * emitters so ParticleEngine never draws the duplicate sheets.
 *
 * <p>{@code remap = false}: compile uses official names; Mixin AP otherwise fails to find SRG
 * mappings for {@code addParticle} under this toolchain.
 */
@Mixin(value = ClientLevel.class, remap = false)
public abstract class SbwClientLevelParticleMixin {
    @Inject(
            method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void coltan$divertParticle(ParticleOptions options, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, CallbackInfo ci) {
        if (SbwParticleBridge.tryDivert(options, x, y, z, xSpeed, ySpeed, zSpeed)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void coltan$divertParticleForced(ParticleOptions options, boolean force,
            double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfo ci) {
        if (SbwParticleBridge.tryDivert(options, x, y, z, xSpeed, ySpeed, zSpeed)) {
            ci.cancel();
        }
    }
}
