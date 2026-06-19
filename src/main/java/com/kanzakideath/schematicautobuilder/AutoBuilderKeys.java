package com.kanzakideath.schematicautobuilder;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

public final class AutoBuilderKeys {

    private static final int KEY_MIGRATION_UNBOUND_DEFAULTS = 1;

    private static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.schematicautobuilder.open_menu",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            KeyMapping.Category.MISC
    );

    private static final KeyMapping PAUSE_AUTOMATION = new KeyMapping(
            "key.schematicautobuilder.pause_automation",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
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
        migrateOldDefaultKeys(minecraft);
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

    private static void migrateOldDefaultKeys(Minecraft minecraft) {
        if (minecraft == null || AutoBuilderConfig.keyMigrationVersion() >= KEY_MIGRATION_UNBOUND_DEFAULTS) {
            return;
        }
        boolean changed = false;
        if (isBoundTo(OPEN_MENU, GLFW.GLFW_KEY_J)) {
            OPEN_MENU.setKey(InputConstants.UNKNOWN);
            changed = true;
        }
        if (isBoundTo(PAUSE_AUTOMATION, GLFW.GLFW_KEY_K)) {
            PAUSE_AUTOMATION.setKey(InputConstants.UNKNOWN);
            changed = true;
        }
        if (changed) {
            KeyMapping.resetMapping();
            minecraft.options.save();
        }
        AutoBuilderConfig.markKeyMigrationVersion(KEY_MIGRATION_UNBOUND_DEFAULTS);
    }

    private static boolean isBoundTo(KeyMapping mapping, int key) {
        return mapping.matches(InputConstants.Type.KEYSYM.getOrCreate(key));
    }

    private static void openMenu(Minecraft minecraft) {
        if (!(minecraft.gui.screen() instanceof AutoBuilderScreen)) {
            minecraft.setScreenAndShow(new AutoBuilderScreen());
        }
    }
}
