package com.neoalive.coltan.client.compat.particle;

import com.neoalive.coltan.debug.ColtanDebug;
import com.wf.gemrender.particle.GemRenderParticleTypes;
import com.wf.gemrender.particle.ParticleModels;
import com.wf.gemrender.particle.ParticlePool;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import net.minecraft.world.level.Level;

/** Flywheel visual for one {@link SbwDecalRegions.Region}: a single DECAL pool. */
final class SbwDecalRegionVisual extends AbstractVisual implements EffectVisual<SbwDecalRegions.Region> {
    private final ParticlePool pool;

    SbwDecalRegionVisual(VisualizationContext ctx, SbwDecalRegions.Region region, float partialTick) {
        super(ctx, (Level) region.level(), partialTick);
        this.pool = new ParticlePool(ctx, region.emitter, GemRenderParticleTypes.DECAL,
                ParticleModels.decal(SbwParticleStyles.DECAL_TEX));
        ColtanDebug.count(ColtanDebug.Cat.PARTICLE, "decal-visual", 5000L, "decal region visuals built total=%d");
    }

    @Override
    protected void _delete() {
        pool.delete();
    }
}
