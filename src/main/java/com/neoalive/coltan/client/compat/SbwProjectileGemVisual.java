package com.neoalive.coltan.client.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.atsuishio.superbwarfare.entity.projectile.BasicGeoProjectileEntity;
import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils;
import com.neoalive.coltan.client.compat.bridge.ProjectileBridgeCache;
import com.neoalive.coltan.client.compat.bridge.ProjectileBridgeProfile;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.NodeHide;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Flywheel skinned visual for SBW Bedrock projectiles (missiles, rockets, bombs, mines, swarm drone).
 *
 * <p>The {@code flare} bone stays NodeHide'd in the main pass; emissive eyes overlay is drawn by
 * {@link SbwProjectileFlare}.
 */
public final class SbwProjectileGemVisual extends GemRenderEntityVisual<Entity> {
    private static final Map<Integer, GltfAnimation> FLARE_HIDE = new ConcurrentHashMap<>();

    private final ProjectileBridgeProfile profile;
    private final Matrix4f lastWorldPose = new Matrix4f();
    private final Matrix4f flareRestSocket = new Matrix4f();
    private boolean flareRestReady;
    @Nullable
    private GltfAnimation motion;
    @Nullable
    private GltfAnimation composed;
    private boolean boundClips;

    public SbwProjectileGemVisual(VisualizationContext ctx, Entity entity, float partialTick,
            ProjectileBridgeProfile profile) {
        super(ctx, entity, partialTick,
                ProjectileBridgeCache.handle(profile, alterTexture(entity, profile)));
        this.profile = profile;
    }

    public ProjectileBridgeProfile profile() {
        return profile;
    }

    public Entity entity() {
        return entity;
    }

    public Matrix4f lastWorldPose() {
        return lastWorldPose;
    }

    /**
     * Rest-pose flare socket (not the NodeHide'd live palette — that scales the bone to zero).
     * Identity if the model has no flare bone yet.
     */
    public Matrix4f flareRestSocket() {
        return flareRestSocket;
    }

    public boolean shouldDrawFlare() {
        if (!profile.hasFlare() || !flareRestReady || isTickHidden()) {
            return false;
        }
        if (!(entity instanceof BasicGeoProjectileEntity geo)) {
            return false;
        }
        int flareHidden = geo.getFlareHiddenTicks();
        if (entity instanceof FastThrowableProjectile fast) {
            return fast.getSyncedTick() > flareHidden;
        }
        return entity.tickCount > flareHidden;
    }

    @Override
    public boolean isVisible(FrustumIntersection frustum) {
        if (isTickHidden()) {
            return false;
        }
        return super.isVisible(frustum);
    }

    @Override
    protected void transform(Matrix4f pose, float partialTick) {
        Vector3f at = getVisualPosition(partialTick);
        pose.translation(at.x, at.y + entity.getBbHeight() * 0.5f, at.z);

        Vec3 look = entity.getLookAngle();
        float yRot;
        float xRot;
        if (look.lengthSqr() < 1.0e-8) {
            yRot = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            xRot = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            pose.rotateY(-yRot * Mth.DEG_TO_RAD)
                    .rotateX(-xRot * Mth.DEG_TO_RAD)
                    .rotateZ(180.0f * Mth.DEG_TO_RAD);
            lastWorldPose.set(pose);
            return;
        }

        yRot = (float) VehicleVecUtils.getYRotFromVector(look);
        xRot = (float) (-VehicleVecUtils.getXRotFromVector(look) + 180.0);
        pose.rotateY(yRot * Mth.DEG_TO_RAD)
                .rotateX(xRot * Mth.DEG_TO_RAD)
                .rotateZ(180.0f * Mth.DEG_TO_RAD);
        lastWorldPose.set(pose);
    }

    @Override
    protected void animate(float partialTick, GltfAnimation[] clips, float[] times) {
        if (isTickHidden()) {
            clips[0] = null;
            times[0] = 0.0f;
            return;
        }

        if (profile.hasFlare()) {
            SbwProjectileFlare.register(this);
        }

        GemRenderGltfModel model = model();
        if (model == null) {
            clips[0] = null;
            times[0] = 0.0f;
            return;
        }

        if (!boundClips) {
            bindClips(model);
            boundClips = true;
            cacheFlareRestSocket(model);
        }

        GltfAnimation clip = composed != null ? composed : motion;
        clips[0] = clip;
        if (clip == null) {
            times[0] = 0.0f;
            return;
        }

        float ageSeconds = ageSeconds(partialTick);
        if (profile.loopAnim()) {
            times[0] = worldSeconds(partialTick);
        } else {
            float duration = clip.duration();
            times[0] = duration <= 0.0f ? ageSeconds : Math.min(ageSeconds, duration);
        }
    }

    @Override
    protected void _delete() {
        SbwProjectileFlare.unregister(this);
        super._delete();
    }

    private void bindClips(GemRenderGltfModel model) {
        if (profile.loopAnim()) {
            motion = model.animation("animation.projectile.idle");
            if (motion == null) {
                motion = model.animationOrAny("animation.projectile.idle");
            }
        } else {
            motion = model.animation("animation.projectile.start");
            if (motion == null) {
                motion = model.animationOrAny("animation.projectile.start");
            }
        }

        // SBW hides flare in the main pass and draws it only as an eyes/emissive overlay.
        // Most missiles have no animation clip — still must hide flare or those zero-depth
        // atlas quads explode as giant sheets behind the body.
        if (!profile.hasFlare()) {
            composed = motion;
            return;
        }

        NodeTable table = model.layout().nodeTable();
        int slot = table.slotOfName("flare");
        if (slot < 0) {
            composed = motion;
            return;
        }

        GltfAnimation hide = FLARE_HIDE.computeIfAbsent(System.identityHashCode(table), ignored -> {
            PoseDriver driver = NodeHide.of(table, slot);
            return GltfAnimation.procedural("coltan.projectile.flare_hide", driver);
        });
        composed = motion == null
                ? hide
                : motion.with(hide.drivers().toArray(PoseDriver[]::new));
    }

    private void cacheFlareRestSocket(GemRenderGltfModel model) {
        flareRestReady = false;
        if (!profile.hasFlare()) {
            flareRestSocket.identity();
            return;
        }
        NodeTable table = model.layout().nodeTable();
        int slot = table.slotOfName("flare");
        if (slot < 0) {
            flareRestSocket.identity();
            return;
        }
        GltfPose.Scratch scratch = new GltfPose.Scratch();
        Matrix4f[] palette = scratch.palette(model.jointCount());
        GltfPose.evaluate(model.layout(), (GltfAnimation) null, 0.0f, palette, model.morphs(), null, scratch);
        flareRestSocket.set(palette[slot]);
        flareRestReady = true;
    }

    private boolean isTickHidden() {
        if (!(entity instanceof BasicGeoProjectileEntity geo)) {
            return false;
        }
        int hidden = geo.getHiddenTicks();
        if (entity instanceof FastThrowableProjectile fast) {
            return fast.getSyncedTick() <= hidden;
        }
        return entity.tickCount <= hidden;
    }

    private float ageSeconds(float partialTick) {
        if (entity instanceof FastThrowableProjectile fast) {
            return (fast.getSyncedTick() + partialTick) / 20.0f;
        }
        return (entity.tickCount + partialTick) / 20.0f;
    }

    private float worldSeconds(float partialTick) {
        if (entity.level() == null) {
            return ageSeconds(partialTick);
        }
        return (entity.level().getGameTime() + partialTick) / 20.0f;
    }

    /** UUID easter-egg alter skins for placed mines; {@code null} keeps the stock texture. */
    @Nullable
    static ResourceLocation alterTexture(Entity entity, ProjectileBridgeProfile profile) {
        String path = profile.entityId().getPath();
        long divisor;
        switch (path) {
            case "c4" -> divisor = 114L;
            case "claymore" -> divisor = 514L;
            case "edd" -> divisor = 191L;
            default -> {
                return null;
            }
        }
        if (entity.getUUID().getLeastSignificantBits() % divisor != 0L) {
            return null;
        }
        return new ResourceLocation("superbwarfare",
                "textures/bedrock/projectile/" + path + "_alter.png");
    }
}
