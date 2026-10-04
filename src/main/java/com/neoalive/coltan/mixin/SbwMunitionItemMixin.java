package com.neoalive.coltan.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.neoalive.coltan.client.compat.SbwMunitionGemCompat;
import com.neoalive.coltan.client.compat.bridge.MunitionBridgeCache;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Claims SBW munition {@code initializeClient} so HandGrenade / TM-62 / PTKM-1R use GemRender BEWLR.
 */
@Mixin(targets = {
        "com.atsuishio.superbwarfare.item.HandGrenade",
        "com.atsuishio.superbwarfare.item.projectile.Tm62Item",
        "com.atsuishio.superbwarfare.item.projectile.Ptkm1rItem"
}, remap = false)
public abstract class SbwMunitionItemMixin {
    @Inject(method = "initializeClient", at = @At("HEAD"), cancellable = true)
    private void coltan$claimMunition(Consumer<IClientItemExtensions> consumer, CallbackInfo ci) {
        if (!SbwMunitionGemCompat.active()) {
            return;
        }
        Item self = (Item) (Object) this;
        if (!SbwMunitionGemCompat.owns(self)) {
            return;
        }
        consumer.accept(new IClientItemExtensions() {
            // Forge asks on every item render; the answer only changes when the catalog rebuilds.
            private int rendererGeneration = -1;
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                int generation = MunitionBridgeCache.generation();
                if (generation != rendererGeneration) {
                    BlockEntityWithoutLevelRenderer resolved = SbwMunitionGemCompat.rendererFor(self);
                    // A null while still claimed is the pre-rebuild race: retry next call.
                    if (resolved != null || !SbwMunitionGemCompat.owns(self)) {
                        renderer = resolved;
                        rendererGeneration = generation;
                    }
                    return resolved;
                }
                return renderer;
            }
        });
        com.neoalive.coltan.debug.ColtanDebug.once(
                com.neoalive.coltan.debug.ColtanDebug.Cat.CLAIM,
                "munition-claim-" + self.getClass().getName(),
                "claimed munition client extensions for %s", self.getClass().getSimpleName());
        ci.cancel();
    }
}
