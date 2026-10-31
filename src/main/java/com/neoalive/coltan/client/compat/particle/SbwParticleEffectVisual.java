package com.neoalive.coltan.client.compat.particle;

import com.wf.gemrender.particle.GemRenderParticleTypes;
import com.wf.gemrender.particle.ParticleModels;
import com.wf.gemrender.particle.ParticlePool;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import net.minecraft.world.level.Level;

/**
 * Flywheel visual: one ParticlePool per SBW particle family.
 *
 * <p>No {@code translucent} model here: one OIT draw in a frame forces GemRender's three OIT passes on
 * every order-independent draw and drops the reduced-resolution absorbance path. Smoke uses
 * {@code absorbance} (extinction only); flares / exhaust use {@code glowing} (absorbance plus emission:
 * plain absorbance has none, so a hot exhaust drawn with it reads as a faint brown smudge). Dust stays
 * {@code cutout} (early-Z), muzzle flashes {@code additive}.
 */
public final class SbwParticleEffectVisual extends AbstractVisual
        implements EffectVisual<SbwParticleEffect> {
    private final ParticlePool flarePool;
    private final ParticlePool cloudPool;
    private final ParticlePool cannonPool;
    private final ParticlePool smokePool;
    private final ParticlePool fireStarPool;
    private final ParticlePool whiteStarPool;
    private final ParticlePool risingSmokePool;
    private final ParticlePool debrisPool;

    public SbwParticleEffectVisual(VisualizationContext ctx, SbwParticleEffect effect, float partialTick) {
        super(ctx, (Level) effect.level(), partialTick);
        this.flarePool = new ParticlePool(ctx, effect.flare(), GemRenderParticleTypes.BILLBOARD,
                flareModel());
        this.cloudPool = new ParticlePool(ctx, effect.cloud(), GemRenderParticleTypes.BILLBOARD,
                ParticleModels.cutout(SbwParticleStyles.CLOUD_TEX));
        this.cannonPool = new ParticlePool(ctx, effect.cannon(), GemRenderParticleTypes.BILLBOARD,
                ParticleModels.additive(SbwParticleStyles.CANNON_TEX));
        this.smokePool = new ParticlePool(ctx, effect.smoke(), GemRenderParticleTypes.BILLBOARD,
                smokeModel(SbwParticleStyles.SMOKE_TEX));
        this.fireStarPool = new ParticlePool(ctx, effect.fireStar(), GemRenderParticleTypes.STREAK,
                ParticleModels.additive(SbwParticleStyles.FIRE_STAR_TEX));
        this.whiteStarPool = new ParticlePool(ctx, effect.whiteStar(), GemRenderParticleTypes.STREAK,
                ParticleModels.additive(SbwParticleStyles.WHITE_STAR_TEX));
        this.risingSmokePool = new ParticlePool(ctx, effect.risingSmoke(), GemRenderParticleTypes.BILLBOARD,
                smokeModel(SbwParticleStyles.RISING_SMOKE_TEX));
        this.debrisPool = new ParticlePool(ctx, effect.debris(), GemRenderParticleTypes.BILLBOARD,
                ParticleModels.additive(SbwParticleStyles.FIRE_STAR_TEX));
    }

    /** {@link SbwParticleStyles#FLARE_MODE}; note {@code translucent} drops every frame back to full OIT. */
    private static Model flareModel() {
        return switch (SbwParticleStyles.FLARE_MODE) {
            case "absorbance" -> ParticleModels.absorbance(SbwParticleStyles.FLARE_TEX);
            case "translucent" -> ParticleModels.translucent(SbwParticleStyles.FLARE_TEX);
            case "additive" -> ParticleModels.additive(SbwParticleStyles.FLARE_TEX);
            default -> ParticleModels.glowing(SbwParticleStyles.FLARE_TEX);
        };
    }

    /** {@link SbwParticleStyles#SMOKE_MODE} when smoke is diverted (absorbance unless translucent asked). */
    private static Model smokeModel(net.minecraft.resources.ResourceLocation texture) {
        return "translucent".equals(SbwParticleStyles.SMOKE_MODE)
                ? ParticleModels.translucent(texture)
                : ParticleModels.absorbance(texture);
    }

    @Override
    protected void _delete() {
        flarePool.delete();
        cloudPool.delete();
        cannonPool.delete();
        smokePool.delete();
        fireStarPool.delete();
        whiteStarPool.delete();
        risingSmokePool.delete();
        debrisPool.delete();
    }
}
