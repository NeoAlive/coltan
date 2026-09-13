package com.neoalive.coltan.client.compat.bridge;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Per-gun first-person procedural ADS / recoil / zoom-hide data (Bedrock cube units, ÷16 at apply).
 */
public record GunAdsProfile(
        float posX,
        float posY,
        float posZ,
        float scaleZ,
        float recoilX,
        float recoilY,
        float recoilZ,
        float recoilRotX,
        float recoilRotY,
        float recoilRotZ,
        float recoilZoomMul,
        float recoilSpeed,
        String adsBone,
        String recoilBone,
        @Nullable String boltBone,
        List<String> zoomHideBones,
        Map<Integer, List<String>> scopeZoomHide
) {
    public GunAdsProfile {
        zoomHideBones = zoomHideBones == null ? List.of() : List.copyOf(zoomHideBones);
        scopeZoomHide = scopeZoomHide == null || scopeZoomHide.isEmpty()
                ? Map.of()
                : Map.copyOf(scopeZoomHide);
    }

    public static GunAdsProfile glockLike() {
        return new GunAdsProfile(1.23f, 1.43f, 7f, 0.55f, 1.25f, -2f, 1.35f, 2.5f, 1.3f, 1f, 0.2f, 1.2f,
                "bone", "gun", "huatao", List.of(), Map.of());
    }

    public static GunAdsProfile ak47() {
        return new GunAdsProfile(1.962f, 1.071f, 2.8f, 0.55f, 1f, -1f, 1f, 1f, 1f, 1f, 0.5f, 0.8f,
                "bone", "fireRootNormal", "shuan",
                List.of(),
                Map.of(
                        2, List.of("Hidden", "gun"),
                        3, List.of("jing", "Barrel", "humu", "qiangguan", "houzhunxing")));
    }

    public static GunAdsProfile m4Family() {
        return new GunAdsProfile(1.8f, 0.5f, 2.5f, 0.5f, 1f, -1f, 1f, 1f, 1f, 1f, 0.5f, 0.8f,
                "bone", "fireRootNormal", null,
                List.of(),
                Map.of(
                        2, List.of("hidden"),
                        3, List.of("hidden2", "yugu", "qiangguan", "Barrel")));
    }

    public static GunAdsProfile forItem(ResourceLocation itemId) {
        if (itemId == null) {
            return glockLike();
        }
        return switch (itemId.getPath()) {
            case "ak_47" -> ak47();
            case "m_4", "hk_416" -> m4Family();
            case "glock_17", "glock_18", "mp_443", "m_1911" -> glockLike();
            default -> glockLike();
        };
    }
}
