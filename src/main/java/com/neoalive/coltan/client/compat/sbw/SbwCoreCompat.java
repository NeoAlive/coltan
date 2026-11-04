package com.neoalive.coltan.client.compat.sbw;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.SbwCore;
import com.neoalive.coltan.client.compat.sbw.light.SbwLightRelight;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * SBW compat that needs only Superb Warfare: spark light fixes and baked armor. Loaded by
 * reflection from {@link Coltan} when SBW is present, with or without GemRender.
 */
public final class SbwCoreCompat {
    private SbwCoreCompat() {}

    public static void init(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(SbwLightRelight::onLevelUnload);
        SbwCore.active = true;
        Coltan.LOGGER.info("Coltan SBW core compat active (spark lights, baked armor)");
    }
}
