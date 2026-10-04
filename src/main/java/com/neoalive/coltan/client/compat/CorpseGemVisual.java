package com.neoalive.coltan.client.compat;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.neoalive.coltan.client.compat.bridge.SemUnitPartMesh;

import de.maxhenkel.corpse.Main;
import de.maxhenkel.corpse.entities.CorpseEntity;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.ComponentEntityVisual;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A Corpse-mod corpse of an SEM unit as six static instanced body parts (see {@link CorpseGemCompat}).
 *
 * <p>CorpseMod draws a corpse by building a fake {@code RemotePlayer} and running the full
 * {@code PlayerRenderer} on it every frame. A corpse never animates, so here the pose is written once
 * and re-uploaded only when its position, yaw, light or skin changes. A resting corpse costs one
 * compare per frame.
 *
 * <p>The pose is CorpseMod's own: {@code CorpseRenderer}'s lie-down transform, then
 * {@code LivingEntityRenderer}'s with body yaw 0 and the player's 0.9375 scale, then vanilla
 * {@code HumanoidModel}'s rest pivots. The fake player's idle arm bob at age ~1 is kept, so the arms
 * splay out exactly as they do on CorpseMod's renderer.
 */
public final class CorpseGemVisual extends ComponentEntityVisual<CorpseEntity> {
    /** {@code AnimationUtils.bobModelPart} roll at ageInTicks ≈ 1: cos(0.09) * 0.05 + 0.05. */
    static final float ARM_BOB = 0.0998f;
    /** HumanoidModel rest pivots, in pixels, in {@link SemUnitPartMesh#PARTS} order. */
    private static final float[][] PIVOTS = {
            {0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}};
    private static final float[] ROLL = {0, 0, ARM_BOB, -ARM_BOB, 0, 0};

    private final TransformedInstance[] instances = new TransformedInstance[SemUnitPartMesh.PARTS.length];
    /** Corpse-origin → model space, shared with the armor overlay. */
    private final Matrix4f local = new Matrix4f();
    private ResourceLocation bound;
    private float lastX = Float.NaN;
    private float lastY;
    private float lastZ;
    private float lastYaw;
    private int lastLight = -1;

    public CorpseGemVisual(VisualizationContext ctx, CorpseEntity entity, float partialTick) {
        super(ctx, entity, partialTick);
    }

    CorpseEntity entity() {
        return entity;
    }

    Matrix4f localMatrix() {
        return local;
    }

    @Override
    public void beginFrame(Context ctx) {
        super.beginFrame(ctx);
        ResourceLocation skin = CorpseGemCompat.skinCached(entity);
        if (skin == null) {
            if (bound != null) {
                deleteInstances();
            }
            return;
        }
        if (!skin.equals(bound)) {
            bind(skin);
        }

        float partialTick = ctx.partialTick();
        Vector3f at = getVisualPosition(partialTick);
        int light = computePackedLight(partialTick);
        float yaw = entity.getYRot();
        if (at.x == lastX && at.y == lastY && at.z == lastZ && yaw == lastYaw && light == lastLight) {
            return;
        }
        lastX = at.x;
        lastY = at.y;
        lastZ = at.z;
        lastYaw = yaw;
        lastLight = light;

        writeLocal(yaw);
        for (int i = 0; i < instances.length; i++) {
            TransformedInstance instance = instances[i];
            instance.pose.translation(at.x, at.y, at.z).mul(local)
                    .translate(PIVOTS[i][0] / 16.0f, PIVOTS[i][1] / 16.0f, PIVOTS[i][2] / 16.0f)
                    .rotateZ(ROLL[i]);
            instance.light(light);
            instance.setChanged();
        }
    }

    private void writeLocal(float yaw) {
        boolean onFace = onFace();
        local.rotationY(-yaw * Mth.DEG_TO_RAD)
                .rotateX((onFace ? 90.0f : -90.0f) * Mth.DEG_TO_RAD)
                .translate(0.0f, -1.0f, (onFace ? -2.01f : 2.01f) / 16.0f)
                .rotateY(Mth.PI)
                .scale(-1.0f, -1.0f, 1.0f)
                .scale(0.9375f)
                .translate(0.0f, -1.501f, 0.0f);
    }

    private static boolean onFace() {
        try {
            return Main.SERVER_CONFIG.spawnCorpseOnFace.get();
        } catch (RuntimeException e) {
            return false; // server config not synced yet
        }
    }

    /** Puts a shared armor model into the same rest pose the body instances use. */
    static void restPose(HumanoidModel<?> model) {
        model.young = false; // EntityModel defaults to true: half-size armor otherwise
        model.crouching = false;
        model.riding = false;
        for (ModelPart part : new ModelPart[] {model.head, model.hat, model.body, model.rightArm,
                model.leftArm, model.rightLeg, model.leftLeg}) {
            part.resetPose();
        }
        model.rightArm.zRot = ARM_BOB;
        model.leftArm.zRot = -ARM_BOB;
    }

    private void bind(ResourceLocation skin) {
        deleteInstances();
        Model[] models = SemUnitPartMesh.modelsForTexture(skin);
        for (int i = 0; i < instances.length; i++) {
            TransformedInstance instance = instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, models[i])
                    .createInstance();
            instance.colorArgb(0xFFFFFFFF);
            instances[i] = instance;
        }
        bound = skin;
        lastLight = -1; // force the first pose write
        CorpseGemCompat.register(this);
    }

    private void deleteInstances() {
        for (int i = 0; i < instances.length; i++) {
            if (instances[i] != null) {
                instances[i].delete();
                instances[i] = null;
            }
        }
        bound = null;
        CorpseGemCompat.unregister(this);
    }

    @Override
    protected void _delete() {
        CorpseGemCompat.forgetSkin(entity);
        deleteInstances();
        super._delete();
    }
}
