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

    private static final Object LOCK = new Object();
    private static int flare = -1;
    private static int cloud = -1;
    private static int cannon = -1;
    private static int smoke = -1;

    private SbwParticleStyles() {
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
                    .tint(0.55f, 0.45f, 0.38f)
                    .alpha(0.55f, 0.7f)
                    .fadeIn(0.05f)
                    .cool(0.15f, 0.55f)
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
                    .alpha(0.5f, 0.35f)
                    .fadeIn(0.1f)
                    .spin(0.15f)
                    .build());
        }
    }
}
