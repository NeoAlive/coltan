package com.neoalive.coltan.client.compat.bridge;

import net.minecraft.resources.ResourceLocation;

/** One vehicle LOD tier from Models[] and/or {@code vehicle_lod/*.lodN}; distance 0 is the full model. */
public record LodEntry(int distance, ResourceLocation geo, ResourceLocation texture) {
}
