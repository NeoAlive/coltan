package com.neoalive.coltan.client.compat;

import java.util.function.BiFunction;

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

    private ColtanArmorSkins() {
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
