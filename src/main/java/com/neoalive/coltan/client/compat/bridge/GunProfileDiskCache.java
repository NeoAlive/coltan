package com.neoalive.coltan.client.compat.bridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neoalive.coltan.Coltan;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Disk cache for sampled gun bridge profiles under config/coltan/bridge-cache/gun/. */
public final class GunProfileDiskCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static int hits;
    private static int misses;

    private GunProfileDiskCache() {
    }

    public static void resetStats() {
        hits = 0;
        misses = 0;
    }

    public static int hits() {
        return hits;
    }

    public static int misses() {
        return misses;
    }

    public static String contentHash(
            @Nullable ResourceLocation geo,
            @Nullable ResourceLocation texture,
            @Nullable ResourceLocation animation,
            String modelClass,
            String rendererClass) {
        String payload = String.valueOf(geo) + "|" + texture + "|" + animation + "|"
                + modelClass + "|" + rendererClass;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Integer.toHexString(payload.hashCode());
        }
    }

    /** Load ignoring hash so rebuilds can still reuse a prior sample. */
    @Nullable
    public static GunBridgeProfile loadAny(ResourceLocation itemId) {
        Path file = file(itemId);
        if (!Files.isRegularFile(file)) {
            misses++;
            return null;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null || !root.has("itemId")) {
                misses++;
                return null;
            }
            hits++;
            return fromJson(root);
        } catch (Exception e) {
            misses++;
            Coltan.LOGGER.debug("Gun bridge cache unreadable for {}: {}", itemId, e.toString());
            return null;
        }
    }

    @Nullable
    public static GunBridgeProfile load(ResourceLocation itemId, String expectedHash) {
        Path file = file(itemId);
        if (!Files.isRegularFile(file)) {
            misses++;
            return null;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null || !expectedHash.equals(
                    root.has("contentHash") ? root.get("contentHash").getAsString()
                            : root.has("hash") ? root.get("hash").getAsString() : "")) {
                misses++;
                return null;
            }
            hits++;
            return fromJson(root);
        } catch (Exception e) {
            misses++;
            Coltan.LOGGER.debug("Gun bridge cache miss/corrupt for {}: {}", itemId, e.toString());
            return null;
        }
    }

    public static void save(ResourceLocation itemId, String hash, GunBridgeProfile profile) {
        Path file = file(itemId);
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = toJson(profile.withHash(hash));
            root.addProperty("hash", hash);
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Coltan.LOGGER.warn("Could not write gun bridge cache for {}", itemId, e);
        }
    }

    static JsonObject toJson(GunBridgeProfile profile) {
        JsonObject root = new JsonObject();
        root.addProperty("itemId", profile.itemId().toString());
        root.addProperty("simple", profile.simple());
        root.addProperty("useOldHandRenderer", profile.useOldHandRenderer());
        putRl(root, "geo", profile.geo());
        putRl(root, "texture", profile.texture());
        putRl(root, "lodGeo", profile.lodGeo());
        putRl(root, "lodTexture", profile.lodTexture());
        putRl(root, "animation", profile.animation());
        putStr(root, "idle", profile.idle());
        putStr(root, "edit", profile.edit());
        putStr(root, "bolt", profile.bolt());
        putStr(root, "reload", profile.reload());
        putStr(root, "reloadNormal", profile.reloadNormal());
        putStr(root, "reloadEmpty", profile.reloadEmpty());
        putStr(root, "fire", profile.fire());
        putStr(root, "run", profile.run());
        putStr(root, "melee", profile.melee());
        root.addProperty("hasHumu1", profile.hasHumu1());
        root.addProperty("hasHumu2", profile.hasHumu2());
        root.addProperty("hasFlare", profile.hasFlare());
        root.addProperty("hasCross", profile.hasCross());
        root.addProperty("hasRoot", profile.hasRoot());
        root.addProperty("hasLefthand", profile.hasLefthand());
        root.addProperty("hasRighthand", profile.hasRighthand());
        putStr(root, "adsBone", profile.adsBone());
        putStr(root, "recoilBone", profile.recoilBone());
        putStr(root, "boltBone", profile.boltBone());
        root.addProperty("rootCustomX", profile.rootCustomX());
        root.addProperty("rootCustomY", profile.rootCustomY());
        root.addProperty("rootCustomZ", profile.rootCustomZ());
        root.addProperty("posX", profile.posX());
        root.addProperty("posY", profile.posY());
        root.addProperty("posZ", profile.posZ());
        root.addProperty("scaleZ", profile.scaleZ());
        root.addProperty("recoilX", profile.recoilX());
        root.addProperty("recoilY", profile.recoilY());
        root.addProperty("recoilZ", profile.recoilZ());
        root.addProperty("recoilRotX", profile.recoilRotX());
        root.addProperty("recoilRotY", profile.recoilRotY());
        root.addProperty("recoilRotZ", profile.recoilRotZ());
        root.addProperty("recoilZoomMul", profile.recoilZoomMul());
        root.addProperty("recoilSpeed", profile.recoilSpeed());
        root.add("scopeZoomHide", scopeZoomHideJson(profile.scopeZoomHide()));
        root.add("scopeCrosshair", scopeCrosshairJson(profile.scopeCrosshair()));
        root.add("scopeAds", scopeAdsJson(profile.scopeAds()));
        root.addProperty("modelClass", profile.modelClass());
        root.addProperty("rendererClass", profile.rendererClass());
        root.addProperty("contentHash", profile.contentHash());
        return root;
    }

    static GunBridgeProfile fromJson(JsonObject root) {
        ResourceLocation itemId = ResourceLocation.tryParse(
                root.has("itemId") ? root.get("itemId").getAsString() : "");
        if (itemId == null) {
            itemId = new ResourceLocation("minecraft", "air");
        }
        return new GunBridgeProfile(
                itemId,
                bool(root, "simple", true),
                bool(root, "useOldHandRenderer", false),
                rl(root, "geo"),
                rl(root, "texture"),
                rl(root, "lodGeo"),
                rl(root, "lodTexture"),
                rl(root, "animation"),
                str(root, "idle"),
                str(root, "edit"),
                str(root, "bolt"),
                str(root, "reload"),
                str(root, "reloadNormal"),
                str(root, "reloadEmpty"),
                str(root, "fire"),
                str(root, "run"),
                str(root, "melee"),
                bool(root, "hasHumu1", false),
                bool(root, "hasHumu2", false),
                bool(root, "hasFlare", false),
                bool(root, "hasCross", false),
                bool(root, "hasRoot", false),
                bool(root, "hasLefthand", false),
                bool(root, "hasRighthand", false),
                str(root, "adsBone"),
                str(root, "recoilBone"),
                str(root, "boltBone"),
                floatVal(root, "rootCustomX", 0f),
                floatVal(root, "rootCustomY", 0f),
                floatVal(root, "rootCustomZ", 0f),
                floatVal(root, "posX", 0f),
                floatVal(root, "posY", 0f),
                floatVal(root, "posZ", 0f),
                floatVal(root, "scaleZ", 1f),
                floatVal(root, "recoilX", 0f),
                floatVal(root, "recoilY", 0f),
                floatVal(root, "recoilZ", 0f),
                floatVal(root, "recoilRotX", 0f),
                floatVal(root, "recoilRotY", 0f),
                floatVal(root, "recoilRotZ", 0f),
                floatVal(root, "recoilZoomMul", 0f),
                floatVal(root, "recoilSpeed", 0f),
                readScopeZoomHide(root),
                readScopeCrosshair(root),
                readScopeAds(root),
                root.has("modelClass") ? root.get("modelClass").getAsString() : "",
                root.has("rendererClass") ? root.get("rendererClass").getAsString() : "",
                root.has("contentHash") ? root.get("contentHash").getAsString()
                        : root.has("hash") ? root.get("hash").getAsString() : "");
    }

    private static Path file(ResourceLocation itemId) {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        return gameDir.resolve("config").resolve("coltan").resolve("bridge-cache").resolve("gun")
                .resolve(itemId.getNamespace())
                .resolve(itemId.getPath() + ".json");
    }

    private static void putRl(JsonObject root, String key, @Nullable ResourceLocation value) {
        if (value != null) {
            root.addProperty(key, value.toString());
        }
    }

    private static void putStr(JsonObject root, String key, @Nullable String value) {
        if (value != null) {
            root.addProperty(key, value);
        }
    }

    @Nullable
    private static ResourceLocation rl(JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return null;
        }
        return ResourceLocation.tryParse(root.get(key).getAsString());
    }

    @Nullable
    private static String str(JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return null;
        }
        return root.get(key).getAsString();
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        return root.has(key) ? root.get(key).getAsBoolean() : fallback;
    }

    private static float floatVal(JsonObject root, String key, float fallback) {
        return root.has(key) ? root.get(key).getAsFloat() : fallback;
    }

    private static JsonObject scopeZoomHideJson(Map<Integer, List<String>> map) {
        JsonObject out = new JsonObject();
        for (var entry : map.entrySet()) {
            JsonArray array = new JsonArray();
            for (String bone : entry.getValue()) {
                array.add(bone);
            }
            out.add(String.valueOf(entry.getKey()), array);
        }
        return out;
    }

    private static JsonObject scopeCrosshairJson(Map<Integer, GunBridgeProfile.Crosshair> map) {
        JsonObject out = new JsonObject();
        for (var entry : map.entrySet()) {
            GunBridgeProfile.Crosshair c = entry.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("x", c.x());
            obj.addProperty("y", c.y());
            obj.addProperty("z", c.z());
            obj.addProperty("size", c.size());
            obj.addProperty("r", c.r());
            obj.addProperty("g", c.g());
            obj.addProperty("b", c.b());
            obj.addProperty("a", c.a());
            obj.addProperty("texture", c.texture());
            obj.addProperty("hasBlack", c.hasBlack());
            out.add(String.valueOf(entry.getKey()), obj);
        }
        return out;
    }

    private static JsonObject scopeAdsJson(Map<Integer, GunBridgeProfile.AdsPose> map) {
        JsonObject out = new JsonObject();
        for (var entry : map.entrySet()) {
            GunBridgeProfile.AdsPose pose = entry.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("posX", pose.posX());
            obj.addProperty("posY", pose.posY());
            obj.addProperty("posZ", pose.posZ());
            obj.addProperty("scaleZ", pose.scaleZ());
            out.add(String.valueOf(entry.getKey()), obj);
        }
        return out;
    }

    private static Map<Integer, List<String>> readScopeZoomHide(JsonObject root) {
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
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                bones.add(element.getAsString());
            }
            out.put(key, List.copyOf(bones));
        }
        return out;
    }

    private static Map<Integer, GunBridgeProfile.Crosshair> readScopeCrosshair(JsonObject root) {
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

    private static Map<Integer, GunBridgeProfile.AdsPose> readScopeAds(JsonObject root) {
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
}
