package com.neoalive.coltan.client.compat.bridge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** Cached SBW-to-GemRender binding for one vehicle type. */
public record VehicleBridgeProfile(
        ResourceLocation entityId,
        EntityType<?> entityType,
        ResourceLocation geo,
        ResourceLocation texture,
        ResourceLocation animation,
        float renderScale,
        float trackDistance,
        int trackLength,
        float[] trackRotX,
        float[] trackMoveY,
        float[] trackMoveZ,
        float sampleStep,
        Set<String> gameplayBones,
        List<FireClip> fireClips,
        List<String> boundBones,
        List<String> boundBonesYaw,
        List<String> boundBonesPitch,
        boolean excluded,
        boolean hideTurretZoom,
        boolean hidePassengerZoom,
        boolean driversTracks,
        boolean driversPropellers,
        List<String> zoomHideBones,
        Map<String, String> boneAliases,
        List<LodEntry> lods
) {
    public record FireClip(String weaponKey, String idleName, String fireName) {
    }

    public ResourceLocation bridgeModelId() {
        return bridgeModelId(0);
    }

    public ResourceLocation bridgeModelId(int lodIndex) {
        String base = "bridge/" + entityId.getNamespace() + "/" + entityId.getPath();
        if (lodIndex <= 0) {
            return new ResourceLocation("coltan", base);
        }
        return new ResourceLocation("coltan", base + "/lod" + lodIndex);
    }

    public String resolveBone(String logical) {
        return boneAliases.getOrDefault(logical, logical);
    }

    /**
     * Highest matching LOD tier for camera distance.
     * <p>{@code vehicle_lod_distance} is a camera-distance gate (−1 disables LOD). Per-tier
     * {@code LODDistance} values still decide which mesh; we keep the furthest tier whose
     * threshold the camera has crossed (not SBW's first-match, which never reaches lod2/3).
     */
    public int lodIndexForDistance(double cameraDistance) {
        if (lods == null || lods.size() <= 1) {
            return 0;
        }
        int globalMin;
        try {
            globalMin = com.atsuishio.superbwarfare.config.client.DisplayConfig.VEHICLE_LOD_DISTANCE.get();
        } catch (Exception e) {
            globalMin = -1;
        }
        if (globalMin < 0 || cameraDistance < globalMin) {
            return 0;
        }
        int best = 0;
        for (int i = 1; i < lods.size(); i++) {
            int threshold = lods.get(i).distance();
            if (threshold <= 0) {
                continue;
            }
            if (cameraDistance >= threshold) {
                best = i;
            }
        }
        return best;
    }

    public LodEntry lod(int index) {
        if (lods == null || lods.isEmpty()) {
            return new LodEntry(0, geo, texture);
        }
        int i = Math.max(0, Math.min(index, lods.size() - 1));
        return lods.get(i);
    }

    public float sampleRotX(float t) {
        return sample(trackRotX, t);
    }

    public float sampleMoveY(float t) {
        return sample(trackMoveY, t);
    }

    public float sampleMoveZ(float t) {
        return sample(trackMoveZ, t);
    }

    private float sample(float[] table, float t) {
        if (table == null || table.length == 0 || sampleStep <= 0.0f) {
            return t;
        }
        float wrapped = wrap(t, trackLength);
        float index = wrapped / sampleStep;
        int i0 = Math.min((int) Math.floor(index), table.length - 1);
        int i1 = Math.min(i0 + 1, table.length - 1);
        float frac = index - i0;
        return table[i0] + (table[i1] - table[i0]) * frac;
    }

    public static float wrap(float value, float range) {
        if (range <= 0.0f) {
            return 0.0f;
        }
        float mod = value % range;
        return mod < 0.0f ? mod + range : mod;
    }

    public static Set<String> applyAliases(Set<String> bones, Map<String, String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return bones;
        }
        Set<String> out = new LinkedHashSet<>(bones);
        out.addAll(aliases.values());
        return out;
    }

    public static Set<String> withExtra(Set<String> bones, List<String> extra) {
        if (extra == null || extra.isEmpty()) {
            return bones;
        }
        Set<String> out = new LinkedHashSet<>(bones);
        out.addAll(extra);
        return out;
    }
}
