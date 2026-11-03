package com.neoalive.coltan.client.compat.sbw;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.ColtanBridgePack;
import com.neoalive.coltan.debug.ColtanDebug;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.bedrock.BedrockImporter;
import com.wf.gemrender.gltf.GemRenderPartsModel;
import com.wf.gemrender.texture.ModelTextures;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;

public final class VehicleBridgeCache {
    // Swapped wholesale by rebuild(): render / Flywheel worker / import threads read without
    // locking.
    private static volatile Map<ResourceLocation, VehicleBridgeProfile> PROFILES = Map.of();
    private static volatile Map<EntityType<?>, VehicleBridgeProfile> BY_TYPE = Map.of();

    /** Per-handle texture overrides for sticky paint (model id to texture). */
    private static final String GEO_SUFFIX = ".geo.json";

    private static final String GAMEPLAY_BONES_KEY = "gemrender:gameplay_bones";

    /** Vehicles whose stock import failed. Their own renderer takes over until the next reload. */
    private static final Set<ResourceLocation> FAILED = ConcurrentHashMap.newKeySet();

    private static final Map<ResourceLocation, ResourceLocation> TEXTURE_OVERRIDES =
            new ConcurrentHashMap<>();

    private static final ModelCache<GemRenderPartsModel> MODELS =
            new ModelCache<>(
                    "Coltan SBW vehicle parts",
                    VehicleBridgeCache::loadModel,
                    (id, model) -> {
                        TEXTURE_OVERRIDES.remove(id);
                        for (ResourceLocation texture : model.textures()) {
                            ModelTextures.release(texture);
                        }
                    });

    private VehicleBridgeCache() {}

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
        if (texture == null) {
            // The generated geo has no sibling png, so name the stock one explicitly.
            texture = sibling(geo, ".png");
        }
        ResourceLocation generated = generatedGeoId(profile, lodIndex);
        try {
            if (ColtanBridgePack.serves(generated)) {
                return BedrockImporter.loadParts(generated, texture);
            }
            ColtanDebug.failOnce(
                    "vehicle-geo-raw-" + id,
                    "no generated geo for %s, importing %s without bridge bones or clips",
                    id,
                    geo);
            return BedrockImporter.loadParts(geo, texture);
        } catch (Exception | LinkageError e) {
            if (!id.getPath().contains("/skin/") && FAILED.add(profile.entityId())) {
                Coltan.LOGGER.warn(
                        "Coltan: GemRender import failed for {} ({}), Superb Warfare renders it"
                                + " instead",
                        profile.entityId(),
                        e.toString());
            }
            throw e;
        }
    }

    /** Pack path of the bones and clips GemRender reads for one LOD. */
    private static ResourceLocation generatedGeoId(VehicleBridgeProfile profile, int lodIndex) {
        return new ResourceLocation(
                ColtanBridgePack.NAMESPACE,
                "bridge_geo/"
                        + profile.entityId().getNamespace()
                        + "/"
                        + profile.entityId().getPath()
                        + "/lod"
                        + lodIndex
                        + GEO_SUFFIX);
    }

    /** Same naming GemRender uses to find a geometry's png and animation file. */
    private static ResourceLocation sibling(ResourceLocation location, String suffix) {
        String path = location.getPath();
        if (path.endsWith(GEO_SUFFIX)) {
            path = path.substring(0, path.length() - GEO_SUFFIX.length());
        } else if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return new ResourceLocation(location.getNamespace(), path + suffix);
    }

    /**
     * Published GemRender reads gameplay bones from the geometry description and clips from the
     * sibling animation file. Serve a copy of each SBW geo with both in place.
     */
    private static Map<ResourceLocation, byte[]> generateGeo(
            Collection<VehicleBridgeProfile> profiles) {
        Map<ResourceLocation, byte[]> out = new LinkedHashMap<>();
        for (VehicleBridgeProfile profile : profiles) {
            for (int lodIndex = 0; lodIndex < profile.lods().size(); lodIndex++) {
                LodEntry lod = profile.lod(lodIndex);
                ResourceLocation geo = lod.geo() != null ? lod.geo() : profile.geo();
                ResourceLocation target = generatedGeoId(profile, lodIndex);
                try {
                    byte[] raw = readBytes(geo);
                    if (raw == null) {
                        ColtanDebug.failOnce(
                                "vehicle-geo-missing-" + target,
                                "geo %s missing for %s",
                                geo,
                                profile.entityId());
                        continue;
                    }
                    JsonObject root =
                            JsonParser.parseString(new String(raw, StandardCharsets.UTF_8))
                                    .getAsJsonObject();
                    if (!injectGameplayBones(root, profile.gameplayBones())) {
                        ColtanDebug.failOnce(
                                "vehicle-geo-shape-" + target,
                                "geo %s has no geometry entry for %s",
                                geo,
                                profile.entityId());
                        continue;
                    }
                    out.put(target, root.toString().getBytes(StandardCharsets.UTF_8));
                    ResourceLocation clips =
                            profile.animation() != null
                                    ? profile.animation()
                                    : sibling(geo, ".animation.json");
                    byte[] clipBytes = readBytes(clips);
                    if (clipBytes != null) {
                        out.put(sibling(target, ".animation.json"), clipBytes);
                    }
                } catch (Exception e) {
                    ColtanDebug.failOnce(
                            "vehicle-geo-gen-" + target,
                            "could not generate geo for %s from %s: %s",
                            profile.entityId(),
                            geo,
                            e.toString());
                }
            }
        }
        return out;
    }

    /** Merge into the description GemRender reads: the first modern entry, or legacy ones. */
    private static boolean injectGameplayBones(JsonObject root, Set<String> bones) {
        List<JsonObject> targets = new ArrayList<>();
        JsonElement modern = root.get("minecraft:geometry");
        if (modern != null && modern.isJsonArray() && !modern.getAsJsonArray().isEmpty()) {
            targets.add(modern.getAsJsonArray().get(0).getAsJsonObject());
        } else {
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (entry.getKey().startsWith("geometry.") && entry.getValue().isJsonObject()) {
                    targets.add(entry.getValue().getAsJsonObject());
                }
            }
        }
        if (targets.isEmpty()) {
            return false;
        }
        for (JsonObject geometry : targets) {
            JsonObject description =
                    geometry.has("description") && geometry.get("description").isJsonObject()
                            ? geometry.getAsJsonObject("description")
                            : geometry;
            Set<String> merged = new LinkedHashSet<>();
            JsonElement existing = description.get(GAMEPLAY_BONES_KEY);
            if (existing != null && existing.isJsonArray()) {
                for (JsonElement name : existing.getAsJsonArray()) {
                    merged.add(name.getAsString());
                }
            }
            for (String bone : bones) {
                if (bone != null && !bone.isBlank()) {
                    merged.add(bone.trim());
                }
            }
            JsonArray array = new JsonArray();
            merged.forEach(array::add);
            description.add(GAMEPLAY_BONES_KEY, array);
        }
        return true;
    }

    private static byte[] readBytes(ResourceLocation id) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open()) {
            return in.readAllBytes();
        }
    }

    /** True when a stock import for this vehicle failed since the last rebuild. */
    public static boolean importFailed(VehicleBridgeProfile profile) {
        return FAILED.contains(profile.entityId());
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
        Map<ResourceLocation, VehicleBridgeProfile> profiles = new LinkedHashMap<>();
        Map<EntityType<?>, VehicleBridgeProfile> byType = new LinkedHashMap<>();
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
                profiles.put(candidate.entityId(), profile);
                byType.put(candidate.entityType(), profile);
                if (ColtanDebug.on(ColtanDebug.Cat.VEHICLE)
                        || ColtanDebug.on(ColtanDebug.Cat.LOD)) {
                    StringBuilder tiers = new StringBuilder();
                    for (int i = 0; i < profile.lods().size(); i++) {
                        LodEntry lod = profile.lods().get(i);
                        if (i > 0) {
                            tiers.append(" | ");
                        }
                        tiers.append('#')
                                .append(i)
                                .append('@')
                                .append(lod.distance())
                                .append(' ')
                                .append(shortPath(lod.geo()));
                    }
                    ColtanDebug.log(
                            ColtanDebug.Cat.VEHICLE,
                            "%s tiers=%d [%s] excluded=%s",
                            candidate.entityId(),
                            profile.lods().size(),
                            tiers,
                            profile.excluded());
                }
            } catch (Exception e) {
                Coltan.LOGGER.error(
                        "Failed to build GemRender bridge profile for {}", candidate.entityId(), e);
                ColtanDebug.failOnce(
                        "vehicle-build-" + candidate.entityId(),
                        "vehicle profile build failed for %s: %s",
                        candidate.entityId(),
                        e.toString());
            }
        }

        Map<ResourceLocation, byte[]> generated = generateGeo(profiles.values());
        ColtanBridgePack.publish(generated);
        FAILED.clear();
        ColtanDebug.log(
                ColtanDebug.Cat.VEHICLE,
                "bridge pack serves %d generated file(s)",
                generated.size());

        PROFILES = Collections.unmodifiableMap(profiles);
        BY_TYPE = Collections.unmodifiableMap(byType);
        // Register handles only after the swap so the async loader resolves the new catalog.
        for (VehicleBridgeProfile profile : profiles.values()) {
            for (int lod = 0; lod < profile.lods().size(); lod++) {
                MODELS.handle(profile.bridgeModelId(lod));
            }
        }

        Coltan.LOGGER.info(
                "Coltan SBW bridge: {} vehicle profile(s), {} excluded, cache hits={} misses={}",
                PROFILES.size(),
                skipped,
                ProfileDiskCache.hits(),
                ProfileDiskCache.misses());
        ColtanDebug.log(
                ColtanDebug.Cat.VEHICLE,
                "catalog ready profiles=%d excluded=%d cache hits=%d misses=%d",
                PROFILES.size(),
                skipped,
                ProfileDiskCache.hits(),
                ProfileDiskCache.misses());
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
        FAILED.clear();
        MODELS.reload();
    }

    /**
     * Everything {@code loadModel} reads, per model. A rebuild that leaves this unchanged does not
     * need {@link #reloadModels()}.
     */
    public static List<List<Object>> modelInputs() {
        List<List<Object>> out = new ArrayList<>();
        for (VehicleBridgeProfile it : PROFILES.values()) {
            out.add(
                    Arrays.asList(
                            it.entityId(),
                            it.geo(),
                            it.texture(),
                            it.animation(),
                            it.gameplayBones(),
                            it.lods()));
        }
        return out;
    }

    /** True when an import failed (e.g. requested before resources were ready). */
    public static boolean hasFailedModels() {
        return MODELS.failedCount() > 0;
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

    public static ModelCache.Handle<GemRenderPartsModel> handle(
            VehicleBridgeProfile profile, int lodIndex) {
        return handle(profile, lodIndex, null);
    }

    /** A null or stock texture uses the shared handle. Any other texture gets its own cache key. */
    public static ModelCache.Handle<GemRenderPartsModel> handle(
            VehicleBridgeProfile profile, int lodIndex, ResourceLocation textureOverride) {
        LodEntry lod = profile.lod(lodIndex);
        ResourceLocation defaultTex = lod.texture() != null ? lod.texture() : profile.texture();
        if (textureOverride == null || Objects.equals(textureOverride, defaultTex)) {
            return MODELS.handle(profile.bridgeModelId(lodIndex));
        }
        ResourceLocation id = skinnedModelId(profile, lodIndex, textureOverride);
        TEXTURE_OVERRIDES.put(id, textureOverride);
        return MODELS.handle(id);
    }

    private static ResourceLocation skinnedModelId(
            VehicleBridgeProfile profile, int lodIndex, ResourceLocation texture) {
        String base =
                "bridge/" + profile.entityId().getNamespace() + "/" + profile.entityId().getPath();
        if (lodIndex > 0) {
            base = base + "/lod" + lodIndex;
        }
        // Encode the texture location so each sticky paint variant is a distinct cache key.
        return new ResourceLocation(
                "coltan", base + "/skin/" + texture.getNamespace() + "/" + texture.getPath());
    }

    private static VehicleBridgeProfile profileForModelId(ResourceLocation modelId) {
        String path = modelId.getPath();
        int skinMarker = path.indexOf("/skin/");
        if (skinMarker >= 0) {
            path = path.substring(0, skinMarker);
        }
        for (VehicleBridgeProfile profile : PROFILES.values()) {
            String base =
                    "bridge/"
                            + profile.entityId().getNamespace()
                            + "/"
                            + profile.entityId().getPath();
            if (path.equals(base) || path.startsWith(base + "/lod")) {
                return profile;
            }
        }
        return null;
    }

    private static VehicleBridgeProfile build(
            SbwVehicleDiscovery.Candidate candidate, BridgeOverride.Data override) {
        VehicleRenderModes.Decision mode =
                VehicleRenderModes.classify(
                        candidate.entityType(), candidate.entityId(), override.renderMode());
        RendererSampler.Sample sample =
                RendererSampler.sample(
                        candidate.entityType(),
                        candidate.entityId(),
                        candidate.geo(),
                        candidate.texture(),
                        candidate.animation());
        if (override.renderMode() == null
                && SbwGemPresence.SBW_OPTIMIZED
                && mode.mode() != VehicleRenderMode.PASSTHROUGH
                && yieldsToSbwOptimized(candidate.geo(), sample.lods())) {
            mode =
                    new VehicleRenderModes.Decision(
                            VehicleRenderMode.PASSTHROUGH, "sbwoptimized draws this geo");
        }
        ColtanDebug.whenChanged(
                ColtanDebug.Cat.VEHICLE,
                "render-mode-" + candidate.entityId(),
                mode.mode(),
                "%s render mode %s (%s)",
                candidate.entityId(),
                mode.mode(),
                mode.reason());
        BoneInference.BoundBoneLists bound = BoneInference.boundBones(candidate.entityId());
        // Replay copies every SBW bone, and an addon hook may move any of them: cut every bone into
        // its own part, since a bone merged into its parent's mesh cannot move on its own.
        Set<String> bones =
                mode.mode() == VehicleRenderMode.REPLAY
                        ? new LinkedHashSet<>(BoneInference.boneNames(candidate.geo()))
                        : BoneInference.gameplayBones(candidate.geo(), candidate.entityId());
        bones = VehicleBridgeProfile.withExtra(bones, override.extraGameplayBones());
        bones = VehicleBridgeProfile.applyAliases(bones, override.boneAliases());

        List<VehicleBridgeProfile.FireClip> fires = BoneInference.fireClips(candidate.animation());

        float scale =
                override.renderScale() != null ? override.renderScale() : sample.renderScale();
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
                List.copyOf(sample.lods()),
                override.hullYawOnly(),
                override.mortarBipodBone(),
                override.mortarMonitorBone(),
                List.copyOf(override.loopClips()),
                List.copyOf(override.stateClips()),
                mode.mode());
    }

    /** Mesh geos and files over GemRender's bone cap. SBW Optimized draws those. */
    private static boolean yieldsToSbwOptimized(ResourceLocation geo, List<LodEntry> lods) {
        if (BoneInference.gemRenderDrops(geo)) {
            return true;
        }
        for (LodEntry lod : lods) {
            ResourceLocation lodGeo = lod.geo();
            if (lodGeo != null && !lodGeo.equals(geo) && BoneInference.gemRenderDrops(lodGeo)) {
                return true;
            }
        }
        return false;
    }
}
