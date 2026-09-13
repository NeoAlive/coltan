package com.neoalive.coltan.client.compat.particle;

import com.wf.gemrender.particle.GemRenderParticleTypes;
import com.wf.gemrender.particle.ParticleModels;
import com.wf.gemrender.particle.ParticlePool;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import net.minecraft.world.level.Level;

/** Flywheel visual: one ParticlePool per SBW particle family. */
public final class SbwParticleEffectVisual extends AbstractVisual
        implements EffectVisual<SbwParticleEffect> {
    private final ParticlePool flarePool;
    private final ParticlePool cloudPool;
    private final ParticlePool cannonPool;
    private final ParticlePool smokePool;

    public SbwParticleEffectVisual(VisualizationContext ctx, SbwParticleEffect effect, float partialTick) {
        super(ctx, (Level) effect.level(), partialTick);
        this.flarePool = new ParticlePool(ctx, effect.flare(), GemRenderParticleTypes.BILLBOARD,
                ParticleModels.translucent(SbwParticleStyles.FLARE_TEX));
        this.cloudPool = new ParticlePool(ctx, effect.cloud(), GemRenderParticleTypes.BILLBOARD,
                ParticleModels.cutout(SbwParticleStyles.CLOUD_TEX));
        this.cannonPool = new ParticlePool(ctx, effect.cannon(), GemRenderParticleTypes.BILLBOARD,
                ParticleModels.additive(SbwParticleStyles.CANNON_TEX));
        this.smokePool = new ParticlePool(ctx, effect.smoke(), GemRenderParticleTypes.BILLBOARD,
                ParticleModels.translucent(SbwParticleStyles.SMOKE_TEX));
    }

    @Override
    protected void _delete() {
        flarePool.delete();
        cloudPool.delete();
        cannonPool.delete();
        smokePool.delete();
    }
}
