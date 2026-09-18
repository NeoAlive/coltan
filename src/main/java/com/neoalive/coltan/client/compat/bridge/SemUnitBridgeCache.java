package com.neoalive.coltan.client.compat.bridge;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.debug.ColtanDebug;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.ModelTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** Owns SEM unit profiles and the GemRender skinned-model cache. */
public final class SemUnitBridgeCache {
    private static final Map<ResourceLocation, SemUnitBridgeProfile> PROFILES = new LinkedHashMap<>();
    private static final Map<EntityType<?>, SemUnitBridgeProfile> BY_TYPE = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> TEXTURE_OVERRIDES =
            new ConcurrentHashMap<>();

    private static final ModelCache<GemRenderGltfModel> MODELS = new ModelCache<>(
            "Coltan SEM units",
            SemUnitBridgeCache::loadModel,
            (id, model) -> {
                TEXTURE_OVERRIDES.remove(id);
                for (ResourceLocation texture : model.textures()) {
                    ModelTextures.release(texture);
                }
            });

    private SemUnitBridgeCache() {
    }

    private static GemRenderGltfModel loadModel(ResourceLocation id) throws Exception {
        SemUnitBridgeProfile profile = profileForModelId(id);
        if (profile == null) {
            throw new IllegalArgumentException("unknown Coltan SEM unit model id: " + id);
        }
        ResourceLocation texture = TEXTURE_OVERRIDES.get(id);
        if (texture == null) {
            texture = profile.defaultTexture();
        }
        return GunModelLoader.load(profile.geo(), texture, profile.animation());
    }

    public static synchronized void rebuild() {
        PROFILES.clear();
        BY_TYPE.clear();
        TEXTURE_OVERRIDES.clear();

        Set<ResourceLocation> excluded = SemUnitDiscovery.loadExcludeList();
        for (SemUnitDiscovery.Candidate candidate : SemUnitDiscovery.discover(excluded)) {
            try {
                SemUnitBridgeProfile profile = new SemUnitBridgeProfile(
                        candidate.entityId(),
                        candidate.entityType(),
                        candidate.textureFolder(),
                        candidate.texturePrefix(),
                        candidate.geo(),
                        candidate.animation());
                PROFILES.put(candidate.entityId(), profile);
                BY_TYPE.put(candidate.entityType(), profile);
                MODELS.handle(profile.bridgeModelId());
            } catch (Exception e) {
                Coltan.LOGGER.error("Failed to build SEM unit bridge for {}", candidate.entityId(), e);
                ColtanDebug.failOnce("sem-unit-build-" + candidate.entityId(),
                        "SEM unit profile build failed for %s: %s", candidate.entityId(), e.toString());
            }
        }

        Coltan.LOGGER.info("Coltan SEM unit bridge: {} profile(s), {} excluded id(s)",
                PROFILES.size(), excluded.size());
        ColtanDebug.log(ColtanDebug.Cat.BOOT, "SEM unit catalog ready profiles=%d excluded=%d",
                PROFILES.size(), excluded.size());
    }

    public static synchronized void reloadModels() {
        MODELS.reload();
    }

    public static Collection<SemUnitBridgeProfile> profiles() {
        return Collections.unmodifiableCollection(PROFILES.values());
    }

    @Nullable
    public static SemUnitBridgeProfile profile(EntityType<?> type) {
        return BY_TYPE.get(type);
    }

    public static ModelCache.Handle<GemRenderGltfModel> handle(SemUnitBridgeProfile profile) {
        return handle(profile, null);
    }

    public static ModelCache.Handle<GemRenderGltfModel> handle(SemUnitBridgeProfile profile,
            @Nullable ResourceLocation textureOverride) {
        ResourceLocation baseId = profile.bridgeModelId();
        if (textureOverride == null || Objects.equals(textureOverride, profile.defaultTexture())) {
            return MODELS.handle(baseId);
        }
        ResourceLocation id = skinnedModelId(baseId, textureOverride);
        TEXTURE_OVERRIDES.put(id, textureOverride);
        return MODELS.handle(id);
    }

    private static ResourceLocation skinnedModelId(ResourceLocation base, ResourceLocation texture) {
        return new ResourceLocation("coltan",
                base.getPath() + "/skin/" + texture.getNamespace() + "/" + texture.getPath());
    }

    @Nullable
    private static SemUnitBridgeProfile profileForModelId(ResourceLocation id) {
        String path = id.getPath();
        int skinMarker = path.indexOf("/skin/");
        if (skinMarker >= 0) {
            path = path.substring(0, skinMarker);
        }
        if (!path.startsWith("unit/")) {
            return null;
        }
        String rest = path.substring("unit/".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null;
        }
        ResourceLocation entityId = new ResourceLocation(rest.substring(0, slash), rest.substring(slash + 1));
        return PROFILES.get(entityId);
    }
}
