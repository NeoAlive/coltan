package com.neoalive.coltan.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.neoalive.coltan.client.compat.bridge.GunClipSelect;
import com.neoalive.coltan.client.compat.bridge.GunVisibilityClip;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * GemRender gun item draw plus FP player-skin arms after the HAND pass is flushed.
 */
public final class SbwGunItemRenderer extends GemRenderItemRenderer {
    private final ItemAppearance appearance;

    public SbwGunItemRenderer(ItemAppearance appearance) {
        super(appearance);
        this.appearance = appearance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        super.renderByItem(stack, context, pose, buffers, light, overlay);
        if (!context.firstPerson()) {
            return;
        }
        // Gun was queued on HAND; flush so arms draw on top with the same PoseStack frame.
        DirectRenderer.flush(DirectPass.HAND);

        GemRenderGltfModel model = appearance.model(stack, context);
        if (model == null) {
            return;
        }
        String clipName = GunClipSelect.select(stack, context);
        GltfAnimation motion = clipName == null ? null : model.animation(clipName);
        float partial = Vanilla.partialTick();
        float seconds = GunClipSelect.seconds(stack, motion != null ? motion : GunVisibilityClip.clip(model, stack, context),
                partial);
        float scale = itemScale(context);
        SbwGunArms.render(stack, context, pose, buffers, light, model, motion, seconds, scale);
    }

    static float itemScale(ItemDisplayContext context) {
        return switch (context) {
            case GUI, GROUND, FIXED, HEAD -> 0.45f;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> 0.55f;
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> 0.5f;
            default -> 0.5f;
        };
    }
}
