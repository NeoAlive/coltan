package com.neoalive.coltan.client.compat.util;

import org.joml.Quaternionf;

import com.wf.gemrender.gltf.NodeTable;

import net.minecraft.client.model.geom.ModelPart;

/**
 * Copies a live vanilla {@link ModelPart}'s pose onto a GemRender bone as a local-space delta from
 * a captured rest pose, composed onto the geo's own authored rest transform via quaternions.
 *
 * <p>Per-axis Euler subtraction (old {@code SemUnitMojmapPose}) does not work once more than one
 * rotation axis moves at once — Euler components don't add/subtract linearly, which is what
 * produced the jitter/floating-mesh bugs that led to reverting the first SEM compat attempt.
 */
public final class ModelPartPoseBridge {
    private ModelPartPoseBridge() {
    }

    /** Rest-pose snapshot of a single {@link ModelPart}, captured once before any animation runs. */
    public record BoneRest(float x, float y, float z, float xRot, float yRot, float zRot,
            float xScale, float yScale, float zScale) {
        public static BoneRest capture(ModelPart part) {
            return new BoneRest(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
                    part.xScale, part.yScale, part.zScale);
        }
    }

    public static void write(NodeTable table, float[] scratch, int slot, ModelPart part, BoneRest rest) {
        if (slot < 0) {
            return;
        }

        // Local-space delta: "the rotation SEM applied, expressed in the bone's own rest frame" —
        // reapplying that same local delta on top of a *different* rest orientation (the geo's) is
        // the standard way to retarget a pose across two skeletons that share one bind pose.
        Quaternionf vRest = new Quaternionf().rotationZYX(rest.zRot(), rest.yRot(), rest.xRot());
        Quaternionf vLive = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
        Quaternionf delta = vRest.invert(new Quaternionf()).mul(vLive);

        Quaternionf geoRest = new Quaternionf();
        table.restRotation(slot, geoRest);
        Quaternionf finalRot = new Quaternionf();
        geoRest.mul(delta, finalRot);
        table.setRotation(scratch, slot, finalRot);

        float dx = (part.x - rest.x()) / 16.0f;
        float dy = (part.y - rest.y()) / 16.0f;
        float dz = (part.z - rest.z()) / 16.0f;
        table.setTranslation(scratch, slot,
                table.restTranslation(slot, 0) + dx,
                table.restTranslation(slot, 1) + dy,
                table.restTranslation(slot, 2) + dz);

        int s = slot * NodeTable.TRS_STRIDE + NodeTable.SCALE;
        scratch[s] *= rest.xScale() == 0.0f ? 1.0f : part.xScale / rest.xScale();
        scratch[s + 1] *= rest.yScale() == 0.0f ? 1.0f : part.yScale / rest.yScale();
        scratch[s + 2] *= rest.zScale() == 0.0f ? 1.0f : part.zScale / rest.zScale();
    }
}
