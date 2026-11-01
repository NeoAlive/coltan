package com.neoalive.coltan.client.compat;

import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Null leaves the corpse on CorpseMod's renderer. Only a resolver that returns a skin takes the
 * instanced path.
 */
public final class ColtanCorpseSkins {

    private static volatile Function<Entity, ResourceLocation> resolver = corpse -> null;

    private ColtanCorpseSkins() {}

    /**
     * Replace the resolver; {@code null} restores "draw nothing". The resolver returns null for
     * "not ours".
     */
    public static void setResolver(@Nullable Function<Entity, ResourceLocation> next) {
        resolver = next != null ? next : corpse -> null;
    }

    @Nullable
    public static ResourceLocation skinOf(Entity corpse) {
        return resolver.apply(corpse);
    }
}
