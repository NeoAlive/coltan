package com.neoalive.coltan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.neoalive.coltan.client.compat.SbwGemPresence;
import com.neoalive.coltan.client.compat.particle.SbwDecalRegions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Drops GemRender bullet-decal regions whose blocks change (SBW's own decal removes itself then).
 * Every client-side block change, server-sent or predicted, goes through {@code sendBlockUpdated}.
 *
 * <p>Both names listed with {@code remap = false}, as in {@link SbwClientLevelParticleMixin}: this
 * build writes no SRG refmap. SRG {@code m_7260_} is from srg_to_official_1.20.1.tsrg.
 */
@Mixin(value = ClientLevel.class, remap = false)
public abstract class SbwClientLevelBlockMixin {
    @Inject(
            method = {
                    "sendBlockUpdated(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V",
                    "m_7260_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V"
            },
            at = @At("HEAD"),
            remap = false
    )
    private void coltan$purgeDecals(BlockPos pos, BlockState oldState, BlockState newState, int flags,
            CallbackInfo ci) {
        // Presence first: without GemRender, SbwDecalRegions (GemRender types) must never load.
        if (oldState != newState && SbwGemPresence.ACTIVE) {
            SbwDecalRegions.blockChanged(pos);
        }
    }
}
