package com.neoalive.coltan.client.compat;

/**
 * Set once Superb Warfare is present and its core compat booted (no GemRender needed). Mixins on
 * classes that can exist without SBW, such as Simple Bedrock Model's armor renderer, check it
 * before touching anything that references SBW.
 */
public final class SbwCore {
    public static volatile boolean active;

    private SbwCore() {}
}
