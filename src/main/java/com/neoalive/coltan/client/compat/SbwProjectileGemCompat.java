package com.neoalive.coltan.client.compat;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.ProjectileBridgeCache;
import com.neoalive.coltan.client.compat.bridge.ProjectileBridgeProfile;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;

/** Registers Flywheel visuals for SBW Bedrock projectiles. */
public final class SbwProjectileGemCompat {
    private static boolean visualizersRegistered;

    private SbwProjectileGemCompat() {
    }

    public static boolean active() {
        return ModList.get().isLoaded("gemrender") && ModList.get().isLoaded("superbwarfare");
    }

    public static void init() {
        if (!active()) {
            return;
        }
        ProjectileBridgeCache.rebuild();
        MinecraftForge.EVENT_BUS.addListener(SbwProjectileFlare::onRenderLevel);
        Coltan.LOGGER.info("GemRender projectile bridge ready for {} SBW type(s)",
                ProjectileBridgeCache.profiles().size());
    }

    public static void reloadModels() {
        if (!active()) {
            return;
        }
        ProjectileBridgeCache.reloadModels();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void tryRegisterVisualizers() {
        if (!active() || visualizersRegistered || ProjectileBridgeCache.profiles().isEmpty()) {
            return;
        }

        int count = 0;
        for (ProjectileBridgeProfile profile : ProjectileBridgeCache.profiles()) {
            ProjectileBridgeProfile captured = profile;
            SimpleEntityVisualizer.builder((EntityType) profile.entityType())
                    .factory((ctx, entity, partialTick) ->
                            new SbwProjectileGemVisual(ctx, entity, partialTick, captured))
                    .skipVanillaRender(entity -> true)
                    .apply();
            count++;
        }
        visualizersRegistered = true;
        Coltan.LOGGER.info("Registered GemRender visuals for {} SBW projectile type(s)", count);
    }
}
