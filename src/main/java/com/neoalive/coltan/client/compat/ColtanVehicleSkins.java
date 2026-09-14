package com.neoalive.coltan.client.compat;

import java.util.function.BiFunction;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.neoalive.coltan.debug.ColtanDebug;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional per-entity texture override for bridged SBW hulls.
 *
 * <p>Default is identity (datapack / LOD texture). Soft-compat mods such as tacz_sewv register a
 * resolver so sticky faction paint still reaches GemRender after {@code skipVanillaRender} bypasses
 * {@code GeoVehicleRenderer}.
 */
public final class ColtanVehicleSkins {

    private static volatile BiFunction<VehicleEntity, ResourceLocation, ResourceLocation> resolver =
            (vehicle, fallback) -> fallback;

    private ColtanVehicleSkins() {
    }

    /**
     * Replace the texture resolver. Pass {@code null} to restore identity. The resolver must never
     * return null — use {@code fallback} when there is no override.
     */
    public static void setResolver(
            @Nullable BiFunction<VehicleEntity, ResourceLocation, ResourceLocation> next) {
        resolver = next != null ? next : (vehicle, fallback) -> fallback;
    }

    /** Resolve the texture Coltan should bind for this hull's active LOD. */
    public static ResourceLocation resolve(VehicleEntity vehicle, ResourceLocation fallback) {
        if (fallback == null) {
            return null;
        }
        ResourceLocation out = resolver.apply(vehicle, fallback);
        ResourceLocation resolved = out != null ? out : fallback;
        if (resolved != fallback) {
            ColtanDebug.whenChanged(ColtanDebug.Cat.SKIN,
                    "skin-" + vehicle.getId(),
                    resolved,
                    "vehicle skin %s #%d → %s (fallback was %s)",
                    vehicle.getType(), vehicle.getId(), resolved, fallback);
        }
        return resolved;
    }
}
