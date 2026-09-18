package com.neoalive.coltan.client.compat;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.neoalive.coltan.client.compat.util.ModelPartPoseBridge;
import com.neoalive.coltan.client.compat.util.ModelPartPoseBridge.BoneRest;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;

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
 * Drives SEM's real {@code setupAnim} on a shadow model of the entity's own type and copies the
 * resulting {@link ModelPart} pose onto GemRender bones (see {@link ModelPartPoseBridge}), so
 * locomotion / hurt / death / aim always match stock SEM instead of a converted clip set.
 *
 * <p>{@code setupAnim} lazily creates and drives the entity's own cached {@code LayeredAnimationManager}
 * (see {@code AbstractUnit#getAnimationManager()}), so calling it here keeps state transitions in
 * sync with what stock SEM would show even though its own renderer never runs
 * ({@code skipVanillaRender(true)}).
 */
final class SemUnitShadowPose {
    private static final String[] BONES = {
            "fakeRoot", "unit", "head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"
    };

    private final HierarchicalModel<Entity> shadow;
    private final ModelPart[] parts = new ModelPart[BONES.length];
    private final BoneRest[] rest = new BoneRest[BONES.length];
    private final BoneDriver[] drivers = new BoneDriver[BONES.length];
    private boolean restCaptured;

    @Nullable
    private GltfAnimation clip;

    SemUnitShadowPose(Entity entity) {
        this.shadow = createShadow(entity);
        for (int i = 0; i < BONES.length; i++) {
            parts[i] = find(shadow.root(), BONES[i]);
            drivers[i] = new BoneDriver(BONES[i]);
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

    GltfAnimation bind(NodeTable table) {
        List<PoseDriver> bound = new ArrayList<>(BONES.length);
        for (BoneDriver driver : drivers) {
            driver.bind(table);
            if (driver.slot >= 0) {
                bound.add(driver);
            }
        }
        // Unique name per instance so PoseCache never merges two units' clips by equals().
        clip = GltfAnimation.procedural("coltan.sem_unit.shadow." + System.identityHashCode(this),
                bound.toArray(PoseDriver[]::new));
        return clip;
    }

    @Nullable
    GltfAnimation clip() {
        return clip;
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

        shadow.root().getAllParts().forEach(ModelPart::resetPose);
        if (!restCaptured) {
            captureRest();
            restCaptured = true;
        }

        shadow.setupAnim(unit, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        for (int i = 0; i < BONES.length; i++) {
            drivers[i].part = parts[i];
            drivers[i].rest = rest[i];
        }
    }

    private void captureRest() {
        for (int i = 0; i < BONES.length; i++) {
            if (parts[i] != null) {
                rest[i] = BoneRest.capture(parts[i]);
            }
        }
    }

    /** Same trick as vanilla's {@code HierarchicalModel.getAnyDescendantWithName}: works at any depth. */
    @Nullable
    private static ModelPart find(ModelPart root, String name) {
        if (name.equals("fakeRoot")) {
            return root;
        }
        return root.getAllParts()
                .filter(part -> part.hasChild(name))
                .findFirst()
                .map(part -> part.getChild(name))
                .orElse(null);
    }

    private static final class BoneDriver implements PoseDriver {
        private final String bone;
        private int slot = -1;
        @Nullable
        private NodeTable table;
        @Nullable
        private ModelPart part;
        @Nullable
        private BoneRest rest;

        BoneDriver(String bone) {
            this.bone = bone;
        }

        void bind(NodeTable table) {
            this.table = table;
            this.slot = table.slotOfName(bone);
        }

        @Override
        public void apply(float ignored, float[] scratch) {
            if (table == null || part == null || rest == null) {
                return;
            }
            ModelPartPoseBridge.write(table, scratch, slot, part, rest);
        }

        @Override
        public float cycleSeconds() {
            return 0.0f;
        }
    }
}
