package com.neoalive.coltan.client.compat.bridge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.item.gun.EmptyGunItem;
import com.atsuishio.superbwarfare.item.gun.GunGeoItem;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Lists SBW {@link GunGeoItem}s for the gun bridge and keeps a class→registry-path map so
 * {@link GunBridgeCache#owns(Item)} works during Item construction (before registry keys exist).
 */
public final class GunBridgeDiscovery {
    /** Class simple name → registry path (usually without namespace; superbwarfare assumed). */
    private static final ConcurrentHashMap<String, String> CLASS_TO_PATH = new ConcurrentHashMap<>();

    static {
        // Former allowlist — construction-time claim before the first rebuild discovers the rest.
        warm("Glock17Item", "glock_17");
        warm("Glock18Item", "glock_18");
        warm("Mp443Item", "mp_443");
        warm("M1911Item", "m_1911");
        warm("HomemadeShotgunItem", "homemade_shotgun");
        warm("Aa12Item", "aa_12");
        warm("MarlinItem", "marlin");
        warm("K98Item", "k_98");
        warm("HuntingRifleItem", "hunting_rifle");
        warm("M60Item", "m_60");
        warm("M2HBItem", "m_2_hb");
        warm("M79Item", "m_79");
        warm("SecondaryCataclysmItem", "secondary_cataclysm");
        warm("SuperStarShooterItem", "super_star_shooter");
        warm("AK47Item", "ak_47");
        warm("Hk416Item", "hk_416");
        warm("M4Item", "m_4");
    }

    private GunBridgeDiscovery() {
    }

    public record Candidate(ResourceLocation itemId, Item item) {
    }

    private static void warm(String classSimpleName, String path) {
        CLASS_TO_PATH.put(classSimpleName, path);
    }

    public static Map<String, String> classToPath() {
        return CLASS_TO_PATH;
    }

    @Nullable
    public static String pathForClass(String classSimpleName) {
        return CLASS_TO_PATH.get(classSimpleName);
    }

    public static void remember(Item item, ResourceLocation itemId) {
        if (item == null || itemId == null) {
            return;
        }
        CLASS_TO_PATH.put(item.getClass().getSimpleName(), itemId.getPath());
    }

    public static List<Candidate> discover() {
        Set<ResourceLocation> excluded = loadExcludeList();
        List<Candidate> out = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (!(item instanceof GunGeoItem) || item instanceof EmptyGunItem) {
                continue;
            }
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId == null || excluded.contains(itemId)) {
                continue;
            }
            remember(item, itemId);
            out.add(new Candidate(itemId, item));
        }
        return out;
    }

    public static Set<ResourceLocation> loadExcludeList() {
        Set<ResourceLocation> out = ConcurrentHashMap.newKeySet();
        JsonObject root = readJson(new ResourceLocation("coltan", "sbw_gun_bridge/_exclude.json"));
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

    @Nullable
    private static JsonObject readJson(ResourceLocation id) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (var in = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        } catch (Exception e) {
            Coltan.LOGGER.warn("Could not read {}", id, e);
            return null;
        }
    }
}
