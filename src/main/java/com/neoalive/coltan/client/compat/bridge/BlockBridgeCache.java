package com.neoalive.coltan.client.compat.bridge;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.bedrock.BedrockImporter;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.ModelTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Fixed catalog of SBW Bedrock BER blocks and their GemRender skinned-model cache.
 *
 * <p>Uses {@link BedrockImporter#load} (skinned + PoseCache) rather than parts — matches GemRender
 * INTEGRATION §2 DrillVisual. Anim JSON is optional (containers have open clips; FuMO / tables do not).
 */
public final class BlockBridgeCache {
    private static final String[] BLOCK_IDS = {
            "container", "small_container", "lucky_container",
            "fumo_25", "vehicle_assembling_table", "blueprint_research_table"
    };

    private static final Map<ResourceLocation, Piece> BY_BLOCK = new LinkedHashMap<>();
    private static final Map<BlockEntityType<?>, Piece> BY_TYPE = new LinkedHashMap<>();

    private static final ModelCache<GemRenderGltfModel> MODELS = new ModelCache<>(
            "Coltan SBW blocks",
            BlockBridgeCache::loadModel,
            (id, model) -> {
                for (ResourceLocation texture : model.textures()) {
                    ModelTextures.release(texture);
                }
            });

    private BlockBridgeCache() {
    }

    private static GemRenderGltfModel loadModel(ResourceLocation id) throws Exception {
        Piece piece = pieceForModelId(id);
        if (piece == null) {
            throw new IllegalArgumentException("unknown Coltan block model id: " + id);
        }
        if (piece.animation() != null) {
            try {
                return BedrockImporter.load(piece.geo(), piece.texture(), List.of(piece.animation()));
            } catch (Exception e) {
                Coltan.LOGGER.warn("loadParts-style anim import failed for {}, falling back to load()",
                        piece.blockId(), e);
            }
        }
        return BedrockImporter.load(piece.geo(), piece.texture());
    }

    public static synchronized void rebuild() {
        BY_BLOCK.clear();
        BY_TYPE.clear();

        Set<ResourceLocation> excluded = loadExcludeList();
        for (String path : BLOCK_IDS) {
            ResourceLocation blockId = new ResourceLocation("superbwarfare", path);
            if (excluded.contains(blockId)) {
                continue;
            }
            ResourceLocation geo = new ResourceLocation("superbwarfare",
                    "models/bedrock/block/" + path + ".geo.json");
            ResourceLocation texture = new ResourceLocation("superbwarfare",
                    "textures/bedrock/block/" + path + ".png");
            ResourceLocation anim = new ResourceLocation("superbwarfare",
                    "animations/bedrock/block/" + path + ".animation.json");
            if (Minecraft.getInstance().getResourceManager().getResource(anim).isEmpty()) {
                anim = null;
            }
            Piece piece = new Piece(blockId, geo, texture, anim);
            BY_BLOCK.put(blockId, piece);

            BlockEntityType<?> type = ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(blockId);
            if (type != null) {
                BY_TYPE.put(type, piece);
            }
        }

        for (Piece piece : BY_BLOCK.values()) {
            MODELS.handle(bridgeModelId(piece));
        }
        Coltan.LOGGER.info("Coltan SBW block bridge: {} piece(s)", BY_BLOCK.size());
    }

    public static synchronized void reloadModels() {
        MODELS.reload();
    }

    public static Collection<Piece> pieces() {
        return Collections.unmodifiableCollection(BY_BLOCK.values());
    }

    public static Map<BlockEntityType<?>, Piece> byType() {
        return Collections.unmodifiableMap(BY_TYPE);
    }

    @Nullable
    public static Piece piece(BlockEntityType<?> type) {
        return BY_TYPE.get(type);
    }

    @Nullable
    public static Piece piece(ResourceLocation blockId) {
        return BY_BLOCK.get(blockId);
    }

    public static ModelCache.Handle<GemRenderGltfModel> handle(Piece piece) {
        return MODELS.handle(bridgeModelId(piece));
    }

    private static ResourceLocation bridgeModelId(Piece piece) {
        return new ResourceLocation("coltan",
                "block/" + piece.blockId().getNamespace() + "/" + piece.blockId().getPath());
    }

    @Nullable
    private static Piece pieceForModelId(ResourceLocation modelId) {
        String path = modelId.getPath();
        if (!path.startsWith("block/")) {
            return null;
        }
        String rest = path.substring("block/".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null;
        }
        return BY_BLOCK.get(new ResourceLocation(rest.substring(0, slash), rest.substring(slash + 1)));
    }

    private static Set<ResourceLocation> loadExcludeList() {
        Set<ResourceLocation> out = ConcurrentHashMap.newKeySet();
        ResourceLocation id = new ResourceLocation("coltan", "sbw_block_bridge/_exclude.json");
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return out;
        }
        try (var in = resource.get().open()) {
            JsonObject root = JsonParser.parseReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (!root.has("exclude")) {
                return out;
            }
            for (JsonElement element : root.getAsJsonArray("exclude")) {
                ResourceLocation exclude = ResourceLocation.tryParse(element.getAsString());
                if (exclude != null) {
                    out.add(exclude);
                }
            }
        } catch (Exception e) {
            Coltan.LOGGER.warn("Could not read {}", id, e);
        }
        return out;
    }

    public record Piece(
            ResourceLocation blockId,
            ResourceLocation geo,
            ResourceLocation texture,
            @Nullable ResourceLocation animation
    ) {
    }
}
