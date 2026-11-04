package com.neoalive.coltan.client.compat.sbw.fx;

import com.atsuishio.superbwarfare.client.particle.CannonMuzzleFlareParticle;
import com.atsuishio.superbwarfare.client.particle.CustomCloudParticle;
import com.atsuishio.superbwarfare.client.particle.CustomFlareOption;
import com.atsuishio.superbwarfare.client.particle.CustomFlareParticle;
import com.atsuishio.superbwarfare.client.particle.CustomSmokeParticle;
import com.atsuishio.superbwarfare.client.particle.ExplosionDebrisParticle;
import com.atsuishio.superbwarfare.client.particle.FireStarParticle;
import com.atsuishio.superbwarfare.client.particle.RisingSmokeParticle;
import com.atsuishio.superbwarfare.client.particle.WhiteStarParticle;
import com.atsuishio.superbwarfare.client.renderer.ModParticleRenderTypes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.ColtanConfig;
import com.neoalive.coltan.client.compat.fx.ColtanFx;
import com.neoalive.coltan.client.compat.fx.FxRenderer;
import com.neoalive.coltan.client.compat.fx.OculusParticles;
import com.neoalive.coltan.debug.ColtanDebug;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Coltan's particle pipeline for Superb Warfare's own particle classes.
 *
 * <p>SBW constructs each particle as usual, so its random start state is exactly SBW's. The add
 * hook then copies that state into a {@link FxPool} and drops the object. Each tick runs a line by
 * line port of Particle.tick and of the SBW class's tick on the arrays, and each frame writes one
 * instance record per visible particle for {@link FxRenderer}. Anything unexpected turns the
 * pipeline off for the session and new particles go back to vanilla.
 */
public final class SbwFx implements ColtanFx.Hooks {
    static final int FLARE = 1;
    static final int CLOUD = 2;
    static final int SMOKE = 3;
    static final int MUZZLE = 4;
    static final int DEBRIS = 5;
    static final int FIRE_STAR = 6;
    static final int WHITE_STAR = 7;
    static final int RISING = 8;

    static final String[] KIND_IDS = {
        "",
        "superbwarfare:custom_flare",
        "superbwarfare:custom_cloud",
        "superbwarfare:custom_smoke",
        "superbwarfare:cannon_muzzle_flare",
        "superbwarfare:explosion_debris",
        "superbwarfare:fire_star",
        "superbwarfare:white_star",
        "superbwarfare:rising_smoke"
    };

    private static final int FULL_BRIGHT = 15728880;

    static SbwFx instance;

    private final Map<Class<?>, Integer> kinds = new IdentityHashMap<>();
    private final Map<Class<?>, Field[]> own = new IdentityHashMap<>();
    private final boolean[] excluded = new boolean[KIND_IDS.length];
    private final FxFields fields = new FxFields();
    private final FxCollide collide = new FxCollide();
    private final List<Particle> pending = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private final BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();
    private final Matrix4f viewProjection = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final int[] counts = new int[FxRenderer.MATERIALS];
    private final boolean[] enabled = new boolean[FxRenderer.MATERIALS];
    final FxParity parity = new FxParity(this);

    FxPool[] pools = newPools();
    private int poolSize = ColtanConfig.particleMaxPerMaterial;
    private long nextSerial = 1;
    private int ticks;
    private boolean failed;
    private boolean warnedOldBridge;

    private SbwFx() {
        kinds.put(CustomFlareParticle.class, FLARE);
        kinds.put(CustomCloudParticle.class, CLOUD);
        kinds.put(CustomSmokeParticle.class, SMOKE);
        kinds.put(CannonMuzzleFlareParticle.class, MUZZLE);
        kinds.put(ExplosionDebrisParticle.class, DEBRIS);
        kinds.put(FireStarParticle.class, FIRE_STAR);
        kinds.put(WhiteStarParticle.class, WHITE_STAR);
        kinds.put(RisingSmokeParticle.class, RISING);
        // spriteSet, then p0 to p5, i0, i1, f0, f1 as each kind stores them.
        own.put(
                CustomFlareParticle.class,
                ownFields(
                        CustomFlareParticle.class,
                        "spriteSet",
                        "fade",
                        "sizeAdd",
                        "targetR",
                        "targetG",
                        "targetB",
                        null,
                        "animationSpeed",
                        null,
                        null,
                        null));
        own.put(
                CustomCloudParticle.class,
                ownFields(
                        CustomCloudParticle.class,
                        "spriteSet",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "cooldown",
                        "light"));
        own.put(
                CustomSmokeParticle.class,
                ownFields(
                        CustomSmokeParticle.class,
                        "spriteSet",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
        own.put(
                CannonMuzzleFlareParticle.class,
                ownFields(
                        CannonMuzzleFlareParticle.class,
                        "spriteSet",
                        "fade",
                        "sizeAdd",
                        null,
                        null,
                        null,
                        null,
                        "animationSpeed",
                        null,
                        null,
                        null));
        own.put(
                ExplosionDebrisParticle.class,
                ownFields(
                        ExplosionDebrisParticle.class,
                        "spriteSet",
                        "fade",
                        "sizeAdd",
                        "targetR",
                        "targetG",
                        "targetB",
                        "size",
                        "animationSpeed",
                        "life",
                        null,
                        null));
        own.put(
                FireStarParticle.class,
                ownFields(
                        FireStarParticle.class,
                        "spriteSet",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
        own.put(
                WhiteStarParticle.class,
                ownFields(
                        WhiteStarParticle.class,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
        own.put(
                RisingSmokeParticle.class,
                ownFields(
                        RisingSmokeParticle.class,
                        "spriteSet",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    /** Called from the SBW reflective boot. */
    public static void init() {
        try {
            instance = new SbwFx();
        } catch (Throwable t) {
            Coltan.LOGGER.warn("Coltan particle pipeline unavailable for this SBW build", t);
            return;
        }
        instance.loadExcludes();
        ColtanFx.install(instance);
        FxParity.register();
        Coltan.LOGGER.info(
                "Coltan particle pipeline ready (particles.sbwInstancedParticles={})",
                ColtanConfig.sbwInstancedParticles);
    }

    /** Re-reads the opt-out list. Called on resource reload. */
    public static void reloadExcludes() {
        if (instance != null) {
            instance.loadExcludes();
        }
    }

    private static Field[] ownFields(Class<?> owner, String... names) {
        Field[] out = new Field[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = names[i] == null ? null : FxFields.own(owner, names[i]);
        }
        return out;
    }

    private static FxPool[] newPools() {
        FxPool[] out = new FxPool[FxRenderer.MATERIALS];
        for (int m = 0; m < out.length; m++) {
            out[m] = new FxPool(ColtanConfig.particleMaxPerMaterial);
        }
        return out;
    }

    private void loadExcludes() {
        java.util.Arrays.fill(excluded, false);
        Set<String> ids = new HashSet<>();
        ResourceLocation id = new ResourceLocation("coltan", "sbw_particle_bridge/_exclude.json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isPresent()) {
            try (var in = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(in).getAsJsonObject();
                if (root.has("exclude")) {
                    for (JsonElement element : root.getAsJsonArray("exclude")) {
                        ids.add(element.getAsString());
                    }
                }
            } catch (Exception e) {
                Coltan.LOGGER.warn("Could not read {}", id, e);
            }
        }
        for (int k = 1; k < KIND_IDS.length; k++) {
            excluded[k] = ids.contains(KIND_IDS[k]);
        }
        ColtanDebug.log(ColtanDebug.Cat.FX, "fx excludes: %s", ids);
    }

    boolean claiming() {
        if (failed || !ColtanConfig.sbwInstancedParticles) {
            return false;
        }
        if (ColtanConfig.gemrenderParticles) {
            if (!warnedOldBridge) {
                warnedOldBridge = true;
                Coltan.LOGGER.warn(
                        "particles.sbwInstancedParticles is off while"
                                + " experimental.gemrenderParticles is on; they cannot run together");
            }
            return false;
        }
        return FxRenderer.available() && !OculusParticles.shaderPackInUse();
    }

    // Hooks

    @Override
    public boolean capture(Particle particle) {
        Integer kind = kinds.get(particle.getClass());
        if (kind == null || excluded[kind] || !claiming()) {
            return false;
        }
        if (particle.getParticleGroup().isPresent() || material(particle) < 0) {
            return false;
        }
        pending.add(particle);
        return true;
    }

    @Override
    public void tick() {
        if (failed) {
            return;
        }
        try {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                clear();
                return;
            }
            if (poolSize != ColtanConfig.particleMaxPerMaterial && empty()) {
                pools = newPools();
                poolSize = ColtanConfig.particleMaxPerMaterial;
            }
            ticks++;
            collide.forgetChunks();
            for (FxPool pool : pools) {
                for (int i = 0; i < pool.count; i++) {
                    tickSlot(pool, pool.slot(i), level);
                }
                pool.compact();
            }
            parity.tick(level);
            flush(level);
        } catch (Throwable t) {
            fail("tick", t);
        }
    }

    @Override
    public void render(Camera camera, float partialTick, int oculusPhase) {
        if (failed || empty()) {
            return;
        }
        try {
            if (OculusParticles.shaderPackInUse() || !FxRenderer.available()) {
                clear();
                return;
            }
            boolean all = oculusPhase == OculusParticles.EVERYTHING;
            boolean opaquePass = all || oculusPhase == OculusParticles.OPAQUE;
            boolean translucentPass = all || oculusPhase == OculusParticles.TRANSLUCENT;
            enabled[FxRenderer.OPAQUE] = opaquePass;
            enabled[FxRenderer.LIT] = opaquePass;
            enabled[FxRenderer.TRANSLUCENT] = translucentPass;
            // Without the single draw fix, mod types are drawn in both Oculus passes, as before.
            enabled[FxRenderer.SOFT] =
                    translucentPass || (opaquePass && !ColtanConfig.oculusSingleDraw);
            frustum.set(FxRenderer.viewProjection(viewProjection));
            Vec3 cam = camera.getPosition();
            boolean any = false;
            for (int m = 0; m < FxRenderer.MATERIALS; m++) {
                counts[m] = enabled[m] ? fill(pools[m], m, cam, partialTick) : 0;
                any |= counts[m] > 0;
            }
            if (any) {
                FxRenderer.draw(camera, counts, enabled);
            }
        } catch (Throwable t) {
            fail("render", t);
        }
    }

    @Override
    public void clear() {
        pending.clear();
        for (FxPool pool : pools) {
            pool.clear();
        }
        parity.clear();
    }

    boolean empty() {
        for (FxPool pool : pools) {
            if (pool.count > 0) {
                return false;
            }
        }
        return pending.isEmpty();
    }

    int pending() {
        return pending.size();
    }

    boolean failed() {
        return failed;
    }

    private void fail(String where, Throwable t) {
        failed = true;
        clear();
        Coltan.LOGGER.error(
                "Coltan particle pipeline failed in {}; vanilla particles for the rest of the session",
                where,
                t);
        ColtanDebug.failOnce("fx-" + where, "fx pipeline off after %s: %s", where, t);
    }

    // Spawning

    private int material(Particle particle) {
        ParticleRenderType type = particle.getRenderType();
        if (type == ModParticleRenderTypes.INSTANCE.getPARTICLE_SHEET_SOFT_TRANSLUCENT()) {
            return FxRenderer.SOFT;
        }
        if (type == ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT) {
            return FxRenderer.TRANSLUCENT;
        }
        if (type == ParticleRenderType.PARTICLE_SHEET_LIT) {
            return FxRenderer.LIT;
        }
        if (type == ParticleRenderType.PARTICLE_SHEET_OPAQUE) {
            return FxRenderer.OPAQUE;
        }
        return -1;
    }

    /** Vanilla moves pending particles in at the end of its tick, in add order. So do we. */
    private void flush(ClientLevel level) throws ReflectiveOperationException {
        if (pending.isEmpty()) {
            return;
        }
        // Spawns made while capturing (none today) land in the next tick, as in vanilla.
        Particle[] batch = pending.toArray(new Particle[0]);
        pending.clear();
        for (Particle particle : batch) {
            int material = material(particle);
            FxPool pool = pools[material];
            int s = pool.push();
            captureInto(particle, pool, s, level);
            parity.track(particle, material, pool.serial[s]);
        }
    }

    private void captureInto(Particle p, FxPool pool, int s, ClientLevel level)
            throws ReflectiveOperationException {
        FxFields f = fields;
        AABB bb = p.getBoundingBox();
        pool.minX[s] = bb.minX;
        pool.minY[s] = bb.minY;
        pool.minZ[s] = bb.minZ;
        pool.maxX[s] = bb.maxX;
        pool.maxY[s] = bb.maxY;
        pool.maxZ[s] = bb.maxZ;
        pool.xo[s] = f.xo.getDouble(p);
        pool.yo[s] = f.yo.getDouble(p);
        pool.zo[s] = f.zo.getDouble(p);
        pool.x[s] = f.x.getDouble(p);
        pool.y[s] = f.y.getDouble(p);
        pool.z[s] = f.z.getDouble(p);
        pool.xd[s] = f.xd.getDouble(p);
        pool.yd[s] = f.yd.getDouble(p);
        pool.zd[s] = f.zd.getDouble(p);
        pool.onGround[s] = f.onGround.getBoolean(p);
        pool.hasPhysics[s] = f.hasPhysics.getBoolean(p);
        pool.stopped[s] = f.stopped.getBoolean(p);
        pool.removed[s] = f.removed.getBoolean(p);
        pool.bbWidth[s] = f.bbWidth.getFloat(p);
        pool.bbHeight[s] = f.bbHeight.getFloat(p);
        pool.age[s] = f.age.getInt(p);
        pool.lifetime[s] = f.lifetime.getInt(p);
        pool.gravity[s] = f.gravity.getFloat(p);
        pool.friction[s] = f.friction.getFloat(p);
        pool.r[s] = f.rCol.getFloat(p);
        pool.g[s] = f.gCol.getFloat(p);
        pool.b[s] = f.bCol.getFloat(p);
        pool.alpha[s] = f.alpha.getFloat(p);
        pool.roll[s] = f.roll.getFloat(p);
        pool.oRoll[s] = f.oRoll.getFloat(p);
        pool.quadSize[s] = f.quadSize.getFloat(p);
        pool.sprite[s] = f.sprite.get(p);
        int kind = kinds.get(p.getClass());
        pool.kind[s] = kind;
        Field[] k = own.get(p.getClass());
        pool.spriteSet[s] = k[0] == null ? null : k[0].get(p);
        pool.p0[s] = k[1] == null ? 0f : k[1].getFloat(p);
        pool.p1[s] = k[2] == null ? 0f : k[2].getFloat(p);
        pool.p2[s] = k[3] == null ? 0f : k[3].getFloat(p);
        pool.p3[s] = k[4] == null ? 0f : k[4].getFloat(p);
        pool.p4[s] = k[5] == null ? 0f : k[5].getFloat(p);
        pool.p5[s] = k[6] == null ? 0f : k[6].getFloat(p);
        pool.i0[s] = k[7] == null ? 0 : k[7].getInt(p);
        pool.i1[s] = k[8] == null ? 0 : k[8].getInt(p);
        pool.f0[s] = k[9] != null && k[9].getBoolean(p);
        pool.f1[s] = k[10] != null && k[10].getBoolean(p);
        pool.serial[s] = nextSerial++;
        pool.light[s] = light(pool, s, level);
        pool.lightAge[s] = ticks;
    }

    // Tick: Particle.tick, then the SBW class's own tick, in the same order

    private void tickSlot(FxPool pool, int s, ClientLevel level) {
        pool.xo[s] = pool.x[s];
        pool.yo[s] = pool.y[s];
        pool.zo[s] = pool.z[s];
        if (pool.age[s]++ >= pool.lifetime[s]) {
            pool.removed[s] = true;
        } else {
            pool.yd[s] = pool.yd[s] - 0.04D * pool.gravity[s];
            move(pool, s, level, pool.xd[s], pool.yd[s], pool.zd[s]);
            pool.xd[s] = pool.xd[s] * pool.friction[s];
            pool.yd[s] = pool.yd[s] * pool.friction[s];
            pool.zd[s] = pool.zd[s] * pool.friction[s];
            if (pool.onGround[s]) {
                pool.xd[s] *= 0.7F;
                pool.zd[s] *= 0.7F;
            }
        }
        switch (pool.kind[s]) {
            case FLARE -> tickFlare(pool, s, level);
            case CLOUD -> tickCloud(pool, s);
            case SMOKE -> tickSmoke(pool, s);
            case MUZZLE -> tickMuzzle(pool, s);
            case DEBRIS -> tickDebris(pool, s, level);
            case FIRE_STAR -> {
                if (!pool.removed[s]) {
                    sprite(pool, s, pool.age[s] / 2 % 8 + 1, 8);
                }
            }
            case WHITE_STAR -> pool.quadSize[s] *= 0.95F;
            case RISING -> {
                pool.alpha[s] *= 0.98F;
                if (!pool.removed[s]) {
                    sprite(pool, s, Math.min(pool.age[s] / 2 + 1, 8), 8);
                }
            }
            default -> {}
        }
        int refresh = ColtanConfig.particleLightRefreshTicks;
        if (refresh <= 1 || ticks - pool.lightAge[s] >= refresh) {
            pool.light[s] = light(pool, s, level);
            pool.lightAge[s] = ticks;
        }
    }

    private void move(FxPool pool, int s, ClientLevel level, double dx, double dy, double dz) {
        if (pool.stopped[s]) {
            return;
        }
        double ox = dx;
        double oy = dy;
        double oz = dz;
        if (pool.hasPhysics[s]
                && (dx != 0.0D || dy != 0.0D || dz != 0.0D)
                && dx * dx + dy * dy + dz * dz < 10000.0D) {
            AABB box =
                    new AABB(
                            pool.minX[s],
                            pool.minY[s],
                            pool.minZ[s],
                            pool.maxX[s],
                            pool.maxY[s],
                            pool.maxZ[s]);
            collide.collide(level, box, dx, dy, dz);
            dx = collide.rx;
            dy = collide.ry;
            dz = collide.rz;
        }
        if (dx != 0.0D || dy != 0.0D || dz != 0.0D) {
            pool.minX[s] += dx;
            pool.minY[s] += dy;
            pool.minZ[s] += dz;
            pool.maxX[s] += dx;
            pool.maxY[s] += dy;
            pool.maxZ[s] += dz;
            pool.x[s] = (pool.minX[s] + pool.maxX[s]) / 2.0D;
            pool.y[s] = pool.minY[s];
            pool.z[s] = (pool.minZ[s] + pool.maxZ[s]) / 2.0D;
        }
        if (Math.abs(oy) >= 1.0E-5F && Math.abs(dy) < 1.0E-5F) {
            pool.stopped[s] = true;
        }
        pool.onGround[s] = oy != dy && oy < 0.0D;
        if (ox != dx) {
            pool.xd[s] = 0.0D;
        }
        if (oz != dz) {
            pool.zd[s] = 0.0D;
        }
    }

    private static void sprite(FxPool pool, int s, int index, int of) {
        Object set = pool.spriteSet[s];
        if (set != null) {
            pool.sprite[s] = ((SpriteSet) set).get(index, of);
        }
    }

    private static int animated(FxPool pool, int s) {
        int speed = pool.i0[s];
        // SBW divides by animationSpeed and would throw on 0; hold the first frame instead.
        int frame = speed == 0 ? 1 : pool.age[s] / speed % 12 + 1;
        return Mth.clamp(frame, 0, 12);
    }

    private void tickFlare(FxPool pool, int s, ClientLevel level) {
        if (!pool.removed[s]) {
            sprite(pool, s, animated(pool, s), 12);
        }
        pool.quadSize[s] += pool.p1[s];
        pool.alpha[s] *= pool.p0[s];
        pool.r[s] = Mth.lerp(0.1F, pool.r[s], pool.p2[s]);
        pool.g[s] = Mth.lerp(0.15F, pool.g[s], pool.p3[s]);
        pool.b[s] = Mth.lerp(0.2F, pool.b[s], pool.p4[s]);
        if (pool.y[s] > (double) (level.getMaxBuildHeight() * 2)) {
            pool.removed[s] = true;
        }
    }

    private static void tickCloud(FxPool pool, int s) {
        if (pool.f0[s]) {
            pool.r[s] *= 0.985F;
            pool.g[s] *= 0.985F;
            pool.b[s] *= 0.985F;
        }
        if (!pool.removed[s]) {
            sprite(pool, s, Math.min(pool.age[s] / 8 + 1, 8), 8);
        }
        int before = pool.age[s]++;
        if (before < pool.lifetime[s] && !(pool.alpha[s] <= 0.0F)) {
            pool.alpha[s] = 1.0F - (float) pool.age[s] / (float) pool.lifetime[s];
        } else {
            pool.removed[s] = true;
        }
        pool.xd[s] *= 0.85D;
        pool.yd[s] *= 0.85D;
        pool.zd[s] *= 0.85D;
    }

    private static void tickSmoke(FxPool pool, int s) {
        if (!pool.removed[s]) {
            sprite(pool, s, Math.min(pool.age[s] / 8 + 1, 8), 8);
        }
        int before = pool.age[s]++;
        if (before < pool.lifetime[s] && !(pool.alpha[s] <= 0.0F)) {
            if (pool.age[s] >= pool.lifetime[s] - 60 && pool.alpha[s] > 0.01F) {
                pool.alpha[s] -= 0.015F;
            }
        } else {
            pool.removed[s] = true;
        }
    }

    private static void tickMuzzle(FxPool pool, int s) {
        if (!pool.removed[s]) {
            sprite(pool, s, animated(pool, s), 12);
        }
        pool.quadSize[s] += pool.p1[s];
        pool.alpha[s] *= pool.p0[s];
        pool.r[s] *= 0.93F;
        pool.g[s] *= 0.93F;
        pool.b[s] *= 0.93F;
    }

    private void tickDebris(FxPool pool, int s, ClientLevel level) {
        if (!pool.removed[s]) {
            sprite(pool, s, pool.age[s] / 2 % 8 + 1, 8);
        }
        if (pool.onGround[s]) {
            pool.removed[s] = true;
            return;
        }
        pool.p2[s] *= 0.92F;
        pool.p3[s] *= 0.92F;
        pool.p4[s] *= 0.92F;
        pool.p0[s] *= 0.994F;
        pool.p5[s] *= 0.99F;
        pool.quadSize[s] *= 0.99F;
        Vec3 velocity = new Vec3(pool.xd[s], pool.yd[s], pool.zd[s]);
        double length = velocity.length();
        float size = pool.p5[s];
        for (double i = 0.0D; i < length; i += (double) (size * (float) 3)) {
            Vec3 start =
                    new Vec3(
                            pool.xo[s],
                            pool.yo[s] + (double) (pool.bbHeight[s] / (float) 2),
                            pool.zo[s]);
            Vec3 at = start.add(velocity.normalize().scale(-i));
            float offset = (float) 2 * (random.nextFloat() - 0.5F);
            level.addParticle(
                    new CustomFlareOption(
                            pool.p2[s],
                            pool.p3[s],
                            pool.p4[s],
                            pool.i1[s],
                            pool.p0[s],
                            pool.i0[s],
                            pool.p1[s],
                            size),
                    at.x + (double) offset * 0.05D,
                    at.y + (double) offset * 0.05D,
                    at.z + (double) offset * 0.05D,
                    0.0D,
                    0.0D,
                    0.0D);
        }
        pool.xd[s] *= 0.96D;
        pool.yd[s] *= 0.96D;
        pool.zd[s] *= 0.99D;
    }

    // Light, as each class's getLightColor computes it

    int light(FxPool pool, int s, ClientLevel level) {
        switch (pool.kind[s]) {
            case MUZZLE, DEBRIS, FIRE_STAR, WHITE_STAR:
                return FULL_BRIGHT;
            case FLARE:
                return flareLight(pool, s, level);
            case CLOUD:
                if (pool.f1[s]) {
                    return FULL_BRIGHT;
                }
                lightPos.set(
                        Mth.floor(pool.x[s]),
                        Mth.floor(pool.y[s] + (double) 1),
                        Mth.floor(pool.z[s]));
                return level.hasChunkAt(lightPos)
                        ? LevelRenderer.getLightColor(level, lightPos)
                        : 0;
            default:
                lightPos.set(Mth.floor(pool.x[s]), Mth.floor(pool.y[s]), Mth.floor(pool.z[s]));
                return level.hasChunkAt(lightPos)
                        ? LevelRenderer.getLightColor(level, lightPos)
                        : 0;
        }
    }

    private int flareLight(FxPool pool, int s, ClientLevel level) {
        lightPos.set(Mth.floor(pool.x[s]), Mth.floor(pool.y[s]), Mth.floor(pool.z[s]));
        int block;
        int sky;
        if (level.getChunk(lightPos) != null && pool.y[s] <= (double) level.getMaxBuildHeight()) {
            block = level.getBrightness(LightLayer.BLOCK, lightPos);
            sky = level.getBrightness(LightLayer.SKY, lightPos);
        } else {
            block = 0;
            sky = 15;
        }
        float a = pool.alpha[s];
        int blockLight = Mth.clamp((int) Mth.lerp(a, (float) block, 15.0F), 0, 15);
        int skyLight = Mth.clamp((int) Mth.lerp(a, (float) sky, 15.0F), 0, 15);
        return blockLight << 4 | skyLight << 20;
    }

    // Frame fill

    private int fill(FxPool pool, int material, Vec3 cam, float partialTick) {
        if (pool.count == 0) {
            return 0;
        }
        long base = FxRenderer.reserve(material, pool.count);
        int written = 0;
        for (int i = 0; i < pool.count; i++) {
            int s = pool.slot(i);
            Object spriteObject = pool.sprite[s];
            if (!(spriteObject instanceof TextureAtlasSprite sprite)) {
                continue;
            }
            float px = (float) (Mth.lerp((double) partialTick, pool.xo[s], pool.x[s]) - cam.x);
            float py = (float) (Mth.lerp((double) partialTick, pool.yo[s], pool.y[s]) - cam.y);
            float pz = (float) (Mth.lerp((double) partialTick, pool.zo[s], pool.z[s]) - cam.z);
            float size = pool.quadSize[s];
            // Corners reach size * sqrt(2) from the centre; anything outside is off screen.
            if (!frustum.testSphere(px, py, pz, Math.abs(size) * 1.4143f)) {
                continue;
            }
            float roll =
                    pool.roll[s] == 0.0F
                            ? 0.0F
                            : Mth.lerp(partialTick, pool.oRoll[s], pool.roll[s]);
            long at = base + (long) written * FxRenderer.STRIDE;
            MemoryUtil.memPutFloat(at + FxRenderer.OFF_POS, px);
            MemoryUtil.memPutFloat(at + FxRenderer.OFF_POS + 4, py);
            MemoryUtil.memPutFloat(at + FxRenderer.OFF_POS + 8, pz);
            MemoryUtil.memPutFloat(at + FxRenderer.OFF_SIZE, size);
            MemoryUtil.memPutFloat(at + FxRenderer.OFF_ROLL, roll);
            MemoryUtil.memPutFloat(at + FxRenderer.OFF_UV, sprite.getU0());
            MemoryUtil.memPutFloat(at + FxRenderer.OFF_UV + 4, sprite.getV0());
            MemoryUtil.memPutFloat(at + FxRenderer.OFF_UV + 8, sprite.getU1());
            MemoryUtil.memPutFloat(at + FxRenderer.OFF_UV + 12, sprite.getV1());
            // BufferBuilder.color(float...) writes (byte) (int) (c * 255).
            MemoryUtil.memPutByte(at + FxRenderer.OFF_COLOR, (byte) (int) (pool.r[s] * 255.0F));
            MemoryUtil.memPutByte(at + FxRenderer.OFF_COLOR + 1, (byte) (int) (pool.g[s] * 255.0F));
            MemoryUtil.memPutByte(at + FxRenderer.OFF_COLOR + 2, (byte) (int) (pool.b[s] * 255.0F));
            MemoryUtil.memPutByte(
                    at + FxRenderer.OFF_COLOR + 3, (byte) (int) (pool.alpha[s] * 255.0F));
            int light = pool.light[s];
            MemoryUtil.memPutShort(at + FxRenderer.OFF_LIGHT, (short) (light & 0xFFFF));
            MemoryUtil.memPutShort(at + FxRenderer.OFF_LIGHT + 2, (short) (light >>> 16 & 0xFFFF));
            written++;
        }
        return written;
    }

    @Nullable
    FxPool pool(int material) {
        return material >= 0 && material < pools.length ? pools[material] : null;
    }

    FxFields fields() {
        return fields;
    }

    int count(int material) {
        return pools[material].count;
    }
}
