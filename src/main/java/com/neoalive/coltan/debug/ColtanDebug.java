package com.neoalive.coltan.debug;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import com.neoalive.coltan.Coltan;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Category-gated client diagnostics. Off by default; enable without restart via
 * {@code config/coltan/debug.txt} (one line, e.g. {@code all} or {@code vehicle,gun,lod})
 * or JVM {@code -Dcoltan.debug=all}.
 *
 * <p>Uses {@code once} / {@code whenChanged} / {@code every} so hot paths cannot flood the
 * log truncator. Messages always go to {@link Coltan#LOGGER} at INFO when the category is on.
 */
public final class ColtanDebug {
    public enum Cat {
        BOOT,
        VEHICLE,
        LOD,
        GUN,
        ARMOR,
        PROJECTILE,
        MUNITION,
        BLOCK,
        PARTICLE,
        SKIN,
        CLAIM,
        CACHE,
        FAIL
    }

    private static final long FILE_REFRESH_MS = 2000L;
    private static final Map<String, Boolean> ONCE = new ConcurrentHashMap<>();
    private static final Map<String, Object> LAST = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_MS = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> COUNTERS = new ConcurrentHashMap<>();

    private static volatile EnumSet<Cat> enabled = EnumSet.noneOf(Cat.class);
    private static volatile long nextFileCheck;
    private static volatile String lastSpec = "";

    private ColtanDebug() {
    }

    public static boolean on(Cat cat) {
        refresh();
        EnumSet<Cat> set = enabled;
        return !set.isEmpty() && set.contains(cat);
    }

    public static boolean any() {
        refresh();
        return !enabled.isEmpty();
    }

    /** One-shot per key for the life of the JVM (or until {@link #clear()}). */
    public static void once(Cat cat, String key, String format, Object... args) {
        if (!on(cat)) {
            return;
        }
        if (ONCE.putIfAbsent(cat.name() + "|" + key, Boolean.TRUE) != null) {
            return;
        }
        log(cat, format, args);
    }

    /** Log only when {@code value} differs from the last value for {@code key}. */
    public static void whenChanged(Cat cat, String key, Object value, String format, Object... args) {
        if (!on(cat)) {
            return;
        }
        Object prev = LAST.put(cat.name() + "|" + key, value == null ? NULL : value);
        Object cmp = value == null ? NULL : value;
        if (ObjectsEquals(prev, cmp)) {
            return;
        }
        log(cat, format, args);
    }

    /** At most one log per {@code key} every {@code minIntervalMs}. */
    public static void every(Cat cat, String key, long minIntervalMs, String format, Object... args) {
        if (!on(cat)) {
            return;
        }
        long now = System.currentTimeMillis();
        String mapKey = cat.name() + "|" + key;
        Long prev = LAST_MS.get(mapKey);
        if (prev != null && now - prev < minIntervalMs) {
            return;
        }
        LAST_MS.put(mapKey, now);
        log(cat, format, args);
    }

    /** Unconditional (still category-gated) — for rebuild summaries. */
    public static void log(Cat cat, String format, Object... args) {
        if (!on(cat)) {
            return;
        }
        Coltan.LOGGER.info("[coltan:{}] {}", cat.name().toLowerCase(Locale.ROOT),
                args == null || args.length == 0 ? format : String.format(Locale.ROOT, format, args));
    }

    public static void failOnce(String key, String format, Object... args) {
        refresh();
        if (enabled.isEmpty()) {
            return;
        }
        if (ONCE.putIfAbsent(Cat.FAIL.name() + "|" + key, Boolean.TRUE) != null) {
            return;
        }
        Coltan.LOGGER.info("[coltan:fail] {}",
                args == null || args.length == 0 ? format : String.format(Locale.ROOT, format, args));
    }

    public static void failWhenChanged(String key, Object value, String format, Object... args) {
        refresh();
        if (enabled.isEmpty()) {
            return;
        }
        Object prev = LAST.put(Cat.FAIL.name() + "|" + key, value == null ? NULL : value);
        Object cmp = value == null ? NULL : value;
        if (ObjectsEquals(prev, cmp)) {
            return;
        }
        Coltan.LOGGER.info("[coltan:fail] {}",
                args == null || args.length == 0 ? format : String.format(Locale.ROOT, format, args));
    }

    /** Increment a named counter; periodically emit the total when {@code cat} is on. */
    public static void count(Cat cat, String counter, long flushIntervalMs, String format) {
        LongAdder adder = COUNTERS.computeIfAbsent(counter, k -> new LongAdder());
        adder.increment();
        every(cat, "count:" + counter, flushIntervalMs, format, adder.sum());
    }

    public static void clear() {
        ONCE.clear();
        LAST.clear();
        LAST_MS.clear();
        COUNTERS.clear();
    }

    /** Current enabled set as a short string for boot banners. */
    public static String describe() {
        refresh();
        if (enabled.isEmpty()) {
            return "off";
        }
        return enabled.toString();
    }

    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now < nextFileCheck) {
            return;
        }
        nextFileCheck = now + FILE_REFRESH_MS;
        String spec = readSpec();
        if (spec.equals(lastSpec)) {
            return;
        }
        lastSpec = spec;
        enabled = parse(spec);
        if (!enabled.isEmpty()) {
            Coltan.LOGGER.info("[coltan:boot] debug flags = {}", enabled);
        }
    }

    private static String readSpec() {
        String prop = System.getProperty("coltan.debug", "");
        if (prop == null) {
            prop = "";
        }
        prop = prop.trim();
        String env = System.getenv("COLTAN_DEBUG");
        if ((prop.isEmpty()) && env != null && !env.isBlank()) {
            prop = env.trim();
        }
        try {
            Path file = FMLPaths.CONFIGDIR.get().resolve("coltan").resolve("debug.txt");
            if (Files.isRegularFile(file)) {
                String line = Files.readString(file, StandardCharsets.UTF_8).trim();
                // First non-empty, non-comment line wins.
                for (String raw : line.split("\\R")) {
                    String s = raw.trim();
                    if (s.isEmpty() || s.startsWith("#") || s.startsWith("//")) {
                        continue;
                    }
                    return s;
                }
            }
        } catch (Exception ignored) {
            // Keep JVM/env spec.
        }
        return prop;
    }

    private static EnumSet<Cat> parse(String spec) {
        EnumSet<Cat> out = EnumSet.noneOf(Cat.class);
        if (spec == null || spec.isBlank()) {
            return out;
        }
        String normalized = spec.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(normalized) || "true".equals(normalized) || "all".equals(normalized)
                || "*".equals(normalized)) {
            out.addAll(EnumSet.allOf(Cat.class));
            return out;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "off".equals(normalized)
                || "none".equals(normalized)) {
            return out;
        }
        for (String part : normalized.split("[,|;\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                out.add(Cat.valueOf(part.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                Coltan.LOGGER.warn("[coltan:boot] unknown debug category '{}'", part);
            }
        }
        return out;
    }

    private static boolean ObjectsEquals(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    private static final Object NULL = new Object();

    /** Forced refresh after writing {@code debug.txt} in the same tick. */
    public static void forceRefresh() {
        nextFileCheck = 0L;
        lastSpec = "\0";
        refresh();
    }
}
