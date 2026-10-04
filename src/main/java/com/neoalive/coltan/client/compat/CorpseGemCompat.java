package com.neoalive.coltan.client.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.bridge.ArmorBridgeCache;
import com.wf.gemrender.direct.GemRenderArmorModel;

import de.maxhenkel.corpse.Main;
import de.maxhenkel.corpse.entities.CorpseEntity;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * GemRender × Corpse (henkelmax): SEM unit corpses drawn as static instanced bodies
 * ({@link CorpseGemVisual}) with SBW armor through the same {@link GemRenderArmorModel} path living
 * units use. The held weapon is never drawn.
 *
 * <p><b>SEM corpses only.</b> {@link ColtanCorpseSkins} answers null for a player corpse (only
 * tacz_sewv registers a resolver, and it recognises its own corpses alone), and {@link #skin} also
 * declines a skeleton or any armor piece Coltan has not bridged. Every declined corpse keeps
 * CorpseMod's own renderer, chosen per corpse per frame through {@code skipVanillaRender}, the same
 * shape as the turret-wreck bridge.
 */
public final class CorpseGemCompat {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final Map<Integer, CorpseGemVisual> LIVE = new ConcurrentHashMap<>();
    /** SBW armor is the only armor Coltan bridges; without SBW any worn piece declines the corpse. */
    private static boolean sbw;

    private CorpseGemCompat() {
    }

    public static void init(FMLClientSetupEvent event) {
        sbw = ModList.get().isLoaded("superbwarfare");
        event.enqueueWork(CorpseGemCompat::registerVisualizer);
        MinecraftForge.EVENT_BUS.addListener(CorpseGemCompat::onRenderLevel);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerVisualizer() {
        SimpleEntityVisualizer.builder((EntityType) Main.CORPSE_ENTITY_TYPE.get())
                .factory((ctx, entity, partialTick) ->
                        new CorpseGemVisual(ctx, (CorpseEntity) entity, partialTick))
                .skipVanillaRender(entity -> skin((CorpseEntity) entity) != null)
                .apply();
        Coltan.LOGGER.info("Registered GemRender corpse visual (SEM unit corpses only)");
    }

    /** Skin to draw this corpse with, or null to leave it to CorpseMod. */
    @Nullable
    static ResourceLocation skin(CorpseEntity corpse) {
        if (corpse.isSkeleton()) {
            return null;
        }
        ResourceLocation skin = ColtanCorpseSkins.skinOf(corpse);
        if (skin == null) {
            return null;
        }
        NonNullList<ItemStack> equipment = corpse.getEquipment();
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = armorIn(equipment, slot);
            if (!stack.isEmpty() && !armorBridged(stack, slot)) {
                return null;
            }
        }
        return skin;
    }

    private static boolean armorBridged(ItemStack stack, EquipmentSlot slot) {
        if (!sbw) {
            return false;
        }
        ArmorBridgeCache.Piece piece = ArmorBridgeCache.piece(stack.getItem());
        return piece != null && piece.slot() == slot;
    }

    private static ItemStack armorIn(NonNullList<ItemStack> equipment, EquipmentSlot slot) {
        int i = slot.ordinal();
        return i < equipment.size() ? equipment.get(i) : ItemStack.EMPTY;
    }

    static void register(CorpseGemVisual visual) {
        LIVE.put(visual.entity().getId(), visual);
    }

    static void unregister(CorpseGemVisual visual) {
        LIVE.remove(visual.entity().getId(), visual);
    }

    /** Armor for bridged corpses, under the same matrix as their body instances. */
    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || LIVE.isEmpty() || !sbw) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        for (CorpseGemVisual visual : LIVE.values()) {
            CorpseEntity corpse = visual.entity();
            if (corpse.isRemoved() || !event.getFrustum().isVisible(corpse.getBoundingBoxForCulling())) {
                continue;
            }
            NonNullList<ItemStack> equipment = corpse.getEquipment();
            int light = mc.getEntityRenderDispatcher().getPackedLightCoords(corpse, partialTick);
            Vec3 pos = corpse.getPosition(partialTick);

            poseStack.pushPose();
            try {
                poseStack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
                poseStack.mulPoseMatrix(visual.localMatrix());
                for (EquipmentSlot slot : ARMOR) {
                    ItemStack stack = armorIn(equipment, slot);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    // GemRender submits to its own direct pass (flushed at the end of renderLevel);
                    // the vertex consumer argument is unused.
                    GemRenderArmorModel model = SbwArmorGemCompat.prepare(null, stack, slot);
                    CorpseGemVisual.restPose(model);
                    model.renderToBuffer(poseStack, null, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
                }
            } finally {
                poseStack.popPose();
            }
        }
    }
}
