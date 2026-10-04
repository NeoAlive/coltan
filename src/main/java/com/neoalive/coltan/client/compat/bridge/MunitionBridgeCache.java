package com.neoalive.coltan.client.compat.bridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import com.atsuishio.superbwarfare.item.HandGrenade;
import com.atsuishio.superbwarfare.item.projectile.Ptkm1rItem;
import com.atsuishio.superbwarfare.item.projectile.Tm62Item;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.debug.ColtanDebug;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.ModelTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/** Small catalog of SBW munition items drawn as Bedrock BEWLR through GemRender. */
public final class MunitionBridgeCache {
    /** Swapped wholesale by {@link #rebuild()}; render threads read it without locking. */
    private static volatile Map<ResourceLocation, Piece> BY_ITEM = Map.of();
    private static final AtomicBoolean REBUILT = new AtomicBoolean(false);
    /** Bumped after every catalog swap; item extensions re-resolve their renderer when it moves. */
    private static volatile int generation;

    private static final ModelCache<GemRenderGltfModel> MODELS = new ModelCache<>(
            "Coltan SBW munitions",
            MunitionBridgeCache::loadModel,
            (id, model) -> {
                for (ResourceLocation texture : model.textures()) {
                    ModelTextures.release(texture);
                }
            });

    private MunitionBridgeCache() {
    }

    private static GemRenderGltfModel loadModel(ResourceLocation id) throws Exception {
        Piece piece = pieceForModelId(id);
        if (piece == null) {
            throw new IllegalArgumentException("unknown Coltan munition model id: " + id);
        }
        return GunModelLoader.load(piece.geo(), piece.texture(), null);
    }

    public static synchronized void rebuild() {
        Map<ResourceLocation, Piece> byItem = new LinkedHashMap<>();

        add(byItem, "hand_grenade",
                new ResourceLocation("superbwarfare", "models/bedrock/item/hand_grenade.geo.json"),
                new ResourceLocation("superbwarfare", "textures/bedrock/item/hand_grenade.png"));
        add(byItem, "tm_62",
                new ResourceLocation("superbwarfare", "models/bedrock/projectile/tm_62.geo.json"),
                new ResourceLocation("superbwarfare", "textures/bedrock/projectile/tm_62.png"));

        ResourceLocation ptkmItemGeo = new ResourceLocation("superbwarfare",
                "models/bedrock/item/ptkm_1r_item.geo.json");
        ResourceLocation ptkmGeo = Minecraft.getInstance().getResourceManager().getResource(ptkmItemGeo)
                .isPresent()
                ? ptkmItemGeo
                : new ResourceLocation("superbwarfare", "models/bedrock/projectile/ptkm_1r_item.geo.json");
        if (Minecraft.getInstance().getResourceManager().getResource(ptkmGeo).isEmpty()) {
            ptkmGeo = new ResourceLocation("superbwarfare", "models/bedrock/projectile/ptkm_1r.geo.json");
        }
        add(byItem, "ptkm_1r", ptkmGeo,
                new ResourceLocation("superbwarfare", "textures/bedrock/projectile/ptkm_1r.png"));

        BY_ITEM = Collections.unmodifiableMap(byItem);
        REBUILT.set(true);
        generation++;
        Coltan.LOGGER.info("Coltan SBW munition bridge: {} piece(s)", BY_ITEM.size());
        ColtanDebug.log(ColtanDebug.Cat.MUNITION, "catalog ready pieces=%d", BY_ITEM.size());
    }

    private static void add(Map<ResourceLocation, Piece> byItem, String path, ResourceLocation geo,
            ResourceLocation texture) {
        ResourceLocation itemId = new ResourceLocation("superbwarfare", path);
        byItem.put(itemId, new Piece(itemId, geo, texture, MODELS.handle(bridgeModelId(itemId))));
    }

    public static synchronized void reloadModels() {
        MODELS.reload();
    }

    /**
     * Everything {@code loadModel} reads, per model. A rebuild that leaves this unchanged does not
     * need {@link #reloadModels()}.
     */
    public static List<List<Object>> modelInputs() {
        List<List<Object>> out = new ArrayList<>();
        for (Piece it : BY_ITEM.values()) {
            out.add(Arrays.asList(it.itemId(), it.geo(), it.texture()));
        }
        return out;
    }

    /** True when an import failed (e.g. requested before resources were ready). */
    public static boolean hasFailedModels() {
        return MODELS.failedCount() > 0;
    }

    /** Catalog generation; changes after every {@link #rebuild()}. */
    public static int generation() {
        return generation;
    }

    public static Collection<Piece> pieces() {
        return Collections.unmodifiableCollection(BY_ITEM.values());
    }

    public static boolean owns(Item item) {
        if (item instanceof HandGrenade || item instanceof Tm62Item || item instanceof Ptkm1rItem) {
            if (!REBUILT.get()) {
                return true;
            }
            ResourceLocation id = itemIdOf(item);
            return id != null && BY_ITEM.containsKey(id);
        }
        return false;
    }

    @Nullable
    public static Piece piece(Item item) {
        ResourceLocation id = itemIdOf(item);
        return id == null ? null : BY_ITEM.get(id);
    }

    @Nullable
    public static ResourceLocation itemIdOf(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null && BY_ITEM.containsKey(id)) {
            return id;
        }
        if (item instanceof HandGrenade) {
            return new ResourceLocation("superbwarfare", "hand_grenade");
        }
        if (item instanceof Tm62Item) {
            return new ResourceLocation("superbwarfare", "tm_62");
        }
        if (item instanceof Ptkm1rItem) {
            return new ResourceLocation("superbwarfare", "ptkm_1r");
        }
        return id;
    }

    public static ModelCache.Handle<GemRenderGltfModel> handle(Piece piece) {
        return piece.handle();
    }

    public static ResourceLocation bridgeModelId(Piece piece) {
        return bridgeModelId(piece.itemId());
    }

    private static ResourceLocation bridgeModelId(ResourceLocation itemId) {
        return new ResourceLocation("coltan",
                "munition/" + itemId.getNamespace() + "/" + itemId.getPath());
    }

    @Nullable
    private static Piece pieceForModelId(ResourceLocation id) {
        String path = id.getPath();
        if (!path.startsWith("munition/")) {
            return null;
        }
        String rest = path.substring("munition/".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null;
        }
        return BY_ITEM.get(new ResourceLocation(rest.substring(0, slash), rest.substring(slash + 1)));
    }

    public record Piece(ResourceLocation itemId, ResourceLocation geo, ResourceLocation texture,
            ModelCache.Handle<GemRenderGltfModel> handle) {
    }
}
