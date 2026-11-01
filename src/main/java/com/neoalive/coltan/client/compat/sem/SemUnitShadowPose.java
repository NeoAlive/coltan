package com.neoalive.coltan.client.compat.sem;

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
import org.joml.Matrix4f;

/**
 * Flywheel beginFrame runs entity visuals on a thread pool. Each visual needs its own shadow model,
 * and every SEM setupAnim call is serialized on SEM_ANIM_LOCK.
 */
final class SemUnitShadowPose {
    static final String[] PARTS = {"head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"};

    /**
     * Guards every call into SEM's own (not known to be thread-safe) animation code, process-wide.
     */
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
        // USunitEntity, or an unrecognized SEM unit type: SemUnitDiscovery only ever
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

    /**
     * Fills {@code dest[i]} with part {@code PARTS[i]}'s current transform, relative to the
     * entity's own origin.
     */
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
