package com.neoalive.coltan.mixin;

import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.neoalive.coltan.client.compat.sbw.GunClipSelect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientEventHandler.class, remap = false)
public class SbwClientEventHandlerMixin {
    @Inject(method = "onOpenEditScreen", at = @At("TAIL"))
    private static void coltan$editOpened(CallbackInfo ci) {
        GunClipSelect.onEditOpened();
    }

    @Inject(method = "onCloseEditScreen", at = @At("TAIL"))
    private static void coltan$editClosed(CallbackInfo ci) {
        GunClipSelect.onEditClosed();
    }
}
