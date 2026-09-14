package com.neoalive.coltan.client.compat;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * SEM-held TACZ → GemRender was withdrawn. Owning placement (bones / ItemInHand / BEWLR shell)
 * fought Gem vs TACZ Bedrock space and never stabilized.
 *
 * <p>Stock SEM {@code GunLayerRenderer} + TACZ BEWLR draw held guns again. A future Gem claim
 * would have to swap only the mesh inside TACZ's already-posed draw — Coltan still cannot skip
 * a Gem↔Java space fix entirely, but it must not re-parent SEM arms or reimplement ItemInHand.
 *
 * <p>{@link #canDraw} is always false so SEWV does not cancel GunLayer for Coltan.
 */
public final class TaczGunDraw {
    private TaczGunDraw() {
    }

    /** Always false — SEM-held TACZ placement claim is inactive. */
    public static boolean canDraw(ItemStack stack) {
        return false;
    }

    /** No-op; kept so older SEWV reflective binds do not crash if a stale layer remains. */
    public static boolean trySubmit(@Nullable LivingEntity entity, ItemStack stack, PoseStack pose,
            int packedLight) {
        return false;
    }

    /** @deprecated see {@link #trySubmit(LivingEntity, ItemStack, PoseStack, int)} */
    @Deprecated
    public static boolean trySubmit(@Nullable LivingEntity entity, ItemStack stack, PoseStack pose,
            int packedLight, float partialTick) {
        return false;
    }
}
