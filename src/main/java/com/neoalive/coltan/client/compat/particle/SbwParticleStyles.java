package com.neoalive.coltan.client.compat.particle;

import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.particle.ParticleStyle;
import net.minecraft.resources.ResourceLocation;

/** Shared GemRender particle styles for diverted SBW ParticleEngine effects. */
public final class SbwParticleStyles {
    /** First frame of SBW CustomFlare sheet. */
    public static final ResourceLocation FLARE_TEX =
            new ResourceLocation("superbwarfare", "textures/particle/custom_flare_0.png");
    /** Minecraft generic puff used by SBW CustomCloud. */
    public static final ResourceLocation CLOUD_TEX =
            new ResourceLocation("minecraft", "textures/particle/generic_0.png");
    /** SBW cannon muzzle / exp cloud sheet frame. */
    public static final ResourceLocation CANNON_TEX =
            new ResourceLocation("superbwarfare", "textures/particle/exp_cloud_0.png");
    /** SBW CustomSmoke generic sheet frame. */
    public static final ResourceLocation SMOKE_TEX =
            new ResourceLocation("superbwarfare", "textures/particle/generic_0.png");

    /** SBW FireStar / ExplosionDebris sheet frame (embers). */
    public static final ResourceLocation FIRE_STAR_TEX =
            new ResourceLocation("superbwarfare", "textures/particle/fire_star_0.png");
    /** SBW WhiteStar sprite. */
    public static final ResourceLocation WHITE_STAR_TEX =
            new ResourceLocation("superbwarfare", "textures/particle/white_star.png");
    /** Vanilla generic puff used by SBW RisingSmoke. */
    public static final ResourceLocation RISING_SMOKE_TEX =
            new ResourceLocation("minecraft", "textures/particle/generic_0.png");
    /** Coltan bullet hole (SBW samples the hit block's sprite; GemRender decals take one texture). */
    public static final ResourceLocation DECAL_TEX =
            new ResourceLocation("coltan", "textures/particle/bullet_hole.png");

    /** SBW BulletDecalParticle: lifetime 200 ticks, quadSize 0.05 (corner +-1) => 0.1 block wide. */
    public static final float DECAL_LIFE_SEC = 10.0f;

    private static final Object LOCK = new Object();
    private static int flare = -1;
    private static int cloud = -1;
    private static int cannon = -1;
    private static int smoke = -1;
    private static int fireStar = -1;
    private static int whiteStar = -1;
    private static int risingSmoke = -1;
    private static int debris = -1;
    private static int decal = -1;

    private SbwParticleStyles() {
    }

    public static int fireStar() {
        ensure();
        return fireStar;
    }

    public static int whiteStar() {
        ensure();
        return whiteStar;
    }

    public static int risingSmoke() {
        ensure();
        return risingSmoke;
    }

    public static int debris() {
        ensure();
        return debris;
    }

    public static int decal() {
        ensure();
        return decal;
    }

    public static int flare() {
        ensure();
        return flare;
    }

    public static int cloud() {
        ensure();
        return cloud;
    }

    public static int cannon() {
        ensure();
        return cannon;
    }

    public static int smoke() {
        ensure();
        return smoke;
    }

    public static void ensureRegistered() {
        ensure();
    }

    private static void ensure() {
        synchronized (LOCK) {
            if (flare >= 0) {
                return;
            }
            ParticleBuffer buffer = ParticleBuffer.getInstance();
            // Trail / explosion soft flares — warm brownish default matching missile trails.
            flare = buffer.registerStyle(ParticleStyle.builder()
                    .drag(ParticleStyle.dragFromPerTickFactor(0.93f))
                    .gravity(0.4f)
                    .size(0.35f, 1.8f)
                    // Colour is per particle (ParticleLook from the SBW option); white style tint.
                    .alpha(0.55f, 0.7f)
                    .fadeIn(0.05f)
                    // glowing model: 80% of alpha emitted while hot, cooling over the first part of
                    // life into absorbing smoke (SBW: white-hot core easing toward its colour).
                    .glow(0.8f)
                    .cool(0.15f, 0.55f)
                    .light(ParticleStyle.FULL_BRIGHT, ParticleStyle.FULL_BRIGHT)
                    .spin(0.4f)
                    .build());
            // Terrain dust / shell trails.
            cloud = buffer.registerStyle(ParticleStyle.builder()
                    .drag(ParticleStyle.dragFromPerTickFactor(0.9f),
                            ParticleStyle.dragFromPerTickFactor(0.95f))
                    .gravity(0.8f)
                    .size(0.5f, 2.2f)
                    .tint(0.75f, 0.72f, 0.68f)
                    .alpha(0.45f, 0.55f)
                    .fadeIn(0.08f)
                    .spin(0.25f)
                    .build());
            // Cannon muzzle — short bright flash.
            cannon = buffer.registerStyle(ParticleStyle.builder()
                    .drag(ParticleStyle.dragFromPerTickFactor(0.85f))
                    .gravity(-1.2f)
                    .size(0.8f, -1.5f)
                    .tint(1.0f, 0.85f, 0.55f)
                    .alpha(0.9f, 1.8f)
                    .fadeIn(0.02f)
                    .light(ParticleStyle.FULL_BRIGHT, ParticleStyle.FULL_BRIGHT)
                    .spin(0.8f)
                    .build());
            // Decoy / M18 smoke — long-lived buoyant puffs.
            smoke = buffer.registerStyle(ParticleStyle.builder()
                    .drag(ParticleStyle.dragFromPerTickFactor(0.96f))
                    .gravity(-0.6f)
                    .size(0.9f, 1.4f)
                    .tint(0.85f, 0.85f, 0.85f)
                    // absorbance sums optical depth: ~15% under the old translucent 0.5. Tune by eye.
                    .alpha(0.42f, 0.35f)
                    .fadeIn(0.1f)
                    .spin(0.15f)
                    .build());

            // Values below are read off SBW 0.8.9.1 bytecode (javap). Vanilla per-tick units:
            // yd -= 0.04 * gravity, v *= friction (0.98 default), quadSize is a half-width.
            // FireStar: gravity 1, v *= 0.98, quadSize ~0.11 x 0.75, life 40-60 t, full bright.
            fireStar = buffer.registerStyle(ParticleStyle.builder()
                    .drag(ParticleStyle.dragFromPerTickFactor(0.98f))
                    .gravity(ParticleStyle.gravityFromPerTickDelta(-0.04f))
                    .size(0.22f, -0.15f)
                    .alpha(1.0f, 1.0f)
                    .fadeOut(0.6f)
                    .streak(0.03f)
                    .light(ParticleStyle.FULL_BRIGHT, ParticleStyle.FULL_BRIGHT)
                    .bouncesOnContact(0.3f, 0.6f)
                    .build());
            // WhiteStar: no gravity, v *= 0.95, quadSize ~0.11 x 0.9 shrinking x0.95/t, full bright.
            whiteStar = buffer.registerStyle(ParticleStyle.builder()
                    .drag(ParticleStyle.dragFromPerTickFactor(0.95f))
                    .size(0.27f, -0.25f)
                    .alpha(1.0f, 1.0f)
                    .fadeOut(0.5f)
                    .streak(0.02f)
                    .light(ParticleStyle.FULL_BRIGHT, ParticleStyle.FULL_BRIGHT)
                    .build());
            // RisingSmoke: gravity -0.2 (rises), v *= 0.98, scale 3, alpha 0.7 x0.98/t.
            risingSmoke = buffer.registerStyle(ParticleStyle.builder()
                    .drag(ParticleStyle.dragFromPerTickFactor(0.98f))
                    .gravity(ParticleStyle.gravityFromPerTickDelta(0.008f))
                    .size(0.9f, 1.2f)
                    .tint(0.8f, 0.8f, 0.8f)
                    .alpha(0.6f, 0.8f)
                    .fadeIn(0.05f)
                    .spin(0.2f)
                    .build());
            // ExplosionDebris: gravity 1, friction 0.92, hasPhysics (vanilla collides every tick: the
            // cost this diversion removes; the arc is swept once at spawn instead), full bright.
            debris = buffer.registerStyle(ParticleStyle.builder()
                    .drag(ParticleStyle.dragFromPerTickFactor(0.92f))
                    .gravity(ParticleStyle.gravityFromPerTickDelta(-0.04f))
                    .size(0.3f, -0.1f)
                    .alpha(1.0f, 1.0f)
                    .fadeOut(0.7f)
                    .light(ParticleStyle.FULL_BRIGHT, ParticleStyle.FULL_BRIGHT)
                    .stopsOnContact()
                    .build());
            // BulletDecal: 0.1 block, alpha 0.9, never moves (DECAL ignores style motion).
            decal = buffer.registerStyle(ParticleStyle.builder()
                    .size(0.1f, 0.0f)
                    .alpha(0.9f, 1.0f)
                    .fadeOut(0.9f)
                    .build());
        }
    }
}
