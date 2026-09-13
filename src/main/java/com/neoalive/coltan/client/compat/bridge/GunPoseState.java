package com.neoalive.coltan.client.compat.bridge;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.wf.gemrender.bedrock.BedrockChannel;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeHide;
import com.wf.gemrender.gltf.NodeRotation;
import com.wf.gemrender.gltf.NodeTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Builds a per-frame gun pose: motion clip + visibility + live ADS/recoil/bolt.
 *
 * <p>Uses {@code float[]} + {@code DirectRenderer.submit(state)} on FP so zoom/fire are not stuck in
 * the clip-time palette quantum (that was making procedural motion look frozen between buckets).
 */
public final class GunPoseState {
    private static final ThreadLocal<float[]> SCRATCH = new ThreadLocal<>();

    private GunPoseState() {
    }

    public static float[] evaluate(GemRenderGltfModel model, ItemStack stack, ItemDisplayContext context,
            @Nullable GltfAnimation motion, float seconds, boolean hideHands) {
        NodeTable table = model.layout().nodeTable();
        float[] state = scratch(table);
        table.resetToRest(state);

        if (motion != null) {
            String name = motion.name();
            motion.apply(GunClipSelect.sampleTime(motion, name, seconds), state);
        }

        GltfAnimation hides = hideHands
                ? GunVisibilityClip.clip(model, stack, context)
                : GunVisibilityClip.armPoseClip(model, stack, context, null);
        if (hides != null) {
            hides.apply(0.0f, state);
        }

        if (context.firstPerson()) {
            GunAdsProfile ads = GunAdsProfile.forItem(GunBridgeCache.itemIdOf(stack.getItem()));
            applyAds(table, state, stack, ads);
            applyZoomHides(table, state, stack, ads);
            applyRecoil(table, state, stack, ads);
            applyBolt(table, state, ads);
            applyFlareScale(table, state, stack);
        }
        return state;
    }

    private static void applyAds(NodeTable table, float[] state, ItemStack stack, GunAdsProfile ads) {
        int slot = table.slotOfName(ads.adsBone());
        if (slot < 0) {
            return;
        }
        float zp = (float) ClientEventHandler.zoomPos;
        float zpz = (float) ClientEventHandler.zoomPosZ;
        if (zp <= 0.0f && zpz <= 0.0f) {
            return;
        }

        GunData data = GunData.from(stack);
        int scope = data.attachment.get(AttachmentType.SCOPE);
        float posY = ads.posY();
        float posX = ads.posX();
        float posZ = ads.posZ();
        float scaleZ = ads.scaleZ();
        // AK scope-dependent tweaks (mirrors AK47ItemModel).
        if ("bone".equals(ads.adsBone()) && ads.recoilBone().startsWith("fireRoot")) {
            posY = switch (scope) {
                case 1 -> 0.261f;
                case 2 -> 0.162f + 0.45f;
                case 3 -> 0.099f + 0.5f;
                default -> 1.071f;
            };
            posX = scope == 2 ? 1.852f : 1.962f;
            posZ = switch (scope) {
                case 2 -> 4.74f;
                case 3 -> 4.5f;
                default -> 2.8f;
            };
            scaleZ = switch (scope) {
                case 1 -> 0.2f;
                case 2 -> 0.87f;
                case 3 -> 0.84f;
                default -> 0.55f;
            };
        }

        int base = slot * NodeTable.TRS_STRIDE + NodeTable.TRANSLATION;
        // BedrockChannel.Position: X flips, ÷16.
        state[base] += -posX * zp / BedrockChannel.UNITS_PER_BLOCK;
        state[base + 1] += (posY * zp - 0.2f * zpz) / BedrockChannel.UNITS_PER_BLOCK;
        state[base + 2] += (posZ * zp + 0.2f * zpz) / BedrockChannel.UNITS_PER_BLOCK;

        int scale = slot * NodeTable.TRS_STRIDE + NodeTable.SCALE;
        state[scale + 2] *= 1.0f - scaleZ * zp;
    }

    private static void applyZoomHides(NodeTable table, float[] state, ItemStack stack, GunAdsProfile ads) {
        if (!ClientEventHandler.zoom || ClientEventHandler.zoomPos <= 0.7) {
            return;
        }
        GunData data = GunData.from(stack);
        int scope = data.attachment.get(AttachmentType.SCOPE);
        var bones = ads.scopeZoomHide().get(scope);
        if (bones == null) {
            return;
        }
        for (String name : bones) {
            int slot = table.slotOfName(name);
            if (slot >= 0) {
                NodeHide.of(table, slot).apply(0.0f, state);
            }
        }
    }

    private static void applyRecoil(NodeTable table, float[] state, ItemStack stack, GunAdsProfile ads) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(stack.getItem() instanceof GunItem)) {
            return;
        }
        if (ClientEventHandler.firePosTimer <= 0.0 && ClientEventHandler.fireRotTimer <= 0.0) {
            return;
        }

        String boneName = ads.recoilBone();
        if (ClientEventHandler.zoomTime >= 0.5 && boneName.startsWith("fireRoot")) {
            int scope = GunData.from(stack).attachment.get(AttachmentType.SCOPE);
            boneName = "fireRoot" + scope;
            if (table.slotOfName(boneName) < 0) {
                boneName = ads.recoilBone();
            }
        }
        int slot = table.slotOfName(boneName);
        if (slot < 0) {
            return;
        }

        GunData data = GunData.from(stack);
        int barrel = data.attachment.get(AttachmentType.BARREL);
        int grip = data.attachment.get(AttachmentType.GRIP);
        int scope = data.attachment.get(AttachmentType.SCOPE);
        float recoil = barrel == 1 ? 0.75f : barrel == 2 ? 0.95f : 1f;
        float gripRecoilX = grip == 1 ? 0.85f : grip == 2 ? 0.95f : 1f;
        float gripRecoilY = grip == 1 ? 0.95f : grip == 2 ? 0.85f : 1f;
        float zoomRecoil = switch (scope) {
            case 2 -> 1.25f - (float) (ClientEventHandler.zoomTime * 0.8);
            case 3 -> 1.25f - (float) ClientEventHandler.zoomTime;
            default -> 1.25f;
        };
        float pose = player.isShiftKeyDown() ? 0.85f : 1f;
        float zoomMul = Mth.clamp(ads.recoilZoomMul(), 0f, 1f);
        float zoom = (float) ((1 - zoomMul * ClientEventHandler.zoomTime) * pose);

        float x = ads.recoilX();
        float y = ads.recoilY();
        float z = ads.recoilZ();
        float firePosZ = (float) ClientEventHandler.firePosZ;
        float firePosTimer = (float) ClientEventHandler.firePosTimer;
        float fireRotTimer = (float) ClientEventHandler.fireRotTimer;
        float horizon = (float) ClientEventHandler.recoilHorizon;

        float posX = zoom * x * (horizon * (0.5f * firePosZ));
        float posY = zoom * y * (ClientEventHandler.getBoneMoveY(firePosTimer) * 0.25f
                * (float) (1 - 0.25 * ClientEventHandler.zoomTime));
        float posZ = zoom * z * (ClientEventHandler.getBoneMoveZ(firePosTimer) * 0.05f + 1.1f * firePosZ)
                * (float) (1 - 0.5 * ClientEventHandler.zoomTime);
        float rotX = zoom * ads.recoilRotX()
                * (-ClientEventHandler.getBoneRotX(fireRotTimer) * Mth.DEG_TO_RAD * 0.5f + 0.01f * firePosZ)
                * gripRecoilX * recoil * (float) (1 - 0.85 * ClientEventHandler.zoomTime) * zoomRecoil;
        float rotY = 3 * zoom * ads.recoilRotY() * ClientEventHandler.getBoneRotY(fireRotTimer) * Mth.DEG_TO_RAD
                * horizon * gripRecoilY * recoil * (float) (1 - 0.3 * ClientEventHandler.zoomTime) * zoomRecoil;
        float rotZ = 2 * zoom * ads.recoilRotZ() * ClientEventHandler.getBoneRotZ(fireRotTimer) * Mth.DEG_TO_RAD
                * horizon * gripRecoilY * recoil * (float) (1 - 0.5 * ClientEventHandler.zoomTime) * zoomRecoil;

        int base = slot * NodeTable.TRS_STRIDE + NodeTable.TRANSLATION;
        state[base] += -posX / BedrockChannel.UNITS_PER_BLOCK;
        state[base + 1] += posY / BedrockChannel.UNITS_PER_BLOCK;
        state[base + 2] += posZ / BedrockChannel.UNITS_PER_BLOCK;
        NodeRotation.compose(state, NodeRotation.offsetOf(table, slot), 1, 0, 0, rotX);
        NodeRotation.compose(state, NodeRotation.offsetOf(table, slot), 0, 1, 0, rotY);
        NodeRotation.compose(state, NodeRotation.offsetOf(table, slot), 0, 0, 1, rotZ);
    }

    private static void applyBolt(NodeTable table, float[] state, GunAdsProfile ads) {
        if (ads.boltBone() == null || ClientEventHandler.boltMove <= 0) {
            return;
        }
        int slot = table.slotOfName(ads.boltBone());
        if (slot < 0) {
            return;
        }
        int base = slot * NodeTable.TRS_STRIDE + NodeTable.TRANSLATION;
        state[base + 2] += 3f * (float) ClientEventHandler.boltMove / BedrockChannel.UNITS_PER_BLOCK;
    }

    private static void applyFlareScale(NodeTable table, float[] state, ItemStack stack) {
        int slot = table.slotOfName("flare");
        if (slot < 0) {
            return;
        }
        GunData data = GunData.from(stack);
        boolean show = ClientEventHandler.fireRotTimer > 0 && ClientEventHandler.fireRotTimer < 0.3
                && data.attachment.get(AttachmentType.BARREL) != 2;
        if (!show) {
            NodeHide.of(table, slot).apply(0.0f, state);
            return;
        }
        var resource = com.atsuishio.superbwarfare.resource.gun.GunResource.from(stack).compute();
        float size = resource != null ? (float) resource.flareSize : 0.3f;
        float s = (float) (size + 0.8 * size * (Math.random() - 0.5));
        int scale = slot * NodeTable.TRS_STRIDE + NodeTable.SCALE;
        state[scale] = s;
        state[scale + 1] = s;
        state[scale + 2] = s;
    }

    private static float[] scratch(NodeTable table) {
        float[] cached = SCRATCH.get();
        if (cached == null || cached.length < table.scratchFloats()) {
            cached = table.newScratch();
            SCRATCH.set(cached);
        }
        return cached;
    }
}
