package com.kanzakideath.schematicautobuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
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
    private static final String MATERIAL_SHORTAGE_HINT = "\u81ea\u52d5\u8a3a\u65ad\u304c\u7d20\u6750\u30c1\u30a7\u30b9\u30c8\u3001\u30af\u30e9\u30d5\u30c8\u3001\u7cbe\u932c\u3001Baritone\u518d\u521d\u671f\u5316\u3092\u9806\u306b\u8a66\u3057\u307e\u3059\u3002";
    private static final int INITIAL_REFETCH_GUARD_TICKS = 10;
    private static final int RESUME_REFETCH_GUARD_TICKS = 10;
    private static final int CREATIVE_REFETCH_GUARD_TICKS = 10;
    private static final int WAITING_RETRY_TICKS = 60;
    private static final int DIAGNOSTIC_RETRY_TICKS = 40;
    private static final int DIAGNOSTIC_NOTICE_TICKS = 100;
    private static final int MAX_DIAGNOSTIC_RESTARTS = 6;
    private static final int PROGRESS_STALL_TICKS = 240;
    private static final int CREATIVE_SMALL_REMAINING_STALL_TICKS = 60;
    private static final int CREATIVE_COMMAND_FILL_REMAINING_THRESHOLD = 512;
    private static final int CREATIVE_COMMAND_FILL_BATCH = 64;
    private static final int MAX_REPEATED_CREATIVE_COMMAND_FILL = 2;
    private static final int CREATIVE_STUCK_SKIP_LIMIT = 128;
    private static final int MAX_COMPLETION_RECHECKS = 2;
    private static final int COMPLETION_RECHECK_GUARD_TICKS = 12;

    private enum Mode {
        IDLE,
        BUILDING,
        FETCHING,
        CRAFTING,
        SMELTING,
        DIAGNOSING,
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
    private static int diagnosticAttempts;
    private static int diagnosticNoticeTicks;
    private static int progressStallTicks;
    private static int completionRecheckCount;
    private static int repeatedCreativeCommandFillAttempts;
    private static int lastRemainingBlocks = -1;
    private static String lastProgressTarget = "";
    private static String lastCreativeCommandFillSignature = "";
    private static boolean materialShortageNotified;
    private static boolean baritoneReportedFinished;
    private static String status = "Idle";
    private static String activeSchematicFileName = "";
    private static BlockPos activeSchematicOrigin;
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
        if (diagnosticNoticeTicks > 0) {
            diagnosticNoticeTicks--;
        }
        if (handleReportedCompletion()) {
            return;
        }
        if ((isAutoBuildAutomationMode() || mode == Mode.PAUSED) && BaritoneBridge.isClearingAreaActive()) {
            prepareForExternalClearArea();
            return;
        }
        if (mode == Mode.WAITING_FOR_MATERIALS) {
            retryWaitingForMaterials();
            return;
        }
        if (mode == Mode.PAUSED || mode == Mode.IDLE || mode == Mode.COMPLETE || mode == Mode.FETCHING || mode == Mode.CRAFTING || mode == Mode.SMELTING) {
            return;
        }

        if (mode == Mode.BUILDING || mode == Mode.DIAGNOSING) {
            monitorBuilder();
        }
    }

    private static void completeBuild(String completeStatus) {
        closeContainerIfNeeded();
        BaritoneBridge.pauseBuilder();
        BaritoneBridge.cancelPathing();
        AutoBuilderDiagnostics.export("complete: " + completeStatus);
        AutoBuilderCheckpoint.clear();
        mode = Mode.COMPLETE;
        pausedFrom = Mode.IDLE;
        diagnosticAttempts = 0;
        completionRecheckCount = 0;
        diagnosticNoticeTicks = 0;
        materialShortageNotified = false;
        baritoneReportedFinished = false;
        resetProgressWatch();
        resetCreativeCommandFillWatch();
        status = completeStatus;
        cachedSnapshotAtMs = 0L;
        message(completeStatus, ChatFormatting.GREEN);
    }

    private static boolean handleReportedCompletion() {
        if (!baritoneReportedFinished || !isAutoBuildAutomationMode()) {
            return false;
        }
        baritoneReportedFinished = false;

        BaritoneBridge.BuildStats stats = BaritoneBridge.buildStats();
        if (stats.remainingBlocks() > 0) {
            completionRecheckCount = 0;
            resetProgressWatch();
            status = "完了確認: 未完了ブロックが残っているため再診断します (" + stats.remainingBlocks() + ")";
            message(status, ChatFormatting.YELLOW);
            runAutoDiagnostic("Baritone 完了通知後に未完了ブロックを検出", true);
            return true;
        }

        if (completionRecheckCount < MAX_COMPLETION_RECHECKS && restartActiveBuild()) {
            completionRecheckCount++;
            refetchGuardTicks = Math.max(refetchGuardTicks, COMPLETION_RECHECK_GUARD_TICKS);
            builderPausedTicks = 0;
            inactiveTicks = 0;
            resetProgressWatch();
            mode = Mode.BUILDING;
            lastWorkMode = WorkMode.AUTO_BUILD;
            status = "完了確認: 設計図を再スキャン中 (" + completionRecheckCount + "/" + MAX_COMPLETION_RECHECKS + ")";
            message(status, ChatFormatting.AQUA);
            return true;
        }

        completionRecheckCount = 0;
        completeBuild("Build complete");
        return true;
    }

    public static void prepareForExternalClearArea() {
        closeContainerIfNeeded();
        MaterialChestProcess.stop("External clear area started");
        MaterialCraftProcess.stop("External clear area started");
        MaterialSmeltProcess.stop("External clear area started");
        mode = Mode.IDLE;
        pausedFrom = Mode.IDLE;
        lastWorkMode = WorkMode.CLEAR_AREA;
        diagnosticAttempts = 0;
        completionRecheckCount = 0;
        diagnosticNoticeTicks = 0;
        materialShortageNotified = false;
        baritoneReportedFinished = false;
        resetProgressWatch();
        resetCreativeCommandFillWatch();
        status = "整地モードに切り替え";
        cachedSnapshotAtMs = 0L;
    }

    public static void onBaritoneBuildFinished() {
        if (isAutoBuildAutomationMode()) {
            baritoneReportedFinished = true;
            status = "Baritone reported build complete";
            cachedSnapshotAtMs = 0L;
        }
    }

    public static void startFullAutoBuild() {
        if (AutoBuilderConfig.dryRunMode()) {
            runDryRunCheck();
            return;
        }
        if (!BaritoneBridge.isAvailable()) {
            status = "Baritone not found";
            mode = Mode.ERROR;
            message(status, ChatFormatting.RED);
            return;
        }
        MaterialChestProcess.stop("Restarting full auto build");
        BaritoneBridge.cancelPathing();
        BaritoneBridge.resumeBuilder();
        BaritoneBridge.applyBuildPolicies();
        if (!BaritoneBridge.startPlacedSchematicBuild()) {
            status = BaritoneBridge.openSchematicStatus();
            message(status, ChatFormatting.YELLOW);
            mode = Mode.IDLE;
            return;
        }
        activeSchematicFileName = "";
        builderPausedTicks = 0;
        inactiveTicks = 0;
        resetProgressWatch();
        resetCreativeCommandFillWatch();
        refetchGuardTicks = isCreativeMode() ? CREATIVE_REFETCH_GUARD_TICKS : INITIAL_REFETCH_GUARD_TICKS;
        creativeSupplyTicks = 0;
        diagnosticAttempts = 0;
        completionRecheckCount = 0;
        diagnosticNoticeTicks = 0;
        materialShortageNotified = false;
        baritoneReportedFinished = false;
        mode = Mode.BUILDING;
        lastWorkMode = WorkMode.AUTO_BUILD;
        status = "Building from placed schematic";
        activeSchematicOrigin = null;
        writeCheckpoint("start placed schematic");
        rememberNeededItems();
        int supplied = CreativeMaterialSupplier.supplyNeeded(Minecraft.getInstance(), lastNeededItems, BaritoneBridge.preferredScaffoldItems(lastNeededItems));
        if (supplied > 0) {
            message("\u30af\u30ea\u30a8\u30a4\u30c6\u30a3\u30d6\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u304b\u3089\u4e0d\u8db3\u5019\u88dc\u3092\u88dc\u5145\u3057\u307e\u3057\u305f: " + supplied, ChatFormatting.GREEN);
        }
        message("Full auto build started", ChatFormatting.AQUA);
    }

    public static void startFullAutoBuildFromFile(String fileName) {
        if (AutoBuilderConfig.dryRunMode()) {
            activeSchematicFileName = fileName == null ? "" : fileName;
            activeSchematicOrigin = null;
            runDryRunCheck();
            return;
        }
        if (!BaritoneBridge.isAvailable()) {
            status = "Baritone not found";
            mode = Mode.ERROR;
            message(status, ChatFormatting.RED);
            return;
        }
        if (fileName == null || fileName.isBlank()) {
            status = "設計図ファイル名が空です";
            mode = Mode.IDLE;
            message(status, ChatFormatting.YELLOW);
            return;
        }
        MaterialChestProcess.stop("Restarting full auto build from file");
        BaritoneBridge.cancelPathing();
        BaritoneBridge.resumeBuilder();
        BaritoneBridge.applyBuildPolicies();
        if (!BaritoneBridge.startSchematicFileBuild(fileName)) {
            status = BaritoneBridge.schematicFileStatus();
            message(status, ChatFormatting.YELLOW);
            mode = Mode.IDLE;
            return;
        }
        activeSchematicFileName = fileName;
        activeSchematicOrigin = null;
        builderPausedTicks = 0;
        inactiveTicks = 0;
        resetProgressWatch();
        resetCreativeCommandFillWatch();
        refetchGuardTicks = isCreativeMode() ? CREATIVE_REFETCH_GUARD_TICKS : INITIAL_REFETCH_GUARD_TICKS;
        creativeSupplyTicks = 0;
        diagnosticAttempts = 0;
        completionRecheckCount = 0;
        diagnosticNoticeTicks = 0;
        materialShortageNotified = false;
        baritoneReportedFinished = false;
        mode = Mode.BUILDING;
        lastWorkMode = WorkMode.AUTO_BUILD;
        status = BaritoneBridge.schematicFileStatus();
        writeCheckpoint("start file schematic");
        rememberNeededItems();
        int supplied = CreativeMaterialSupplier.supplyNeeded(Minecraft.getInstance(), lastNeededItems, BaritoneBridge.preferredScaffoldItems(lastNeededItems));
        if (supplied > 0) {
            message("クリエイティブインベントリから不足候補を補充しました: " + supplied, ChatFormatting.GREEN);
        }
        message("Full auto build started from file", ChatFormatting.AQUA);
    }

    public static void startFullAutoBuildFromFileAt(String fileName, int x, int y, int z) {
        if (AutoBuilderConfig.dryRunMode()) {
            activeSchematicFileName = fileName == null ? "" : fileName;
            activeSchematicOrigin = new BlockPos(x, y, z);
            runDryRunCheck();
            return;
        }
        if (!BaritoneBridge.isAvailable()) {
            status = "Baritone not found";
            mode = Mode.ERROR;
            message(status, ChatFormatting.RED);
            return;
        }
        if (fileName == null || fileName.isBlank()) {
            status = "Schematic file name is empty";
            mode = Mode.IDLE;
            message(status, ChatFormatting.YELLOW);
            return;
        }
        MaterialChestProcess.stop("Restarting full auto build from file at origin");
        BaritoneBridge.cancelPathing();
        BaritoneBridge.resumeBuilder();
        BaritoneBridge.applyBuildPolicies();
        if (!BaritoneBridge.startSchematicFileBuildAt(fileName, x, y, z)) {
            status = BaritoneBridge.schematicFileStatus();
            message(status, ChatFormatting.YELLOW);
            mode = Mode.IDLE;
            return;
        }
        activeSchematicFileName = fileName;
        activeSchematicOrigin = new BlockPos(x, y, z);
        builderPausedTicks = 0;
        inactiveTicks = 0;
        resetProgressWatch();
        resetCreativeCommandFillWatch();
        refetchGuardTicks = isCreativeMode() ? CREATIVE_REFETCH_GUARD_TICKS : INITIAL_REFETCH_GUARD_TICKS;
        creativeSupplyTicks = 0;
        diagnosticAttempts = 0;
        completionRecheckCount = 0;
        diagnosticNoticeTicks = 0;
        materialShortageNotified = false;
        baritoneReportedFinished = false;
        mode = Mode.BUILDING;
        lastWorkMode = WorkMode.AUTO_BUILD;
        status = BaritoneBridge.schematicFileStatus();
        writeCheckpoint("start file schematic at origin");
        rememberNeededItems();
        int supplied = CreativeMaterialSupplier.supplyNeeded(Minecraft.getInstance(), lastNeededItems, BaritoneBridge.preferredScaffoldItems(lastNeededItems));
        if (supplied > 0) {
            message("Creative inventory supplied candidate materials: " + supplied, ChatFormatting.GREEN);
        }
        message("Full auto build started from file @ " + x + "," + y + "," + z, ChatFormatting.AQUA);
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
        diagnosticAttempts = 0;
        completionRecheckCount = 0;
        diagnosticNoticeTicks = 0;
        lastNeededItems = Set.of();
        activeSchematicFileName = "";
        activeSchematicOrigin = null;
        baritoneReportedFinished = false;
        resetCreativeCommandFillWatch();
        AutoBuilderDiagnostics.export("cancel");
        AutoBuilderCheckpoint.clear();
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
            case BUILDING, FETCHING, CRAFTING, SMELTING, DIAGNOSING, WAITING_FOR_MATERIALS -> true;
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
        return mode == Mode.BUILDING || mode == Mode.FETCHING || mode == Mode.CRAFTING || mode == Mode.SMELTING || mode == Mode.DIAGNOSING || mode == Mode.WAITING_FOR_MATERIALS || mode == Mode.PAUSED;
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

    public static List<String> materialPlanSummaries(int limit) {
        List<String> plan = BaritoneBridge.materialPlanSummaries(limit);
        return plan.isEmpty() ? neededMaterialSummaries(limit) : plan;
    }

    public static List<String> unfinishedBlockSummaries(int limit) {
        return BaritoneBridge.unfinishedBlockSummaries(limit);
    }

    public static String exportDiagnosisNow() {
        String path = AutoBuilderDiagnostics.export("manual export");
        if (path.isBlank()) {
            message("診断ログを保存できませんでした", ChatFormatting.YELLOW);
        } else {
            message("診断ログを保存しました: " + path, ChatFormatting.GREEN);
        }
        return path;
    }

    public static void runDryRunCheck() {
        rememberNeededItems();
        BaritoneBridge.BuildStats stats = BaritoneBridge.buildStats();
        String path = AutoBuilderDiagnostics.export("dry run");
        status = "Dry-run: remaining=" + stats.remainingBlocks() + " materialTypes=" + lastNeededItems.size();
        cachedSnapshotAtMs = 0L;
        message(status + (path.isBlank() ? "" : " / " + path), ChatFormatting.AQUA);
    }

    public static void saveCheckpointNow() {
        writeCheckpoint("manual save");
        message("チェックポイントを保存しました: " + AutoBuilderCheckpoint.summary(), ChatFormatting.GREEN);
    }

    public static void clearCheckpointNow() {
        AutoBuilderCheckpoint.clear();
        message("チェックポイントを削除しました", ChatFormatting.YELLOW);
    }

    public static void resumeCheckpoint() {
        AutoBuilderCheckpoint.Snapshot snapshot = AutoBuilderCheckpoint.load();
        if (snapshot == null) {
            message("再開できるチェックポイントがありません", ChatFormatting.YELLOW);
            return;
        }
        if (snapshot.hasFileBuild()) {
            if (snapshot.hasOrigin()) {
                BlockPos origin = snapshot.origin();
                startFullAutoBuildFromFileAt(snapshot.schematicFile(), origin.getX(), origin.getY(), origin.getZ());
            } else {
                startFullAutoBuildFromFile(snapshot.schematicFile());
            }
            return;
        }
        startFullAutoBuild();
    }

    public static String checkpointSummary() {
        return AutoBuilderCheckpoint.summary();
    }

    private static void monitorBuilder() {
        if (BaritoneBridge.isBuilderPaused()) {
            builderPausedTicks++;
            inactiveTicks = 0;
            status = "自動診断中: Baritone が一時停止しました";
            rememberNeededItems();
            if (builderPausedTicks >= 6 && (refetchGuardTicks == 0 || isCreativeMode()) && runAutoDiagnostic("Baritone が建築を止めました", true)) {
                return;
            }
            if (builderPausedTicks >= 30 && !materialShortageNotified) {
                materialShortageNotified = true;
                message("自動診断中です。" + MATERIAL_SHORTAGE_HINT, ChatFormatting.YELLOW);
            }
            return;
        }

        builderPausedTicks = 0;
        materialShortageNotified = false;
        supplyCreativeWhileBuilding();
        if (BaritoneBridge.isBuilderActive()) {
            inactiveTicks = 0;
            diagnosticAttempts = 0;
            if (handleProgressStall()) {
                return;
            }
            status = "Building";
            return;
        }

        inactiveTicks++;
        if (inactiveTicks > 80) {
            if (refetchGuardTicks == 0) {
                runAutoDiagnostic("Baritone が待機状態のまま進んでいません", true);
            } else {
                status = "自動診断待機中: Baritone idle";
            }
        }
    }

    private static boolean handleProgressStall() {
        BaritoneBridge.BuildStats stats = BaritoneBridge.buildStats();
        if (stats.totalBlocks() <= 0 || stats.remainingBlocks() <= 0) {
            resetProgressWatch();
            return false;
        }
        String target = stats.target() == null ? "" : stats.target();
        if (stats.remainingBlocks() == lastRemainingBlocks) {
            progressStallTicks++;
        } else {
            lastRemainingBlocks = stats.remainingBlocks();
            lastProgressTarget = target;
            progressStallTicks = 0;
            return false;
        }
        lastProgressTarget = target;
        int stallThreshold = isCreativeMode() && stats.remainingBlocks() <= CREATIVE_COMMAND_FILL_REMAINING_THRESHOLD
                ? CREATIVE_SMALL_REMAINING_STALL_TICKS
                : PROGRESS_STALL_TICKS;
        if (progressStallTicks < stallThreshold || refetchGuardTicks > 0) {
            return false;
        }
        progressStallTicks = 0;
        rememberNeededItems();
        if (tryCreativeCommandFill(stats.remainingBlocks(), "progress stalled at " + stats.remainingBlocks() + " block(s)")) {
            return true;
        }
        int deferred = BaritoneBridge.deferCurrentBuildTargets("progress stalled at " + stats.remainingBlocks() + " block(s)");
        if (deferred > 0) {
            refetchGuardTicks = DIAGNOSTIC_RETRY_TICKS;
            BaritoneBridge.cancelPathing();
            BaritoneBridge.resumeBuilder();
            status = "自動診断: 到達困難な候補を後回しにして続行します";
            message(status + " (" + deferred + ")", ChatFormatting.AQUA);
            return true;
        }
        if (runAutoDiagnostic("進捗が止まっています", true)) {
            return true;
        }
        return false;
    }

    private static void resetProgressWatch() {
        progressStallTicks = 0;
        lastRemainingBlocks = -1;
        lastProgressTarget = "";
    }

    private static boolean runAutoDiagnostic(String trigger, boolean allowChestFetch) {
        mode = Mode.DIAGNOSING;
        status = "自動診断中: " + trigger;
        writeCheckpoint("diagnostic: " + trigger);
        AutoBuilderDiagnostics.export("diagnostic: " + trigger);
        if (diagnosticNoticeTicks <= 0) {
            diagnosticNoticeTicks = DIAGNOSTIC_NOTICE_TICKS;
            message(status + "。代替手順を試します。", ChatFormatting.AQUA);
        }
        rememberNeededItems();
        if (supplyCreativeAndResume()) {
            return true;
        }
        BaritoneBridge.BuildStats stats = BaritoneBridge.buildStats();
        if (tryCreativeCommandFill(stats.remainingBlocks(), "creative diagnostic")) {
            return true;
        }
        if (isCreativeMode()) {
            if (stats.remainingBlocks() > 0 && stats.remainingBlocks() <= 16) {
                int skipped = BaritoneBridge.markCurrentBuildTargetsUnreachable("creative diagnostic could not progress small remainder", CREATIVE_STUCK_SKIP_LIMIT);
                if (skipped > 0) {
                    refetchGuardTicks = DIAGNOSTIC_RETRY_TICKS;
                    BaritoneBridge.cancelPathing();
                    BaritoneBridge.resumeBuilder();
                    mode = Mode.BUILDING;
                    status = "Creative診断: 残った難所を後回しにして続行します (" + skipped + ")";
                    message(status, ChatFormatting.YELLOW);
                    return true;
                }
            }
            int deferred = BaritoneBridge.deferCurrentBuildTargets("creative diagnostic");
            if (deferred > 0) {
                refetchGuardTicks = DIAGNOSTIC_RETRY_TICKS;
                BaritoneBridge.cancelPathing();
                BaritoneBridge.resumeBuilder();
                mode = Mode.BUILDING;
                status = "Creative診断: 到達困難な候補を後回しにして続行します";
                message(status + " (" + deferred + ")", ChatFormatting.AQUA);
                return true;
            }
        }
        if (!isCreativeMode() && allowChestFetch && AutoBuilderConfig.autoFetchMaterials() && MaterialChestProcess.hasMaterialSources() && !MaterialChestProcess.isRunning()) {
            materialShortageNotified = false;
            startMaterialFetchForBuild("自動診断: 素材チェストから補充します");
            return true;
        }
        if (!isCreativeMode() && tryMaterialCreationForBuild("自動診断: 手持ち素材から作れる分を作成します")) {
            return true;
        }
        if (AutoBuilderConfig.startBuildAfterFetch() && diagnosticAttempts < MAX_DIAGNOSTIC_RESTARTS) {
            diagnosticAttempts++;
            refetchGuardTicks = DIAGNOSTIC_RETRY_TICKS;
            resumeBuildAfterMaterialChange("自動診断: Baritone を再初期化して建築を再開します (" + diagnosticAttempts + "/" + MAX_DIAGNOSTIC_RESTARTS + ")");
            return true;
        }
        mode = Mode.WAITING_FOR_MATERIALS;
        refetchGuardTicks = DIAGNOSTIC_RETRY_TICKS;
        status = "自動診断: 代替手順待機中。" + MATERIAL_SHORTAGE_HINT;
        if (!materialShortageNotified) {
            materialShortageNotified = true;
            message(status, ChatFormatting.YELLOW);
        }
        return false;
    }

    private static boolean tryCreativeCommandFill(int remainingBlocks, String reason) {
        if (!isCreativeMode() || remainingBlocks <= 0 || remainingBlocks > CREATIVE_COMMAND_FILL_REMAINING_THRESHOLD) {
            return false;
        }
        String signature = remainingBlocks + "|" + BaritoneBridge.buildStats().target();
        if (signature.equals(lastCreativeCommandFillSignature)) {
            repeatedCreativeCommandFillAttempts++;
        } else {
            lastCreativeCommandFillSignature = signature;
            repeatedCreativeCommandFillAttempts = 1;
        }
        if (repeatedCreativeCommandFillAttempts > MAX_REPEATED_CREATIVE_COMMAND_FILL) {
            int skipped = BaritoneBridge.markCurrentBuildTargetsUnreachable("creative command fill did not change " + signature, CREATIVE_STUCK_SKIP_LIMIT);
            if (skipped > 0) {
                refetchGuardTicks = DIAGNOSTIC_RETRY_TICKS;
                BaritoneBridge.cancelPathing();
                BaritoneBridge.resumeBuilder();
                mode = Mode.BUILDING;
                resetProgressWatch();
                resetCreativeCommandFillWatch();
                status = "Creative\u88dc\u5b8c\u3067\u9032\u307e\u306a\u3044\u96e3\u6240\u3092\u5f8c\u56de\u3057\u306b\u3057\u3066\u7d9a\u884c\u3057\u307e\u3059 (" + skipped + ")";
                message(status, ChatFormatting.YELLOW);
                return true;
            }
            status = "Creative\u88dc\u5b8c\u3092\u30b9\u30ad\u30c3\u30d7: \u540c\u3058\u5bfe\u8c61\u3067\u6539\u5584\u3057\u306a\u3044\u305f\u3081\u5225\u624b\u9806\u3078\u9032\u307f\u307e\u3059";
            message(status, ChatFormatting.YELLOW);
            return false;
        }
        int sent = BaritoneBridge.creativeCommandSetRemaining(CREATIVE_COMMAND_FILL_BATCH);
        if (sent <= 0) {
            return false;
        }
        refetchGuardTicks = CREATIVE_REFETCH_GUARD_TICKS;
        BaritoneBridge.cancelPathing();
        BaritoneBridge.resumeBuilder();
        mode = Mode.BUILDING;
        resetProgressWatch();
        status = "Creative\u88dc\u5b8c: \u672a\u5b8c\u4e86\u30d6\u30ed\u30c3\u30af\u3092\u76f4\u63a5\u53cd\u6620\u3057\u307e\u3057\u305f (" + sent + ")";
        message(status + " / " + reason, ChatFormatting.GREEN);
        return true;
    }

    private static void resetCreativeCommandFillWatch() {
        repeatedCreativeCommandFillAttempts = 0;
        lastCreativeCommandFillSignature = "";
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
            if (runAutoDiagnostic("素材チェストから必要素材を取れませんでした", false)) {
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
        BaritoneBridge.applyBuildPolicies();
        materialShortageNotified = false;
        if (!restartActiveBuild()) {
            mode = Mode.WAITING_FOR_MATERIALS;
            status = "\u8a2d\u8a08\u56f3\u304c\u898b\u3064\u304b\u3089\u306a\u3044\u305f\u3081\u518d\u958b\u3067\u304d\u307e\u305b\u3093";
            message(status, ChatFormatting.YELLOW);
            return;
        }
        builderPausedTicks = 0;
        inactiveTicks = 0;
        resetProgressWatch();
        mode = Mode.BUILDING;
        lastWorkMode = WorkMode.AUTO_BUILD;
        status = resumeStatus;
        writeCheckpoint("resume after material change");
        message(status, ChatFormatting.GREEN);
    }

    private static boolean restartActiveBuild() {
        BaritoneBridge.applyBuildPolicies();
        if (activeSchematicFileName != null && !activeSchematicFileName.isBlank()) {
            if (activeSchematicOrigin != null) {
                return BaritoneBridge.startSchematicFileBuildAt(activeSchematicFileName, activeSchematicOrigin.getX(), activeSchematicOrigin.getY(), activeSchematicOrigin.getZ());
            }
            return BaritoneBridge.startSchematicFileBuild(activeSchematicFileName);
        }
        return BaritoneBridge.startPlacedSchematicBuild();
    }

    private static void writeCheckpoint(String reason) {
        AutoBuilderCheckpoint.save(activeSchematicFileName, activeSchematicOrigin, mode.name(), reason + " / " + status);
    }

    private static void retryWaitingForMaterials() {
        if (refetchGuardTicks > 0) {
            return;
        }
        if (runAutoDiagnostic("素材不足で待機しています", true)) {
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
        int supplied = CreativeMaterialSupplier.supplyNeeded(Minecraft.getInstance(), needed, BaritoneBridge.preferredScaffoldItems(needed));
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
        CreativeMaterialSupplier.supplyNeeded(Minecraft.getInstance(), needed, BaritoneBridge.preferredScaffoldItems(needed));
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
        writeCheckpoint("pause");
        AutoBuilderDiagnostics.export("pause");
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
        BaritoneBridge.applyBuildPolicies();
        if (resumeFrom == Mode.BUILDING || BaritoneBridge.isBuilderActive()) {
            if (!BaritoneBridge.isBuilderActive()) {
                if (!restartActiveBuild()) {
                    mode = Mode.IDLE;
                    status = activeSchematicFileName == null || activeSchematicFileName.isBlank()
                            ? BaritoneBridge.openSchematicStatus()
                            : BaritoneBridge.schematicFileStatus();
                    message(status, ChatFormatting.YELLOW);
                    return;
                }
            }
            materialShortageNotified = false;
            resetProgressWatch();
            mode = Mode.BUILDING;
            lastWorkMode = WorkMode.AUTO_BUILD;
            status = "Resumed build";
            writeCheckpoint("resume");
            message(status, ChatFormatting.GREEN);
            return;
        }
        mode = Mode.IDLE;
        status = "Idle";
        lastNeededItems = Set.of();
        activeSchematicFileName = "";
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
        if (mode == Mode.BUILDING || mode == Mode.FETCHING || mode == Mode.CRAFTING || mode == Mode.SMELTING || mode == Mode.DIAGNOSING || mode == Mode.WAITING_FOR_MATERIALS || mode == Mode.PAUSED || mode == Mode.COMPLETE) {
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
            case DIAGNOSING -> "自動診断中";
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
            case DIAGNOSING -> "原因分析と代替手順を実行中";
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
