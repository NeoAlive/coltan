package com.neoalive.coltan.client.compat.sbw;

import com.atsuishio.superbwarfare.client.animation.entity.VehicleAnimationInstance;
import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance;
import com.atsuishio.superbwarfare.client.renderer.entity.GeoVehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.VehicleModelEntry;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneState;
import com.maydaymemory.mae.blend.EulerAdditiveBlender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * Copies the SBW pose by bone name and skips SBW's vertex submit. Translation is rest + (pos -
 * bind) / 16, rotation is (state * bind^-1) * rest, and a hidden bone is scale 0.
 */
final class SbwVehicleReplay {
    /**
     * tickVariables is shared by every entity of a type. Replay is serialized across Flywheel
     * workers.
     */
    private static final Object REPLAY_LOCK = new Object();

    private static EulerAdditiveBlender blender;

    private final VehicleEntity entity;

    @SuppressWarnings("rawtypes")
    private final GeoVehicleRenderer renderer;

    private final VehicleModelInstance instance;
    private final PoseStack poseStack = new PoseStack();

    private final float[] state;
    private final float[] previous;
    private final GltfAnimation clip;

    // Mapped bones, parallel arrays.
    private final int[] boneIndex;
    private final int[] slot;
    private final boolean[] ignoreVisibility;
    private final float[] bindPos;
    private final float[] restPos;
    private final Quaternionf[] bindInverse;
    private final Quaternionf[] rest;
    private final Quaternionf rotation = new Quaternionf();

    private SbwVehicleReplay(
            VehicleEntity entity,
            GeoVehicleRenderer<?> renderer,
            VehicleModelInstance instance,
            NodeTable table,
            List<int[]> pairs) {
        this.entity = entity;
        this.renderer = renderer;
        this.instance = instance;
        this.state = table.newScratch();
        table.resetToRest(state);
        this.previous = new float[state.length];
        int n = pairs.size();
        this.boneIndex = new int[n];
        this.slot = new int[n];
        this.ignoreVisibility = new boolean[n];
        this.bindPos = new float[n * 3];
        this.restPos = new float[n * 3];
        this.bindInverse = new Quaternionf[n];
        this.rest = new Quaternionf[n];
        BoneState[] bones = instance.getBoneIndexes();
        for (int k = 0; k < n; k++) {
            int bone = pairs.get(k)[0];
            int s = pairs.get(k)[1];
            BoneDefinition def = bones[bone].definition();
            boneIndex[k] = bone;
            slot[k] = s;
            // SBW toggles FX placeholders (flare / laser / waterMask / dog tags) for its own
            // passes;
            // Coltan never instances them and reads flare scale for its overlay, so keep their TRS
            // only.
            ignoreVisibility[k] = BoneInference.neverDraw(def.name());
            bindPos[k * 3] = def.bindX();
            bindPos[k * 3 + 1] = def.bindY();
            bindPos[k * 3 + 2] = def.bindZ();
            for (int axis = 0; axis < 3; axis++) {
                restPos[k * 3 + axis] = table.restTranslation(s, axis);
            }
            bindInverse[k] = new Quaternionf(def.bindRotation()).invert();
            rest[k] = new Quaternionf();
            table.restRotation(s, rest[k]);
        }
        float[] pose = state;
        this.clip =
                GltfAnimation.procedural(
                        "coltan_replay",
                        new PoseDriver() {
                            @Override
                            public void apply(float time, float[] scratch) {
                                System.arraycopy(
                                        pose, 0, scratch, 0, Math.min(pose.length, scratch.length));
                            }

                            @Override
                            public float cycleSeconds() {
                                return 0.0f;
                            }
                        });
    }

    /**
     * Binds the entity's SBW model instance to {@code table}, or null when there is nothing to
     * replay (no GeoVehicleRenderer, no model entries, no shared bone names).
     */
    @Nullable
    static SbwVehicleReplay bind(VehicleEntity entity, NodeTable table) {
        EntityRenderer<?> renderer =
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof GeoVehicleRenderer<?> geo)) {
            return null;
        }
        VehicleModelInstance instance;
        BoneState[] bones;
        // First access bakes the entity's SBW model entries (lazy): keep it on the replay lock too.
        synchronized (REPLAY_LOCK) {
            List<VehicleModelEntry> entries = entity.getModelEntries();
            if (entries == null || entries.isEmpty()) {
                return null;
            }
            // Always the full model: SBW skips animation on LOD entries, and bones map by name onto
            // whichever Coltan LOD mesh is bound.
            instance = entries.get(0).getInstance();
            bones = instance.getBoneIndexes();
        }
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < bones.length; i++) {
            if (bones[i] == null) {
                continue;
            }
            int s = table.slotOfName(bones[i].name());
            if (s >= 0 && table.isPosable(s)) {
                pairs.add(new int[] {i, s});
            }
        }
        return pairs.isEmpty() ? null : new SbwVehicleReplay(entity, geo, instance, table, pairs);
    }

    GltfAnimation clip() {
        return clip;
    }

    @SuppressWarnings("unchecked")
    boolean capture(NodeTable table, float partialTick) throws ReflectiveOperationException {
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        synchronized (REPLAY_LOCK) {
            VehicleAnimationInstance<?> animation = entity.getAnimationInstance();
            if (animation != null && !entity.getSympatheticDetonated()) {
                animation.getContext().setPartialTick(partialTick);
                animation.tick();
                instance.resetPose();
                instance.applyPose(blender().blend(instance.getBindPose(), animation.getPose()));
            } else {
                instance.resetPose();
            }
            renderer.tickVariables(entity, yaw, partialTick);
            // Hooks get a scratch stack (Coltan places the hull itself); hooks that multiply into
            // it
            // without pushing must not compound across frames.
            poseStack.last().pose().identity();
            poseStack.last().normal().identity();
            renderer.transformCustomModelPart(entity, instance, poseStack, yaw, partialTick);
            if (!poseStack.clear()) {
                // An addon hook pushed without popping: start the next frame from a clean stack.
                while (!poseStack.clear()) {
                    poseStack.popPose();
                }
            }
            copyPose(table);
        }
        if (Arrays.equals(state, previous)) {
            return false;
        }
        System.arraycopy(state, 0, previous, 0, state.length);
        return true;
    }

    private void copyPose(NodeTable table) {
        BoneState[] bones = instance.getBoneIndexes();
        for (int k = 0; k < boneIndex.length; k++) {
            BoneState bone = bones[boneIndex[k]];
            int s = slot[k];
            table.setTranslation(
                    state,
                    s,
                    restPos[k * 3] + (bone.x - bindPos[k * 3]) / 16.0f,
                    restPos[k * 3 + 1] + (bone.y - bindPos[k * 3 + 1]) / 16.0f,
                    restPos[k * 3 + 2] + (bone.z - bindPos[k * 3 + 2]) / 16.0f);
            rotation.set(bone.rotation).mul(bindInverse[k]).mul(rest[k]);
            table.setRotation(state, s, rotation);
            if (!bone.visible && !ignoreVisibility[k]) {
                table.setScale(state, s, 0.0f, 0.0f, 0.0f);
            } else {
                table.setScale(state, s, bone.xScale, bone.yScale, bone.zScale);
            }
        }
    }

    private static EulerAdditiveBlender blender() throws ReflectiveOperationException {
        EulerAdditiveBlender cached = blender;
        if (cached == null) {
            Field field = GeoVehicleRenderer.class.getDeclaredField("BLENDER");
            field.setAccessible(true);
            cached = (EulerAdditiveBlender) field.get(null);
            blender = cached;
        }
        return cached;
    }
}
