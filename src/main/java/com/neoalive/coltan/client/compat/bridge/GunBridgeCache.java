package com.neoalive.coltan.client.compat.bridge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.ModelTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Catalog of SBW guns drawn through GemRender ({@code GunRendererBuilder.simple} set + phase-2
 * dedicated allowlist).
 *
 * <p>The item id set is filled in a static initializer so {@link #owns(Item)} works when Forge
 * calls {@code GunGeoItem.getClientExtensions} during client setup — before {@link #rebuild()}
 * runs. Leaving the map empty until rebuild let GeckoLib keep the viewmodel (animated), which
 * looked like the bridge was a no-op.
 */
public final class GunBridgeCache {
    private static final String[] SIMPLE_IDS = {
            "glock_17", "glock_18", "mp_443", "m_1911", "homemade_shotgun", "aa_12", "marlin",
            "k_98", "hunting_rifle", "m_60", "m_2_hb", "m_79", "secondary_cataclysm",
            "super_star_shooter"
    };

    private static final String[] PHASE2_IDS = {"ak_47", "hk_416", "m_4"};

    private static final Map<ResourceLocation, Piece> BY_ITEM = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> TEXTURE_OVERRIDES =
            new ConcurrentHashMap<>();

    private static final ModelCache<GemRenderGltfModel> MODELS = new ModelCache<>(
            "Coltan SBW guns",
            GunBridgeCache::loadModel,
            (id, model) -> {
                TEXTURE_OVERRIDES.remove(id);
                for (ResourceLocation texture : model.textures()) {
                    ModelTextures.release(texture);
                }
            });

    static {
        for (String path : SIMPLE_IDS) {
            registerGun(path, true, Map.of(), null);
        }
        for (String path : PHASE2_IDS) {
            registerGun(path, false, Map.of(), null);
        }
    }

    private GunBridgeCache() {
    }

    private static GemRenderGltfModel loadModel(ResourceLocation id) throws Exception {
        Piece piece = pieceForModelId(id);
        if (piece == null) {
            throw new IllegalArgumentException("unknown Coltan gun model id: " + id);
        }
        boolean lod = id.getPath().contains("/lod");
        ResourceLocation geo = lod && piece.lodGeo() != null ? piece.lodGeo() : piece.geo();
        ResourceLocation texture = TEXTURE_OVERRIDES.get(id);
        if (texture == null) {
            texture = lod && piece.lodTexture() != null ? piece.lodTexture() : piece.texture();
        }
        return GunModelLoader.load(geo, texture, lod ? null : piece.animation());
    }

    public static synchronized void rebuild() {
        TEXTURE_OVERRIDES.clear();

        Set<ResourceLocation> excluded = loadExcludeList();
        ResourceManager resources = Minecraft.getInstance().getResourceManager();

        // Refresh LOD paths / phase-2 attachment maps; keep static catalog for owns().
        for (String path : SIMPLE_IDS) {
            registerGun(path, true, Map.of(), resources);
        }
        loadPhase2(resources);
        for (ResourceLocation excludedId : excluded) {
            BY_ITEM.remove(excludedId);
        }

        for (Piece piece : BY_ITEM.values()) {
            MODELS.handle(bridgeModelId(piece, false));
            if (piece.lodGeo() != null) {
                MODELS.handle(bridgeModelId(piece, true));
            }
        }
        GunVisibilityClip.clear();
        Coltan.LOGGER.info("Coltan SBW gun bridge: {} piece(s)", BY_ITEM.size());
    }

    public static synchronized void reloadModels() {
        MODELS.reload();
    }

    private static void loadPhase2(ResourceManager resources) {
        JsonObject root = readJson(new ResourceLocation("coltan", "sbw_gun_bridge/_phase2.json"));
        if (root == null || !root.has("guns") || !root.get("guns").isJsonArray()) {
            for (String path : PHASE2_IDS) {
                registerGun(path, false, Map.of(), resources);
            }
            return;
        }
        for (JsonElement element : root.getAsJsonArray("guns")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("id")) {
                continue;
            }
            String path = entry.get("id").getAsString();
            Map<String, List<String>> bones = Map.of();
            if (entry.has("attachmentBones") && entry.get("attachmentBones").isJsonObject()) {
                Map<String, List<String>> parsed = new LinkedHashMap<>();
                for (var slot : entry.getAsJsonObject("attachmentBones").entrySet()) {
                    if (!slot.getValue().isJsonArray()) {
                        continue;
                    }
                    parsed.put(slot.getKey(), jsonStrings(slot.getValue().getAsJsonArray()));
                }
                bones = Map.copyOf(parsed);
            }
            registerGun(path, false, bones, resources);
        }
    }

    private static void registerGun(String path, boolean simple, Map<String, List<String>> bones,
            @Nullable ResourceManager resources) {
        ResourceLocation itemId = new ResourceLocation("superbwarfare", path);
        ResourceLocation geo = geoFor(path);
        ResourceLocation texture = textureFor(path);
        ResourceLocation lodGeo = resources != null ? lodGeoFor(path, resources) : null;
        ResourceLocation lodTexture = resources != null
                ? lodTextureFor(path, lodGeo, texture, resources)
                : null;
        ResourceLocation animation = animationFor(path, resources);

        GunBridgeProfile profile = simple
                ? GunBridgeProfile.simple(itemId)
                : GunBridgeProfile.dedicated(itemId, bones);
        BY_ITEM.put(itemId, new Piece(itemId, geo, texture, lodGeo, lodTexture, animation, profile));
    }

    /** HK shares the M4 animation file in SBW client gun JSON. */
    @Nullable
    private static ResourceLocation animationFor(String path, @Nullable ResourceManager resources) {
        String animPath = "hk_416".equals(path) ? "m_4" : path;
        if ("m_79".equals(path)) {
            animPath = "m79";
        }
        ResourceLocation anim =
                new ResourceLocation("superbwarfare", "animations/" + animPath + ".animation.json");
        if (resources != null && resources.getResource(anim).isEmpty()) {
            return null;
        }
        return anim;
    }

    /** SBW stores M79 geo as {@code m79.geo.json} (no underscore). */
    private static ResourceLocation geoFor(String path) {
        if ("m_79".equals(path)) {
            return new ResourceLocation("superbwarfare", "geo/m79.geo.json");
        }
        return new ResourceLocation("superbwarfare", "geo/" + path + ".geo.json");
    }

    /** Glock 18 reuses the Glock 17 sheet in SBW gun data. */
    private static ResourceLocation textureFor(String path) {
        if ("glock_18".equals(path)) {
            return new ResourceLocation("superbwarfare", "textures/item/glock_17.png");
        }
        return new ResourceLocation("superbwarfare", "textures/item/" + path + ".png");
    }

    private static ResourceLocation lodGeoFor(String path, ResourceManager resources) {
        String lodPath = "glock_18".equals(path) ? "glock_17" : path;
        ResourceLocation lodGeo = new ResourceLocation("superbwarfare", "geo/lod/" + lodPath + ".geo.json");
        return resources.getResource(lodGeo).isPresent() ? lodGeo : null;
    }

    private static ResourceLocation lodTextureFor(String path, @Nullable ResourceLocation lodGeo,
            ResourceLocation fallback, ResourceManager resources) {
        if (lodGeo == null) {
            return null;
        }
        String lodPath = "glock_18".equals(path) ? "glock_17" : path;
        ResourceLocation lodTex =
                new ResourceLocation("superbwarfare", "textures/item/lod/" + lodPath + ".png");
        if (resources.getResource(lodTex).isPresent()) {
            return lodTex;
        }
        // homemade_shotgun has LOD geo but no LOD sheet — fall back to full texture.
        return fallback;
    }

    /** Class simple name → registry path. Needed because Forge calls initializeClient in the Item
     * constructor before the item is registered, so {@link ForgeRegistries#ITEMS}.getKey is null. */
    private static final Map<String, String> CLASS_TO_PATH = Map.ofEntries(
            Map.entry("Glock17Item", "glock_17"),
            Map.entry("Glock18Item", "glock_18"),
            Map.entry("Mp443Item", "mp_443"),
            Map.entry("M1911Item", "m_1911"),
            Map.entry("HomemadeShotgunItem", "homemade_shotgun"),
            Map.entry("Aa12Item", "aa_12"),
            Map.entry("MarlinItem", "marlin"),
            Map.entry("K98Item", "k_98"),
            Map.entry("HuntingRifleItem", "hunting_rifle"),
            Map.entry("M60Item", "m_60"),
            Map.entry("M2HBItem", "m_2_hb"),
            Map.entry("M79Item", "m_79"),
            Map.entry("SecondaryCataclysmItem", "secondary_cataclysm"),
            Map.entry("SuperStarShooterItem", "super_star_shooter"),
            Map.entry("AK47Item", "ak_47"),
            Map.entry("Hk416Item", "hk_416"),
            Map.entry("M4Item", "m_4"));

    public static Collection<Piece> pieces() {
        return Collections.unmodifiableCollection(BY_ITEM.values());
    }

    public static boolean owns(Item item) {
        return itemIdOf(item) != null;
    }

    @Nullable
    public static Piece piece(Item item) {
        ResourceLocation id = itemIdOf(item);
        return id == null ? null : BY_ITEM.get(id);
    }

    @Nullable
    public static Piece piece(ResourceLocation itemId) {
        return BY_ITEM.get(itemId);
    }

    /** Registry key when available; otherwise class→path map for construction-time claims. */
    @Nullable
    public static ResourceLocation itemIdOf(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null && BY_ITEM.containsKey(id)) {
            return id;
        }
        String path = CLASS_TO_PATH.get(item.getClass().getSimpleName());
        if (path == null) {
            return null;
        }
        ResourceLocation fallback = new ResourceLocation("superbwarfare", path);
        return BY_ITEM.containsKey(fallback) ? fallback : null;
    }

    public static ModelCache.Handle<GemRenderGltfModel> handle(Piece piece) {
        return handle(piece, false, null);
    }

    public static ModelCache.Handle<GemRenderGltfModel> handle(Piece piece, boolean lod) {
        return handle(piece, lod, null);
    }

    /**
     * Skinned handle, optionally rebaked with a soft-compat texture override. Distinct cache keys
     * keep paint overrides from poisoning the shared stock mesh.
     */
    public static ModelCache.Handle<GemRenderGltfModel> handle(Piece piece, boolean lod,
            @Nullable ResourceLocation textureOverride) {
        ResourceLocation baseId = bridgeModelId(piece, lod && piece.lodGeo() != null);
        if (textureOverride == null || Objects.equals(textureOverride,
                lod && piece.lodTexture() != null ? piece.lodTexture() : piece.texture())) {
            return MODELS.handle(baseId);
        }
        ResourceLocation id = skinnedModelId(baseId, textureOverride);
        TEXTURE_OVERRIDES.put(id, textureOverride);
        return MODELS.handle(id);
    }

    private static ResourceLocation bridgeModelId(Piece piece, boolean lod) {
        String path = "gun/" + piece.itemId().getNamespace() + "/" + piece.itemId().getPath();
        if (lod) {
            path = path + "/lod";
        }
        return new ResourceLocation("coltan", path);
    }

    private static ResourceLocation skinnedModelId(ResourceLocation base, ResourceLocation texture) {
        return new ResourceLocation("coltan",
                base.getPath() + "/skin/" + texture.getNamespace() + "/" + texture.getPath());
    }

    @Nullable
    private static Piece pieceForModelId(ResourceLocation modelId) {
        String path = modelId.getPath();
        int skinMarker = path.indexOf("/skin/");
        if (skinMarker >= 0) {
            path = path.substring(0, skinMarker);
        }
        boolean lod = path.endsWith("/lod");
        if (lod) {
            path = path.substring(0, path.length() - "/lod".length());
        }
        if (!path.startsWith("gun/")) {
            return null;
        }
        String rest = path.substring("gun/".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null;
        }
        return BY_ITEM.get(new ResourceLocation(rest.substring(0, slash), rest.substring(slash + 1)));
    }

    private static Set<ResourceLocation> loadExcludeList() {
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

    private static List<String> jsonStrings(JsonArray array) {
        return array.asList().stream().map(JsonElement::getAsString).toList();
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

    public record Piece(
            ResourceLocation itemId,
            ResourceLocation geo,
            ResourceLocation texture,
            @Nullable ResourceLocation lodGeo,
            @Nullable ResourceLocation lodTexture,
            @Nullable ResourceLocation animation,
            GunBridgeProfile profile
    ) {
    }
}
