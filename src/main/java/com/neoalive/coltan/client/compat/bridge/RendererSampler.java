package com.neoalive.coltan.client.compat.bridge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import com.atsuishio.superbwarfare.client.renderer.entity.GeoVehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.resource.vehicle.DefaultVehicleResource;
import com.atsuishio.superbwarfare.resource.vehicle.VehicleModelPojo;
import com.atsuishio.superbwarfare.resource.vehicle.VehicleResource;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.debug.ColtanDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Samples an SBW vehicle renderer into a cacheable profile. */
public final class RendererSampler {
    public static final float SAMPLE_STEP = 0.25f;

    private RendererSampler() {
    }

    public record Sample(
            float renderScale,
            float trackDistance,
            int trackLength,
            float[] rotX,
            float[] moveY,
            float[] moveZ,
            float step,
            boolean hideTurretZoom,
            boolean hidePassengerZoom,
            String rendererClass,
            List<LodEntry> lods
    ) {
        public static Sample defaults(int trackLength, List<LodEntry> lods) {
            int len = Math.max(1, trackLength);
            // Zeros only. Using the sample index as a curve lifts tracks into the air.
            float[] zeros = new float[(int) Math.ceil(len / SAMPLE_STEP) + 1];
            return new Sample(1.0f, 2.0f, len, zeros, zeros.clone(), zeros.clone(), SAMPLE_STEP,
                    false, false, "", lods);
        }

        ProfileDiskCache.CachedSample toCached() {
            return new ProfileDiskCache.CachedSample(renderScale, trackDistance, trackLength, rotX, moveY, moveZ,
                    step, hideTurretZoom, hidePassengerZoom, rendererClass);
        }

        static Sample fromCached(ProfileDiskCache.CachedSample cached, List<LodEntry> lods) {
            return new Sample(cached.renderScale(), cached.trackDistance(), cached.trackLength(), cached.rotX(),
                    cached.moveY(), cached.moveZ(), cached.step(), cached.hideTurretZoom(),
                    cached.hidePassengerZoom(), cached.rendererClass(), lods);
        }
    }

    /** Default metres for Models[] / filesystem tiers that omit {@code LODDistance} (lod1=32…). */
    private static final int DEFAULT_LOD_STEP = 32;

    public static List<LodEntry> lodEntries(ResourceLocation entityId, ResourceLocation fallbackGeo,
                                            ResourceLocation fallbackTexture) {
        List<LodEntry> lods = new ArrayList<>();
        DefaultVehicleResource res = VehicleResource.getDefault(entityId.toString());
        for (VehicleModelPojo pojo : res.getModels()) {
            if (pojo.model == null) {
                continue;
            }
            ResourceLocation texture = resolveTexture(entityId, pojo.texture, pojo.model, fallbackTexture);
            lods.add(new LodEntry(pojo.distance, pojo.model, texture));
        }
        mergeFilesystemLods(entityId, fallbackTexture, lods);
        if (lods.isEmpty()) {
            lods.add(new LodEntry(0, fallbackGeo, fallbackTexture));
        }
        return List.copyOf(normalizeLodDistances(lods, fallbackGeo));
    }

    /**
     * Ensures one distance-0 full mesh, then ascending tiers. Entries with {@code distance <= 0}
     * that are not the full geo (e.g. mi_28 / plz_05 LOD rows missing {@code LODDistance}) get
     * {@code 32 * tier}. Duplicate geos keep the stronger (explicit) distance.
     */
    static List<LodEntry> normalizeLodDistances(List<LodEntry> raw, ResourceLocation fallbackGeo) {
        LodEntry full = null;
        List<LodEntry> tiers = new ArrayList<>();
        for (LodEntry entry : raw) {
            if (entry.geo() == null) {
                continue;
            }
            boolean looksLod = entry.distance() > 0 || isLodGeoPath(entry.geo());
            if (!looksLod) {
                if (full == null || entry.geo().equals(fallbackGeo)) {
                    full = new LodEntry(0, entry.geo(), entry.texture());
                }
                continue;
            }
            tiers.add(entry);
        }
        if (full == null) {
            for (LodEntry entry : raw) {
                if (entry.geo() != null && !isLodGeoPath(entry.geo())) {
                    full = new LodEntry(0, entry.geo(), entry.texture());
                    break;
                }
            }
        }
        if (full == null && fallbackGeo != null) {
            full = new LodEntry(0, fallbackGeo, raw.isEmpty() ? null : raw.get(0).texture());
        }

        // Dedupe by geo; prefer an authored positive distance over 0.
        LinkedHashMap<ResourceLocation, LodEntry> byGeo = new LinkedHashMap<>();
        for (LodEntry tier : tiers) {
            LodEntry prev = byGeo.get(tier.geo());
            if (prev == null || (prev.distance() <= 0 && tier.distance() > 0)) {
                byGeo.put(tier.geo(), tier);
            }
        }
        List<LodEntry> unique = new ArrayList<>(byGeo.values());
        unique.sort((a, b) -> {
            int da = a.distance() > 0 ? a.distance() : Integer.MAX_VALUE;
            int db = b.distance() > 0 ? b.distance() : Integer.MAX_VALUE;
            int cmp = Integer.compare(da, db);
            if (cmp != 0) {
                return cmp;
            }
            return a.geo().compareTo(b.geo());
        });

        List<LodEntry> out = new ArrayList<>();
        if (full != null) {
            out.add(full);
        }
        int autoTier = 0;
        for (LodEntry tier : unique) {
            if (full != null && tier.geo().equals(full.geo())) {
                continue;
            }
            autoTier++;
            int distance = tier.distance() > 0 ? tier.distance() : DEFAULT_LOD_STEP * autoTier;
            out.add(new LodEntry(distance, tier.geo(), tier.texture()));
        }
        out.sort((a, b) -> Integer.compare(a.distance(), b.distance()));
        return out;
    }

    /** Pull in {@code models/bedrock/vehicle_lod/{id}.lodN.geo.json} even when Models[] omitted them. */
    private static void mergeFilesystemLods(ResourceLocation entityId, ResourceLocation fallbackTexture,
            List<LodEntry> lods) {
        var resources = Minecraft.getInstance().getResourceManager();
        HashSet<ResourceLocation> known = new HashSet<>();
        for (LodEntry entry : lods) {
            if (entry.geo() != null) {
                known.add(entry.geo());
            }
        }
        String id = entityId.getPath();
        String ns = entityId.getNamespace();
        for (int n = 1; n <= 8; n++) {
            ResourceLocation geo = new ResourceLocation(ns, "models/bedrock/vehicle_lod/" + id + ".lod" + n + ".geo.json");
            if (known.contains(geo) || resources.getResource(geo).isEmpty()) {
                continue;
            }
            ResourceLocation texture = resolveTexture(entityId, null, geo, fallbackTexture);
            lods.add(new LodEntry(DEFAULT_LOD_STEP * n, geo, texture));
            known.add(geo);
            ColtanDebug.once(ColtanDebug.Cat.LOD, "fs-" + geo,
                    "discovered filesystem LOD %s for %s", geo, entityId);
        }
    }

    private static boolean isLodGeoPath(ResourceLocation geo) {
        String path = geo.getPath();
        return path.contains("/vehicle_lod/") || path.contains(".lod");
    }

    private static ResourceLocation resolveTexture(ResourceLocation entityId, ResourceLocation authored,
            ResourceLocation geo, ResourceLocation fallbackTexture) {
        var resources = Minecraft.getInstance().getResourceManager();
        if (authored != null) {
            if (resources.getResource(authored).isPresent()) {
                return authored;
            }
            Coltan.LOGGER.warn("LOD texture missing for {} ({}), trying fallbacks", entityId, authored);
            ColtanDebug.once(ColtanDebug.Cat.LOD, "tex-miss-" + authored,
                    "LOD texture missing for %s (%s) — trying fallbacks", entityId, authored);
        }
        String ns = entityId.getNamespace();
        String id = entityId.getPath();
        int lodLevel = lodLevelFromGeo(geo);
        ResourceLocation[] candidates;
        if (lodLevel > 0) {
            candidates = new ResourceLocation[] {
                    new ResourceLocation(ns, "textures/bedrock/vehicle_lod/" + id + ".lod" + lodLevel + ".png"),
                    new ResourceLocation(ns, "textures/bedrock/vehicle_lod/" + id + ".lod.png"),
                    new ResourceLocation(ns, "textures/bedrock/vehicle/" + id + ".lod.png"),
                    new ResourceLocation(ns, "textures/bedrock/vehicle/" + id + ".png"),
                    fallbackTexture
            };
        } else {
            candidates = new ResourceLocation[] {
                    authored,
                    fallbackTexture
            };
        }
        for (ResourceLocation candidate : candidates) {
            if (candidate != null && resources.getResource(candidate).isPresent()) {
                return candidate;
            }
        }
        return fallbackTexture;
    }

    private static int lodLevelFromGeo(ResourceLocation geo) {
        if (geo == null) {
            return 0;
        }
        String path = geo.getPath();
        int marker = path.lastIndexOf(".lod");
        if (marker < 0) {
            return 0;
        }
        int start = marker + 4;
        int end = start;
        while (end < path.length() && Character.isDigit(path.charAt(end))) {
            end++;
        }
        if (end == start) {
            return 0;
        }
        try {
            return Integer.parseInt(path.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static Sample sample(EntityType<?> type, ResourceLocation entityId, ResourceLocation geo,
                                ResourceLocation texture, ResourceLocation animation) {
        List<LodEntry> lods = lodEntries(entityId, geo, texture);

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            // Prefer the disk cache over zero defaults before a level exists.
            ProfileDiskCache.CachedSample cached = ProfileDiskCache.loadAny(entityId);
            if (cached != null) {
                return Sample.fromCached(cached, lods);
            }
            return Sample.defaults(100, lods);
        }

        VehicleEntity dummy;
        try {
            dummy = (VehicleEntity) type.create(level);
        } catch (Exception e) {
            Coltan.LOGGER.warn("Could not create dummy for {}; using default track sample", type, e);
            ColtanDebug.failOnce("sample-dummy-" + type,
                    "track sample dummy failed for %s: %s", type, e.toString());
            return Sample.defaults(100, lods);
        }
        if (dummy == null) {
            return Sample.defaults(100, lods);
        }

        try {
            int trackLength = Math.max(1, dummy.getTrackAnimationLength());
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
            String rendererClass = renderer.getClass().getName();
            String hash = ProfileDiskCache.contentHash(geo, texture, animation, rendererClass);
            ProfileDiskCache.CachedSample cached = ProfileDiskCache.load(entityId, hash);
            if (cached != null) {
                return Sample.fromCached(cached, lods);
            }

            if (!(renderer instanceof GeoVehicleRenderer<?> geoRenderer)) {
                Sample sample = Sample.defaults(trackLength, lods);
                ProfileDiskCache.save(entityId, hash, sample.toCached());
                return sample;
            }

            float scale = geoRenderer.renderScale();
            float distance = geoRenderer.getTrackDistance();
            boolean hideTurret = geoRenderer.hideForTurretControllerWhileZooming();

            int samples = (int) Math.ceil(trackLength / SAMPLE_STEP) + 1;
            float[] rotX = new float[samples];
            float[] moveY = new float[samples];
            float[] moveZ = new float[samples];
            for (int i = 0; i < samples; i++) {
                float t = Math.min(i * SAMPLE_STEP, trackLength);
                rotX[i] = geoRenderer.getBoneRotX(t);
                moveY[i] = geoRenderer.getBoneMoveY(t);
                moveZ[i] = geoRenderer.getBoneMoveZ(t);
            }

            Sample sample = new Sample(scale, distance, trackLength, rotX, moveY, moveZ, SAMPLE_STEP,
                    hideTurret, false, rendererClass, lods);
            ProfileDiskCache.save(entityId, hash, sample.toCached());
            return sample;
        } catch (Exception e) {
            Coltan.LOGGER.warn("Failed sampling renderer for {}", type, e);
            ColtanDebug.failOnce("sample-renderer-" + type,
                    "renderer sample failed for %s: %s", type, e.toString());
            return Sample.defaults(100, lods);
        } finally {
            dummy.discard();
        }
    }
}
