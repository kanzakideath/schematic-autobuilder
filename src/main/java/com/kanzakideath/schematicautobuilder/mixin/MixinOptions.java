package com.kanzakideath.schematicautobuilder.mixin;

import com.kanzakideath.schematicautobuilder.AutoBuilderKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.Arrays;

@Mixin(Options.class)
public class MixinOptions {

    @Mutable
    @Final
    @Shadow
    public KeyMapping[] keyMappings;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Options;load()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void addSchematicAutoBuilderKeyMappings(Minecraft minecraft, File file, CallbackInfo ci) {
        KeyMapping[] keys = AutoBuilderKeys.all();
        int originalLength = this.keyMappings.length;
        this.keyMappings = Arrays.copyOf(this.keyMappings, originalLength + keys.length);
        System.arraycopy(keys, 0, this.keyMappings, originalLength, keys.length);
    }
}

