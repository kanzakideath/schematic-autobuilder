package com.kanzakideath.schematicautobuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class AutoBuildController {

    private static final String MATERIAL_SHORTAGE = "\u8cc7\u6750\u304c\u8db3\u308a\u307e\u305b\u3093";
    private static final String MATERIAL_SHORTAGE_HINT = "\u767b\u9332\u30c1\u30a7\u30b9\u30c8\u306b\u8cc7\u6750\u3092\u8ffd\u52a0\u3057\u3066\u304b\u3089\u3001\u4e00\u6642\u505c\u6b62/\u518d\u958b\u307e\u305f\u306f\u958b\u59cb\u3092\u62bc\u3057\u3066\u304f\u3060\u3055\u3044\u3002";

    private enum Mode {
        IDLE,
        BUILDING,
        FETCHING,
        WAITING_FOR_MATERIALS,
        PAUSED,
        COMPLETE
    }

    private static Mode mode = Mode.IDLE;
    private static Mode pausedFrom = Mode.IDLE;
    private static int builderPausedTicks;
    private static int inactiveTicks;
    private static int refetchGuardTicks;
    private static boolean materialShortageNotified;
    private static String status = "Idle";

    private AutoBuildController() {}

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        MaterialChestProcess.tick(minecraft);

        if (refetchGuardTicks > 0) {
            refetchGuardTicks--;
        }
        if (mode == Mode.PAUSED || mode == Mode.IDLE || mode == Mode.COMPLETE || mode == Mode.FETCHING || mode == Mode.WAITING_FOR_MATERIALS) {
            return;
        }

        if (mode == Mode.BUILDING) {
            monitorBuilder();
        }
    }

    public static void startFullAutoBuild() {
        if (!BaritoneBridge.isAvailable()) {
            status = "Baritone not found";
            message(status, ChatFormatting.RED);
            return;
        }
        MaterialChestProcess.stop("Restarting full auto build");
        BaritoneBridge.cancelPathing();
        BaritoneBridge.resumeBuilder();
        if (!BaritoneBridge.startPlacedSchematicBuild()) {
            status = BaritoneBridge.openSchematicStatus();
            message(status, ChatFormatting.YELLOW);
            mode = Mode.IDLE;
            return;
        }
        builderPausedTicks = 0;
        inactiveTicks = 0;
        refetchGuardTicks = 80;
        materialShortageNotified = false;
        mode = Mode.BUILDING;
        status = "Building from placed schematic";
        message("Full auto build started", ChatFormatting.AQUA);
    }

    public static void fetchMaterialsOnly() {
        if (MaterialChestProcess.isRunning()) {
            MaterialChestProcess.stop("Material fetch cancelled");
            status = "Material fetch cancelled";
            return;
        }
        if (!MaterialChestProcess.start(AutoBuildController::onManualFetchFinished)) {
            status = MaterialChestProcess.status();
        } else {
            mode = Mode.FETCHING;
            status = "Fetching materials";
        }
    }

    public static void togglePause() {
        if (mode == Mode.PAUSED) {
            resume();
        } else {
            pause();
        }
    }

    public static boolean isPaused() {
        return mode == Mode.PAUSED;
    }

    public static boolean isRunning() {
        return mode == Mode.BUILDING || mode == Mode.FETCHING || mode == Mode.WAITING_FOR_MATERIALS || mode == Mode.PAUSED;
    }

    public static String status() {
        return status;
    }

    public static String modeName() {
        return mode.name();
    }

    private static void monitorBuilder() {
        if (BaritoneBridge.isBuilderPaused()) {
            builderPausedTicks++;
            inactiveTicks = 0;
            status = MATERIAL_SHORTAGE;
            if (builderPausedTicks >= 40 && AutoBuilderConfig.autoFetchMaterials() && AutoBuilderConfig.materialChestCount() > 0 && refetchGuardTicks == 0) {
                materialShortageNotified = false;
                startMaterialFetchForBuild(MATERIAL_SHORTAGE + ": checking registered material chests");
            } else if (builderPausedTicks >= 40 && !materialShortageNotified) {
                materialShortageNotified = true;
                message(MATERIAL_SHORTAGE + "\u3002" + MATERIAL_SHORTAGE_HINT, ChatFormatting.RED);
            }
            return;
        }

        builderPausedTicks = 0;
        materialShortageNotified = false;
        if (BaritoneBridge.isBuilderActive()) {
            inactiveTicks = 0;
            status = "Building";
            return;
        }

        inactiveTicks++;
        if (inactiveTicks > 80) {
            mode = Mode.COMPLETE;
            status = "Build complete or Baritone idle";
            message(status, ChatFormatting.GREEN);
        }
    }

    private static void startMaterialFetchForBuild(String reason) {
        BaritoneBridge.pauseBuilder();
        BaritoneBridge.cancelPathing();
        if (!MaterialChestProcess.start(AutoBuildController::onBuildFetchFinished)) {
            mode = Mode.WAITING_FOR_MATERIALS;
            status = MaterialChestProcess.status().isBlank() ? MATERIAL_SHORTAGE : MaterialChestProcess.status();
            message(status, ChatFormatting.RED);
            return;
        }
        mode = Mode.FETCHING;
        status = reason;
        message(reason, ChatFormatting.AQUA);
    }

    private static void onBuildFetchFinished(MaterialChestProcess.FetchResult result) {
        if (mode == Mode.PAUSED) {
            return;
        }
        if (!result.tookMaterials()) {
            mode = Mode.WAITING_FOR_MATERIALS;
            status = result.message().isBlank() ? MATERIAL_SHORTAGE : result.message();
            message(status + "\u3002" + MATERIAL_SHORTAGE_HINT, ChatFormatting.RED);
            return;
        }
        if (!AutoBuilderConfig.startBuildAfterFetch()) {
            mode = Mode.IDLE;
            status = "Fetched materials; auto resume is off";
            message(status, ChatFormatting.YELLOW);
            return;
        }
        refetchGuardTicks = 100;
        resumeBuildAfterFetch(result.stacksTaken());
    }

    private static void onManualFetchFinished(MaterialChestProcess.FetchResult result) {
        if (mode == Mode.PAUSED) {
            return;
        }
        mode = Mode.IDLE;
        status = result.message();
    }

    private static void resumeBuildAfterFetch(int stacksTaken) {
        BaritoneBridge.resumeBuilder();
        materialShortageNotified = false;
        if (!BaritoneBridge.startPlacedSchematicBuild()) {
            mode = Mode.WAITING_FOR_MATERIALS;
            status = "Fetched " + stacksTaken + " stack(s), but no placed schematic was found";
            message(status, ChatFormatting.YELLOW);
            return;
        }
        builderPausedTicks = 0;
        inactiveTicks = 0;
        mode = Mode.BUILDING;
        status = "Resumed build after fetching " + stacksTaken + " stack(s)";
        message(status, ChatFormatting.GREEN);
    }

    private static void pause() {
        if (mode == Mode.PAUSED) {
            return;
        }
        pausedFrom = mode;
        mode = Mode.PAUSED;
        status = "Paused";
        MaterialChestProcess.stop("Paused");
        closeContainerIfNeeded();
        BaritoneBridge.pauseBuilder();
        BaritoneBridge.cancelPathing();
        message("Auto builder paused", ChatFormatting.YELLOW);
    }

    private static void resume() {
        Mode resumeFrom = pausedFrom;
        pausedFrom = Mode.IDLE;
        if (resumeFrom == Mode.FETCHING) {
            if (MaterialChestProcess.start(AutoBuildController::onBuildFetchFinished)) {
                mode = Mode.FETCHING;
                status = "Resumed material fetch";
                message(status, ChatFormatting.GREEN);
                return;
            }
        }
        BaritoneBridge.resumeBuilder();
        if (resumeFrom == Mode.BUILDING || BaritoneBridge.isBuilderActive()) {
            if (!BaritoneBridge.isBuilderActive()) {
                if (!BaritoneBridge.startPlacedSchematicBuild()) {
                    mode = Mode.IDLE;
                    status = BaritoneBridge.openSchematicStatus();
                    message(status, ChatFormatting.YELLOW);
                    return;
                }
            }
            materialShortageNotified = false;
            mode = Mode.BUILDING;
            status = "Resumed build";
            message(status, ChatFormatting.GREEN);
            return;
        }
        mode = Mode.IDLE;
        status = "Idle";
        message("Auto builder resumed", ChatFormatting.GREEN);
    }

    private static void closeContainerIfNeeded() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null) {
            player.closeContainer();
        }
    }

    static void message(String text, ChatFormatting color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.gui != null) {
            minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("[AutoBuilder] " + text).withStyle(color));
        }
    }
}
