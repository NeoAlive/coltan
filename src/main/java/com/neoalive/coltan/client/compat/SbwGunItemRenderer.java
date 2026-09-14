package com.neoalive.coltan.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.neoalive.coltan.client.compat.bridge.GunClipSelect;
import com.neoalive.coltan.client.compat.bridge.GunPoseState;
import com.neoalive.coltan.debug.ColtanDebug;
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
 * GemRender gun draw. FP uses a live {@code float[]} pose (ADS/recoil/flare) so procedural motion is
 * not stuck in DirectRenderer's clip-time palette quantum; other contexts keep the cached clip path.
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
        if (!context.firstPerson()) {
            super.renderByItem(stack, context, pose, buffers, light, overlay);
            return;
        }

        GemRenderGltfModel model = appearance.model(stack, context);
        if (model == null) {
            ColtanDebug.failOnce("gun-fp-null-model-" + stack.getItem().getClass().getSimpleName(),
                    "FP gun draw skipped — null model for %s ctx=%s",
                    stack.getItem().getClass().getSimpleName(), context);
            return;
        }

        String clipName = GunClipSelect.select(stack, context);
        GltfAnimation motion = clipName == null ? null : model.animation(clipName);
        if (clipName != null && motion == null) {
            ColtanDebug.once(ColtanDebug.Cat.GUN,
                    "gun-missing-clip-" + stack.getItem().getClass().getSimpleName() + "-" + clipName,
                    "gun clip '%s' missing on %s — rest pose",
                    clipName, stack.getItem().getClass().getSimpleName());
        }
        float partial = Vanilla.partialTick();
        float seconds = GunClipSelect.seconds(stack, motion, clipName, partial);
        float[] gunState = GunPoseState.evaluate(model, stack, context, motion, seconds, true);

        pose.pushPose();
        try {
            pose.translate(0.5f, 0.5f, 0.5f);
            appearance.transform(stack, context, pose);
            DirectRenderer.submit(model, gunState, pose.last().pose(), light, overlay,
                    appearance.tint(stack, context), DirectPass.HAND, appearance.variant(stack, context));
        } finally {
            pose.popPose();
        }

        DirectRenderer.flush(DirectPass.HAND);

        float[] armState = GunPoseState.evaluate(model, stack, context, motion, seconds, false);
        // No extra scale: SBW displaysettings already applied by vanilla before BEWLR.
        SbwGunArms.render(stack, context, pose, buffers, light, model, armState, 1.0f);
        SbwGunFlare.render(stack, pose, buffers, light, model, gunState, 1.0f);
        SbwGunCrosshair.render(stack, pose, buffers, model, gunState, 1.0f);
    }

    /**
     * Extra scale on top of vanilla item display transforms. Hands/TP stay at 1 — SBW
     * {@code displaysettings/*.item.json} already set authentic sizes (e.g. AK FP scale 1 / TP 0.7).
     * GUI/ground keep a fit scale for GemRender's cell-centred origin.
     */
    static float itemScale(ItemDisplayContext context) {
        return switch (context) {
            case GUI, GROUND, FIXED, HEAD -> 0.45f;
            default -> 1.0f;
        };
    }
}
