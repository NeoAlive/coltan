package com.neoalive.coltan.client.compat.fx;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.debug.ColtanDebug;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

/**
 * Instanced particle draw: a static unit quad plus one instance record per particle, drawn with
 * vanilla's particle uniforms and blend states. Plain GL 3.3 only (instanced arrays, no SSBO,
 * indirect, compute or persistent mapping). Render thread only.
 */
public final class FxRenderer {
    public static final int SOFT = 0;
    public static final int TRANSLUCENT = 1;
    public static final int LIT = 2;
    public static final int OPAQUE = 3;
    public static final int MATERIALS = 4;

    /** Bytes per instance: pos 3f, size and roll 2f, uv 4f, rgba 4ub, light 2us. */
    public static final int STRIDE = 44;

    public static final int OFF_POS = 0;
    public static final int OFF_SIZE = 12;
    public static final int OFF_ROLL = 16;
    public static final int OFF_UV = 20;
    public static final int OFF_COLOR = 36;
    public static final int OFF_LIGHT = 40;

    /** Attribute names bound to locations 0 to 5, in this order, by ShaderInstance. */
    public static final VertexFormat FORMAT =
            new VertexFormat(
                    ImmutableMap.<String, VertexFormatElement>builder()
                            .put("Corner", element(VertexFormatElement.Type.FLOAT, 2))
                            .put("IPos", element(VertexFormatElement.Type.FLOAT, 3))
                            .put("ISizeRoll", element(VertexFormatElement.Type.FLOAT, 2))
                            .put("IUV", element(VertexFormatElement.Type.FLOAT, 4))
                            .put("IColor", element(VertexFormatElement.Type.UBYTE, 4))
                            .put("ILight", element(VertexFormatElement.Type.USHORT, 2))
                            .build());

    private static final float[] CORNERS = {-1f, -1f, -1f, 1f, 1f, 1f, 1f, -1f};

    @Nullable static volatile ShaderInstance shader;

    private static int supported = -1;
    private static int quadVbo;
    private static final int[] vao = new int[MATERIALS];
    private static final int[] vbo = new int[MATERIALS];
    private static final long[] memory = new long[MATERIALS];
    private static final int[] capacity = new int[MATERIALS];
    private static final Vector3f right = new Vector3f();
    private static final Vector3f up = new Vector3f();

    private FxRenderer() {}

    private static VertexFormatElement element(VertexFormatElement.Type type, int count) {
        return new VertexFormatElement(0, type, VertexFormatElement.Usage.GENERIC, count);
    }

    /** GL 3.3 or ARB_instanced_arrays, and the shader loaded. */
    public static boolean available() {
        if (supported < 0) {
            GLCapabilities caps = GL.getCapabilities();
            supported = caps.OpenGL33 || caps.GL_ARB_instanced_arrays ? 1 : 0;
            if (supported == 0) {
                Coltan.LOGGER.warn(
                        "Coltan particle pipeline needs GL 3.3 instanced arrays; staying on vanilla");
            }
        }
        return supported == 1 && shader != null;
    }

    /** Native scratch for {@code instances} records of a material, grown on demand. */
    public static long reserve(int material, int instances) {
        if (instances > capacity[material]) {
            int grown = Math.max(instances, Math.max(256, capacity[material] * 2));
            memory[material] =
                    memory[material] == 0
                            ? MemoryUtil.nmemAlloc((long) grown * STRIDE)
                            : MemoryUtil.nmemRealloc(memory[material], (long) grown * STRIDE);
            capacity[material] = grown;
        }
        return memory[material];
    }

    /** proj times model view, for culling camera-relative positions. */
    public static Matrix4f viewProjection(Matrix4f out) {
        return out.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
    }

    /** Draws {@code counts[m]} instances from each material's scratch, in vanilla's order. */
    public static void draw(Camera camera, int[] counts, boolean[] enabled) {
        ShaderInstance program = shader;
        if (program == null) {
            return;
        }
        ensureObjects();
        right.set(1f, 0f, 0f).rotate(camera.rotation());
        up.set(0f, 1f, 0f).rotate(camera.rotation());
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
        boolean drew = false;
        for (int material : new int[] {OPAQUE, LIT, TRANSLUCENT, SOFT}) {
            int count = counts[material];
            if (count <= 0 || !enabled[material]) {
                continue;
            }
            upload(material, count);
            setUniforms(program, material);
            program.apply();
            applyState(material);
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_FAN, 0, 4, count);
            drew = true;
        }
        if (drew) {
            program.clear();
            GlStateManager._glBindVertexArray(0);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            BufferUploader.invalidate();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            if (ColtanDebug.on(ColtanDebug.Cat.FX)) {
                int error = GL11.glGetError();
                if (error != GL11.GL_NO_ERROR) {
                    ColtanDebug.failOnce(
                            "fx-gl-" + error,
                            "fx draw left GL error 0x%s",
                            Integer.toHexString(error));
                }
            }
        }
    }

    private static void setUniforms(ShaderInstance program, int material) {
        for (int i = 0; i < 12; ++i) {
            program.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }
        if (program.MODEL_VIEW_MATRIX != null) {
            program.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        }
        if (program.PROJECTION_MATRIX != null) {
            program.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }
        if (program.COLOR_MODULATOR != null) {
            program.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }
        if (program.FOG_START != null) {
            program.FOG_START.set(RenderSystem.getShaderFogStart());
        }
        if (program.FOG_END != null) {
            program.FOG_END.set(RenderSystem.getShaderFogEnd());
        }
        if (program.FOG_COLOR != null) {
            program.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }
        if (program.FOG_SHAPE != null) {
            program.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }
        var camRight = program.getUniform("CamRight");
        if (camRight != null) {
            camRight.set(right.x, right.y, right.z);
        }
        var camUp = program.getUniform("CamUp");
        if (camUp != null) {
            camUp.set(up.x, up.y, up.z);
        }
        var discard = program.getUniform("AlphaDiscard");
        if (discard != null) {
            discard.set(material == SOFT ? 0 : 1);
        }
    }

    /** The same state each vanilla or SBW render type sets in its begin. */
    private static void applyState(int material) {
        switch (material) {
            case SOFT -> {
                // SBW's soft shader json blends srcalpha, 1-srcalpha for colour and alpha.
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                RenderSystem.depthMask(false);
            }
            case TRANSLUCENT -> {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.depthMask(true);
            }
            default -> {
                RenderSystem.disableBlend();
                RenderSystem.depthMask(true);
            }
        }
    }

    private static void upload(int material, int count) {
        GlStateManager._glBindVertexArray(vao[material]);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo[material]);
        long bytes = (long) count * STRIDE;
        // Orphan, then fill: the driver never waits on last frame's copy.
        GL15.nglBufferData(GL15.GL_ARRAY_BUFFER, bytes, 0L, GL15.GL_STREAM_DRAW);
        GL15.nglBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, bytes, memory[material]);
    }

    private static void ensureObjects() {
        if (quadVbo != 0) {
            return;
        }
        quadVbo = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
        long corners = MemoryUtil.nmemAlloc(CORNERS.length * 4L);
        for (int i = 0; i < CORNERS.length; i++) {
            MemoryUtil.memPutFloat(corners + i * 4L, CORNERS[i]);
        }
        GL15.nglBufferData(GL15.GL_ARRAY_BUFFER, CORNERS.length * 4L, corners, GL15.GL_STATIC_DRAW);
        MemoryUtil.nmemFree(corners);
        for (int m = 0; m < MATERIALS; m++) {
            vao[m] = GlStateManager._glGenVertexArrays();
            vbo[m] = GlStateManager._glGenBuffers();
            GlStateManager._glBindVertexArray(vao[m]);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 8, 0L);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo[m]);
            instanceAttrib(1, 3, GL11.GL_FLOAT, false, OFF_POS);
            instanceAttrib(2, 2, GL11.GL_FLOAT, false, OFF_SIZE);
            instanceAttrib(3, 4, GL11.GL_FLOAT, false, OFF_UV);
            instanceAttrib(4, 4, GL11.GL_UNSIGNED_BYTE, true, OFF_COLOR);
            GL20.glEnableVertexAttribArray(5);
            GL30.glVertexAttribIPointer(5, 2, GL11.GL_UNSIGNED_SHORT, STRIDE, OFF_LIGHT);
            GL33.glVertexAttribDivisor(5, 1);
        }
        GlStateManager._glBindVertexArray(0);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        BufferUploader.invalidate();
        ColtanDebug.log(ColtanDebug.Cat.FX, "fx GL objects created");
    }

    private static void instanceAttrib(
            int index, int size, int type, boolean normalized, int offset) {
        GL20.glEnableVertexAttribArray(index);
        GL20.glVertexAttribPointer(index, size, type, normalized, STRIDE, offset);
        GL33.glVertexAttribDivisor(index, 1);
    }

    /** Frees GL objects and scratch. Render thread. */
    public static void close() {
        if (quadVbo != 0) {
            GlStateManager._glDeleteBuffers(quadVbo);
            quadVbo = 0;
            for (int m = 0; m < MATERIALS; m++) {
                GlStateManager._glDeleteVertexArrays(vao[m]);
                GlStateManager._glDeleteBuffers(vbo[m]);
                vao[m] = 0;
                vbo[m] = 0;
            }
        }
        for (int m = 0; m < MATERIALS; m++) {
            if (memory[m] != 0) {
                MemoryUtil.nmemFree(memory[m]);
                memory[m] = 0;
                capacity[m] = 0;
            }
        }
    }
}
