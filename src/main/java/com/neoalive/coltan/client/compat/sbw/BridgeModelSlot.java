package com.neoalive.coltan.client.compat.sbw;

import com.wf.gemrender.asset.ModelCache;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Handle resolved once. ModelCache.reload keeps the same handle objects and re-requests them in
 * place.
 */
public final class BridgeModelSlot<T> {
    private final ModelCache<T> models;
    private final ResourceLocation id;
    private final ResourceLocation stockTexture;
    private final ModelCache.Handle<T> handle;
    private final Map<ResourceLocation, Skin<T>> skins = new ConcurrentHashMap<>();

    public BridgeModelSlot(
            ModelCache<T> models, ResourceLocation id, ResourceLocation stockTexture) {
        this.models = models;
        this.id = id;
        this.stockTexture = stockTexture;
        this.handle = models.handle(id);
    }

    public ResourceLocation id() {
        return id;
    }

    public ModelCache.Handle<T> handle() {
        return handle;
    }

    /** Refill the override map on every hit. The cache disposer drops entries on reload. */
    public ModelCache.Handle<T> handle(
            @Nullable ResourceLocation textureOverride,
            Map<ResourceLocation, ResourceLocation> overrides) {
        if (textureOverride == null || Objects.equals(textureOverride, stockTexture)) {
            return handle;
        }
        Skin<T> skin = skins.get(textureOverride);
        if (skin == null) {
            ResourceLocation skinId =
                    new ResourceLocation(
                            id.getNamespace(),
                            id.getPath()
                                    + "/skin/"
                                    + textureOverride.getNamespace()
                                    + "/"
                                    + textureOverride.getPath());
            overrides.put(skinId, textureOverride);
            skin = new Skin<>(skinId, models.handle(skinId));
            Skin<T> raced = skins.putIfAbsent(textureOverride, skin);
            if (raced != null) {
                skin = raced;
            }
        }
        overrides.putIfAbsent(skin.id(), textureOverride);
        return skin.handle();
    }

    private record Skin<T>(ResourceLocation id, ModelCache.Handle<T> handle) {}
}
