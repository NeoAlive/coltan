package com.neoalive.coltan.client.compat.fx;

import com.neoalive.coltan.Coltan;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Loads {@code coltan:fx_particle} with the instanced attribute layout of {@link FxRenderer}. */
@Mod.EventBusSubscriber(
        modid = Coltan.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class FxShaders {
    private FxShaders() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            new ResourceLocation(Coltan.MOD_ID, "fx_particle"),
                            FxRenderer.FORMAT),
                    loaded -> FxRenderer.shader = loaded);
        } catch (IOException e) {
            FxRenderer.shader = null;
            Coltan.LOGGER.warn("Coltan particle shader failed to load; staying on vanilla", e);
        }
    }
}
