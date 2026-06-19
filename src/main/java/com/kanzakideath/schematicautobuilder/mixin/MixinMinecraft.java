package com.kanzakideath.schematicautobuilder.mixin;

import com.kanzakideath.schematicautobuilder.AutoBuilderKeys;
import com.kanzakideath.schematicautobuilder.MaterialChestProcess;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "tick", at = @At("HEAD"))
    private void schematicAutoBuilderTick(CallbackInfo ci) {
        AutoBuilderKeys.onClientTick((Minecraft) (Object) this);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void schematicAutoBuilderRegisterMaterialChest(CallbackInfo ci) {
        if (MaterialChestProcess.handleChestRegistrationClick((Minecraft) (Object) this)) {
            ci.cancel();
        }
    }
}
