package com.kanzakideath.schematicautobuilder.mixin;

import com.kanzakideath.schematicautobuilder.AutoBuilderHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class MixinHud {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void schematicAutoBuilderStatusHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        AutoBuilderHudRenderer.render(graphics);
    }
}
