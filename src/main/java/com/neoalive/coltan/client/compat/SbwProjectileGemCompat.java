package com.neoalive.coltan.client.compat;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.ProjectileBridgeCache;
import com.neoalive.coltan.client.compat.bridge.ProjectileBridgeProfile;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.MinecraftForge;

/** Registers Flywheel visuals for SBW Bedrock projectiles. */
public final class SbwProjectileGemCompat {
    private static boolean visualizersRegistered;

    private SbwProjectileGemCompat() {
    }

    public static boolean active() {
        return SbwGemPresence.ACTIVE;
    }

    public static void init() {
        if (!active()) {
            return;
        }
        // Catalog is built once by SbwGemCompat.rebuild(false) right after the feature inits.
        MinecraftForge.EVENT_BUS.addListener(SbwProjectileFlare::onRenderLevel);
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
            EntityType<?> type = profile.entityType();
            Coltan.LOGGER.info("GemRender projectile bridge: claiming entity type {} (registry name {}) for {}",
                    type, EntityType.getKey(type), profile.entityId());
            SimpleEntityVisualizer.builder((EntityType) type)
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
