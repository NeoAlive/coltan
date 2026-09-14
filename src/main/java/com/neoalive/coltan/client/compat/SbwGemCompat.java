package com.neoalive.coltan.client.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.ArmorBridgeCache;
import com.neoalive.coltan.client.compat.bridge.BlockBridgeCache;
import com.neoalive.coltan.client.compat.bridge.GunBridgeCache;
import com.neoalive.coltan.client.compat.bridge.MunitionBridgeCache;
import com.neoalive.coltan.client.compat.bridge.ProjectileBridgeCache;
import com.neoalive.coltan.client.compat.particle.SbwParticleBridge;
import com.neoalive.coltan.client.compat.bridge.VehicleBridgeCache;
import com.neoalive.coltan.client.compat.bridge.VehicleBridgeProfile;
import com.neoalive.coltan.debug.ColtanDebug;
import dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Discovers SBW vehicles/armor/guns/blocks/projectiles/munitions and hooks GemRender visuals.
 *
 * <p>Vehicle Flywheel visualizers must be registered <em>before</em> the client receives entities
 * on login. {@code skipVanillaRender} otherwise blanks hulls that never get a visual attached.
 */
public final class SbwGemCompat {
    private static boolean vehicleVisualizersRegistered;
    private static boolean sampledWithLevel;

    private SbwGemCompat() {
    }

    public static void init(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SbwArmorGemCompat.init();
            SbwGunGemCompat.init();
            SbwBlockGemCompat.init();
            SbwMunitionGemCompat.init();
            SbwProjectileGemCompat.init();
            if (SbwParticleBridge.active()) {
                com.neoalive.coltan.client.compat.particle.SbwParticleStyles.ensureRegistered();
                Coltan.LOGGER.info("Coltan SBW particle bridge styles ready");
            }
            // Pre-world: build catalogs (disk-cache / defaults OK without a Level) and register
            // Flywheel visualizers so login entity packets already match a factory.
            rebuild(false);
            tryRegisterVisualizers();
            ColtanDebug.log(ColtanDebug.Cat.BOOT,
                    "early catalog+visualizers (pre-world); vehicles=%d",
                    VehicleBridgeCache.profiles().size());
        });
        MinecraftForge.EVENT_BUS.addListener(SbwGemCompat::onResourceReload);
        MinecraftForge.EVENT_BUS.addListener(SbwGemCompat::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(SbwGemCompat::onClientTick);
    }

    /** Last chance before the client starts ticking the joined level. */
    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (vehicleVisualizersRegistered) {
            return;
        }
        ColtanDebug.log(ColtanDebug.Cat.BOOT, "LoggingIn — visualizers still missing, registering now");
        rebuild(false);
        tryRegisterVisualizers();
    }

    private static void onResourceReload(EndClientResourceReloadEvent event) {
        sampledWithLevel = false;
        SbwParticleBridge.shutdown();
        ColtanDebug.log(ColtanDebug.Cat.BOOT, "resource reload — catalogs will rebuild with level");
        if (Minecraft.getInstance().level != null) {
            rebuild(true);
            sampledWithLevel = true;
            tryRegisterVisualizers();
            SbwParticleBridge.ensureStarted(Minecraft.getInstance().level);
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SbwParticleBridge.shutdown();
            return;
        }
        if (!sampledWithLevel) {
            ColtanDebug.log(ColtanDebug.Cat.BOOT, "first client tick with level — re-sampling catalogs");
            rebuild(true);
            sampledWithLevel = true;
            tryRegisterVisualizers();
        }
        SbwParticleBridge.ensureStarted(mc.level);
    }

    private static void rebuild(boolean reloadModels) {
        ColtanDebug.log(ColtanDebug.Cat.BOOT, "rebuild(reloadModels=%s)", reloadModels);
        VehicleBridgeCache.rebuild();
        ArmorBridgeCache.rebuild();
        GunBridgeCache.rebuild();
        BlockBridgeCache.rebuild();
        ProjectileBridgeCache.rebuild();
        MunitionBridgeCache.rebuild();
        if (reloadModels) {
            VehicleBridgeCache.reloadModels();
            SbwArmorGemCompat.reloadModels();
            SbwGunGemCompat.reloadModels();
            SbwBlockGemCompat.reloadModels();
            SbwProjectileGemCompat.reloadModels();
            SbwMunitionGemCompat.reloadModels();
            if (TaczGunGemCompat.active()) {
                TaczGunGemCompat.reloadModels();
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void tryRegisterVisualizers() {
        if (!vehicleVisualizersRegistered && !VehicleBridgeCache.profiles().isEmpty()) {
            int count = 0;
            for (VehicleBridgeProfile profile : VehicleBridgeCache.profiles()) {
                SimpleEntityVisualizer.builder((EntityType) profile.entityType())
                        .factory((ctx, entity, partialTick) ->
                                new SbwVehicleGemVisual(ctx, (VehicleEntity) entity, partialTick))
                        .skipVanillaRender(entity -> true)
                        .apply();
                count++;
            }
            vehicleVisualizersRegistered = true;
            Coltan.LOGGER.info("Registered GemRender parts visuals for {} SBW vehicle type(s)", count);
            ColtanDebug.log(ColtanDebug.Cat.VEHICLE, "Flywheel visualizers registered for %d type(s)", count);
        } else if (!vehicleVisualizersRegistered) {
            ColtanDebug.failOnce("vehicle-viz-empty",
                    "no vehicle profiles — Flywheel visualizers not registered");
        }
        SbwBlockGemCompat.tryRegisterVisualizers();
        SbwProjectileGemCompat.tryRegisterVisualizers();
    }
}
