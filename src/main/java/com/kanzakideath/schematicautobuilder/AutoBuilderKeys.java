package com.kanzakideath.schematicautobuilder;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

public final class AutoBuilderKeys {

    private static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.schematicautobuilder.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            KeyMapping.Category.MISC
    );

    private static final KeyMapping PAUSE_AUTOMATION = new KeyMapping(
            "key.schematicautobuilder.pause_automation",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            KeyMapping.Category.MISC
    );

    private static final KeyMapping[] ALL = new KeyMapping[]{
            OPEN_MENU,
            PAUSE_AUTOMATION
    };

    private AutoBuilderKeys() {}

    public static KeyMapping[] all() {
        return ALL.clone();
    }

    public static void onClientTick(Minecraft minecraft) {
        while (OPEN_MENU.consumeClick()) {
            openMenu(minecraft);
        }
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        while (PAUSE_AUTOMATION.consumeClick()) {
            AutoBuildController.togglePause();
        }
        AutoBuildController.tick(minecraft);
    }

    public static boolean handleGlobalKey(Minecraft minecraft, int action, KeyEvent event) {
        if (action != GLFW.GLFW_PRESS || minecraft == null || event == null) {
            return false;
        }
        if (OPEN_MENU.matches(event)) {
            openMenu(minecraft);
            return true;
        }
        if (minecraft.player != null && minecraft.level != null && PAUSE_AUTOMATION.matches(event)) {
            AutoBuildController.togglePause();
            return true;
        }
        return false;
    }

    private static void openMenu(Minecraft minecraft) {
        if (!(minecraft.gui.screen() instanceof AutoBuilderScreen)) {
            minecraft.setScreenAndShow(new AutoBuilderScreen());
        }
    }
}
