package com.neoalive.coltan.client.compat.bridge;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** Catalog entry for an SBW Bedrock projectile drawn through GemRender. */
public record ProjectileBridgeProfile(
        ResourceLocation entityId,
        EntityType<?> entityType,
        ResourceLocation geo,
        ResourceLocation texture,
        @Nullable ResourceLocation animation,
        boolean loopAnim,
        boolean hasFlare
) {
    public ResourceLocation bridgeModelId() {
        return new ResourceLocation("coltan",
                "projectile/" + entityId.getNamespace() + "/" + entityId.getPath());
    }
}
