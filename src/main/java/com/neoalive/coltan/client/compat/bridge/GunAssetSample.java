package com.neoalive.coltan.client.compat.bridge;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.item.CustomRendererItem;
import com.atsuishio.superbwarfare.resource.ModelResource;
import com.atsuishio.superbwarfare.resource.gun.DefaultGunResource;
import com.atsuishio.superbwarfare.resource.gun.GunAnimation;
import com.atsuishio.superbwarfare.resource.gun.GunResource;
import com.neoalive.coltan.Coltan;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Tier A gun sampler: GunResource JSON + geo bone names → {@link GunBridgeProfile}, with disk cache.
 *
 * <p>On cache hit (matching hash) returns the stored profile as-is (already Tier-B enriched). On miss
 * builds Tier A, runs {@link GunFpProbe#enrich}, then saves.
 */
public final class GunAssetSample {
    private static final Pattern ATTACHMENT_BONE = Pattern.compile(
            "^(Scope|Magazine|Barrel|Stock|Grip)\\d+$", Pattern.CASE_INSENSITIVE);

    private GunAssetSample() {
    }

    public static GunBridgeProfile sample(Item item, ResourceLocation itemId) {
        DefaultGunResource resource = GunResource.getDefault(item);
        ModelResource model = resource.getModel();

        ResourceLocation geo = model.model;
        ResourceLocation texture = model.texture;
        ResourceLocation animationRl = model.animation;
        ResourceLocation lodGeo = model.hasLOD() ? model.getLODModel(1) : null;
        ResourceLocation lodTexture = model.hasLOD() ? model.getLODTexture(1) : null;
        // LOD getters fall back to the full asset when level is out of range — treat same-as-full as none.
        if (lodGeo != null && lodGeo.equals(geo)) {
            lodGeo = null;
        }
        if (lodTexture != null && lodTexture.equals(texture)) {
            lodTexture = null;
        }

        String modelClass = item.getClass().getName();
        String rendererClass = rendererClassOf(item);
        String hash = GunProfileDiskCache.contentHash(geo, texture, animationRl, modelClass, rendererClass);

        GunBridgeProfile cached = GunProfileDiskCache.load(itemId, hash);
        if (cached != null) {
            return cached;
        }

        List<String> bones = geo != null ? BoneInference.boneNames(geo) : List.of();
        boolean simple = !hasAttachmentBones(bones);

        GunAnimation clips = resource.animation;
        String idle = clips != null ? clips.idle : null;
        String edit = clips != null ? clips.edit : null;
        String bolt = clips != null ? clips.bolt : null;
        String reload = clips != null ? clips.reload : null;
        String reloadNormal = clips != null ? clips.reloadNormal : null;
        String reloadEmpty = clips != null ? clips.reloadEmpty : null;
        String fire = clips != null ? clips.fire : null;
        String run = clips != null ? clips.run : null;
        String melee = clips != null ? clips.melee : null;

        boolean hasHumu1 = hasBone(bones, "humu1");
        boolean hasHumu2 = hasBone(bones, "humu2");
        boolean hasFlare = hasBoneIgnoreCasePrefix(bones, "flare");
        boolean hasCross = hasBone(bones, "cross") || hasBone(bones, "Cross");
        boolean hasRoot = hasBone(bones, "root") || hasBone(bones, "Root");
        boolean hasLefthand = hasBone(bones, "Lefthand");
        boolean hasRighthand = hasBone(bones, "Righthand");

        String adsBone = prefer(bones, "bone");
        String recoilBone = prefer(bones, "fireRootNormal", "gun");
        String boltBone = prefer(bones, "shuan", "huatao");

        GunBridgeProfile tierA = new GunBridgeProfile(
                itemId,
                simple,
                resource.useOldHandRenderer,
                geo,
                texture,
                lodGeo,
                lodTexture,
                animationRl,
                idle,
                edit,
                bolt,
                reload,
                reloadNormal,
                reloadEmpty,
                fire,
                run,
                melee,
                hasHumu1,
                hasHumu2,
                hasFlare,
                hasCross,
                hasRoot,
                hasLefthand,
                hasRighthand,
                adsBone,
                recoilBone,
                boltBone,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                1f,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                Map.of(),
                Map.of(),
                Map.of(),
                modelClass,
                rendererClass,
                hash);

        GunBridgeProfile enriched = GunFpProbe.enrich(tierA);
        GunProfileDiskCache.save(itemId, hash, enriched);
        return enriched;
    }

    private static String rendererClassOf(Item item) {
        if (!(item instanceof CustomRendererItem custom)) {
            return "unknown";
        }
        try {
            Object renderer = custom.getRenderer().get();
            return renderer != null ? renderer.getClass().getName() : "unknown";
        } catch (Exception e) {
            Coltan.LOGGER.debug("Could not resolve renderer class for {}: {}",
                    item.getClass().getSimpleName(), e.toString());
            return "unknown";
        }
    }

    private static boolean hasAttachmentBones(List<String> bones) {
        for (String name : bones) {
            if (name != null && ATTACHMENT_BONE.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBone(List<String> bones, String exact) {
        for (String name : bones) {
            if (exact.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBoneIgnoreCasePrefix(List<String> bones, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String name : bones) {
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String prefer(List<String> bones, String... candidates) {
        for (String candidate : candidates) {
            if (hasBone(bones, candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
