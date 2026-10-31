package com.neoalive.coltan.client.compat.bridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.client.compat.ColtanArmorSkins;
import com.neoalive.coltan.debug.ColtanDebug;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.bedrock.BedrockImporter;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.ModelTextures;
import com.wf.gemrender.texture.VariantUv;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/** Fixed catalog of SBW military armor pieces and their GemRender skinned-model cache. */
public final class ArmorBridgeCache {
    private static final Map<ResourceLocation, Piece> BY_ITEM = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> TEXTURE_OVERRIDES =
            new ConcurrentHashMap<>();

    private static final ModelCache<GemRenderGltfModel> MODELS = new ModelCache<>(
            "Coltan SBW armor",
            ArmorBridgeCache::loadModel,
            (id, model) -> {
                TEXTURE_OVERRIDES.remove(id);
                for (ResourceLocation texture : model.textures()) {
                    ModelTextures.release(texture);
                }
            });

    static {
        register("ru_helmet_6b47", EquipmentSlot.HEAD);
        register("ru_chest_6b43", EquipmentSlot.CHEST);
        register("us_helmet_pasgt", EquipmentSlot.HEAD);
        register("us_chest_iotv", EquipmentSlot.CHEST);
        register("ge_helmet_m_35", EquipmentSlot.HEAD);
    }

    private ArmorBridgeCache() {
    }

    private static void register(String path, EquipmentSlot slot) {
        ResourceLocation itemId = new ResourceLocation("superbwarfare", path);
        ResourceLocation geo = new ResourceLocation("superbwarfare", "models/bedrock/armor/" + path + ".geo.json");
        ResourceLocation texture = new ResourceLocation("superbwarfare", "textures/bedrock/armor/" + path + ".png");
        BY_ITEM.put(itemId, new Piece(itemId, geo, texture, slot,
                new BridgeModelSlot<>(MODELS, bridgeModelId(itemId), texture)));
    }

    private static GemRenderGltfModel loadModel(ResourceLocation id) throws Exception {
        Piece piece = pieceForModelId(id);
        if (piece == null) {
            throw new IllegalArgumentException("unknown Coltan armor model id: " + id);
        }
        List<ResourceLocation> skins = VARIANT_SKINS.get(id);
        if (skins != null) {
            return BedrockImporter.load(piece.geo(), id, skins);
        }
        ResourceLocation texture = TEXTURE_OVERRIDES.getOrDefault(id, piece.texture());
        return BedrockImporter.load(piece.geo(), texture);
    }

    /**
     * Variant-set id -> its skins (stock first). Never cleared: a resource reload re-requests the
     * same handles in place and the loader must still find their skins.
     */
    private static final Map<ResourceLocation, List<ResourceLocation>> VARIANT_SKINS = new ConcurrentHashMap<>();
    /** Armor item id -> current variant set, rebuilt when the declared catalog changes. */
    private static final Map<ResourceLocation, VariantSet> VARIANT_SETS = new ConcurrentHashMap<>();
    private static final AtomicInteger VARIANT_GENERATION = new AtomicInteger();

    /** A worn piece drawn from its variant model, or {@code null} model while that model loads. */
    public record Variant(@Nullable GemRenderGltfModel model, VariantUv uv) {
    }

    /**
     * The variant-model route for this piece and resolved texture, or {@code null} to use the
     * per-texture route ({@link #handle(Piece, ResourceLocation)}): no catalog declared, texture not
     * in it, or the variant import failed / came back unatlased (size mismatch, sheet ceiling).
     * While the variant model loads, draws the stock model if resident rather than importing a
     * per-texture model that is about to be thrown away.
     */
    @Nullable
    public static Variant variant(Piece piece, ResourceLocation texture) {
        List<ResourceLocation> catalog = ColtanArmorSkins.catalog(piece.itemId());
        if (catalog.isEmpty()) {
            return null;
        }
        VariantSet set = VARIANT_SETS.get(piece.itemId());
        if (set == null || (set.catalog != catalog && !set.catalog.equals(catalog))) {
            set = buildVariantSet(piece, catalog);
        }
        Integer index = set.index.get(texture);
        if (index == null || set.handle.hasFailed()) {
            return null;
        }
        GemRenderGltfModel model = set.handle.get();
        if (model == null) {
            return new Variant(piece.model().handle().get(), VariantUv.NONE);
        }
        if (model.variantCount() < set.skins.size()) {
            ColtanDebug.failOnce("armor-variants-" + piece.itemId(),
                    "armor %s: variant model has %d of %d skins (sheet ceiling or unatlased), per-texture fallback",
                    piece.itemId(), model.variantCount(), set.skins.size());
            return null;
        }
        return new Variant(model, model.variant(index));
    }

    private static synchronized VariantSet buildVariantSet(Piece piece, List<ResourceLocation> catalog) {
        VariantSet current = VARIANT_SETS.get(piece.itemId());
        if (current != null && (current.catalog == catalog || current.catalog.equals(catalog))) {
            return current;
        }
        List<ResourceLocation> skins = new ArrayList<>(catalog.size() + 1);
        Map<ResourceLocation, Integer> index = new HashMap<>();
        skins.add(piece.texture());
        index.put(piece.texture(), 0);
        for (ResourceLocation skin : catalog) {
            if (skin != null && !index.containsKey(skin)) {
                index.put(skin, skins.size());
                skins.add(skin);
            }
        }
        ResourceLocation id = new ResourceLocation(piece.model().id().getNamespace(),
                piece.model().id().getPath() + "/variants/" + VARIANT_GENERATION.incrementAndGet());
        VARIANT_SKINS.put(id, List.copyOf(skins));
        VariantSet set = new VariantSet(catalog, List.copyOf(skins), Map.copyOf(index), MODELS.handle(id));
        VARIANT_SETS.put(piece.itemId(), set);
        ColtanDebug.log(ColtanDebug.Cat.ARMOR, "armor %s: variant set %s with %d skin(s)",
                piece.itemId(), id, skins.size());
        return set;
    }

    private record VariantSet(List<ResourceLocation> catalog, List<ResourceLocation> skins,
            Map<ResourceLocation, Integer> index, ModelCache.Handle<GemRenderGltfModel> handle) {
    }

    public static synchronized void rebuild() {
        TEXTURE_OVERRIDES.clear();
        Coltan.LOGGER.info("Coltan SBW armor bridge: {} piece(s)", BY_ITEM.size());
        ColtanDebug.log(ColtanDebug.Cat.ARMOR, "catalog ready pieces=%d", BY_ITEM.size());
    }

    public static synchronized void reloadModels() {
        MODELS.reload();
    }

    /** True when an import failed (e.g. requested before resources were ready). */
    public static boolean hasFailedModels() {
        return MODELS.failedCount() > 0;
    }

    public static Collection<Piece> pieces() {
        return Collections.unmodifiableCollection(BY_ITEM.values());
    }

    public static boolean owns(Item item) {
        ResourceLocation id = itemIdOf(item);
        return id != null && BY_ITEM.containsKey(id);
    }

    @Nullable
    public static Piece piece(Item item) {
        ResourceLocation id = itemIdOf(item);
        return id == null ? null : BY_ITEM.get(id);
    }

    /**
     * Registry key when available. During Item construction Forge has not registered the item yet,
     * so fall back to matching the known catalog by path derived from description id when present.
     */
    @Nullable
    private static ResourceLocation itemIdOf(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null) {
            return id;
        }
        // Construction-time: descriptionId is often still the default unset form; match catalog by
        // scanning — only 5 pieces. Prefer exact class name via getClass when registry is empty.
        String simple = item.getClass().getSimpleName();
        for (ResourceLocation known : BY_ITEM.keySet()) {
            // ru_helmet_6b47 → RuHelmet6b47Item-style is unreliable; armor uses Kotlin classes.
            // Match if path appears in canonical name (com...RuHelmet6b47Item / us_helmet...).
            String path = known.getPath().replace("_", "");
            String compact = simple.toLowerCase().replace("item", "");
            if (compact.contains(path.replace("_", "")) || pathEqualsArmorClass(known.getPath(), simple)) {
                return known;
            }
        }
        return null;
    }

    private static boolean pathEqualsArmorClass(String path, String simpleName) {
        // us_helmet_pasgt ↔ UsHelmetPasgtItem
        StringBuilder camel = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            camel.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                camel.append(part.substring(1));
            }
        }
        camel.append("Item");
        return camel.toString().equals(simpleName);
    }

    @Nullable
    public static Piece piece(ResourceLocation itemId) {
        return BY_ITEM.get(itemId);
    }

    public static ModelCache.Handle<GemRenderGltfModel> handle(Piece piece) {
        return handle(piece, null);
    }

    /**
     * Skinned handle, optionally rebaked with a soft-compat texture override. Distinct cache keys
     * keep crew paint from poisoning the shared stock mesh.
     */
    public static ModelCache.Handle<GemRenderGltfModel> handle(Piece piece,
            @Nullable ResourceLocation textureOverride) {
        return piece.model().handle(textureOverride, TEXTURE_OVERRIDES);
    }

    private static ResourceLocation bridgeModelId(ResourceLocation itemId) {
        return new ResourceLocation("coltan",
                "armor/" + itemId.getNamespace() + "/" + itemId.getPath());
    }

    @Nullable
    private static Piece pieceForModelId(ResourceLocation modelId) {
        String path = modelId.getPath();
        int skinMarker = path.indexOf("/skin/");
        if (skinMarker >= 0) {
            path = path.substring(0, skinMarker);
        }
        int variantMarker = path.indexOf("/variants/");
        if (variantMarker >= 0) {
            path = path.substring(0, variantMarker);
        }
        if (!path.startsWith("armor/")) {
            return null;
        }
        String rest = path.substring("armor/".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null;
        }
        ResourceLocation itemId = new ResourceLocation(rest.substring(0, slash), rest.substring(slash + 1));
        return BY_ITEM.get(itemId);
    }

    public record Piece(
            ResourceLocation itemId,
            ResourceLocation geo,
            ResourceLocation texture,
            EquipmentSlot slot,
            BridgeModelSlot<GemRenderGltfModel> model) {
    }
}
