package com.neoalive.coltan.client.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.config.client.DisplayConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.GunBridgeCache;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * SBW simple / phase-2 guns drawn through GemRender's {@link GemRenderItemRenderer}.
 *
 * <p>Does not construct renderers during FMLClientSetup — lazy on first {@link #rendererFor}.
 * Gun clips live under {@code animations/}, not as geo siblings, so {@link ItemAppearance#clip}
 * stays null (rest pose) for v1.
 */
public final class SbwGunGemCompat {
    private static final Map<ResourceLocation, GemRenderItemRenderer> RENDERERS =
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
        // Do not construct GemRenderItemRenderer here — needs baked entity models like armor.
        GunBridgeCache.rebuild();
        Coltan.LOGGER.info("GemRender gun bridge ready for {} SBW gun(s)", GunBridgeCache.pieces().size());
    }

    public static void reloadModels() {
        if (!active()) {
            return;
        }
        GunBridgeCache.reloadModels();
    }

    public static boolean owns(Item item) {
        return active() && GunBridgeCache.owns(item);
    }

    @Nullable
    public static GemRenderItemRenderer rendererFor(Item item) {
        if (!owns(item)) {
            return null;
        }
        GunBridgeCache.Piece piece = GunBridgeCache.piece(item);
        if (piece == null) {
            return null;
        }
        return RENDERERS.computeIfAbsent(piece.itemId(), id -> {
            ResourceLocation key = new ResourceLocation("coltan", "gun/" + id.getPath());
            return GemRenderItemRenderer.register(key, new GemRenderItemRenderer(APPEARANCE));
        });
    }

    private static final ItemAppearance APPEARANCE = new ItemAppearance() {
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
            return null;
        }

        @Override
        public void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
            // Origin is cell centre (GemRender ItemAppearance contract). Scale to fit the cube.
            float scale = switch (context) {
                case GUI, GROUND, FIXED, HEAD -> 0.45f;
                case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> 0.55f;
                case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> 0.5f;
                default -> 0.5f;
            };
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
