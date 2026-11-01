package com.neoalive.coltan.client.compat.sem;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public record SemUnitRigidProfile(
        ResourceLocation entityId,
        EntityType<?> entityType,
        String textureFolder,
        String texturePrefix) {
    public ResourceLocation defaultTexture() {
        return new ResourceLocation(
                "simpleenemymod",
                "textures/entity/" + textureFolder + "/" + texturePrefix + "_default.png");
    }

    public ResourceLocation textureForVariant(int variant) {
        if (variant <= 0) {
            return defaultTexture();
        }
        return new ResourceLocation(
                "simpleenemymod",
                "textures/entity/"
                        + textureFolder
                        + "/"
                        + texturePrefix
                        + "_variant"
                        + variant
                        + ".png");
    }
}
