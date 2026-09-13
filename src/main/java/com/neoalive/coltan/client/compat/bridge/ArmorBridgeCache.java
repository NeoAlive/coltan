package com.neoalive.coltan.client.compat.bridge;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.neoalive.coltan.Coltan;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.bedrock.BedrockImporter;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.ModelTextures;
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
        BY_ITEM.put(itemId, new Piece(itemId, geo, texture, slot));
    }

    private static GemRenderGltfModel loadModel(ResourceLocation id) throws Exception {
        Piece piece = pieceForModelId(id);
        if (piece == null) {
            throw new IllegalArgumentException("unknown Coltan armor model id: " + id);
        }
        ResourceLocation texture = TEXTURE_OVERRIDES.getOrDefault(id, piece.texture());
        return BedrockImporter.load(piece.geo(), texture);
    }

    public static synchronized void rebuild() {
        TEXTURE_OVERRIDES.clear();
        for (Piece piece : BY_ITEM.values()) {
            MODELS.handle(bridgeModelId(piece));
        }
        Coltan.LOGGER.info("Coltan SBW armor bridge: {} piece(s)", BY_ITEM.size());
    }

    public static synchronized void reloadModels() {
        MODELS.reload();
    }

    public static Collection<Piece> pieces() {
        return Collections.unmodifiableCollection(BY_ITEM.values());
    }

    public static boolean owns(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null && BY_ITEM.containsKey(id);
    }

    @Nullable
    public static Piece piece(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id == null ? null : BY_ITEM.get(id);
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
        if (textureOverride == null || Objects.equals(textureOverride, piece.texture())) {
            return MODELS.handle(bridgeModelId(piece));
        }
        ResourceLocation id = skinnedModelId(piece, textureOverride);
        TEXTURE_OVERRIDES.put(id, textureOverride);
        return MODELS.handle(id);
    }

    private static ResourceLocation bridgeModelId(Piece piece) {
        return new ResourceLocation("coltan",
                "armor/" + piece.itemId().getNamespace() + "/" + piece.itemId().getPath());
    }

    private static ResourceLocation skinnedModelId(Piece piece, ResourceLocation texture) {
        return new ResourceLocation("coltan",
                "armor/" + piece.itemId().getNamespace() + "/" + piece.itemId().getPath()
                        + "/skin/" + texture.getNamespace() + "/" + texture.getPath());
    }

    @Nullable
    private static Piece pieceForModelId(ResourceLocation modelId) {
        String path = modelId.getPath();
        int skinMarker = path.indexOf("/skin/");
        if (skinMarker >= 0) {
            path = path.substring(0, skinMarker);
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
            EquipmentSlot slot) {
    }
}
