package com.neoalive.coltan.client.compat.bridge;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.debug.ColtanDebug;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Tier B: ASM scrape of SBW gun renderer/model FP constants, seed fallback, then optional overrides.
 */
public final class GunFpProbe {
    private static final String HANDLE_ZOOM = "handleZoomCrossHair";
    private static final String HANDLE_SHOOT = "handleShootAnimation";
    private static final String GUN_ROOT = "gunRootMove";
    private static final String SET_POS_X = "setPosX";

    private GunFpProbe() {
    }

    public static GunBridgeProfile enrich(GunBridgeProfile base) {
        if (base == null || base.hasFpData()) {
            return base;
        }

        Scrape scrape = scrape(base.rendererClass(), deriveModelClass(base.rendererClass()));
        GunBridgeProfile profile = applyScrape(base, scrape);

        if (profile.scopeCrosshair().isEmpty()
                || Math.abs(profile.posX()) + Math.abs(profile.posY()) + Math.abs(profile.posZ()) <= 1e-3f) {
            profile = mergeSeed(profile, GunAdsProfile.seedFromItemId(profile.itemId()));
        }

        GunBridgeOverride.Data override = GunBridgeOverride.load(profile.itemId());
        if (!override.isEmpty()) {
            profile = applyOverride(profile, override);
        }
        return profile;
    }

    private static GunBridgeProfile applyScrape(GunBridgeProfile base, Scrape scrape) {
        float posX = scrape.posX != null ? scrape.posX : base.posX();
        float posY = scrape.posY != null ? scrape.posY : base.posY();
        float posZ = scrape.posZ != null ? scrape.posZ : base.posZ();
        float scaleZ = scrape.scaleZ != null ? scrape.scaleZ : base.scaleZ();
        float rootX = scrape.rootX != null ? scrape.rootX : base.rootCustomX();
        float rootY = scrape.rootY != null ? scrape.rootY : base.rootCustomY();
        float rootZ = scrape.rootZ != null ? scrape.rootZ : base.rootCustomZ();
        float rx = scrape.recoilX != null ? scrape.recoilX : base.recoilX();
        float ry = scrape.recoilY != null ? scrape.recoilY : base.recoilY();
        float rz = scrape.recoilZ != null ? scrape.recoilZ : base.recoilZ();
        float rrx = scrape.recoilRotX != null ? scrape.recoilRotX : base.recoilRotX();
        float rry = scrape.recoilRotY != null ? scrape.recoilRotY : base.recoilRotY();
        float rrz = scrape.recoilRotZ != null ? scrape.recoilRotZ : base.recoilRotZ();
        float rzm = scrape.recoilZoomMul != null ? scrape.recoilZoomMul : base.recoilZoomMul();
        float rs = scrape.recoilSpeed != null ? scrape.recoilSpeed : base.recoilSpeed();

        Map<Integer, List<String>> zoomHide = scrape.zoomHide.isEmpty()
                ? base.scopeZoomHide() : Map.copyOf(scrape.zoomHide);
        Map<Integer, GunBridgeProfile.Crosshair> cross = scrape.crosshair.isEmpty()
                ? base.scopeCrosshair() : Map.copyOf(scrape.crosshair);
        Map<Integer, GunBridgeProfile.AdsPose> ads = scrape.scopeAds.isEmpty()
                ? base.scopeAds() : Map.copyOf(scrape.scopeAds);

        return base.withFp(rootX, rootY, rootZ, posX, posY, posZ, scaleZ,
                rx, ry, rz, rrx, rry, rrz, rzm, rs, zoomHide, cross, ads, null, null, null);
    }

    private static GunBridgeProfile mergeSeed(GunBridgeProfile base, GunAdsProfile seed) {
        Map<Integer, GunBridgeProfile.Crosshair> cross = new LinkedHashMap<>(base.scopeCrosshair());
        if (cross.isEmpty()) {
            for (var e : seed.scopeCrosshair().entrySet()) {
                GunAdsProfile.Crosshair c = e.getValue();
                cross.put(e.getKey(), new GunBridgeProfile.Crosshair(
                        c.x(), c.y(), c.z(), c.size(), c.r(), c.g(), c.b(), c.a(), c.texture(),
                        c.hasBlack()));
            }
        }
        Map<Integer, List<String>> zoom = base.scopeZoomHide().isEmpty()
                ? seed.scopeZoomHide() : base.scopeZoomHide();
        Map<Integer, GunBridgeProfile.AdsPose> ads = new LinkedHashMap<>(base.scopeAds());
        if (ads.isEmpty()) {
            ads.putAll(akScopeAdsIfNeeded(base.itemId(), seed));
        }

        float posX = nearZero(base.posX(), base.posY(), base.posZ()) ? seed.posX() : base.posX();
        float posY = nearZero(base.posX(), base.posY(), base.posZ()) ? seed.posY() : base.posY();
        float posZ = nearZero(base.posX(), base.posY(), base.posZ()) ? seed.posZ() : base.posZ();
        float scaleZ = base.scaleZ() == 1f && nearZero(base.posX(), base.posY(), base.posZ())
                ? seed.scaleZ() : base.scaleZ();

        return base.withFp(
                base.rootCustomX() == 0f && base.rootCustomY() == 0f && base.rootCustomZ() == 0f
                        ? seed.rootCustomX() : base.rootCustomX(),
                base.rootCustomX() == 0f && base.rootCustomY() == 0f && base.rootCustomZ() == 0f
                        ? seed.rootCustomY() : base.rootCustomY(),
                base.rootCustomX() == 0f && base.rootCustomY() == 0f && base.rootCustomZ() == 0f
                        ? seed.rootCustomZ() : base.rootCustomZ(),
                posX, posY, posZ, scaleZ,
                base.recoilSpeed() == 0f ? seed.recoilX() : base.recoilX(),
                base.recoilSpeed() == 0f ? seed.recoilY() : base.recoilY(),
                base.recoilSpeed() == 0f ? seed.recoilZ() : base.recoilZ(),
                base.recoilSpeed() == 0f ? seed.recoilRotX() : base.recoilRotX(),
                base.recoilSpeed() == 0f ? seed.recoilRotY() : base.recoilRotY(),
                base.recoilSpeed() == 0f ? seed.recoilRotZ() : base.recoilRotZ(),
                base.recoilSpeed() == 0f ? seed.recoilZoomMul() : base.recoilZoomMul(),
                base.recoilSpeed() == 0f ? seed.recoilSpeed() : base.recoilSpeed(),
                zoom, cross, ads,
                seed.adsBone(), seed.recoilBone(), seed.boltBone());
    }

    private static Map<Integer, GunBridgeProfile.AdsPose> akScopeAdsIfNeeded(
            ResourceLocation itemId, GunAdsProfile seed) {
        if (itemId == null || !"ak_47".equals(itemId.getPath())) {
            return Map.of();
        }
        // Former GunPoseState AK switch — keep quality without hardcoding at runtime.
        Map<Integer, GunBridgeProfile.AdsPose> ads = new LinkedHashMap<>();
        ads.put(0, new GunBridgeProfile.AdsPose(seed.posX(), seed.posY(), seed.posZ(), seed.scaleZ()));
        ads.put(1, new GunBridgeProfile.AdsPose(1.962f, 0.261f, 2.8f, 0.2f));
        ads.put(2, new GunBridgeProfile.AdsPose(1.852f, 0.162f + 0.45f, 4.74f, 0.87f));
        ads.put(3, new GunBridgeProfile.AdsPose(1.962f, 0.099f + 0.5f, 4.5f, 0.84f));
        return ads;
    }

    private static boolean nearZero(float x, float y, float z) {
        return Math.abs(x) + Math.abs(y) + Math.abs(z) <= 1e-3f;
    }

    private static GunBridgeProfile applyOverride(GunBridgeProfile base, GunBridgeOverride.Data o) {
        GunBridgeProfile profile = base;
        if (o.adsSeed() != null) {
            GunAdsProfile seed = GunAdsProfile.seedByName(o.adsSeed());
            if (seed != null) {
                profile = mergeSeed(profile.withFp(
                        0f, 0f, 0f, 0f, 0f, 0f, 1f,
                        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
                        Map.of(), Map.of(), Map.of(), null, null, null), seed);
            }
        }
        float posX = o.posX() != null ? o.posX() : profile.posX();
        float posY = o.posY() != null ? o.posY() : profile.posY();
        float posZ = o.posZ() != null ? o.posZ() : profile.posZ();
        float scaleZ = o.scaleZ() != null ? o.scaleZ() : profile.scaleZ();
        float rootX = o.rootCustomX() != null ? o.rootCustomX() : profile.rootCustomX();
        float rootY = o.rootCustomY() != null ? o.rootCustomY() : profile.rootCustomY();
        float rootZ = o.rootCustomZ() != null ? o.rootCustomZ() : profile.rootCustomZ();
        float rx = o.recoilX() != null ? o.recoilX() : profile.recoilX();
        float ry = o.recoilY() != null ? o.recoilY() : profile.recoilY();
        float rz = o.recoilZ() != null ? o.recoilZ() : profile.recoilZ();
        float rrx = o.recoilRotX() != null ? o.recoilRotX() : profile.recoilRotX();
        float rry = o.recoilRotY() != null ? o.recoilRotY() : profile.recoilRotY();
        float rrz = o.recoilRotZ() != null ? o.recoilRotZ() : profile.recoilRotZ();
        float rzm = o.recoilZoomMul() != null ? o.recoilZoomMul() : profile.recoilZoomMul();
        float rs = o.recoilSpeed() != null ? o.recoilSpeed() : profile.recoilSpeed();
        Map<Integer, List<String>> zoom = o.scopeZoomHide().isEmpty()
                ? profile.scopeZoomHide() : o.scopeZoomHide();
        Map<Integer, GunBridgeProfile.Crosshair> cross = o.scopeCrosshair().isEmpty()
                ? profile.scopeCrosshair() : o.scopeCrosshair();
        Map<Integer, GunBridgeProfile.AdsPose> ads = o.scopeAds().isEmpty()
                ? profile.scopeAds() : o.scopeAds();
        return profile.withFp(rootX, rootY, rootZ, posX, posY, posZ, scaleZ,
                rx, ry, rz, rrx, rry, rrz, rzm, rs, zoom, cross, ads, null, null, null);
    }

    private static Scrape scrape(@Nullable String rendererClass, @Nullable String modelClass) {
        Scrape out = new Scrape();
        ClassNode renderer = readClass(rendererClass);
        if (renderer != null) {
            scrapeRenderer(renderer, out);
        }
        ClassNode model = readClass(modelClass);
        if (model == null && rendererClass != null) {
            model = readClass(deriveModelClass(rendererClass));
        }
        if (model != null) {
            scrapeModel(model, out);
        }
        return out;
    }

    private static void scrapeRenderer(ClassNode cn, Scrape out) {
        int crossIndex = 0;
        for (MethodNode method : cn.methods) {
            InsnList insns = method.instructions;
            if (insns == null || insns.size() == 0) {
                continue;
            }
            AbstractInsnNode insn = insns.getFirst();
            while (insn != null) {
                if (insn instanceof MethodInsnNode call && HANDLE_ZOOM.equals(call.name)) {
                    crossIndex++;
                    GunBridgeProfile.Crosshair ch = readCrosshairArgs(insn);
                    if (ch != null) {
                        int scope = guessScopeIndex(insn, crossIndex);
                        out.crosshair.putIfAbsent(scope, ch);
                    }
                }
                insn = insn.getNext();
            }
            scrapeZoomHides(method, out);
        }
    }

    private static void scrapeZoomHides(MethodNode method, Scrape out) {
        InsnList insns = method.instructions;
        if (insns == null) {
            return;
        }
        Integer currentScope = null;
        AbstractInsnNode insn = insns.getFirst();
        while (insn != null) {
            if (isScopeCompare(insn)) {
                currentScope = intConstantBefore(insn);
            } else if (currentScope != null && insn instanceof MethodInsnNode call
                    && "equals".equals(call.name)
                    && "java/lang/String".equals(call.owner)) {
                String bone = stringArgBefore(insn);
                if (bone != null && !bone.isEmpty() && looksLikeBoneName(bone)) {
                    out.zoomHide.computeIfAbsent(currentScope, k -> new ArrayList<>());
                    List<String> list = out.zoomHide.get(currentScope);
                    if (!list.contains(bone)) {
                        list.add(bone);
                    }
                }
            } else if (currentScope != null && isScopeCompare(insn.getNext() != null ? insn : insn)) {
                // next scope compare handled at loop top
            }
            // Reset scope when another SCOPE get is far — keep simple: new compare replaces current.
            insn = insn.getNext();
        }
    }

    private static boolean looksLikeBoneName(String s) {
        if (s.length() > 32 || s.contains("/") || s.contains(".")) {
            return false;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        return !lower.equals("true") && !lower.equals("false");
    }

    private static boolean isScopeCompare(AbstractInsnNode insn) {
        if (insn == null) {
            return false;
        }
        // AttachmentType.SCOPE getfield followed later by icmp — detect SCOPE field ref nearby.
        AbstractInsnNode p = insn;
        for (int i = 0; i < 8 && p != null; i++) {
            if (p instanceof FieldInsnNode f && "SCOPE".equals(f.name)) {
                return insn.getOpcode() == Opcodes.IF_ICMPEQ
                        || insn.getOpcode() == Opcodes.IF_ICMPNE
                        || insn.getOpcode() == Opcodes.TABLESWITCH
                        || insn.getOpcode() == Opcodes.LOOKUPSWITCH;
            }
            p = p.getPrevious();
        }
        return false;
    }

    @Nullable
    private static Integer intConstantBefore(AbstractInsnNode call) {
        AbstractInsnNode p = call.getPrevious();
        for (int i = 0; i < 12 && p != null; i++) {
            Integer v = asInt(p);
            if (v != null && v >= 0 && v <= 8) {
                return v;
            }
            p = p.getPrevious();
        }
        return null;
    }

    private static int guessScopeIndex(AbstractInsnNode call, int appearanceOrder) {
        Integer nearby = intConstantBefore(call);
        if (nearby != null && nearby >= 1 && nearby <= 4) {
            return nearby;
        }
        return appearanceOrder;
    }

    @Nullable
    private static GunBridgeProfile.Crosshair readCrosshairArgs(AbstractInsnNode call) {
        // Args pushed before invoke: … D D D F I I I I String Z
        List<Object> consts = new ArrayList<>();
        AbstractInsnNode p = call.getPrevious();
        for (int i = 0; i < 48 && p != null && consts.size() < 16; i++) {
            Object c = constantValue(p);
            if (c != null) {
                consts.add(0, c);
            }
            p = p.getPrevious();
        }
        // Walk from end: bool, string, a, b, g, r, size, z, y, x (rough)
        if (consts.size() < 10) {
            return null;
        }
        try {
            int n = consts.size();
            boolean hasBlack = asBool(consts.get(n - 1));
            String texture = String.valueOf(consts.get(n - 2));
            int a = asIntValue(consts.get(n - 3), 255);
            int b = asIntValue(consts.get(n - 4), 0);
            int g = asIntValue(consts.get(n - 5), 0);
            int r = asIntValue(consts.get(n - 6), 255);
            float size = asFloatValue(consts.get(n - 7), 1f);
            double z = asDoubleValue(consts.get(n - 8), 0);
            double y = asDoubleValue(consts.get(n - 9), 0);
            double x = asDoubleValue(consts.get(n - 10), 0);
            if (texture.contains("/") || texture.length() > 24) {
                return null;
            }
            return new GunBridgeProfile.Crosshair(x, y, z, size, r, g, b, a, texture, hasBlack);
        } catch (Exception e) {
            return null;
        }
    }

    private static void scrapeModel(ClassNode cn, Scrape out) {
        for (MethodNode method : cn.methods) {
            InsnList insns = method.instructions;
            if (insns == null) {
                continue;
            }
            AbstractInsnNode insn = insns.getFirst();
            while (insn != null) {
                if (insn instanceof MethodInsnNode call) {
                    if (HANDLE_SHOOT.equals(call.name) && !call.name.contains("V2")) {
                        float[] floats = floatArgsBefore(insn, 8);
                        if (floats != null && floats.length >= 8) {
                            out.recoilX = floats[0];
                            out.recoilY = floats[1];
                            out.recoilZ = floats[2];
                            out.recoilRotX = floats[3];
                            out.recoilRotY = floats[4];
                            out.recoilRotZ = floats[5];
                            out.recoilZoomMul = floats[6];
                            out.recoilSpeed = floats[7];
                        }
                    } else if (GUN_ROOT.equals(call.name)) {
                        float[] floats = floatArgsBefore(insn, 3);
                        if (floats != null && floats.length >= 3) {
                            out.rootX = floats[0];
                            out.rootY = floats[1];
                            out.rootZ = floats[2];
                        }
                    } else if (SET_POS_X.equals(call.name) && out.posX == null) {
                        // Default ADS cluster: first setPosX float const ≈ default scope pose X.
                        Float x = floatConstBefore(insn);
                        if (x != null) {
                            out.posX = x;
                            // Look ahead for setPosY / setPosZ / setScaleZ nearby constants — best-effort.
                            fillAdsCluster(insn, out);
                        }
                    }
                }
                insn = insn.getNext();
            }
        }
    }

    private static void fillAdsCluster(AbstractInsnNode from, Scrape out) {
        AbstractInsnNode n = from.getNext();
        int seen = 0;
        while (n != null && seen < 40) {
            if (n instanceof MethodInsnNode call) {
                if ("setPosY".equals(call.name) && out.posY == null) {
                    out.posY = floatConstBefore(n);
                } else if ("setPosZ".equals(call.name) && out.posZ == null) {
                    out.posZ = floatConstBefore(n);
                } else if ("setScaleZ".equals(call.name) && out.scaleZ == null) {
                    // Often 1 - scale*zoom; catch preceding float if small.
                    Float f = floatConstBefore(n);
                    if (f != null && f > 0f && f < 1.5f) {
                        out.scaleZ = f;
                    }
                }
            }
            n = n.getNext();
            seen++;
        }
    }

    @Nullable
    private static float[] floatArgsBefore(AbstractInsnNode call, int count) {
        List<Float> floats = new ArrayList<>();
        AbstractInsnNode p = call.getPrevious();
        for (int i = 0; i < 64 && p != null && floats.size() < count; i++) {
            Float f = asFloat(p);
            if (f != null) {
                floats.add(0, f);
            }
            p = p.getPrevious();
        }
        if (floats.size() < count) {
            return null;
        }
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = floats.get(floats.size() - count + i);
        }
        return out;
    }

    @Nullable
    private static Float floatConstBefore(AbstractInsnNode call) {
        AbstractInsnNode p = call.getPrevious();
        for (int i = 0; i < 16 && p != null; i++) {
            Float f = asFloat(p);
            if (f != null) {
                return f;
            }
            p = p.getPrevious();
        }
        return null;
    }

    @Nullable
    private static String stringArgBefore(AbstractInsnNode call) {
        AbstractInsnNode p = call.getPrevious();
        for (int i = 0; i < 6 && p != null; i++) {
            if (p instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
                return s;
            }
            p = p.getPrevious();
        }
        return null;
    }

    @Nullable
    private static Object constantValue(AbstractInsnNode n) {
        if (n instanceof LdcInsnNode ldc) {
            return ldc.cst;
        }
        Integer i = asInt(n);
        if (i != null) {
            return i;
        }
        Float f = asFloat(n);
        if (f != null) {
            return f;
        }
        return null;
    }

    @Nullable
    private static Integer asInt(AbstractInsnNode n) {
        if (n == null) {
            return null;
        }
        return switch (n.getOpcode()) {
            case Opcodes.ICONST_M1 -> -1;
            case Opcodes.ICONST_0 -> 0;
            case Opcodes.ICONST_1 -> 1;
            case Opcodes.ICONST_2 -> 2;
            case Opcodes.ICONST_3 -> 3;
            case Opcodes.ICONST_4 -> 4;
            case Opcodes.ICONST_5 -> 5;
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> ((IntInsnNode) n).operand;
            default -> n instanceof LdcInsnNode ldc && ldc.cst instanceof Integer i ? i : null;
        };
    }

    @Nullable
    private static Float asFloat(AbstractInsnNode n) {
        if (n == null) {
            return null;
        }
        return switch (n.getOpcode()) {
            case Opcodes.FCONST_0 -> 0f;
            case Opcodes.FCONST_1 -> 1f;
            case Opcodes.FCONST_2 -> 2f;
            default -> {
                if (n instanceof LdcInsnNode ldc) {
                    if (ldc.cst instanceof Float f) {
                        yield f;
                    }
                    if (ldc.cst instanceof Double d) {
                        yield d.floatValue();
                    }
                    if (ldc.cst instanceof Integer i) {
                        yield i.floatValue();
                    }
                }
                yield null;
            }
        };
    }

    private static boolean asBool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Integer i) {
            return i != 0;
        }
        return false;
    }

    private static int asIntValue(Object o, int fallback) {
        if (o instanceof Integer i) {
            return i;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }

    private static float asFloatValue(Object o, float fallback) {
        if (o instanceof Number n) {
            return n.floatValue();
        }
        return fallback;
    }

    private static double asDoubleValue(Object o, double fallback) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return fallback;
    }

    @Nullable
    private static ClassNode readClass(@Nullable String className) {
        if (className == null || className.isEmpty() || "unknown".equals(className)) {
            return null;
        }
        String path = className.replace('.', '/') + ".class";
        ClassLoader cl = GunFpProbe.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            byte[] bytes = in.readAllBytes();
            ClassReader reader = new ClassReader(bytes);
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            return node;
        } catch (Exception e) {
            Coltan.LOGGER.debug("Gun FP probe could not read {}: {}", className, e.toString());
            ColtanDebug.once(ColtanDebug.Cat.GUN, "fp-probe-" + className,
                    "FP probe could not read %s: %s", className, e.toString());
            return null;
        }
    }

    @Nullable
    static String deriveModelClass(@Nullable String rendererClass) {
        if (rendererClass == null || rendererClass.isEmpty() || "unknown".equals(rendererClass)) {
            return null;
        }
        String s = rendererClass.replace(".client.renderer.gun.", ".client.model.item.");
        if (s.endsWith("ItemRenderer")) {
            return s.substring(0, s.length() - "ItemRenderer".length()) + "ItemModel";
        }
        if (s.endsWith("Renderer")) {
            return s.substring(0, s.length() - "Renderer".length()) + "ItemModel";
        }
        return s;
    }

    private static final class Scrape {
        final Map<Integer, GunBridgeProfile.Crosshair> crosshair = new LinkedHashMap<>();
        final Map<Integer, List<String>> zoomHide = new HashMap<>();
        final Map<Integer, GunBridgeProfile.AdsPose> scopeAds = new LinkedHashMap<>();
        @Nullable Float posX;
        @Nullable Float posY;
        @Nullable Float posZ;
        @Nullable Float scaleZ;
        @Nullable Float rootX;
        @Nullable Float rootY;
        @Nullable Float rootZ;
        @Nullable Float recoilX;
        @Nullable Float recoilY;
        @Nullable Float recoilZ;
        @Nullable Float recoilRotX;
        @Nullable Float recoilRotY;
        @Nullable Float recoilRotZ;
        @Nullable Float recoilZoomMul;
        @Nullable Float recoilSpeed;
    }
}
