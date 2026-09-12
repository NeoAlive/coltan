package com.neoalive.coltan.client.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.atsuishio.superbwarfare.api.event.ClientVehicleFireEvent;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.neoalive.coltan.client.compat.bridge.VehicleBridgeCache;
import com.neoalive.coltan.client.compat.bridge.VehicleBridgeProfile;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderPartsModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeRotation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PartsPose;
import com.wf.gemrender.gltf.PoseDriver;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.ComponentEntityVisual;
import dev.engine_room.flywheel.lib.visual.component.ShadowComponent;
import net.minecraft.util.Mth;
import net.minecraftforge.common.MinecraftForge;

/**
 * Shared GemRender parts visual for any SBW vehicle with a {@link VehicleBridgeProfile}.
 */
public final class SbwVehicleGemVisual extends ComponentEntityVisual<VehicleEntity> {
    private static final float WHEEL_FACTOR = 1.5f;
    private static final Pattern TRACK_MOV = Pattern.compile("^trackMov([LR])(\\d+)$");
    private static final Pattern TRACK_ROT = Pattern.compile("^trackRot([LR])(\\d+)$");
    private static final Pattern WHEEL_L = Pattern.compile("^wheelL.*$|^w_[lL].*$");
    private static final Pattern WHEEL_R = Pattern.compile("^wheelR.*$|^w_[rR].*$");

    private static final Map<Integer, FireTimes> FIRE = new ConcurrentHashMap<>();

    static {
        MinecraftForge.EVENT_BUS.addListener(SbwVehicleGemVisual::onVehicleFire);
    }

    private final VehicleBridgeProfile profile;
    private final ModelCache.Handle<GemRenderPartsModel> handle;

    private TransformedInstance[] instances = new TransformedInstance[0];
    private Matrix4f[] transforms = new Matrix4f[0];
    private final Matrix4f base = new Matrix4f();
    private final Matrix4f composed = new Matrix4f();
    private final PartsPose.Scratch scratch = new PartsPose.Scratch();

    private GltfAnimation[] clips = new GltfAnimation[0];
    private float[] times = new float[0];
    private GemRenderPartsModel model;
    private GltfAnimation turretClip;
    private GltfAnimation barrelClip;
    private GltfAnimation leftWheelClip;
    private GltfAnimation rightWheelClip;
    private GltfAnimation leftTrackClip;
    private GltfAnimation rightTrackClip;
    private final List<FireLayer> fireLayers = new ArrayList<>();

    public SbwVehicleGemVisual(VisualizationContext ctx, VehicleEntity entity, float partialTick) {
        super(ctx, entity, partialTick);
        this.profile = VehicleBridgeCache.profile(entity.getType());
        this.handle = profile == null ? null : VehicleBridgeCache.handle(profile);
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
        if (profile == null || handle == null || !acquire()) {
            return;
        }

        float partialTick = ctx.partialTick();
        writeBase(partialTick);
        writeLayers(partialTick);
        PartsPose.evaluate(model, clips, times, transforms, null, scratch);

        int light = computePackedLight(partialTick);
        for (int part = 0; part < instances.length; part++) {
            TransformedInstance instance = instances[part];
            if (instance == null) {
                continue;
            }
            composed.set(base).mul(transforms[part]);
            instance.pose.set(composed);
            instance.light(light);
            instance.setChanged();
        }
    }

    private void writeLayers(float partialTick) {
        float turretYaw = Mth.lerp(partialTick, entity.getTurretYRotO(), entity.getTurretYRot()) * Mth.DEG_TO_RAD;
        float barrelPitch = Mth.clamp(-Mth.lerp(partialTick, entity.getTurretXRotO(), entity.getTurretXRot()),
                entity.getTurretMinPitch(), entity.getTurretMaxPitch()) * Mth.DEG_TO_RAD;
        float leftWheel = WHEEL_FACTOR * Mth.lerp(partialTick, entity.getLeftWheelRotO(), entity.getLeftWheelRot());
        float rightWheel = WHEEL_FACTOR * Mth.lerp(partialTick, entity.getRightWheelRotO(), entity.getRightWheelRot());
        float leftTrack = Mth.lerp(partialTick, entity.getLeftTrackO(), entity.getLeftTrack());
        float rightTrack = Mth.lerp(partialTick, entity.getRightTrackO(), entity.getRightTrack());

        int i = 0;
        clips[i] = turretClip;
        times[i++] = turretYaw;
        clips[i] = barrelClip;
        times[i++] = barrelPitch;
        clips[i] = leftWheelClip;
        times[i++] = leftWheel;
        clips[i] = rightWheelClip;
        times[i++] = rightWheel;
        clips[i] = leftTrackClip;
        times[i++] = leftTrack;
        clips[i] = rightTrackClip;
        times[i++] = rightTrack;

        FireTimes fire = FIRE.get(entity.getId());
        float now = entity.tickCount + partialTick;
        for (FireLayer layer : fireLayers) {
            float start = fire == null ? Float.NEGATIVE_INFINITY : fire.starts.getOrDefault(layer.weaponKey, Float.NEGATIVE_INFINITY);
            boolean firing = layer.fire != null && now - start >= 0.0f && now - start < layer.fire.duration() * 20.0f;
            clips[i] = firing ? layer.fire : layer.idle;
            times[i] = firing ? (now - start) / 20.0f : 0.0f;
            i++;
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
        return true;
    }

    private void bindClips(GemRenderPartsModel loaded) {
        NodeTable table = loaded.layout().nodeTable();
        turretClip = angleClip(table, "turret", "turret", 0.0f, 1.0f, 0.0f);
        barrelClip = angleClip(table, "barrel", "barrel", 1.0f, 0.0f, 0.0f);
        leftWheelClip = wheelsClip(table, WHEEL_L, "wheelL", 1.0f, 0.0f, 0.0f);
        rightWheelClip = wheelsClip(table, WHEEL_R, "wheelR", 1.0f, 0.0f, 0.0f);
        leftTrackClip = trackClip(table, 'L', "trackL");
        rightTrackClip = trackClip(table, 'R', "trackR");

        fireLayers.clear();
        for (VehicleBridgeProfile.FireClip fire : profile.fireClips()) {
            fireLayers.add(new FireLayer(fire.weaponKey(),
                    fire.idleName() == null ? null : loaded.animation(fire.idleName()),
                    loaded.animation(fire.fireName())));
        }

        int layers = 6 + fireLayers.size();
        clips = new GltfAnimation[layers];
        times = new float[layers];
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

    private void writeBase(float partialTick) {
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
    }

    private void deleteInstances() {
        for (TransformedInstance instance : instances) {
            if (instance != null) {
                instance.delete();
            }
        }
        instances = new TransformedInstance[0];
        transforms = new Matrix4f[0];
        model = null;
        turretClip = barrelClip = leftWheelClip = rightWheelClip = leftTrackClip = rightTrackClip = null;
        fireLayers.clear();
        clips = new GltfAnimation[0];
        times = new float[0];
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

    /** {@code timeSeconds} is the side track parameter (leftTrack / rightTrack). */
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
