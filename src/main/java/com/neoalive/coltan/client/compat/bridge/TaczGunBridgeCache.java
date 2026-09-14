package com.neoalive.coltan.client.compat.bridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.neoalive.coltan.debug.ColtanDebug;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.GunLod;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.bedrock.BedrockImporter;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.ModelTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

/**
 * GemRender cache for TACZ Bedrock gun geos, keyed by resolved display id. Used only for SEM-unit
 * held draws — never replaces player BEWLR.
 *
 * <p>{@code tacz:default} is a sentinel on the stack ({@link DefaultAssets#DEFAULT_GUN_DISPLAY_ID}),
 * not a real display file — resolve through the gun index's {@code display} field instead.
 *
 * <p>Prefers the display's LOD geo when present (third-person: one mesh, no mag/hand variants).
 */
public final class TaczGunBridgeCache {
    private static final FileToIdConverter GEO = FileToIdConverter.json("geo_models");
    private static final Map<ResourceLocation, Piece> BY_DISPLAY = new ConcurrentHashMap<>();

    private static final ModelCache<GemRenderGltfModel> MODELS = new ModelCache<>(
            "Coltan TACZ guns",
            TaczGunBridgeCache::loadModel,
            (id, model) -> {
                for (ResourceLocation texture : model.textures()) {
                    ModelTextures.release(texture);
                }
            });

    private TaczGunBridgeCache() {
    }

    public record Piece(ResourceLocation displayId, ResourceLocation geo, ResourceLocation texture,
            boolean lod) {
    }

    public static void init() {
        BY_DISPLAY.clear();
        TaczVisibilityClip.clear();
        ColtanDebug.log(ColtanDebug.Cat.GUN, "TaczGunBridgeCache init (lazy resolve)");
    }

    public static void reloadModels() {
        BY_DISPLAY.clear();
        TaczVisibilityClip.clear();
        MODELS.reload();
    }

    @Nullable
    public static Piece piece(ItemStack stack) {
        if (!(stack.getItem() instanceof IGun gun)) {
            return null;
        }
        ResourceLocation gunId = gun.getGunId(stack);
        if (gunId == null) {
            return null;
        }
        ResourceLocation displayId = resolveDisplayId(gun, stack, gunId);
        if (displayId == null) {
            return null;
        }
        Piece cached = BY_DISPLAY.get(displayId);
        if (cached != null) {
            return cached;
        }
        return resolve(displayId);
    }

    /**
     * Stack display id, or the gun index's default display when the stack still carries the
     * {@code tacz:default} sentinel.
     */
    @Nullable
    private static ResourceLocation resolveDisplayId(IGun gun, ItemStack stack, ResourceLocation gunId) {
        ResourceLocation displayId = gun.getGunDisplayId(stack);
        if (displayId != null && !displayId.equals(DefaultAssets.DEFAULT_GUN_DISPLAY_ID)
                && ClientAssetsManager.INSTANCE.getGunDisplay(displayId) != null) {
            return displayId;
        }
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getPojo().getDisplay())
                .orElse(null);
    }

    @Nullable
    private static synchronized Piece resolve(ResourceLocation displayId) {
        Piece existing = BY_DISPLAY.get(displayId);
        if (existing != null) {
            return existing;
        }
        GunDisplay display = ClientAssetsManager.INSTANCE.getGunDisplay(displayId);
        if (display == null) {
            ColtanDebug.once(ColtanDebug.Cat.GUN, "tacz-no-display-pojo-" + displayId,
                    "TACZ GunDisplay POJO missing for %s", displayId);
            return null;
        }
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        ResourceLocation modelLoc = display.getModelLocation();
        ResourceLocation texture = display.getModelTexture();
        boolean lod = false;
        GunLod gunLod = display.getGunLod();
        if (gunLod != null && gunLod.getModelLocation() != null && gunLod.getModelTexture() != null) {
            ResourceLocation lodGeo = GEO.idToFile(gunLod.getModelLocation());
            if (resources.getResource(lodGeo).isPresent()) {
                modelLoc = gunLod.getModelLocation();
                texture = gunLod.getModelTexture();
                lod = true;
            }
        }
        if (modelLoc == null) {
            ColtanDebug.once(ColtanDebug.Cat.GUN, "tacz-no-model-" + displayId,
                    "TACZ display %s has no model field", displayId);
            return null;
        }
        ResourceLocation geo = GEO.idToFile(modelLoc);
        if (texture == null) {
            ColtanDebug.once(ColtanDebug.Cat.GUN, "tacz-no-tex-" + displayId,
                    "TACZ display %s has no texture", displayId);
            return null;
        }
        if (resources.getResource(geo).isEmpty()) {
            ColtanDebug.failOnce("tacz-geo-miss-" + displayId,
                    "TACZ geo missing at %s (display %s)", geo, displayId);
            return null;
        }
        Piece piece = new Piece(displayId, geo, texture, lod);
        BY_DISPLAY.put(displayId, piece);
        MODELS.handle(bridgeModelId(piece));
        ColtanDebug.once(ColtanDebug.Cat.GUN, "tacz-piece-" + displayId,
                "TACZ gun claimed display=%s lod=%s geo=%s tex=%s", displayId, lod, geo, texture);
        return piece;
    }

    public static ModelCache.Handle<GemRenderGltfModel> handle(Piece piece) {
        return MODELS.handle(bridgeModelId(piece));
    }

    public static ResourceLocation bridgeModelId(Piece piece) {
        return new ResourceLocation("coltan",
                "tacz_gun/" + piece.displayId().getNamespace() + "/" + piece.displayId().getPath());
    }

    private static GemRenderGltfModel loadModel(ResourceLocation id) throws Exception {
        Piece piece = null;
        for (Piece p : BY_DISPLAY.values()) {
            if (bridgeModelId(p).equals(id)) {
                piece = p;
                break;
            }
        }
        if (piece == null) {
            throw new IllegalArgumentException("unknown Coltan TACZ gun model id: " + id);
        }
        return BedrockImporter.load(piece.geo(), piece.texture());
    }
}
