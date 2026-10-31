package com.neoalive.coltan.mixin.gemrender;

import java.util.List;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.wf.gemrender.light.Lights;

/** Read access to GemRender's private light registry (its getters are package-private). */
@Mixin(value = Lights.class, remap = false)
public interface LightsAccessor {
    @Accessor("PROVIDERS")
    static List<Lights.Provider> coltan$providers() {
        throw new AssertionError();
    }

    @Accessor("irView")
    static Supplier<Lights.IrView> coltan$irView() {
        throw new AssertionError();
    }
}
