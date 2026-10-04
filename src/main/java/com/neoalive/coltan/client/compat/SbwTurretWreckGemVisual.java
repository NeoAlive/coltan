package com.neoalive.coltan.client.compat;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4fc;

import com.atsuishio.superbwarfare.entity.vehicle.TurretWreckEntity;
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

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.ComponentEntityVisual;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;

/**
 * GemRender visual for {@code TurretWreckEntity} — the flying turret chunk SBW spawns on
 * sympathetic detonation ({@code VehicleDestroyUtils.destroy}). Renders only the turret bone and
 * everything mounted on it (barrel, bound weapons) from the source vehicle's own bridged model, so
 * it matches the hull left behind exactly instead of drifting from vanilla's separate GeckoLib path.
 *
 * <p>Mirrors {@code TurretWreckRenderer.renderWreck}'s transform chain
 * ({@code T(entityPos) * T(0,0.6,0) * R(quaternion) * T(0,-0.6,0)} for the whole piece, then
 * {@code T(-turretPivot) * R(180°Y) * partLocal} to re-center the turret's own mesh-space pivot at
 * the wreck entity's origin and correct for the same axis flip {@code SbwVehicleGemVisual}'s
 * {@code writeBase} bakes into its own root rotation) but reads the turret pivot directly out of
 * the rest-pose part transform instead of querying a throwaway dummy entity for {@code turretPos}.
 */
public final class SbwTurretWreckGemVisual extends ComponentEntityVisual<TurretWreckEntity> {
    /** ~30% brightness, matching vanilla's {@code renderSingleBone(..., 0.3f, 0.3f, 0.3f, 1f)} tint. */
    private static final int WRECK_TINT = 0xFF4D4D4D;

    @Nullable
    private final VehicleBridgeProfile sourceProfile;
    @Nullable
    private final ModelCache.Handle<GemRenderPartsModel> handle;

    private GemRenderPartsModel model;
    private int turretSlot = -1;
    private GltfAnimation barrelClip;
    private boolean[] turretMask = new boolean[0];
    private TransformedInstance[] instances = new TransformedInstance[0];
    private Matrix4f[] transforms = new Matrix4f[0];
    private final PartsPose.Scratch scratch = new PartsPose.Scratch();

    private final Vector3f pivot = new Vector3f();
    private final Matrix4f world = new Matrix4f();
    private final Matrix4f partLocal = new Matrix4f();
    private final Matrix4f composed = new Matrix4f();
    private final Vector3f scratchPoint = new Vector3f();

    public SbwTurretWreckGemVisual(VisualizationContext ctx, TurretWreckEntity entity, float partialTick) {
        super(ctx, entity, partialTick);
        EntityType<?> type = EntityType.byString(entity.getVehicleName()).orElse(null);
        VehicleBridgeProfile source = type == null ? null : VehicleBridgeCache.profile(type);
        // Passthrough sources keep vanilla's TurretWreckRenderer (skipVanillaRender declines them).
        this.sourceProfile = source == null || source.renderMode() == VehicleRenderMode.PASSTHROUGH
                ? null : source;
        this.handle = sourceProfile == null ? null : VehicleBridgeCache.handle(sourceProfile, 0);
    }

    @Override
    public void beginFrame(Context ctx) {
        super.beginFrame(ctx);
        if (sourceProfile == null || handle == null) {
            return;
        }
        if (!acquire()) {
            if (handle.hasFailed()) {
                ColtanDebug.failOnce("turret-wreck-visual-load-failed-" + sourceProfile.entityId(),
                        "SbwTurretWreckGemVisual model load failed for %s", sourceProfile.entityId());
            }
            return;
        }

        float partialTick = ctx.partialTick();

        // TurretWreckEntity freezes xRot/xRotO to the turret's pitch at the moment it detached
        // (VehicleDestroyUtils.destroy) and never touches it again, so this stays constant for the
        // wreck's lifetime — matches vanilla using the raw (unlerped) value for the same reason.
        float barrelPitch = -entity.getXRot() * Mth.DEG_TO_RAD;
        if (barrelClip != null) {
            PartsPose.evaluate(model, barrelClip, barrelPitch, transforms, scratch);
        } else {
            PartsPose.evaluate(model, (GltfAnimation) null, 0.0f, transforms, scratch);
        }
        // The turret bone's own rest-pose translation IS its pivot offset from the vehicle root —
        // reading it back here is equivalent to vanilla's VehicleEntity.turretPos without needing a
        // throwaway entity instance to ask for it.
        transforms[turretSlot].getTranslation(pivot);

        // How far below (or above) its own pivot bone the turret's real geometry extends. SBW's
        // wreck hitbox is a fixed 1.2-tall generic box with no relation to how high a given
        // vehicle's turret pivot actually sits (BMP-2's is 2+ blocks up its hull) — without this,
        // re-centering on the pivot alone leaves the piece resting with its real footprint buried
        // in the terrain and only whatever sits above the pivot poking out, which reads as
        // "floating" once it's static enough to compare against the ground. Measured from each
        // turret-mask part's own bounding sphere so it's correct for any vehicle's proportions
        // instead of a guessed constant.
        float restLowestY = Float.POSITIVE_INFINITY;
        for (int part = 0; part < instances.length; part++) {
            if (instances[part] == null) {
                continue;
            }
            computePartLocal(part);
            Vector4fc sphere = model.parts().get(part).model().boundingSphere();
            scratchPoint.set(sphere.x(), sphere.y(), sphere.z());
            partLocal.transformPosition(scratchPoint);
            float bottom = scratchPoint.y - sphere.w();
            if (bottom < restLowestY) {
                restLowestY = bottom;
            }
        }
        if (!Float.isFinite(restLowestY)) {
            restLowestY = 0.0f;
        }

        Vector3f at = getVisualPosition(partialTick);
        Quaternionf q = entity.getQuaternion(partialTick);

        world.translation(at.x, at.y - restLowestY, at.z)
                .translate(0.0f, 0.6f, 0.0f)
                .rotate(q)
                .translate(0.0f, -0.6f, 0.0f);

        int light = computePackedLight(partialTick);

        for (int part = 0; part < instances.length; part++) {
            TransformedInstance instance = instances[part];
            if (instance == null) {
                continue;
            }
            computePartLocal(part);
            composed.set(world).mul(partLocal);
            instance.pose.set(composed);
            instance.colorArgb(WRECK_TINT);
            instance.light(light);
            instance.setChanged();
        }
    }

    private void computePartLocal(int part) {
        partLocal.translation(-pivot.x, -pivot.y, -pivot.z)
                .rotateY(180.0f * Mth.DEG_TO_RAD)
                .mul(transforms[part]);
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
        this.transforms = loaded.newTransforms();
        bindTurret(loaded);
        if (turretSlot < 0) {
            // Profile has no turret bone — shouldn't happen (only hasTurret() vehicles spawn this
            // entity), but leave nothing rendered rather than guess.
            deleteInstances();
            return false;
        }

        int parts = loaded.partCount();
        this.instances = new TransformedInstance[parts];
        for (int part = 0; part < parts; part++) {
            if (!turretMask[part]) {
                continue;
            }
            Model mesh = loaded.parts().get(part).model();
            if (mesh == null) {
                continue;
            }
            TransformedInstance instance = instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, mesh)
                    .createInstance();
            instances[part] = instance;
        }
        return true;
    }

    private void bindTurret(GemRenderPartsModel loaded) {
        NodeTable table = loaded.layout().nodeTable();
        String turretBone = sourceProfile.resolveBone("turret");
        turretSlot = table.slotOfName(turretBone);
        GltfAnimation turretClip = turretSlot < 0 ? null : angleClip(table, "turret", turretBone, 0.0f, 1.0f, 0.0f);
        // Everything that moves when the turret bone rotates is the turret itself or something
        // mounted on it — exactly the set of parts that flew off with this wreck.
        turretMask = turretClip == null ? new boolean[loaded.partCount()] : loaded.drivenBy(turretClip);
        barrelClip = angleClip(table, "barrel", sourceProfile.resolveBone("barrel"), 1.0f, 0.0f, 0.0f);
    }

    private static GltfAnimation angleClip(NodeTable table, String clipName, String bone, float ax, float ay,
            float az) {
        int slot = table.slotOfName(bone);
        if (slot < 0) {
            return null;
        }
        return GltfAnimation.procedural(clipName, BoneAngle.about(table, slot, ax, ay, az));
    }

    private void deleteInstances() {
        for (TransformedInstance instance : instances) {
            if (instance != null) {
                instance.delete();
            }
        }
        instances = new TransformedInstance[0];
        transforms = new Matrix4f[0];
        turretMask = new boolean[0];
        turretSlot = -1;
        barrelClip = null;
        model = null;
    }

    @Override
    protected void _delete() {
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
}
