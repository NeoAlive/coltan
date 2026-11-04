package com.neoalive.coltan.client.compat.sbw.light;

import com.atsuishio.superbwarfare.client.lighting.LightPositionRegistry;
import com.neoalive.coltan.ColtanConfig;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraftforge.event.level.LevelEvent;

/**
 * Relight bookkeeping for SBW sparks: the last level queued per spark, the optional per tick budget
 * for live sparks, and the registry reset on level change. Client tick thread only.
 */
public final class SbwLightRelight {
    private static final byte UNSEEN = -2;
    private static final Long2ByteOpenHashMap LAST = new Long2ByteOpenHashMap();

    /** Live spark checks over budget, oldest first. */
    private static final LongLinkedOpenHashSet DEFERRED = new LongLinkedOpenHashSet();

    private static final BlockPos.MutableBlockPos SCRATCH = new BlockPos.MutableBlockPos();
    private static int budget = Integer.MAX_VALUE;

    static {
        LAST.defaultReturnValue(UNSEEN);
    }

    private SbwLightRelight() {}

    /**
     * True when the spark's level moved to a new fade step since the last check queued, recording
     * it. Going dark always counts as a change.
     */
    public static boolean changed(long packedPos) {
        if (!ColtanConfig.sbwLightDedupe) {
            return true;
        }
        int level = LightPositionRegistry.getLevel(packedPos);
        int step = ColtanConfig.sbwLightFadeStep;
        byte bucket = (byte) (level <= 0 ? -1 : step <= 1 ? level : level / step);
        return LAST.put(packedPos, bucket) != bucket;
    }

    public static void forget(long packedPos) {
        LAST.remove(packedPos);
        DEFERRED.remove(packedPos);
    }

    /** Start of SBW's light tick: refill the budget and drain checks left from earlier ticks. */
    public static void beginTick(LevelLightEngine engine) {
        int limit = ColtanConfig.sbwLightChecksPerTick;
        budget = limit <= 0 ? Integer.MAX_VALUE : limit;
        while (budget > 0 && !DEFERRED.isEmpty()) {
            engine.checkBlock(SCRATCH.set(DEFERRED.removeFirstLong()));
            budget--;
        }
    }

    /** Queues a live spark check now, or defers it when this tick's budget is spent. */
    public static void queueLive(LevelLightEngine engine, BlockPos pos) {
        if (budget > 0) {
            if (budget != Integer.MAX_VALUE) {
                budget--;
            }
            engine.checkBlock(pos);
        } else {
            DEFERRED.add(pos.asLong());
        }
    }

    public static int size() {
        return LAST.size();
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        LAST.clear();
        DEFERRED.clear();
        // Sparks are keyed by position only, so leftovers would light the next level.
        LightPositionRegistry.clear();
    }
}
