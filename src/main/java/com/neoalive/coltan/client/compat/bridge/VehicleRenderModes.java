package com.neoalive.coltan.client.compat.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.client.renderer.entity.GeoVehicleRenderer;
import com.atsuishio.superbwarfare.resource.vehicle.VehicleResource;
import com.neoalive.coltan.debug.ColtanDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * Picks a {@link VehicleRenderMode} per vehicle type by looking at which {@link GeoVehicleRenderer}
 * hooks its renderer overrides outside SBW itself (SBW's own subclasses count as stock).
 */
public final class VehicleRenderModes {
    private static final String SBW_PACKAGE = "com.atsuishio.superbwarfare.";
    /** Pose-only hooks: everything they do lands in bone state, which replay copies. */
    private static final Set<String> POSE_HOOKS = Set.of(
            "transformCustomModelPart", "transformCustomModelPartByScript", "tickVariables");
    /**
     * Hooks that draw or place geometry outside the bone tree; GemRender cannot reproduce them.
     * {@code m_7392_} is {@code EntityRenderer.render}'s SRG name: in a production jar an addon's
     * {@code render} override also carries that bridge method.
     */
    private static final Set<String> DRAW_HOOKS = Set.of(
            "render", "m_7392_", "renderEmissive", "renderCustomPart", "customLaserLength", "rotateVehicleAxis",
            "getCurrentModelEntry");

    private static Field renderersField;

    private VehicleRenderModes() {
    }

    public record Decision(VehicleRenderMode mode, String reason) {
    }

    /**
     * Mode for {@code type}. Before entity renderers exist (pre-world catalog) this answers NATIVE;
     * the level re-sample classifies again once they do.
     */
    public static Decision classify(EntityType<?> type, ResourceLocation entityId,
            @Nullable VehicleRenderMode forced) {
        if (forced != null) {
            return new Decision(forced, "sbw_bridge renderMode");
        }
        EntityRenderer<?> renderer = rendererOf(type);
        if (renderer == null) {
            return new Decision(VehicleRenderMode.NATIVE, "renderer not built yet");
        }
        if (!(renderer instanceof GeoVehicleRenderer<?>)) {
            return new Decision(VehicleRenderMode.PASSTHROUGH,
                    "renderer " + renderer.getClass().getSimpleName() + " is not a GeoVehicleRenderer");
        }
        Set<String> overridden = addonOverrides(renderer.getClass());
        for (String hook : DRAW_HOOKS) {
            if (overridden.contains(hook)) {
                return new Decision(VehicleRenderMode.PASSTHROUGH,
                        renderer.getClass().getSimpleName() + " overrides " + hook);
            }
        }
        for (String hook : POSE_HOOKS) {
            if (overridden.contains(hook)) {
                return new Decision(VehicleRenderMode.REPLAY,
                        renderer.getClass().getSimpleName() + " overrides " + hook);
            }
        }
        if (hasScript(entityId)) {
            return new Decision(VehicleRenderMode.REPLAY, "vehicle resource has a transform script");
        }
        return new Decision(VehicleRenderMode.NATIVE, "stock pose hooks");
    }

    /** Method names declared by non-SBW classes between {@code cls} and {@link GeoVehicleRenderer}. */
    private static Set<String> addonOverrides(Class<?> cls) {
        Set<String> names = new TreeSet<>();
        for (Class<?> c = cls; c != null && c != GeoVehicleRenderer.class; c = c.getSuperclass()) {
            if (c.getName().startsWith(SBW_PACKAGE)) {
                continue;
            }
            for (Method method : c.getDeclaredMethods()) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private static boolean hasScript(ResourceLocation entityId) {
        try {
            return VehicleResource.getDefault(entityId.toString()).getScript() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static EntityRenderer<?> rendererOf(EntityType<?> type) {
        try {
            if (renderersField == null) {
                renderersField = ObfuscationReflectionHelper.findField(EntityRenderDispatcher.class, "f_114362_");
            }
            Map<EntityType<?>, EntityRenderer<?>> renderers = (Map<EntityType<?>, EntityRenderer<?>>)
                    renderersField.get(Minecraft.getInstance().getEntityRenderDispatcher());
            return renderers == null ? null : renderers.get(type);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ColtanDebug.failOnce("render-mode-renderers",
                    "cannot read entity renderers for render-mode detection: %s", e.toString());
            return null;
        }
    }
}
