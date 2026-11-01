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
        }
    }
}
