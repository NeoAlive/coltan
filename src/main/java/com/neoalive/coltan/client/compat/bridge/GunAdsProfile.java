package com.neoalive.coltan.client.compat.bridge;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Per-gun first-person procedural ADS / recoil / zoom-hide / crosshair / root-sway data
 * (Bedrock cube units, ÷16 at apply).
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
        float rootCustomX,
        float rootCustomY,
        float rootCustomZ,
        List<String> zoomHideBones,
        Map<Integer, List<String>> scopeZoomHide,
        Map<Integer, Crosshair> scopeCrosshair
) {
    public GunAdsProfile {
        zoomHideBones = zoomHideBones == null ? List.of() : List.copyOf(zoomHideBones);
        scopeZoomHide = scopeZoomHide == null || scopeZoomHide.isEmpty()
                ? Map.of()
                : Map.copyOf(scopeZoomHide);
        scopeCrosshair = scopeCrosshair == null || scopeCrosshair.isEmpty()
                ? Map.of()
                : Map.copyOf(scopeCrosshair);
    }

    public record Crosshair(
            double x,
            double y,
            double z,
            float size,
            int r,
            int g,
            int b,
            int a,
            String texture,
            boolean hasBlack
    ) {
    }

    public static GunAdsProfile glockLike() {
        return new GunAdsProfile(1.23f, 1.43f, 7f, 0.55f, 1.25f, -2f, 1.35f, 2.5f, 1.3f, 1f, 0.2f, 1.2f,
                "bone", "gun", "huatao", 4f, 2f, 3f, List.of(), Map.of(), Map.of());
    }

    public static GunAdsProfile ak47() {
        return new GunAdsProfile(1.962f, 1.071f, 2.8f, 0.55f, 1f, -1f, 1f, 1f, 1f, 1f, 0.5f, 0.8f,
                "bone", "fireRootNormal", "shuan", 0f, 0f, 0f,
                List.of(),
                Map.of(
                        2, List.of("Hidden", "gun"),
                        3, List.of("jing", "Barrel", "humu", "qiangguan", "houzhunxing")),
                Map.of(
                        1, new Crosshair(-0.03, 0.27363125, 20, 1f, 255, 0, 0, 255, "kobra", false),
                        2, new Crosshair(-0.04, 0.28, 18, 1f, 255, 0, 0, 255, "pso_1", true),
                        3, new Crosshair(-0.03, 0.28, 36, 1f, 255, 0, 0, 255, "lpvo", true)));
    }

    public static GunAdsProfile m4Family() {
        return new GunAdsProfile(1.8f, 0.5f, 2.5f, 0.5f, 1f, -1f, 1f, 1f, 1f, 1f, 0.5f, 0.8f,
                "bone", "fireRootNormal", null, 0f, 0f, 0f,
                List.of(),
                Map.of(
                        2, List.of("hidden"),
                        3, List.of("hidden2", "yugu", "qiangguan", "Barrel")),
                Map.of(
                        1, new Crosshair(0, 0.32, 30, 1.2f, 255, 0, 0, 255, "dot", false),
                        2, new Crosshair(0, 0.34, 30, 0.25f, 255, 0, 0, 255, "delta", false),
                        3, new Crosshair(0, 0.294, 13, 0.87f, 255, 0, 0, 255, "hamr", true)));
    }

    public static GunAdsProfile hk416() {
        return new GunAdsProfile(1.8f, 0.5f, 2.5f, 0.5f, 1f, -1f, 1f, 1f, 1f, 1f, 0.5f, 0.8f,
                "bone", "fireRootNormal", null, 0f, 0f, 0f,
                List.of(),
                Map.of(
                        2, List.of("hidden"),
                        3, List.of("yugu", "qiangguan", "Barrel")),
                Map.of(
                        1, new Crosshair(0, 0.25, 30, 1f, 0, 255, 0, 255, "eotech", false),
                        2, new Crosshair(0, 0.313, 9, 1f, 255, 0, 0, 255, "acog", true),
                        3, new Crosshair(0, 0.29, 65, 1f, 255, 0, 0, 255, "lpvo", true)));
    }

    public static GunAdsProfile forItem(ResourceLocation itemId) {
        if (itemId == null) {
            return glockLike();
        }
        return switch (itemId.getPath()) {
            case "ak_47" -> ak47();
            case "m_4" -> m4Family();
            case "hk_416" -> hk416();
            case "glock_17", "glock_18", "mp_443", "m_1911" -> glockLike();
            default -> glockLike();
        };
    }
}
