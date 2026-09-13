package com.neoalive.coltan.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.neoalive.coltan.client.compat.bridge.MunitionBridgeCache;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** GemRender BEWLR for SBW munition items (grenade / TM-62 / PTKM-1R). No ADS. */
public final class SbwMunitionItemRenderer extends GemRenderItemRenderer {
    public SbwMunitionItemRenderer(ItemAppearance appearance) {
        super(appearance);
    }

    /** GUI/ground fit scale; hands stay at 1 (SBW displaysettings already applied). */
    static float itemScale(ItemDisplayContext context) {
        return switch (context) {
            case GUI, GROUND, FIXED, HEAD -> 0.45f;
            default -> 1.0f;
        };
    }

    static final ItemAppearance APPEARANCE = new ItemAppearance() {
        @Override
        public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
            MunitionBridgeCache.Piece piece = MunitionBridgeCache.piece(stack.getItem());
            if (piece == null) {
                return null;
            }
            return MunitionBridgeCache.handle(piece).get();
        }

        @Override
        public GltfAnimation clip(ItemStack stack, ItemDisplayContext context) {
            return null;
        }

        @Override
        public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
            return 0.0f;
        }

        @Override
        public void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
            float scale = itemScale(context);
            if (scale != 1.0f) {
                pose.scale(scale, scale, scale);
            }
        }
    };
}
