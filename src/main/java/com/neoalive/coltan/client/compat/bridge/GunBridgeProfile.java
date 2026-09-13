package com.neoalive.coltan.client.compat.bridge;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * Metadata for a bridged SBW gun.
 *
 * <p>Phase 1 simple guns use {@code simple=true} and empty {@link #attachmentBones()}. Phase 2
 * dedicated guns (ak_47 / hk_416 / m_4) share the same GemRender path; attachment visibility is
 * handled by {@link GunVisibilityClip} (SBW bone-name rules). Motion clips and FP player arms are
 * wired via {@code GunClipSelect} / {@code SbwGunArms}. ADS and muzzle flare remain deferred.
 */
public record GunBridgeProfile(
        ResourceLocation itemId,
        boolean simple,
        Map<String, List<String>> attachmentBones
) {
    public GunBridgeProfile {
        attachmentBones = attachmentBones == null || attachmentBones.isEmpty()
                ? Map.of()
                : Map.copyOf(attachmentBones);
    }

    public static GunBridgeProfile simple(ResourceLocation itemId) {
        return new GunBridgeProfile(itemId, true, Collections.emptyMap());
    }

    public static GunBridgeProfile dedicated(ResourceLocation itemId,
            Map<String, List<String>> attachmentBones) {
        return new GunBridgeProfile(itemId, false, attachmentBones);
    }
}
