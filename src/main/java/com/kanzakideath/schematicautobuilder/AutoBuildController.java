package com.kanzakideath.schematicautobuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class AutoBuildController {

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
            status = "Builder paused by Baritone";
            if (builderPausedTicks >= 40 && AutoBuilderConfig.autoFetchMaterials() && AutoBuilderConfig.materialChestCount() > 0 && refetchGuardTicks == 0) {
                startMaterialFetchForBuild("Builder paused; trying registered material chests");
            }
            return;
        }

        builderPausedTicks = 0;
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
            status = MaterialChestProcess.status();
            message(status, ChatFormatting.YELLOW);
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
            status = "No usable build materials found in registered chests";
            message(status + ". Add materials, then press pause/resume or start again.", ChatFormatting.YELLOW);
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
