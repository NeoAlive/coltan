package com.neoalive.coltan.client.compat.bridge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.ModelTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** Owns projectile profiles and the GemRender skinned-model cache. */
public final class ProjectileBridgeCache {
    private static final Map<ResourceLocation, ProjectileBridgeProfile> PROFILES = new LinkedHashMap<>();
    private static final Map<EntityType<?>, ProjectileBridgeProfile> BY_TYPE = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> TEXTURE_OVERRIDES =
            new ConcurrentHashMap<>();

    private static final ModelCache<GemRenderGltfModel> MODELS = new ModelCache<>(
            "Coltan SBW projectiles",
            ProjectileBridgeCache::loadModel,
            (id, model) -> {
                TEXTURE_OVERRIDES.remove(id);
                for (ResourceLocation texture : model.textures()) {
                    ModelTextures.release(texture);
                }
            });

    private ProjectileBridgeCache() {
    }

    private static GemRenderGltfModel loadModel(ResourceLocation id) throws Exception {
        ProjectileBridgeProfile profile = profileForModelId(id);
        if (profile == null) {
            throw new IllegalArgumentException("unknown Coltan projectile model id: " + id);
        }
        ResourceLocation texture = TEXTURE_OVERRIDES.get(id);
        if (texture == null) {
            texture = profile.texture();
        }
        return GunModelLoader.load(profile.geo(), texture, profile.animation());
    }

    public static synchronized void rebuild() {
        PROFILES.clear();
        BY_TYPE.clear();
        TEXTURE_OVERRIDES.clear();

        Set<ResourceLocation> excluded = loadExcludeList();
        int skipped = excluded.size();
        for (SbwProjectileDiscovery.Candidate candidate : SbwProjectileDiscovery.discover(excluded)) {
            try {
                ProjectileBridgeProfile profile = new ProjectileBridgeProfile(
                        candidate.entityId(),
                        candidate.entityType(),
                        candidate.geo(),
                        candidate.texture(),
                        candidate.animation(),
                        candidate.loopAnim(),
                        candidate.hasFlare());
                PROFILES.put(candidate.entityId(), profile);
                BY_TYPE.put(candidate.entityType(), profile);
                MODELS.handle(profile.bridgeModelId());
            } catch (Exception e) {
                Coltan.LOGGER.error("Failed to build projectile bridge for {}", candidate.entityId(), e);
            }
        }

        Coltan.LOGGER.info("Coltan SBW projectile bridge: {} profile(s), {} excluded id(s)",
                PROFILES.size(), skipped);
    }

    public static synchronized void reloadModels() {
        MODELS.reload();
    }

    public static Collection<ProjectileBridgeProfile> profiles() {
        return Collections.unmodifiableCollection(PROFILES.values());
    }

    @Nullable
    public static ProjectileBridgeProfile profile(EntityType<?> type) {
        return BY_TYPE.get(type);
    }

    @Nullable
    public static ProjectileBridgeProfile profile(ResourceLocation entityId) {
        return PROFILES.get(entityId);
    }

    public static ModelCache.Handle<GemRenderGltfModel> handle(ProjectileBridgeProfile profile) {
        return handle(profile, null);
    }

    /**
     * Skinned handle, optionally rebaked with a mine alter texture. Distinct cache keys keep rare
     * UUID skins from poisoning the shared stock mesh.
     */
    public static ModelCache.Handle<GemRenderGltfModel> handle(ProjectileBridgeProfile profile,
            @Nullable ResourceLocation textureOverride) {
        ResourceLocation baseId = profile.bridgeModelId();
        if (textureOverride == null || Objects.equals(textureOverride, profile.texture())) {
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
    private static ProjectileBridgeProfile profileForModelId(ResourceLocation id) {
        String path = id.getPath();
        int skinMarker = path.indexOf("/skin/");
        if (skinMarker >= 0) {
            path = path.substring(0, skinMarker);
        }
        if (!path.startsWith("projectile/")) {
            return null;
        }
        String rest = path.substring("projectile/".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null;
        }
        ResourceLocation entityId = new ResourceLocation(rest.substring(0, slash), rest.substring(slash + 1));
        return PROFILES.get(entityId);
    }

    private static Set<ResourceLocation> loadExcludeList() {
        Set<ResourceLocation> out = new HashSet<>();
        ResourceLocation id = new ResourceLocation("coltan", "sbw_projectile_bridge/_exclude.json");
        var optional = Minecraft.getInstance().getResourceManager().getResource(id);
        if (optional.isEmpty()) {
            return out;
        }
        try (var reader = new InputStreamReader(optional.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("exclude")) {
                return out;
            }
            for (JsonElement element : root.getAsJsonArray("exclude")) {
                ResourceLocation excluded = ResourceLocation.tryParse(element.getAsString());
                if (excluded != null) {
                    out.add(excluded);
                }
            }
        } catch (Exception e) {
            Coltan.LOGGER.warn("Could not read {}", id, e);
        }
        return out;
    }
}
