package com.neoalive.coltan.client.compat;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.neoalive.coltan.client.compat.bridge.SemUnitPartMesh;
import com.neoalive.coltan.client.compat.bridge.SemUnitRigidProfile;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.ComponentEntityVisual;
import dev.engine_room.flywheel.lib.visual.component.ShadowComponent;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.nekoyuni.SimpleEnemyMod.entity.unit.PmcUnitEntity;
import net.nekoyuni.SimpleEnemyMod.entity.unit.RUunitEntity;
import net.nekoyuni.SimpleEnemyMod.entity.unit.USunitEntity;

/**
 * Flywheel visual for a SimpleEnemyMod unit as six rigid body parts (head/body/arms/legs), each its
 * own instanced mesh built straight from SEM's vanilla cuboid data ({@link SemUnitPartMesh}).
 *
 * <p>Every frame the real SEM animation pipeline ({@link SemUnitShadowPose}) drives a shadow model,
 * and each part's own vanilla world matrix is copied directly onto its Flywheel instance. There is
 * no skinned mesh, no NodeTable, no pose cache — just six rigid transforms computed the same way
 * vanilla itself computes them, so there's no format-conversion step for a bug to hide in. The
 * trade-off is the one the Bedrock-geo attempt was trying to avoid paying twice: no shared-pose
 * reuse across instances, since each unit's pose is genuinely unique per-instance CPU work (same as
 * vanilla always required).
 */
public final class SemUnitRigidVisual extends ComponentEntityVisual<Entity> {
    private final SemUnitRigidProfile profile;
    private final SemUnitShadowPose shadowPose;
    private final TransformedInstance[] instances = new TransformedInstance[SemUnitShadowPose.PARTS.length];
    private final Matrix4f[] partLocal = new Matrix4f[SemUnitShadowPose.PARTS.length];
    private final Matrix4f entityWorld = new Matrix4f();
    private final Matrix4f scratch = new Matrix4f();
    private ResourceLocation boundTexture;

    public SemUnitRigidVisual(VisualizationContext ctx, Entity entity, float partialTick,
            SemUnitRigidProfile profile) {
        super(ctx, entity, partialTick);
        this.profile = profile;
        this.shadowPose = new SemUnitShadowPose(entity);
        for (int i = 0; i < partLocal.length; i++) {
            partLocal[i] = new Matrix4f();
        }
        addComponent(new ShadowComponent(ctx, entity).radius(0.6f));
    }

    public Entity entity() {
        return entity;
    }

    @Override
    public void beginFrame(Context ctx) {
        super.beginFrame(ctx);
        SemUnitGunOverlay.register(this);

        ResourceLocation texture = resolveTexture();
        if (!texture.equals(boundTexture)) {
            bind(texture);
        }
        if (instances[0] == null) {
            return;
        }

        float partialTick = ctx.partialTick();
        shadowPose.evaluate(entity, partialTick);
        shadowPose.computeWorldMatrices(partLocal);
        writeEntityWorld(partialTick);

        int light = computePackedLight(partialTick);
        for (int i = 0; i < instances.length; i++) {
            TransformedInstance instance = instances[i];
            scratch.set(entityWorld).mul(partLocal[i]);
            instance.pose.set(scratch);
            instance.light(light);
            instance.setChanged();
        }
    }

    private void bind(ResourceLocation texture) {
        deleteInstances();
        Model[] models = SemUnitPartMesh.modelsForTexture(texture);
        for (int i = 0; i < instances.length; i++) {
            TransformedInstance instance = instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, models[i])
                    .createInstance();
            instance.colorArgb(0xFFFFFFFF);
            instances[i] = instance;
        }
        boundTexture = texture;
    }

    /**
     * Replicates {@code LivingEntityRenderer.render()}'s exact outer transform (position, then
     * {@code rotateY(180 - bodyYaw)}, then {@code scale(-1,-1,1)}, then {@code translate(0,-1.501,0)})
     * since GemRender's Flywheel path skips that renderer entirely — every vanilla model implicitly
     * depends on this mirror/offset to appear right-side-up and facing the right way; without it the
     * body renders upside-down and yawed 180°.
     */
    private void writeEntityWorld(float partialTick) {
        Vector3f at = getVisualPosition(partialTick);
        float bodyYaw = entity instanceof LivingEntity living
                ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot)
                : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        entityWorld.translation(at.x, at.y, at.z)
                .rotateY((180.0f - bodyYaw) * Mth.DEG_TO_RAD)
                .scale(-1.0f, -1.0f, 1.0f)
                .translate(0.0f, -1.501f, 0.0f);
    }

    /** Current world matrix for a named part (e.g. {@code "rightArm"}), for the held-gun overlay. */
    Matrix4f partWorldMatrix(String partName, Matrix4f dest) {
        return dest.set(entityWorld).mul(partLocal[indexOf(partName)]);
    }

    private static int indexOf(String name) {
        String[] parts = SemUnitShadowPose.PARTS;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private ResourceLocation resolveTexture() {
        int variant = variantOf(entity);
        ResourceLocation wanted = profile.textureForVariant(variant);
        if (Minecraft.getInstance().getResourceManager().getResource(wanted).isPresent()) {
            return wanted;
        }
        return profile.defaultTexture();
    }

    private static int variantOf(Entity entity) {
        if (entity instanceof USunitEntity us) {
            return us.getVariant();
        }
        if (entity instanceof RUunitEntity ru) {
            return ru.getVariant();
        }
        if (entity instanceof PmcUnitEntity pmc) {
            return pmc.getVariant();
        }
        return 0;
    }

    private void deleteInstances() {
        for (int i = 0; i < instances.length; i++) {
            if (instances[i] != null) {
                instances[i].delete();
                instances[i] = null;
            }
        }
        boundTexture = null;
    }

    @Override
    protected void _delete() {
        SemUnitGunOverlay.unregister(this);
        deleteInstances();
        super._delete();
    }
}
