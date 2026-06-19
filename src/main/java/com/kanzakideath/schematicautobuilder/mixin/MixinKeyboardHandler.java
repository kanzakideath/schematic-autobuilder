package com.kanzakideath.schematicautobuilder.mixin;

import com.kanzakideath.schematicautobuilder.AutoBuilderKeys;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void schematicAutoBuilderGlobalKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (AutoBuilderKeys.handleGlobalKey(Minecraft.getInstance(), action, event)) {
            ci.cancel();
        }
    }
}
