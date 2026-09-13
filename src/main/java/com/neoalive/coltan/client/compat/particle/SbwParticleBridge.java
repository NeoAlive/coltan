package com.neoalive.coltan.client.compat.particle;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.client.particle.CannonMuzzleFlareOption;
import com.atsuishio.superbwarfare.client.particle.CustomCloudOption;
import com.atsuishio.superbwarfare.client.particle.CustomFlareOption;
import com.atsuishio.superbwarfare.client.particle.CustomSmokeOption;
import com.neoalive.coltan.Coltan;
import com.wf.gemrender.particle.ParticleEmitter;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/**
 * Diverts SBW soft-sheet ParticleEngine spawns onto GemRender emitters when both soft deps are
 * present. One level Effect owns the rings; {@link #tryDivert} is the funnel from ClientLevel.
 */
public final class SbwParticleBridge {
    @Nullable
    private static SbwParticleEffect effect;
    @Nullable
    private static Level boundLevel;

    private SbwParticleBridge() {
    }

    public static boolean active() {
        return ModList.get().isLoaded("gemrender") && ModList.get().isLoaded("superbwarfare");
    }

    /** Queue / refresh the level Effect once visualization exists. */
    public static void ensureStarted(Level level) {
        if (!active() || level == null) {
            return;
        }
        if (effect != null && boundLevel == level) {
            return;
        }
        shutdown();
        SbwParticleStyles.ensureRegistered();
        effect = new SbwParticleEffect(level);
        boundLevel = level;
        VisualizationHelper.queueAdd(effect);
        Coltan.LOGGER.info("Coltan SBW particle Effect queued for level");
    }

    public static void shutdown() {
        if (effect != null) {
            VisualizationHelper.queueRemove(effect);
            effect.closeEmitters();
            effect = null;
        }
        boundLevel = null;
    }

    /**
     * If {@code options} is a diverted SBW soft particle, spawn on GemRender and return true
     * (caller should cancel ParticleEngine).
     */
    public static boolean tryDivert(ParticleOptions options, double x, double y, double z,
            double vx, double vy, double vz) {
        if (!active() || effect == null) {
            return false;
        }
        if (options instanceof CustomFlareOption flare) {
            spawnFlare(x, y, z, vx, vy, vz, flare.getLife() / 20.0f, Math.max(0.15f, flare.getSize()),
                    tintScale(flare.getRed(), flare.getGreen(), flare.getBlue()));
            return true;
        }
        if (options instanceof CustomCloudOption cloud) {
            spawnCloud(x, y, z, vx, vy, vz + cloud.getGravity() * 0.05,
                    cloud.getLife() / 20.0f, Math.max(0.2f, cloud.getSize()),
                    tintScale(cloud.getRed(), cloud.getGreen(), cloud.getBlue()));
            return true;
        }
        if (options instanceof CustomSmokeOption smoke) {
            spawnSmoke(x, y, z, vx, vy + 0.05, vz, 25.0f + (float) (Math.random() * 10.0),
                    1.0f + (float) (Math.random() * 0.5),
                    tintScale(smoke.getRed(), smoke.getGreen(), smoke.getBlue()));
            return true;
        }
        if (options instanceof CannonMuzzleFlareOption cannon) {
            spawnCannon(x, y, z, vx, vy, vz, Math.max(0.05f, cannon.getLife() / 20.0f),
                    1.2f + cannon.getSizeAdd() * 0.5f, 1.0f);
            return true;
        }
        return false;
    }

    public static void spawnFlare(double x, double y, double z, double vx, double vy, double vz,
            float lifeSec, float sizeScale, float tintScale) {
        spawn(effect == null ? null : effect.flare(), x, y, z, vx, vy, vz, lifeSec, sizeScale, tintScale);
    }

    public static void spawnCloud(double x, double y, double z, double vx, double vy, double vz,
            float lifeSec, float sizeScale, float tintScale) {
        spawn(effect == null ? null : effect.cloud(), x, y, z, vx, vy, vz, lifeSec, sizeScale, tintScale);
    }

    public static void spawnCannon(double x, double y, double z, double vx, double vy, double vz,
            float lifeSec, float sizeScale, float tintScale) {
        spawn(effect == null ? null : effect.cannon(), x, y, z, vx, vy, vz, lifeSec, sizeScale, tintScale);
    }

    public static void spawnSmoke(double x, double y, double z, double vx, double vy, double vz,
            float lifeSec, float sizeScale, float tintScale) {
        spawn(effect == null ? null : effect.smoke(), x, y, z, vx, vy, vz, lifeSec, sizeScale, tintScale);
    }

    private static void spawn(@Nullable ParticleEmitter emitter, double x, double y, double z,
            double vx, double vy, double vz, float lifeSec, float sizeScale, float tintScale) {
        if (emitter == null || lifeSec <= 0.0f) {
            return;
        }
        emitter.spawn(x, y, z, vx, vy, vz, lifeSec, sizeScale, (float) (Math.random() * Math.PI * 2.0),
                tintScale);
    }

    private static float tintScale(float r, float g, float b) {
        return Math.max(0.35f, Math.min(1.35f, (r + g + b) / 1.5f));
    }
}
