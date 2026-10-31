package com.neoalive.coltan.client.compat.particle;

import java.util.Arrays;

import com.neoalive.coltan.debug.ColtanDebug;
import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.particle.ParticleEmitter;
import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * Bullet decals on GemRender {@code DECAL} particles: a few large emitters, single-decal removal.
 *
 * <p>One emitter per 128^3-block region (GemRender's guidance: "one decal emitter per region near the
 * player"); offsets stay within 64 blocks of the origin, far inside {@code DECAL_REACH}, so float
 * precision never z-fights. The first design (one emitter per 16^3 section, 64 max) churned: a battle
 * spreads fire over hundreds of sections, so it created ~5 regions/s at <3 decals each, and LRU
 * eviction wiped whole regions' decals long before their 10 s life.
 *
 * <p>SBW removes a decal when its block changes. GemRender has no per-decal handle, but an emitter
 * writes slot {@code slotBase + cursor} and advances the cursor by one per accepted decal (decals are
 * never thinned or dropped), so this mirrors the cursor, records which block owns each slot, and
 * clears exactly those slots ({@link ParticleBuffer#clearSlot}). Client thread only.
 */
public final class SbwDecalRegions {
    static final int REGION_SHIFT = 7;
    static final int MAX_REGIONS = 16;
    static final int REGION_CAP = 512;
    private static final long EMPTY = Long.MIN_VALUE;

    private static final Long2ObjectLinkedOpenHashMap<Region> REGIONS = new Long2ObjectLinkedOpenHashMap<>();

    private SbwDecalRegions() {
    }

    private static long regionKey(BlockPos pos) {
        return BlockPos.asLong(pos.getX() >> REGION_SHIFT, pos.getY() >> REGION_SHIFT, pos.getZ() >> REGION_SHIFT);
    }

    /** @return false if GemRender refused the decal; the caller leaves it to SBW's own particle. */
    static boolean spawn(Level level, BlockPos hitBlock, double x, double y, double z,
            float nx, float ny, float nz, int look) {
        long key = regionKey(hitBlock);
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
            ColtanDebug.log(ColtanDebug.Cat.PARTICLE, "decal region %d,%d,%d queued (regions=%d)",
                    BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key), REGIONS.size());
        }
        try {
            region.emitter.spawnDecal(x, y, z, nx, ny, nz, SbwParticleStyles.DECAL_LIFE_SEC, 1.0f,
                    (float) (Math.random() * Math.PI * 2.0), look);
        } catch (RuntimeException e) {
            ColtanDebug.failOnce("decal-spawn", "GemRender refused a decal at %.2f %.2f %.2f n=(%.0f %.0f %.0f): %s",
                    x, y, z, nx, ny, nz, e.toString());
            return false;
        }
        region.accepted(hitBlock.asLong());
        ColtanDebug.once(ColtanDebug.Cat.PARTICLE, "decal-first",
                "first decal: pos=%.2f %.2f %.2f normal=(%.0f %.0f %.0f) look=%d", x, y, z, nx, ny, nz, look);
        return true;
    }

    /** From the ClientLevel block-update hook: clear the decals sitting on this block, only those. */
    public static void blockChanged(BlockPos pos) {
        if (REGIONS.isEmpty()) {
            return;
        }
        Region region = REGIONS.get(regionKey(pos));
        if (region != null) {
            int cleared = region.purge(pos.asLong());
            if (cleared > 0) {
                ColtanDebug.count(ColtanDebug.Cat.PARTICLE, "decal-purge", 5000L, "decal purges total=%d");
            }
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
        /** Block (packed) owning each slot, EMPTY if none. Stale after a decal expires: clearing a dead slot is harmless. */
        private final long[] owner = new long[REGION_CAP];
        /** Block -> slots it owns, so a block change without decals costs one map miss. */
        private final Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
        /** Mirror of the emitter's private cursor: +1 per accepted decal, same modulus. */
        private int cursor;

        Region(Level level, long key) {
            this.level = level;
            Arrays.fill(owner, EMPTY);
            int half = 1 << (REGION_SHIFT - 1);
            this.emitter = ParticleEmitter.create(SbwParticleStyles.decal(), REGION_CAP,
                    (BlockPos.getX(key) << REGION_SHIFT) + half,
                    (BlockPos.getY(key) << REGION_SHIFT) + half,
                    (BlockPos.getZ(key) << REGION_SHIFT) + half);
        }

        void accepted(long block) {
            long previous = owner[cursor];
            if (previous != EMPTY) {
                int left = counts.addTo(previous, -1) - 1;
                if (left <= 0) {
                    counts.remove(previous);
                }
            }
            owner[cursor] = block;
            counts.addTo(block, 1);
            cursor = (cursor + 1) % REGION_CAP;
        }

        int purge(long block) {
            if (!counts.containsKey(block)) {
                return 0;
            }
            int cleared = 0;
            ParticleBuffer buffer = ParticleBuffer.getInstance();
            int base = emitter.slotBase();
            for (int i = 0; i < REGION_CAP; i++) {
                if (owner[i] == block) {
                    buffer.clearSlot(base + i);
                    owner[i] = EMPTY;
                    cleared++;
                }
            }
            counts.remove(block);
            return cleared;
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
