package com.neoalive.coltan.client.compat.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.subdata.Attachment;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeHide;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Gun visibility via {@link NodeHide}: FP hand stubs (gun atlas) + SBW attachment bone rules.
 * Composed onto a motion clip with {@link GltfAnimation#with}.
 */
public final class GunVisibilityClip {
    private static final String[] HAND_BONES = {"Lefthand", "Righthand"};
    private static final String[] ATTACHMENT_PREFIXES = {
            "Scope", "Magazine", "Barrel", "Stock", "Grip", "AmmoType"
    };

    private static final Map<Long, GltfAnimation> HIDE_ONLY = new ConcurrentHashMap<>();
    private static final Map<String, GltfAnimation> COMPOSED = new ConcurrentHashMap<>();

    private GunVisibilityClip() {
    }

    public static void clear() {
        HIDE_ONLY.clear();
        COMPOSED.clear();
    }

    /** Hide-only clip (rest pose + visibility). */
    public static GltfAnimation clip(GemRenderGltfModel model, ItemStack stack,
            ItemDisplayContext context) {
        return hideOnly(model, stack, context, true);
    }

    /**
     * Motion clip with attachment + hand hides overlaid. Cache key includes motion name.
     */
    public static GltfAnimation compose(GemRenderGltfModel model, ItemStack stack,
            ItemDisplayContext context, @Nullable GltfAnimation motion) {
        GltfAnimation hides = hideOnly(model, stack, context, true);
        if (motion == null) {
            return hides;
        }
        int packed = packSelection(stack, context);
        String key = System.identityHashCode(model.layout().nodeTable()) + "|" + packed + "|"
                + motion.name();
        return COMPOSED.computeIfAbsent(key, ignored -> motion.with(hides.drivers().toArray(PoseDriver[]::new)));
    }

    /**
     * Pose for FP arms: motion + attachment hides, but <em>not</em> hand NodeHides (those zero the
     * bone matrices arms need).
     */
    public static GltfAnimation armPoseClip(GemRenderGltfModel model, ItemStack stack,
            ItemDisplayContext context, @Nullable GltfAnimation motion) {
        GltfAnimation hides = hideOnly(model, stack, context, false);
        if (motion == null) {
            return hides;
        }
        int packed = packSelection(stack, context);
        String key = "arm|" + System.identityHashCode(model.layout().nodeTable()) + "|" + packed + "|"
                + motion.name();
        return COMPOSED.computeIfAbsent(key, ignored -> motion.with(hides.drivers().toArray(PoseDriver[]::new)));
    }

    private static GltfAnimation hideOnly(GemRenderGltfModel model, ItemStack stack,
            ItemDisplayContext context, boolean hideHands) {
        NodeTable table = model.layout().nodeTable();
        int packed = packSelection(stack, context);
        long key = (((long) System.identityHashCode(table)) << 32) | (packed & 0xffffffffL);
        if (hideHands) {
            key ^= 0x4c4e4448L; // "HNDL"
        }
        return HIDE_ONLY.computeIfAbsent(key, ignored -> build(table, packed, hideHands));
    }

    private static GltfAnimation build(NodeTable table, int packed, boolean hideHands) {
        int[] selected = unpack(packed);
        List<PoseDriver> drivers = new ArrayList<>();

        if (hideHands) {
            for (String hand : HAND_BONES) {
                hide(table, drivers, hand);
            }
        }

        int grip = selected[4];
        if (grip != 0) {
            hide(table, drivers, "humu1");
        } else {
            hide(table, drivers, "humu2");
        }

        for (int slot = 0; slot < table.nodeCount(); slot++) {
            String name = table.nodeName(slot);
            if (name == null || shouldKeep(name, selected)) {
                continue;
            }
            drivers.add(NodeHide.of(table, slot));
        }

        if (drivers.isEmpty()) {
            return GltfAnimation.procedural("gun_visibility");
        }
        return GltfAnimation.procedural("gun_visibility", drivers.toArray(PoseDriver[]::new));
    }

    private static boolean shouldKeep(String boneName, int[] selected) {
        for (int i = 0; i < ATTACHMENT_PREFIXES.length; i++) {
            String prefix = ATTACHMENT_PREFIXES[i];
            if (!boneName.startsWith(prefix)) {
                continue;
            }
            String[] parts = boneName.split("(?<=\\D)(?=\\d)");
            if (parts.length != 2) {
                return true;
            }
            try {
                int index = Integer.parseInt(parts[1]);
                return selected[i] == index;
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        return true;
    }

    private static int packSelection(ItemStack stack, ItemDisplayContext context) {
        boolean live = context.firstPerson()
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        int packed = live ? (1 << 28) : 0;
        if (!live) {
            return packed;
        }
        GunData data = GunData.from(stack);
        Attachment attachment = data.attachment;
        packed |= (attachment.get(AttachmentType.SCOPE) & 0xf);
        packed |= (attachment.get(AttachmentType.MAGAZINE) & 0xf) << 4;
        packed |= (attachment.get(AttachmentType.BARREL) & 0xf) << 8;
        packed |= (attachment.get(AttachmentType.STOCK) & 0xf) << 12;
        packed |= (attachment.get(AttachmentType.GRIP) & 0xf) << 16;
        packed |= (data.selectedAmmoType.get() & 0xf) << 20;
        return packed;
    }

    private static int[] unpack(int packed) {
        return new int[] {
                packed & 0xf,
                (packed >> 4) & 0xf,
                (packed >> 8) & 0xf,
                (packed >> 12) & 0xf,
                (packed >> 16) & 0xf,
                (packed >> 20) & 0xf
        };
    }

    private static void hide(NodeTable table, List<PoseDriver> drivers, String bone) {
        int slot = table.slotOfName(bone);
        if (slot >= 0) {
            drivers.add(NodeHide.of(table, slot));
        }
    }
}
