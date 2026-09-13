package com.neoalive.coltan.client.compat.bridge;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Sampled SBW gun → GemRender binding (assets, clips, bone flags, ADS placeholders).
 *
 * <p>Tier A fills assets / clips / bone inference from GunResource + geo. Tier B
 * ({@link GunFpProbe}) later enriches ADS / recoil / scope maps.
 */
public record GunBridgeProfile(
        ResourceLocation itemId,
        boolean simple,
        boolean useOldHandRenderer,
        @Nullable ResourceLocation geo,
        @Nullable ResourceLocation texture,
        @Nullable ResourceLocation lodGeo,
        @Nullable ResourceLocation lodTexture,
        @Nullable ResourceLocation animation,
        @Nullable String idle,
        @Nullable String edit,
        @Nullable String bolt,
        @Nullable String reload,
        @Nullable String reloadNormal,
        @Nullable String reloadEmpty,
        @Nullable String fire,
        @Nullable String run,
        @Nullable String melee,
        boolean hasHumu1,
        boolean hasHumu2,
        boolean hasFlare,
        boolean hasCross,
        boolean hasRoot,
        boolean hasLefthand,
        boolean hasRighthand,
        @Nullable String adsBone,
        @Nullable String recoilBone,
        @Nullable String boltBone,
        float rootCustomX,
        float rootCustomY,
        float rootCustomZ,
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
        Map<Integer, List<String>> scopeZoomHide,
        Map<Integer, Crosshair> scopeCrosshair,
        Map<Integer, AdsPose> scopeAds,
        String modelClass,
        String rendererClass,
        String contentHash
) {
    public GunBridgeProfile {
        scopeZoomHide = scopeZoomHide == null || scopeZoomHide.isEmpty()
                ? Map.of()
                : Map.copyOf(scopeZoomHide);
        scopeCrosshair = scopeCrosshair == null || scopeCrosshair.isEmpty()
                ? Map.of()
                : Map.copyOf(scopeCrosshair);
        scopeAds = scopeAds == null || scopeAds.isEmpty()
                ? Map.of()
                : Map.copyOf(scopeAds);
        modelClass = modelClass == null ? "" : modelClass;
        rendererClass = rendererClass == null ? "" : rendererClass;
        contentHash = contentHash == null ? "" : contentHash;
    }

    /** Scope reticle (Bedrock cube units), same shape as {@link GunAdsProfile.Crosshair}. */
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

    /** Per-scope ADS pose override. */
    public record AdsPose(float posX, float posY, float posZ, float scaleZ) {
        public static AdsPose identity() {
            return new AdsPose(0f, 0f, 0f, 1f);
        }
    }

    public static GunBridgeProfile empty(ResourceLocation itemId) {
        return defaults(itemId);
    }

    public static GunBridgeProfile defaults(ResourceLocation itemId) {
        return new GunBridgeProfile(
                itemId,
                true,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                1f,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                Map.of(),
                Map.of(),
                Map.of(),
                "",
                "",
                "");
    }

    public GunBridgeProfile withAssets(
            @Nullable ResourceLocation geo,
            @Nullable ResourceLocation texture,
            @Nullable ResourceLocation lodGeo,
            @Nullable ResourceLocation lodTexture,
            @Nullable ResourceLocation animation) {
        return new GunBridgeProfile(
                itemId, simple, useOldHandRenderer,
                geo, texture, lodGeo, lodTexture, animation,
                idle, edit, bolt, reload, reloadNormal, reloadEmpty, fire, run, melee,
                hasHumu1, hasHumu2, hasFlare, hasCross, hasRoot, hasLefthand, hasRighthand,
                adsBone, recoilBone, boltBone,
                rootCustomX, rootCustomY, rootCustomZ,
                posX, posY, posZ, scaleZ,
                recoilX, recoilY, recoilZ, recoilRotX, recoilRotY, recoilRotZ,
                recoilZoomMul, recoilSpeed,
                scopeZoomHide, scopeCrosshair, scopeAds,
                modelClass, rendererClass, contentHash);
    }

    public GunBridgeProfile withHash(String hash) {
        return new GunBridgeProfile(
                itemId, simple, useOldHandRenderer,
                geo, texture, lodGeo, lodTexture, animation,
                idle, edit, bolt, reload, reloadNormal, reloadEmpty, fire, run, melee,
                hasHumu1, hasHumu2, hasFlare, hasCross, hasRoot, hasLefthand, hasRighthand,
                adsBone, recoilBone, boltBone,
                rootCustomX, rootCustomY, rootCustomZ,
                posX, posY, posZ, scaleZ,
                recoilX, recoilY, recoilZ, recoilRotX, recoilRotY, recoilRotZ,
                recoilZoomMul, recoilSpeed,
                scopeZoomHide, scopeCrosshair, scopeAds,
                modelClass, rendererClass, hash);
    }

    /**
     * Copy replacing first-person procedural fields. Non-null bone args replace ads/recoil/bolt
     * bone names; null keeps the existing value.
     */
    public GunBridgeProfile withFp(
            float rootCustomX,
            float rootCustomY,
            float rootCustomZ,
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
            Map<Integer, List<String>> scopeZoomHide,
            Map<Integer, Crosshair> scopeCrosshair,
            Map<Integer, AdsPose> scopeAds,
            @Nullable String adsBone,
            @Nullable String recoilBone,
            @Nullable String boltBone) {
        return new GunBridgeProfile(
                itemId, simple, useOldHandRenderer,
                geo, texture, lodGeo, lodTexture, animation,
                idle, edit, bolt, reload, reloadNormal, reloadEmpty, fire, run, melee,
                hasHumu1, hasHumu2, hasFlare, hasCross, hasRoot, hasLefthand, hasRighthand,
                adsBone != null ? adsBone : this.adsBone,
                recoilBone != null ? recoilBone : this.recoilBone,
                boltBone != null ? boltBone : this.boltBone,
                rootCustomX, rootCustomY, rootCustomZ,
                posX, posY, posZ, scaleZ,
                recoilX, recoilY, recoilZ, recoilRotX, recoilRotY, recoilRotZ,
                recoilZoomMul, recoilSpeed,
                scopeZoomHide, scopeCrosshair, scopeAds,
                modelClass, rendererClass, contentHash);
    }

    /** True when Tier B FP fields are already populated (disk cache / prior enrich). */
    public boolean hasFpData() {
        return !scopeCrosshair.isEmpty()
                || !scopeAds.isEmpty()
                || Math.abs(posX) + Math.abs(posY) + Math.abs(posZ) > 1e-3f
                || recoilSpeed != 0f;
    }
}
