package com.neoalive.coltan.client.compat.sbw.particle;

import com.atsuishio.superbwarfare.client.particle.BulletDecalOption;
import com.atsuishio.superbwarfare.client.particle.CannonMuzzleFlareOption;
import com.atsuishio.superbwarfare.client.particle.CustomCloudOption;
import com.atsuishio.superbwarfare.client.particle.CustomFlareOption;
import com.atsuishio.superbwarfare.client.particle.CustomSmokeOption;
import com.atsuishio.superbwarfare.client.particle.ExplosionDebrisOption;
import com.atsuishio.superbwarfare.init.ModParticleTypes;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.sbw.SbwGemPresence;
import com.neoalive.coltan.debug.ColtanDebug;
import com.wf.gemrender.particle.ParticleEmitter;
import com.wf.gemrender.particle.ParticleLook;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.level.Level;

/**
 * Diverts SBW soft-sheet ParticleEngine spawns onto GemRender emitters when both soft deps are
 * present. One level Effect owns the rings; {@link #tryDivert} is the funnel from ClientLevel.
 */
public final class SbwParticleBridge {
    @Nullable private static SbwParticleEffect effect;
    @Nullable private static Level boundLevel;

    /**
     * SBW SimpleParticleTypes, resolved once per Effect start (RegistryObject.get is a map read).
     */
    @Nullable private static ParticleType<?> fireStarType;

    @Nullable private static ParticleType<?> whiteStarType;
    @Nullable private static ParticleType<?> risingSmokeType;

    /** SBW spawn velocities are per tick; GemRender emitters take blocks per second. */
    private static final double PER_SECOND = 20.0;

    /** CustomFlare keeps 60% of its spawn velocity (per tick), converted to per second. */
    private static final double FLARE_VELOCITY = 0.6 * PER_SECOND;

    private SbwParticleBridge() {}

    public static boolean active() {
        return SbwGemPresence.ACTIVE;
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
        try {
            fireStarType = ModParticleTypes.FIRE_STAR.get();
            whiteStarType = ModParticleTypes.WHITE_STAR.get();
            risingSmokeType = ModParticleTypes.RISING_SMOKE.get();
        } catch (RuntimeException | LinkageError e) {
            // Simple types stay on ParticleEngine; option-typed diversions still work.
            ColtanDebug.failOnce(
                    "particle-simple-types",
                    "SBW simple particle types unavailable: %s",
                    e.toString());
        }
        effect = new SbwParticleEffect(level);
        boundLevel = level;
        VisualizationHelper.queueAdd(effect);
        Coltan.LOGGER.info(
                "Coltan SBW particle Effect queued for level (flare: {}, smoke: {})",
                SbwParticleStyles.FLARE_MODE,
                SbwParticleStyles.SMOKE_MODE);
        ColtanDebug.log(
                ColtanDebug.Cat.PARTICLE,
                "Effect queued for level %s",
                level.dimension().location());
    }

    public static void shutdown() {
        if (effect != null) {
            ColtanDebug.log(ColtanDebug.Cat.PARTICLE, "Effect shutdown");
            VisualizationHelper.queueRemove(effect);
            effect.closeEmitters();
            effect = null;
        }
        SbwDecalRegions.clear();
        boundLevel = null;
    }

    /**
     * If {@code options} is a diverted SBW soft particle, spawn on GemRender and return true
     * (caller should cancel ParticleEngine).
     */
    public static boolean tryDivert(
            ParticleOptions options,
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz) {
        if (!active()) {
            return false;
        }
        if (effect == null) {
            ColtanDebug.failOnce(
                    "particle-no-effect",
                    "tryDivert while Effect null — soft particles still going to ParticleEngine");
            return false;
        }
        if (options instanceof CustomFlareOption flare
                && !"vanilla".equals(SbwParticleStyles.FLARE_MODE)) {
            // SBW CustomFlareParticle: v *= 0.6 at spawn (per tick), starts white-hot at full
            // brightness and cools toward the option colour. Colour rides per particle (look,
            // full bright); the glowing model + style glow/cool give the hot core.
            float lifeSec = Math.min(flare.getLife() / 20.0f, SbwParticleStyles.FLARE_VISIBLE_SEC);
            if (lifeSec > 0.0f) {
                // SBW birth width: 2 x vanilla quadSize (0.1..0.2) x size x 14.
                float width = (0.2f + (float) Math.random() * 0.2f) * flare.getSize() * 14.0f;
                // SBW starts white and lerps to its colour within ~1 s; GemRender has no colour
                // lerp,
                // so the particle takes the halfway tint (reads hot near the missile, tan behind
                // it).
                int rgb =
                        rgb(
                                0.5f + 0.5f * flare.getRed(),
                                0.5f + 0.5f * flare.getGreen(),
                                0.5f + 0.5f * flare.getBlue());
                effect.flare()
                        .spawn(
                                x,
                                y,
                                z,
                                vx * FLARE_VELOCITY,
                                vy * FLARE_VELOCITY,
                                vz * FLARE_VELOCITY,
                                lifeSec,
                                Math.max(0.15f, width),
                                (float) (Math.random() * Math.PI * 2.0),
                                1.0f,
                                ParticleLook.of(rgb));
            }
            ColtanDebug.count(
                    ColtanDebug.Cat.PARTICLE,
                    "divert-flare",
                    5000L,
                    "diverted CustomFlare total=%d");
            return true;
        }
        if (options instanceof CustomCloudOption cloud) {
            spawnCloud(
                    x,
                    y,
                    z,
                    vx,
                    vy,
                    vz + cloud.getGravity() * 0.05,
                    cloud.getLife() / 20.0f,
                    Math.max(0.2f, cloud.getSize()),
                    tintScale(cloud.getRed(), cloud.getGreen(), cloud.getBlue()));
            ColtanDebug.count(
                    ColtanDebug.Cat.PARTICLE,
                    "divert-cloud",
                    5000L,
                    "diverted CustomCloud total=%d");
            return true;
        }
        if (options instanceof CustomSmokeOption smoke
                && !"vanilla".equals(SbwParticleStyles.SMOKE_MODE)) {
            spawnSmoke(
                    x,
                    y,
                    z,
                    vx,
                    vy + 0.05,
                    vz,
                    25.0f + (float) (Math.random() * 10.0),
                    1.0f + (float) (Math.random() * 0.5),
                    tintScale(smoke.getRed(), smoke.getGreen(), smoke.getBlue()));
            ColtanDebug.count(
                    ColtanDebug.Cat.PARTICLE,
                    "divert-smoke",
                    5000L,
                    "diverted CustomSmoke total=%d");
            return true;
        }
        if (options instanceof CannonMuzzleFlareOption cannon) {
            spawnCannon(
                    x,
                    y,
                    z,
                    vx,
                    vy,
                    vz,
                    Math.max(0.05f, cannon.getLife() / 20.0f),
                    1.2f + cannon.getSizeAdd() * 0.5f,
                    1.0f);
            ColtanDebug.count(
                    ColtanDebug.Cat.PARTICLE,
                    "divert-cannon",
                    5000L,
                    "diverted CannonMuzzleFlare total=%d");
            return true;
        }
        if (options instanceof ExplosionDebrisOption debris) {
            spawnDebris(debris, x, y, z, vx, vy, vz);
            return true;
        }
        if (options instanceof BulletDecalOption decal) {
            return spawnDecal(decal, x, y, z);
        }
        ParticleType<?> type = options.getType();
        if (type == fireStarType && fireStarType != null) {
            // SBW: v *= 0.98 at spawn, life 40-60 ticks.
            spawnPerTick(
                    effect.fireStar(),
                    x,
                    y,
                    z,
                    vx * 0.98,
                    vy * 0.98,
                    vz * 0.98,
                    (40 + (float) Math.random() * 20.0f) / 20.0f,
                    0.8f + (float) Math.random() * 0.4f);
            ColtanDebug.count(
                    ColtanDebug.Cat.PARTICLE,
                    "divert-firestar",
                    5000L,
                    "diverted FireStar total=%d");
            return true;
        }
        if (type == whiteStarType && whiteStarType != null) {
            spawnPerTick(
                    effect.whiteStar(),
                    x,
                    y,
                    z,
                    vx * 0.95,
                    vy * 0.95,
                    vz * 0.95,
                    (40 + (float) Math.random() * 20.0f) / 20.0f,
                    0.8f + (float) Math.random() * 0.4f);
            ColtanDebug.count(
                    ColtanDebug.Cat.PARTICLE,
                    "divert-whitestar",
                    5000L,
                    "diverted WhiteStar total=%d");
            return true;
        }
        if (type == risingSmokeType
                && risingSmokeType != null
                && !"vanilla".equals(SbwParticleStyles.SMOKE_MODE)) {
            // SBW: v *= 0.8 at spawn, life 20-80 ticks.
            spawnPerTick(
                    effect.risingSmoke(),
                    x,
                    y,
                    z,
                    vx * 0.8,
                    vy * 0.8,
                    vz * 0.8,
                    (20 + (float) Math.random() * 60.0f) / 20.0f,
                    0.75f + (float) Math.random() * 0.5f);
            ColtanDebug.count(
                    ColtanDebug.Cat.PARTICLE,
                    "divert-risingsmoke",
                    5000L,
                    "diverted RisingSmoke total=%d");
            return true;
        }
        return false;
    }

    /** New diversions: per-tick SBW velocity converted to GemRender's per-second units. */
    private static void spawnPerTick(
            ParticleEmitter emitter,
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            float lifeSec,
            float sizeScale) {
        emitter.spawn(
                x,
                y,
                z,
                vx * PER_SECOND,
                vy * PER_SECOND,
                vz * PER_SECOND,
                lifeSec,
                sizeScale,
                (float) (Math.random() * Math.PI * 2.0),
                1.0f);
    }

    /**
     * Arcs are swept once here (stopsOnContact), not collided every tick. One probe is reused while
     * the clock stands still.
     */
    private static void spawnDebris(
            ExplosionDebrisOption debris,
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz) {
        Level level = boundLevel;
        if (level == null || effect == null) {
            return;
        }
        float life = Math.max(0.1f, debris.getLife() / 20.0f);
        int rgb = rgb(debris.getRed(), debris.getGreen(), debris.getBlue());
        effect.debris()
                .spawn(
                        level,
                        x,
                        y,
                        z,
                        vx * PER_SECOND,
                        vy * PER_SECOND,
                        vz * PER_SECOND,
                        life,
                        Math.max(0.2f, debris.getSize()),
                        (float) (Math.random() * Math.PI * 2.0),
                        1.0f,
                        0.0f,
                        ParticleLook.of(rgb));
        ColtanDebug.count(
                ColtanDebug.Cat.PARTICLE,
                "divert-debris",
                5000L,
                "diverted ExplosionDebris total=%d");
    }

    /**
     * @return false (left to SBW) when the client level is not a ClientLevel.
     */
    private static boolean spawnDecal(BulletDecalOption decal, double x, double y, double z) {
        if (!(boundLevel instanceof ClientLevel level)) {
            return false;
        }
        Direction face = decal.getDirection();
        BlockPos pos = decal.getPos();
        int rgb = rgb(decal.getRed(), decal.getGreen(), decal.getBlue());
        int look =
                ParticleLook.lit(
                        level,
                        x + face.getStepX() * 0.5,
                        y + face.getStepY() * 0.5,
                        z + face.getStepZ() * 0.5,
                        rgb);
        if (!SbwDecalRegions.spawn(
                level, pos, x, y, z, face.getStepX(), face.getStepY(), face.getStepZ(), look)) {
            return false;
        }
        ColtanDebug.count(
                ColtanDebug.Cat.PARTICLE, "divert-decal", 5000L, "diverted BulletDecal total=%d");
        return true;
    }

    private static int rgb(float r, float g, float b) {
        int ri = Math.max(0, Math.min(255, Math.round(r * 255.0f)));
        int gi = Math.max(0, Math.min(255, Math.round(g * 255.0f)));
        int bi = Math.max(0, Math.min(255, Math.round(b * 255.0f)));
        return ri << 16 | gi << 8 | bi;
    }

    public static void spawnFlare(
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            float lifeSec,
            float sizeScale,
            float tintScale) {
        spawn(
                effect == null ? null : effect.flare(),
                x,
                y,
                z,
                vx,
                vy,
                vz,
                lifeSec,
                sizeScale,
                tintScale);
    }

    public static void spawnCloud(
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            float lifeSec,
            float sizeScale,
            float tintScale) {
        spawn(
                effect == null ? null : effect.cloud(),
                x,
                y,
                z,
                vx,
                vy,
                vz,
                lifeSec,
                sizeScale,
                tintScale);
    }

    public static void spawnCannon(
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            float lifeSec,
            float sizeScale,
            float tintScale) {
        spawn(
                effect == null ? null : effect.cannon(),
                x,
                y,
                z,
                vx,
                vy,
                vz,
                lifeSec,
                sizeScale,
                tintScale);
    }

    public static void spawnSmoke(
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            float lifeSec,
            float sizeScale,
            float tintScale) {
        spawn(
                effect == null ? null : effect.smoke(),
                x,
                y,
                z,
                vx,
                vy,
                vz,
                lifeSec,
                sizeScale,
                tintScale);
    }

    private static void spawn(
            @Nullable ParticleEmitter emitter,
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            float lifeSec,
            float sizeScale,
            float tintScale) {
        if (emitter == null || lifeSec <= 0.0f) {
            return;
        }
        emitter.spawn(
                x,
                y,
                z,
                vx,
                vy,
                vz,
                lifeSec,
                sizeScale,
                (float) (Math.random() * Math.PI * 2.0),
                tintScale);
    }

    private static float tintScale(float r, float g, float b) {
        return Math.max(0.35f, Math.min(1.35f, (r + g + b) / 1.5f));
    }
}
