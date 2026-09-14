package com.neoalive.coltan.client.compat;

import java.util.Map;

import javax.annotation.Nullable;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.ArmorBridgeCache;
import com.neoalive.coltan.debug.ColtanDebug;
import com.wf.gemrender.direct.ArmorAppearance;
import com.wf.gemrender.direct.GemRenderArmorModel;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * SBW military armor drawn through GemRender's DirectRenderer path ({@link GemRenderArmorModel}).
 *
 * <p>Skips Handsome Goggles (special dual-pass glass). Crew paint goes through
 * {@link ColtanArmorSkins}.
 */
public final class SbwArmorGemCompat {
    private static final Map<String, String> BONES = Map.of(
            "head", "bipedHead",
            "body", "bipedBody",
            "left_arm", "bipedLeftArm",
            "right_arm", "bipedRightArm",
            "left_leg", "bipedLeftLeg",
            "right_leg", "bipedRightLeg");

    private static GemRenderArmorModel armorModel;

    private SbwArmorGemCompat() {
    }

    public static boolean active() {
        return ModList.get().isLoaded("gemrender") && ModList.get().isLoaded("superbwarfare");
    }

    public static void init() {
        if (!active()) {
            return;
        }
        // Do not construct GemRenderArmorModel here — PLAYER_INNER_ARMOR is not baked yet during
        // FMLClientSetup deferred work. Lazily create on first prepare() after models load.
        ArmorBridgeCache.rebuild();
        Coltan.LOGGER.info("GemRender armor bridge ready for {} SBW piece(s)", ArmorBridgeCache.pieces().size());
    }

    public static void reloadModels() {
        if (!active()) {
            return;
        }
        ArmorBridgeCache.reloadModels();
    }

    /** For {@code IClientItemExtensions.getHumanoidArmorModel} — calls {@code prepare}. */
    public static GemRenderArmorModel prepare(@Nullable LivingEntity entity, ItemStack stack,
            EquipmentSlot slot) {
        return model().prepare(entity, stack, slot);
    }

    private static GemRenderArmorModel model() {
        if (armorModel == null) {
            armorModel = new GemRenderArmorModel(APPEARANCE, BONES);
        }
        return armorModel;
    }

    private static final ArmorAppearance APPEARANCE = new ArmorAppearance() {
        @Override
        public GemRenderGltfModel model(@Nullable LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
            ArmorBridgeCache.Piece piece = ArmorBridgeCache.piece(stack.getItem());
            if (piece == null || piece.slot() != slot) {
                ColtanDebug.failOnce("armor-model-" + stack.getItem().getClass().getSimpleName() + "-" + slot,
                        "armor model null for %s slot=%s piece=%s",
                        stack.getItem().getClass().getSimpleName(), slot, piece);
                return null;
            }
            ResourceLocation texture = ColtanArmorSkins.resolve(entity, stack, piece.texture());
            return ArmorBridgeCache.handle(piece, texture).get();
        }
    };
}
