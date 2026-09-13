package com.neoalive.coltan.client.compat.bridge;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * Metadata for a bridged SBW gun.
 *
 * <p>Phase 1 simple guns use {@code simple=true} and empty {@link #attachmentBones()}. Phase 2
 * dedicated guns (ak_47 / hk_416 / m_4) are claimable on the same rest-pose GemRender path;
 * ADS, muzzle flare, and FP arms are deferred. Attachment bone maps are stored for a later
 * NodeHide pass — DirectRenderer items do not apply world-pose drivers yet, so runtime hide is
 * not wired.
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
