package com.neoalive.coltan.client.compat.bridge;

import net.minecraft.resources.ResourceLocation;

/** One Models[] LOD tier; distance 0 is the full model. */
public record LodEntry(int distance, ResourceLocation geo, ResourceLocation texture) {
}
