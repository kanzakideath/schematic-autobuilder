package com.kanzakideath.schematicautobuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AutoBuildController {

    private static final String MATERIAL_SHORTAGE = "\u8cc7\u6750\u304c\u8db3\u308a\u307e\u305b\u3093";
    private static final String MATERIAL_SHORTAGE_HINT = "\u767b\u9332\u30c1\u30a7\u30b9\u30c8\u306b\u8cc7\u6750\u3092\u8ffd\u52a0\u3057\u3066\u304b\u3089\u3001\u4e00\u6642\u505c\u6b62/\u518d\u958b\u307e\u305f\u306f\u958b\u59cb\u3092\u62bc\u3057\u3066\u304f\u3060\u3055\u3044\u3002";
    private static final int INITIAL_REFETCH_GUARD_TICKS = 40;
    private static final int RESUME_REFETCH_GUARD_TICKS = 40;
    private static final int CREATIVE_REFETCH_GUARD_TICKS = 10;
    private static final int WAITING_RETRY_TICKS = 60;

    private enum Mode {
        IDLE,
        BUILDING,
        FETCHING,
        CRAFTING,
        SMELTING,
        WAITING_FOR_MATERIALS,
        PAUSED,
        COMPLETE,
        CANCELLED,
        ERROR
    }

    private enum WorkMode {
        NONE,
        AUTO_BUILD,
        CLEAR_AREA
    }

    private static Mode mode = Mode.IDLE;
    private static Mode pausedFrom = Mode.IDLE;
    private static WorkMode lastWorkMode = WorkMode.NONE;
    private static int builderPausedTicks;
    private static int inactiveTicks;
    private static int refetchGuardTicks;
    private static int creativeSupplyTicks;
    private static boolean materialShortageNotified;
    private static String status = "Idle";
    private static Set<Item> lastNeededItems = Set.of();
    private static AutoBuilderStatusSnapshot cachedSnapshot = AutoBuilderStatusSnapshot.empty();
    private static long cachedSnapshotAtMs;

    private AutoBuildController() {}

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        MaterialChestProcess.tick(minecraft);
        MaterialCraftProcess.tick(minecraft);
        MaterialSmeltProcess.tick(minecraft);
        noteExternalWorkMode();

        if (refetchGuardTicks > 0) {
            refetchGuardTicks--;
        }
        if (creativeSupplyTicks > 0) {
            creativeSupplyTicks--;
        }
        if (mode == Mode.WAITING_FOR_MATERIALS) {
            retryWaitingForMaterials();
            return;
        }
        if (mode == Mode.PAUSED || mode == Mode.IDLE || mode == Mode.COMPLETE || mode == Mode.FETCHING || mode == Mode.CRAFTING || mode == Mode.SMELTING) {
            return;
        }

        if (mode == Mode.BUILDING) {
            monitorBuilder();
        }
    }

    public static void startFullAutoBuild() {
        if (!BaritoneBridge.isAvailable()) {
            status = "Baritone not found";
            mode = Mode.ERROR;
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
        refetchGuardTicks = isCreativeMode() ? CREATIVE_REFETCH_GUARD_TICKS : INITIAL_REFETCH_GUARD_TICKS;
        creativeSupplyTicks = 0;
        materialShortageNotified = false;
        mode = Mode.BUILDING;
        lastWorkMode = WorkMode.AUTO_BUILD;
        status = "Building from placed schematic";
        rememberNeededItems();
        int supplied = CreativeMaterialSupplier.supplyNeeded(Minecraft.getInstance(), lastNeededItems);
        if (supplied > 0) {
            message("\u30af\u30ea\u30a8\u30a4\u30c6\u30a3\u30d6\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u304b\u3089\u4e0d\u8db3\u5019\u88dc\u3092\u88dc\u5145\u3057\u307e\u3057\u305f: " + supplied, ChatFormatting.GREEN);
        }
        message("Full auto build started", ChatFormatting.AQUA);
    }

    public static void startOrResumeAutoBuild() {
        if (mode == Mode.PAUSED) {
            resume();
            return;
        }
        startFullAutoBuild();
    }

    public static void cancelAutomation() {
        MaterialChestProcess.stop("Cancelled");
        MaterialCraftProcess.stop("Cancelled");
        MaterialSmeltProcess.stop("Cancelled");
        closeContainerIfNeeded();
        BaritoneBridge.pauseBuilder();
        BaritoneBridge.cancelPathing();
        mode = Mode.CANCELLED;
        pausedFrom = Mode.IDLE;
        lastNeededItems = Set.of();
        status = "Cancelled";
        cachedSnapshotAtMs = 0L;
        message("Auto builder stopped", ChatFormatting.YELLOW);
    }

    public static void toggleMaterialChestRegistration(Minecraft minecraft) {
        if (MaterialChestProcess.isRegisteringChests()) {
            MaterialChestProcess.stopChestRegistration();
            return;
        }
        MaterialChestProcess.startChestRegistration(minecraft == null ? Minecraft.getInstance() : minecraft);
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
            lastWorkMode = WorkMode.AUTO_BUILD;
            status = "Fetching materials";
        }
    }

    public static void togglePause() {
        toggleAutoBuildPause();
    }

    public static boolean toggleSharedPauseKey() {
        noteExternalWorkMode();
        if (mode == Mode.PAUSED || isAutoBuildAutomationMode()) {
            lastWorkMode = WorkMode.AUTO_BUILD;
            return toggleAutoBuildPause();
        }
        if (BaritoneBridge.isClearingAreaActive() || lastWorkMode == WorkMode.CLEAR_AREA) {
            boolean toggled = BaritoneBridge.toggleClearAreaPause();
            if (toggled) {
                lastWorkMode = WorkMode.CLEAR_AREA;
            }
            return toggled;
        }
        return false;
    }

    private static boolean toggleAutoBuildPause() {
        if (mode == Mode.PAUSED) {
            resume();
            return true;
        }
        if (isAutoBuildAutomationMode()) {
            pause();
            return true;
        }
        return false;
    }

    private static boolean isAutoBuildAutomationMode() {
        return switch (mode) {
            case BUILDING, FETCHING, CRAFTING, SMELTING, WAITING_FOR_MATERIALS -> true;
            default -> false;
        };
    }

    public static void noteExternalWorkMode() {
        if (isAutoBuildAutomationMode() || mode == Mode.PAUSED) {
            lastWorkMode = WorkMode.AUTO_BUILD;
            return;
        }
        if (BaritoneBridge.isClearingAreaActive()) {
            lastWorkMode = WorkMode.CLEAR_AREA;
        }
    }

    public static boolean isPaused() {
        return mode == Mode.PAUSED;
    }

    public static boolean isRunning() {
        return mode == Mode.BUILDING || mode == Mode.FETCHING || mode == Mode.CRAFTING || mode == Mode.SMELTING || mode == Mode.WAITING_FOR_MATERIALS || mode == Mode.PAUSED;
    }

    public static String status() {
        return status;
    }

    public static String modeName() {
        return mode.name();
    }

    public static AutoBuilderStatusSnapshot statusSnapshot() {
        long now = System.currentTimeMillis();
        if (now - cachedSnapshotAtMs > 250L) {
            cachedSnapshot = createStatusSnapshot(now);
            cachedSnapshotAtMs = now;
        }
        return cachedSnapshot;
    }

    public static List<String> neededMaterialSummaries(int limit) {
        return lastNeededItems.stream()
                .sorted(Comparator.comparing(AutoBuildController::itemId))
                .limit(Math.max(0, limit))
                .map(AutoBuildController::itemId)
                .toList();
    }

    private static void monitorBuilder() {
        if (BaritoneBridge.isBuilderPaused()) {
            builderPausedTicks++;
            inactiveTicks = 0;
            status = MATERIAL_SHORTAGE;
            rememberNeededItems();
            if (builderPausedTicks >= 1 && (refetchGuardTicks == 0 || isCreativeMode()) && supplyCreativeAndResume()) {
                return;
            }
            if (builderPausedTicks >= 20 && isCreativeMode() && refetchGuardTicks == 0 && AutoBuilderConfig.startBuildAfterFetch()) {
                refetchGuardTicks = CREATIVE_REFETCH_GUARD_TICKS;
                resumeBuildAfterMaterialChange("\u30af\u30ea\u30a8\u30a4\u30c6\u30a3\u30d6\u3067\u5efa\u7bc9\u3092\u518d\u8a66\u884c\u3057\u307e\u3059");
                return;
            }
            if (builderPausedTicks >= 20 && refetchGuardTicks == 0 && tryMaterialCreationForBuild("\u624b\u6301\u3061\u7d20\u6750\u304b\u3089\u4e0d\u8db3\u5206\u3092\u4f5c\u6210\u4e2d")) {
                return;
            }
            if (builderPausedTicks >= 40 && AutoBuilderConfig.autoFetchMaterials() && MaterialChestProcess.hasMaterialSources() && refetchGuardTicks == 0) {
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
        supplyCreativeWhileBuilding();
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
            refetchGuardTicks = WAITING_RETRY_TICKS;
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
            if (tryMaterialCreationForBuild("\u624b\u6301\u3061\u7d20\u6750\u304b\u3089\u4f5c\u308c\u308b\u5206\u3092\u4f5c\u6210\u4e2d")) {
                return;
            }
            mode = Mode.WAITING_FOR_MATERIALS;
            refetchGuardTicks = WAITING_RETRY_TICKS;
            status = result.message().isBlank() ? MATERIAL_SHORTAGE : result.message();
            message(status + "\u3002" + MATERIAL_SHORTAGE_HINT, ChatFormatting.RED);
            return;
        }
        if (result.inventoryFull()) {
            refetchGuardTicks = RESUME_REFETCH_GUARD_TICKS;
            resumeBuildAfterMaterialChange("\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u304c\u3044\u3063\u3071\u3044\u306e\u305f\u3081\u3001\u53d6\u5f97\u6e08\u307f\u306e\u7d20\u6750\u3067\u5efa\u7bc9\u306b\u623b\u308a\u307e\u3059");
            return;
        }
        if (tryMaterialCreationForBuild("\u88dc\u5145\u3057\u305f\u7d20\u6750\u304b\u3089\u4f5c\u308c\u308b\u5206\u3092\u4f5c\u6210\u4e2d")) {
            return;
        }
        if (!AutoBuilderConfig.startBuildAfterFetch()) {
            mode = Mode.IDLE;
            status = "Fetched materials; auto resume is off";
            message(status, ChatFormatting.YELLOW);
            return;
        }
        refetchGuardTicks = RESUME_REFETCH_GUARD_TICKS;
        resumeBuildAfterFetch(result.stacksTaken());
    }

    private static void onManualFetchFinished(MaterialChestProcess.FetchResult result) {
        if (mode == Mode.PAUSED) {
            return;
        }
        mode = Mode.IDLE;
        status = result.message();
    }

    private static void onBuildCraftFinished(MaterialCraftProcess.CraftResult result) {
        if (mode == Mode.PAUSED) {
            return;
        }
        if (!AutoBuilderConfig.startBuildAfterFetch()) {
            mode = Mode.IDLE;
            status = result.message();
            message(status, result.crafted() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
            return;
        }
        refetchGuardTicks = RESUME_REFETCH_GUARD_TICKS;
        resumeBuildAfterFetch(result.craftedCount());
    }

    private static void onBuildSmeltFinished(MaterialSmeltProcess.SmeltResult result) {
        if (mode == Mode.PAUSED) {
            return;
        }
        if (AutoBuilderConfig.autoCraftMaterials() && MaterialCraftProcess.start(AutoBuildController::onBuildCraftFinished, lastNeededItems)) {
            mode = Mode.CRAFTING;
            status = "Crafting smelted materials";
            message(status, ChatFormatting.AQUA);
            return;
        }
        if (!AutoBuilderConfig.startBuildAfterFetch()) {
            mode = Mode.IDLE;
            status = result.message();
            message(status, result.smelted() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
            return;
        }
        refetchGuardTicks = RESUME_REFETCH_GUARD_TICKS;
        resumeBuildAfterFetch(result.outputTaken());
    }

    private static void resumeBuildAfterFetch(int stacksTaken) {
        resumeBuildAfterMaterialChange("Resumed build after fetching " + stacksTaken + " stack(s)");
    }

    private static void resumeBuildAfterMaterialChange(String resumeStatus) {
        BaritoneBridge.cancelPathing();
        BaritoneBridge.resumeBuilder();
        materialShortageNotified = false;
        if (!BaritoneBridge.startPlacedSchematicBuild()) {
            mode = Mode.WAITING_FOR_MATERIALS;
            status = "\u8a2d\u8a08\u56f3\u304c\u898b\u3064\u304b\u3089\u306a\u3044\u305f\u3081\u518d\u958b\u3067\u304d\u307e\u305b\u3093";
            message(status, ChatFormatting.YELLOW);
            return;
        }
        builderPausedTicks = 0;
        inactiveTicks = 0;
        mode = Mode.BUILDING;
        lastWorkMode = WorkMode.AUTO_BUILD;
        status = resumeStatus;
        message(status, ChatFormatting.GREEN);
    }

    private static void retryWaitingForMaterials() {
        if (refetchGuardTicks > 0) {
            return;
        }
        rememberNeededItems();
        if (supplyCreativeAndResume()) {
            return;
        }
        if (isCreativeMode() && AutoBuilderConfig.startBuildAfterFetch()) {
            refetchGuardTicks = CREATIVE_REFETCH_GUARD_TICKS;
            resumeBuildAfterMaterialChange("\u30af\u30ea\u30a8\u30a4\u30c6\u30a3\u30d6\u306e\u305f\u3081\u5efa\u7bc9\u3092\u518d\u8a66\u884c\u3057\u307e\u3059");
            return;
        }
        if (tryMaterialCreationForBuild("\u624b\u6301\u3061\u7d20\u6750\u304b\u3089\u4e0d\u8db3\u5206\u3092\u4f5c\u6210\u4e2d")) {
            return;
        }
        if (AutoBuilderConfig.autoFetchMaterials() && MaterialChestProcess.hasMaterialSources()) {
            startMaterialFetchForBuild(MATERIAL_SHORTAGE + ": checking registered material chests");
            return;
        }
        if (!materialShortageNotified) {
            materialShortageNotified = true;
            message(MATERIAL_SHORTAGE + "\u3002" + MATERIAL_SHORTAGE_HINT, ChatFormatting.RED);
        }
        refetchGuardTicks = WAITING_RETRY_TICKS;
    }

    private static Set<Item> rememberNeededItems() {
        Set<Item> current = BaritoneBridge.currentNeededBuildItems();
        if (!current.isEmpty()) {
            lastNeededItems = Set.copyOf(current);
        }
        BaritoneBridge.configureTemporaryScaffoldItems(lastNeededItems);
        return lastNeededItems;
    }

    private static boolean supplyCreativeAndResume() {
        Set<Item> needed = rememberNeededItems();
        int supplied = CreativeMaterialSupplier.supplyNeeded(Minecraft.getInstance(), needed);
        if (supplied <= 0) {
            return false;
        }
        refetchGuardTicks = CREATIVE_REFETCH_GUARD_TICKS;
        resumeBuildAfterMaterialChange("\u30af\u30ea\u30a8\u30a4\u30c6\u30a3\u30d6\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u304b\u3089\u4e0d\u8db3\u7d20\u6750\u3092\u88dc\u5145\u3057\u3066\u518d\u958b\u3057\u307e\u3057\u305f");
        return true;
    }

    private static void supplyCreativeWhileBuilding() {
        if (!isCreativeMode() || creativeSupplyTicks > 0) {
            return;
        }
        creativeSupplyTicks = 20;
        Set<Item> needed = rememberNeededItems();
        CreativeMaterialSupplier.supplyNeeded(Minecraft.getInstance(), needed);
    }

    private static boolean isCreativeMode() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.isCreative();
    }

    private static boolean tryMaterialCreationForBuild(String progressStatus) {
        if (!AutoBuilderConfig.autoCraftMaterials()) {
            return false;
        }
        if (inventoryAlmostFull()) {
            return false;
        }
        Set<Item> needed = rememberNeededItems();
        if (needed.isEmpty()) {
            return false;
        }
        if (MaterialCraftProcess.start(AutoBuildController::onBuildCraftFinished, needed)) {
            mode = Mode.CRAFTING;
            status = progressStatus;
            message(status, ChatFormatting.AQUA);
            return true;
        }
        if (MaterialSmeltProcess.start(AutoBuildController::onBuildSmeltFinished, needed)) {
            mode = Mode.SMELTING;
            status = progressStatus;
            message(status, ChatFormatting.AQUA);
            return true;
        }
        return false;
    }

    private static boolean inventoryAlmostFull() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        int empty = 0;
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            if (player.getInventory().getNonEquipmentItems().get(i).isEmpty()) {
                empty++;
            }
        }
        return empty <= 2;
    }

    private static void pause() {
        if (mode == Mode.PAUSED) {
            return;
        }
        pausedFrom = mode;
        lastWorkMode = WorkMode.AUTO_BUILD;
        mode = Mode.PAUSED;
        status = "Paused";
        MaterialChestProcess.stop("Paused");
        MaterialCraftProcess.stop("Paused");
        MaterialSmeltProcess.stop("Paused");
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
            lastWorkMode = WorkMode.AUTO_BUILD;
            status = "Resumed build";
            message(status, ChatFormatting.GREEN);
            return;
        }
        mode = Mode.IDLE;
        status = "Idle";
        lastNeededItems = Set.of();
        message("Auto builder resumed", ChatFormatting.GREEN);
    }

    private static AutoBuilderStatusSnapshot createStatusSnapshot(long now) {
        BaritoneBridge.BuildStats stats = BaritoneBridge.buildStats();
        int missingTypes = missingMaterialTypes();
        int missingItems = missingTypes <= 0 ? 0 : -1;
        String modeLabel = hudModeLabel();
        String stateLabel = hudStateLabel();
        String actionLabel = hudActionLabel(stats);
        String target = MaterialChestProcess.isRunning() && !MaterialChestProcess.currentTargetSummary().isBlank()
                ? "chest @ " + MaterialChestProcess.currentTargetSummary()
                : stats.target();
        return new AutoBuilderStatusSnapshot(
                modeLabel,
                stateLabel,
                actionLabel,
                stats.progress(),
                stats.totalBlocks(),
                stats.doneBlocks(),
                stats.remainingBlocks(),
                stats.failedBlocks(),
                stats.unreachableBlocks(),
                missingTypes,
                missingItems,
                AutoBuilderConfig.materialChestCount(),
                BaritoneBridge.hudStatus(),
                target == null ? "" : target,
                "--:--",
                now
        );
    }

    private static String hudModeLabel() {
        if (MaterialChestProcess.isRegisteringChests()) {
            return "MATERIAL CHEST REGISTRATION";
        }
        if (mode == Mode.BUILDING || mode == Mode.FETCHING || mode == Mode.CRAFTING || mode == Mode.SMELTING || mode == Mode.WAITING_FOR_MATERIALS || mode == Mode.PAUSED || mode == Mode.COMPLETE) {
            return "AUTO BUILD";
        }
        if (BaritoneBridge.isClearingAreaActive() || lastWorkMode == WorkMode.CLEAR_AREA) {
            return "TERRAIN CLEAR";
        }
        return "IDLE";
    }

    private static String hudStateLabel() {
        if (MaterialChestProcess.isRegisteringChests()) {
            return "素材チェスト登録中";
        }
        return switch (mode) {
            case BUILDING -> "実行中";
            case FETCHING -> "チェスト補充中";
            case CRAFTING -> "素材作成中";
            case SMELTING -> "精錬中";
            case WAITING_FOR_MATERIALS -> "素材不足";
            case PAUSED -> "一時停止";
            case COMPLETE -> "完了";
            case CANCELLED -> "停止";
            case ERROR -> "エラー";
            default -> BaritoneBridge.isClearingAreaActive() ? "整地中" : "待機中";
        };
    }

    private static String hudActionLabel(BaritoneBridge.BuildStats stats) {
        if (MaterialChestProcess.isRegisteringChests()) {
            return "素材チェストを右クリックで登録";
        }
        if (MaterialChestProcess.isRunning()) {
            return MaterialChestProcess.status();
        }
        if (MaterialCraftProcess.isRunning()) {
            return MaterialCraftProcess.status();
        }
        if (MaterialSmeltProcess.isRunning()) {
            return MaterialSmeltProcess.status();
        }
        return switch (mode) {
            case BUILDING -> stats.target().isBlank() ? "Baritone 移動要求中" : "ブロック設置中";
            case WAITING_FOR_MATERIALS -> "素材探索中";
            case PAUSED -> "一時停止中";
            case COMPLETE -> "完了";
            case CANCELLED -> "停止";
            case ERROR -> "エラー";
            default -> status;
        };
    }

    private static int missingMaterialTypes() {
        String rawStatus = status == null ? "" : status;
        String lowerStatus = rawStatus.toLowerCase(Locale.ROOT);
        if (mode != Mode.WAITING_FOR_MATERIALS && !MATERIAL_SHORTAGE.equals(rawStatus) && !lowerStatus.contains("material") && !rawStatus.contains("素材")) {
            return 0;
        }
        Set<Item> needed = rememberNeededItems();
        if (needed.isEmpty()) {
            return 0;
        }
        return needed.size();
    }

    private static String itemId(Item item) {
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? item.toString() : key.toString();
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
