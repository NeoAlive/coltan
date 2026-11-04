package com.neoalive.coltan;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * {@code config/coltan-common.toml}. Values are mirrored into plain statics on load / reload so
 * per-frame readers never touch the config spec.
 */
@Mod.EventBusSubscriber(modid = Coltan.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ColtanConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue VEHICLE_DISTANCE_THROTTLE;
    private static final ForgeConfigSpec.BooleanValue GEMRENDER_PARTICLES;
    private static final ForgeConfigSpec.BooleanValue SBW_INSTANCED_PARTICLES;
    private static final ForgeConfigSpec.BooleanValue OCULUS_SINGLE_DRAW;
    private static final ForgeConfigSpec.IntValue PARTICLE_LIGHT_REFRESH_TICKS;
    private static final ForgeConfigSpec.IntValue PARTICLE_MAX_PER_MATERIAL;
    private static final ForgeConfigSpec.BooleanValue SBW_BAKED_ARMOR;
    private static final ForgeConfigSpec.BooleanValue SBW_LIGHT_DEDUPE;
    private static final ForgeConfigSpec.BooleanValue SBW_LIGHT_CLIENT_ONLY;
    private static final ForgeConfigSpec.IntValue SBW_LIGHT_DISTANCE;
    private static final ForgeConfigSpec.IntValue SBW_LIGHT_FADE_STEP;
    private static final ForgeConfigSpec.IntValue SBW_LIGHT_RADIUS_CAP;
    private static final ForgeConfigSpec.IntValue SBW_LIGHT_CHECKS_PER_TICK;

    /** See {@link #SBW_INSTANCED_PARTICLES}. */
    public static volatile boolean sbwInstancedParticles = true;

    /** See {@link #OCULUS_SINGLE_DRAW}. */
    public static volatile boolean oculusSingleDraw = true;

    /** See {@link #PARTICLE_LIGHT_REFRESH_TICKS}. */
    public static volatile int particleLightRefreshTicks = 1;

    /** See {@link #PARTICLE_MAX_PER_MATERIAL}. */
    public static volatile int particleMaxPerMaterial = 16384;

    /** See {@link #SBW_BAKED_ARMOR}. */
    public static volatile boolean sbwBakedArmor = true;

    /** See {@link #SBW_LIGHT_DEDUPE}. */
    public static volatile boolean sbwLightDedupe = true;

    /** See {@link #SBW_LIGHT_CLIENT_ONLY}. */
    public static volatile boolean sbwLightClientOnly = true;

    /** See {@link #SBW_LIGHT_DISTANCE}. */
    public static volatile int sbwLightDistance;

    /** See {@link #SBW_LIGHT_FADE_STEP}. */
    public static volatile int sbwLightFadeStep = 1;

    /** See {@link #SBW_LIGHT_RADIUS_CAP}. */
    public static volatile int sbwLightRadiusCap = -1;

    /** See {@link #SBW_LIGHT_CHECKS_PER_TICK}. */
    public static volatile int sbwLightChecksPerTick;

    /** See {@link #VEHICLE_DISTANCE_THROTTLE}. Off until the config loads. */
    public static volatile boolean vehicleDistanceThrottle;

    /** See {@link #GEMRENDER_PARTICLES}. Off until the config loads. */
    public static volatile boolean gemrenderParticles;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("performance");
        VEHICLE_DISTANCE_THROTTLE =
                builder.comment(
                                "Last resort for vehicle-heavy scenes. Vehicles farther than 96 blocks (except the one",
                                "you ride) skip pose updates on some frames, following Flywheel's distance limiter.",
                                "Saves CPU, but distant moving vehicles visibly trail behind their real position.")
                        .define("vehicleDistanceThrottle", false);
        builder.pop();
        builder.push("experimental");
        GEMRENDER_PARTICLES =
                builder.comment(
                                "EXPERIMENTAL, USE WITH CAUTION.",
                                "Routes Superb Warfare particles (flares, smoke, debris, bullet hole decals)",
                                "through GemRender's GPU particle system instead of vanilla's.",
                                "Known to hang some GPUs (Intel Arc / Meteor Lake on Mesa): blank or black",
                                "textures, missing letters in text, flickering and a black screen.",
                                "If you see any of that, turn this off. Applies on the next world join.")
                        .define("gemrenderParticles", false);
        builder.pop();
        builder.push("particles");
        SBW_INSTANCED_PARTICLES =
                builder.comment(
                                "Simulate and draw Superb Warfare particles (flares, clouds, smoke, debris,",
                                "stars) in Coltan's own pipeline: one instanced draw per material instead of",
                                "one object per particle. Does not use GemRender or Flywheel. Turns itself off",
                                "with a shader pack. Cannot run together with experimental.gemrenderParticles.",
                                "Applies to particles spawned after the change.")
                        .define("sbwInstancedParticles", true);
        OCULUS_SINGLE_DRAW =
                builder.comment(
                                "Without a shader pack, Oculus still renders particles in two passes and",
                                "draws mod particle types (Superb Warfare flares) in both, doubling their",
                                "cost and brightness. Draw them once, in the translucent pass.")
                        .define("oculusSingleDraw", true);
        PARTICLE_LIGHT_REFRESH_TICKS =
                builder.comment(
                                "Ticks between light samples for each Coltan particle. 1 matches vanilla.")
                        .defineInRange("lightRefreshTicks", 1, 1, 20);
        PARTICLE_MAX_PER_MATERIAL =
                builder.comment(
                                "Most Coltan particles kept per material before the oldest are dropped.",
                                "16384 matches vanilla's limit per particle render type.")
                        .defineInRange("maxPerMaterial", 16384, 256, 65536);
        builder.pop();
        builder.push("sbw");
        SBW_BAKED_ARMOR =
                builder.comment(
                                "Draw Superb Warfare armor that still uses Simple Bedrock Model's renderer",
                                "(on players, SEM units and any other wearer) from a baked mesh instead of",
                                "transforming every cube each frame. Same pose, scaling and texture.")
                        .define("bakedArmor", true);
        SBW_LIGHT_DEDUPE =
                builder.comment(
                                "Only relight a Superb Warfare flash or trail light when its brightness",
                                "changes, instead of every tick it is alive.")
                        .define("lightDedupe", true);
        SBW_LIGHT_CLIENT_ONLY =
                builder.comment(
                                "Keep Superb Warfare flash and trail lights on the client light engine. In",
                                "singleplayer the integrated server otherwise bakes them into the saved",
                                "world, leaving stale light.")
                        .define("lightClientOnly", true);
        SBW_LIGHT_DISTANCE =
                builder.comment(
                                "Do not create Superb Warfare flash or trail lights farther than this many",
                                "blocks from the camera. 0 = off.")
                        .defineInRange("lightDistance", 0, 0, 1024);
        SBW_LIGHT_FADE_STEP =
                builder.comment(
                                "Relight a fading Superb Warfare light only every this many brightness levels.",
                                "1 = every level (unchanged). Higher is cheaper with a coarser fade.")
                        .defineInRange("lightFadeStep", 1, 1, 15);
        SBW_LIGHT_RADIUS_CAP =
                builder.comment(
                                "Largest radius a Superb Warfare area light may cover (burning vehicles,",
                                "muzzle flashes). -1 = unchanged, 0 = a single block.")
                        .defineInRange("lightRadiusCap", -1, -1, 5);
        SBW_LIGHT_CHECKS_PER_TICK =
                builder.comment(
                                "Most live Superb Warfare light updates queued per tick; the rest wait for",
                                "the next ticks. Expiring lights are never delayed. 0 = unlimited.")
                        .defineInRange("lightChecksPerTick", 0, 0, 65536);
        builder.pop();
        SPEC = builder.build();
    }

    private ColtanConfig() {}

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        refresh(event);
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        refresh(event);
    }

    private static void refresh(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            vehicleDistanceThrottle = VEHICLE_DISTANCE_THROTTLE.get();
            gemrenderParticles = GEMRENDER_PARTICLES.get();
            sbwInstancedParticles = SBW_INSTANCED_PARTICLES.get();
            oculusSingleDraw = OCULUS_SINGLE_DRAW.get();
            particleLightRefreshTicks = PARTICLE_LIGHT_REFRESH_TICKS.get();
            particleMaxPerMaterial = PARTICLE_MAX_PER_MATERIAL.get();
            sbwBakedArmor = SBW_BAKED_ARMOR.get();
            sbwLightDedupe = SBW_LIGHT_DEDUPE.get();
            sbwLightClientOnly = SBW_LIGHT_CLIENT_ONLY.get();
            sbwLightDistance = SBW_LIGHT_DISTANCE.get();
            sbwLightFadeStep = SBW_LIGHT_FADE_STEP.get();
            sbwLightRadiusCap = SBW_LIGHT_RADIUS_CAP.get();
            sbwLightChecksPerTick = SBW_LIGHT_CHECKS_PER_TICK.get();
        }
    }
}
