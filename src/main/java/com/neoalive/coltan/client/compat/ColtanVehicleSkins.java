package com.neoalive.coltan.client.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.neoalive.coltan.debug.ColtanDebug;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional per-entity texture override for bridged SBW hulls.
 *
 * <p>Default is identity (datapack / LOD texture). Soft-compat mods such as tacz_sewv register a
 * resolver so sticky faction paint still reaches GemRender after {@code skipVanillaRender} bypasses
 * {@code GeoVehicleRenderer}.
 *
 * <p>The resolver only ever runs on the render thread, so it may create or register textures.
 * Flywheel workers read {@link #cached} instead.
 */
public final class ColtanVehicleSkins {

    private static volatile BiFunction<VehicleEntity, ResourceLocation, ResourceLocation> resolver =
            (vehicle, fallback) -> fallback;

    private record Key(int entityId, ResourceLocation fallback) {}

    private record Entry(VehicleEntity vehicle, @Nullable ResourceLocation resolved) {}

    /** Resolved textures, plus pending misses (resolved == null) for the next render frame. */
    private static final Map<Key, Entry> CACHE = new ConcurrentHashMap<>();

    private ColtanVehicleSkins() {}

    /**
     * Replace the texture resolver. Pass {@code null} to restore identity. The resolver must never
     * return null use {@code fallback} when there is no override.
     */
    public static void setResolver(
            @Nullable BiFunction<VehicleEntity, ResourceLocation, ResourceLocation> next) {
        resolver = next != null ? next : (vehicle, fallback) -> fallback;
        CACHE.clear();
    }

    /** Render thread only. Runs the resolver now. */
    public static ResourceLocation resolve(VehicleEntity vehicle, ResourceLocation fallback) {
        if (fallback == null) {
            return null;
        }
        ResourceLocation resolved = compute(vehicle, fallback);
        CACHE.put(new Key(vehicle.getId(), fallback), new Entry(vehicle, resolved));
        return resolved;
    }

    /**
     * Any thread. The last texture the render thread resolved, or null when it has not resolved
     * this hull and fallback yet; the miss is resolved on the next {@link #drainPending}.
     */
    @Nullable
    public static ResourceLocation cached(VehicleEntity vehicle, ResourceLocation fallback) {
        if (fallback == null) {
            return null;
        }
        if (RenderSystem.isOnRenderThread()) {
            return resolve(vehicle, fallback);
        }
        Key key = new Key(vehicle.getId(), fallback);
        Entry entry = CACHE.get(key);
        if (entry == null || entry.vehicle() != vehicle) {
            CACHE.put(key, new Entry(vehicle, null));
            return null;
        }
        return entry.resolved();
    }

    /** Render thread, once per frame before visuals update. */
    public static void drainPending() {
        if (CACHE.isEmpty()) {
            return;
        }
        for (Map.Entry<Key, Entry> it : CACHE.entrySet()) {
            Entry entry = it.getValue();
            if (entry.resolved() == null && !entry.vehicle().isRemoved()) {
                Key key = it.getKey();
                CACHE.put(
                        key, new Entry(entry.vehicle(), compute(entry.vehicle(), key.fallback())));
            }
        }
    }

    /** Render thread, once per client tick: picks up paint changes and drops removed hulls. */
    public static void refresh() {
        CACHE.entrySet().removeIf(it -> it.getValue().vehicle().isRemoved());
        for (Map.Entry<Key, Entry> it : CACHE.entrySet()) {
            Entry entry = it.getValue();
            Key key = it.getKey();
            CACHE.put(key, new Entry(entry.vehicle(), compute(entry.vehicle(), key.fallback())));
        }
    }

    public static void clear() {
        CACHE.clear();
    }

    private static ResourceLocation compute(VehicleEntity vehicle, ResourceLocation fallback) {
        ResourceLocation out;
        try {
            out = resolver.apply(vehicle, fallback);
        } catch (RuntimeException e) {
            ColtanDebug.failOnce(
                    "skin-resolver-" + vehicle.getType(),
                    "vehicle skin resolver threw for %s: %s",
                    vehicle.getType(),
                    e.toString());
            out = null;
        }
        ResourceLocation resolved = out != null ? out : fallback;
        if (!resolved.equals(fallback) && ColtanDebug.on(ColtanDebug.Cat.SKIN)) {
            ColtanDebug.whenChanged(
                    ColtanDebug.Cat.SKIN,
                    "skin-" + vehicle.getId(),
                    resolved,
                    "vehicle skin %s #%d to %s (fallback was %s)",
                    vehicle.getType(),
                    vehicle.getId(),
                    resolved,
                    fallback);
        }
        return resolved;
    }
}
