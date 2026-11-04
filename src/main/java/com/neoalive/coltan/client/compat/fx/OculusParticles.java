package com.neoalive.coltan.client.compat.fx;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.ColtanConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraftforge.fml.ModList;

/**
 * Oculus particle phases, read by reflection so Coltan does not compile against Oculus.
 *
 * <p>With its default MIXED setting (also used without a shader pack) Oculus renders particles
 * twice per frame: an OPAQUE pass that skips only vanilla's translucent sheet, and a TRANSLUCENT
 * pass that skips vanilla's four opaque sheets. Any mod render type passes both filters.
 */
public final class OculusParticles {
    public static final int EVERYTHING = 0;
    public static final int OPAQUE = 1;
    public static final int TRANSLUCENT = 2;

    private static boolean resolved;
    private static MethodHandle phaseGetter;
    private static MethodHandle packInUse;
    private static Object irisApi;

    private OculusParticles() {}

    /** Current Oculus phase of the particle engine, {@link #EVERYTHING} without Oculus. */
    public static int phase(ParticleEngine engine) {
        resolve();
        if (phaseGetter == null) {
            return EVERYTHING;
        }
        try {
            Object phase = phaseGetter.invoke(engine);
            if (phase == null) {
                return EVERYTHING;
            }
            String name = ((Enum<?>) phase).name();
            return switch (name) {
                case "OPAQUE" -> OPAQUE;
                case "TRANSLUCENT" -> TRANSLUCENT;
                default -> EVERYTHING;
            };
        } catch (Throwable t) {
            phaseGetter = null;
            return EVERYTHING;
        }
    }

    public static boolean shaderPackInUse() {
        resolve();
        if (packInUse == null) {
            return false;
        }
        try {
            return (boolean) packInUse.invoke(irisApi);
        } catch (Throwable t) {
            packInUse = null;
            return false;
        }
    }

    /**
     * True for a render type Oculus also draws in its translucent pass, while the OPAQUE pass is
     * running without a shader pack: skipping it here leaves exactly one draw.
     */
    public static boolean skipInOpaquePass(ParticleEngine engine, ParticleRenderType type) {
        if (!ColtanConfig.oculusSingleDraw || phase(engine) != OPAQUE) {
            return false;
        }
        if (type == ParticleRenderType.TERRAIN_SHEET
                || type == ParticleRenderType.PARTICLE_SHEET_OPAQUE
                || type == ParticleRenderType.PARTICLE_SHEET_LIT
                || type == ParticleRenderType.CUSTOM
                || type == ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT) {
            return false;
        }
        return !shaderPackInUse();
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!ModList.get().isLoaded("oculus")) {
            return;
        }
        try {
            for (Field field : ParticleEngine.class.getDeclaredFields()) {
                if (field.getType()
                        .getName()
                        .equals("net.irisshaders.iris.fantastic.ParticleRenderingPhase")) {
                    field.setAccessible(true);
                    phaseGetter = MethodHandles.lookup().unreflectGetter(field);
                    break;
                }
            }
        } catch (Throwable t) {
            Coltan.LOGGER.warn("Coltan could not read the Oculus particle phase", t);
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisApi = api.getMethod("getInstance").invoke(null);
            Method inUse = api.getMethod("isShaderPackInUse");
            packInUse = MethodHandles.lookup().unreflect(inUse);
        } catch (Throwable t) {
            Coltan.LOGGER.warn("Coltan could not read the Oculus shader pack state", t);
        }
    }
}
