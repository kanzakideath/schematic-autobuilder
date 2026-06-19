package com.kanzakideath.schematicautobuilder;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class AutoBuilderKeys {

    private static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.schematicautobuilder.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            KeyMapping.Category.MISC
    );

    private static final KeyMapping[] ALL = new KeyMapping[]{
            OPEN_MENU
    };

    private AutoBuilderKeys() {}

    public static KeyMapping[] all() {
        return ALL.clone();
    }

    public static void onClientTick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        while (OPEN_MENU.consumeClick()) {
            if (minecraft.gui.screen() == null) {
                minecraft.setScreenAndShow(new AutoBuilderScreen());
            }
        }
        MaterialChestProcess.tick(minecraft);
    }
}

