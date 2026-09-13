package com.neoalive.coltan.client.compat.particle;

import com.wf.gemrender.particle.ParticleEmitter;
import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * Level-scoped GemRender particle emitters for diverted SBW soft-sheet effects.
 * Emitters outlive Flywheel visual rebuilds; pools live on {@link SbwParticleEffectVisual}.
 */
public final class SbwParticleEffect implements Effect {
    static final int FLARE_CAP = 4096;
    static final int CLOUD_CAP = 2048;
    static final int CANNON_CAP = 512;
    static final int SMOKE_CAP = 1024;

    private final Level level;
    private final BlockPos origin;
    private final ParticleEmitter flare;
    private final ParticleEmitter cloud;
    private final ParticleEmitter cannon;
    private final ParticleEmitter smoke;

    public SbwParticleEffect(Level level) {
        this.level = level;
        // Wide origin so spawn deltas stay reasonable; absolute world coords still work via write().
        this.origin = BlockPos.ZERO;
        double ox = origin.getX() + 0.5;
        double oy = origin.getY() + 0.5;
        double oz = origin.getZ() + 0.5;
        this.flare = ParticleEmitter.create(SbwParticleStyles.flare(), FLARE_CAP, ox, oy, oz);
        this.cloud = ParticleEmitter.create(SbwParticleStyles.cloud(), CLOUD_CAP, ox, oy, oz);
        this.cannon = ParticleEmitter.create(SbwParticleStyles.cannon(), CANNON_CAP, ox, oy, oz);
        this.smoke = ParticleEmitter.create(SbwParticleStyles.smoke(), SMOKE_CAP, ox, oy, oz);
    }

    public ParticleEmitter flare() {
        return flare;
    }

    public ParticleEmitter cloud() {
        return cloud;
    }

    public ParticleEmitter cannon() {
        return cannon;
    }

    public ParticleEmitter smoke() {
        return smoke;
    }

    void closeEmitters() {
        flare.close();
        cloud.close();
        cannon.close();
        smoke.close();
    }

    @Override
    public LevelAccessor level() {
        return level;
    }

    @Override
    public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
        return new SbwParticleEffectVisual(ctx, this, partialTick);
    }
}
