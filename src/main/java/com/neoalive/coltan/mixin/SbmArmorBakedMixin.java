package com.neoalive.coltan.mixin;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.renderer.GeoArmorRendererV2;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BedrockArmorInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.TreeArmorModelInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.neoalive.coltan.ColtanConfig;
import com.neoalive.coltan.client.compat.SbwCore;
import com.neoalive.coltan.client.compat.sbw.armor.BakedSbmArmor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Swaps only the mesh inside SBM's already posed armor draw: renderArmorToBuffer keeps its push,
 * baby scaling, render type and texture (including tacz_sewv's crew skin argument swap), and the
 * per-frame tree instance draw becomes the baked twin's. Exact class only, since subclasses such as
 * the goggles renderer override this method with their own passes.
 */
@Mixin(value = GeoArmorRendererV2.class, remap = false)
public abstract class SbmArmorBakedMixin {
    @Shadow protected LivingEntity livingEntity;
    @Shadow protected ItemStack itemStack;
    @Shadow protected EquipmentSlot equipmentSlot;
    @Shadow protected HumanoidModel<?> original;

    @Redirect(
            method = "renderArmorToBuffer",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/github/mcmodderanchor/simplebedrockmodel/v2/common/model/runtime/TreeArmorModelInstance;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
                            remap = false),
            remap = false)
    private void coltan$bakedMesh(
            TreeArmorModelInstance tree,
            PoseStack poseStack,
            VertexConsumer buffer,
            int light,
            int overlay,
            float r,
            float g,
            float b,
            float a) {
        GeoArmorRendererV2 self = (GeoArmorRendererV2) (Object) this;
        if (ColtanConfig.sbwBakedArmor
                && SbwCore.active
                && self.getClass() == GeoArmorRendererV2.class
                && livingEntity != null
                && itemStack != null
                && equipmentSlot != null
                && original != null) {
            BedrockArmorInstance baked = BakedSbmArmor.instance(self.getModel());
            if (baked != null) {
                baked.preparePose(livingEntity, itemStack, equipmentSlot, original);
                baked.renderToBuffer(poseStack, buffer, light, overlay, r, g, b, a);
                return;
            }
        }
        tree.renderToBuffer(poseStack, buffer, light, overlay, r, g, b, a);
    }
}
