package com.kanzakideath.schematicautobuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public final class AutoBuilderScreen extends Screen {

    private static final int BACKGROUND = 0xD8101218;
    private static final int PANEL = 0xF0222630;
    private static final int PANEL_2 = 0xF02C323D;
    private static final int BORDER = 0xFF5E6A7A;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFB9C2CE;
    private static final int GREEN = 0xFF2ECC71;
    private static final int YELLOW = 0xFFFFC857;
    private static final int RED = 0xFFFF5A5F;
    private static final int BLUE = 0xFF4DA3FF;
    private static final int CYAN = 0xFF54D6E8;
    private static final int PURPLE = 0xFFC084FC;

    private Page page = Page.MAIN;

    public AutoBuilderScreen() {
        super(Component.literal("AutoBuilder 設定"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int panelWidth = panelWidth();
        int x = (this.width - panelWidth) / 2;
        int y = panelY();
        int contentX = x + 18;
        int contentY = y + 82;
        int contentWidth = panelWidth - 36;

        addPageTabs(contentX, y + 48, contentWidth);

        switch (page) {
            case SETTINGS -> initSettings(contentX, contentY, contentWidth);
            case CHESTS -> initChests(contentX, contentY, contentWidth);
            case MATERIALS -> initMaterials(contentX, contentY, contentWidth);
            default -> initMain(contentX, contentY, contentWidth);
        }

        int footerY = y + panelHeight() - 34;
        int buttonWidth = (contentWidth - 18) / 4;
        addRenderableWidget(button(contentX, footerY, buttonWidth, "戻る", this::backToMain));
        addRenderableWidget(button(contentX + (buttonWidth + 6), footerY, buttonWidth, "更新確認", () -> {
            AutoUpdater.checkNow();
            page = Page.MAIN;
            refresh();
        }));
        addRenderableWidget(button(contentX + (buttonWidth + 6) * 2, footerY, buttonWidth, "全停止", AutoBuildController::cancelAutomation));
        addRenderableWidget(button(contentX + (buttonWidth + 6) * 3, footerY, buttonWidth, "閉じる", () -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(null);
            }
        }));
    }

    private void initMain(int x, int y, int width) {
        int half = (width - 8) / 2;
        int row = 25;
        int gap = 7;

        addRenderableWidget(button(x, y, half, "自動建築 開始/再開（ベータ）", () -> {
            AutoBuildController.startOrResumeAutoBuild();
            refresh();
        }));
        addRenderableWidget(button(x + half + 8, y, half, "整地モードを開く", this::openBaritoneClearMode));
        y += row + gap;

        addRenderableWidget(button(x, y, half, pauseLabel(), () -> {
            AutoBuildController.togglePause();
            refresh();
        }));
        addRenderableWidget(button(x + half + 8, y, half, "素材を今すぐ補充", () -> {
            AutoBuildController.fetchMaterialsOnly();
            refresh();
        }));
        y += row + gap + 10;

        addRenderableWidget(button(x, y, half, materialChestRegisterLabel(), () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (MaterialChestProcess.isRegisteringChests()) {
                MaterialChestProcess.stopChestRegistration();
                refresh();
                return;
            }
            MaterialChestProcess.startChestRegistration(minecraft);
            if (minecraft != null) {
                minecraft.setScreenAndShow(null);
            }
        }));
        addRenderableWidget(button(x + half + 8, y, half, "素材チェスト一覧", () -> {
            page = Page.CHESTS;
            refresh();
        }));
        y += row + gap;

        addRenderableWidget(button(x, y, half, "必要/不足素材", () -> {
            page = Page.MATERIALS;
            refresh();
        }));
        addRenderableWidget(button(x + half + 8, y, half, "詳細設定", () -> {
            page = Page.SETTINGS;
            refresh();
        }));
        y += row + gap + 10;

        addRenderableWidget(button(x, y, half, autoFetchLabel(), () -> {
            AutoBuilderConfig.toggleAutoFetchMaterials();
            refresh();
        }));
        addRenderableWidget(button(x + half + 8, y, half, autoResumeLabel(), () -> {
            AutoBuilderConfig.toggleStartBuildAfterFetch();
            refresh();
        }));
        y += row + gap;

        addRenderableWidget(button(x, y, half, autoCraftLabel(), () -> {
            AutoBuilderConfig.toggleAutoCraftMaterials();
            refresh();
        }));
        addRenderableWidget(button(x + half + 8, y, half, substituteLabel(), () -> {
            AutoBuilderConfig.toggleAutoSubstituteMaterials();
            refresh();
        }));
        y += row + gap;

        addRenderableWidget(button(x, y, half, topDownLabel(), () -> {
            AutoBuilderConfig.toggleTopDownBuild();
            refresh();
        }));
        addRenderableWidget(button(x + half + 8, y, half, "素材チェスト全削除", () -> {
            AutoBuilderConfig.clearMaterialChests();
            refresh();
        }));
    }

    private void initSettings(int x, int y, int width) {
        int third = (width - 16) / 3;
        int half = (width - 8) / 2;
        int row = 25;
        int gap = 7;

        addRenderableWidget(button(x, y, half, hudEnabledLabel(), () -> {
            AutoBuilderConfig.toggleHudEnabled();
            refresh();
        }));
        addRenderableWidget(button(x + half + 8, y, half, hudModeLabel(), () -> {
            AutoBuilderConfig.toggleHudDetailed();
            refresh();
        }));
        y += row + gap;

        addRenderableWidget(button(x, y, third, hudPositionLabel(), () -> {
            AutoBuilderConfig.cycleHudPosition();
            refresh();
        }));
        addRenderableWidget(button(x + third + 8, y, third, "HUD X -4: " + AutoBuilderConfig.hudXOffset(), () -> {
            AutoBuilderConfig.adjustHudXOffset(-4);
            refresh();
        }));
        addRenderableWidget(button(x + (third + 8) * 2, y, third, "HUD X +4: " + AutoBuilderConfig.hudXOffset(), () -> {
            AutoBuilderConfig.adjustHudXOffset(4);
            refresh();
        }));
        y += row + gap;

        addRenderableWidget(button(x, y, third, "HUD Y -4: " + AutoBuilderConfig.hudYOffset(), () -> {
            AutoBuilderConfig.adjustHudYOffset(-4);
            refresh();
        }));
        addRenderableWidget(button(x + third + 8, y, third, "HUD Y +4: " + AutoBuilderConfig.hudYOffset(), () -> {
            AutoBuilderConfig.adjustHudYOffset(4);
            refresh();
        }));
        addRenderableWidget(button(x + (third + 8) * 2, y, third, "背景濃さ: " + AutoBuilderConfig.hudOpacity(), () -> {
            AutoBuilderConfig.adjustHudOpacity(16);
            refresh();
        }));
        y += row + gap + 10;

        addRenderableWidget(button(x, y, third, "文字 -10%", () -> {
            AutoBuilderConfig.adjustHudTextScalePercent(-10);
            refresh();
        }));
        addRenderableWidget(button(x + third + 8, y, third, "文字 +10%: " + AutoBuilderConfig.hudTextScalePercent() + "%", () -> {
            AutoBuilderConfig.adjustHudTextScalePercent(10);
            refresh();
        }));
        addRenderableWidget(button(x + (third + 8) * 2, y, third, safetyModeLabel(), () -> {
            AutoBuilderConfig.cycleSafetyMode();
            refresh();
        }));
        y += row + gap;

        addRenderableWidget(button(x, y, half, "素材不足表示: " + onOff(AutoBuilderConfig.hudShowMissingMaterials()), () -> {
            AutoBuilderConfig.toggleHudShowMissingMaterials();
            refresh();
        }));
        addRenderableWidget(button(x + half + 8, y, half, "Baritone表示: " + onOff(AutoBuilderConfig.hudShowBaritoneStatus()), () -> {
            AutoBuilderConfig.toggleHudShowBaritoneStatus();
            refresh();
        }));
        y += row + gap;

        addRenderableWidget(button(x, y, third, "ターゲット: " + onOff(AutoBuilderConfig.hudShowTarget()), () -> {
            AutoBuilderConfig.toggleHudShowTarget();
            refresh();
        }));
        addRenderableWidget(button(x + third + 8, y, third, "ETA: " + onOff(AutoBuilderConfig.hudShowEta()), () -> {
            AutoBuilderConfig.toggleHudShowEta();
            refresh();
        }));
        addRenderableWidget(button(x + (third + 8) * 2, y, third, "DEBUG: " + onOff(AutoBuilderConfig.hudShowDebug()), () -> {
            AutoBuilderConfig.toggleHudShowDebug();
            refresh();
        }));
        y += row + gap;

        addRenderableWidget(button(x, y, third, "診断ログ: " + onOff(AutoBuilderConfig.diagnosisLogEnabled()), () -> {
            AutoBuilderConfig.toggleDiagnosisLogEnabled();
            refresh();
        }));
        addRenderableWidget(button(x + third + 8, y, third, "チェックポイント: " + onOff(AutoBuilderConfig.checkpointEnabled()), () -> {
            AutoBuilderConfig.toggleCheckpointEnabled();
            refresh();
        }));
        addRenderableWidget(button(x + (third + 8) * 2, y, third, "ドライラン: " + onOff(AutoBuilderConfig.dryRunMode()), () -> {
            AutoBuilderConfig.toggleDryRunMode();
            refresh();
        }));
    }

    private void initChests(int x, int y, int width) {
        int row = 25;
        int gap = 6;
        List<BlockPos> chests = AutoBuilderConfig.materialChests();
        int count = Math.min(8, chests.size());
        for (int i = 0; i < count; i++) {
            BlockPos pos = chests.get(i);
            addRenderableWidget(button(x, y, width, "削除: x=" + pos.getX() + " y=" + pos.getY() + " z=" + pos.getZ(), () -> {
                MaterialChestProcess.removeRegisteredChest(pos);
                refresh();
            }));
            y += row + gap;
        }
        int half = (width - 8) / 2;
        addRenderableWidget(button(x, y, half, "素材チェスト全削除", () -> {
            AutoBuilderConfig.clearMaterialChests();
            refresh();
        }));
        addRenderableWidget(button(x + half + 8, y, half, materialChestRegisterLabel(), () -> {
            AutoBuildController.toggleMaterialChestRegistration(Minecraft.getInstance());
            refresh();
        }));
    }

    private void initMaterials(int x, int y, int width) {
        int half = (width - 8) / 2;
        int row = 25;
        int gap = 7;
        addRenderableWidget(button(x, y, half, "素材を今すぐ補充", () -> {
            AutoBuildController.fetchMaterialsOnly();
            refresh();
        }));
        addRenderableWidget(button(x + half + 8, y, half, "ドライラン診断", () -> {
            AutoBuildController.runDryRunCheck();
            refresh();
        }));
        y += row + gap;
        addRenderableWidget(button(x, y, half, "診断ログ保存", () -> {
            AutoBuildController.exportDiagnosisNow();
            refresh();
        }));
        addRenderableWidget(button(x + half + 8, y, half, "チェックポイント保存", () -> {
            AutoBuildController.saveCheckpointNow();
            refresh();
        }));
        y += row + gap;
        addRenderableWidget(button(x, y, half, "チェックポイントから再開", () -> {
            AutoBuildController.resumeCheckpoint();
            refresh();
        }));
        addRenderableWidget(button(x + half + 8, y, half, "チェックポイント削除", () -> {
            AutoBuildController.clearCheckpointNow();
            refresh();
        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        extractTransparentBackground(graphics);
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int x = (this.width - panelWidth) / 2;
        int y = panelY();
        int contentX = x + 18;
        int contentY = y + 82;
        int contentWidth = panelWidth - 36;

        graphics.fill(0, 0, this.width, this.height, BACKGROUND);
        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL);
        outline(graphics, x, y, panelWidth, panelHeight, BORDER);
        graphics.fill(x, y, x + panelWidth, y + 4, pageColor());

        graphics.centeredText(this.font, "AutoBuilder 設定（ベータ版）", this.width / 2, y + 14, TEXT);
        graphics.centeredText(this.font, "自動建築 / 整地 / 素材補充 / 更新", this.width / 2, y + 30, DIM);

        drawStatusChips(graphics, contentX, y + 50, contentWidth);
        drawPageInfo(graphics, contentX, contentY - 8, contentWidth);

        switch (page) {
            case SETTINGS -> drawSettingsPage(graphics, contentX, contentY, contentWidth);
            case CHESTS -> drawChestsPage(graphics, contentX, contentY, contentWidth);
            case MATERIALS -> drawMaterialsPage(graphics, contentX, contentY, contentWidth);
            default -> drawMainPage(graphics, contentX, contentY, contentWidth);
        }

        graphics.text(this.font, "状態: " + statusJa(AutoBuildController.status()), contentX, y + panelHeight - 58, statusColor(AutoBuildController.status()));
        graphics.text(this.font, "素材: " + statusJa(MaterialChestProcess.status()), contentX, y + panelHeight - 44, DIM);
        graphics.text(this.font, "更新: " + statusJa(AutoUpdater.status()), contentX + contentWidth / 2, y + panelHeight - 44, DIM);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    private void addPageTabs(int x, int y, int width) {
        int tabWidth = (width - 18) / 4;
        addRenderableWidget(button(x, y, tabWidth, page == Page.MAIN ? "> メイン" : "メイン", () -> setPage(Page.MAIN)));
        addRenderableWidget(button(x + tabWidth + 6, y, tabWidth, page == Page.CHESTS ? "> チェスト" : "チェスト", () -> setPage(Page.CHESTS)));
        addRenderableWidget(button(x + (tabWidth + 6) * 2, y, tabWidth, page == Page.MATERIALS ? "> 素材" : "素材", () -> setPage(Page.MATERIALS)));
        addRenderableWidget(button(x + (tabWidth + 6) * 3, y, tabWidth, page == Page.SETTINGS ? "> 設定" : "設定", () -> setPage(Page.SETTINGS)));
    }

    private void drawStatusChips(GuiGraphicsExtractor graphics, int x, int y, int width) {
        int chipWidth = (width - 24) / 4;
        drawChip(graphics, x, y, chipWidth, "補充", AutoBuilderConfig.autoFetchMaterials() ? "ON" : "OFF", AutoBuilderConfig.autoFetchMaterials() ? GREEN : RED);
        drawChip(graphics, x + chipWidth + 8, y, chipWidth, "作成", AutoBuilderConfig.autoCraftMaterials() ? "ON" : "OFF", AutoBuilderConfig.autoCraftMaterials() ? GREEN : RED);
        drawChip(graphics, x + (chipWidth + 8) * 2, y, chipWidth, "代用", AutoBuilderConfig.autoSubstituteMaterials() ? "ON" : "OFF", AutoBuilderConfig.autoSubstituteMaterials() ? GREEN : YELLOW);
        drawChip(graphics, x + (chipWidth + 8) * 3, y, chipWidth, "箱", String.valueOf(AutoBuilderConfig.materialChestCount()), AutoBuilderConfig.materialChestCount() > 0 ? BLUE : YELLOW);
    }

    private void drawPageInfo(GuiGraphicsExtractor graphics, int x, int y, int width) {
        graphics.fill(x, y + 30, x + width, y + 31, 0x445E6A7A);
        graphics.text(this.font, pageTitle(), x, y + 12, pageColor());
        AutoBuilderStatusSnapshot snapshot = AutoBuildController.statusSnapshot();
        String progress = String.format(Locale.ROOT, "進捗 %.1f%% / 残り %d / Chest %d", snapshot.progress(), snapshot.remainingBlocks(), snapshot.materialChestCount());
        graphics.text(this.font, progress, x + width - this.font.width(progress), y + 12, DIM);
    }

    private void drawMainPage(GuiGraphicsExtractor graphics, int x, int y, int width) {
        drawSection(graphics, x, y + 8, width, 82, BLUE, "1. 作業モード", "自動建築はベータ版です。止まった場合は自動診断で補充、作成、再初期化、難所スキップを試します。");
        drawSection(graphics, x, y + 100, width, 112, GREEN, "2. 素材補充", "素材チェストを登録すると、不足時に少量ずつ取りに戻ります。登録中はチェストを右クリックしてください。");
        drawSection(graphics, x, y + 222, width, 74, PURPLE, "3. 管理", "更新確認、素材代用、建築順序、素材一覧をここから確認できます。");
        graphics.text(this.font, "建築順序: " + buildOrderText(), x + 12, y + 316, AutoBuilderConfig.topDownBuild() ? BLUE : YELLOW);
        graphics.text(this.font, "Baritone: " + statusJa(BaritoneBridge.hudStatus()), x + 180, y + 316, BaritoneBridge.isAvailable() ? GREEN : RED);
    }

    private void drawSettingsPage(GuiGraphicsExtractor graphics, int x, int y, int width) {
        drawSection(graphics, x, y + 8, width, 112, CYAN, "HUD 表示", "ゲーム中の左下などに表示する状態 UI です。邪魔なら OFF、位置と文字サイズを調整してください。");
        drawSection(graphics, x, y + 130, width, 112, YELLOW, "診断と安全", "停止時の自動診断、チェックポイント、保護設定、ドライランを管理します。");
        AutoBuilderStatusSnapshot snapshot = AutoBuildController.statusSnapshot();
        graphics.text(this.font, "HUD: " + onOff(AutoBuilderConfig.hudEnabled()) + " / " + (AutoBuilderConfig.hudDetailed() ? "詳細" : "簡易")
                + " / " + AutoBuilderConfig.hudPosition(), x + 12, y + 54, AutoBuilderConfig.hudEnabled() ? GREEN : RED);
        graphics.text(this.font, "プレビュー: " + snapshot.state() + " / " + String.format(Locale.ROOT, "%.1f%%", snapshot.progress())
                + " / 残り " + snapshot.remainingBlocks(), x + 12, y + 176, statusColor(snapshot.state()));
    }

    private void drawChestsPage(GuiGraphicsExtractor graphics, int x, int y, int width) {
        drawSection(graphics, x, y + 8, width, 296, GREEN, "素材チェスト", "登録済みチェストを個別削除できます。登録中はチェスト、樽、シュルカーなどを右クリックしてください。");
        List<BlockPos> chests = AutoBuilderConfig.materialChests();
        graphics.text(this.font, "登録数: " + chests.size(), x + 12, y + 50, chests.isEmpty() ? YELLOW : GREEN);
        int ly = y + 70;
        for (int i = 0; i < Math.min(8, chests.size()); i++) {
            BlockPos pos = chests.get(i);
            graphics.text(this.font, String.format(Locale.ROOT, "%02d  x:%d y:%d z:%d", i + 1, pos.getX(), pos.getY(), pos.getZ()), x + 12, ly, TEXT);
            ly += 18;
        }
        if (chests.size() > 8) {
            graphics.text(this.font, "... +" + (chests.size() - 8), x + 12, ly, DIM);
        }
    }

    private void drawMaterialsPage(GuiGraphicsExtractor graphics, int x, int y, int width) {
        drawSection(graphics, x, y + 8, width, 296, YELLOW, "素材 / 診断", "必要素材、不足候補、未完了ブロック、チェックポイントを確認します。");
        AutoBuilderStatusSnapshot snapshot = AutoBuildController.statusSnapshot();
        graphics.text(this.font, "不足候補: " + snapshot.missingMaterialTypes() + "種 / Chest: " + snapshot.materialChestCount(), x + 12, y + 50, snapshot.missingMaterialTypes() > 0 ? YELLOW : GREEN);
        int ly = y + 72;
        int index = 1;
        for (String item : AutoBuildController.materialPlanSummaries(8)) {
            graphics.text(this.font, String.format(Locale.ROOT, "%02d  %s", index, item), x + 12, ly, TEXT);
            ly += 16;
            index++;
        }
        if (index == 1) {
            graphics.text(this.font, "建築開始後に必要素材が表示されます。", x + 12, ly, DIM);
            ly += 18;
        }
        ly += 8;
        graphics.text(this.font, "未完了ブロック:", x + 12, ly, CYAN);
        ly += 16;
        int unfinished = 1;
        if (AutoBuilderConfig.showUnfinishedList()) {
            for (String line : AutoBuildController.unfinishedBlockSummaries(6)) {
                graphics.text(this.font, String.format(Locale.ROOT, "%02d  %s", unfinished, line), x + 12, ly, DIM);
                ly += 14;
                unfinished++;
            }
        }
        if (unfinished == 1) {
            graphics.text(this.font, "まだ未完了一覧はありません。", x + 12, ly, DIM);
        }
    }

    private void drawSection(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, String title, String note) {
        graphics.fill(x, y, x + width, y + height, PANEL_2);
        graphics.fill(x, y, x + 5, y + height, color);
        outline(graphics, x, y, width, height, 0x665E6A7A);
        graphics.text(this.font, title, x + 14, y + 10, color);
        graphics.text(this.font, note, x + 14, y + 27, DIM);
    }

    private void drawChip(GuiGraphicsExtractor graphics, int x, int y, int width, String label, String value, int color) {
        graphics.fill(x, y, x + width, y + 18, 0xAA171B22);
        graphics.fill(x, y, x + 4, y + 18, color);
        outline(graphics, x, y, width, 18, color);
        graphics.text(this.font, label + ": " + value, x + 10, y + 5, color);
    }

    private Button button(int x, int y, int width, String label, Runnable action) {
        return Button.builder(Component.literal(label), button -> action.run()).bounds(x, y, width, 24).build();
    }

    private void refresh() {
        clearWidgets();
        init();
    }

    private void setPage(Page next) {
        page = next;
        refresh();
    }

    private void backToMain() {
        page = Page.MAIN;
        refresh();
    }

    private void openBaritoneClearMode() {
        if (this.minecraft == null) {
            return;
        }
        try {
            Class<?> screenClass = Class.forName("baritone.utils.BaritoneSettingsScreen");
            Object screen = screenClass.getConstructor().newInstance();
            if (screen instanceof Screen baritoneScreen) {
                this.minecraft.setScreenAndShow(baritoneScreen);
                return;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        AutoBuildController.message("Baritoneの整地メニューが見つかりません", ChatFormatting.YELLOW);
        refresh();
    }

    private String pageTitle() {
        return switch (page) {
            case SETTINGS -> "詳細設定";
            case CHESTS -> "素材チェスト";
            case MATERIALS -> "素材 / 診断";
            default -> "メイン";
        };
    }

    private int pageColor() {
        return switch (page) {
            case SETTINGS -> CYAN;
            case CHESTS -> GREEN;
            case MATERIALS -> YELLOW;
            default -> BLUE;
        };
    }

    private static String pauseLabel() {
        return AutoBuildController.isPaused() ? "現在の作業を再開" : "現在の作業を一時停止";
    }

    private static String autoFetchLabel() {
        return "素材自動補充: " + onOff(AutoBuilderConfig.autoFetchMaterials());
    }

    private static String autoResumeLabel() {
        return "補充後自動再開: " + onOff(AutoBuilderConfig.startBuildAfterFetch());
    }

    private static String autoCraftLabel() {
        return "不足素材を自動作成: " + onOff(AutoBuilderConfig.autoCraftMaterials());
    }

    private static String topDownLabel() {
        return "建築順序: " + buildOrderText();
    }

    private static String substituteLabel() {
        return "素材代用: " + onOff(AutoBuilderConfig.autoSubstituteMaterials());
    }

    private static String hudEnabledLabel() {
        return "HUD表示: " + onOff(AutoBuilderConfig.hudEnabled());
    }

    private static String hudModeLabel() {
        return "HUD形式: " + (AutoBuilderConfig.hudDetailed() ? "詳細" : "簡易");
    }

    private static String hudPositionLabel() {
        return "HUD位置: " + AutoBuilderConfig.hudPosition().name();
    }

    private static String safetyModeLabel() {
        return "安全モード: " + switch (AutoBuilderConfig.safetyMode()) {
            case NORMAL -> "通常";
            case STABLE -> "安定";
            case COMPLETE -> "完遂優先";
        };
    }

    private static String buildOrderText() {
        return AutoBuilderConfig.topDownBuild() ? "上から" : "下から";
    }

    private static String materialChestRegisterLabel() {
        return MaterialChestProcess.isRegisteringChests() ? "素材チェスト登録を終了" : "素材チェストを登録";
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String statusJa(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.contains("Baritone not found")) {
            return "Baritoneが見つかりません";
        }
        if (value.contains("Builder paused") || value.equals("Paused")) {
            return "一時停止中";
        }
        if (value.contains("Builder active") || value.equals("Building") || value.contains("building")) {
            return "実行中";
        }
        if (value.contains("idle") || value.contains("Idle")) {
            return "待機中";
        }
        if (value.contains("complete")) {
            return "完了";
        }
        return value;
    }

    private int statusColor(String value) {
        if (value == null) {
            return DIM;
        }
        if (value.contains("不足") || value.contains("Missing") || value.contains("Shortage") || value.contains("failed") || value.contains("Error")) {
            return RED;
        }
        if (value.contains("一時停止") || value.contains("Paused") || value.contains("waiting") || value.contains("待機")) {
            return YELLOW;
        }
        if (value.contains("完了") || value.contains("complete")) {
            return BLUE;
        }
        if (AutoBuildController.isRunning()) {
            return GREEN;
        }
        return DIM;
    }

    private int panelWidth() {
        return Math.min(760, this.width - 32);
    }

    private int panelY() {
        return Math.max(12, (this.height - panelHeight()) / 2);
    }

    private static int panelHeight() {
        return 500;
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private enum Page {
        MAIN,
        SETTINGS,
        CHESTS,
        MATERIALS
    }
}
