package com.neoalive.coltan.client.compat.particle;

import com.neoalive.coltan.debug.ColtanDebug;
import com.wf.gemrender.particle.ParticleEmitter;
import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * Bullet decals on GemRender {@code DECAL} particles, one emitter per 16^3 section that has any.
 *
 * <p>GemRender decals cannot be removed one by one, and SBW's own decal disappears when its block
 * changes. So each section region remembers which blocks carry decals, and a change to one of them
 * drops the whole region; the next decal in that section starts a fresh one. Regions are LRU-capped:
 * the oldest region (and its decals) goes when the cap is hit. Client thread only.
 */
public final class SbwDecalRegions {
    static final int MAX_REGIONS = 64;
    static final int REGION_CAP = 128;

    private static final Long2ObjectLinkedOpenHashMap<Region> REGIONS = new Long2ObjectLinkedOpenHashMap<>();

    private SbwDecalRegions() {
    }

    /** @return false if GemRender refused the decal; the caller leaves it to SBW's own particle. */
    static boolean spawn(Level level, BlockPos hitBlock, double x, double y, double z,
            float nx, float ny, float nz, int look) {
        long key = SectionPos.asLong(hitBlock);
        Region region = REGIONS.getAndMoveToLast(key);
        if (region == null || region.level != level) {
            if (region != null) {
                remove(key);
            }
            while (REGIONS.size() >= MAX_REGIONS) {
                remove(REGIONS.firstLongKey());
            }
            region = new Region(level, key);
            REGIONS.put(key, region);
            VisualizationHelper.queueAdd(region);
            ColtanDebug.log(ColtanDebug.Cat.PARTICLE, "decal region %s queued (regions=%d)",
                    SectionPos.of(hitBlock), REGIONS.size());
        }
        try {
            region.emitter.spawnDecal(x, y, z, nx, ny, nz, SbwParticleStyles.DECAL_LIFE_SEC, 1.0f,
                    (float) (Math.random() * Math.PI * 2.0), look);
        } catch (RuntimeException e) {
            ColtanDebug.failOnce("decal-spawn", "GemRender refused a decal at %.2f %.2f %.2f n=(%.0f %.0f %.0f): %s",
                    x, y, z, nx, ny, nz, e.toString());
            return false;
        }
        region.decalBlocks.add(hitBlock.asLong());
        ColtanDebug.once(ColtanDebug.Cat.PARTICLE, "decal-first",
                "first decal: pos=%.2f %.2f %.2f normal=(%.0f %.0f %.0f) look=%d region=%s",
                x, y, z, nx, ny, nz, look, SectionPos.of(hitBlock));
        return true;
    }

    /** From the ClientLevel block-update hook: purge the region if this block carries decals. */
    public static void blockChanged(BlockPos pos) {
        if (REGIONS.isEmpty()) {
            return;
        }
        long key = SectionPos.asLong(pos);
        Region region = REGIONS.get(key);
        if (region != null && region.decalBlocks.contains(pos.asLong())) {
            remove(key);
            ColtanDebug.count(ColtanDebug.Cat.PARTICLE, "decal-purge", 5000L, "decal regions purged total=%d");
        }
    }

    static void clear() {
        while (!REGIONS.isEmpty()) {
            remove(REGIONS.firstLongKey());
        }
    }

    private static void remove(long key) {
        Region region = REGIONS.remove(key);
        if (region != null) {
            VisualizationHelper.queueRemove(region);
            region.emitter.close();
        }
    }

    static final class Region implements Effect {
        final Level level;
        final ParticleEmitter emitter;
        final LongOpenHashSet decalBlocks = new LongOpenHashSet();

        Region(Level level, long sectionKey) {
            this.level = level;
            // Section centre as origin: every decal in it is within a few blocks, far under DECAL_REACH,
            // so float offsets stay exact (no z-fighting from precision loss).
            this.emitter = ParticleEmitter.create(SbwParticleStyles.decal(), REGION_CAP,
                    SectionPos.sectionToBlockCoord(SectionPos.x(sectionKey), 8),
                    SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey), 8),
                    SectionPos.sectionToBlockCoord(SectionPos.z(sectionKey), 8));
        }

        @Override
        public LevelAccessor level() {
            return level;
        }

        @Override
        public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
            return new SbwDecalRegionVisual(ctx, this, partialTick);
        }
    }
}
