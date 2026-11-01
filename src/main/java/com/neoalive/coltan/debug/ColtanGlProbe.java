package com.neoalive.coltan.debug;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;

/**
 * Debug category {@code gl} only. Compares GlStateManager's cached texture state with the real GL
 * state at each frame stage, and reports the local player's skin texture. A mismatch means some
 * code changed GL texture state behind GlStateManager; the first stage that reports it follows the
 * pass that did it.
 */
public final class ColtanGlProbe {
    private static final int UNITS = 3;

    private static Field texturesField;
    private static Field bindingField;
    private static boolean reflectionFailed;

    private ColtanGlProbe() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ColtanGlProbe::onStage);
        MinecraftForge.EVENT_BUS.addListener(ColtanGlProbe::onHand);
        MinecraftForge.EVENT_BUS.addListener(ColtanGlProbe::onGui);
    }

    private static void onStage(RenderLevelStageEvent event) {
        if (ColtanDebug.on(ColtanDebug.Cat.GL)) {
            check(event.getStage().toString());
        }
    }

    private static void onHand(RenderHandEvent event) {
        if (ColtanDebug.on(ColtanDebug.Cat.GL)
                && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            check("before_hand");
            skin();
        }
    }

    private static void onGui(RenderGuiEvent.Pre event) {
        if (ColtanDebug.on(ColtanDebug.Cat.GL)) {
            check("before_gui");
        }
    }

    private static void check(String stage) {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        int cachedActive = GlStateManager._getActiveTexture();
        int realActive = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        StringBuilder bad = new StringBuilder();
        if (cachedActive != realActive) {
            bad.append(" active cached=")
                    .append(cachedActive - GL13C.GL_TEXTURE0)
                    .append(" real=")
                    .append(realActive - GL13C.GL_TEXTURE0);
        }
        int[] cached = cachedBindings();
        if (cached != null) {
            for (int unit = 0; unit < UNITS; unit++) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
                int real = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
                if (real != cached[unit]) {
                    bad.append(" unit")
                            .append(unit)
                            .append(" cached=")
                            .append(cached[unit])
                            .append(" real=")
                            .append(real);
                }
            }
            GL13C.glActiveTexture(realActive);
        }
        if (bad.length() > 0) {
            ColtanDebug.whenChanged(
                    ColtanDebug.Cat.GL,
                    "gl-desync-" + stage,
                    bad.toString(),
                    "GlStateManager out of sync at %s:%s",
                    stage,
                    bad);
        }
    }

    private static void skin() {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null) {
            return;
        }
        ResourceLocation id = player.getSkinTextureLocation();
        AbstractTexture texture = mc.getTextureManager().getTexture(id);
        int glId = texture.getId();
        boolean live = GL11C.glIsTexture(glId);
        int width = -1;
        if (live) {
            int realActive = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, glId);
            width = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
            GL13C.glActiveTexture(realActive);
        }
        String state =
                id
                        + " "
                        + texture.getClass().getName()
                        + " glId="
                        + glId
                        + " live="
                        + live
                        + " width="
                        + width;
        ColtanDebug.whenChanged(
                ColtanDebug.Cat.GL, "gl-skin", state, "player skin texture %s", state);
    }

    /** GlStateManager.TEXTURES[i].binding, found by type so it works with and without mappings. */
    private static int[] cachedBindings() {
        if (reflectionFailed) {
            return null;
        }
        try {
            if (texturesField == null) {
                for (Field field : GlStateManager.class.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                            && field.getType().isArray()
                            && field.getType()
                                    .getComponentType()
                                    .getName()
                                    .endsWith("GlStateManager$TextureState")) {
                        field.setAccessible(true);
                        texturesField = field;
                        break;
                    }
                }
                if (texturesField == null) {
                    throw new NoSuchFieldException("GlStateManager texture states");
                }
                for (Field field : texturesField.getType().getComponentType().getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                        field.setAccessible(true);
                        bindingField = field;
                        break;
                    }
                }
                if (bindingField == null) {
                    throw new NoSuchFieldException("TextureState binding");
                }
            }
            Object states = texturesField.get(null);
            int[] out = new int[UNITS];
            for (int i = 0; i < UNITS; i++) {
                out[i] = bindingField.getInt(Array.get(states, i));
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            ColtanDebug.failOnce(
                    "gl-probe-reflection",
                    "GL probe cannot read GlStateManager texture cache: %s",
                    e.toString());
            return null;
        }
    }
}
