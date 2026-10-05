package com.neoalive.coltan.client.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.atsuishio.superbwarfare.api.event.ClientVehicleFireEvent;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.vehicle.subdata.SeatInfo;
import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.ColtanConfig;
import com.neoalive.coltan.client.compat.bridge.BoneInference;
import com.neoalive.coltan.client.compat.bridge.BridgeOverride;
import com.neoalive.coltan.client.compat.bridge.LodEntry;
import com.neoalive.coltan.client.compat.bridge.VehicleBridgeCache;
import com.neoalive.coltan.client.compat.bridge.VehicleBridgeProfile;
import com.neoalive.coltan.client.compat.bridge.VehicleRenderMode;
import com.neoalive.coltan.debug.ColtanDebug;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderPartsModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeRotation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PartsPose;
import com.wf.gemrender.gltf.PoseDriver;
import com.wf.gemrender.render.PoseCache;
import com.wf.gemrender.render.PoseLod;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.ComponentEntityVisual;
import dev.engine_room.flywheel.lib.visual.component.ShadowComponent;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

/**
 * GemRender parts visual driven by a vehicle bridge profile.
 * 
 */
public final class SbwVehicleGemVisual extends ComponentEntityVisual<VehicleEntity> {
    private static final float WHEEL_FACTOR = 1.5f;
    private static final Pattern TRACK_MOV = Pattern.compile("^trackMov([LR])(\\d+)$");
    private static final Pattern TRACK_ROT = Pattern.compile("^trackRot([LR])(\\d+)$");
    private static final Pattern WHEEL_L = Pattern.compile("^wheelL.*$|^w_[lL].*$");
    private static final Pattern WHEEL_R = Pattern.compile("^wheelR.*$|^w_[rR].*$");
    /** Turret, barrel, wheels, tracks, pws, propeller, rudder, control, drone wings, mortar bipod, hull recoil. */
    private static final int FIXED_LAYERS = 14;
    private static final String[] DRONE_WINGS = {"wingFL", "wingFR", "wingBL", "wingBR"};
    /** ~30% brightness, matching SBW's TextureBrightnessHandler.getBrightenedTexture(texture, 0.3f) for wrecks. */
    private static final int WRECK_TINT = 0xFF4D4D4D;

    private static final Map<Integer, FireTimes> FIRE = new ConcurrentHashMap<>();
    /** Types whose replay failure was already logged with a stack trace. */
    private static final Set<EntityType<?>> REPLAY_WARNED = ConcurrentHashMap.newKeySet();

    static {
        MinecraftForge.EVENT_BUS.addListener(SbwVehicleGemVisual::onVehicleFire);
        MinecraftForge.EVENT_BUS.addListener(SbwVehicleFlare::onRenderLevel);
    }

    /** LOD tier switches only once the camera is this far past a threshold (min 4 blocks). */
    private static final double LOD_HYSTERESIS = 0.05;

    private final FireTimes fireTimes;
    private VehicleBridgeProfile profile;
    /** REPLAY-mode pose source, bound per model; null in NATIVE mode or after a replay failure. */
    private SbwVehicleReplay replay;
    private boolean replayFailed;
    /** Mesh being drawn; a LOD / skin target waits in {@code pending*} until its import is resident. */
    private ModelCache.Handle<GemRenderPartsModel> handle;
    private int activeLod = -1;
    private double lastDistanceSq;
    private ResourceLocation boundTexture;
    private ModelCache.Handle<GemRenderPartsModel> pendingHandle;
    private int pendingLod;
    private ResourceLocation pendingTexture;

    private TransformedInstance[] instances = new TransformedInstance[0];
    private Matrix4f[] transforms = new Matrix4f[0];
    private final Matrix4f base = new Matrix4f();
    /** {@link #base} with the entity's absolute position (base is relative to Flywheel's render origin). */
    private final Matrix4f worldBase = new Matrix4f();
    private final Matrix4f composed = new Matrix4f();
    /** Parts cut from {@code flare*} bones; drawn by {@link SbwVehicleFlare}. */
    private int[] flareParts = new int[0];
    private final PartsPose.Scratch scratch = new PartsPose.Scratch();

    private GltfAnimation[] clips = new GltfAnimation[0];
    private float[] times = new float[0];
    private int[] lastBucket = new int[0];
    private GltfAnimation[] lastClips = new GltfAnimation[0];
    private boolean[][] layerMasks = new boolean[0][];
    private boolean[] changed = new boolean[0];
    private boolean[] partHidden = new boolean[0];
    /** Per-part constants resolved at bind: never-drawn bones get no instance at all. */
    private boolean[] neverDrawPart = new boolean[0];
    private boolean[] hullHidePart = new boolean[0];
    private int mortarMonitorPart = -1;

    private float lastAtX = Float.NaN;
    private float lastAtY;
    private float lastAtZ;
    private float lastYaw;
    private float lastPitch;
    private float lastRoll;
    private float lastPivotY;
    private float lastScale;
    private int lastLight = Integer.MIN_VALUE;
    private boolean lastZoomSight;
    private boolean lastHideHull;
    private boolean lastMortarMonitorHidden;
    private boolean lastSympatheticWreck;
    private boolean lastIsWreck;
    private boolean forceFullDirty = true;
    /** Parts driven by the turret bone (turret + everything mounted on it); hidden once it flies off as a wreck. */
    private boolean[] turretMask = new boolean[0];

    private GemRenderPartsModel model;
    private GltfAnimation turretClip;
    private GltfAnimation barrelClip;
    private GltfAnimation leftWheelClip;
    private GltfAnimation rightWheelClip;
    private GltfAnimation leftTrackClip;
    private GltfAnimation rightTrackClip;
    private GltfAnimation passengerYawClip;
    private GltfAnimation passengerPitchClip;
    private GltfAnimation propellerClip;
    private GltfAnimation rudderClip;
    private GltfAnimation controlClip;
    private GltfAnimation droneWingClip;
    private GltfAnimation mortarBipodClip;
    private GltfAnimation baseRecoilClip;
    private BaseRecoil baseRecoil;
    private final List<FireLayer> fireLayers = new ArrayList<>();
    /** Ambient clips (radar dish etc.) looped while the vehicle is powered. */
    private final List<GltfAnimation> loopLayers = new ArrayList<>();
    /** Clip pairs gated by an addon-entity boolean getter (e.g. a VLS hatch), see bridge StateClip. */
    private final List<StateLayer> stateLayers = new ArrayList<>();
    /** One yaw+pitch layer pair per seat whose weapons declare BoundBones/Yaw/Pitch. */
    private final List<SeatAimLayer> seatLayers = new ArrayList<>();
    /** Seconds of powered time; frozen while unpowered so a dish stops where it is instead of snapping. */
    private float loopClock;
    private float lastLoopNow = Float.NaN;

    public SbwVehicleGemVisual(VisualizationContext ctx, VehicleEntity entity, float partialTick) {
        super(ctx, entity, partialTick);
        this.fireTimes = FIRE.computeIfAbsent(entity.getId(), id -> new FireTimes());
        this.profile = VehicleBridgeCache.profile(entity.getType());
        this.handle = profile == null ? null : VehicleBridgeCache.handle(profile, 0);
        // Scale to the vehicle's own footprint instead of a fixed radius — 1.8f was BMP-2's
        // half-width (3.6 wide / 2) baked in, which drew a comically oversized shadow under the
        // 0.8-wide mortar. Passthrough vehicles keep their own renderer, which draws its own shadow.
        if (profile == null || profile.renderMode() != VehicleRenderMode.PASSTHROUGH) {
            addComponent(new ShadowComponent(ctx, entity).radius(entity.getBbWidth() * 0.5f));
        }
    }

    private static void onVehicleFire(ClientVehicleFireEvent event) {
        VehicleEntity vehicle = event.getVehicle();
        if (VehicleBridgeCache.profile(vehicle.getType()) == null) {
            return;
        }
        String name = event.getWeaponName();
        if (name == null) {
            name = vehicle.getGunName(vehicle.getSeatIndex(event.getShooter()));
        }
        if (name == null) {
            return;
        }
        FireTimes times = FIRE.computeIfAbsent(vehicle.getId(), id -> new FireTimes());
        times.starts.put(camelToSnake(name), (float) vehicle.tickCount);
    }

    private static String camelToSnake(String name) {
        StringBuilder out = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                out.append('_');
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    @Override
    public void beginFrame(Context ctx) {
        super.beginFrame(ctx);
        VehicleBridgeProfile latest = VehicleBridgeCache.profile(entity.getType());
        if (latest != profile) {
            profile = latest;
            activeLod = -1;
            boundTexture = null;
            handle = null;
            pendingHandle = null;
            pendingTexture = null;
            deleteInstances();
            forceFullDirty = true;
        }
        if (profile == null) {
            if (ColtanDebug.any()) {
                ColtanDebug.failOnce("vehicle-visual-no-profile-" + entity.getType(),
                        "SbwVehicleGemVisual has no profile for %s", entity.getType());
            }
            return;
        }
        if (profile.renderMode() == VehicleRenderMode.PASSTHROUGH) {
            // Drawn by the vehicle's own renderer (skipVanillaRender declines it).
            if (instances.length > 0) {
                deleteInstances();
            }
            return;
        }

        float partialTick = ctx.partialTick();
        updateLodAndSkin(partialTick);
        if (handle == null) {
            if (ColtanDebug.any()) {
                ColtanDebug.failOnce("vehicle-visual-no-handle-" + entity.getType(),
                        "SbwVehicleGemVisual has no ModelCache handle for %s (lod=%d)",
                        entity.getType(), activeLod);
            }
            return;
        }
        // Off-screen hulls skip pose work; dirty tracking catches up on the next drawn frame.
        if (!isVisible(ctx.frustum())) {
            return;
        }
        // Opt-in (coltan-common.toml): far hulls skip frames and visibly trail their entity. Flares
        // (≤ SbwVehicleFlare range) read transforms every frame, so never throttle those.
        if (ColtanConfig.vehicleDistanceThrottle && !forceFullDirty && lastDistanceSq > SbwVehicleFlare.MAX_DISTANCE_SQ && !isPlayerVehicle()
                && !ctx.limiter().shouldUpdate(lastDistanceSq)) {
            return;
        }
        if (!acquire()) {
            // Handle.get() is null while the async import runs — that is the protocol, not a failure.
            if (!ColtanDebug.any()) {
                return;
            }
            if (handle.hasFailed()) {
                ColtanDebug.failOnce("vehicle-visual-load-failed-" + entity.getType(),
                        "SbwVehicleGemVisual model load failed for %s (lod=%d id=%s)",
                        entity.getType(), activeLod, handle.id());
            } else {
                ColtanDebug.once(ColtanDebug.Cat.CACHE, "vehicle-mesh-wait-" + entity.getType(),
                        "SbwVehicleGemVisual waiting for mesh %s (lod=%d loading=%s)",
                        entity.getType(), activeLod, handle.isLoading());
            }
            return;
        }

        boolean baseDirty = writeBase(partialTick);
        boolean layersDirty = replay != null ? replayLayers(partialTick) : writeLayers(partialTick);

        boolean zoomSight = shouldHideRootWhileSighting();
        boolean hideHull = shouldHideHullWhileSighting();
        boolean mortarMonitorHidden = mortarMonitorPart >= 0 && mortarMonitorHidden();
        // SBW keeps the hull as the same entity after death (isWreck); when the turret sympathetically
        // detonates it flies off as a separate TurretWreckEntity, so the hull's own turret+barrel mesh
        // must be hidden or it duplicates the flying wreck (GeoVehicleRenderer.transformCustomModelPart
        // does the same via bone.isHidden).
        boolean sympatheticWreck = entity.hasTurret() && entity.isWreck() && entity.getSympatheticDetonated();
        boolean hideDirty = zoomSight != lastZoomSight || hideHull != lastHideHull
                || mortarMonitorHidden != lastMortarMonitorHidden || sympatheticWreck != lastSympatheticWreck;
        lastZoomSight = zoomSight;
        lastHideHull = hideHull;
        lastMortarMonitorHidden = mortarMonitorHidden;
        lastSympatheticWreck = sympatheticWreck;

        boolean isWreck = entity.isWreck();
        boolean wreckTintDirty = isWreck != lastIsWreck;
        lastIsWreck = isWreck;

        int light = computePackedLight(partialTick);
        boolean lightDirty = light != lastLight;
        lastLight = light;

        boolean fullDirty = forceFullDirty;
        if (forceFullDirty) {
            Arrays.fill(changed, true);
            layersDirty = true;
            wreckTintDirty = true;
            forceFullDirty = false;
        }

        if (!layersDirty && !baseDirty && !hideDirty && !lightDirty && !wreckTintDirty) {
            return;
        }

        if (layersDirty) {
            if (replay != null) {
                PartsPose.evaluate(model, replay.clip(), 0.0f, transforms, scratch);
                Arrays.fill(changed, true);
            } else {
                PartsPose.evaluate(model, clips, times, transforms, changed, scratch);
            }
        }

        int wreckTint = isWreck ? WRECK_TINT : 0xFFFFFFFF;
        boolean recomputeHide = hideDirty || fullDirty;

        // Never-drawn bones (flare / laser / waterMask / dog tags) have no instance.
        for (int part = 0; part < instances.length; part++) {
            TransformedInstance instance = instances[part];
            if (instance == null) {
                continue;
            }
            boolean hide;
            boolean hideChanged = false;
            if (recomputeHide) {
                hide = zoomSight
                        || (hideHull && hullHidePart[part])
                        || (part == mortarMonitorPart && mortarMonitorHidden)
                        || (sympatheticWreck && part < turretMask.length && turretMask[part]);
                hideChanged = hide != partHidden[part];
                partHidden[part] = hide;
            } else {
                hide = partHidden[part];
            }

            if (hide) {
                // Tint is staged without upload; it ships with the pose once the part reappears.
                if (wreckTintDirty) {
                    instance.colorArgb(wreckTint);
                }
                if (hideChanged || fullDirty) {
                    instance.setZeroTransform();
                    instance.setChanged();
                }
                continue;
            }

            boolean poseDirty = layersDirty ? changed[part] : false;
            if (!poseDirty && !baseDirty && !hideChanged && !lightDirty && !hideDirty && !wreckTintDirty) {
                continue;
            }

            if (poseDirty || baseDirty || hideChanged || hideDirty) {
                composed.set(base).mul(transforms[part]);
                instance.pose.set(composed);
            }
            if (lightDirty || poseDirty || baseDirty || hideChanged || hideDirty) {
                instance.light(light);
            }
            if (wreckTintDirty) {
                instance.colorArgb(wreckTint);
            }
            instance.setChanged();
        }
    }

    public VehicleEntity vehicle() {
        return entity;
    }

    public int[] flareParts() {
        return flareParts;
    }

    /** World pose of a part (hull base included); null before the model has been evaluated. */
    public Matrix4f flareWorld(int part, Matrix4f out) {
        if (part < 0 || part >= transforms.length || transforms[part] == null) {
            return null;
        }
        return out.set(worldBase).mul(transforms[part]);
    }

    /** SBW draws flares unless LOD'd, wrecked, or the turret gunner is zoomed in. */
    public boolean shouldDrawFlares() {
        return flareParts.length > 0 && activeLod <= 0 && !entity.isWreck() && !shouldHideRootWhileSighting();
    }

    /** Mortar fire-control monitor is hidden unless the mortar is the intelligent variant. */
    private boolean mortarMonitorHidden() {
        if (entity instanceof com.atsuishio.superbwarfare.entity.vehicle.MortarEntity mortar) {
            return !mortar.getEntityData().get(
                    com.atsuishio.superbwarfare.entity.vehicle.MortarEntity.INTELLIGENT);
        }
        return true;
    }

    private boolean isPlayerVehicle() {
        Player player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() == entity;
    }

    /** True while the turret gunner is in right-click zoom. */
    private boolean shouldHideRootWhileSighting() {
        if (profile == null || !profile.hideTurretZoom()) {
            return false;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null || entity.getNthEntity(entity.getTurretControllerIndex()) != player) {
            return false;
        }
        return ClientEventHandler.zoomVehicle;
    }

    /** FP passenger view: hide the hull, keep the turret. */
    private boolean shouldHideHullWhileSighting() {
        if (ClientEventHandler.zoomVehicle) {
            return false;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null || player.getVehicle() != entity) {
            return false;
        }
        if (entity.getFirstPassenger() == player || !entity.hasWeapon(entity.getSeatIndex(player))) {
            return false;
        }
        return Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;
    }

    private boolean isHullHideBone(String name) {
        if ("root".equals(name) || "turret".equals(name) || "barrel".equals(name)) {
            return false;
        }
        if ("base".equals(name) || "move_Track".equals(name)) {
            return true;
        }
        return profile != null && profile.zoomHideBones().contains(name);
    }

    private void updateLodAndSkin(float partialTick) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distance = camera.distanceTo(entity.getPosition(partialTick));
        lastDistanceSq = distance * distance;
        int lod = lodWithHysteresis(distance);
        LodEntry entry = profile.lod(lod);
        ResourceLocation fallback = entry.texture() != null ? entry.texture() : profile.texture();
        ResourceLocation resolved = ColtanVehicleSkins.resolve(entity, fallback);

        if (lod == activeLod && handle != null && Objects.equals(resolved, boundTexture)) {
            pendingHandle = null; // target swung back to what is already drawn
            pendingTexture = null;
            return;
        }
        if (pendingHandle == null || lod != pendingLod || !Objects.equals(resolved, pendingTexture)) {
            pendingHandle = VehicleBridgeCache.handle(profile, lod, resolved);
            pendingLod = lod;
            pendingTexture = resolved;
        }
        // Keep drawing the current mesh until the target is resident; get() also starts its import.
        // Nothing drawn yet (or a failed import, so the fail path still reports) commits at once.
        if (handle != null && activeLod >= 0 && pendingHandle.get() == null && !pendingHandle.hasFailed()) {
            return;
        }
        activeLod = pendingLod;
        boundTexture = pendingTexture;
        handle = pendingHandle;
        pendingHandle = null;
        pendingTexture = null;
        deleteInstances();
        forceFullDirty = true;
        if (ColtanDebug.on(ColtanDebug.Cat.LOD)) {
            ColtanDebug.whenChanged(ColtanDebug.Cat.LOD,
                    "lod-" + entity.getId(),
                    lod + "|" + resolved,
                    "%s #%d → lod=%d dist=%.1f geo=%s tex=%s",
                    profile.entityId(), entity.getId(), lod, distance,
                    entry.geo(), resolved);
        }
    }

    /**
     * REPLAY-mode layer update: SBW's own pose pipeline, copied into the GemRender pose. A failure
     * (an addon hook that cannot run off its own render call) drops this vehicle to native layers.
     */
    private boolean replayLayers(float partialTick) {
        try {
            return replay.capture(model.layout().nodeTable(), partialTick);
        } catch (Exception | LinkageError e) {
            ColtanDebug.failOnce("vehicle-replay-" + entity.getType(),
                    "replay failed for %s, using native layers: %s", entity.getType(), e.toString());
            if (REPLAY_WARNED.add(entity.getType())) {
                Coltan.LOGGER.warn("Coltan: replay failed for {}, falling back to native layers",
                        entity.getType(), e);
            }
            replay = null;
            replayFailed = true;
            forceFullDirty = true;
            return writeLayers(partialTick);
        }
    }

    /**
     * {@link VehicleBridgeProfile#lodIndexForDistance} with a dead band around each threshold, so a
     * camera hovering at a tier boundary does not rebind the hull every frame.
     */
    private int lodWithHysteresis(double distance) {
        int raw = profile.lodIndexForDistance(distance);
        if (activeLod < 0 || raw == activeLod) {
            return raw;
        }
        double margin = Math.max(4.0, distance * LOD_HYSTERESIS);
        int farther = profile.lodIndexForDistance(distance - margin);
        if (farther > activeLod) {
            return farther;
        }
        int nearer = profile.lodIndexForDistance(distance + margin);
        if (nearer < activeLod) {
            return nearer;
        }
        return activeLod;
    }

    /**
     * Writes layer parameters and marks which parts need re-evaluation. Returns true when any layer
     * bucket or clip identity moved (GemRender §4 quantisation, scaled by {@link PoseLod}).
     */
    private boolean writeLayers(float partialTick) {
        float turretYaw = Mth.lerp(partialTick, entity.getTurretYRotO(), entity.getTurretYRot()) * Mth.DEG_TO_RAD;
        float barrelPitch;
        if (profile.hullYawOnly()) {
            // Mortar: elevation is entity pitch on move_paoguan, not turret X.
            barrelPitch = -Mth.lerp(partialTick, entity.xRotO, entity.getXRot()) * Mth.DEG_TO_RAD;
        } else {
            barrelPitch = Mth.clamp(-Mth.lerp(partialTick, entity.getTurretXRotO(), entity.getTurretXRot()),
                    entity.getTurretMinPitch(), entity.getTurretMaxPitch()) * Mth.DEG_TO_RAD;
        }
        float leftWheel = WHEEL_FACTOR * Mth.lerp(partialTick, entity.getLeftWheelRotO(), entity.getLeftWheelRot());
        float rightWheel = WHEEL_FACTOR * Mth.lerp(partialTick, entity.getRightWheelRotO(), entity.getRightWheelRot());
        float leftTrack = profile.driversTracks()
                ? Mth.lerp(partialTick, entity.getLeftTrackO(), entity.getLeftTrack()) : 0.0f;
        float rightTrack = profile.driversTracks()
                ? Mth.lerp(partialTick, entity.getRightTrackO(), entity.getRightTrack()) : 0.0f;
        float gunYaw = Mth.lerp(partialTick, entity.getGunYRotO(), entity.getGunYRot()) * Mth.DEG_TO_RAD;
        float gunPitch = Mth.clamp(-Mth.lerp(partialTick, entity.getGunXRotO(), entity.getGunXRot()),
                entity.getPassengerWeaponMinPitch(), entity.getPassengerWeaponMaxPitch()) * Mth.DEG_TO_RAD;
        float passengerYaw = gunYaw - turretYaw;
        float propeller = profile.driversPropellers()
                ? Mth.lerp(partialTick, entity.getPropellerRotO(), entity.getPropellerRot()) : 0.0f;
        float rudder = Mth.lerp(partialTick, entity.getRudderRotO(), entity.getRudderRot());

        int i = 0;
        clips[i] = turretClip;
        times[i++] = turretYaw;
        clips[i] = barrelClip;
        times[i++] = barrelPitch;
        clips[i] = leftWheelClip;
        times[i++] = leftWheel;
        clips[i] = rightWheelClip;
        times[i++] = rightWheel;
        clips[i] = profile.driversTracks() ? leftTrackClip : null;
        times[i++] = leftTrack;
        clips[i] = profile.driversTracks() ? rightTrackClip : null;
        times[i++] = rightTrack;
        clips[i] = passengerYawClip;
        times[i++] = passengerYaw;
        clips[i] = passengerPitchClip;
        times[i++] = gunPitch;
        clips[i] = profile.driversPropellers() ? propellerClip : null;
        times[i++] = propeller;
        clips[i] = rudderClip;
        times[i++] = rudder;
        clips[i] = controlClip;
        times[i++] = -4.0f * rudder;
        // DroneModel: bone.rotY = (millis % 36000000) / 12f (degrees) → radians for BoneAngle.
        clips[i] = droneWingClip;
        times[i++] = droneWingClip == null ? 0.0f
                : ((System.currentTimeMillis() % 36_000_000L) / 12.0f) * Mth.DEG_TO_RAD;
        clips[i] = mortarBipodClip;
        if (mortarBipodClip != null && profile.hullYawOnly()) {
            // SBW MortarRenderer: -2 * ((headPitch - (10 - headPitch * 0.1f)) * DEG)
            // headPitch is already degrees of elevation (−xRot); BoneAngle takes radians.
            float headPitchDeg = -Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            times[i++] = -2.0f * ((headPitchDeg - (10.0f - headPitchDeg * 0.1f)) * Mth.DEG_TO_RAD);
        } else {
            times[i++] = 0.0f;
        }

        // SBW GeoVehicleRenderer 'base' bone: the whole hull shakes when a weapon with RecoilTime fires.
        float recoilShake = (float) Mth.lerp(partialTick, entity.getRecoilShakeO(), entity.getRecoilShake());
        if (baseRecoil != null) {
            baseRecoil.set(recoilShake, entity.getYawWhileShoot());
        }
        clips[i] = baseRecoilClip;
        times[i++] = recoilShake;

        FireTimes fire = fireTimes;
        float now = entity.tickCount + partialTick;
        for (FireLayer layer : fireLayers) {
            float start = fire == null ? Float.NEGATIVE_INFINITY
                    : fire.starts.getOrDefault(layer.weaponKey, Float.NEGATIVE_INFINITY);
            boolean firing = layer.fire != null && now - start >= 0.0f
                    && now - start < layer.fire.duration() * 20.0f;
            clips[i] = firing ? layer.fire : layer.idle;
            times[i] = firing ? (now - start) / 20.0f : 0.0f;
            i++;
        }

        // Ambient loops (radar dish...): SBW starts these from entity code when energy > 0; the bridge
        // never sees that animation context, so drive them from the same condition here.
        boolean powered = entity.getEnergy() > 0 && !entity.isWreck();
        if (!Float.isNaN(lastLoopNow)) {
            float dt = now - lastLoopNow;
            if (powered && dt > 0.0f && dt < 40.0f) {
                loopClock += dt / 20.0f;
            }
        }
        lastLoopNow = now;
        for (GltfAnimation loop : loopLayers) {
            clips[i] = loop;
            times[i++] = loop.loop(loopClock);
        }

        // Addon-entity-gated clip pairs (e.g. a VLS hatch): openClip plays once and holds its last
        // frame (SBW PLAY_ONCE_HOLD) while the getter reports true; closeClip plays once (SBW
        // PLAY_ONCE_STOP) on the true->false edge, then falls back to bind pose (null = closed).
        for (StateLayer state : stateLayers) {
            boolean open;
            try {
                open = Boolean.TRUE.equals(state.getter.invoke(entity));
            } catch (ReflectiveOperationException | RuntimeException e) {
                open = false;
            }
            if (open != state.lastOpen) {
                state.lastOpen = open;
                state.transitionStart = now;
                ColtanDebug.log(ColtanDebug.Cat.VEHICLE, "%s: stateClip flipped to %s at tick %.1f",
                        profile.entityId(), open, now);
            }
            float elapsed = (now - state.transitionStart) / 20.0f;
            GltfAnimation clip;
            float time;
            if (open) {
                clip = state.openClip;
                float end = Math.max(0.0f, clip.duration() - 1.0e-4f);
                time = Math.min(Math.max(0.0f, elapsed), end);
            } else if (elapsed < state.closeClip.duration()) {
                clip = state.closeClip;
                time = elapsed;
            } else {
                clip = null;
                time = 0.0f;
            }
            clips[i] = clip;
            times[i++] = time;
        }

        // SBW GeoVehicleRenderer BoundBones*: each occupied seat rotates its bound bones by the delta
        // between the seat's aim vector and the weapon's DefaultBarrelDirection.
        for (SeatAimLayer seat : seatLayers) {
            float yawParam = 0.0f;
            float pitchParam = 0.0f;
            float[] aim = seatAim(seat.seat, partialTick);
            if (aim != null) {
                yawParam = aim[0];
                pitchParam = aim[1];
            }
            clips[i] = seat.yaw;
            times[i++] = yawParam;
            clips[i] = seat.pitch;
            times[i++] = pitchParam;
        }

        float quantum = PoseCache.getInstance()
                .quantumSeconds(PoseLod.getInstance().levelAt(lastDistanceSq));
        Arrays.fill(changed, false);
        boolean any = false;
        for (int layer = 0; layer < clips.length; layer++) {
            GltfAnimation clip = clips[layer];
            if (clip == null) {
                lastClips[layer] = null;
                lastBucket[layer] = Integer.MIN_VALUE;
                continue;
            }
            float param = times[layer];
            int bucket = quantum <= 0.0f
                    ? Float.floatToIntBits(param)
                    : Math.round(param / quantum);
            if (clip != lastClips[layer] || bucket != lastBucket[layer]) {
                lastClips[layer] = clip;
                lastBucket[layer] = bucket;
                times[layer] = quantum <= 0.0f ? param : bucket * quantum;
                any = true;
                or(changed, layerMasks[layer]);
            }
        }
        return any;
    }

    private static void or(boolean[] into, boolean[] from) {
        if (from == null) {
            return;
        }
        for (int i = 0; i < into.length && i < from.length; i++) {
            into[i] |= from[i];
        }
    }

    private boolean acquire() {
        GemRenderPartsModel loaded = handle.get();
        if (loaded == null) {
            return false;
        }
        if (loaded == model && instances.length == loaded.partCount()) {
            return true;
        }

        deleteInstances();
        this.model = loaded;
        int parts = loaded.partCount();
        this.transforms = loaded.newTransforms();
        this.instances = new TransformedInstance[parts];
        this.changed = new boolean[parts];
        this.partHidden = new boolean[parts];
        bindClips(loaded);
        replay = profile.renderMode() == VehicleRenderMode.REPLAY && !replayFailed
                ? SbwVehicleReplay.bind(entity, loaded.layout().nodeTable())
                : null;

        this.neverDrawPart = new boolean[parts];
        this.hullHidePart = new boolean[parts];
        this.mortarMonitorPart = -1;
        String monitor = profile.mortarMonitorBone();
        for (int part = 0; part < parts; part++) {
            String partName = loaded.parts().get(part).name();
            neverDrawPart[part] = BoneInference.neverDraw(partName);
            hullHidePart[part] = isHullHideBone(partName);
            if (monitor != null && monitor.equals(partName)) {
                mortarMonitorPart = part;
            }
        }

        List<Integer> flares = new ArrayList<>();
        for (int part = 0; part < parts; part++) {
            String partName = loaded.parts().get(part).name();
            if (partName != null && partName.startsWith("flare")) {
                flares.add(part);
            }
        }
        this.flareParts = flares.stream().mapToInt(Integer::intValue).toArray();
        if (flareParts.length > 0) {
            SbwVehicleFlare.register(this);
        }

        PartsPose.evaluate(loaded, (GltfAnimation) null, 0.0f, transforms, scratch);

        for (int part = 0; part < parts; part++) {
            Model mesh = loaded.parts().get(part).model();
            if (mesh == null || neverDrawPart[part]) {
                continue;
            }
            TransformedInstance instance = instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, mesh)
                    .createInstance();
            instance.colorArgb(0xFFFFFFFF);
            instances[part] = instance;
        }
        forceFullDirty = true;
        return true;
    }

    private void bindClips(GemRenderPartsModel loaded) {
        NodeTable table = loaded.layout().nodeTable();
        turretClip = angleClip(table, "turret", profile.resolveBone("turret"), 0.0f, 1.0f, 0.0f);
        barrelClip = angleClip(table, "barrel", profile.resolveBone("barrel"), 1.0f, 0.0f, 0.0f);
        // Every part that moves when the turret bone rotates is, by construction, the turret itself
        // or something mounted on it (barrel, bound weapons, turret armor) — exactly what needs to
        // disappear from the hull once it flies off as a separate TurretWreckEntity.
        turretMask = loaded.drivenBy(turretClip);
        leftWheelClip = wheelsClip(table, WHEEL_L, "wheelL", 1.0f, 0.0f, 0.0f);
        rightWheelClip = wheelsClip(table, WHEEL_R, "wheelR", 1.0f, 0.0f, 0.0f);
        leftTrackClip = trackClip(table, 'L', "trackL");
        rightTrackClip = trackClip(table, 'R', "trackR");
        passengerYawClip = angleClip(table, "pwsYaw", "passengerWeaponStationYaw", 0.0f, 1.0f, 0.0f);
        passengerPitchClip = angleClip(table, "pwsPitch", "passengerWeaponStationPitch", 1.0f, 0.0f, 0.0f);

        propellerClip = propellerClip(table);
        rudderClip = angleClip(table, "rudder", "move_rudder", 0.0f, 1.0f, 0.0f);
        controlClip = angleClip(table, "control", "move_control", 0.0f, 0.0f, 1.0f);
        droneWingClip = isDroneProfile()
                ? bonesClip(table, "droneWings", Arrays.asList(DRONE_WINGS), 0.0f, 1.0f, 0.0f)
                : null;
        baseRecoil = BaseRecoil.of(table);
        baseRecoilClip = baseRecoil == null ? null : GltfAnimation.procedural("baseRecoil", baseRecoil);
        String bipod = profile.mortarBipodBone();
        mortarBipodClip = bipod == null
                ? null
                : angleClip(table, "mortarBipod", bipod, 1.0f, 0.0f, 0.0f);

        fireLayers.clear();
        for (VehicleBridgeProfile.FireClip fire : profile.fireClips()) {
            fireLayers.add(new FireLayer(fire.weaponKey(),
                    fire.idleName() == null ? null : loaded.animation(fire.idleName()),
                    loaded.animation(fire.fireName())));
        }

        loopLayers.clear();
        for (String clipName : profile.loopClips()) {
            GltfAnimation clip = loaded.animation(clipName);
            if (clip == null) {
                ColtanDebug.failOnce("vehicle-loop-clip-" + profile.entityId() + "-" + clipName,
                        "loop clip '%s' not found in animations of %s", clipName, profile.entityId());
                continue;
            }
            loopLayers.add(clip);
        }

        stateLayers.clear();
        for (BridgeOverride.StateClip state : profile.stateClips()) {
            Method getter;
            try {
                getter = entity.getClass().getMethod(state.stateField());
            } catch (NoSuchMethodException e) {
                ColtanDebug.failOnce("vehicle-state-field-" + profile.entityId() + "-" + state.stateField(),
                        "state field getter '%s' not found on %s: %s",
                        state.stateField(), entity.getClass(), e.toString());
                continue;
            }
            GltfAnimation open = loaded.animation(state.openClip());
            GltfAnimation close = loaded.animation(state.closeClip());
            if (open == null || close == null) {
                ColtanDebug.failOnce("vehicle-state-clip-" + profile.entityId() + "-" + state.stateField(),
                        "state clip '%s'/'%s' not found in animations of %s (open=%s close=%s)",
                        state.openClip(), state.closeClip(), profile.entityId(), open != null, close != null);
                continue;
            }
            // Gated behind the VEHICLE debug category (off by default, see ColtanDebug) - enable
            // with "vehicle" or "all" in config/coltan/debug.txt to confirm this bound (or see why
            // it didn't) without needing a rebuild.
            ColtanDebug.log(ColtanDebug.Cat.VEHICLE,
                    "%s: bound stateClip '%s' -> open='%s'(%.2fs, %d drivers) close='%s'(%.2fs, %d drivers)",
                    profile.entityId(), state.stateField(), state.openClip(), open.duration(), open.drivers().size(),
                    state.closeClip(), close.duration(), close.drivers().size());
            stateLayers.add(new StateLayer(getter, open, close));
        }

        bindSeatAimLayers(table);

        int layerCount = FIXED_LAYERS + fireLayers.size() + loopLayers.size() + stateLayers.size()
                + 2 * seatLayers.size();
        clips = new GltfAnimation[layerCount];
        times = new float[layerCount];
        lastBucket = new int[layerCount];
        lastClips = new GltfAnimation[layerCount];
        Arrays.fill(lastBucket, Integer.MIN_VALUE);

        // Precompute per-layer dirty masks (driven parts + ancestors) once per model bind.
        GltfAnimation[] fixed = {
                turretClip, barrelClip, leftWheelClip, rightWheelClip,
                leftTrackClip, rightTrackClip, passengerYawClip, passengerPitchClip,
                propellerClip, rudderClip, controlClip, droneWingClip,
                mortarBipodClip, baseRecoilClip
        };
        layerMasks = new boolean[layerCount][];
        for (int layer = 0; layer < FIXED_LAYERS; layer++) {
            layerMasks[layer] = loaded.withAncestors(loaded.drivenBy(fixed[layer]));
        }
        for (int f = 0; f < fireLayers.size(); f++) {
            FireLayer layer = fireLayers.get(f);
            // Union idle+fire so a clip swap still covers every part either state can move.
            boolean[] mask = loaded.withAncestors(loaded.drivenBy(layer.idle));
            or(mask, loaded.withAncestors(loaded.drivenBy(layer.fire)));
            layerMasks[FIXED_LAYERS + f] = mask;
        }
        int next = FIXED_LAYERS + fireLayers.size();
        for (GltfAnimation loop : loopLayers) {
            layerMasks[next++] = loaded.withAncestors(loaded.drivenBy(loop));
        }
        for (StateLayer state : stateLayers) {
            // Union open+close so a clip swap still covers every part either state can move.
            boolean[] mask = loaded.withAncestors(loaded.drivenBy(state.openClip));
            or(mask, loaded.withAncestors(loaded.drivenBy(state.closeClip)));
            layerMasks[next++] = mask;
        }
        for (SeatAimLayer seat : seatLayers) {
            layerMasks[next++] = loaded.withAncestors(loaded.drivenBy(seat.yaw));
            layerMasks[next++] = loaded.withAncestors(loaded.drivenBy(seat.pitch));
        }
    }

    /**
     * Mirrors SBW GeoVehicleRenderer's BoundBones loop: for every seat, every weapon's
     * BoundBones (yaw then pitch), BoundBonesYaw and BoundBonesPitch follow that seat's aim.
     */
    private void bindSeatAimLayers(NodeTable table) {
        seatLayers.clear();
        List<SeatInfo> seats;
        try {
            seats = entity.computed().seats();
        } catch (RuntimeException e) {
            ColtanDebug.failOnce("vehicle-seats-" + entity.getType(),
                    "could not read seats for %s: %s", entity.getType(), e.toString());
            return;
        }
        for (int seat = 0; seat < seats.size(); seat++) {
            Set<String> yawBones = new LinkedHashSet<>();
            Set<String> pitchBones = new LinkedHashSet<>();
            for (int k = 0; k < seats.get(seat).weapons().size(); k++) {
                GunData gun = entity.getGunData(seat, k);
                if (gun == null) {
                    continue;
                }
                addNames(yawBones, gun.get(GunProp.BOUND_BONES));
                addNames(pitchBones, gun.get(GunProp.BOUND_BONES));
                addNames(yawBones, gun.get(GunProp.BOUND_BONES_YAW));
                addNames(pitchBones, gun.get(GunProp.BOUND_BONES_PITCH));
            }
            GltfAnimation yaw = bonesClip(table, "seat" + seat + "Yaw", new ArrayList<>(yawBones), 0.0f, 1.0f, 0.0f);
            GltfAnimation pitch = bonesClip(table, "seat" + seat + "Pitch", new ArrayList<>(pitchBones),
                    1.0f, 0.0f, 0.0f);
            if (yaw != null || pitch != null) {
                seatLayers.add(new SeatAimLayer(seat, yaw, pitch));
            }
        }
    }

    private static void addNames(Set<String> into, Iterable<String> names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                into.add(name);
            }
        }
    }

    /** Yaw/pitch in radians that SBW would apply to a seat's bound bones; null while unoccupied. */
    private float[] seatAim(int seat, float partialTick) {
        try {
            if (entity.getNthEntity(seat) == null) {
                return null;
            }
            Vec3 defaultVec = entity.getDefaultBarrelDirection(seat, partialTick);
            Vec3 targetVec = entity.getShootVec(seat, partialTick);
            if (defaultVec == null || targetVec == null) {
                return null;
            }
            float diffY = (float) Mth.wrapDegrees(-VehicleVecUtils.getYRotFromVector(targetVec)
                    + VehicleVecUtils.getYRotFromVector(defaultVec));
            float diffX = (float) Mth.wrapDegrees(-VehicleVecUtils.getXRotFromVector(targetVec)
                    + VehicleVecUtils.getXRotFromVector(defaultVec));
            return new float[] {-diffY * Mth.DEG_TO_RAD, -diffX * Mth.DEG_TO_RAD};
        } catch (RuntimeException e) {
            ColtanDebug.failOnce("vehicle-seat-aim-" + entity.getType() + "-" + seat,
                    "seat aim failed for %s seat %d: %s", entity.getType(), seat, e.toString());
            return null;
        }
    }

    private boolean isDroneProfile() {
        if (entity instanceof DroneEntity) {
            return true;
        }
        return profile != null && "drone".equals(profile.entityId().getPath());
    }

    private static GltfAnimation propellerClip(NodeTable table) {
        boolean heli = table.slotOfName("move_tailPropeller") >= 0;
        List<PoseDriver> drivers = new ArrayList<>();
        for (int slot = 0; slot < table.nodeCount(); slot++) {
            String name = table.nodeName(slot);
            if (!name.startsWith("move_propeller") && !name.equals("move_tailPropeller")) {
                continue;
            }
            float ax;
            float ay;
            float az;
            float scale = 1.0f;
            if (name.contains("tail") || name.equals("move_tailPropeller")) {
                ax = 1.0f;
                ay = 0.0f;
                az = 0.0f;
                scale = 6.0f;
            } else if (heli) {
                ax = 0.0f;
                ay = name.matches(".*\\d+$") ? 1.0f : -1.0f;
                az = 0.0f;
            } else {
                ax = 0.0f;
                ay = 0.0f;
                az = name.matches(".*[2-9]$") ? -1.0f : 1.0f;
            }
            drivers.add(new ScaledAngle(BoneAngle.about(table, slot, ax, ay, az), scale));
        }
        if (drivers.isEmpty()) {
            return null;
        }
        return GltfAnimation.procedural("propellers", drivers.toArray(PoseDriver[]::new));
    }

    private static GltfAnimation bonesClip(NodeTable table, String name, List<String> bones, float ax, float ay,
                                           float az) {
        List<PoseDriver> drivers = new ArrayList<>();
        for (String bone : bones) {
            int slot = table.slotOfName(bone);
            if (slot >= 0) {
                drivers.add(BoneAngle.about(table, slot, ax, ay, az));
            }
        }
        if (drivers.isEmpty()) {
            return null;
        }
        return GltfAnimation.procedural(name, drivers.toArray(PoseDriver[]::new));
    }

    private GltfAnimation trackClip(NodeTable table, char side, String name) {
        List<PoseDriver> drivers = new ArrayList<>();
        for (int slot = 0; slot < table.nodeCount(); slot++) {
            String bone = table.nodeName(slot);
            Matcher mov = TRACK_MOV.matcher(bone);
            if (mov.matches() && mov.group(1).charAt(0) == side) {
                int index = Integer.parseInt(mov.group(2));
                int offset = table.offsetFor(slot, "translation");
                if (offset >= 0) {
                    float[] rest = table.newScratch();
                    table.resetToRest(rest);
                    drivers.add(new TrackMove(offset, rest[offset], rest[offset + 1], rest[offset + 2], index,
                            profile));
                }
            }
            Matcher rot = TRACK_ROT.matcher(bone);
            if (rot.matches() && rot.group(1).charAt(0) == side) {
                int index = Integer.parseInt(rot.group(2));
                drivers.add(new TrackRot(NodeRotation.offsetOf(table, slot), index, profile));
            }
        }
        if (drivers.isEmpty()) {
            return null;
        }
        return GltfAnimation.procedural(name, drivers.toArray(PoseDriver[]::new));
    }

    private static GltfAnimation angleClip(NodeTable table, String clipName, String bone, float ax, float ay,
                                           float az) {
        int slot = table.slotOfName(bone);
        if (slot < 0) {
            return null;
        }
        return GltfAnimation.procedural(clipName, BoneAngle.about(table, slot, ax, ay, az));
    }

    private static GltfAnimation wheelsClip(NodeTable table, Pattern pattern, String name, float ax, float ay,
                                            float az) {
        List<PoseDriver> drivers = new ArrayList<>();
        for (int slot = 0; slot < table.nodeCount(); slot++) {
            if (pattern.matcher(table.nodeName(slot)).matches()) {
                drivers.add(BoneAngle.about(table, slot, ax, ay, az));
            }
        }
        if (drivers.isEmpty()) {
            return null;
        }
        return GltfAnimation.procedural(name, drivers.toArray(PoseDriver[]::new));
    }

    /** Writes the hull base transform; returns true when it moved enough to re-upload poses. */
    private boolean writeBase(float partialTick) {
        Vector3f at = getVisualPosition(partialTick);
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = 0.0f;
        float roll = 0.0f;
        if (!profile.hullYawOnly()) {
            pitch = Mth.lerp(partialTick, entity.xRotO + entity.getFakePitchO(),
                    entity.getXRot() + entity.getFakePitch());
            roll = Mth.lerp(partialTick, entity.getPrevRoll() + entity.getFakeRollO(),
                    entity.getRoll() + entity.getFakeRoll());
        }
        float pivotY = (float) entity.getRotateOffsetHeight();
        float scale = profile.renderScale();

        base.translation(at.x, at.y, at.z)
                .translate(0.0f, pivotY, 0.0f)
                .rotateY((-yaw + 180.0f) * Mth.DEG_TO_RAD);
        if (!profile.hullYawOnly()) {
            base.rotateX(-pitch * Mth.DEG_TO_RAD)
                    .rotateZ(-roll * Mth.DEG_TO_RAD);
        }
        base.translate(0.0f, -pivotY, 0.0f)
                .scale(scale);
        Vec3 abs = entity.getPosition(partialTick);
        worldBase.translation((float) abs.x - at.x, (float) abs.y - at.y, (float) abs.z - at.z).mul(base);

        boolean dirty = Float.isNaN(lastAtX)
                || at.x != lastAtX || at.y != lastAtY || at.z != lastAtZ
                || yaw != lastYaw || pitch != lastPitch || roll != lastRoll
                || pivotY != lastPivotY || scale != lastScale;
        lastAtX = at.x;
        lastAtY = at.y;
        lastAtZ = at.z;
        lastYaw = yaw;
        lastPitch = pitch;
        lastRoll = roll;
        lastPivotY = pivotY;
        lastScale = scale;
        return dirty;
    }

    private void deleteInstances() {
        SbwVehicleFlare.unregister(this);
        flareParts = new int[0];
        for (TransformedInstance instance : instances) {
            if (instance != null) {
                instance.delete();
            }
        }
        instances = new TransformedInstance[0];
        transforms = new Matrix4f[0];
        changed = new boolean[0];
        partHidden = new boolean[0];
        neverDrawPart = new boolean[0];
        hullHidePart = new boolean[0];
        mortarMonitorPart = -1;
        layerMasks = new boolean[0][];
        turretMask = new boolean[0];
        lastBucket = new int[0];
        lastClips = new GltfAnimation[0];
        model = null;
        replay = null;
        turretClip = barrelClip = leftWheelClip = rightWheelClip = leftTrackClip = rightTrackClip = null;
        passengerYawClip = passengerPitchClip = null;
        propellerClip = rudderClip = controlClip = droneWingClip = mortarBipodClip = null;
        baseRecoilClip = null;
        baseRecoil = null;
        fireLayers.clear();
        loopLayers.clear();
        stateLayers.clear();
        seatLayers.clear();
        lastLoopNow = Float.NaN;
        clips = new GltfAnimation[0];
        times = new float[0];
        lastAtX = Float.NaN;
        lastLight = Integer.MIN_VALUE;
        forceFullDirty = true;
    }

    @Override
    protected void _delete() {
        // Conditional: a replacement visual for the same entity may already own the entry.
        FIRE.remove(entity.getId(), fireTimes);
        deleteInstances();
        super._delete();
    }

    private record BoneAngle(int offset, float axisX, float axisY, float axisZ) implements PoseDriver {
        static BoneAngle about(NodeTable table, int slot, float axisX, float axisY, float axisZ) {
            float[] axis = NodeRotation.axis(axisX, axisY, axisZ);
            return new BoneAngle(NodeRotation.offsetOf(table, slot), axis[0], axis[1], axis[2]);
        }

        @Override
        public void apply(float angleRadians, float[] scratch) {
            NodeRotation.compose(scratch, offset, axisX, axisY, axisZ, angleRadians);
        }

        @Override
        public float cycleSeconds() {
            return 0.0f;
        }
    }

    private record ScaledAngle(BoneAngle inner, float scale) implements PoseDriver {
        @Override
        public void apply(float angleRadians, float[] scratch) {
            inner.apply(angleRadians * scale, scratch);
        }

        @Override
        public float cycleSeconds() {
            return 0.0f;
        }
    }

    private record TrackRot(int offset, int index, VehicleBridgeProfile profile) implements PoseDriver {
        @Override
        public void apply(float trackParam, float[] scratch) {
            float t = VehicleBridgeProfile.wrap(trackParam + profile.trackDistance() * index, profile.trackLength());
            float deg = profile.sampleRotX(t);
            NodeRotation.compose(scratch, offset, 1.0f, 0.0f, 0.0f, -deg * Mth.DEG_TO_RAD);
        }

        @Override
        public float cycleSeconds() {
            return 0.0f;
        }
    }

    private record TrackMove(int offset, float restX, float restY, float restZ, int index,
                             VehicleBridgeProfile profile) implements PoseDriver {
        @Override
        public void apply(float trackParam, float[] scratch) {
            float t = VehicleBridgeProfile.wrap(trackParam + profile.trackDistance() * index, profile.trackLength());
            scratch[offset] = restX;
            scratch[offset + 1] = restY + profile.sampleMoveY(t) / 16.0f;
            scratch[offset + 2] = restZ + profile.sampleMoveZ(t) / 16.0f;
        }

        @Override
        public float cycleSeconds() {
            return 0.0f;
        }
    }

    private record FireLayer(String weaponKey, GltfAnimation idle, GltfAnimation fire) {
    }

    /**
     * SBW's hull recoil: the {@code base} bone is shifted and tilted by a damped oscillation
     * ({@code recoilShake}) whose direction comes from the shot's yaw relative to the hull
     * ({@code yawWhileShoot}). The clip parameter is only the shake amount, so the pose is
     * re-evaluated exactly when it changes; the direction is fed in through {@link #set}.
     */
    private static final class BaseRecoil implements PoseDriver {
        private final int translation;
        private final int rotation;
        private final float restX;
        private final float restZ;
        private float dx;
        private float dz;
        private float tiltX;
        private float tiltZ;

        private BaseRecoil(int translation, int rotation, float restX, float restZ) {
            this.translation = translation;
            this.rotation = rotation;
            this.restX = restX;
            this.restZ = restZ;
        }

        static BaseRecoil of(NodeTable table) {
            int slot = table.slotOfName("base");
            if (slot < 0) {
                return null;
            }
            int translation = table.offsetFor(slot, "translation");
            if (translation < 0) {
                return null;
            }
            float[] rest = table.newScratch();
            table.resetToRest(rest);
            return new BaseRecoil(translation, NodeRotation.offsetOf(table, slot),
                    rest[translation], rest[translation + 2]);
        }

        void set(float shake, float yawWhileShoot) {
            float a = yawWhileShoot;
            float r = (Math.abs(a) - 90.0f) / 90.0f;
            float r2;
            if (Math.abs(a) <= 90.0f) {
                r2 = a / 90.0f;
            } else if (a < 0.0f) {
                r2 = -(180.0f + a) / 90.0f;
            } else {
                r2 = (180.0f - a) / 90.0f;
            }
            dx = -r2 * shake * 0.5f / 16.0f;
            dz = r * shake / 16.0f;
            tiltX = r * shake * Mth.DEG_TO_RAD;
            tiltZ = r2 * shake * Mth.DEG_TO_RAD;
        }

        @Override
        public void apply(float shake, float[] scratch) {
            scratch[translation] = restX + dx;
            scratch[translation + 2] = restZ + dz;
            NodeRotation.compose(scratch, rotation, 1.0f, 0.0f, 0.0f, tiltX);
            NodeRotation.compose(scratch, rotation, 0.0f, 0.0f, 1.0f, tiltZ);
        }

        @Override
        public float cycleSeconds() {
            return 0.0f;
        }
    }

    private record SeatAimLayer(int seat, GltfAnimation yaw, GltfAnimation pitch) {
    }

    /** Resolved StateClip binding: getter cached once, transition tracked per instance. */
    private static final class StateLayer {
        final Method getter;
        final GltfAnimation openClip;
        final GltfAnimation closeClip;
        boolean lastOpen;
        float transitionStart = Float.NEGATIVE_INFINITY;

        StateLayer(Method getter, GltfAnimation openClip, GltfAnimation closeClip) {
            this.getter = getter;
            this.openClip = openClip;
            this.closeClip = closeClip;
        }
    }

    private static final class FireTimes {
        final Map<String, Float> starts = new ConcurrentHashMap<>();
    }
}
