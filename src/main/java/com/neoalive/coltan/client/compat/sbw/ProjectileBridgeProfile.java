package com.neoalive.coltan.client.compat.sbw;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public record ProjectileBridgeProfile(
        ResourceLocation entityId,
        EntityType<?> entityType,
        ResourceLocation geo,
        ResourceLocation texture,
        @Nullable ResourceLocation animation,
        boolean loopAnim,
        boolean hasFlare) {
    public ResourceLocation bridgeModelId() {
        return new ResourceLocation(
                "coltan", "projectile/" + entityId.getNamespace() + "/" + entityId.getPath());
    }
}
