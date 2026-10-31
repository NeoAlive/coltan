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
 * <p>{@code remap = false} with both names listed: this build has no MixinGradle, so no SRG refmap is
 * written. Dev runs official names ({@code addParticle}); a production Forge client runs SRG
 * ({@code m_7106_} / {@code m_6493_}, from srg_to_official_1.20.1.tsrg). Whichever exists matches.
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
            remap = false
    )
    private void coltan$divertParticle(ParticleOptions options, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, CallbackInfo ci) {
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
            remap = false
    )
    private void coltan$divertParticleForced(ParticleOptions options, boolean force,
            double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfo ci) {
        if (SbwParticleBridge.tryDivert(options, x, y, z, xSpeed, ySpeed, zSpeed)) {
            ci.cancel();
        }
    }
}
