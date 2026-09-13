package com.neoalive.coltan.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.neoalive.coltan.client.compat.SbwArmorGemCompat;
import com.neoalive.coltan.client.compat.bridge.ArmorBridgeCache;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Claims SBW military armor {@code initializeClient} so worn pieces use {@link SbwArmorGemCompat}
 * instead of {@code GeoArmorRendererV2}. Handsome Goggles are not in the target list.
 */
@Mixin(targets = {
        "com.atsuishio.superbwarfare.item.armor.UsHelmetPasgtItem",
        "com.atsuishio.superbwarfare.item.armor.UsChestIotvItem",
        "com.atsuishio.superbwarfare.item.armor.RuHelmet6b47Item",
        "com.atsuishio.superbwarfare.item.armor.RuChest6b43Item",
        "com.atsuishio.superbwarfare.item.armor.GeHelmetM35Item"
}, remap = false)
public abstract class SbwArmorItemMixin {
    @Inject(method = "initializeClient", at = @At("HEAD"), cancellable = true)
    private void coltan$claimArmor(Consumer<IClientItemExtensions> consumer, CallbackInfo ci) {
        if (!SbwArmorGemCompat.active()) {
            return;
        }
        Item self = (Item) (Object) this;
        if (!ArmorBridgeCache.owns(self)) {
            return;
        }
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                    EquipmentSlot slot, HumanoidModel<?> original) {
                return SbwArmorGemCompat.prepare(entity, stack, slot);
            }
        });
        ci.cancel();
    }
}
