package com.neoalive.coltan.client.compat;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Optional per-wearer texture override for bridged SBW armor.
 *
 * <p>Default is identity (stock bedrock texture). Soft-compat mods such as tacz_sewv register a
 * resolver so faction crew paint still reaches GemRender after Coltan bypasses
 * {@code GeoArmorRendererV2}.
 */
public final class ColtanArmorSkins {

    private static volatile BiFunction<LivingEntity, ItemStack, ResourceLocation> resolver =
            (entity, stack) -> null;

    private static volatile Function<ResourceLocation, List<ResourceLocation>> catalog = itemId -> List.of();

    private ColtanArmorSkins() {
    }

    /**
     * Declare, per armor item id, every skin texture the resolver can return. Pieces with a catalog
     * draw all their skins from one GemRender variant model (one sheet, one batch: mixed-camo crews
     * cost one draw per piece instead of one per skin). Textures must be resources (the importer
     * reads them through the ResourceManager, not the TextureManager) and the same size as the stock
     * texture; anything else falls back to the per-texture model. Return the same list instance while
     * the set is unchanged (checked by identity first, then equality). Pass {@code null} to clear.
     */
    public static void setCatalog(@Nullable Function<ResourceLocation, List<ResourceLocation>> next) {
        catalog = next != null ? next : itemId -> List.of();
    }

    /** Declared skins for this armor item; empty = none declared. Never null. */
    public static List<ResourceLocation> catalog(ResourceLocation itemId) {
        List<ResourceLocation> out = catalog.apply(itemId);
        return out != null ? out : List.of();
    }

    /**
     * Replace the texture resolver. Pass {@code null} to restore identity. Return {@code null} from
     * the resolver when there is no override (Coltan keeps the stock texture).
     */
    public static void setResolver(
            @Nullable BiFunction<LivingEntity, ItemStack, ResourceLocation> next) {
        resolver = next != null ? next : (entity, stack) -> null;
    }

    /** Resolve the texture Coltan should bind for this worn piece. */
    public static ResourceLocation resolve(@Nullable LivingEntity entity, ItemStack stack,
            ResourceLocation fallback) {
        if (fallback == null) {
            return null;
        }
        if (entity == null || stack == null || stack.isEmpty()) {
            return fallback;
        }
        ResourceLocation out = resolver.apply(entity, stack);
        return out != null ? out : fallback;
    }
}
