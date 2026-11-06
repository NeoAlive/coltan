package com.neoalive.coltan.client.compat.sbw.fx;

/**
 * Particles of one material as parallel arrays in insertion order: a ring whose oldest entry is
 * dropped when full, like vanilla's EvictingQueue per render type. Fields mirror vanilla's Particle
 * state one to one, so each kind's tick is a line by line port.
 */
final class FxPool {
    final int capacity;
    final int mask;
    int head;
    int count;

    // Particle
    final double[] minX, minY, minZ, maxX, maxY, maxZ;
    final double[] xo, yo, zo, x, y, z, xd, yd, zd;
    final boolean[] onGround, hasPhysics, stopped, removed;
    final float[] bbWidth, bbHeight, gravity, friction;
    final float[] r, g, b, alpha, roll, oRoll, quadSize;
    final int[] age, lifetime;
    // TextureSheetParticle sprite and the kind's sprite set
    final Object[] sprite, spriteSet;
    // Kind and its fields
    final int[] kind, i0, i1;
    final float[] p0, p1, p2, p3, p4, p5;
    final boolean[] f0, f1;
    // Packed light and the tick it was sampled on
    final int[] light, lightAge;

    FxPool(int requested) {
        int cap = Integer.highestOneBit(Math.max(256, requested) - 1) << 1;
        capacity = cap;
        mask = cap - 1;
        minX = new double[cap];
        minY = new double[cap];
        minZ = new double[cap];
        maxX = new double[cap];
        maxY = new double[cap];
        maxZ = new double[cap];
        xo = new double[cap];
        yo = new double[cap];
        zo = new double[cap];
        x = new double[cap];
        y = new double[cap];
        z = new double[cap];
        xd = new double[cap];
        yd = new double[cap];
        zd = new double[cap];
        onGround = new boolean[cap];
        hasPhysics = new boolean[cap];
        stopped = new boolean[cap];
        removed = new boolean[cap];
        bbWidth = new float[cap];
        bbHeight = new float[cap];
        gravity = new float[cap];
        friction = new float[cap];
        r = new float[cap];
        g = new float[cap];
        b = new float[cap];
        alpha = new float[cap];
        roll = new float[cap];
        oRoll = new float[cap];
        quadSize = new float[cap];
        age = new int[cap];
        lifetime = new int[cap];
        sprite = new Object[cap];
        spriteSet = new Object[cap];
        kind = new int[cap];
        i0 = new int[cap];
        i1 = new int[cap];
        p0 = new float[cap];
        p1 = new float[cap];
        p2 = new float[cap];
        p3 = new float[cap];
        p4 = new float[cap];
        p5 = new float[cap];
        f0 = new boolean[cap];
        f1 = new boolean[cap];
        light = new int[cap];
        lightAge = new int[cap];
    }

    int slot(int logical) {
        return (head + logical) & mask;
    }

    /** Slot for a new particle at the tail, dropping the oldest when the ring is full. */
    int push() {
        if (count == capacity) {
            release(head);
            head = (head + 1) & mask;
            count--;
        }
        int s = (head + count) & mask;
        count++;
        return s;
    }

    /**
     * Drops removed particles, keeping order, as vanilla's iterator remove does after each tick.
     */
    void compact() {
        int write = 0;
        for (int read = 0; read < count; read++) {
            int from = slot(read);
            if (removed[from]) {
                release(from);
                continue;
            }
            if (write != read) {
                copy(from, slot(write));
            }
            write++;
        }
        for (int i = write; i < count; i++) {
            release(slot(i));
        }
        count = write;
    }

    void clear() {
        for (int i = 0; i < count; i++) {
            release(slot(i));
        }
        head = 0;
        count = 0;
    }

    private void release(int s) {
        sprite[s] = null;
        spriteSet[s] = null;
    }

    private void copy(int from, int to) {
        minX[to] = minX[from];
        minY[to] = minY[from];
        minZ[to] = minZ[from];
        maxX[to] = maxX[from];
        maxY[to] = maxY[from];
        maxZ[to] = maxZ[from];
        xo[to] = xo[from];
        yo[to] = yo[from];
        zo[to] = zo[from];
        x[to] = x[from];
        y[to] = y[from];
        z[to] = z[from];
        xd[to] = xd[from];
        yd[to] = yd[from];
        zd[to] = zd[from];
        onGround[to] = onGround[from];
        hasPhysics[to] = hasPhysics[from];
        stopped[to] = stopped[from];
        removed[to] = removed[from];
        bbWidth[to] = bbWidth[from];
        bbHeight[to] = bbHeight[from];
        gravity[to] = gravity[from];
        friction[to] = friction[from];
        r[to] = r[from];
        g[to] = g[from];
        b[to] = b[from];
        alpha[to] = alpha[from];
        roll[to] = roll[from];
        oRoll[to] = oRoll[from];
        quadSize[to] = quadSize[from];
        age[to] = age[from];
        lifetime[to] = lifetime[from];
        sprite[to] = sprite[from];
        spriteSet[to] = spriteSet[from];
        kind[to] = kind[from];
        i0[to] = i0[from];
        i1[to] = i1[from];
        p0[to] = p0[from];
        p1[to] = p1[from];
        p2[to] = p2[from];
        p3[to] = p3[from];
        p4[to] = p4[from];
        p5[to] = p5[from];
        f0[to] = f0[from];
        f1[to] = f1[from];
        light[to] = light[from];
        lightAge[to] = lightAge[from];
    }
}
