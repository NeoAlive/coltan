package com.neoalive.coltan.client.compat.bridge;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** Catalog entry for a SimpleEnemyMod unit drawn through GemRender. */
public record SemUnitBridgeProfile(
        ResourceLocation entityId,
        EntityType<?> entityType,
        String textureFolder,
        String texturePrefix,
        ResourceLocation geo,
        @Nullable ResourceLocation animation
) {
    public ResourceLocation bridgeModelId() {
        return new ResourceLocation("coltan",
                "unit/" + entityId.getNamespace() + "/" + entityId.getPath());
    }

    public ResourceLocation defaultTexture() {
        return new ResourceLocation("simpleenemymod",
                "textures/entity/" + textureFolder + "/" + texturePrefix + "_default.png");
    }

    public ResourceLocation textureForVariant(int variant) {
        if (variant <= 0) {
            return defaultTexture();
        }
        return new ResourceLocation("simpleenemymod",
                "textures/entity/" + textureFolder + "/" + texturePrefix + "_variant" + variant + ".png");
    }
}
