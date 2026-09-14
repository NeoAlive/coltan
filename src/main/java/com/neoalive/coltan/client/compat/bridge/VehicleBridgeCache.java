package com.neoalive.coltan.client.compat.bridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.debug.ColtanDebug;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.bedrock.BedrockImporter;
import com.wf.gemrender.gltf.GemRenderPartsModel;
import com.wf.gemrender.texture.ModelTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** Owns vehicle profiles and the GemRender parts cache. */
public final class VehicleBridgeCache {
    private static final Map<ResourceLocation, VehicleBridgeProfile> PROFILES = new LinkedHashMap<>();
    private static final Map<EntityType<?>, VehicleBridgeProfile> BY_TYPE = new LinkedHashMap<>();
    /** Per-handle texture overrides for soft-compat sticky paint (model id → texture). */
    private static final Map<ResourceLocation, ResourceLocation> TEXTURE_OVERRIDES =
            new ConcurrentHashMap<>();

    private static final ModelCache<GemRenderPartsModel> MODELS = new ModelCache<>(
            "Coltan SBW vehicle parts",
            VehicleBridgeCache::loadModel,
            (id, model) -> {
                TEXTURE_OVERRIDES.remove(id);
                for (ResourceLocation texture : model.textures()) {
                    ModelTextures.release(texture);
                }
            });

    private VehicleBridgeCache() {
    }

    private static GemRenderPartsModel loadModel(ResourceLocation id) throws Exception {
        VehicleBridgeProfile profile = profileForModelId(id);
        if (profile == null) {
            throw new IllegalArgumentException("unknown Coltan bridge model id: " + id);
        }
        int lodIndex = lodIndexOf(id);
        LodEntry lod = profile.lod(lodIndex);
        ResourceLocation geo = lod.geo() != null ? lod.geo() : profile.geo();
        ResourceLocation texture = TEXTURE_OVERRIDES.get(id);
        if (texture == null) {
            texture = lod.texture() != null ? lod.texture() : profile.texture();
        }
        return BedrockImporter.loadParts(geo, texture, profile.animation(), profile.gameplayBones());
    }

    private static int lodIndexOf(ResourceLocation id) {
        String path = id.getPath();
        int skinMarker = path.indexOf("/skin/");
        if (skinMarker >= 0) {
            path = path.substring(0, skinMarker);
        }
        int lodMarker = path.lastIndexOf("/lod");
        if (lodMarker < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(path.substring(lodMarker + 4));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static synchronized void rebuild() {
        PROFILES.clear();
        BY_TYPE.clear();
        TEXTURE_OVERRIDES.clear();
        ProfileDiskCache.resetStats();

        Set<ResourceLocation> excluded = BridgeOverride.loadExcludeList();
        List<SbwVehicleDiscovery.Candidate> candidates = SbwVehicleDiscovery.discover();
        int skipped = 0;
        for (SbwVehicleDiscovery.Candidate candidate : candidates) {
            try {
                BridgeOverride.Data override = BridgeOverride.load(candidate.entityId());
                if (override.exclude() || excluded.contains(candidate.entityId())) {
                    skipped++;
                    continue;
                }
                VehicleBridgeProfile profile = build(candidate, override);
                PROFILES.put(candidate.entityId(), profile);
                BY_TYPE.put(candidate.entityType(), profile);
                for (int lod = 0; lod < profile.lods().size(); lod++) {
                    MODELS.handle(profile.bridgeModelId(lod));
                }
                if (ColtanDebug.on(ColtanDebug.Cat.VEHICLE) || ColtanDebug.on(ColtanDebug.Cat.LOD)) {
                    StringBuilder tiers = new StringBuilder();
                    for (int i = 0; i < profile.lods().size(); i++) {
                        LodEntry lod = profile.lods().get(i);
                        if (i > 0) {
                            tiers.append(" | ");
                        }
                        tiers.append('#').append(i).append('@').append(lod.distance())
                                .append(' ').append(shortPath(lod.geo()));
                    }
                    ColtanDebug.log(ColtanDebug.Cat.VEHICLE, "%s tiers=%d [%s] excluded=%s",
                            candidate.entityId(), profile.lods().size(), tiers, profile.excluded());
                }
            } catch (Exception e) {
                Coltan.LOGGER.error("Failed to build GemRender bridge profile for {}", candidate.entityId(), e);
                ColtanDebug.failOnce("vehicle-build-" + candidate.entityId(),
                        "vehicle profile build failed for %s: %s", candidate.entityId(), e.toString());
            }
        }

        Coltan.LOGGER.info("Coltan SBW bridge: {} vehicle profile(s), {} excluded, cache hits={} misses={}",
                PROFILES.size(), skipped, ProfileDiskCache.hits(), ProfileDiskCache.misses());
        ColtanDebug.log(ColtanDebug.Cat.VEHICLE,
                "catalog ready profiles=%d excluded=%d cache hits=%d misses=%d",
                PROFILES.size(), skipped, ProfileDiskCache.hits(), ProfileDiskCache.misses());
        if (PROFILES.isEmpty()) {
            ColtanDebug.failOnce("vehicle-catalog-empty", "vehicle catalog empty after rebuild");
        }
    }

    private static String shortPath(ResourceLocation id) {
        if (id == null) {
            return "?";
        }
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    public static synchronized void reloadModels() {
        MODELS.reload();
    }

    public static Collection<VehicleBridgeProfile> profiles() {
        return Collections.unmodifiableCollection(PROFILES.values());
    }

    public static VehicleBridgeProfile profile(EntityType<?> type) {
        return BY_TYPE.get(type);
    }

    public static VehicleBridgeProfile profile(ResourceLocation entityId) {
        return PROFILES.get(entityId);
    }

    public static ModelCache.Handle<GemRenderPartsModel> handle(VehicleBridgeProfile profile) {
        return handle(profile, 0);
    }

    public static ModelCache.Handle<GemRenderPartsModel> handle(VehicleBridgeProfile profile, int lodIndex) {
        return handle(profile, lodIndex, null);
    }

    /**
     * Parts handle for an LOD, optionally rebaked with a soft-compat texture override. When
     * {@code textureOverride} is null or equals the LOD's own texture, returns the shared default
     * handle; otherwise a distinct cache key so sticky paint does not poison the shared LOD mesh.
     */
    public static ModelCache.Handle<GemRenderPartsModel> handle(VehicleBridgeProfile profile, int lodIndex,
            ResourceLocation textureOverride) {
        LodEntry lod = profile.lod(lodIndex);
        ResourceLocation defaultTex = lod.texture() != null ? lod.texture() : profile.texture();
        if (textureOverride == null || Objects.equals(textureOverride, defaultTex)) {
            return MODELS.handle(profile.bridgeModelId(lodIndex));
        }
        ResourceLocation id = skinnedModelId(profile, lodIndex, textureOverride);
        TEXTURE_OVERRIDES.put(id, textureOverride);
        return MODELS.handle(id);
    }

    private static ResourceLocation skinnedModelId(VehicleBridgeProfile profile, int lodIndex,
            ResourceLocation texture) {
        String base = "bridge/" + profile.entityId().getNamespace() + "/" + profile.entityId().getPath();
        if (lodIndex > 0) {
            base = base + "/lod" + lodIndex;
        }
        // Encode the texture location so each sticky paint variant is a distinct cache key.
        return new ResourceLocation("coltan",
                base + "/skin/" + texture.getNamespace() + "/" + texture.getPath());
    }

    private static VehicleBridgeProfile profileForModelId(ResourceLocation modelId) {
        String path = modelId.getPath();
        int skinMarker = path.indexOf("/skin/");
        if (skinMarker >= 0) {
            path = path.substring(0, skinMarker);
        }
        for (VehicleBridgeProfile profile : PROFILES.values()) {
            String base = "bridge/" + profile.entityId().getNamespace() + "/" + profile.entityId().getPath();
            if (path.equals(base) || path.startsWith(base + "/lod")) {
                return profile;
            }
        }
        return null;
    }

    private static VehicleBridgeProfile build(SbwVehicleDiscovery.Candidate candidate, BridgeOverride.Data override) {
        BoneInference.BoundBoneLists bound = BoneInference.boundBones(candidate.entityId());
        Set<String> bones = BoneInference.gameplayBones(candidate.geo(), candidate.entityId());
        bones = VehicleBridgeProfile.withExtra(bones, override.extraGameplayBones());
        bones = VehicleBridgeProfile.applyAliases(bones, override.boneAliases());

        List<VehicleBridgeProfile.FireClip> fires = BoneInference.fireClips(candidate.animation());
        RendererSampler.Sample sample = RendererSampler.sample(candidate.entityType(), candidate.entityId(),
                candidate.geo(), candidate.texture(), candidate.animation());

        float scale = override.renderScale() != null ? override.renderScale() : sample.renderScale();
        // SBW hides these bones by name, not as a whole subtree.
        List<String> zoomHide = new ArrayList<>();
        if (sample.hideTurretZoom()) {
            zoomHide.add("root");
        }
        for (String bone : override.zoomHideBones()) {
            if (!zoomHide.contains(bone)) {
                zoomHide.add(bone);
            }
        }

        return new VehicleBridgeProfile(
                candidate.entityId(),
                candidate.entityType(),
                candidate.geo(),
                candidate.texture(),
                candidate.animation(),
                scale,
                sample.trackDistance(),
                sample.trackLength(),
                sample.rotX(),
                sample.moveY(),
                sample.moveZ(),
                sample.step(),
                Set.copyOf(bones),
                List.copyOf(fires),
                List.copyOf(bound.both()),
                List.copyOf(bound.yaw()),
                List.copyOf(bound.pitch()),
                false,
                sample.hideTurretZoom(),
                sample.hidePassengerZoom(),
                override.tracks(),
                override.propellers(),
                List.copyOf(zoomHide),
                Map.copyOf(override.boneAliases()),
                List.copyOf(sample.lods()));
    }
}
