package com.neoalive.coltan.client.compat;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.SemUnitCatalog;
import com.neoalive.coltan.client.compat.bridge.SemUnitPartMesh;
import com.neoalive.coltan.client.compat.bridge.SemUnitRigidProfile;
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
 * Soft-dep orchestrator: GemRender × SimpleEnemyMod unit visuals.
 *
 * <p>Flywheel visualizers must register before login entity packets, matching {@link SbwGemCompat}.
 */
public final class SemGemCompat {
    private static boolean visualizersRegistered;
    private static boolean sampledWithLevel;

    private SemGemCompat() {
    }

    /**
     * Deliberately hardcoded off: {@code active()} used to gate this on GemRender+SEM both being
     * loaded, but the whole bridge is disabled for now regardless of that, so the check is folded
     * in here rather than left to look like a live condition.
     */
    public static boolean active() {
        return false;
    }

    /**
     * Deliberately disabled via {@link #active()}: {@code skipVanillaRender(true)} below bypasses
     * SEM's own {@code MobRenderer}/{@code LivingEntityRenderer} entirely, which means SEM's native
     * armor layer ({@code UniversalArmorLayer}) and held-gun layer ({@code GunLayerRenderer}) never
     * run either — there is no Flywheel-side replacement for either one anymore (armor support was
     * removed as not worth the effort; the gun overlay never reliably matched SEM's own rendering).
     * Letting SEM units fall through to full vanilla rendering is the trade the user chose over
     * continuing to chase that gap — GPU-instanced bodies traded back for correct armor/weapons via
     * SEM's own renderer. The rigid-parts infrastructure below is left in place (not deleted) in
     * case bridging is revisited later.
     */
    public static void init(FMLClientSetupEvent event) {
        if (!active()) {
            return;
        }
        event.enqueueWork(() -> {
            SemUnitCatalog.rebuild();
            tryRegisterVisualizers();
            Coltan.LOGGER.info("GemRender SEM unit bridge ready for {} type(s)",
                    SemUnitCatalog.profiles().size());
            ColtanDebug.log(ColtanDebug.Cat.BOOT,
                    "SEM early catalog+visualizers (pre-world); units=%d",
                    SemUnitCatalog.profiles().size());
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
        SemUnitCatalog.rebuild();
        tryRegisterVisualizers();
    }

    private static void onResourceReload(EndClientResourceReloadEvent event) {
        sampledWithLevel = false;
        SemUnitPartMesh.clearTextureCache();
        ColtanDebug.log(ColtanDebug.Cat.BOOT, "SEM resource reload — catalog/textures will rebuild with level");
        if (Minecraft.getInstance().level != null) {
            SemUnitCatalog.rebuild();
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
            ColtanDebug.log(ColtanDebug.Cat.BOOT, "SEM first client tick with level — re-sampling catalog");
            SemUnitCatalog.rebuild();
            sampledWithLevel = true;
            tryRegisterVisualizers();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void tryRegisterVisualizers() {
        if (!active() || visualizersRegistered || SemUnitCatalog.profiles().isEmpty()) {
            if (!visualizersRegistered && SemUnitCatalog.profiles().isEmpty()) {
                ColtanDebug.failOnce("sem-unit-viz-empty",
                        "no SEM unit profiles — Flywheel visualizers not registered");
            }
            return;
        }

        int count = 0;
        for (SemUnitRigidProfile profile : SemUnitCatalog.profiles()) {
            SemUnitRigidProfile captured = profile;
            SimpleEntityVisualizer.builder((EntityType) profile.entityType())
                    .factory((ctx, entity, partialTick) ->
                            new SemUnitRigidVisual(ctx, entity, partialTick, captured))
                    .skipVanillaRender(entity -> true)
                    .apply();
            count++;
        }
        visualizersRegistered = true;
        Coltan.LOGGER.info("Registered GemRender visuals for {} SEM unit type(s)", count);
        ColtanDebug.log(ColtanDebug.Cat.BOOT, "SEM Flywheel visualizers registered for %d type(s)", count);
    }
}
