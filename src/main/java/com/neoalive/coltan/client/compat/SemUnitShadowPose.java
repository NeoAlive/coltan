package com.neoalive.coltan.client.compat;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.nekoyuni.SimpleEnemyMod.entity.client.pmc_unit.PmcUnitModel;
import net.nekoyuni.SimpleEnemyMod.entity.client.ru_unit.RUunitModel;
import net.nekoyuni.SimpleEnemyMod.entity.client.us_unit.USunitModel;
import net.nekoyuni.SimpleEnemyMod.entity.unit.AbstractUnit;
import net.nekoyuni.SimpleEnemyMod.entity.unit.PmcUnitEntity;
import net.nekoyuni.SimpleEnemyMod.entity.unit.RUunitEntity;

/**
 * Drives SEM's real {@code setupAnim} on a shadow model of the entity's own type, so locomotion /
 * hurt / death / aim always match stock SEM instead of a reimplementation.
 *
 * <p>Flywheel's {@code beginFrame} for entity visuals runs through a real thread-pool
 * {@code TaskExecutor} ({@code VisualizationManagerImpl}'s frame plan is a {@code SimplePlan} of
 * parallel tasks) — <b>not</b> single-threaded-sequential as this class originally assumed. Two
 * consequences follow: (1) the shadow model must be a private instance per visual, never a shared
 * singleton — a shared model was mutated and read by multiple SEM-unit visuals' threads at once,
 * which is what made limb jitter dramatically worse, not better; (2) SEM's own {@code setupAnim} /
 * {@code LayeredAnimationManager} / procedural-layer code was written assuming vanilla's
 * single-threaded render loop and is not known to be safe under concurrent calls (e.g. any reused
 * scratch buffers in SEM's own library code), so every call into it is serialized behind
 * {@link #SEM_ANIM_LOCK} regardless of how many unit types or instances Flywheel evaluates in the
 * same frame — this is the fix for the jitter that was already present before the (reverted) shared
 * singleton experiment.
 *
 * <p>Each body part is read back as a real vanilla {@link ModelPart} world transform (via
 * {@link ModelPart#translateAndRotate}) and handed straight to a Flywheel instance — there is no
 * mesh/format conversion step here, so there is no coordinate system for a pivot or rotation sign
 * bug to hide in (unlike the Bedrock-geo path this replaced).
 */
final class SemUnitShadowPose {
    static final String[] PARTS = {"head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"};

    /** Guards every call into SEM's own (not known to be thread-safe) animation code, process-wide. */
    private static final Object SEM_ANIM_LOCK = new Object();

    private final HierarchicalModel<Entity> shadow;
    private final ModelPart unit;
    private final ModelPart[] parts = new ModelPart[PARTS.length];

    private final PoseStack unitChain = new PoseStack();
    private final PoseStack partLocal = new PoseStack();
    private final Matrix4f unitMatrix = new Matrix4f();

    SemUnitShadowPose(Entity entity) {
        this.shadow = createShadow(entity);
        this.unit = shadow.root().getChild("unit");
        for (int i = 0; i < PARTS.length; i++) {
            parts[i] = unit.getChild(PARTS[i]);
        }
    }

    private static HierarchicalModel<Entity> createShadow(Entity entity) {
        if (entity instanceof RUunitEntity) {
            return new RUunitModel<>(RUunitModel.createBodyLayer().bakeRoot());
        }
        if (entity instanceof PmcUnitEntity) {
            return new PmcUnitModel<>(PmcUnitModel.createBodyLayer().bakeRoot());
        }
        // USunitEntity, or an unrecognized SEM unit type — SemUnitDiscovery only ever
        // registers these three, so this is the sole remaining/fallback case.
        return new USunitModel<>(USunitModel.createBodyLayer().bakeRoot());
    }

    void evaluate(Entity entity, float partialTick) {
        if (!(entity instanceof AbstractUnit unit)) {
            return;
        }

        float limbSwing = unit.walkAnimation.position(partialTick);
        float limbSwingAmount = unit.walkAnimation.speed(partialTick);
        float ageInTicks = unit.tickCount + partialTick;
        float bodyYaw = Mth.rotLerp(partialTick, unit.yBodyRotO, unit.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, unit.yHeadRotO, unit.getYHeadRot());
        float netHeadYaw = headYaw - bodyYaw;
        float headPitch = Mth.lerp(partialTick, unit.xRotO, unit.getXRot());

        synchronized (SEM_ANIM_LOCK) {
            shadow.setupAnim(unit, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }

    /** Fills {@code dest[i]} with part {@code PARTS[i]}'s current transform, relative to the entity's own origin. */
    void computeWorldMatrices(Matrix4f[] dest) {
        unitChain.setIdentity();
        shadow.root().translateAndRotate(unitChain);
        unit.translateAndRotate(unitChain);
        unitMatrix.set(unitChain.last().pose());

        for (int i = 0; i < PARTS.length; i++) {
            partLocal.setIdentity();
            parts[i].translateAndRotate(partLocal);
            dest[i].set(unitMatrix).mul(partLocal.last().pose());
        }
    }
}
