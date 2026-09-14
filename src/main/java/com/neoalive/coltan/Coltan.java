package com.neoalive.coltan;

import com.mojang.logging.LogUtils;
import com.neoalive.coltan.debug.ColtanDebug;
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
            ColtanDebug.forceRefresh();
            boolean gem = ModList.get().isLoaded("gemrender");
            boolean sbw = ModList.get().isLoaded("superbwarfare");
            ColtanDebug.log(ColtanDebug.Cat.BOOT,
                    "client setup; debug=%s gemrender=%s superbwarfare=%s",
                    ColtanDebug.describe(), gem, sbw);
            if (gem && sbw) {
                try {
                    Class.forName("com.neoalive.coltan.client.compat.SbwGemCompat")
                            .getMethod("init", FMLClientSetupEvent.class)
                            .invoke(null, event);
                    LOGGER.info("GemRender × Superb Warfare bridge active");
                    ColtanDebug.log(ColtanDebug.Cat.BOOT, "SbwGemCompat.init OK");
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to start Coltan SBW/GemRender compat", e);
                }
            } else {
                LOGGER.info("GemRender/Superb Warfare bridge inactive (missing soft dependency)");
                ColtanDebug.once(ColtanDebug.Cat.FAIL, "soft-deps-sbw",
                        "SBW bridge inactive — gemrender=%s superbwarfare=%s", gem, sbw);
            }
        }
    }
}
