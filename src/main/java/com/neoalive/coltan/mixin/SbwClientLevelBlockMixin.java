package com.neoalive.coltan.mixin;

import com.neoalive.coltan.client.compat.sbw.SbwGemPresence;
import com.neoalive.coltan.client.compat.sbw.particle.SbwDecalRegions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** remap = false and both names: this build writes no SRG refmap. Production name is m_7260_. */
@Mixin(value = ClientLevel.class, remap = false)
public abstract class SbwClientLevelBlockMixin {
    @Inject(
            method = {
                "sendBlockUpdated(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V",
                "m_7260_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V"
            },
            at = @At("HEAD"),
            remap = false)
    private void coltan$purgeDecals(
            BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        // Presence first: without GemRender, SbwDecalRegions (GemRender types) must never load.
        if (oldState != newState && SbwGemPresence.ACTIVE) {
            SbwDecalRegions.blockChanged(pos);
        }
    }
}
