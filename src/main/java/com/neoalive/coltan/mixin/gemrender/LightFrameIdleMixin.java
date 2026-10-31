package com.neoalive.coltan.mixin.gemrender;

import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wf.gemrender.light.LightFrame;
import com.wf.gemrender.light.Lights;
import net.minecraft.client.Camera;

/**
 * Skips GemRender's per-frame light collection when there is nothing to light.
 *
 * <p>GemRender 0.1.20 runs the whole light frame every level render on 1.20.1 Forge: occupancy
 * window walk (512 sections) and refills, light grid and occupancy uploads, shadow trace setup.
 * With no provider registered and the IR view OFF, the result is always the zero-light UBO, so
 * this returns right after {@code ensureBuffers()} instead:
 * <ul>
 *   <li>{@code levelGpu.begin()} has already run, which balances the {@code levelGpu.end()} that
 *       {@code end()} always calls (GPU timer queries stay paired).</li>
 *   <li>{@code ensureBuffers()} has run, so the empty UBO exists; it is rebound here every skipped
 *       frame (one GL call), as {@code begin} would, in case anything else took the binding point.</li>
 *   <li>{@code active} stays false, so {@code end()} returns early; {@code LightGlare} was already
 *       cleared, so it draws nothing.</li>
 * </ul>
 * If a mod registers a light later, the next frame runs normally and the occupancy grid refills
 * progressively, which GemRender already handles (teleport / chunk-load path).
 *
 * <p>Applies only through {@link ColtanGemRenderMixinPlugin}: GemRender present, version {@code [0.1.20, 0.2)}.
 */
@Mixin(value = LightFrame.class, remap = false)
public abstract class LightFrameIdleMixin {
    @Shadow
    private int emptyBuffer;

    @Inject(
            method = "begin(Lnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;F)V",
            at = @At(value = "INVOKE", target = "Lcom/wf/gemrender/light/LightFrame;ensureBuffers()V",
                    shift = At.Shift.AFTER),
            cancellable = true,
            remap = false
    )
    private void coltan$skipIdleFrame(Camera camera, Matrix4f view, Matrix4f projection, float partialTick,
            CallbackInfo ci) {
        if (LightsAccessor.coltan$providers().isEmpty()
                && LightsAccessor.coltan$irView().get() == Lights.IrView.OFF) {
            glBindBufferBase(GL_UNIFORM_BUFFER, LightFrame.UBO_BINDING, emptyBuffer);
            ci.cancel();
        }
    }
}
