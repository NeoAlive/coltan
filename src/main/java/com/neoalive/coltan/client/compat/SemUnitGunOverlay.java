package com.neoalive.coltan.client.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.wf.gemrender.render.PoseCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Draws SEM-held TaCZ guns after the GemRender body pass.
 *
 * <p>Mirrors {@code GunLayerRenderer} offsets on the posed {@code rightArm} socket. Full parent
 * chain is included via the bone palette so the gun tracks {@code fakeRoot}/{@code unit} pitch.
 */
public final class SemUnitGunOverlay {
    private static final Map<Integer, SemUnitGemVisual> LIVE = new ConcurrentHashMap<>();
    private static final Matrix4f ARM_SCRATCH = new Matrix4f();

    private SemUnitGunOverlay() {
    }

    public static void register(SemUnitGemVisual visual) {
        LIVE.put(visual.entity().getId(), visual);
    }

    public static void unregister(SemUnitGemVisual visual) {
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
        float partialTick = event.getPartialTick();

        for (SemUnitGemVisual visual : LIVE.values()) {
            Entity entity = visual.entity();
            if (!(entity instanceof LivingEntity living) || living.isDeadOrDying() || living.isRemoved()) {
                continue;
            }

            ItemStack stack = living.getItemInHand(InteractionHand.MAIN_HAND);
            if (!(stack.getItem() instanceof AbstractGunItem gunItem)) {
                continue;
            }

            PoseCache.Pose posed = visual.pose();
            if (posed == null) {
                continue;
            }

            poseStack.pushPose();
            try {
                poseStack.translate(-cam.x, -cam.y, -cam.z);
                Matrix4f world = poseStack.last().pose();
                world.mul(visual.lastWorldPose());
                posed.boneMatrix("rightArm", ARM_SCRATCH);
                world.mul(ARM_SCRATCH);

                ResourceLocation gunId = gunItem.getGunId(stack);
                boolean minigun = gunId != null && gunId.getPath().contains("minigun");
                if (minigun) {
                    poseStack.translate(-0.03D, 0.85D, 0.1D);
                    poseStack.mulPose(Axis.YP.rotationDegrees(-180.0f));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-85.0f));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(6.0f));
                } else {
                    poseStack.translate(-0.06D, 0.73D, 0.3D);
                    poseStack.mulPose(Axis.YP.rotationDegrees(-180.0f));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
                }
                poseStack.scale(1.0F, -1.0F, -1.0F);

                int light = mc.getEntityRenderDispatcher().getPackedLightCoords(living, partialTick);
                mc.gameRenderer.itemInHandRenderer.renderItem(
                        living,
                        stack,
                        ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        false,
                        poseStack,
                        buffers,
                        light);
            } finally {
                poseStack.popPose();
            }
        }

        buffers.endBatch();
    }
}
