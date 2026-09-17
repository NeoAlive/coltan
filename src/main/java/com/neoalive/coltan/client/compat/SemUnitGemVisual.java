package com.neoalive.coltan.client.compat;

import java.util.Optional;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.neoalive.coltan.client.compat.bridge.SemUnitBridgeCache;
import com.neoalive.coltan.client.compat.bridge.SemUnitBridgeProfile;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeRotation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.nekoyuni.SimpleEnemyMod.entity.unit.AbstractUnit;
import net.nekoyuni.SimpleEnemyMod.entity.unit.PmcUnitEntity;
import net.nekoyuni.SimpleEnemyMod.entity.unit.RUunitEntity;
import net.nekoyuni.SimpleEnemyMod.entity.unit.USunitEntity;

/**
 * Flywheel skinned visual for SimpleEnemyMod units.
 *
 * <p>Motion clips come from {@code unit.animation.json} (SEM keyframes). Procedural look / weapon
 * aim layers on top for idle/walk — death/hurt play clip-only. Locomotion is inferred from entity
 * motion because SEM's {@code setupAnim} does not run under {@code skipVanillaRender}.
 */
public final class SemUnitGemVisual extends GemRenderEntityVisual<Entity> {
    private static final String CLIP_IDLE = "animation.unit.idle";
    private static final String CLIP_WALK = "animation.unit.walk";
    private static final String CLIP_HURT = "animation.unit.hurt";
    private static final String CLIP_HURT_1 = "animation.unit.hurt_1";
    private static final String CLIP_DEATH = "animation.unit.death";
    private static final String CLIP_DEATH_BACK = "animation.unit.death_back";

    private final SemUnitBridgeProfile profile;
    private final Matrix4f lastWorldPose = new Matrix4f();

    @Nullable
    private AimBundle aim;
    private boolean boundClips;

    public SemUnitGemVisual(VisualizationContext ctx, Entity entity, float partialTick,
            SemUnitBridgeProfile profile) {
        super(ctx, entity, partialTick,
                SemUnitBridgeCache.handle(profile, resolveTexture(entity, profile)));
        this.profile = profile;
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
            aim = AimBundle.bind(model.layout().nodeTable());
            boundClips = true;
        }

        MotionChoice motion = selectMotion(model, partialTick);
        boolean allowAim = motion.allowProceduralAim && aim != null;
        if (allowAim) {
            updateAim(partialTick, aim);
            clips[0] = motion.clip.with(aim.head, aim.rightArm, aim.leftArm);
        } else {
            clips[0] = motion.clip;
        }
        times[0] = motion.timeSeconds;
    }

    @Override
    protected void _delete() {
        SemUnitGunOverlay.unregister(this);
        super._delete();
    }

    private MotionChoice selectMotion(GemRenderGltfModel model, float partialTick) {
        if (entity instanceof AbstractUnit unit) {
            if (unit.deathAnimationState.isStarted() || unit.isDeadOrDying()) {
                boolean back = Boolean.TRUE.equals(unit.getEntityData().get(AbstractUnit.BACK_DEATH));
                GltfAnimation clip = clipOr(model, back ? CLIP_DEATH_BACK : CLIP_DEATH, CLIP_DEATH);
                return MotionChoice.of(clip, oneshotTime(unit.deathAnimationState.getAccumulatedTime(), clip),
                        false);
            }

            int hurtTicks = unit.getEntityData().get(AbstractUnit.DAMAGE_ANIMATION_TICKS);
            if (hurtTicks > 0) {
                String name = unit.currentHurtVariant == 1 ? CLIP_HURT_1 : CLIP_HURT;
                GltfAnimation clip = clipOr(model, name, CLIP_HURT);
                float elapsed = (20 - hurtTicks + partialTick) / 20.0f;
                return MotionChoice.of(clip, clampClipTime(elapsed, clip), false);
            }

            boolean walking = unit.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6
                    || unit.walkAnimation.speed(partialTick) > 0.01f;
            if (walking) {
                GltfAnimation clip = clipOr(model, CLIP_WALK, CLIP_IDLE);
                return MotionChoice.of(clip, loopTime(partialTick, clip), true);
            }

            GltfAnimation clip = clipOr(model, CLIP_IDLE, CLIP_WALK);
            return MotionChoice.of(clip, loopTime(partialTick, clip), true);
        }

        GltfAnimation clip = clipOr(model, CLIP_IDLE, CLIP_WALK);
        return MotionChoice.of(clip, loopTime(partialTick, clip), true);
    }

    private float loopTime(float partialTick, @Nullable GltfAnimation clip) {
        float seconds = (entity.tickCount + partialTick) / 20.0f;
        if (clip == null) {
            return seconds;
        }
        float duration = clip.duration();
        return duration <= 0.0f ? seconds : clip.loop(seconds);
    }

    private static float oneshotTime(long accumulatedMillis, @Nullable GltfAnimation clip) {
        float seconds = accumulatedMillis / 1000.0f;
        return clampClipTime(seconds, clip);
    }

    private static float clampClipTime(float seconds, @Nullable GltfAnimation clip) {
        if (clip == null) {
            return Math.max(0.0f, seconds);
        }
        float duration = clip.duration();
        if (duration <= 0.0f) {
            return Math.max(0.0f, seconds);
        }
        return Mth.clamp(seconds, 0.0f, duration);
    }

    @Nullable
    private static GltfAnimation clipOr(GemRenderGltfModel model, String preferred, String fallback) {
        GltfAnimation clip = model.animation(preferred);
        if (clip != null) {
            return clip;
        }
        clip = model.animation(fallback);
        if (clip != null) {
            return clip;
        }
        return model.animationOrAny(preferred);
    }

    private void updateAim(float partialTick, AimBundle bundle) {
        float headYaw = 0.0f;
        float headPitch = 0.0f;
        if (entity instanceof LivingEntity living) {
            float bodyYaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
            float yHead = Mth.rotLerp(partialTick, living.yHeadRotO, living.getYHeadRot());
            headYaw = Mth.clamp(yHead - bodyYaw, -30.0f, 30.0f) * Mth.DEG_TO_RAD;
            headPitch = Mth.clamp(living.getViewXRot(partialTick), -25.0f, 45.0f) * Mth.DEG_TO_RAD;
        }
        bundle.head.set(headPitch, headYaw, 0.0f);
        applyWeaponPose(bundle, headYaw, headPitch);
    }

    private void applyWeaponPose(AimBundle bundle, float headYaw, float headPitch) {
        if (!(entity instanceof LivingEntity living)) {
            bundle.rightArm.set(0.0f, 0.0f, 0.0f);
            bundle.leftArm.set(0.0f, 0.0f, 0.0f);
            return;
        }

        ItemStack stack = living.getMainHandItem();
        WeaponKind kind = WeaponKind.of(stack);
        switch (kind) {
            case HEAVY -> {
                float yawFollow = 0.6f;
                float targetPitch = headPitch * 0.15f;
                bundle.rightArm.set(targetPitch + 1.48f, headYaw * yawFollow - 0.05f, 0.12f);
                bundle.leftArm.set(targetPitch + 0.26f, headYaw * yawFollow + 0.12f, -0.25f);
            }
            case PISTOL -> {
                bundle.rightArm.set(headPitch * 0.9f - 0.55f, headYaw * 0.7f, 0.0f);
                bundle.leftArm.set(headPitch * 0.5f, headYaw * 0.7f, 0.0f);
            }
            case RIFLE -> {
                bundle.rightArm.set(headPitch * 0.85f, headYaw * 0.65f, 0.0f);
                bundle.leftArm.set(headPitch * 0.85f, headYaw * 0.65f, 0.0f);
            }
            case NONE -> {
                bundle.rightArm.set(0.0f, 0.0f, 0.0f);
                bundle.leftArm.set(0.0f, 0.0f, 0.0f);
            }
        }
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

    private enum WeaponKind {
        NONE,
        RIFLE,
        PISTOL,
        HEAVY;

        static WeaponKind of(ItemStack stack) {
            if (!(stack.getItem() instanceof GunItemDataAccessor gunItem)) {
                return NONE;
            }
            ResourceLocation gunId = gunItem.getGunId(stack);
            if (gunId != null && gunId.getPath().contains("minigun")) {
                return HEAVY;
            }
            if (gunId != null) {
                Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gunId);
                if (index.isPresent() && "pistol".equals(index.get().getType())) {
                    return PISTOL;
                }
            }
            return RIFLE;
        }
    }

    private record MotionChoice(@Nullable GltfAnimation clip, float timeSeconds, boolean allowProceduralAim) {
        static MotionChoice of(@Nullable GltfAnimation clip, float time, boolean allowAim) {
            return new MotionChoice(clip, time, allowAim && clip != null);
        }
    }

    private static final class AimBundle {
        final MutableEuler head;
        final MutableEuler rightArm;
        final MutableEuler leftArm;

        private AimBundle(MutableEuler head, MutableEuler rightArm, MutableEuler leftArm) {
            this.head = head;
            this.rightArm = rightArm;
            this.leftArm = leftArm;
        }

        static AimBundle bind(NodeTable table) {
            return new AimBundle(
                    MutableEuler.of(table, "head"),
                    MutableEuler.of(table, "rightArm"),
                    MutableEuler.of(table, "leftArm"));
        }
    }

    private static final class MutableEuler implements PoseDriver {
        private final int offset;
        private float x;
        private float y;
        private float z;

        private MutableEuler(int offset) {
            this.offset = offset;
        }

        static MutableEuler of(NodeTable table, String bone) {
            int slot = table.slotOfName(bone);
            if (slot < 0) {
                return new MutableEuler(-1);
            }
            return new MutableEuler(NodeRotation.offsetOf(table, slot));
        }

        void set(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void apply(float ignored, float[] scratch) {
            if (offset < 0) {
                return;
            }
            NodeRotation.compose(scratch, offset, 1.0f, 0.0f, 0.0f, x);
            NodeRotation.compose(scratch, offset, 0.0f, 1.0f, 0.0f, y);
            NodeRotation.compose(scratch, offset, 0.0f, 0.0f, 1.0f, z);
        }

        @Override
        public float cycleSeconds() {
            return 0.0f;
        }
    }
}
