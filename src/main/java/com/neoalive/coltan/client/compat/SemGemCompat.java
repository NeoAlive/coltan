package com.neoalive.coltan.client.compat;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.SemUnitBridgeCache;
import com.neoalive.coltan.client.compat.bridge.SemUnitBridgeProfile;
import com.neoalive.coltan.debug.ColtanDebug;
import dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Soft-dep orchestrator: GemRender × SimpleEnemyMod unit visuals.
 *
 * <p>Flywheel visualizers must register before login entity packets, matching {@link SbwGemCompat}.
 */
public final class SemGemCompat {
    private static boolean visualizersRegistered;
    private static boolean sampledWithLevel;

    private SemGemCompat() {
    }

    public static boolean active() {
        return ModList.get().isLoaded("gemrender") && ModList.get().isLoaded("simpleenemymod");
    }

    public static void init(FMLClientSetupEvent event) {
        if (!active()) {
            return;
        }
        event.enqueueWork(() -> {
            SemUnitBridgeCache.rebuild();
            tryRegisterVisualizers();
            Coltan.LOGGER.info("GemRender SEM unit bridge ready for {} type(s)",
                    SemUnitBridgeCache.profiles().size());
            ColtanDebug.log(ColtanDebug.Cat.BOOT,
                    "SEM early catalog+visualizers (pre-world); units=%d",
                    SemUnitBridgeCache.profiles().size());
        });
        MinecraftForge.EVENT_BUS.addListener(SemUnitGunOverlay::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(SemGemCompat::onResourceReload);
        MinecraftForge.EVENT_BUS.addListener(SemGemCompat::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(SemGemCompat::onClientTick);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (visualizersRegistered) {
            return;
        }
        ColtanDebug.log(ColtanDebug.Cat.BOOT, "SEM LoggingIn — visualizers still missing, registering now");
        rebuild(false);
        tryRegisterVisualizers();
    }

    private static void onResourceReload(EndClientResourceReloadEvent event) {
        sampledWithLevel = false;
        ColtanDebug.log(ColtanDebug.Cat.BOOT, "SEM resource reload — catalogs will rebuild with level");
        if (Minecraft.getInstance().level != null) {
            rebuild(true);
            sampledWithLevel = true;
            tryRegisterVisualizers();
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            return;
        }
        if (!sampledWithLevel) {
            ColtanDebug.log(ColtanDebug.Cat.BOOT, "SEM first client tick with level — re-sampling catalogs");
            rebuild(true);
            sampledWithLevel = true;
            tryRegisterVisualizers();
        }
    }

    private static void rebuild(boolean reloadModels) {
        SemUnitBridgeCache.rebuild();
        if (reloadModels) {
            SemUnitBridgeCache.reloadModels();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void tryRegisterVisualizers() {
        if (!active() || visualizersRegistered || SemUnitBridgeCache.profiles().isEmpty()) {
            if (!visualizersRegistered && SemUnitBridgeCache.profiles().isEmpty()) {
                ColtanDebug.failOnce("sem-unit-viz-empty",
                        "no SEM unit profiles — Flywheel visualizers not registered");
            }
            return;
        }

        int count = 0;
        for (SemUnitBridgeProfile profile : SemUnitBridgeCache.profiles()) {
            SemUnitBridgeProfile captured = profile;
            SimpleEntityVisualizer.builder((EntityType) profile.entityType())
                    .factory((ctx, entity, partialTick) ->
                            new SemUnitGemVisual(ctx, entity, partialTick, captured))
                    .skipVanillaRender(entity -> true)
                    .apply();
            count++;
        }
        visualizersRegistered = true;
        Coltan.LOGGER.info("Registered GemRender visuals for {} SEM unit type(s)", count);
        ColtanDebug.log(ColtanDebug.Cat.BOOT, "SEM Flywheel visualizers registered for %d type(s)", count);
    }
}
