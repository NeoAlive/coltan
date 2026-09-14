package com.neoalive.coltan.client.compat.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeHide;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * TACZ Bedrock guns pack every magazine / FP-hand / locator variant into one geo. Stock TACZ
 * toggles {@code visible}; GemRender needs {@link NodeHide}. SEM units almost always have no
 * attachments — default keeps {@code mag_standard} only.
 */
public final class TaczVisibilityClip {
    private static final String[] ALWAYS_HIDE = {
            "additional_magazine",
            "lefthand",
            "righthand",
            "lefthand_pos",
            "righthand_pos",
            "handguard_tactical",
            "mount",
            "sight_folded",
    };

    private static final String[] MAG_BY_LEVEL = {
            "mag_standard",
            "mag_extended_1",
            "mag_extended_2",
            "mag_extended_3",
    };

    private static final Map<Long, GltfAnimation> CACHE = new ConcurrentHashMap<>();

    private TaczVisibilityClip() {
    }

    public static void clear() {
        CACHE.clear();
    }

    public static GltfAnimation clip(GemRenderGltfModel model, ItemStack stack) {
        NodeTable table = model.layout().nodeTable();
        int level = extendMagLevel(stack);
        boolean scoped = hasAttachment(stack, AttachmentType.SCOPE);
        long key = (((long) System.identityHashCode(table)) << 32)
                | ((level & 0xf) << 4)
                | (scoped ? 1L : 0L);
        return CACHE.computeIfAbsent(key, ignored -> build(table, level, scoped));
    }

    private static GltfAnimation build(NodeTable table, int level, boolean scoped) {
        List<PoseDriver> drivers = new ArrayList<>();
        for (String name : ALWAYS_HIDE) {
            hide(table, drivers, name);
        }
        if (scoped) {
            hide(table, drivers, "sight");
            hide(table, drivers, "carry");
        } else {
            // mount / sight_folded already always-hidden for unscoped
        }
        for (int i = 0; i < MAG_BY_LEVEL.length; i++) {
            if (i != level) {
                hide(table, drivers, MAG_BY_LEVEL[i]);
            }
        }
        if (drivers.isEmpty()) {
            return GltfAnimation.procedural("tacz_visibility");
        }
        return GltfAnimation.procedural("tacz_visibility", drivers.toArray(PoseDriver[]::new));
    }

    private static int extendMagLevel(ItemStack stack) {
        if (!(stack.getItem() instanceof IGun gun)) {
            return 0;
        }
        ItemStack mag = gun.getAttachment(stack, AttachmentType.EXTENDED_MAG);
        if (mag.isEmpty()) {
            mag = gun.getBuiltinAttachment(stack, AttachmentType.EXTENDED_MAG);
        }
        if (mag.isEmpty()) {
            return 0;
        }
        ResourceLocation id = gun.getAttachmentId(stack, AttachmentType.EXTENDED_MAG);
        if (id == null) {
            return 0;
        }
        return TimelessAPI.getClientAttachmentIndex(id)
                .map(index -> Math.max(0, Math.min(3, index.getData().getExtendedMagLevel())))
                .orElse(0);
    }

    private static boolean hasAttachment(ItemStack stack, AttachmentType type) {
        if (!(stack.getItem() instanceof IGun gun)) {
            return false;
        }
        ItemStack att = gun.getAttachment(stack, type);
        if (att.isEmpty()) {
            att = gun.getBuiltinAttachment(stack, type);
        }
        return !att.isEmpty();
    }

    private static void hide(NodeTable table, List<PoseDriver> drivers, String bone) {
        int slot = table.slotOfName(bone);
        if (slot >= 0) {
            drivers.add(NodeHide.of(table, slot));
        }
    }
}
