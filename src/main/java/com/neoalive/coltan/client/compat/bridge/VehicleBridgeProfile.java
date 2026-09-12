package com.neoalive.coltan.client.compat.bridge;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * Cached SBW → GemRender binding for one vehicle entity type.
 */
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
        List<FireClip> fireClips
) {
    public record FireClip(String weaponKey, String idleName, String fireName) {
    }

    public ResourceLocation bridgeModelId() {
        return new ResourceLocation("coltan", "bridge/" + entityId.getNamespace() + "/" + entityId.getPath());
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
}
