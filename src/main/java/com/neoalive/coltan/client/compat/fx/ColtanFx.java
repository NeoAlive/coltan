package com.neoalive.coltan.client.compat.fx;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;

/**
 * Entry point the particle engine mixin calls. It references no soft dependency: the SBW side
 * installs its {@link Hooks} from the reflective boot, and until then every call is a no-op.
 */
public final class ColtanFx {
    /** What the SBW particle pipeline does at each particle engine step. Render thread only. */
    public interface Hooks {
        /** True when the particle was taken over and must not enter the vanilla engine. */
        boolean capture(Particle particle);

        /** End of {@code ParticleEngine.tick}, where vanilla moves pending particles in. */
        void tick();

        /** Inside {@code ParticleEngine.render}, before the model view pose is popped. */
        void render(Camera camera, float partialTick, int oculusPhase);

        /** The engine dropped all of its particles (level change, resource reload). */
        void clear();
    }

    private static volatile Hooks hooks;

    private ColtanFx() {}

    public static void install(Hooks installed) {
        hooks = installed;
    }

    public static boolean capture(Particle particle) {
        Hooks current = hooks;
        return current != null && current.capture(particle);
    }

    public static void tick() {
        Hooks current = hooks;
        if (current != null) {
            current.tick();
        }
    }

    public static void render(ParticleEngine engine, Camera camera, float partialTick) {
        Hooks current = hooks;
        if (current != null) {
            current.render(camera, partialTick, OculusParticles.phase(engine));
        }
    }

    public static void clear() {
        Hooks current = hooks;
        if (current != null) {
            current.clear();
        }
    }
}
