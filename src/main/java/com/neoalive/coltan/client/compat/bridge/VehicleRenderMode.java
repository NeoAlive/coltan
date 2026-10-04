package com.neoalive.coltan.client.compat.bridge;

import java.util.Locale;

import javax.annotation.Nullable;

/**
 * How a bridged vehicle type is drawn. Chosen per type by {@link VehicleRenderModes} from its SBW
 * renderer, or forced with {@code "renderMode"} in {@code assets/coltan/sbw_bridge/<ns>/<id>.json}.
 */
public enum VehicleRenderMode {
    /** Stock SBW renderer: Coltan's own procedural layers rebuild the pose (cheapest). */
    NATIVE,
    /**
     * Addon renderer with custom pose hooks (or an SBW vehicle script): SBW's own pose pipeline runs on
     * the entity's model instance each frame and every bone is copied into the GemRender pose.
     */
    REPLAY,
    /** Not representable on GemRender (custom draw path): the vehicle's own renderer draws it. */
    PASSTHROUGH;

    @Nullable
    public static VehicleRenderMode parse(@Nullable String value) {
        if (value == null || value.isBlank() || "auto".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
