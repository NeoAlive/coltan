package com.neoalive.coltan.client.compat;

import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Which Corpse-mod corpses Coltan may draw, and with what skin.
 *
 * <p>The default answers null for everything, which leaves every corpse on CorpseMod's own renderer.
 * tacz_sewv registers a resolver that returns the unit skin of an SEM unit corpse and null for a
 * player corpse, so only SEM corpses ever take the instanced path.
 */
public final class ColtanCorpseSkins {

    private static volatile Function<Entity, ResourceLocation> resolver = corpse -> null;

    private ColtanCorpseSkins() {
    }

    /** Replace the resolver; {@code null} restores "draw nothing". The resolver returns null for "not ours". */
    public static void setResolver(@Nullable Function<Entity, ResourceLocation> next) {
        resolver = next != null ? next : corpse -> null;
    }

    @Nullable
    public static ResourceLocation skinOf(Entity corpse) {
        return resolver.apply(corpse);
    }
}
