package com.neoalive.coltan.client.compat.bridge;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.atsuishio.superbwarfare.resource.gun.DefaultGunResource;
import com.atsuishio.superbwarfare.resource.gun.GunAnimation;
import com.atsuishio.superbwarfare.resource.gun.GunResource;
import com.wf.gemrender.gltf.GltfAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Picks SBW gun clip names for GemRender — mirrors {@code GunGeoItem.animationPredicate} for simple
 * guns and the dedicated AK/M4/HK idle/edit/reload tables.
 */
public final class GunClipSelect {
    private GunClipSelect() {
    }

    @Nullable
    public static String select(ItemStack stack, ItemDisplayContext context) {
        if (!(stack.getItem() instanceof GunItem)) {
            return null;
        }
        String path = GunBridgeCache.itemIdOf(stack.getItem()) != null
                ? GunBridgeCache.itemIdOf(stack.getItem()).getPath()
                : "";

        DefaultGunResource resource = GunResource.from(stack).compute();
        if (resource != null && resource.animation != null && resource.animation.idle != null) {
            return fromResource(stack, context, resource.animation);
        }
        if ("ak_47".equals(path)) {
            return dedicatedRifle(stack, context, "ak_47");
        }
        if ("m_4".equals(path) || "hk_416".equals(path)) {
            // HK reuses m_4 clip names / animation file in SBW.
            return dedicatedRifle(stack, context, "m_4");
        }
        // Fallback: idle if the model loaded a conventional name.
        return "animation." + path + ".idle";
    }

    private static String fromResource(ItemStack stack, ItemDisplayContext context, GunAnimation animation) {
        if (!context.firstPerson() || context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            return animation.idle;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return animation.idle;
        }
        GunData data = GunData.from(stack);

        if (animation.edit != null && ClientEventHandler.isEditing) {
            return animation.edit;
        }
        if (animation.bolt != null && data.bolt.actionTimer.get() > 0) {
            return animation.bolt;
        }
        if (data.reloading()) {
            if (animation.reload != null) {
                return animation.reload;
            }
            if (animation.reloadNormal != null && data.reload.normal()) {
                return animation.reloadNormal;
            }
            if (animation.reloadEmpty != null && data.reload.empty()) {
                return animation.reloadEmpty;
            }
        }
        if (animation.melee != null && ClientEventHandler.gunMelee > 0) {
            return animation.melee;
        }
        if (animation.fire != null && ClientEventHandler.holdingFireKey && data.canShoot(player)) {
            return animation.fire;
        }
        if (animation.run != null && player.isSprinting() && player.onGround()
                && ClientEventHandler.noSprintTicks == 0 && ClientEventHandler.drawTime < 0.01) {
            return animation.run;
        }
        return animation.idle;
    }

    private static String dedicatedRifle(ItemStack stack, ItemDisplayContext context, String prefix) {
        String idle = "animation." + prefix + ".idle";
        if (!context.firstPerson() || context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            return idle;
        }
        GunData data = GunData.from(stack);
        boolean drum = data.attachment.get(AttachmentType.MAGAZINE) == 2;
        boolean grip = data.attachment.get(AttachmentType.GRIP) == 1
                || data.attachment.get(AttachmentType.GRIP) == 2;

        if (ClientEventHandler.isEditing) {
            return "animation." + prefix + ".edit";
        }
        if (data.reload.empty()) {
            return reloadName(prefix, true, drum, grip);
        }
        if (data.reload.normal()) {
            return reloadName(prefix, false, drum, grip);
        }
        return grip ? "animation." + prefix + ".idle_grip" : idle;
    }

    private static String reloadName(String prefix, boolean empty, boolean drum, boolean grip) {
        StringBuilder name = new StringBuilder("animation.").append(prefix)
                .append(empty ? ".reload_empty" : ".reload_normal");
        if (drum) {
            name.append("_drum");
        }
        if (grip) {
            name.append("_grip");
        }
        return name.toString();
    }

    /**
     * Clip-local seconds. Looping clips use the world clock; one-shots use the reload countdown.
     */
    public static float seconds(ItemStack stack, GltfAnimation clip, float partialTick) {
        if (clip == null || clip.duration() <= 0.0f) {
            return 0.0f;
        }
        GunData data = GunData.from(stack);
        if (data.reloading() && data.reload.time() > 0) {
            float remaining = data.reload.time() / 20.0f;
            return Math.max(0.0f, clip.duration() - remaining);
        }
        if (data.bolt.actionTimer.get() > 0) {
            float remaining = data.bolt.actionTimer.get() / 20.0f;
            return Math.max(0.0f, clip.duration() - remaining);
        }
        Minecraft mc = Minecraft.getInstance();
        long tick = mc.level != null ? mc.level.getGameTime() : 0L;
        return (tick + partialTick) / 20.0f;
    }
}
