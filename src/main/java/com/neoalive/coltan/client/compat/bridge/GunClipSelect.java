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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Picks SBW gun clip names for GemRender — mirrors {@code GunGeoItem.animationPredicate} for simple
 * guns and the dedicated AK/M4/HK idle/edit/reload tables.
 */
public final class GunClipSelect {
    /** Game tick when the current edit session started; -1 if not editing. */
    private static long editStartTick = -1L;

    private GunClipSelect() {
    }

    /** Called from {@code ClientEventHandler.onOpenEditScreen} — one play per H open. */
    public static void onEditOpened() {
        Minecraft mc = Minecraft.getInstance();
        editStartTick = mc.level != null ? mc.level.getGameTime() : 0L;
    }

    public static void onEditClosed() {
        editStartTick = -1L;
    }

    @Nullable
    public static String select(ItemStack stack, ItemDisplayContext context) {
        if (!(stack.getItem() instanceof GunItem)) {
            return null;
        }
        ResourceLocation itemId = GunBridgeCache.itemIdOf(stack.getItem());
        String path = itemId != null ? itemId.getPath() : "";

        GunBridgeCache.Piece piece = GunBridgeCache.piece(stack.getItem());
        if (piece != null) {
            GunBridgeProfile profile = piece.profile();
            if (profile.idle() != null) {
                return fromProfile(stack, context, profile);
            }
            String prefix = dedicatedPrefix(path, profile.animation());
            if (prefix != null) {
                return dedicatedRifle(stack, context, prefix);
            }
            return "animation." + path + ".idle";
        }

        DefaultGunResource resource = GunResource.from(stack).compute();
        if (resource != null && resource.animation != null && resource.animation.idle != null) {
            return fromResource(stack, context, resource.animation);
        }
        String prefix = dedicatedPrefix(path, null);
        if (prefix != null) {
            return dedicatedRifle(stack, context, prefix);
        }
        return "animation." + path + ".idle";
    }

    @Nullable
    private static String dedicatedPrefix(String path, @Nullable ResourceLocation animation) {
        String animPath = animation != null ? animation.getPath() : "";
        if ("ak_47".equals(path) || animPath.contains("ak_47")) {
            return "ak_47";
        }
        if ("m_4".equals(path) || "hk_416".equals(path)
                || animPath.contains("m_4") || animPath.contains("hk_416")) {
            // hk_416 shares M4 reload/idle tables
            return "m_4";
        }
        return null;
    }

    private static String fromProfile(ItemStack stack, ItemDisplayContext context,
            GunBridgeProfile profile) {
        if (!context.firstPerson() || context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            return profile.idle();
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return profile.idle();
        }
        GunData data = GunData.from(stack);

        if (profile.edit() != null && ClientEventHandler.isEditing) {
            return profile.edit();
        }
        if (profile.bolt() != null && data.bolt.actionTimer.get() > 0) {
            return profile.bolt();
        }
        if (data.reloading()) {
            if (profile.reload() != null) {
                return profile.reload();
            }
            if (profile.reloadNormal() != null && data.reload.normal()) {
                return profile.reloadNormal();
            }
            if (profile.reloadEmpty() != null && data.reload.empty()) {
                return profile.reloadEmpty();
            }
        }
        if (profile.melee() != null && ClientEventHandler.gunMelee > 0) {
            return profile.melee();
        }
        if (profile.fire() != null && ClientEventHandler.holdingFireKey && data.canShoot(player)) {
            return profile.fire();
        }
        if (profile.run() != null && player.isSprinting() && player.onGround()
                && ClientEventHandler.noSprintTicks == 0 && ClientEventHandler.drawTime < 0.01) {
            return profile.run();
        }
        return profile.idle();
    }

    private static String fromResource(ItemStack stack, ItemDisplayContext context,
            GunAnimation animation) {
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

    /** True for clips that should hold the last frame instead of looping (SBW {@code thenPlay}). */
    public static boolean oneShot(@Nullable String clipName) {
        if (clipName == null) {
            return false;
        }
        return clipName.contains(".edit")
                || clipName.contains("reload")
                || clipName.contains(".bolt")
                || clipName.contains(".melee");
    }

    /**
     * Clip-local seconds. Looping clips use the world clock; one-shots use reload/bolt countdown or
     * an edit-session clock (clamped by the caller via {@link #sampleTime}).
     */
    public static float seconds(ItemStack stack, @Nullable GltfAnimation clip, @Nullable String clipName,
            float partialTick) {
        if (clip == null || clip.duration() <= 0.0f) {
            return 0.0f;
        }
        GunData data = GunData.from(stack);
        if (data.reloading() && data.reload.time() > 0) {
            float remaining = (data.reload.time() - partialTick) / 20.0f;
            return Math.max(0.0f, clip.duration() - remaining);
        }
        if (data.bolt.actionTimer.get() > 0) {
            float remaining = (data.bolt.actionTimer.get() - partialTick) / 20.0f;
            return Math.max(0.0f, clip.duration() - remaining);
        }
        if (ClientEventHandler.isEditing && clipName != null && clipName.contains(".edit")) {
            Minecraft mc = Minecraft.getInstance();
            long tick = mc.level != null ? mc.level.getGameTime() : 0L;
            // Prefer mixin-stamped start; fall back if open was missed.
            if (editStartTick < 0L) {
                editStartTick = tick;
            }
            return (tick - editStartTick + partialTick) / 20.0f;
        }

        Minecraft mc = Minecraft.getInstance();
        long tick = mc.level != null ? mc.level.getGameTime() : 0L;
        return (tick + partialTick) / 20.0f;
    }

    /** Time fed to {@link GltfAnimation#apply}: loops idle/run, clamps one-shots to the last frame. */
    public static float sampleTime(GltfAnimation clip, @Nullable String clipName, float seconds) {
        if (clip == null) {
            return 0.0f;
        }
        if (oneShot(clipName)) {
            float end = Math.max(0.0f, clip.duration() - 1.0e-4f);
            return Math.min(Math.max(0.0f, seconds), end);
        }
        return clip.loop(seconds);
    }
}
