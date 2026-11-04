package com.neoalive.coltan.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.neoalive.coltan.client.compat.fx.ColtanFx;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks for Coltan's particle pipeline. remap = false with dev and SRG names: add is m_107344_,
 * tick m_107388_, clearParticles m_107301_, PoseStack.popPose m_85849_. render with a Frustum is
 * Forge's own method and keeps its name.
 */
@Mixin(value = ParticleEngine.class, remap = false)
public abstract class ParticleEngineFxMixin {
    @Inject(
            method = {
                "add(Lnet/minecraft/client/particle/Particle;)V",
                "m_107344_(Lnet/minecraft/client/particle/Particle;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void coltan$fxCapture(Particle particle, CallbackInfo ci) {
        if (ColtanFx.capture(particle)) {
            ci.cancel();
        }
    }

    @Inject(
            method = {"tick()V", "m_107388_()V"},
            at = @At("TAIL"),
            remap = false)
    private void coltan$fxTick(CallbackInfo ci) {
        ColtanFx.tick();
    }

    @Inject(
            method =
                    "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",
            at = {
                @At(
                        value = "INVOKE",
                        target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                        remap = false),
                @At(
                        value = "INVOKE",
                        target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85849_()V",
                        remap = false)
            },
            remap = false)
    private void coltan$fxRender(
            PoseStack poseStack,
            MultiBufferSource.BufferSource buffers,
            LightTexture lightTexture,
            Camera camera,
            float partialTick,
            Frustum frustum,
            CallbackInfo ci) {
        ColtanFx.render((ParticleEngine) (Object) this, camera, partialTick);
    }

    @Inject(
            method = {"clearParticles()V", "m_107301_()V"},
            at = @At("HEAD"),
            remap = false)
    private void coltan$fxClear(CallbackInfo ci) {
        ColtanFx.clear();
    }
}
