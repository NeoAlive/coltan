package com.neoalive.coltan.client.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.atsuishio.superbwarfare.Mod;
import com.atsuishio.superbwarfare.client.renderer.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Muzzle flares for SBW vehicles. SBW draws its shared {@code muzzle_flare} model on every bone named
 * {@code flare*} and shows or hides it purely through the bone's animated scale (idle clip = 0, fire
 * clip = a short pulse). The bridge keeps those bones out of the Flywheel pass, so the flash is drawn here
 * instead, as an emissive overlay at each flare part's live world pose (which already carries that scale).
 *
 * <p>Geometry mirrors {@code muzzle_flare.geo.json} at scale 1 (geo units / 16): a 1.455 square front disc
 * plus three 3.66 x 1.215 blades 60 degrees apart, reaching from z -2.63 to +1.03 (forward is -Z).
 */
public final class SbwVehicleFlare {
    private static final Map<Integer, SbwVehicleGemVisual> LIVE = new ConcurrentHashMap<>();
    private static final ResourceLocation FLARE_TEXTURE = Mod.loc("textures/particle/flare.png");
    private static final float U = 1.0f / 16.0f;
    private static final float DISC = 1.455f * U;
    private static final float BLADE_WIDTH = 1.215f * U;
    private static final float BLADE_FRONT = -2.63f * U;
    private static final float BLADE_BACK = 1.03f * U;
    static final double MAX_DISTANCE_SQ = 96.0 * 96.0;
    private static final Matrix4f SCRATCH = new Matrix4f();
    /** Blades sit 60 degrees apart; {@code mulPose} only reads these, so they are shared. */
    private static final Quaternionf[] BLADE_ROTATIONS = {
            Axis.ZP.rotationDegrees(0.0f), Axis.ZP.rotationDegrees(60.0f), Axis.ZP.rotationDegrees(120.0f)};
    /** Per-flare roll jitter, rewritten in place (render thread only). */
    private static final Quaternionf JITTER = new Quaternionf();

    private SbwVehicleFlare() {
    }

    public static void register(SbwVehicleGemVisual visual) {
        LIVE.put(visual.vehicle().getId(), visual);
    }

    public static void unregister(SbwVehicleGemVisual visual) {
        LIVE.remove(visual.vehicle().getId(), visual);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || LIVE.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        RenderType type = ModRenderTypes.MUZZLE_FLASH_TYPE.apply(FLARE_TEXTURE);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean drew = false;

        for (SbwVehicleGemVisual visual : LIVE.values()) {
            if (!visual.shouldDrawFlares()
                    || visual.vehicle().distanceToSqr(cam.x, cam.y, cam.z) > MAX_DISTANCE_SQ) {
                continue;
            }
            for (int part : visual.flareParts()) {
                Matrix4f world = visual.flareWorld(part, SCRATCH);
                if (world == null) {
                    continue;
                }
                float scaleSq = world.m00() * world.m00() + world.m01() * world.m01() + world.m02() * world.m02();
                if (scaleSq < 1.0e-6f) {
                    continue; // idle clip keeps the bone at scale 0
                }

                poseStack.pushPose();
                try {
                    poseStack.translate(-cam.x, -cam.y, -cam.z);
                    poseStack.last().pose().mul(world);
                    poseStack.mulPose(JITTER.rotationZ(0.15f * (random.nextFloat() - 0.5f)));

                    Matrix4f mat = poseStack.last().pose();
                    Matrix3f normal = poseStack.last().normal();
                    VertexConsumer consumer = buffers.getBuffer(type);
                    quad(consumer, mat, normal, -DISC / 2, -DISC / 2, DISC / 2, DISC / 2, -0.012f * U);
                    for (Quaternionf bladeRotation : BLADE_ROTATIONS) {
                        poseStack.pushPose();
                        poseStack.mulPose(bladeRotation);
                        blade(consumer, poseStack.last().pose(), poseStack.last().normal());
                        poseStack.popPose();
                    }
                    drew = true;
                } finally {
                    poseStack.popPose();
                }
            }
        }

        if (drew) {
            buffers.endBatch(type);
        }
    }

    /** Blade in the local XZ plane: width along X, length along Z. */
    private static void blade(VertexConsumer consumer, Matrix4f pose, Matrix3f normal) {
        float h = BLADE_WIDTH / 2;
        vertex(consumer, pose, normal, -h, 0f, BLADE_BACK, 0, 1);
        vertex(consumer, pose, normal, h, 0f, BLADE_BACK, 1, 1);
        vertex(consumer, pose, normal, h, 0f, BLADE_FRONT, 1, 0);
        vertex(consumer, pose, normal, -h, 0f, BLADE_FRONT, 0, 0);
    }

    /** Quad in the local XY plane at depth z. */
    private static void quad(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
            float x0, float y0, float x1, float y1, float z) {
        vertex(consumer, pose, normal, x0, y0, z, 0, 1);
        vertex(consumer, pose, normal, x1, y0, z, 1, 1);
        vertex(consumer, pose, normal, x1, y1, z, 1, 0);
        vertex(consumer, pose, normal, x0, y1, z, 0, 0);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
            float x, float y, float z, float u, float v) {
        consumer.vertex(pose, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0f, 0f, 1f)
                .endVertex();
    }
}
