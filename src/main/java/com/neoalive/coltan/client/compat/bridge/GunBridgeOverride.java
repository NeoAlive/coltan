package com.neoalive.coltan.client.compat.bridge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Optional per-gun FP patches under {@code assets/coltan/sbw_gun_bridge/}.
 *
 * <p>Also folds {@code _phase2.json} {@code ads} seed names (allowlist retired; ads only).
 */
public final class GunBridgeOverride {
    private static final Map<ResourceLocation, Data> PHASE2_ADS = new ConcurrentHashMap<>();
    private static volatile boolean phase2Loaded;

    private GunBridgeOverride() {
    }

    public record Data(
            @Nullable String adsSeed,
            @Nullable Float posX,
            @Nullable Float posY,
            @Nullable Float posZ,
            @Nullable Float scaleZ,
            @Nullable Float rootCustomX,
            @Nullable Float rootCustomY,
            @Nullable Float rootCustomZ,
            @Nullable Float recoilX,
            @Nullable Float recoilY,
            @Nullable Float recoilZ,
            @Nullable Float recoilRotX,
            @Nullable Float recoilRotY,
            @Nullable Float recoilRotZ,
            @Nullable Float recoilZoomMul,
            @Nullable Float recoilSpeed,
            Map<Integer, List<String>> scopeZoomHide,
            Map<Integer, GunBridgeProfile.Crosshair> scopeCrosshair,
            Map<Integer, GunBridgeProfile.AdsPose> scopeAds
    ) {
        public static Data empty() {
            return new Data(null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, Map.of(), Map.of(), Map.of());
        }

        public boolean isEmpty() {
            return adsSeed == null
                    && posX == null && posY == null && posZ == null && scaleZ == null
                    && rootCustomX == null && rootCustomY == null && rootCustomZ == null
                    && recoilX == null && recoilY == null && recoilZ == null
                    && recoilRotX == null && recoilRotY == null && recoilRotZ == null
                    && recoilZoomMul == null && recoilSpeed == null
                    && scopeZoomHide.isEmpty() && scopeCrosshair.isEmpty() && scopeAds.isEmpty();
        }
    }

    public static void clearPhase2Cache() {
        PHASE2_ADS.clear();
        phase2Loaded = false;
    }

    public static Data load(ResourceLocation itemId) {
        ensurePhase2();
        Data phase2 = PHASE2_ADS.getOrDefault(itemId, Data.empty());
        Data file = loadFile(itemId);
        return merge(phase2, file);
    }

    private static Data merge(Data base, Data over) {
        if (over.isEmpty()) {
            return base;
        }
        if (base.isEmpty()) {
            return over;
        }
        return new Data(
                over.adsSeed() != null ? over.adsSeed() : base.adsSeed(),
                over.posX() != null ? over.posX() : base.posX(),
                over.posY() != null ? over.posY() : base.posY(),
                over.posZ() != null ? over.posZ() : base.posZ(),
                over.scaleZ() != null ? over.scaleZ() : base.scaleZ(),
                over.rootCustomX() != null ? over.rootCustomX() : base.rootCustomX(),
                over.rootCustomY() != null ? over.rootCustomY() : base.rootCustomY(),
                over.rootCustomZ() != null ? over.rootCustomZ() : base.rootCustomZ(),
                over.recoilX() != null ? over.recoilX() : base.recoilX(),
                over.recoilY() != null ? over.recoilY() : base.recoilY(),
                over.recoilZ() != null ? over.recoilZ() : base.recoilZ(),
                over.recoilRotX() != null ? over.recoilRotX() : base.recoilRotX(),
                over.recoilRotY() != null ? over.recoilRotY() : base.recoilRotY(),
                over.recoilRotZ() != null ? over.recoilRotZ() : base.recoilRotZ(),
                over.recoilZoomMul() != null ? over.recoilZoomMul() : base.recoilZoomMul(),
                over.recoilSpeed() != null ? over.recoilSpeed() : base.recoilSpeed(),
                over.scopeZoomHide().isEmpty() ? base.scopeZoomHide() : over.scopeZoomHide(),
                over.scopeCrosshair().isEmpty() ? base.scopeCrosshair() : over.scopeCrosshair(),
                over.scopeAds().isEmpty() ? base.scopeAds() : over.scopeAds());
    }

    private static void ensurePhase2() {
        if (phase2Loaded) {
            return;
        }
        synchronized (GunBridgeOverride.class) {
            if (phase2Loaded) {
                return;
            }
            JsonObject root = read(new ResourceLocation("coltan", "sbw_gun_bridge/_phase2.json"));
            if (root != null && root.has("guns") && root.get("guns").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("guns")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject gun = element.getAsJsonObject();
                    if (!gun.has("id") || !gun.has("ads")) {
                        continue;
                    }
                    String id = gun.get("id").getAsString();
                    ResourceLocation itemId = id.indexOf(':') >= 0
                            ? ResourceLocation.tryParse(id)
                            : new ResourceLocation("superbwarfare", id);
                    if (itemId == null) {
                        continue;
                    }
                    PHASE2_ADS.put(itemId, new Data(
                            gun.get("ads").getAsString(),
                            null, null, null, null, null, null, null, null, null, null, null, null,
                            null, null, null, Map.of(), Map.of(), Map.of()));
                }
            }
            phase2Loaded = true;
        }
    }

    private static Data loadFile(ResourceLocation itemId) {
        String path = "sbw_gun_bridge/" + itemId.getNamespace() + "/" + itemId.getPath() + ".json";
        JsonObject root = read(new ResourceLocation("coltan", path));
        if (root == null) {
            root = read(new ResourceLocation("coltan", "sbw_gun_bridge/" + itemId.getPath() + ".json"));
        }
        if (root == null) {
            return Data.empty();
        }
        return parse(root);
    }

    private static Data parse(JsonObject root) {
        String ads = root.has("ads") ? root.get("ads").getAsString() : null;
        return new Data(
                ads,
                floatOrNull(root, "posX"),
                floatOrNull(root, "posY"),
                floatOrNull(root, "posZ"),
                floatOrNull(root, "scaleZ"),
                floatOrNull(root, "rootCustomX"),
                floatOrNull(root, "rootCustomY"),
                floatOrNull(root, "rootCustomZ"),
                floatOrNull(root, "recoilX"),
                floatOrNull(root, "recoilY"),
                floatOrNull(root, "recoilZ"),
                floatOrNull(root, "recoilRotX"),
                floatOrNull(root, "recoilRotY"),
                floatOrNull(root, "recoilRotZ"),
                floatOrNull(root, "recoilZoomMul"),
                floatOrNull(root, "recoilSpeed"),
                readZoomHide(root),
                readCrosshair(root),
                readAds(root));
    }

    @Nullable
    private static Float floatOrNull(JsonObject root, String key) {
        return root.has(key) ? root.get(key).getAsFloat() : null;
    }

    private static Map<Integer, List<String>> readZoomHide(JsonObject root) {
        if (!root.has("scopeZoomHide") || !root.get("scopeZoomHide").isJsonObject()) {
            return Map.of();
        }
        Map<Integer, List<String>> out = new LinkedHashMap<>();
        for (var entry : root.getAsJsonObject("scopeZoomHide").entrySet()) {
            int key;
            try {
                key = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            if (!entry.getValue().isJsonArray()) {
                continue;
            }
            List<String> bones = new ArrayList<>();
            for (JsonElement e : entry.getValue().getAsJsonArray()) {
                bones.add(e.getAsString());
            }
            out.put(key, List.copyOf(bones));
        }
        return out;
    }

    private static Map<Integer, GunBridgeProfile.Crosshair> readCrosshair(JsonObject root) {
        if (!root.has("scopeCrosshair") || !root.get("scopeCrosshair").isJsonObject()) {
            return Map.of();
        }
        Map<Integer, GunBridgeProfile.Crosshair> out = new LinkedHashMap<>();
        for (var entry : root.getAsJsonObject("scopeCrosshair").entrySet()) {
            int key;
            try {
                key = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject c = entry.getValue().getAsJsonObject();
            out.put(key, new GunBridgeProfile.Crosshair(
                    c.has("x") ? c.get("x").getAsDouble() : 0,
                    c.has("y") ? c.get("y").getAsDouble() : 0,
                    c.has("z") ? c.get("z").getAsDouble() : 0,
                    c.has("size") ? c.get("size").getAsFloat() : 1f,
                    c.has("r") ? c.get("r").getAsInt() : 255,
                    c.has("g") ? c.get("g").getAsInt() : 0,
                    c.has("b") ? c.get("b").getAsInt() : 0,
                    c.has("a") ? c.get("a").getAsInt() : 255,
                    c.has("texture") ? c.get("texture").getAsString() : "",
                    c.has("hasBlack") && c.get("hasBlack").getAsBoolean()));
        }
        return out;
    }

    private static Map<Integer, GunBridgeProfile.AdsPose> readAds(JsonObject root) {
        if (!root.has("scopeAds") || !root.get("scopeAds").isJsonObject()) {
            return Map.of();
        }
        Map<Integer, GunBridgeProfile.AdsPose> out = new LinkedHashMap<>();
        for (var entry : root.getAsJsonObject("scopeAds").entrySet()) {
            int key;
            try {
                key = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject p = entry.getValue().getAsJsonObject();
            out.put(key, new GunBridgeProfile.AdsPose(
                    p.has("posX") ? p.get("posX").getAsFloat() : 0f,
                    p.has("posY") ? p.get("posY").getAsFloat() : 0f,
                    p.has("posZ") ? p.get("posZ").getAsFloat() : 0f,
                    p.has("scaleZ") ? p.get("scaleZ").getAsFloat() : 1f));
        }
        return out;
    }

    @Nullable
    private static JsonObject read(ResourceLocation id) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (var in = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        } catch (Exception e) {
            Coltan.LOGGER.warn("Could not read gun bridge override {}", id, e);
            return null;
        }
    }
}
