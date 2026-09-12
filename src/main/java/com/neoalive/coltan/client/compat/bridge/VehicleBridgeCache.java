package com.neoalive.coltan.client.compat.bridge;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.neoalive.coltan.Coltan;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.bedrock.BedrockImporter;
import com.wf.gemrender.gltf.GemRenderPartsModel;
import com.wf.gemrender.texture.ModelTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * Discovers SBW vehicles, samples renderer params, merges overrides, and owns parts model caches.
 */
public final class VehicleBridgeCache {
    private static final Map<ResourceLocation, VehicleBridgeProfile> PROFILES = new LinkedHashMap<>();
    private static final Map<EntityType<?>, VehicleBridgeProfile> BY_TYPE = new LinkedHashMap<>();

    private static final ModelCache<GemRenderPartsModel> MODELS = new ModelCache<>(
            "Coltan SBW vehicle parts",
            VehicleBridgeCache::loadModel,
            (id, model) -> {
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
        String path = id.getPath();
        int lodIndex = 0;
        int lodMarker = path.lastIndexOf("/lod");
        if (lodMarker >= 0) {
            try {
                lodIndex = Integer.parseInt(path.substring(lodMarker + 4));
            } catch (NumberFormatException ignored) {
                lodIndex = 0;
            }
        }
        LodEntry lod = profile.lod(lodIndex);
        ResourceLocation geo = lod.geo() != null ? lod.geo() : profile.geo();
        ResourceLocation texture = lod.texture() != null ? lod.texture() : profile.texture();
        return BedrockImporter.loadParts(geo, texture, profile.animation(), profile.gameplayBones());
    }

    public static synchronized void rebuild() {
        PROFILES.clear();
        BY_TYPE.clear();
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
            } catch (Exception e) {
                Coltan.LOGGER.error("Failed to build GemRender bridge profile for {}", candidate.entityId(), e);
            }
        }

        Coltan.LOGGER.info("Coltan SBW bridge: {} vehicle profile(s), {} excluded, cache hits={} misses={}",
                PROFILES.size(), skipped, ProfileDiskCache.hits(), ProfileDiskCache.misses());
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
        return MODELS.handle(profile.bridgeModelId(lodIndex));
    }

    private static VehicleBridgeProfile profileForModelId(ResourceLocation modelId) {
        String path = modelId.getPath();
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
        List<String> zoomHide = override.zoomHideBones().isEmpty() && sample.hideTurretZoom()
                ? List.of("base", "move_Track")
                : override.zoomHideBones();

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
