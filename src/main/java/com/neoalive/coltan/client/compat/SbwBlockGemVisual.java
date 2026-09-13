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
    private GemRenderGltfModel gltf;
    private GemRenderInstance instance;

    public SbwBlockGemVisual(VisualizationContext ctx, T be, float partialTick) {
        super(ctx, be, partialTick);
        BlockBridgeCache.Piece piece = BlockBridgeCache.piece(be.getType());
        this.handle = piece == null ? null : BlockBridgeCache.handle(piece);
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

        placeInstance(0.0f);
        instance.colorArgb(0xFFFFFFFF);
        relight(instance);
        instance.setChanged();
        return true;
    }

    private void placeInstance(float spinYawRad) {
        float facingYaw = facingYawRadians();
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
        placeInstance(spin);

        GltfAnimation clip = resolveClip();
        float time = resolveTime(clip, partialTick);

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
        // Pose matrix is rewritten every frame (facing + optional FuMO spin).
        instance.setChanged();
    }

    private GltfAnimation resolveClip() {
        if (isContainerOpened()) {
            return gltf.animationOrAny("open");
        }
        if (blockEntity instanceof BlueprintResearchTableBlockEntity table && table.getActivated()) {
            GltfAnimation run = gltf.animation("run");
            if (run == null) {
                run = gltf.animationOrAny("open");
            }
            return run;
        }
        return null;
    }

    private float resolveTime(GltfAnimation clip, float partialTick) {
        if (clip == null) {
            return 0.0f;
        }
        if (isContainerOpened()) {
            return clip.duration();
        }
        if (blockEntity instanceof BlueprintResearchTableBlockEntity) {
            float seconds = (level.getGameTime() + partialTick) / 20.0f;
            return AnimationPhase.scattered(clip, pos.asLong()).timeAt(seconds);
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
