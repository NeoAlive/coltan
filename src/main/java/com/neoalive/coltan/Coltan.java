package com.neoalive.coltan;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Coltan.MOD_ID)
public final class Coltan {
    public static final String MOD_ID = "coltan";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Coltan(FMLJavaModLoadingContext context) {
        // Mod identity only; client compat boots from ClientSetup.
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientSetup {
        private ClientSetup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            if (!ModList.get().isLoaded("gemrender") || !ModList.get().isLoaded("superbwarfare")) {
                LOGGER.info("GemRender/Superb Warfare bridge inactive (missing soft dependency)");
                return;
            }
            try {
                Class.forName("com.neoalive.coltan.client.compat.SbwGemCompat")
                        .getMethod("init", FMLClientSetupEvent.class)
                        .invoke(null, event);
                LOGGER.info("GemRender × Superb Warfare bridge active");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to start Coltan SBW/GemRender compat", e);
            }
        }
    }
}
