package com.neoalive.coltan.client.compat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.atsuishio.superbwarfare.api.event.ClientVehicleFireEvent;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.neoalive.coltan.client.compat.bridge.BoneInference;
import com.neoalive.coltan.client.compat.bridge.LodEntry;
import com.neoalive.coltan.client.compat.bridge.VehicleBridgeCache;
import com.neoalive.coltan.client.compat.bridge.VehicleBridgeProfile;
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
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;

/**
 * GemRender parts visual driven by a vehicle bridge profile.
 *
 * <p>Follows GemRender INTEGRATION §4: bucket each layer's parameter (coarser via {@link PoseLod}
 * at distance), re-evaluate only dirty parts, and call {@code setChanged()} only when something
 * actually moved — so a parked AI-crewed hull early-outs instead of re-uploading every part every
 * frame.
 */
public final class SbwVehicleGemVisual extends ComponentEntityVisual<VehicleEntity> {
    private static final float WHEEL_FACTOR = 1.5f;
    private static final Pattern TRACK_MOV = Pattern.compile("^trackMov([LR])(\\d+)$");
    private static final Pattern TRACK_ROT = Pattern.compile("^trackRot([LR])(\\d+)$");
    private static final Pattern WHEEL_L = Pattern.compile("^wheelL.*$|^w_[lL].*$");
    private static final Pattern WHEEL_R = Pattern.compile("^wheelR.*$|^w_[rR].*$");
    private static final int FIXED_LAYERS = 13;

    private static final Map<Integer, FireTimes> FIRE = new ConcurrentHashMap<>();

    static {
        MinecraftForge.EVENT_BUS.addListener(SbwVehicleGemVisual::onVehicleFire);
    }

    private VehicleBridgeProfile profile;
    private ModelCache.Handle<GemRenderPartsModel> handle;
    private int activeLod = -1;
    private double lastDistanceSq;
    private ResourceLocation boundTexture;

    private TransformedInstance[] instances = new TransformedInstance[0];
    private Matrix4f[] transforms = new Matrix4f[0];
    private final Matrix4f base = new Matrix4f();
    private final Matrix4f composed = new Matrix4f();
    private final PartsPose.Scratch scratch = new PartsPose.Scratch();

    private GltfAnimation[] clips = new GltfAnimation[0];
    private float[] times = new float[0];
    private int[] lastBucket = new int[0];
    private GltfAnimation[] lastClips = new GltfAnimation[0];
    private boolean[][] layerMasks = new boolean[0][];
    private boolean[] changed = new boolean[0];
    private boolean[] partHidden = new boolean[0];

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
    private boolean forceFullDirty = true;

    private GemRenderPartsModel model;
    private GltfAnimation turretClip;
    private GltfAnimation barrelClip;
    private GltfAnimation leftWheelClip;
    private GltfAnimation rightWheelClip;
    private GltfAnimation leftTrackClip;
    private GltfAnimation rightTrackClip;
    private GltfAnimation passengerYawClip;
    private GltfAnimation passengerPitchClip;
    private GltfAnimation boundYawClip;
    private GltfAnimation boundPitchClip;
    private GltfAnimation propellerClip;
    private GltfAnimation rudderClip;
    private GltfAnimation controlClip;
    private final List<FireLayer> fireLayers = new ArrayList<>();

    public SbwVehicleGemVisual(VisualizationContext ctx, VehicleEntity entity, float partialTick) {
        super(ctx, entity, partialTick);
        this.profile = VehicleBridgeCache.profile(entity.getType());
        this.handle = profile == null ? null : VehicleBridgeCache.handle(profile, 0);
        addComponent(new ShadowComponent(ctx, entity).radius(1.8f));
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
            deleteInstances();
            forceFullDirty = true;
        }
        if (profile == null) {
            return;
        }

        float partialTick = ctx.partialTick();
        updateLodAndSkin(partialTick);
        if (handle == null || !acquire()) {
            return;
        }

        boolean baseDirty = writeBase(partialTick);
        boolean layersDirty = writeLayers(partialTick);

        boolean zoomSight = shouldHideRootWhileSighting();
        boolean hideHull = shouldHideHullWhileSighting();
        boolean hideDirty = zoomSight != lastZoomSight || hideHull != lastHideHull;
        lastZoomSight = zoomSight;
        lastHideHull = hideHull;

        int light = computePackedLight(partialTick);
        boolean lightDirty = light != lastLight;
        lastLight = light;

        if (forceFullDirty) {
            Arrays.fill(changed, true);
            layersDirty = true;
            forceFullDirty = false;
        }

        if (!layersDirty && !baseDirty && !hideDirty && !lightDirty) {
            return;
        }

        if (layersDirty) {
            PartsPose.evaluate(model, clips, times, transforms, changed, scratch);
        }

        for (int part = 0; part < instances.length; part++) {
            TransformedInstance instance = instances[part];
            if (instance == null) {
                continue;
            }
            String name = model.parts().get(part).name();
            boolean hide = BoneInference.neverDraw(name)
                    || zoomSight
                    || (hideHull && isHullHideBone(name));
            boolean hideChanged = hide != partHidden[part];
            partHidden[part] = hide;

            boolean poseDirty = layersDirty ? changed[part] : false;
            if (!poseDirty && !baseDirty && !hideChanged && !lightDirty && !hideDirty) {
                continue;
            }

            if (hide) {
                instance.setZeroTransform();
            } else if (poseDirty || baseDirty || hideChanged || hideDirty) {
                composed.set(base).mul(transforms[part]);
                instance.pose.set(composed);
            }
            if (lightDirty || poseDirty || baseDirty || hideChanged || hideDirty) {
                instance.light(light);
            }
            instance.setChanged();
        }
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
        int lod = profile.lodIndexForDistance(distance);
        LodEntry entry = profile.lod(lod);
        ResourceLocation fallback = entry.texture() != null ? entry.texture() : profile.texture();
        ResourceLocation resolved = ColtanVehicleSkins.resolve(entity, fallback);

        if (lod == activeLod && handle != null && Objects.equals(resolved, boundTexture)) {
            return;
        }
        activeLod = lod;
        boundTexture = resolved;
        handle = VehicleBridgeCache.handle(profile, lod, resolved);
        deleteInstances();
        forceFullDirty = true;
    }

    /**
     * Writes layer parameters and marks which parts need re-evaluation. Returns true when any layer
     * bucket or clip identity moved (GemRender §4 quantisation, scaled by {@link PoseLod}).
     */
    private boolean writeLayers(float partialTick) {
        float turretYaw = Mth.lerp(partialTick, entity.getTurretYRotO(), entity.getTurretYRot()) * Mth.DEG_TO_RAD;
        float barrelPitch = Mth.clamp(-Mth.lerp(partialTick, entity.getTurretXRotO(), entity.getTurretXRot()),
                entity.getTurretMinPitch(), entity.getTurretMaxPitch()) * Mth.DEG_TO_RAD;
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
        clips[i] = boundYawClip;
        times[i++] = gunYaw;
        clips[i] = boundPitchClip;
        times[i++] = gunPitch;
        clips[i] = profile.driversPropellers() ? propellerClip : null;
        times[i++] = propeller;
        clips[i] = rudderClip;
        times[i++] = rudder;
        clips[i] = controlClip;
        times[i++] = -4.0f * rudder;

        FireTimes fire = FIRE.get(entity.getId());
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

        PartsPose.evaluate(loaded, (GltfAnimation) null, 0.0f, transforms, scratch);

        for (int part = 0; part < parts; part++) {
            Model mesh = loaded.parts().get(part).model();
            if (mesh == null) {
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
        leftWheelClip = wheelsClip(table, WHEEL_L, "wheelL", 1.0f, 0.0f, 0.0f);
        rightWheelClip = wheelsClip(table, WHEEL_R, "wheelR", 1.0f, 0.0f, 0.0f);
        leftTrackClip = trackClip(table, 'L', "trackL");
        rightTrackClip = trackClip(table, 'R', "trackR");
        passengerYawClip = angleClip(table, "pwsYaw", "passengerWeaponStationYaw", 0.0f, 1.0f, 0.0f);
        passengerPitchClip = angleClip(table, "pwsPitch", "passengerWeaponStationPitch", 1.0f, 0.0f, 0.0f);

        List<String> yawBones = new ArrayList<>(profile.boundBonesYaw());
        yawBones.addAll(profile.boundBones());
        List<String> pitchBones = new ArrayList<>(profile.boundBonesPitch());
        pitchBones.addAll(profile.boundBones());
        boundYawClip = bonesClip(table, "boundYaw", yawBones, 0.0f, -1.0f, 0.0f);
        boundPitchClip = bonesClip(table, "boundPitch", pitchBones, 1.0f, 0.0f, 0.0f);

        propellerClip = propellerClip(table);
        rudderClip = angleClip(table, "rudder", "move_rudder", 0.0f, 1.0f, 0.0f);
        controlClip = angleClip(table, "control", "move_control", 0.0f, 0.0f, 1.0f);

        fireLayers.clear();
        for (VehicleBridgeProfile.FireClip fire : profile.fireClips()) {
            fireLayers.add(new FireLayer(fire.weaponKey(),
                    fire.idleName() == null ? null : loaded.animation(fire.idleName()),
                    loaded.animation(fire.fireName())));
        }

        int layerCount = FIXED_LAYERS + fireLayers.size();
        clips = new GltfAnimation[layerCount];
        times = new float[layerCount];
        lastBucket = new int[layerCount];
        lastClips = new GltfAnimation[layerCount];
        Arrays.fill(lastBucket, Integer.MIN_VALUE);

        // Precompute per-layer dirty masks (driven parts + ancestors) once per model bind.
        GltfAnimation[] fixed = {
                turretClip, barrelClip, leftWheelClip, rightWheelClip,
                leftTrackClip, rightTrackClip, passengerYawClip, passengerPitchClip,
                boundYawClip, boundPitchClip, propellerClip, rudderClip, controlClip
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
        float pitch = Mth.lerp(partialTick, entity.xRotO + entity.getFakePitchO(),
                entity.getXRot() + entity.getFakePitch());
        float roll = Mth.lerp(partialTick, entity.getPrevRoll() + entity.getFakeRollO(),
                entity.getRoll() + entity.getFakeRoll());
        float pivotY = (float) entity.getRotateOffsetHeight();
        float scale = profile.renderScale();

        base.translation(at.x, at.y, at.z)
                .translate(0.0f, pivotY, 0.0f)
                .rotateY((-yaw + 180.0f) * Mth.DEG_TO_RAD)
                .rotateX(-pitch * Mth.DEG_TO_RAD)
                .rotateZ(-roll * Mth.DEG_TO_RAD)
                .translate(0.0f, -pivotY, 0.0f)
                .scale(scale);

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
        for (TransformedInstance instance : instances) {
            if (instance != null) {
                instance.delete();
            }
        }
        instances = new TransformedInstance[0];
        transforms = new Matrix4f[0];
        changed = new boolean[0];
        partHidden = new boolean[0];
        layerMasks = new boolean[0][];
        lastBucket = new int[0];
        lastClips = new GltfAnimation[0];
        model = null;
        turretClip = barrelClip = leftWheelClip = rightWheelClip = leftTrackClip = rightTrackClip = null;
        passengerYawClip = passengerPitchClip = boundYawClip = boundPitchClip = null;
        propellerClip = rudderClip = controlClip = null;
        fireLayers.clear();
        clips = new GltfAnimation[0];
        times = new float[0];
        lastAtX = Float.NaN;
        lastLight = Integer.MIN_VALUE;
        forceFullDirty = true;
    }

    @Override
    protected void _delete() {
        FIRE.remove(entity.getId());
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

    private static final class FireTimes {
        final Map<String, Float> starts = new ConcurrentHashMap<>();
    }
}
