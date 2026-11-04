package com.neoalive.coltan.client.compat.sbw.fx;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.ColtanConfig;
import com.neoalive.coltan.client.compat.fx.FxRenderer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * {@code /coltanfx parity <count> [ticks]} keeps the next claimed SBW particles alive as real
 * objects next to their pooled copies, ticks both, and reports every field that drifts. {@code
 * /coltanfx stats} prints pool sizes.
 */
final class FxParity {
    private static final String[] FIELDS = {
        "x",
        "y",
        "z",
        "xd",
        "yd",
        "zd",
        "age",
        "alpha",
        "r",
        "g",
        "b",
        "size",
        "roll",
        "sprite",
        "onGround",
        "alive",
        "light"
    };

    private final SbwFx fx;
    private final List<Entry> entries = new ArrayList<>();
    private int armed;
    private int ticksPerEntry = 40;

    FxParity(SbwFx fx) {
        this.fx = fx;
    }

    static void register() {
        MinecraftForge.EVENT_BUS.addListener(FxParity::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher()
                .register(
                        Commands.literal("coltanfx")
                                .then(
                                        Commands.literal("parity")
                                                .then(
                                                        Commands.argument(
                                                                        "count",
                                                                        IntegerArgumentType.integer(
                                                                                1, 64))
                                                                .executes(
                                                                        ctx ->
                                                                                arm(
                                                                                        ctx
                                                                                                .getSource(),
                                                                                        IntegerArgumentType
                                                                                                .getInteger(
                                                                                                        ctx,
                                                                                                        "count"),
                                                                                        40))
                                                                .then(
                                                                        Commands.argument(
                                                                                        "ticks",
                                                                                        IntegerArgumentType
                                                                                                .integer(
                                                                                                        1,
                                                                                                        2000))
                                                                                .executes(
                                                                                        ctx ->
                                                                                                arm(
                                                                                                        ctx
                                                                                                                .getSource(),
                                                                                                        IntegerArgumentType
                                                                                                                .getInteger(
                                                                                                                        ctx,
                                                                                                                        "count"),
                                                                                                        IntegerArgumentType
                                                                                                                .getInteger(
                                                                                                                        ctx,
                                                                                                                        "ticks"))))))
                                .then(
                                        Commands.literal("stats")
                                                .executes(ctx -> stats(ctx.getSource()))));
    }

    private static int arm(CommandSourceStack source, int count, int ticks) {
        SbwFx fx = SbwFx.instance;
        if (fx == null || !fx.claiming()) {
            source.sendFailure(
                    Component.literal(
                            "Coltan particle pipeline is not claiming (particles.sbwInstancedParticles"
                                    + " off, shader pack, or failed)"));
            return 0;
        }
        fx.parity.armed = count;
        fx.parity.ticksPerEntry = ticks;
        source.sendSuccess(
                () ->
                        Component.literal(
                                "Tracking the next "
                                        + count
                                        + " SBW particles for "
                                        + ticks
                                        + " ticks; spawn some"),
                false);
        return 1;
    }

    private static int stats(CommandSourceStack source) {
        SbwFx fx = SbwFx.instance;
        if (fx == null) {
            source.sendFailure(Component.literal("Coltan particle pipeline not loaded"));
            return 0;
        }
        String line =
                String.format(
                        Locale.ROOT,
                        "fx soft=%d translucent=%d lit=%d opaque=%d pending=%d claiming=%s failed=%s",
                        fx.count(FxRenderer.SOFT),
                        fx.count(FxRenderer.TRANSLUCENT),
                        fx.count(FxRenderer.LIT),
                        fx.count(FxRenderer.OPAQUE),
                        fx.pending(),
                        fx.claiming(),
                        fx.failed());
        source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    void track(Particle particle, int material, long serial) {
        if (armed <= 0) {
            return;
        }
        armed--;
        entries.add(new Entry(particle, material, serial, ticksPerEntry));
    }

    void clear() {
        entries.clear();
        armed = 0;
    }

    /** Runs after the pools ticked: tick the real object once and compare. */
    void tick(ClientLevel level) throws ReflectiveOperationException {
        if (entries.isEmpty()) {
            return;
        }
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            entry.particle.tick();
            entry.ticks++;
            FxPool pool = fx.pool(entry.material);
            int slot = find(pool, entry.serial);
            boolean realAlive = entry.particle.isAlive();
            boolean done = entry.ticks >= entry.limit || !realAlive || slot < 0;
            compare(entry, pool, slot, realAlive);
            if (done) {
                report(entry);
                it.remove();
            }
        }
    }

    private static int find(FxPool pool, long serial) {
        if (pool == null) {
            return -1;
        }
        for (int i = 0; i < pool.count; i++) {
            int s = pool.slot(i);
            if (pool.serial[s] == serial) {
                return s;
            }
        }
        return -1;
    }

    private void compare(Entry e, FxPool pool, int s, boolean realAlive)
            throws ReflectiveOperationException {
        FxFields f = fx.fields();
        Particle p = e.particle;
        // Our pool drops a slot the tick it is removed; vanilla's object only flags it.
        boolean oursAlive = s >= 0 && !pool.removed[s];
        diff(e, 15, realAlive == oursAlive ? 0 : 1);
        if (s < 0) {
            return;
        }
        diff(e, 0, f.x.getDouble(p) - pool.x[s]);
        diff(e, 1, f.y.getDouble(p) - pool.y[s]);
        diff(e, 2, f.z.getDouble(p) - pool.z[s]);
        diff(e, 3, f.xd.getDouble(p) - pool.xd[s]);
        diff(e, 4, f.yd.getDouble(p) - pool.yd[s]);
        diff(e, 5, f.zd.getDouble(p) - pool.zd[s]);
        diff(e, 6, f.age.getInt(p) - pool.age[s]);
        diff(e, 7, f.alpha.getFloat(p) - pool.alpha[s]);
        diff(e, 8, f.rCol.getFloat(p) - pool.r[s]);
        diff(e, 9, f.gCol.getFloat(p) - pool.g[s]);
        diff(e, 10, f.bCol.getFloat(p) - pool.b[s]);
        diff(e, 11, f.quadSize.getFloat(p) - pool.quadSize[s]);
        diff(e, 12, f.roll.getFloat(p) - pool.roll[s]);
        diff(e, 13, f.sprite.get(p) == pool.sprite[s] ? 0 : 1);
        diff(e, 14, f.onGround.getBoolean(p) == pool.onGround[s] ? 0 : 1);
        if (realAlive && oursAlive) {
            int realLight = (int) f.lightColor.invoke(p, 0.0F);
            diff(e, 16, realLight == pool.light[s] ? 0 : 1);
        }
    }

    private static void diff(Entry e, int field, double delta) {
        double d = Math.abs(delta);
        if (d > e.max[field]) {
            e.max[field] = d;
        }
        if (d != 0.0D) {
            e.mismatches[field]++;
        }
    }

    private static void report(Entry e) {
        StringBuilder bad = new StringBuilder();
        for (int i = 0; i < FIELDS.length; i++) {
            if (e.mismatches[i] > 0) {
                bad.append(' ')
                        .append(FIELDS[i])
                        .append('x')
                        .append(e.mismatches[i])
                        .append(" max ")
                        .append(String.format(Locale.ROOT, "%.3g", e.max[i]));
            }
        }
        String name = e.particle.getClass().getSimpleName();
        String line =
                bad.length() == 0
                        ? "parity " + name + ": " + e.ticks + " ticks, identical"
                        : "parity " + name + ": " + e.ticks + " ticks, drift:" + bad;
        Coltan.LOGGER.info("[coltan fx] {}", line);
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(line), false);
        }
    }

    private static final class Entry {
        final Particle particle;
        final int material;
        final long serial;
        final int limit;
        final double[] max = new double[FIELDS.length];
        final int[] mismatches = new int[FIELDS.length];
        int ticks;

        Entry(Particle particle, int material, long serial, int limit) {
            this.particle = particle;
            this.material = material;
            this.serial = serial;
            this.limit = Math.max(1, Math.min(limit, ColtanConfig.particleMaxPerMaterial));
        }
    }
}
