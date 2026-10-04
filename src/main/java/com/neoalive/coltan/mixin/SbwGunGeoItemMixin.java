package com.neoalive.coltan.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.atsuishio.superbwarfare.client.PoseTool;
import com.atsuishio.superbwarfare.item.gun.GunGeoItem;
import com.neoalive.coltan.client.compat.SbwGunGemCompat;
import com.neoalive.coltan.client.compat.bridge.GunBridgeCache;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Claims SBW {@link GunGeoItem}s at {@code initializeClient} (runs from the Item
 * constructor, before registry keys exist). {@link com.neoalive.coltan.client.compat.bridge.GunBridgeCache#owns}
 * claims every geo gun pre-rebuild; class→path mapping fills item ids that early.
 */
@Mixin(value = GunGeoItem.class, remap = false)
public abstract class SbwGunGeoItemMixin {
    @Inject(method = "initializeClient", at = @At("HEAD"), cancellable = true)
    private void coltan$claimGun(Consumer<IClientItemExtensions> consumer, CallbackInfo ci) {
        if (!SbwGunGemCompat.active()) {
            return;
        }
        Item self = (Item) (Object) this;
        if (!SbwGunGemCompat.owns(self)) {
            return;
        }
        consumer.accept(new IClientItemExtensions() {
            // Forge asks on every item render; the answer only changes when the catalog rebuilds.
            private int rendererGeneration = -1;
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                int generation = GunBridgeCache.generation();
                if (generation != rendererGeneration) {
                    BlockEntityWithoutLevelRenderer resolved = SbwGunGemCompat.rendererFor(self);
                    // A null while still claimed is the pre-rebuild race: retry next call.
                    if (resolved != null || !SbwGunGemCompat.owns(self)) {
                        renderer = resolved;
                        rendererGeneration = generation;
                    }
                    return resolved;
                }
                return renderer;
            }

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand,
                    ItemStack stack) {
                return PoseTool.pose(entity, hand, stack);
            }
        });
        com.neoalive.coltan.debug.ColtanDebug.once(
                com.neoalive.coltan.debug.ColtanDebug.Cat.CLAIM,
                "gun-claim-" + self.getClass().getName(),
                "claimed gun client extensions for %s", self.getClass().getSimpleName());
        ci.cancel();
    }
}
