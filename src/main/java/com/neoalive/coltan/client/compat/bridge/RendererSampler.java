package com.neoalive.coltan.client.compat.bridge;

import com.atsuishio.superbwarfare.client.renderer.entity.GeoVehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.neoalive.coltan.Coltan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Samples SBW {@link GeoVehicleRenderer} track curves and scale into flat tables.
 */
public final class RendererSampler {
    public static final float SAMPLE_STEP = 0.25f;

    private RendererSampler() {
    }

    public record Sample(float renderScale, float trackDistance, int trackLength, float[] rotX, float[] moveY,
                         float[] moveZ, float step) {
        public static Sample defaults(int trackLength) {
            int len = Math.max(1, trackLength);
            float[] identity = new float[(int) Math.ceil(len / SAMPLE_STEP) + 1];
            for (int i = 0; i < identity.length; i++) {
                identity[i] = i * SAMPLE_STEP;
            }
            return new Sample(1.0f, 2.0f, len, identity, identity.clone(), identity.clone(), SAMPLE_STEP);
        }
    }

    public static Sample sample(EntityType<?> type) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return Sample.defaults(100);
        }

        VehicleEntity dummy;
        try {
            dummy = (VehicleEntity) type.create(level);
        } catch (Exception e) {
            Coltan.LOGGER.warn("Could not create dummy for {}; using default track sample", type, e);
            return Sample.defaults(100);
        }
        if (dummy == null) {
            return Sample.defaults(100);
        }

        try {
            int trackLength = Math.max(1, dummy.getTrackAnimationLength());
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
            if (!(renderer instanceof GeoVehicleRenderer<?> geo)) {
                return Sample.defaults(trackLength);
            }

            float scale = geo.renderScale();
            float distance = geo.getTrackDistance();
            int samples = (int) Math.ceil(trackLength / SAMPLE_STEP) + 1;
            float[] rotX = new float[samples];
            float[] moveY = new float[samples];
            float[] moveZ = new float[samples];
            for (int i = 0; i < samples; i++) {
                float t = Math.min(i * SAMPLE_STEP, trackLength);
                rotX[i] = geo.getBoneRotX(t);
                moveY[i] = geo.getBoneMoveY(t);
                moveZ[i] = geo.getBoneMoveZ(t);
            }
            return new Sample(scale, distance, trackLength, rotX, moveY, moveZ, SAMPLE_STEP);
        } catch (Exception e) {
            Coltan.LOGGER.warn("Failed sampling renderer for {}", type, e);
            return Sample.defaults(100);
        } finally {
            dummy.discard();
        }
    }
}
