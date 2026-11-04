package com.neoalive.coltan.mixin;

import com.neoalive.coltan.client.compat.fx.OculusParticles;
import java.util.Map;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Oculus draws mod particle render types in both of its passes. A null queue in the OPAQUE pass
 * skips the type there, so it is drawn once. Oculus redirects keySet in the same method, not get.
 */
@Mixin(value = ParticleEngine.class, remap = false)
public abstract class ParticleEngineOculusPhaseMixin {
    @Redirect(
            method =
                    "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;",
                            remap = false),
            remap = false)
    private Object coltan$singleDraw(Map<?, ?> particles, Object type) {
        if (type instanceof ParticleRenderType renderType
                && OculusParticles.skipInOpaquePass((ParticleEngine) (Object) this, renderType)) {
            return null;
        }
        return particles.get(type);
    }
}
