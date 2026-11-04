package com.neoalive.coltan.client.compat.sbw.fx;

import java.util.ArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code Entity.collideBoundingBox(null, ...)} as Particle.move calls it, without the immutable
 * list and iterator: the same block range, the same corner and edge rules as BlockCollisions, the
 * same Shapes.collide order. Sections with only air are skipped whole, which BlockCollisions would
 * also find empty. Client tick thread only.
 */
final class FxCollide {
    private final ArrayList<VoxelShape> shapes = new ArrayList<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    @Nullable private BlockGetter cachedChunk;
    private long cachedChunkPos = Long.MIN_VALUE;
    @Nullable private Level cachedLevel;

    double rx;
    double ry;
    double rz;

    /** Resolves the move into {@link #rx}, {@link #ry}, {@link #rz}. */
    void collide(Level level, AABB box, double dx, double dy, double dz) {
        rx = dx;
        ry = dy;
        rz = dz;
        if (level != cachedLevel) {
            cachedLevel = level;
            cachedChunk = null;
            cachedChunkPos = Long.MIN_VALUE;
        }
        AABB swept = box.expandTowards(dx, dy, dz);
        gather(level, swept);
        if (shapes.isEmpty()) {
            return;
        }
        double d0 = dx;
        double d1 = dy;
        double d2 = dz;
        AABB bb = box;
        if (d1 != 0.0D) {
            d1 = Shapes.collide(Direction.Axis.Y, bb, shapes, d1);
            if (d1 != 0.0D) {
                bb = bb.move(0.0D, d1, 0.0D);
            }
        }
        boolean zFirst = Math.abs(d0) < Math.abs(d2);
        if (zFirst && d2 != 0.0D) {
            d2 = Shapes.collide(Direction.Axis.Z, bb, shapes, d2);
            if (d2 != 0.0D) {
                bb = bb.move(0.0D, 0.0D, d2);
            }
        }
        if (d0 != 0.0D) {
            d0 = Shapes.collide(Direction.Axis.X, bb, shapes, d0);
            if (!zFirst && d0 != 0.0D) {
                bb = bb.move(d0, 0.0D, 0.0D);
            }
        }
        if (!zFirst && d2 != 0.0D) {
            d2 = Shapes.collide(Direction.Axis.Z, bb, shapes, d2);
        }
        shapes.clear();
        rx = d0;
        ry = d1;
        rz = d2;
    }

    private void gather(Level level, AABB box) {
        shapes.clear();
        int x0 = Mth.floor(box.minX - 1.0E-7D) - 1;
        int x1 = Mth.floor(box.maxX + 1.0E-7D) + 1;
        int y0 = Mth.floor(box.minY - 1.0E-7D) - 1;
        int y1 = Mth.floor(box.maxY + 1.0E-7D) + 1;
        int z0 = Mth.floor(box.minZ - 1.0E-7D) - 1;
        int z1 = Mth.floor(box.maxZ + 1.0E-7D) + 1;
        VoxelShape boxShape = null;
        for (int bz = z0; bz <= z1; bz++) {
            for (int by = y0; by <= y1; by++) {
                for (int bx = x0; bx <= x1; bx++) {
                    int type =
                            (bx == x0 || bx == x1 ? 1 : 0)
                                    + (by == y0 || by == y1 ? 1 : 0)
                                    + (bz == z0 || bz == z1 ? 1 : 0);
                    if (type == 3) {
                        continue;
                    }
                    BlockGetter chunk = chunk(level, bx, bz);
                    if (chunk == null) {
                        continue;
                    }
                    if (chunk instanceof LevelChunk levelChunk && onlyAir(levelChunk, by)) {
                        continue;
                    }
                    pos.set(bx, by, bz);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if ((type == 1 && !state.hasLargeCollisionShape())
                            || (type == 2 && !state.is(Blocks.MOVING_PISTON))) {
                        continue;
                    }
                    VoxelShape shape =
                            state.getCollisionShape(level, pos, CollisionContext.empty());
                    if (shape == Shapes.block()) {
                        if (box.intersects(bx, by, bz, bx + 1.0D, by + 1.0D, bz + 1.0D)) {
                            shapes.add(shape.move(bx, by, bz));
                        }
                    } else {
                        VoxelShape moved = shape.move(bx, by, bz);
                        if (!moved.isEmpty()) {
                            if (boxShape == null) {
                                boxShape = Shapes.create(box);
                            }
                            if (Shapes.joinIsNotEmpty(moved, boxShape, BooleanOp.AND)) {
                                shapes.add(moved);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean onlyAir(LevelChunk chunk, int y) {
        int index = chunk.getSectionIndex(y);
        if (index < 0 || index >= chunk.getSections().length) {
            return true;
        }
        LevelChunkSection section = chunk.getSection(index);
        return section.hasOnlyAir();
    }

    @Nullable
    private BlockGetter chunk(Level level, int bx, int bz) {
        int cx = bx >> 4;
        int cz = bz >> 4;
        long key = ChunkPos.asLong(cx, cz);
        if (key != cachedChunkPos) {
            cachedChunk = level.getChunkForCollisions(cx, cz);
            cachedChunkPos = key;
        }
        return cachedChunk;
    }

    /** Chunks can unload between ticks. */
    void forgetChunks() {
        cachedChunk = null;
        cachedChunkPos = Long.MIN_VALUE;
    }
}
