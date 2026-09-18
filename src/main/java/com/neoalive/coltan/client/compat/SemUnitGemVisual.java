package com.neoalive.coltan.client.compat;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.neoalive.coltan.client.compat.bridge.SemUnitBridgeCache;
import com.neoalive.coltan.client.compat.bridge.SemUnitBridgeProfile;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.nekoyuni.SimpleEnemyMod.entity.unit.PmcUnitEntity;
import net.nekoyuni.SimpleEnemyMod.entity.unit.RUunitEntity;
import net.nekoyuni.SimpleEnemyMod.entity.unit.USunitEntity;

/**
 * Flywheel skinned visual for SimpleEnemyMod units.
 *
 * <p>Mesh/textures come from Coltan Bedrock geo + SEM PNGs. Bone motion is evaluated by SEM's own
 * animation pipeline via a shadow model ({@link SemUnitShadowPose}), so death / hurt / locomotion /
 * aim always match stock SEM rather than a converted Bedrock clip set.
 */
public final class SemUnitGemVisual extends GemRenderEntityVisual<Entity> {
    private final SemUnitBridgeProfile profile;
    private final Matrix4f lastWorldPose = new Matrix4f();
    private final SemUnitShadowPose shadowPose;

    @Nullable
    private GltfAnimation poseClip;
    private boolean boundClips;

    public SemUnitGemVisual(VisualizationContext ctx, Entity entity, float partialTick,
            SemUnitBridgeProfile profile) {
        super(ctx, entity, partialTick,
                SemUnitBridgeCache.handle(profile, resolveTexture(entity, profile)));
        this.profile = profile;
        this.shadowPose = new SemUnitShadowPose(entity);
    }

    public SemUnitBridgeProfile profile() {
        return profile;
    }

    public Entity entity() {
        return entity;
    }

    public Matrix4f lastWorldPose() {
        return lastWorldPose;
    }

    @Override
    protected void transform(Matrix4f pose, float partialTick) {
        Vector3f at = getVisualPosition(partialTick);
        pose.translation(at.x, at.y, at.z);

        float bodyYaw;
        if (entity instanceof LivingEntity living) {
            bodyYaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
        } else {
            bodyYaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        }
        pose.rotateY(-(bodyYaw) * Mth.DEG_TO_RAD);
        lastWorldPose.set(pose);
    }

    @Override
    protected void animate(float partialTick, GltfAnimation[] clips, float[] times) {
        SemUnitGunOverlay.register(this);

        GemRenderGltfModel model = model();
        if (model == null) {
            clips[0] = null;
            times[0] = 0.0f;
            return;
        }

        if (!boundClips) {
            poseClip = shadowPose.bind(model.layout().nodeTable());
            boundClips = true;
        }

        shadowPose.evaluate(entity, partialTick);
        clips[0] = poseClip;
        // Real, ever-increasing time — PoseCache keys on (clip, time-bucket) and this clip's name is
        // already unique per instance, so there's nothing to gain from faking a cache-busting value.
        times[0] = entity.tickCount + partialTick;
    }

    @Override
    protected void _delete() {
        SemUnitGunOverlay.unregister(this);
        super._delete();
    }

    static ResourceLocation resolveTexture(Entity entity, SemUnitBridgeProfile profile) {
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
}
