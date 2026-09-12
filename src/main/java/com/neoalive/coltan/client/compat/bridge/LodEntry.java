package com.neoalive.coltan.client.compat.bridge;

import net.minecraft.resources.ResourceLocation;

/** One LOD tier from SBW {@code Models[]} ({@code distance == 0} is the full-detail model). */
public record LodEntry(int distance, ResourceLocation geo, ResourceLocation texture) {
}
