package com.neoalive.coltan.client.compat;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.atsuishio.superbwarfare.Mod;
import com.atsuishio.superbwarfare.client.renderer.ModRenderTypes;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.resource.gun.DefaultGunResource;
import com.atsuishio.superbwarfare.resource.gun.GunResource;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.NodeTable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Muzzle flash quad — mirrors SBW {@code AnimationHelper.handleShootFlare}. */
public final class SbwGunFlare {
    private static final GltfPose.Scratch SCRATCH = new GltfPose.Scratch();

    private SbwGunFlare() {
    }

    public static void render(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light,
            GemRenderGltfModel model, float[] state, float itemScale) {
        if (ClientEventHandler.fireRotTimer <= 0 || ClientEventHandler.fireRotTimer >= 0.3) {
            return;
        }
        GunData data = GunData.from(stack);
        if (data.attachment.get(AttachmentType.BARREL) == 2) {
            return;
        }
        DefaultGunResource resource = GunResource.from(stack).compute();
        if (resource == null || resource.flarePosition == null) {
            return;
        }
        NodeTable table = model.layout().nodeTable();
        int slot = table.slotOfName("flare");
        Matrix4f[] palette = SCRATCH.palette(model.jointCount());
        GltfPose.evaluate(model.layout(), state, palette, model.morphs(), null, SCRATCH);

        Vec3 fp = resource.flarePosition;
        float height = 0f;
        int scope = data.attachment.get(AttachmentType.SCOPE);
        if ((scope == 2 || scope == 3) && ClientEventHandler.zoom) {
            height = -0.07f;
        }

        pose.pushPose();
        try {
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.scale(itemScale, itemScale, itemScale);
            if (slot >= 0) {
                pose.last().pose().mul(palette[slot]);
            }
            pose.translate(fp.x, fp.y + 0.02 + height, -fp.z);

            Matrix4f mat = pose.last().pose();
            Matrix3f normal = pose.last().normal();
            VertexConsumer consumer = buffers.getBuffer(
                    ModRenderTypes.MUZZLE_FLASH_TYPE.apply(Mod.loc("textures/particle/flare.png")));
            vertex(consumer, mat, normal, LightTexture.FULL_BRIGHT, 0f, 0f, 0, 1);
            vertex(consumer, mat, normal, LightTexture.FULL_BRIGHT, 1f, 0f, 1, 1);
            vertex(consumer, mat, normal, LightTexture.FULL_BRIGHT, 1f, 1f, 1, 0);
            vertex(consumer, mat, normal, LightTexture.FULL_BRIGHT, 0f, 1f, 0, 0);
        } finally {
            pose.popPose();
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, int light,
            float u, float v, int x, int y) {
        consumer.vertex(pose, x - 0.5f, y - 0.5f, 0f)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, 0f, 1f, 0f)
                .endVertex();
    }
}
