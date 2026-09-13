package com.neoalive.coltan.client.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Emissive flare overlay for SBW projectiles — mirrors
 * {@code BasicProjectileRenderer}'s {@code RenderType.eyes} single-bone pass with a centered quad
 * at the {@code flare} socket (Flywheel body keeps the bone NodeHide'd).
 */
public final class SbwProjectileFlare {
    private static final Map<Integer, SbwProjectileGemVisual> LIVE = new ConcurrentHashMap<>();
    private static final ResourceLocation FLARE_TEXTURE =
            new ResourceLocation("superbwarfare", "textures/bedrock/projectile/flare.png");
    private static final float Z_JITTER_DEG = 1.25f;

    private SbwProjectileFlare() {
    }

    public static void register(SbwProjectileGemVisual visual) {
        LIVE.put(visual.entity().getId(), visual);
    }

    public static void unregister(SbwProjectileGemVisual visual) {
        LIVE.remove(visual.entity().getId(), visual);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || LIVE.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        RenderType eyes = RenderType.eyes(FLARE_TEXTURE);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (SbwProjectileGemVisual visual : LIVE.values()) {
            if (!visual.shouldDrawFlare()) {
                continue;
            }

            poseStack.pushPose();
            try {
                poseStack.translate(-cam.x, -cam.y, -cam.z);
                // Rest socket: live palette scales flare to 0 via NodeHide.
                poseStack.last().pose().mul(visual.lastWorldPose()).mul(visual.flareRestSocket());

                float zRot = (random.nextFloat() * 2.0f - 1.0f) * Z_JITTER_DEG * Mth.DEG_TO_RAD;
                poseStack.mulPose(Axis.ZP.rotation(zRot));

                float sx = (2.0f * random.nextFloat() - 1.0f) * 0.4f + 1.6f;
                float sy = (2.0f * random.nextFloat() - 1.0f) * 0.4f + 1.6f;
                float sz = (2.0f * random.nextFloat() - 1.0f) * 0.4f + 1.6f;
                poseStack.scale(sx, sy, sz);

                Matrix4f mat = poseStack.last().pose();
                Matrix3f normal = poseStack.last().normal();
                VertexConsumer consumer = buffers.getBuffer(eyes);
                vertex(consumer, mat, normal, 0f, 0f, 0, 1);
                vertex(consumer, mat, normal, 1f, 0f, 1, 1);
                vertex(consumer, mat, normal, 1f, 1f, 1, 0);
                vertex(consumer, mat, normal, 0f, 1f, 0, 0);
            } finally {
                poseStack.popPose();
            }
        }

        buffers.endBatch(eyes);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
            float u, float v, int x, int y) {
        consumer.vertex(pose, x - 0.5f, y - 0.5f, 0f)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(normal, 0f, 0f, 1f)
                .endVertex();
    }
}
