package com.neoalive.coltan.client.compat.sbw;

import net.minecraftforge.fml.ModList;

/**
 * GemRender + Superb Warfare presence, resolved once. Mod presence cannot change after load, and
 * {@code active()} sits on per-particle / per-item-render paths.
 */
public final class SbwGemPresence {
    /** Initialized on first access (class init), not at Coltan load. */
    public static final boolean ACTIVE =
            ModList.get().isLoaded("gemrender") && ModList.get().isLoaded("superbwarfare");

    /** VBO path for hulls GemRender will not draw. Presence cannot change after load. */
    public static final boolean SBW_OPTIMIZED = ModList.get().isLoaded("sbwoptimized");

    private SbwGemPresence() {}
}
