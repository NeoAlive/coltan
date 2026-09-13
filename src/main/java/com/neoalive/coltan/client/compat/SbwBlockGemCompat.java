package com.neoalive.coltan.client.compat;

import com.atsuishio.superbwarfare.init.ModBlockEntities;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.BlockBridgeCache;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraftforge.fml.ModList;

/** Registers Flywheel visuals for SBW Bedrock BER blocks. */
public final class SbwBlockGemCompat {
    private static boolean visualizersRegistered;

    private SbwBlockGemCompat() {
    }

    public static boolean active() {
        return ModList.get().isLoaded("gemrender") && ModList.get().isLoaded("superbwarfare");
    }

    public static void init() {
        if (!active()) {
            return;
        }
        BlockBridgeCache.rebuild();
        Coltan.LOGGER.info("GemRender block bridge catalog ready for {} SBW block(s)",
                BlockBridgeCache.pieces().size());
    }

    public static void reloadModels() {
        if (!active()) {
            return;
        }
        BlockBridgeCache.reloadModels();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void tryRegisterVisualizers() {
        if (!active() || visualizersRegistered || BlockBridgeCache.byType().isEmpty()) {
            return;
        }

        SimpleBlockEntityVisualizer.builder(ModBlockEntities.CONTAINER.get())
                .factory(SbwBlockGemVisual::new)
                .skipVanillaRender(be -> true)
                .apply();
        SimpleBlockEntityVisualizer.builder(ModBlockEntities.SMALL_CONTAINER.get())
                .factory(SbwBlockGemVisual::new)
                .skipVanillaRender(be -> true)
                .apply();
        SimpleBlockEntityVisualizer.builder(ModBlockEntities.LUCKY_CONTAINER.get())
                .factory(SbwBlockGemVisual::new)
                .skipVanillaRender(be -> true)
                .apply();
        SimpleBlockEntityVisualizer.builder(ModBlockEntities.FUMO_25.get())
                .factory(SbwBlockGemVisual::new)
                .skipVanillaRender(be -> true)
                .apply();
        SimpleBlockEntityVisualizer.builder(ModBlockEntities.VEHICLE_ASSEMBLING_TABLE.get())
                .factory(SbwBlockGemVisual::new)
                .skipVanillaRender(be -> true)
                .apply();
        SimpleBlockEntityVisualizer.builder(ModBlockEntities.BLUEPRINT_RESEARCH_TABLE.get())
                .factory(SbwBlockGemVisual::new)
                .skipVanillaRender(be -> true)
                .apply();

        visualizersRegistered = true;
        Coltan.LOGGER.info("Registered GemRender visuals for {} SBW block entity type(s)",
                BlockBridgeCache.byType().size());
    }
}
