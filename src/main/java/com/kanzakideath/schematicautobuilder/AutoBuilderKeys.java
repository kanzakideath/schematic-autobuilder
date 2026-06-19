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

    private static final KeyMapping START_OR_RESUME = new KeyMapping(
            "key.schematicautobuilder.start_or_resume",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            KeyMapping.Category.MISC
    );

    private static final KeyMapping STOP_AUTOMATION = new KeyMapping(
            "key.schematicautobuilder.stop_automation",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            KeyMapping.Category.MISC
    );

    private static final KeyMapping TOGGLE_HUD_DETAIL = new KeyMapping(
            "key.schematicautobuilder.toggle_hud_detail",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            KeyMapping.Category.MISC
    );

    private static final KeyMapping TOGGLE_MATERIAL_CHEST_REGISTRATION = new KeyMapping(
            "key.schematicautobuilder.toggle_material_chest_registration",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            KeyMapping.Category.MISC
    );

    private static final KeyMapping[] ALL = new KeyMapping[]{
            OPEN_MENU,
            PAUSE_AUTOMATION,
            START_OR_RESUME,
            STOP_AUTOMATION,
            TOGGLE_HUD_DETAIL,
            TOGGLE_MATERIAL_CHEST_REGISTRATION
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
            AutoBuildController.toggleSharedPauseKey();
        }
        while (START_OR_RESUME.consumeClick()) {
            AutoBuildController.startOrResumeAutoBuild();
        }
        while (STOP_AUTOMATION.consumeClick()) {
            AutoBuildController.cancelAutomation();
        }
        while (TOGGLE_HUD_DETAIL.consumeClick()) {
            AutoBuilderConfig.toggleHudDetailed();
        }
        while (TOGGLE_MATERIAL_CHEST_REGISTRATION.consumeClick()) {
            AutoBuildController.toggleMaterialChestRegistration(minecraft);
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
        if (minecraft.player != null && minecraft.level != null) {
            if (BaritoneBridge.matchesClearAreaPauseKey(event)) {
                return AutoBuildController.toggleSharedPauseKey();
            }
            if (PAUSE_AUTOMATION.matches(event)) {
                return AutoBuildController.toggleSharedPauseKey();
            }
            if (START_OR_RESUME.matches(event)) {
                AutoBuildController.startOrResumeAutoBuild();
                return true;
            }
            if (STOP_AUTOMATION.matches(event)) {
                AutoBuildController.cancelAutomation();
                return true;
            }
            if (TOGGLE_HUD_DETAIL.matches(event)) {
                AutoBuilderConfig.toggleHudDetailed();
                return true;
            }
            if (TOGGLE_MATERIAL_CHEST_REGISTRATION.matches(event)) {
                AutoBuildController.toggleMaterialChestRegistration(minecraft);
                return true;
            }
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
