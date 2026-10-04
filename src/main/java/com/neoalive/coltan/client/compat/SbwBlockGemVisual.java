package com.neoalive.coltan.client.compat;

import java.util.function.Consumer;

import com.atsuishio.superbwarfare.block.entity.BlueprintResearchTableBlockEntity;
import com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity;
import com.atsuishio.superbwarfare.block.entity.FuMO25BlockEntity;
import com.atsuishio.superbwarfare.block.entity.LuckyContainerBlockEntity;
import com.atsuishio.superbwarfare.block.entity.SmallContainerBlockEntity;
import com.neoalive.coltan.client.compat.bridge.BlockBridgeCache;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.AnimationPhase;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.render.GemRenderInstance;
import com.wf.gemrender.render.GemRenderInstanceTypes;
import com.wf.gemrender.render.PoseCache;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * GemRender skinned visual for SBW Bedrock BER blocks (INTEGRATION §2 DrillVisual shape).
 */
public final class SbwBlockGemVisual<T extends BlockEntity> extends AbstractBlockEntityVisual<T>
        implements SimpleDynamicVisual {

    private final ModelCache.Handle<GemRenderGltfModel> handle;
    /** A visual is rebuilt when its block state changes, so facing is fixed for its lifetime. */
    private final float facingYaw;
    private GemRenderGltfModel gltf;
    private GemRenderInstance instance;
    /** Clips resolved once per bound model; {@code runPhase} only for the research table. */
    private GltfAnimation openClip;
    private GltfAnimation runClip;
    private AnimationPhase runPhase;
    /** Last written pose inputs: an idle block skips the upload entirely. */
    private GltfAnimation lastClip;
    private float lastTime = Float.NaN;
    private float lastSpin = Float.NaN;

    public SbwBlockGemVisual(VisualizationContext ctx, T be, float partialTick) {
        super(ctx, be, partialTick);
        BlockBridgeCache.Piece piece = BlockBridgeCache.piece(be.getType());
        this.handle = piece == null ? null : BlockBridgeCache.handle(piece);
        this.facingYaw = facingYawRadians();
    }

    private boolean acquire() {
        if (handle == null) {
            return false;
        }
        gltf = handle.get();
        if (gltf == null) {
            return false;
        }

        instance = instancerProvider()
                .instancer(GemRenderInstanceTypes.SKINNED, gltf.model())
                .createInstance();

        openClip = gltf.animationOrAny("open");
        GltfAnimation run = gltf.animation("run");
        runClip = run != null ? run : openClip;
        runPhase = runClip != null && blockEntity instanceof BlueprintResearchTableBlockEntity
                ? AnimationPhase.scattered(runClip, pos.asLong())
                : null;

        placeInstance(0.0f);
        instance.colorArgb(0xFFFFFFFF);
        relight(instance);
        instance.setChanged();
        return true;
    }

    private void placeInstance(float spinYawRad) {
        instance.pose.translation(
                        visualPos.getX() + 0.5f,
                        visualPos.getY(),
                        visualPos.getZ() + 0.5f)
                .rotateY(facingYaw + spinYawRad);
    }

    private float facingYawRadians() {
        Direction facing = null;
        if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            facing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);
        } else if (blockState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            facing = blockState.getValue(HorizontalDirectionalBlock.FACING);
        }
        if (facing == null) {
            return 0.0f;
        }
        // Blockstate Y degrees are clockwise from north; JOML rotateY is CCW.
        return switch (facing) {
            case EAST -> -Mth.HALF_PI;
            case SOUTH -> -Mth.PI;
            case WEST -> -Mth.PI * 1.5f;
            default -> 0.0f;
        };
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        if (!isVisible(ctx.frustum()) || doDistanceLimitThisFrame(ctx)) {
            return;
        }
        if (instance == null && !acquire()) {
            return;
        }
        if (gltf == null || instance == null) {
            return;
        }

        float partialTick = ctx.partialTick();
        float spin = spinYaw(partialTick);
        boolean opened = isContainerOpened();
        GltfAnimation clip = resolveClip(opened);
        float time = resolveTime(clip, opened, partialTick);
        if (clip == lastClip && time == lastTime && spin == lastSpin) {
            return; // closed / idle block: nothing moved, skip the upload
        }
        lastClip = clip;
        lastTime = time;
        lastSpin = spin;
        placeInstance(spin);

        PoseCache.Pose pose = PoseCache.getInstance()
                .pose(gltf.layout(), gltf.bounds(), gltf.morphs(), clip, time);

        boolean poseChanged = instance.boneBase != pose.boneBase()
                || instance.morphBase != pose.morphBase()
                || !instance.boneSphere.equals(pose.sphere());
        if (poseChanged) {
            instance.boneBase = pose.boneBase();
            instance.morphBase = pose.morphBase();
            instance.boneSphere.set(pose.sphere());
        }
        instance.setChanged();
    }

    private GltfAnimation resolveClip(boolean opened) {
        if (opened) {
            return openClip;
        }
        if (blockEntity instanceof BlueprintResearchTableBlockEntity table && table.getActivated()) {
            return runClip;
        }
        return null;
    }

    private float resolveTime(GltfAnimation clip, boolean opened, float partialTick) {
        if (clip == null) {
            return 0.0f;
        }
        if (opened) {
            return clip.duration();
        }
        if (runPhase != null) {
            float seconds = (level.getGameTime() + partialTick) / 20.0f;
            return runPhase.timeAt(seconds);
        }
        return 0.0f;
    }

    private float spinYaw(float partialTick) {
        if (blockEntity instanceof FuMO25BlockEntity fumo) {
            // Radar dish: whole-model Y spin from BE tick (no dedicated spin clip in pack).
            return (fumo.getTick() + partialTick) * 0.05f;
        }
        return 0.0f;
    }

    private boolean isContainerOpened() {
        if (blockEntity instanceof ContainerBlockEntity c) {
            return c.getOpened();
        }
        if (blockEntity instanceof SmallContainerBlockEntity c) {
            return c.getOpened();
        }
        if (blockEntity instanceof LuckyContainerBlockEntity c) {
            return c.getOpened();
        }
        return false;
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        if (instance != null) {
            consumer.accept(instance);
        }
    }

    @Override
    public void updateLight(float partialTick) {
        if (instance != null) {
            relight(instance);
        }
    }

    @Override
    protected void _delete() {
        if (instance != null) {
            instance.delete();
            instance = null;
        }
    }
}
