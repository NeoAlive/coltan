package com.neoalive.coltan.client.compat;

import net.minecraftforge.fml.ModList;

/**
 * GemRender + Superb Warfare presence, resolved once. Mod presence cannot change after load, and
 * {@code active()} sits on per-particle / per-item-render paths.
 */
public final class SbwGemPresence {
    /** Initialized on first access (class init), not at Coltan load. */
    public static final boolean ACTIVE =
            ModList.get().isLoaded("gemrender") && ModList.get().isLoaded("superbwarfare");

    private SbwGemPresence() {
    }
}
