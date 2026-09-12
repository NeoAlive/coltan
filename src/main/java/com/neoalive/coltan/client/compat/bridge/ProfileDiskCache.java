package com.neoalive.coltan.client.compat.bridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neoalive.coltan.Coltan;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Warm disk cache for sampled track curves / scale / zoom-hide flags under
 * {@code config/coltan/bridge-cache/}.
 */
public final class ProfileDiskCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static int hits;
    private static int misses;

    private ProfileDiskCache() {
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

    public static String contentHash(ResourceLocation geo, ResourceLocation texture, ResourceLocation animation,
                                     String rendererClass) {
        String payload = String.valueOf(geo) + "|" + texture + "|" + animation + "|" + rendererClass;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Integer.toHexString(payload.hashCode());
        }
    }

    /**
     * Loads a cached sample if present, ignoring content hash. Used when the client level is not
     * ready yet so a pre-level rebuild can still publish real track curves instead of defaults.
     */
    public static CachedSample loadAny(ResourceLocation entityId) {
        Path file = file(entityId);
        if (!Files.isRegularFile(file)) {
            misses++;
            return null;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null || !root.has("rotX")) {
                misses++;
                return null;
            }
            hits++;
            return CachedSample.fromJson(root);
        } catch (Exception e) {
            misses++;
            Coltan.LOGGER.debug("Bridge cache unreadable for {}: {}", entityId, e.toString());
            return null;
        }
    }

    public static CachedSample load(ResourceLocation entityId, String expectedHash) {
        Path file = file(entityId);
        if (!Files.isRegularFile(file)) {
            misses++;
            return null;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null || !expectedHash.equals(root.has("hash") ? root.get("hash").getAsString() : "")) {
                misses++;
                return null;
            }
            hits++;
            return CachedSample.fromJson(root);
        } catch (Exception e) {
            misses++;
            Coltan.LOGGER.debug("Bridge cache miss/corrupt for {}: {}", entityId, e.toString());
            return null;
        }
    }

    public static void save(ResourceLocation entityId, String hash, CachedSample sample) {
        Path file = file(entityId);
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = sample.toJson();
            root.addProperty("hash", hash);
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Coltan.LOGGER.warn("Could not write bridge cache for {}", entityId, e);
        }
    }

    private static Path file(ResourceLocation entityId) {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        return gameDir.resolve("config").resolve("coltan").resolve("bridge-cache")
                .resolve(entityId.getNamespace())
                .resolve(entityId.getPath() + ".json");
    }

    public record CachedSample(
            float renderScale,
            float trackDistance,
            int trackLength,
            float[] rotX,
            float[] moveY,
            float[] moveZ,
            float step,
            boolean hideTurretZoom,
            boolean hidePassengerZoom,
            String rendererClass
    ) {
        JsonObject toJson() {
            JsonObject root = new JsonObject();
            root.addProperty("renderScale", renderScale);
            root.addProperty("trackDistance", trackDistance);
            root.addProperty("trackLength", trackLength);
            root.addProperty("step", step);
            root.addProperty("hideTurretZoom", hideTurretZoom);
            root.addProperty("hidePassengerZoom", hidePassengerZoom);
            root.addProperty("rendererClass", rendererClass);
            root.add("rotX", floats(rotX));
            root.add("moveY", floats(moveY));
            root.add("moveZ", floats(moveZ));
            return root;
        }

        static CachedSample fromJson(JsonObject root) {
            return new CachedSample(
                    root.get("renderScale").getAsFloat(),
                    root.get("trackDistance").getAsFloat(),
                    root.get("trackLength").getAsInt(),
                    readFloats(root.getAsJsonArray("rotX")),
                    readFloats(root.getAsJsonArray("moveY")),
                    readFloats(root.getAsJsonArray("moveZ")),
                    root.get("step").getAsFloat(),
                    root.has("hideTurretZoom") && root.get("hideTurretZoom").getAsBoolean(),
                    root.has("hidePassengerZoom") && root.get("hidePassengerZoom").getAsBoolean(),
                    root.has("rendererClass") ? root.get("rendererClass").getAsString() : "");
        }

        private static JsonArray floats(float[] values) {
            JsonArray array = new JsonArray();
            for (float value : values) {
                array.add(value);
            }
            return array;
        }

        private static float[] readFloats(JsonArray array) {
            float[] out = new float[array.size()];
            for (int i = 0; i < array.size(); i++) {
                out[i] = array.get(i).getAsFloat();
            }
            return out;
        }
    }
}
