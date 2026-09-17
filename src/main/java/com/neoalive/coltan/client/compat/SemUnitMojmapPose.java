package com.neoalive.coltan.client.compat;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeRotation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.nekoyuni.SimpleEnemyMod.entity.client.us_unit.USunitModel;
import net.nekoyuni.SimpleEnemyMod.entity.client.util.UnitModelDefinitions;
import net.nekoyuni.SimpleEnemyMod.entity.unit.AbstractUnit;

/**
 * Evaluates SEM's Mojmap {@code AnimationDefinition} path on a shadow {@link USunitModel} and
 * exposes the result as GemRender {@link PoseDriver}s (deltas from SEM rest → geo rest).
 */
final class SemUnitMojmapPose {
    private static final String[] BONES = {
            "fakeRoot", "unit", "head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"
    };

    private final USunitModel<Entity> shadow;
    private final ModelPart[] parts = new ModelPart[BONES.length];
    private final float[] restX = new float[BONES.length];
    private final float[] restY = new float[BONES.length];
    private final float[] restZ = new float[BONES.length];
    private final float[] restXRot = new float[BONES.length];
    private final float[] restYRot = new float[BONES.length];
    private final float[] restZRot = new float[BONES.length];
    private final float[] restXScale = new float[BONES.length];
    private final float[] restYScale = new float[BONES.length];
    private final float[] restZScale = new float[BONES.length];

    private final BoneDelta[] deltas = new BoneDelta[BONES.length];
    @Nullable
    private GltfAnimation clip;
    private boolean restCaptured;
    /** Changes every evaluate so PoseCache cannot reuse a stale bucket-0 pose. */
    private float cacheKey;

    SemUnitMojmapPose() {
        ModelPart root = UnitModelDefinitions.createBaseUnitBodyLayer().bakeRoot();
        this.shadow = new USunitModel<>(root);
        for (int i = 0; i < BONES.length; i++) {
            parts[i] = find(shadow.root(), BONES[i]);
            deltas[i] = new BoneDelta(BONES[i]);
        }
    }

    GltfAnimation bind(NodeTable table) {
        List<PoseDriver> drivers = new ArrayList<>(BONES.length);
        for (int i = 0; i < BONES.length; i++) {
            deltas[i].bind(table);
            if (deltas[i].offset >= 0) {
                drivers.add(deltas[i]);
            }
        }
        // Unique name per instance so PoseCache never merges two units' clips by equals().
        clip = GltfAnimation.procedural("coltan.sem_unit.mojmap." + System.identityHashCode(this),
                drivers.toArray(PoseDriver[]::new));
        return clip;
    }

    @Nullable
    GltfAnimation clip() {
        return clip;
    }

    /** PoseCache time bucket — must change when mutable drivers change. */
    float cacheKey() {
        return cacheKey;
    }

    void evaluate(Entity entity, float partialTick) {
        if (!(entity instanceof AbstractUnit unit)) {
            clearDeltas();
            cacheKey = 0.0f;
            return;
        }

        float limbSwing = unit.walkAnimation.position(partialTick);
        float limbSwingAmount = unit.walkAnimation.speed(partialTick);
        float ageInTicks = unit.tickCount + partialTick;
        float bodyYaw = Mth.rotLerp(partialTick, unit.yBodyRotO, unit.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, unit.yHeadRotO, unit.getYHeadRot());
        float netHeadYaw = headYaw - bodyYaw;
        float headPitch = Mth.lerp(partialTick, unit.xRotO, unit.getXRot());

        shadow.root().getAllParts().forEach(ModelPart::resetPose);
        if (!restCaptured) {
            captureRest();
            restCaptured = true;
        }

        shadow.setupAnim(unit, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // Death root-motion uses large negative unit.y. XY-flipping that lifts the mesh mid-air.
        // Keep locomotion XY-flip; death/hurt keep raw SEM position Y (and full raw deltas on death).
        boolean death = unit.deathAnimationState.isStarted();
        boolean hurt = !death && unit.hurtAnimationState.isStarted();

        int hash = entity.getId() * 31 + Float.floatToIntBits(ageInTicks);
        for (int i = 0; i < BONES.length; i++) {
            ModelPart part = parts[i];
            if (part == null) {
                deltas[i].clear();
                continue;
            }
            float dx = (part.x - restX[i]) / 16.0f;
            float dy = (part.y - restY[i]) / 16.0f;
            float dz = (part.z - restZ[i]) / 16.0f;
            float dXRot = part.xRot - restXRot[i];
            float dYRot = part.yRot - restYRot[i];
            float dZRot = part.zRot - restZRot[i];

            if (death) {
                // Faithful SEM death/back death: no XY remap (lands instead of floating).
                deltas[i].dx = dx;
                deltas[i].dy = dy;
                deltas[i].dz = dz;
                deltas[i].dXRot = dXRot;
                deltas[i].dYRot = dYRot;
                deltas[i].dZRot = dZRot;
            } else {
                // LivingEntityRenderer scale(-1,-1,1) dual for idle/walk/aim on baked geo.
                deltas[i].dx = -dx;
                // Hurt also sinks/shifts on Y — keep raw Y like death to avoid hops.
                deltas[i].dy = hurt ? dy : -dy;
                deltas[i].dz = dz;
                deltas[i].dXRot = -dXRot;
                deltas[i].dYRot = -dYRot;
                deltas[i].dZRot = dZRot;
            }
            deltas[i].xScale = part.xScale / (restXScale[i] == 0.0f ? 1.0f : restXScale[i]);
            deltas[i].yScale = part.yScale / (restYScale[i] == 0.0f ? 1.0f : restYScale[i]);
            deltas[i].zScale = part.zScale / (restZScale[i] == 0.0f ? 1.0f : restZScale[i]);

            hash = hash * 31 + Float.floatToIntBits(deltas[i].dx);
            hash = hash * 31 + Float.floatToIntBits(deltas[i].dy);
            hash = hash * 31 + Float.floatToIntBits(deltas[i].dz);
            hash = hash * 31 + Float.floatToIntBits(deltas[i].dXRot);
            hash = hash * 31 + Float.floatToIntBits(deltas[i].dYRot);
            hash = hash * 31 + Float.floatToIntBits(deltas[i].dZRot);
            hash = hash * 31 + Float.floatToIntBits(deltas[i].xScale);
            hash = hash * 31 + Float.floatToIntBits(deltas[i].yScale);
            hash = hash * 31 + Float.floatToIntBits(deltas[i].zScale);
        }
        // Finite positive key so PoseCache (quantum 1/128s) gets a fresh bucket when drivers move.
        cacheKey = ((hash & 0x7fffffff) * (1.0f / 1_048_576.0f)) + 1.0e-4f;
    }

    private void captureRest() {
        for (int i = 0; i < BONES.length; i++) {
            ModelPart part = parts[i];
            if (part == null) {
                continue;
            }
            restX[i] = part.x;
            restY[i] = part.y;
            restZ[i] = part.z;
            restXRot[i] = part.xRot;
            restYRot[i] = part.yRot;
            restZRot[i] = part.zRot;
            restXScale[i] = part.xScale;
            restYScale[i] = part.yScale;
            restZScale[i] = part.zScale;
        }
    }

    private void clearDeltas() {
        for (BoneDelta delta : deltas) {
            delta.clear();
        }
    }

    @Nullable
    private static ModelPart find(ModelPart root, String name) {
        if (name.equals("fakeRoot")) {
            return root;
        }
        try {
            if (root.hasChild(name)) {
                return root.getChild(name);
            }
            if (root.hasChild("unit")) {
                ModelPart unit = root.getChild("unit");
                if (name.equals("unit")) {
                    return unit;
                }
                if (unit.hasChild(name)) {
                    return unit.getChild(name);
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static final class BoneDelta implements PoseDriver {
        private final String bone;
        private int offset = -1;
        private int slot = -1;
        @Nullable
        private NodeTable table;
        float dx;
        float dy;
        float dz;
        float dXRot;
        float dYRot;
        float dZRot;
        float xScale = 1.0f;
        float yScale = 1.0f;
        float zScale = 1.0f;

        BoneDelta(String bone) {
            this.bone = bone;
        }

        void bind(NodeTable table) {
            this.table = table;
            this.slot = table.slotOfName(bone);
            this.offset = slot < 0 ? -1 : NodeRotation.offsetOf(table, slot);
        }

        void clear() {
            dx = dy = dz = 0.0f;
            dXRot = dYRot = dZRot = 0.0f;
            xScale = yScale = zScale = 1.0f;
        }

        @Override
        public void apply(float ignored, float[] scratch) {
            if (offset < 0 || table == null || slot < 0) {
                return;
            }
            NodeRotation.compose(scratch, offset, 0.0f, 0.0f, 1.0f, dZRot);
            NodeRotation.compose(scratch, offset, 0.0f, 1.0f, 0.0f, dYRot);
            NodeRotation.compose(scratch, offset, 1.0f, 0.0f, 0.0f, dXRot);

            int t = slot * NodeTable.TRS_STRIDE + NodeTable.TRANSLATION;
            scratch[t] += dx;
            scratch[t + 1] += dy;
            scratch[t + 2] += dz;

            if (xScale != 1.0f || yScale != 1.0f || zScale != 1.0f) {
                int s = slot * NodeTable.TRS_STRIDE + NodeTable.SCALE;
                scratch[s] *= xScale;
                scratch[s + 1] *= yScale;
                scratch[s + 2] *= zScale;
            }
        }

        @Override
        public float cycleSeconds() {
            return 0.0f;
        }
    }
}
