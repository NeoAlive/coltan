package com.neoalive.coltan.client.compat.bridge;

import java.util.ArrayList;
import java.util.List;

import com.atsuishio.superbwarfare.client.renderer.entity.GeoVehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.resource.vehicle.DefaultVehicleResource;
import com.atsuishio.superbwarfare.resource.vehicle.VehicleModelPojo;
import com.atsuishio.superbwarfare.resource.vehicle.VehicleResource;
import com.neoalive.coltan.Coltan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Samples SBW {@link GeoVehicleRenderer} params and vehicle resource LOD list.
 */
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
            // Rest pose: zeros. Never encode the sample parameter as a curve value — that lifts
            // track links into the air when a pre-level rebuild publishes these defaults.
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

    public static List<LodEntry> lodEntries(ResourceLocation entityId, ResourceLocation fallbackGeo,
                                            ResourceLocation fallbackTexture) {
        List<LodEntry> lods = new ArrayList<>();
        DefaultVehicleResource res = VehicleResource.getDefault(entityId.toString());
        for (VehicleModelPojo pojo : res.getModels()) {
            if (pojo.model == null) {
                continue;
            }
            ResourceLocation texture = pojo.texture != null ? pojo.texture : fallbackTexture;
            if (texture != null && Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty()) {
                Coltan.LOGGER.warn("LOD texture missing for {} ({}), falling back to {}", entityId, texture,
                        fallbackTexture);
                texture = fallbackTexture;
            }
            lods.add(new LodEntry(pojo.distance, pojo.model, texture));
        }
        if (lods.isEmpty()) {
            lods.add(new LodEntry(0, fallbackGeo, fallbackTexture));
        }
        lods.sort((a, b) -> Integer.compare(a.distance(), b.distance()));
        return List.copyOf(lods);
    }

    public static Sample sample(EntityType<?> type, ResourceLocation entityId, ResourceLocation geo,
                                ResourceLocation texture, ResourceLocation animation) {
        List<LodEntry> lods = lodEntries(entityId, geo, texture);

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            // Warm disk cache does not need the renderer class; prefer it over rest-pose defaults.
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
            return Sample.defaults(100, lods);
        } finally {
            dummy.discard();
        }
    }
}
