package com.neoalive.coltan.client.compat;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.TaczGunBridgeCache;
import com.neoalive.coltan.client.compat.bridge.TaczVisibilityClip;
import com.neoalive.coltan.debug.ColtanDebug;
import dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;

/**
 * Soft-dep gate for TACZ + GemRender. SEM-held placement claim is withdrawn — stock SEM
 * GunLayer + TACZ BEWLR draw unit guns. Cache/reload hooks remain for a future mesh-only swap.
 */
public final class TaczGunGemCompat {
    private static boolean reloadHooked;

    private TaczGunGemCompat() {
    }

    public static boolean active() {
        return ModList.get().isLoaded("gemrender") && ModList.get().isLoaded("tacz");
    }

    public static void init() {
        if (!active()) {
            return;
        }
        TaczGunBridgeCache.init();
        if (!reloadHooked) {
            MinecraftForge.EVENT_BUS.addListener(TaczGunGemCompat::onResourceReload);
            reloadHooked = true;
        }
        Coltan.LOGGER.info("Coltan TACZ+GemRender soft-dep ready (SEM held placement withdrawn)");
        ColtanDebug.log(ColtanDebug.Cat.GUN, "TaczGunGemCompat ready (held claim inactive)");
    }

    private static void onResourceReload(EndClientResourceReloadEvent event) {
        reloadModels();
    }

    public static void reloadModels() {
        if (!active()) {
            return;
        }
        TaczGunBridgeCache.reloadModels();
        TaczVisibilityClip.clear();
    }
}
