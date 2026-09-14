package com.neoalive.coltan.client.compat.bridge;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.item.gun.EmptyGunItem;
import com.atsuishio.superbwarfare.item.gun.GunGeoItem;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.debug.ColtanDebug;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.ModelTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Catalog of SBW guns drawn through GemRender. Discovery is allowlist-free: every
 * {@link GunGeoItem} (except {@link EmptyGunItem} / exclude list) gets a sampled
 * {@link GunBridgeProfile}.
 *
 * <p>{@link #owns(Item)} must work during Item construction (Forge calls
 * {@code GunGeoItem.initializeClient} before registry keys exist and before
 * {@link #rebuild()}). Pre-rebuild every {@link GunGeoItem} is claimed; after rebuild only
 * pieces present in the catalog. Class→path mapping from {@link GunBridgeDiscovery} fills
 * {@link #itemIdOf(Item)} when the registry key is still null.
 */
public final class GunBridgeCache {
    private static final Map<ResourceLocation, Piece> BY_ITEM = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> TEXTURE_OVERRIDES =
            new ConcurrentHashMap<>();
    private static final AtomicBoolean REBUILT = new AtomicBoolean(false);

    private static final ModelCache<GemRenderGltfModel> MODELS = new ModelCache<>(
            "Coltan SBW guns",
            GunBridgeCache::loadModel,
            (id, model) -> {
                TEXTURE_OVERRIDES.remove(id);
                for (ResourceLocation texture : model.textures()) {
                    ModelTextures.release(texture);
                }
            });

    private GunBridgeCache() {
    }

    private static GemRenderGltfModel loadModel(ResourceLocation id) throws Exception {
        Piece piece = pieceForModelId(id);
        if (piece == null) {
            throw new IllegalArgumentException("unknown Coltan gun model id: " + id);
        }
        boolean lod = id.getPath().contains("/lod");
        ResourceLocation geo = lod && piece.lodGeo() != null ? piece.lodGeo() : piece.geo();
        ResourceLocation texture = TEXTURE_OVERRIDES.get(id);
        if (texture == null) {
            texture = lod && piece.lodTexture() != null ? piece.lodTexture() : piece.texture();
        }
        return GunModelLoader.load(geo, texture, lod ? null : piece.animation());
    }

    public static synchronized void rebuild() {
        TEXTURE_OVERRIDES.clear();
        BY_ITEM.clear();
        GunProfileDiskCache.resetStats();
        GunBridgeOverride.clearPhase2Cache();

        for (GunBridgeDiscovery.Candidate candidate : GunBridgeDiscovery.discover()) {
            try {
                // sample() enriches+saves on miss; enrich is a no-op when FP data already present.
                GunBridgeProfile profile = GunFpProbe.enrich(
                        GunAssetSample.sample(candidate.item(), candidate.itemId()));
                BY_ITEM.put(candidate.itemId(), new Piece(candidate.itemId(), profile));
            } catch (Exception e) {
                Coltan.LOGGER.error("Failed to sample gun bridge profile for {}",
                        candidate.itemId(), e);
                ColtanDebug.failOnce("gun-sample-" + candidate.itemId(),
                        "gun sample failed for %s: %s", candidate.itemId(), e.toString());
            }
        }

        for (Piece piece : BY_ITEM.values()) {
            if (piece.geo() == null) {
                ColtanDebug.failOnce("gun-no-geo-" + piece.itemId(),
                        "gun piece %s has null geo — skipped model handle", piece.itemId());
                continue;
            }
            MODELS.handle(bridgeModelId(piece, false));
            if (piece.lodGeo() != null) {
                MODELS.handle(bridgeModelId(piece, true));
            }
        }
        GunVisibilityClip.clear();
        REBUILT.set(true);
        Coltan.LOGGER.info(
                "Coltan SBW gun bridge: {} piece(s), cache hits={} misses={}",
                BY_ITEM.size(), GunProfileDiskCache.hits(), GunProfileDiskCache.misses());
        ColtanDebug.log(ColtanDebug.Cat.GUN, "catalog ready pieces=%d cache hits=%d misses=%d",
                BY_ITEM.size(), GunProfileDiskCache.hits(), GunProfileDiskCache.misses());
    }

    public static synchronized void reloadModels() {
        MODELS.reload();
    }

    public static Collection<Piece> pieces() {
        return Collections.unmodifiableCollection(BY_ITEM.values());
    }

    public static boolean owns(Item item) {
        if (item instanceof EmptyGunItem) {
            return false;
        }
        if (!(item instanceof GunGeoItem)) {
            return false;
        }
        ResourceLocation id = itemIdOf(item);
        if (id != null && BY_ITEM.containsKey(id)) {
            return true;
        }
        // Construction-time / pre-rebuild: claim every GunGeoItem so initializeClient switches
        // renderers before the registry key and rebuild exist.
        return !REBUILT.get();
    }

    @Nullable
    public static Piece piece(Item item) {
        ResourceLocation id = itemIdOf(item);
        return id == null ? null : BY_ITEM.get(id);
    }

    @Nullable
    public static Piece piece(ResourceLocation itemId) {
        return BY_ITEM.get(itemId);
    }

    /**
     * Registry key when the piece is catalogued; otherwise class→path map for construction-time
     * claims (Forge registry key is still null inside the Item constructor).
     */
    @Nullable
    public static ResourceLocation itemIdOf(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null && BY_ITEM.containsKey(id)) {
            return id;
        }
        String path = GunBridgeDiscovery.pathForClass(item.getClass().getSimpleName());
        if (path != null) {
            ResourceLocation fallback = path.indexOf(':') >= 0
                    ? ResourceLocation.tryParse(path)
                    : new ResourceLocation("superbwarfare", path);
            if (fallback != null && (BY_ITEM.containsKey(fallback) || !REBUILT.get())) {
                return fallback;
            }
        }
        if (id != null && (!REBUILT.get() || BY_ITEM.containsKey(id))) {
            return id;
        }
        return null;
    }

    public static ModelCache.Handle<GemRenderGltfModel> handle(Piece piece) {
        return handle(piece, false, null);
    }

    public static ModelCache.Handle<GemRenderGltfModel> handle(Piece piece, boolean lod) {
        return handle(piece, lod, null);
    }

    /**
     * Skinned handle, optionally rebaked with a soft-compat texture override. Distinct cache keys
     * keep paint overrides from poisoning the shared stock mesh.
     */
    public static ModelCache.Handle<GemRenderGltfModel> handle(Piece piece, boolean lod,
            @Nullable ResourceLocation textureOverride) {
        ResourceLocation baseId = bridgeModelId(piece, lod && piece.lodGeo() != null);
        if (textureOverride == null || Objects.equals(textureOverride,
                lod && piece.lodTexture() != null ? piece.lodTexture() : piece.texture())) {
            return MODELS.handle(baseId);
        }
        ResourceLocation id = skinnedModelId(baseId, textureOverride);
        TEXTURE_OVERRIDES.put(id, textureOverride);
        return MODELS.handle(id);
    }

    private static ResourceLocation bridgeModelId(Piece piece, boolean lod) {
        String path = "gun/" + piece.itemId().getNamespace() + "/" + piece.itemId().getPath();
        if (lod) {
            path = path + "/lod";
        }
        return new ResourceLocation("coltan", path);
    }

    private static ResourceLocation skinnedModelId(ResourceLocation base, ResourceLocation texture) {
        return new ResourceLocation("coltan",
                base.getPath() + "/skin/" + texture.getNamespace() + "/" + texture.getPath());
    }

    @Nullable
    private static Piece pieceForModelId(ResourceLocation modelId) {
        String path = modelId.getPath();
        int skinMarker = path.indexOf("/skin/");
        if (skinMarker >= 0) {
            path = path.substring(0, skinMarker);
        }
        boolean lod = path.endsWith("/lod");
        if (lod) {
            path = path.substring(0, path.length() - "/lod".length());
        }
        if (!path.startsWith("gun/")) {
            return null;
        }
        String rest = path.substring("gun/".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null;
        }
        return BY_ITEM.get(new ResourceLocation(rest.substring(0, slash), rest.substring(slash + 1)));
    }

    /** Piece exposes the profile and asset getters derived from it. */
    public record Piece(ResourceLocation itemId, GunBridgeProfile profile) {
        @Nullable
        public ResourceLocation geo() {
            return profile.geo();
        }

        @Nullable
        public ResourceLocation texture() {
            return profile.texture();
        }

        @Nullable
        public ResourceLocation lodGeo() {
            return profile.lodGeo();
        }

        @Nullable
        public ResourceLocation lodTexture() {
            return profile.lodTexture();
        }

        @Nullable
        public ResourceLocation animation() {
            return profile.animation();
        }
    }
}
