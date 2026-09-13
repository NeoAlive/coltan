package com.neoalive.coltan.client.compat;

import org.joml.Matrix4f;

import com.atsuishio.superbwarfare.resource.gun.GunResource;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.neoalive.coltan.client.compat.bridge.GunVisibilityClip;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.NodeTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Player-skin FP arms parented to Lefthand/Righthand — mirrors SBW {@code AnimationHelper.renderArms}.
 *
 * <p>Hand bones stay {@link NodeHide}d on the gun mesh (gun atlas). Arm placement evaluates the same
 * motion clip <em>without</em> hand hides so the matrices are not collapsed.
 */
public final class SbwGunArms {
    private static final float SCALE_RECIPROCAL = 1.0f / 16.0f;
    private static final GltfPose.Scratch SCRATCH = new GltfPose.Scratch();

    private SbwGunArms() {
    }

    public static void render(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, GemRenderGltfModel model, GltfAnimation motion,
            float seconds, float itemScale) {
        if (!context.firstPerson()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || model == null) {
            return;
        }

        GltfAnimation armClip = GunVisibilityClip.armPoseClip(model, stack, context, motion);
        NodeTable table = model.layout().nodeTable();
        int left = table.slotOfName("Lefthand");
        int right = table.slotOfName("Righthand");
        if (left < 0 && right < 0) {
            return;
        }

        Matrix4f[] palette = SCRATCH.palette(model.jointCount());
        float t = motion != null ? motion.loop(seconds) : 0.0f;
        if (armClip != null) {
            t = armClip.loop(seconds);
        }
        GltfPose.evaluate(model.layout(), armClip, t, palette, model.morphs(), null, SCRATCH);

        PlayerRenderer renderer =
                (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        PlayerModel<AbstractClientPlayer> playerModel = renderer.getModel();
        boolean oldHands = GunResource.from(stack).compute().useOldHandRenderer;

        pose.pushPose();
        try {
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.scale(itemScale, itemScale, itemScale);

            if (left >= 0) {
                drawArm(player, playerModel, HumanoidArm.LEFT, pose, buffers, light, palette[left],
                        oldHands);
            }
            if (right >= 0) {
                drawArm(player, playerModel, HumanoidArm.RIGHT, pose, buffers, light, palette[right],
                        oldHands);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void drawArm(LocalPlayer player, PlayerModel<AbstractClientPlayer> model,
            HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light, Matrix4f bone,
            boolean oldHands) {
        pose.pushPose();
        try {
            pose.last().pose().mul(bone);
            float side = arm == HumanoidArm.LEFT ? -1.0f : 1.0f;
            pose.translate(side * SCALE_RECIPROCAL, 2.0f * SCALE_RECIPROCAL, 0.0f);

            var skin = player.getSkinTextureLocation();
            VertexConsumer armBuf = buffers.getBuffer(RenderType.entitySolid(skin));
            VertexConsumer sleeveBuf = buffers.getBuffer(RenderType.entityTranslucent(skin));

            ModelPart armPart = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
            ModelPart sleevePart = arm == HumanoidArm.LEFT ? model.leftSleeve : model.rightSleeve;
            armPart.visible = true;
            PlayerModelPart sleeveFlag =
                    arm == HumanoidArm.LEFT ? PlayerModelPart.LEFT_SLEEVE : PlayerModelPart.RIGHT_SLEEVE;
            if (Minecraft.getInstance().options.isModelPartEnabled(sleeveFlag)) {
                sleevePart.visible = true;
            }

            setupArm(armPart, oldHands);
            armPart.render(pose, armBuf, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            setupArm(sleevePart, oldHands);
            sleevePart.render(pose, sleeveBuf, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        } finally {
            pose.popPose();
        }
    }

    private static void setupArm(ModelPart part, boolean oldHands) {
        if (oldHands) {
            part.setPos(0.0f, 0.0f, 0.0f);
            part.xRot = 0.0f;
            part.yRot = 0.0f;
            part.zRot = 0.0f;
        } else {
            part.setPos(0.0f, 7.0f, 0.0f);
            part.xRot = 0.0f;
            part.yRot = 180.0f * Mth.DEG_TO_RAD;
            part.zRot = 180.0f * Mth.DEG_TO_RAD;
        }
    }
}
