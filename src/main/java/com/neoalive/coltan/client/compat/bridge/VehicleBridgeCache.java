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
 * Discovers SBW vehicles, samples renderer params, and owns the GemRender parts model cache.
 */
public final class VehicleBridgeCache {
    private static final Map<ResourceLocation, VehicleBridgeProfile> PROFILES = new LinkedHashMap<>();
    private static final Map<EntityType<?>, VehicleBridgeProfile> BY_TYPE = new LinkedHashMap<>();

    private static final ModelCache<GemRenderPartsModel> MODELS = new ModelCache<>(
            "Coltan SBW vehicle parts",
            id -> {
                VehicleBridgeProfile profile = profileForModelId(id);
                if (profile == null) {
                    throw new IllegalArgumentException("unknown Coltan bridge model id: " + id);
                }
                return BedrockImporter.loadParts(profile.geo(), profile.texture(), profile.animation(),
                        profile.gameplayBones());
            },
            (id, model) -> {
                for (ResourceLocation texture : model.textures()) {
                    ModelTextures.release(texture);
                }
            });

    private VehicleBridgeCache() {
    }

    public static synchronized void rebuild() {
        PROFILES.clear();
        BY_TYPE.clear();

        List<SbwVehicleDiscovery.Candidate> candidates = SbwVehicleDiscovery.discover();
        for (SbwVehicleDiscovery.Candidate candidate : candidates) {
            try {
                VehicleBridgeProfile profile = build(candidate);
                PROFILES.put(candidate.entityId(), profile);
                BY_TYPE.put(candidate.entityType(), profile);
                MODELS.handle(profile.bridgeModelId());
            } catch (Exception e) {
                Coltan.LOGGER.error("Failed to build GemRender bridge profile for {}", candidate.entityId(), e);
            }
        }

        Coltan.LOGGER.info("Coltan SBW bridge: {} vehicle profile(s)", PROFILES.size());
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
        return MODELS.handle(profile.bridgeModelId());
    }

    private static VehicleBridgeProfile profileForModelId(ResourceLocation modelId) {
        for (VehicleBridgeProfile profile : PROFILES.values()) {
            if (profile.bridgeModelId().equals(modelId)) {
                return profile;
            }
        }
        return null;
    }

    private static VehicleBridgeProfile build(SbwVehicleDiscovery.Candidate candidate) {
        Set<String> bones = BoneInference.gameplayBones(candidate.geo());
        List<VehicleBridgeProfile.FireClip> fires = BoneInference.fireClips(candidate.animation());
        RendererSampler.Sample sample = RendererSampler.sample(candidate.entityType());

        return new VehicleBridgeProfile(
                candidate.entityId(),
                candidate.entityType(),
                candidate.geo(),
                candidate.texture(),
                candidate.animation(),
                sample.renderScale(),
                sample.trackDistance(),
                sample.trackLength(),
                sample.rotX(),
                sample.moveY(),
                sample.moveZ(),
                sample.step(),
                Set.copyOf(bones),
                List.copyOf(fires));
    }
}
