package com.neoalive.coltan.client.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.MunitionBridgeCache;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;

/** SBW munition items through GemRender DirectRenderer (Bedrock BEWLR only). */
public final class SbwMunitionGemCompat {
    private static final Map<ResourceLocation, BlockEntityWithoutLevelRenderer> RENDERERS =
            new ConcurrentHashMap<>();

    private SbwMunitionGemCompat() {
    }

    public static boolean active() {
        return ModList.get().isLoaded("gemrender") && ModList.get().isLoaded("superbwarfare");
    }

    public static void init() {
        if (!active()) {
            return;
        }
        MunitionBridgeCache.rebuild();
        Coltan.LOGGER.info("GemRender munition bridge ready for {} SBW item(s)",
                MunitionBridgeCache.pieces().size());
    }

    public static void reloadModels() {
        if (!active()) {
            return;
        }
        MunitionBridgeCache.reloadModels();
    }

    public static boolean owns(Item item) {
        return active() && MunitionBridgeCache.owns(item);
    }

    @Nullable
    public static BlockEntityWithoutLevelRenderer rendererFor(Item item) {
        if (!owns(item)) {
            return null;
        }
        MunitionBridgeCache.Piece piece = MunitionBridgeCache.piece(item);
        if (piece == null) {
            ResourceLocation fallback = MunitionBridgeCache.itemIdOf(item);
            if (fallback == null) {
                return null;
            }
            return RENDERERS.computeIfAbsent(fallback, id -> GemRenderItemRenderer.register(
                    new ResourceLocation("coltan", "munition/" + id.getPath()),
                    new SbwMunitionItemRenderer(SbwMunitionItemRenderer.APPEARANCE)));
        }
        return RENDERERS.computeIfAbsent(piece.itemId(), id -> GemRenderItemRenderer.register(
                new ResourceLocation("coltan", "munition/" + id.getPath()),
                new SbwMunitionItemRenderer(SbwMunitionItemRenderer.APPEARANCE)));
    }
}
