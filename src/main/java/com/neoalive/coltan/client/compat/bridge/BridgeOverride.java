package com.neoalive.coltan.client.compat.bridge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Data-driven per-vehicle bridge patches from {@code assets/coltan/sbw_bridge/}.
 */
public final class BridgeOverride {
    private BridgeOverride() {
    }

    public record Data(
            boolean exclude,
            Float renderScale,
            List<String> extraGameplayBones,
            Map<String, String> boneAliases,
            boolean tracks,
            boolean propellers,
            List<String> zoomHideBones
    ) {
        public static Data defaults() {
            return new Data(false, null, List.of(), Map.of(), true, true, List.of());
        }
    }

    public static Set<ResourceLocation> loadExcludeList() {
        Set<ResourceLocation> out = new HashSet<>();
        JsonObject root = read("coltan:sbw_bridge/_exclude.json");
        if (root == null || !root.has("exclude")) {
            return out;
        }
        for (JsonElement element : root.getAsJsonArray("exclude")) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    public static Data load(ResourceLocation entityId) {
        String path = "sbw_bridge/" + entityId.getNamespace() + "/" + entityId.getPath() + ".json";
        JsonObject root = read(new ResourceLocation("coltan", path).toString());
        if (root == null) {
            // Also try without namespace folder duplication when entity is already coltan-keyed.
            root = read("coltan:sbw_bridge/" + entityId.getPath() + ".json");
        }
        if (root == null) {
            return Data.defaults();
        }

        boolean exclude = root.has("exclude") && root.get("exclude").getAsBoolean();
        Float scale = root.has("renderScale") ? root.get("renderScale").getAsFloat() : null;

        List<String> extra = new ArrayList<>();
        if (root.has("extraGameplayBones")) {
            for (JsonElement e : root.getAsJsonArray("extraGameplayBones")) {
                extra.add(e.getAsString());
            }
        }

        Map<String, String> aliases = new HashMap<>();
        if (root.has("boneAliases") && root.get("boneAliases").isJsonObject()) {
            for (var entry : root.getAsJsonObject("boneAliases").entrySet()) {
                aliases.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        boolean tracks = true;
        boolean propellers = true;
        List<String> zoomHide = new ArrayList<>();
        if (root.has("drivers") && root.get("drivers").isJsonObject()) {
            JsonObject drivers = root.getAsJsonObject("drivers");
            if (drivers.has("tracks")) {
                tracks = drivers.get("tracks").getAsBoolean();
            }
            if (drivers.has("propellers")) {
                propellers = drivers.get("propellers").getAsBoolean();
            }
            if (drivers.has("zoomHideBones")) {
                for (JsonElement e : drivers.getAsJsonArray("zoomHideBones")) {
                    zoomHide.add(e.getAsString());
                }
            }
        }

        return new Data(exclude, scale, List.copyOf(extra), Map.copyOf(aliases), tracks, propellers,
                List.copyOf(zoomHide));
    }

    private static JsonObject read(String location) {
        ResourceLocation id = ResourceLocation.tryParse(location);
        if (id == null) {
            return null;
        }
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (var in = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        } catch (Exception e) {
            Coltan.LOGGER.warn("Could not read bridge override {}", id, e);
            return null;
        }
    }
}
