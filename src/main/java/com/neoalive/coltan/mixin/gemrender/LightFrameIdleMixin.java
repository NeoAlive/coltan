package com.neoalive.coltan.mixin.gemrender;

import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;

import com.wf.gemrender.light.LightFrame;
import com.wf.gemrender.light.Lights;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skip the light frame when nothing is lit. Return after ensureBuffers so levelGpu.begin stays
 * paired with end, and rebind the empty UBO. active stays false so end returns early.
 */
@Mixin(value = LightFrame.class, remap = false)
public abstract class LightFrameIdleMixin {
    @Shadow private int emptyBuffer;

    @Inject(
            method = "begin(Lnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;F)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/wf/gemrender/light/LightFrame;ensureBuffers()V",
                            shift = At.Shift.AFTER),
            cancellable = true,
            remap = false)
    private void coltan$skipIdleFrame(
            Camera camera, Matrix4f view, Matrix4f projection, float partialTick, CallbackInfo ci) {
        if (LightsAccessor.coltan$providers().isEmpty()
                && LightsAccessor.coltan$irView().get() == Lights.IrView.OFF) {
            glBindBufferBase(GL_UNIFORM_BUFFER, LightFrame.UBO_BINDING, emptyBuffer);
            ci.cancel();
        }
    }
}
