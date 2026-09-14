package com.neoalive.coltan.client.compat;

import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.neoalive.coltan.client.compat.bridge.TaczGunBridgeCache;
import com.neoalive.coltan.debug.ColtanDebug;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.TransformScale;
import com.tacz.guns.client.resource.pojo.display.gun.GunTransform;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Public SEM-unit TACZ → GemRender draw. Call after the unit right-arm pose; applies TACZ
 * third-person item-space transforms then {@link DirectRenderer#submit}.
 *
 * <p>Returns {@code false} when inactive, unknown, or still loading — caller should fall back
 * to stock TACZ (do not cancel SEM GunLayer in that case).
 */
public final class TaczGunDraw {
    private TaczGunDraw() {
    }

    /** True when this stack can be drawn through the bridge (soft-deps + resolvable display). */
    public static boolean canDraw(ItemStack stack) {
        if (!TaczGunGemCompat.active()) {
            return false;
        }
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IGun)) {
            return false;
        }
        return TaczGunBridgeCache.piece(stack) != null;
    }

    /**
     * Draw a TACZ gun in the current pose stack (SEM right-arm + SEM offsets already applied).
     *
     * @return true if submitted (or waiting on async load without error)
     */
    public static boolean trySubmit(@Nullable LivingEntity entity, ItemStack stack, PoseStack pose,
            int packedLight) {
        if (!TaczGunGemCompat.active()) {
            return false;
        }
        TaczGunBridgeCache.Piece piece = TaczGunBridgeCache.piece(stack);
        if (piece == null) {
            return false;
        }
        ModelCache.Handle<GemRenderGltfModel> handle = TaczGunBridgeCache.handle(piece);
        GemRenderGltfModel model = handle.get();
        if (model == null) {
            if (handle.hasFailed()) {
                ColtanDebug.failOnce("tacz-load-fail-" + piece.displayId(),
                        "TACZ gun model load failed for %s", piece.displayId());
                return false;
            }
            // Async load in flight — treat as handled so SEM GunLayer stays cancelled.
            return true;
        }

        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
        TransformScale scale = TransformScale.getGunDefault();
        BedrockGunModel taczModel = null;
        if (display != null) {
            GunTransform transform = display.getTransform();
            if (transform != null && transform.getScale() != null) {
                scale = transform.getScale();
            }
            taczModel = display.getGunModel();
        }

        pose.pushPose();
        try {
            // Match GunItemRendererWrapper third-person path.
            pose.translate(0.5, 2.0, 0.5);
            pose.scale(-1.0f, -1.0f, 1.0f);
            applyPositioningNodeTransform(
                    taczModel == null ? null : taczModel.getThirdPersonHandOriginPath(),
                    pose,
                    scale.getThirdPerson());
            applyScaleTransform(pose, scale.getThirdPerson());

            int light = packedLight;
            if (entity != null) {
                light = LightTexture.pack(
                        Math.max(LightTexture.block(packedLight), 0),
                        Math.max(LightTexture.sky(packedLight), 0));
            }
            DirectRenderer.submit(model, (com.wf.gemrender.gltf.GltfAnimation) null, 0.0f,
                    pose.last().pose(), light, OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF, DirectPass.LEVEL);
        } finally {
            pose.popPose();
        }
        return true;
    }

    private static void applyScaleTransform(PoseStack pose, @Nullable Vector3f scale) {
        if (scale == null) {
            return;
        }
        pose.translate(0, 1.5, 0);
        pose.scale(scale.x(), scale.y(), scale.z());
        pose.translate(0, -1.5, 0);
    }

    private static void applyPositioningNodeTransform(@Nullable List<BedrockPart> nodePath,
            PoseStack poseStack, @Nullable Vector3f scale) {
        if (nodePath == null) {
            return;
        }
        if (scale == null) {
            scale = new Vector3f(1, 1, 1);
        }
        poseStack.translate(0, 1.5, 0);
        for (int i = nodePath.size() - 1; i >= 0; i--) {
            BedrockPart t = nodePath.get(i);
            poseStack.mulPose(Axis.XN.rotation(t.xRot));
            poseStack.mulPose(Axis.YN.rotation(t.yRot));
            poseStack.mulPose(Axis.ZN.rotation(t.zRot));
            if (t.getParent() != null) {
                poseStack.translate(-t.x * scale.x() / 16.0F, -t.y * scale.y() / 16.0F,
                        -t.z * scale.z() / 16.0F);
            } else {
                poseStack.translate(-t.x * scale.x() / 16.0F, (1.5F - t.y / 16.0F) * scale.y(),
                        -t.z * scale.z() / 16.0F);
            }
        }
        poseStack.translate(0, -1.5, 0);
    }
}
