package com.neoalive.coltan.client.compat;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.atsuishio.superbwarfare.Mod;
import com.atsuishio.superbwarfare.client.renderer.ModRenderTypes;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.neoalive.coltan.client.compat.bridge.GunBridgeCache;
import com.neoalive.coltan.client.compat.bridge.GunBridgeProfile;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.NodeTable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Scope reticle while ADS — mirrors {@code AnimationHelper.handleZoomCrossHair}. */
public final class SbwGunCrosshair {
    private static final GltfPose.Scratch SCRATCH = new GltfPose.Scratch();

    private SbwGunCrosshair() {
    }

    public static void render(ItemStack stack, PoseStack pose, MultiBufferSource buffers,
            GemRenderGltfModel model, float[] state, float itemScale) {
        if (ClientEventHandler.zoomPos <= 0.1) {
            return;
        }
        GunBridgeCache.Piece piece = GunBridgeCache.piece(stack.getItem());
        if (piece == null) {
            return;
        }
        int scope = GunData.from(stack).attachment.get(AttachmentType.SCOPE);
        GunBridgeProfile.Crosshair spec = piece.profile().scopeCrosshair().get(scope);
        if (spec == null) {
            return;
        }

        NodeTable table = model.layout().nodeTable();
        int slot = table.slotOfName("cross");
        Matrix4f[] palette = SCRATCH.palette(model.jointCount());
        GltfPose.evaluate(model.layout(), state, palette, model.morphs(), null, SCRATCH);

        float size = spec.size();
        if ("lpvo".equals(spec.texture())) {
            size = (float) ClientEventHandler.customZoom;
            if (size <= 0.0f) {
                size = spec.size();
            }
        }

        int alpha = (int) (3 * Mth.clamp(ClientEventHandler.zoomTime - 0.34, 0.0, 1.0) * 255);
        int blackAlpha = spec.hasBlack() ? alpha : (int) (0.12 * alpha);

        var tex = Mod.loc("textures/crosshair/" + spec.texture() + ".png");

        pose.pushPose();
        try {
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.scale(itemScale, itemScale, itemScale);
            if (slot >= 0) {
                pose.last().pose().mul(palette[slot]);
            }
            pose.translate(spec.x(), spec.y(), -spec.z());

            Matrix4f mat = pose.last().pose();
            Matrix3f normal = pose.last().normal();

            VertexConsumer black = buffers.getBuffer(RenderType.entityTranslucentEmissive(tex));
            vertex(black, mat, normal, 0f, 0f, 0, 1, spec.r(), spec.g(), spec.b(), blackAlpha, size);
            vertex(black, mat, normal, size, 0f, 1, 1, spec.r(), spec.g(), spec.b(), blackAlpha, size);
            vertex(black, mat, normal, size, size, 1, 0, spec.r(), spec.g(), spec.b(), blackAlpha, size);
            vertex(black, mat, normal, 0f, size, 0, 0, spec.r(), spec.g(), spec.b(), blackAlpha, size);

            VertexConsumer bright = buffers.getBuffer(ModRenderTypes.MUZZLE_FLASH_TYPE.apply(tex));
            vertex(bright, mat, normal, 0f, 0f, 0, 1, spec.r(), spec.g(), spec.b(), spec.a(), size);
            vertex(bright, mat, normal, size, 0f, 1, 1, spec.r(), spec.g(), spec.b(), spec.a(), size);
            vertex(bright, mat, normal, size, size, 1, 0, spec.r(), spec.g(), spec.b(), spec.a(), size);
            vertex(bright, mat, normal, 0f, size, 0, 0, spec.r(), spec.g(), spec.b(), spec.a(), size);
        } finally {
            pose.popPose();
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
            float x, float y, int u, int v, int r, int g, int b, int a, float size) {
        consumer.vertex(pose, x - 0.5f * size, y - 0.5f * size, 0f)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(normal, 0f, 1f, 0f)
                .endVertex();
    }
}
