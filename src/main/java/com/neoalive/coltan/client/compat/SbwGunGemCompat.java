package com.neoalive.coltan.client.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.config.client.DisplayConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.GunBridgeCache;
import com.neoalive.coltan.client.compat.bridge.GunClipSelect;
import com.neoalive.coltan.client.compat.bridge.GunVisibilityClip;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * SBW guns through GemRender: motion clips + visibility NodeHide + FP player arms.
 */
public final class SbwGunGemCompat {
    private static final Map<ResourceLocation, BlockEntityWithoutLevelRenderer> RENDERERS =
            new ConcurrentHashMap<>();

    private SbwGunGemCompat() {
    }

    public static boolean active() {
        return ModList.get().isLoaded("gemrender") && ModList.get().isLoaded("superbwarfare");
    }

    public static void init() {
        if (!active()) {
            return;
        }
        GunBridgeCache.rebuild();
        Coltan.LOGGER.info("GemRender gun bridge ready for {} SBW gun(s)", GunBridgeCache.pieces().size());
    }

    public static void reloadModels() {
        if (!active()) {
            return;
        }
        GunVisibilityClip.clear();
        GunBridgeCache.reloadModels();
    }

    public static boolean owns(Item item) {
        return active() && GunBridgeCache.owns(item);
    }

    @Nullable
    public static BlockEntityWithoutLevelRenderer rendererFor(Item item) {
        if (!owns(item)) {
            return null;
        }
        GunBridgeCache.Piece piece = GunBridgeCache.piece(item);
        if (piece == null) {
            return null;
        }
        return RENDERERS.computeIfAbsent(piece.itemId(), id -> {
            ResourceLocation key = new ResourceLocation("coltan", "gun/" + id.getPath());
            return GemRenderItemRenderer.register(key, new SbwGunItemRenderer(APPEARANCE));
        });
    }

    static final ItemAppearance APPEARANCE = new ItemAppearance() {
        @Override
        public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
            GunBridgeCache.Piece piece = GunBridgeCache.piece(stack.getItem());
            if (piece == null) {
                return null;
            }
            boolean lod = useLod(piece, context);
            return GunBridgeCache.handle(piece, lod).get();
        }

        @Override
        public GltfAnimation clip(ItemStack stack, ItemDisplayContext context) {
            GemRenderGltfModel model = model(stack, context);
            if (model == null) {
                return null;
            }
            String name = GunClipSelect.select(stack, context);
            GltfAnimation motion = name == null ? null : model.animation(name);
            return GunVisibilityClip.compose(model, stack, context, motion);
        }

        @Override
        public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
            GemRenderGltfModel model = model(stack, context);
            if (model == null) {
                return 0.0f;
            }
            String name = GunClipSelect.select(stack, context);
            GltfAnimation motion = name == null ? null : model.animation(name);
            return GunClipSelect.seconds(stack, motion, partialTick);
        }

        @Override
        public void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
            float scale = SbwGunItemRenderer.itemScale(context);
            pose.scale(scale, scale, scale);
        }
    };

    private static boolean useLod(GunBridgeCache.Piece piece, ItemDisplayContext context) {
        if (piece.lodGeo() == null) {
            return false;
        }
        if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            return false;
        }
        return DisplayConfig.ENABLE_GUN_LOD.get();
    }
}
