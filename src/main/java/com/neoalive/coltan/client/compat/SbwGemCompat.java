package com.neoalive.coltan.client.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.VehicleBridgeCache;
import com.neoalive.coltan.client.compat.bridge.VehicleBridgeProfile;
import dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Discovers SBW vehicles and registers shared GemRender visuals for each.
 */
public final class SbwGemCompat {
    private static boolean visualizersRegistered;
    private static boolean sampledWithLevel;

    private SbwGemCompat() {
    }

    public static void init(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(SbwGemCompat::onResourceReload);
        MinecraftForge.EVENT_BUS.addListener(SbwGemCompat::onClientTick);
        event.enqueueWork(() -> {
            rebuild(false);
            tryRegisterVisualizers();
        });
    }

    private static void onResourceReload(EndClientResourceReloadEvent event) {
        sampledWithLevel = false;
        rebuild(true);
        tryRegisterVisualizers();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!sampledWithLevel && Minecraft.getInstance().level != null) {
            rebuild(true);
            sampledWithLevel = true;
            tryRegisterVisualizers();
        }
    }

    private static void rebuild(boolean reloadModels) {
        VehicleBridgeCache.rebuild();
        if (reloadModels) {
            VehicleBridgeCache.reloadModels();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void tryRegisterVisualizers() {
        if (visualizersRegistered || VehicleBridgeCache.profiles().isEmpty()) {
            return;
        }

        int count = 0;
        for (VehicleBridgeProfile profile : VehicleBridgeCache.profiles()) {
            SimpleEntityVisualizer.builder((EntityType) profile.entityType())
                    .factory((ctx, entity, partialTick) ->
                            new SbwVehicleGemVisual(ctx, (VehicleEntity) entity, partialTick))
                    .skipVanillaRender(entity -> true)
                    .apply();
            count++;
        }
        visualizersRegistered = true;
        Coltan.LOGGER.info("Registered GemRender parts visuals for {} SBW vehicle type(s)", count);
    }
}
