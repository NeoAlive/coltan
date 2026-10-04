package com.neoalive.coltan.client.compat.bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.client.animation.AnimationCurves;
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
    /** Separate buffer for {@link #withHandsHidden}, so the arm pose it copies stays intact. */
    private static final ThreadLocal<float[]> HANDS_HIDDEN_SCRATCH = new ThreadLocal<>();
    private static final Map<NodeTable, HandRig> HAND_RIGS = Collections.synchronizedMap(new WeakHashMap<>());

    private GunPoseState() {
    }

    /**
     * FP pose with hands visible (what the arms read). Pair with {@link #withHandsHidden} for the gun
     * mesh instead of evaluating twice: the two poses differ only by the hand NodeHides.
     */
    public static float[] evaluateFp(GemRenderGltfModel model, ItemStack stack, ItemDisplayContext context,
            @Nullable GltfAnimation motion, float seconds) {
        return evaluate(model, stack, context, motion, seconds, false);
    }

    /**
     * Copy of {@code armState} with {@code Lefthand}/{@code Righthand} hidden, equal to
     * {@code evaluate(..., hideHands = true)}: NodeHide only zeroes scale, so applying it after the
     * procedural steps instead of before lands on the same floats.
     */
    public static float[] withHandsHidden(GemRenderGltfModel model, float[] armState) {
        NodeTable table = model.layout().nodeTable();
        int floats = table.scratchFloats();
        float[] out = HANDS_HIDDEN_SCRATCH.get();
        if (out == null || out.length < floats) {
            out = table.newScratch();
            HANDS_HIDDEN_SCRATCH.set(out);
        }
        System.arraycopy(armState, 0, out, 0, floats);
        for (NodeHide hide : rig(table).hides()) {
            hide.apply(0.0f, out);
        }
        return out;
    }

    /**
     * True when {@code flare} or {@code cross} hangs under a hand bone, so overlays need the
     * hands-hidden palette rather than the arm palette.
     */
    public static boolean overlaysUnderHands(GemRenderGltfModel model) {
        return rig(model.layout().nodeTable()).overlaysUnderHands();
    }

    private static HandRig rig(NodeTable table) {
        HandRig rig = HAND_RIGS.get(table);
        if (rig == null) {
            rig = HandRig.of(table);
            HAND_RIGS.put(table, rig);
        }
        return rig;
    }

    private record HandRig(NodeHide[] hides, boolean overlaysUnderHands) {
        static HandRig of(NodeTable table) {
            List<NodeHide> hides = new ArrayList<>();
            List<Integer> handSlots = new ArrayList<>();
            for (String hand : GunVisibilityClip.HAND_BONES) {
                int slot = table.slotOfName(hand);
                if (slot >= 0) {
                    hides.add(NodeHide.of(table, slot));
                    handSlots.add(slot);
                }
            }
            int[] parents = table.parentSlots();
            boolean under = false;
            for (String overlay : new String[] {"flare", "cross"}) {
                // Depth-capped: a malformed parent table must not hang the render thread.
                int p = parentOf(parents, table.slotOfName(overlay));
                for (int depth = 0; p >= 0 && depth < parents.length; depth++, p = parentOf(parents, p)) {
                    if (handSlots.contains(p)) {
                        under = true;
                    }
                }
            }
            return new HandRig(hides.toArray(NodeHide[]::new), under);
        }

        private static int parentOf(int[] parents, int slot) {
            return slot >= 0 && slot < parents.length ? parents[slot] : -1;
        }
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
            GunBridgeCache.Piece piece = GunBridgeCache.piece(stack.getItem());
            if (piece != null) {
                GunBridgeProfile profile = piece.profile();
                applyRootMove(table, state, profile);
                applyAds(table, state, stack, profile);
                applyZoomHides(table, state, stack, profile);
                applyRecoil(table, state, stack, profile);
                applyBolt(table, state, profile);
                if (profile.hasFlare()) {
                    applyFlareScale(table, state, stack);
                }
            }
        }
        return state;
    }

    /** Walk / sprint / draw sway on {@code root} — mirrors {@code ClientEventHandler.gunRootMove}. */
    private static void applyRootMove(NodeTable table, float[] state, GunBridgeProfile profile) {
        if (!profile.hasRoot()) {
            return;
        }
        int slot = table.slotOfName("root");
        if (slot < 0) {
            return;
        }
        float customX = profile.rootCustomX();
        float customY = profile.rootCustomY();
        float customZ = profile.rootCustomZ();
        float i = 1f;

        float walkPosX = (float) ClientEventHandler.movePosX;
        float walkPosY = (float) (ClientEventHandler.swayY + ClientEventHandler.movePosY);
        float walkRotX = (float) ClientEventHandler.swayX;
        float walkRotY = (float) (0.2f * ClientEventHandler.movePosX);
        float walkRotZ = (float) (0.2f * ClientEventHandler.movePosX);

        double pb = AnimationCurves.PARABOLA.apply(ClientEventHandler.sprintBasicPosY);
        float basicSprintPosX = (float) (ClientEventHandler.sprintBasicPosX * (1.5 + customX)) * i;
        float basicSprintPosY =
                (float) (ClientEventHandler.sprintBasicPosY * (-2.35 + customY - 8 * pb)) * i;
        float basicSprintPosZ = (float) (ClientEventHandler.sprintBasicPosZ * (-0.55 + customZ)) * i;
        float basicSprintRotX = (float) (ClientEventHandler.sprintBasicRotX * 39 * Mth.DEG_TO_RAD) * i;
        float basicSprintRotY = (float) (ClientEventHandler.sprintBasicRotY * 35.6 * Mth.DEG_TO_RAD) * i;
        float basicSprintRotZ = (float) (ClientEventHandler.sprintBasicRotZ * 34.7 * Mth.DEG_TO_RAD) * i;

        float zt = (float) ClientEventHandler.zoomTime;
        float gunPosX = (float) ((walkPosX + basicSprintPosX + ClientEventHandler.sprintPosX * i
                + 20 * ClientEventHandler.drawTime + 9.3f * ClientEventHandler.movePosHorizon)
                * (1 - 0.5 * zt));
        float gunPosY = (float) ((walkPosY + basicSprintPosY + ClientEventHandler.sprintPosY * i
                - 40 * ClientEventHandler.drawTime - 2f * ClientEventHandler.velocityY) * (1 - 0.5 * zt));
        float gunPosZ = (float) ((basicSprintPosZ) * (1 - zt));
        float gunRotX = (float) ((walkRotX + basicSprintRotX - Mth.DEG_TO_RAD * 60 * ClientEventHandler.drawTime
                - 0.15f * ClientEventHandler.velocityY) * (1 - 0.5 * zt)
                + Mth.DEG_TO_RAD * ClientEventHandler.turnRot[0]);
        float gunRotY = (float) ((walkRotY + basicSprintRotY
                + (0.2f * ClientEventHandler.sprintBasicPosX * i)
                + Mth.DEG_TO_RAD * 300 * ClientEventHandler.drawTime) * (1 - 0.75 * zt)
                + Mth.DEG_TO_RAD * ClientEventHandler.turnRot[1]);
        float gunRotZ = (float) ((walkRotZ + basicSprintRotZ + ClientEventHandler.moveRotZ
                + Mth.DEG_TO_RAD * 90 * ClientEventHandler.drawTime
                + 2.7f * ClientEventHandler.movePosHorizon) * (1 - 0.5 * zt)
                + Mth.DEG_TO_RAD * ClientEventHandler.turnRot[2]);

        int base = slot * NodeTable.TRS_STRIDE + NodeTable.TRANSLATION;
        state[base] += -gunPosX / BedrockChannel.UNITS_PER_BLOCK;
        state[base + 1] += gunPosY / BedrockChannel.UNITS_PER_BLOCK;
        state[base + 2] += gunPosZ / BedrockChannel.UNITS_PER_BLOCK;
        int rot = NodeRotation.offsetOf(table, slot);
        NodeRotation.compose(state, rot, 1, 0, 0, gunRotX);
        NodeRotation.compose(state, rot, 0, 1, 0, gunRotY);
        NodeRotation.compose(state, rot, 0, 0, 1, gunRotZ);
    }

    private static void applyAds(NodeTable table, float[] state, ItemStack stack, GunBridgeProfile profile) {
        String adsBone = profile.adsBone();
        if (adsBone == null) {
            return;
        }
        int slot = table.slotOfName(adsBone);
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
        float posY = profile.posY();
        float posX = profile.posX();
        float posZ = profile.posZ();
        float scaleZ = profile.scaleZ();
        GunBridgeProfile.AdsPose scoped = profile.scopeAds().get(scope);
        if (scoped != null) {
            posX = scoped.posX();
            posY = scoped.posY();
            posZ = scoped.posZ();
            scaleZ = scoped.scaleZ();
        }

        int base = slot * NodeTable.TRS_STRIDE + NodeTable.TRANSLATION;
        // BedrockChannel.Position: X flips, ÷16.
        state[base] += -posX * zp / BedrockChannel.UNITS_PER_BLOCK;
        state[base + 1] += (posY * zp - 0.2f * zpz) / BedrockChannel.UNITS_PER_BLOCK;
        state[base + 2] += (posZ * zp + 0.2f * zpz) / BedrockChannel.UNITS_PER_BLOCK;

        int scale = slot * NodeTable.TRS_STRIDE + NodeTable.SCALE;
        // Any scaleZ >= 1/zp drives this to zero or negative, flattening the gun into a plane and
        // then mirroring it through — clamp so a bad scrape/override/disk-cache value can't do that.
        state[scale + 2] *= Mth.clamp(1.0f - scaleZ * zp, 0.05f, 1.0f);
    }

    private static void applyZoomHides(NodeTable table, float[] state, ItemStack stack,
            GunBridgeProfile profile) {
        if (!ClientEventHandler.zoom || ClientEventHandler.zoomPos <= 0.7) {
            return;
        }
        GunData data = GunData.from(stack);
        int scope = data.attachment.get(AttachmentType.SCOPE);
        var bones = profile.scopeZoomHide().get(scope);
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

    private static void applyRecoil(NodeTable table, float[] state, ItemStack stack,
            GunBridgeProfile profile) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(stack.getItem() instanceof GunItem)) {
            return;
        }
        if (ClientEventHandler.firePosTimer <= 0.0 && ClientEventHandler.fireRotTimer <= 0.0) {
            return;
        }

        String boneName = profile.recoilBone();
        if (boneName == null) {
            return;
        }
        if (ClientEventHandler.zoomTime >= 0.5 && boneName.startsWith("fireRoot")) {
            int scope = GunData.from(stack).attachment.get(AttachmentType.SCOPE);
            boneName = "fireRoot" + scope;
            if (table.slotOfName(boneName) < 0) {
                boneName = profile.recoilBone();
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
        float zoomMul = Mth.clamp(profile.recoilZoomMul(), 0f, 1f);
        float zoom = (float) ((1 - zoomMul * ClientEventHandler.zoomTime) * pose);

        float x = profile.recoilX();
        float y = profile.recoilY();
        float z = profile.recoilZ();
        float firePosZ = (float) ClientEventHandler.firePosZ;
        float firePosTimer = (float) ClientEventHandler.firePosTimer;
        float fireRotTimer = (float) ClientEventHandler.fireRotTimer;
        float horizon = (float) ClientEventHandler.recoilHorizon;

        float posX = zoom * x * (horizon * (0.5f * firePosZ));
        float posY = zoom * y * (ClientEventHandler.getBoneMoveY(firePosTimer) * 0.25f
                * (float) (1 - 0.25 * ClientEventHandler.zoomTime));
        float posZ = zoom * z * (ClientEventHandler.getBoneMoveZ(firePosTimer) * 0.05f + 1.1f * firePosZ)
                * (float) (1 - 0.5 * ClientEventHandler.zoomTime);
        float rotX = zoom * profile.recoilRotX()
                * (-ClientEventHandler.getBoneRotX(fireRotTimer) * Mth.DEG_TO_RAD * 0.5f + 0.01f * firePosZ)
                * gripRecoilX * recoil * (float) (1 - 0.85 * ClientEventHandler.zoomTime) * zoomRecoil;
        float rotY = 3 * zoom * profile.recoilRotY() * ClientEventHandler.getBoneRotY(fireRotTimer)
                * Mth.DEG_TO_RAD * horizon * gripRecoilY * recoil
                * (float) (1 - 0.3 * ClientEventHandler.zoomTime) * zoomRecoil;
        float rotZ = 2 * zoom * profile.recoilRotZ() * ClientEventHandler.getBoneRotZ(fireRotTimer)
                * Mth.DEG_TO_RAD * horizon * gripRecoilY * recoil
                * (float) (1 - 0.5 * ClientEventHandler.zoomTime) * zoomRecoil;

        int base = slot * NodeTable.TRS_STRIDE + NodeTable.TRANSLATION;
        state[base] += -posX / BedrockChannel.UNITS_PER_BLOCK;
        state[base + 1] += posY / BedrockChannel.UNITS_PER_BLOCK;
        state[base + 2] += posZ / BedrockChannel.UNITS_PER_BLOCK;
        NodeRotation.compose(state, NodeRotation.offsetOf(table, slot), 1, 0, 0, rotX);
        NodeRotation.compose(state, NodeRotation.offsetOf(table, slot), 0, 1, 0, rotY);
        NodeRotation.compose(state, NodeRotation.offsetOf(table, slot), 0, 0, 1, rotZ);
    }

    private static void applyBolt(NodeTable table, float[] state, GunBridgeProfile profile) {
        if (profile.boltBone() == null || ClientEventHandler.boltMove <= 0) {
            return;
        }
        int slot = table.slotOfName(profile.boltBone());
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
