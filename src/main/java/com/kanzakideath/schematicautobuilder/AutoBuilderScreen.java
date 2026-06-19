package com.kanzakideath.schematicautobuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class AutoBuilderScreen extends Screen {

    private static final int PANEL = 0xDD061015;
    private static final int CYAN = 0xFF19EAF2;
    private static final int GREEN = 0xFF72FFAA;
    private static final int ORANGE = 0xFFFFB545;
    private static final int RED = 0xFFFF5548;
    private static final int TEXT = 0xFFEAFDFF;
    private static final int DIM = 0xFF8DB9C1;
    private static final int BLOCK = 0x66000000;

    private Page page = Page.MAIN;

    public AutoBuilderScreen() {
        super(Component.literal("\u8a2d\u8a08\u56f3 \u81ea\u52d5\u5efa\u7bc9\uff08\u30d9\u30fc\u30bf\u7248\uff09"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        switch (page) {
            case SETTINGS -> initSettings();
            case CHESTS -> initChests();
            case MATERIALS -> initMaterials();
            default -> initMain();
        }
    }

    private void initMain() {
        int panelWidth = Math.min(680, this.width - 36);
        int x = (this.width - panelWidth) / 2;
        int y = panelY();
        int full = panelWidth - 48;
        int half = (full - 8) / 2;
        int third = (full - 16) / 3;
        int row = 24;
        int gap = 6;
        int by = y + 134;

        addRenderableWidget(button(x + 24, by, half, "\u81ea\u52d5\u5efa\u7bc9\u3092\u958b\u59cb\uff08\u30d9\u30fc\u30bf\uff09", () -> {
            AutoBuildController.startFullAutoBuild();
            refresh();
        }));
        addRenderableWidget(button(x + 32 + half, by, half, "\u6574\u5730\u30e2\u30fc\u30c9\u3092\u958b\u304f", this::openBaritoneClearMode));
        by += row + gap;

        addRenderableWidget(button(x + 24, by, full, pauseLabel(), () -> {
            AutoBuildController.togglePause();
            refresh();
        }));
        by += row + gap + 20;

        addRenderableWidget(button(x + 24, by, half, materialChestRegisterLabel(), () -> {
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
            refresh();
        }));
        addRenderableWidget(button(x + 32 + half, by, half, "\u4eca\u3059\u3050\u7d20\u6750\u88dc\u5145", () -> {
            AutoBuildController.fetchMaterialsOnly();
            refresh();
        }));
        by += row + gap;

        addRenderableWidget(button(x + 24, by, half, autoFetchLabel(), () -> {
            AutoBuilderConfig.toggleAutoFetchMaterials();
            refresh();
        }));
        addRenderableWidget(button(x + 32 + half, by, half, autoResumeLabel(), () -> {
            AutoBuilderConfig.toggleStartBuildAfterFetch();
            refresh();
        }));
        by += row + gap;

        addRenderableWidget(button(x + 24, by, full, autoCraftLabel(), () -> {
            AutoBuilderConfig.toggleAutoCraftMaterials();
            refresh();
        }));
        by += row + gap;

        addRenderableWidget(button(x + 24, by, half, topDownLabel(), () -> {
            AutoBuilderConfig.toggleTopDownBuild();
            refresh();
        }));
        addRenderableWidget(button(x + 32 + half, by, half, substituteLabel(), () -> {
            AutoBuilderConfig.toggleAutoSubstituteMaterials();
            refresh();
        }));
        by += row + gap;

        addRenderableWidget(button(x + 24, by, third, "\u7d20\u6750\u30c1\u30a7\u30b9\u30c8\u4e00\u89a7", () -> {
            page = Page.CHESTS;
            refresh();
        }));
        addRenderableWidget(button(x + 32 + third, by, third, "\u5fc5\u8981/\u4e0d\u8db3\u7d20\u6750", () -> {
            page = Page.MATERIALS;
            refresh();
        }));
        addRenderableWidget(button(x + 40 + third * 2, by, third, "HUD\u8a2d\u5b9a", () -> {
            page = Page.SETTINGS;
            refresh();
        }));
        by += row + gap + 20;

        addRenderableWidget(button(x + 24, by, half, "\u7d20\u6750\u30c1\u30a7\u30b9\u30c8\u767b\u9332\u3092\u5168\u524a\u9664", () -> {
            AutoBuilderConfig.clearMaterialChests();
            refresh();
        }));
        addRenderableWidget(button(x + 32 + half, by, half, "\u66f4\u65b0\u3092\u78ba\u8a8d", () -> {
            AutoUpdater.checkNow();
            refresh();
        }));
        by += row + gap;

        addRenderableWidget(button(x + 24, by, full, "\u9589\u3058\u308b", () -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(null);
            }
        }));
    }

    private void initSettings() {
        int panelWidth = Math.min(680, this.width - 36);
        int x = (this.width - panelWidth) / 2;
        int y = panelY();
        int full = panelWidth - 48;
        int half = (full - 8) / 2;
        int third = (full - 16) / 3;
        int quarter = (full - 24) / 4;
        int row = 24;
        int gap = 6;
        int by = y + 134;

        addRenderableWidget(button(x + 24, by, half, hudEnabledLabel(), () -> {
            AutoBuilderConfig.toggleHudEnabled();
            refresh();
        }));
        addRenderableWidget(button(x + 32 + half, by, half, hudModeLabel(), () -> {
            AutoBuilderConfig.toggleHudDetailed();
            refresh();
        }));
        by += row + gap;

        addRenderableWidget(button(x + 24, by, third, hudPositionLabel(), () -> {
            AutoBuilderConfig.cycleHudPosition();
            refresh();
        }));
        addRenderableWidget(button(x + 32 + third, by, third, "HUD X -4: " + AutoBuilderConfig.hudXOffset(), () -> {
            AutoBuilderConfig.adjustHudXOffset(-4);
            refresh();
        }));
        addRenderableWidget(button(x + 40 + third * 2, by, third, "HUD X +4: " + AutoBuilderConfig.hudXOffset(), () -> {
            AutoBuilderConfig.adjustHudXOffset(4);
            refresh();
        }));
        by += row + gap;

        addRenderableWidget(button(x + 24, by, third, "HUD Y -4: " + AutoBuilderConfig.hudYOffset(), () -> {
            AutoBuilderConfig.adjustHudYOffset(-4);
            refresh();
        }));
        addRenderableWidget(button(x + 32 + third, by, third, "HUD Y +4: " + AutoBuilderConfig.hudYOffset(), () -> {
            AutoBuilderConfig.adjustHudYOffset(4);
            refresh();
        }));
        addRenderableWidget(button(x + 40 + third * 2, by, third, "透明度: " + AutoBuilderConfig.hudOpacity(), () -> {
            AutoBuilderConfig.adjustHudOpacity(16);
            refresh();
        }));
        by += row + gap;

        addRenderableWidget(button(x + 24, by, quarter, "透明度 -16", () -> {
            AutoBuilderConfig.adjustHudOpacity(-16);
            refresh();
        }));
        addRenderableWidget(button(x + 32 + quarter, by, quarter, "透明度 +16", () -> {
            AutoBuilderConfig.adjustHudOpacity(16);
            refresh();
        }));
        addRenderableWidget(button(x + 40 + quarter * 2, by, quarter, "文字 -10%", () -> {
            AutoBuilderConfig.adjustHudTextScalePercent(-10);
            refresh();
        }));
        addRenderableWidget(button(x + 48 + quarter * 3, by, quarter, "文字 +10%: " + AutoBuilderConfig.hudTextScalePercent() + "%", () -> {
            AutoBuilderConfig.adjustHudTextScalePercent(10);
            refresh();
        }));
        by += row + gap + 10;

        addRenderableWidget(button(x + 24, by, half, "素材不足表示: " + onOff(AutoBuilderConfig.hudShowMissingMaterials()), () -> {
            AutoBuilderConfig.toggleHudShowMissingMaterials();
            refresh();
        }));
        addRenderableWidget(button(x + 32 + half, by, half, "Baritone表示: " + onOff(AutoBuilderConfig.hudShowBaritoneStatus()), () -> {
            AutoBuilderConfig.toggleHudShowBaritoneStatus();
            refresh();
        }));
        by += row + gap;

        addRenderableWidget(button(x + 24, by, third, "ターゲット: " + onOff(AutoBuilderConfig.hudShowTarget()), () -> {
            AutoBuilderConfig.toggleHudShowTarget();
            refresh();
        }));
        addRenderableWidget(button(x + 32 + third, by, third, "ETA: " + onOff(AutoBuilderConfig.hudShowEta()), () -> {
            AutoBuilderConfig.toggleHudShowEta();
            refresh();
        }));
        addRenderableWidget(button(x + 40 + third * 2, by, third, "DEBUG: " + onOff(AutoBuilderConfig.hudShowDebug()), () -> {
            AutoBuilderConfig.toggleHudShowDebug();
            refresh();
        }));
        by += row + gap + 20;

        addRenderableWidget(button(x + 24, by, full, "\u623b\u308b", this::backToMain));
    }

    private void initChests() {
        int panelWidth = Math.min(680, this.width - 36);
        int x = (this.width - panelWidth) / 2;
        int y = panelY();
        int full = panelWidth - 48;
        int half = (full - 8) / 2;
        int row = 24;
        int gap = 6;
        int by = y + 134;
        List<BlockPos> chests = AutoBuilderConfig.materialChests();
        int count = Math.min(8, chests.size());
        for (int i = 0; i < count; i++) {
            BlockPos pos = chests.get(i);
            addRenderableWidget(button(x + 24, by, full, "\u524a\u9664: " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), () -> {
                MaterialChestProcess.removeRegisteredChest(pos);
                refresh();
            }));
            by += row + gap;
        }
        by += gap;
        addRenderableWidget(button(x + 24, by, half, "\u7d20\u6750\u30c1\u30a7\u30b9\u30c8\u5168\u524a\u9664", () -> {
            AutoBuilderConfig.clearMaterialChests();
            refresh();
        }));
        addRenderableWidget(button(x + 32 + half, by, half, "\u623b\u308b", this::backToMain));
    }

    private void initMaterials() {
        int panelWidth = Math.min(680, this.width - 36);
        int x = (this.width - panelWidth) / 2;
        int y = panelY();
        int full = panelWidth - 48;
        int half = (full - 8) / 2;
        int by = y + panelHeight() - 76;

        addRenderableWidget(button(x + 24, by, half, "\u4eca\u3059\u3050\u7d20\u6750\u88dc\u5145", () -> {
            AutoBuildController.fetchMaterialsOnly();
            refresh();
        }));
        addRenderableWidget(button(x + 32 + half, by, half, "\u623b\u308b", this::backToMain));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        extractTransparentBackground(graphics);
        int panelWidth = Math.min(680, this.width - 36);
        int panelHeight = panelHeight();
        int x = (this.width - panelWidth) / 2;
        int y = panelY();

        graphics.fill(0, 0, this.width, this.height, 0xD8000508);
        drawGrid(graphics, 28, 0x2219EAF2);
        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL);
        border(graphics, x, y, panelWidth, panelHeight, CYAN);
        graphics.fill(x + 10, y + 10, x + panelWidth - 10, y + panelHeight - 10, 0x66000000);
        border(graphics, x + 10, y + 10, panelWidth - 20, panelHeight - 20, 0x6619EAF2);

        graphics.centeredText(this.font, "\u8a2d \u8a08 \u56f3   \u81ea \u52d5 \u5efa \u7bc9   BETA", this.width / 2, y + 18, TEXT);
        graphics.centeredText(this.font, "\u30e2\u30fc\u30c9\u9078\u629e / \u7d20\u6750\u88dc\u5145 / \u66f4\u65b0\u7aef\u672b", this.width / 2, y + 34, CYAN);
        graphics.centeredText(this.font, "\u30d9\u30fc\u30bf\u7248: \u505c\u6b62\u6642\u306f\u81ea\u52d5\u8a3a\u65ad\u3067\u88dc\u5145/\u4f5c\u6210/\u518d\u521d\u671f\u5316\u3092\u8a66\u3057\u307e\u3059\u3002\u30ad\u30fc\u306fMinecraft\u306e\u64cd\u4f5c\u8a2d\u5b9a\u304b\u3089\u5272\u308a\u5f53\u3066\u3067\u304d\u307e\u3059\u3002", this.width / 2, y + 52, DIM);

        drawChip(graphics, x + 24, y + 72, "\u81ea\u52d5\u88dc\u5145", AutoBuilderConfig.autoFetchMaterials());
        drawChip(graphics, x + 182, y + 72, "\u88dc\u5145\u5f8c\u518d\u958b", AutoBuilderConfig.startBuildAfterFetch());
        drawChip(graphics, x + 340, y + 72, "\u81ea\u52d5\u4f5c\u6210", AutoBuilderConfig.autoCraftMaterials());
        drawSubstituteChip(graphics, x + 498, y + 72);
        graphics.text(this.font, "\u30e2\u30fc\u30c9: " + modeNameJa() + " / \u7d20\u6750\u7bb1: " + AutoBuilderConfig.materialChestCount(), x + 24, y + 94, modeColor());
        graphics.text(this.font, "\u5efa\u7bc9\u9806\u5e8f: " + buildOrderText(), x + 182, y + 94, AutoBuilderConfig.topDownBuild() ? CYAN : ORANGE);
        graphics.text(this.font, "Baritone: " + statusJa(BaritoneBridge.status()), x + 360, y + 94, BaritoneBridge.isAvailable() ? GREEN : RED);

        int sx = x + 24;
        int sy = y + 108;
        switch (page) {
            case SETTINGS -> drawSettingsPage(graphics, sx, sy, panelWidth - 48);
            case CHESTS -> drawChestsPage(graphics, sx, sy, panelWidth - 48);
            case MATERIALS -> drawMaterialsPage(graphics, sx, sy, panelWidth - 48);
            default -> {
                section(graphics, sx, sy, panelWidth - 48, 84, "01  \u30e2\u30fc\u30c9\uff08\u81ea\u52d5\u5efa\u7bc9\u306f\u30d9\u30fc\u30bf\u7248\uff09", "\u914d\u7f6e\u6e08\u307f\u8a2d\u8a08\u56f3\u306e\u81ea\u52d5\u5efa\u7bc9\u3001\u307e\u305f\u306fBaritone\u6574\u5730\u30e2\u30fc\u30c9\u3092\u958b\u59cb\u3057\u307e\u3059");
                section(graphics, sx, sy + 96, panelWidth - 48, 134, "02  \u7d20\u6750\u88dc\u5145", "\u7d20\u6750\u30c1\u30a7\u30b9\u30c8\u3068\u6574\u5730\u4fdd\u7ba1\u30c1\u30a7\u30b9\u30c8\u304b\u3089\u5c11\u91cf\u305a\u3064\u88dc\u5145\u3057\u3001\u4e0d\u8db3\u5206\u306f\u4f5c\u6210/\u7cbe\u932c\u3057\u307e\u3059");
                section(graphics, sx, sy + 242, panelWidth - 48, 74, "03  \u7ba1\u7406", "\u5efa\u7bc9\u9806\u5e8f\u3001\u6728\u6750/\u8272\u7d20\u6750\u306e\u4ee3\u7528\u3001\u7d20\u6750\u767b\u9332\u3001\u66f4\u65b0\u78ba\u8a8d\u3067\u3059");
            }
        }

        graphics.text(this.font, "\u8a2d\u8a08\u56f3: " + statusJa(BaritoneBridge.openSchematicStatus()), x + 24, y + panelHeight - 58, DIM);
        graphics.text(this.font, "\u81ea\u52d5\u5efa\u7bc9: " + statusJa(AutoBuildController.status()), x + 24, y + panelHeight - 42, statusColor(AutoBuildController.status()));
        graphics.text(this.font, "\u7d20\u6750\u88dc\u5145: " + statusJa(MaterialChestProcess.status()), x + 24, y + panelHeight - 26, statusColor(MaterialChestProcess.status()));
        graphics.text(this.font, "\u66f4\u65b0: " + statusJa(AutoUpdater.status()), x + 360, y + panelHeight - 26, DIM);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    private Button button(int x, int y, int width, String label, Runnable action) {
        return Button.builder(Component.literal(label), button -> action.run()).bounds(x, y, width, 24).build();
    }

    private void refresh() {
        clearWidgets();
        init();
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
        AutoBuildController.message("Baritone\u306e\u6574\u5730\u30e1\u30cb\u30e5\u30fc\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093", ChatFormatting.YELLOW);
        refresh();
    }

    private static String pauseLabel() {
        return AutoBuildController.isPaused() ? "\u73fe\u5728\u306e\u4f5c\u696d\u3092\u518d\u958b" : "\u73fe\u5728\u306e\u4f5c\u696d\u3092\u4e00\u6642\u505c\u6b62";
    }

    private static String autoFetchLabel() {
        return "\u7d20\u6750\u81ea\u52d5\u88dc\u5145: " + onOff(AutoBuilderConfig.autoFetchMaterials());
    }

    private static String autoResumeLabel() {
        return "\u88dc\u5145\u5f8c\u81ea\u52d5\u518d\u958b: " + onOff(AutoBuilderConfig.startBuildAfterFetch());
    }

    private static String autoCraftLabel() {
        return "\u4e0d\u8db3\u7d20\u6750\u3092\u81ea\u52d5\u4f5c\u6210: " + onOff(AutoBuilderConfig.autoCraftMaterials());
    }

    private static String topDownLabel() {
        return "\u5efa\u7bc9\u9806\u5e8f: " + buildOrderText();
    }

    private static String substituteLabel() {
        return "\u7d20\u6750\u4ee3\u7528: " + onOff(AutoBuilderConfig.autoSubstituteMaterials());
    }

    private static String hudEnabledLabel() {
        return "HUD\u8868\u793a: " + onOff(AutoBuilderConfig.hudEnabled());
    }

    private static String hudModeLabel() {
        return "HUD\u8868\u793a\u5f62\u5f0f: " + (AutoBuilderConfig.hudDetailed() ? "detailed" : "compact");
    }

    private static String hudPositionLabel() {
        return "HUD\u4f4d\u7f6e: " + AutoBuilderConfig.hudPosition().name();
    }

    private static String buildOrderText() {
        return AutoBuilderConfig.topDownBuild() ? "\u4e0a\u304b\u3089" : "\u4e0b\u304b\u3089";
    }

    private static String materialChestRegisterLabel() {
        return MaterialChestProcess.isRegisteringChests()
                ? "\u7d20\u6750\u30c1\u30a7\u30b9\u30c8\u767b\u9332\u3092\u7d42\u4e86"
                : "\u7d20\u6750\u30c1\u30a7\u30b9\u30c8\u3092\u767b\u9332";
    }

    private static String onOff(boolean value) {
        return value ? "\u6709\u52b9" : "\u7121\u52b9";
    }

    private static String modeNameJa() {
        return switch (AutoBuildController.modeName()) {
            case "BUILDING" -> "\u5efa\u7bc9\u4e2d";
            case "FETCHING" -> "\u7d20\u6750\u88dc\u5145\u4e2d";
            case "CRAFTING" -> "\u7d20\u6750\u4f5c\u6210\u4e2d";
            case "SMELTING" -> "\u7d20\u6750\u7cbe\u932c\u4e2d";
            case "DIAGNOSING" -> "\u81ea\u52d5\u8a3a\u65ad\u4e2d";
            case "WAITING_FOR_MATERIALS" -> "\u7d20\u6750\u5f85\u3061";
            case "PAUSED" -> "\u4e00\u6642\u505c\u6b62\u4e2d";
            case "COMPLETE" -> "\u5b8c\u4e86";
            default -> "\u5f85\u6a5f\u4e2d";
        };
    }

    private static String statusJa(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.contains("\u8cc7\u6750")) {
            return value;
        }
        if (value.contains("Baritone not found")) {
            return "Baritone\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093";
        }
        if (value.contains("Builder process not found")) {
            return "\u5efa\u7bc9\u30d7\u30ed\u30bb\u30b9\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093";
        }
        if (value.contains("Builder paused") || value.equals("Paused")) {
            return "\u4e00\u6642\u505c\u6b62\u4e2d";
        }
        if (value.contains("Builder active") || value.equals("Building") || value.contains("Building from placed schematic")) {
            return "\u5efa\u7bc9\u4e2d";
        }
        if (value.contains("Builder idle") || value.equals("Idle")) {
            return "\u5f85\u6a5f\u4e2d";
        }
        if (value.contains("ready")) {
            return "\u8a2d\u8a08\u56f3\u3092\u691c\u51fa\u6e08\u307f";
        }
        if (value.contains("No placed")) {
            return "\u914d\u7f6e\u6e08\u307f\u306e\u8a2d\u8a08\u56f3\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093";
        }
        if (value.contains("Fetching") || value.contains("Going to material chest") || value.contains("Walking to material chest")) {
            return "\u7d20\u6750\u30c1\u30a7\u30b9\u30c8\u3078\u79fb\u52d5\u4e2d";
        }
        if (value.contains("Opening material chest")) {
            return "\u7d20\u6750\u30c1\u30a7\u30b9\u30c8\u3092\u958b\u3044\u3066\u3044\u307e\u3059";
        }
        if (value.contains("Taking build materials")) {
            return "\u5efa\u7bc9\u7d20\u6750\u3092\u53d6\u5f97\u4e2d";
        }
        if (value.contains("Crafting")) {
            return "\u7d20\u6750\u3092\u4f5c\u6210\u4e2d";
        }
        if (value.contains("Smelting")) {
            return "\u7d20\u6750\u3092\u7cbe\u932c\u4e2d";
        }
        if (value.contains("Fetched")) {
            return "\u7d20\u6750\u88dc\u5145\u6e08\u307f";
        }
        if (value.contains("complete")) {
            return "\u5b8c\u4e86";
        }
        return value;
    }

    private int modeColor() {
        if (AutoBuildController.isPaused()) {
            return ORANGE;
        }
        if (AutoBuildController.isRunning()) {
            return GREEN;
        }
        return DIM;
    }

    private int statusColor(String value) {
        if (value != null && (value.contains("Shortage") || value.contains("\u8cc7\u6750") || value.contains("Missing"))) {
            return RED;
        }
        return DIM;
    }

    private void section(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String title, String note) {
        graphics.fill(x, y, x + width, y + height, BLOCK);
        border(graphics, x, y, width, height, 0x6619EAF2);
        graphics.text(this.font, title, x + 10, y + 7, CYAN);
        graphics.text(this.font, note, x + 10, y + 20, DIM);
    }

    private void drawSettingsPage(GuiGraphicsExtractor graphics, int x, int y, int width) {
        section(graphics, x, y, width, 270, "HUD  \u72b6\u614bUI\u8a2d\u5b9a", "\u30b2\u30fc\u30e0\u4e2d\u306e\u5de6\u4e0b\u306a\u3069\u306b\u8868\u793a\u3059\u308bAutoBuilder\u72b6\u614bHUD\u3067\u3059");
        graphics.text(this.font, "\u73fe\u5728: " + hudEnabledLabel() + " / " + hudModeLabel(), x + 10, y + 42, AutoBuilderConfig.hudEnabled() ? GREEN : RED);
        graphics.text(this.font, "\u4f4d\u7f6e: " + AutoBuilderConfig.hudPosition() + "  X:" + AutoBuilderConfig.hudXOffset() + "  Y:" + AutoBuilderConfig.hudYOffset(), x + 10, y + 58, CYAN);
        graphics.text(this.font, "\u80cc\u666f\u900f\u660e\u5ea6: " + AutoBuilderConfig.hudOpacity() + " / \u6587\u5b57: " + AutoBuilderConfig.hudTextScalePercent() + "%", x + 10, y + 74, DIM);
        graphics.text(this.font, "\u8868\u793a\u9805\u76ee: \u7d20\u6750\u4e0d\u8db3=" + onOff(AutoBuilderConfig.hudShowMissingMaterials())
                + " Baritone=" + onOff(AutoBuilderConfig.hudShowBaritoneStatus())
                + " Target=" + onOff(AutoBuilderConfig.hudShowTarget()), x + 10, y + 90, DIM);
        AutoBuilderStatusSnapshot snapshot = AutoBuildController.statusSnapshot();
        graphics.text(this.font, "\u30d7\u30ec\u30d3\u30e5\u30fc: " + snapshot.state() + " / \u9032\u6357 " + String.format(java.util.Locale.ROOT, "%.1f%%", snapshot.progress())
                + " / \u6b8b\u308a " + snapshot.remainingBlocks(), x + 10, y + 106, statusColor(snapshot.state()));
    }

    private void drawChestsPage(GuiGraphicsExtractor graphics, int x, int y, int width) {
        section(graphics, x, y, width, 270, "MATERIAL CHESTS  \u7d20\u6750\u30c1\u30a7\u30b9\u30c8", "\u767b\u9332\u6e08\u307f\u306e\u7d20\u6750\u30c1\u30a7\u30b9\u30c8\u3067\u3059\u3002\u30dc\u30bf\u30f3\u3067\u500b\u5225\u524a\u9664\u3067\u304d\u307e\u3059");
        List<BlockPos> chests = AutoBuilderConfig.materialChests();
        graphics.text(this.font, "\u767b\u9332\u6570: " + chests.size(), x + 10, y + 42, chests.isEmpty() ? ORANGE : GREEN);
        int ly = y + 60;
        int index = 1;
        for (BlockPos pos : chests.subList(0, Math.min(8, chests.size()))) {
            graphics.text(this.font, String.format(java.util.Locale.ROOT, "%02d  x:%d y:%d z:%d", index, pos.getX(), pos.getY(), pos.getZ()), x + 10, ly, TEXT);
            ly += 18;
            index++;
        }
        if (chests.size() > 8) {
            graphics.text(this.font, "... +" + (chests.size() - 8), x + 10, ly, DIM);
        }
    }

    private void drawMaterialsPage(GuiGraphicsExtractor graphics, int x, int y, int width) {
        section(graphics, x, y, width, 270, "MATERIAL ANALYSIS  \u5fc5\u8981/\u4e0d\u8db3\u7d20\u6750", "\u76f4\u8fd1\u306eBaritone\u5efa\u7bc9\u304c\u8981\u6c42\u3057\u305f\u7d20\u6750\u5019\u88dc\u3067\u3059");
        AutoBuilderStatusSnapshot snapshot = AutoBuildController.statusSnapshot();
        graphics.text(this.font, "\u4e0d\u8db3\u5019\u88dc: " + snapshot.missingMaterialTypes() + "\u7a2e / \u7d20\u6750\u30c1\u30a7\u30b9\u30c8: " + snapshot.materialChestCount(), x + 10, y + 42, snapshot.missingMaterialTypes() > 0 ? ORANGE : GREEN);
        int ly = y + 64;
        int index = 1;
        for (String item : AutoBuildController.neededMaterialSummaries(10)) {
            graphics.text(this.font, String.format(java.util.Locale.ROOT, "%02d  %s", index, item), x + 10, ly, TEXT);
            ly += 16;
            index++;
        }
        if (index == 1) {
            graphics.text(this.font, "\u307e\u3060\u5fc5\u8981\u7d20\u6750\u60c5\u5831\u304c\u3042\u308a\u307e\u305b\u3093\u3002\u5efa\u7bc9\u958b\u59cb\u5f8c\u306b\u66f4\u65b0\u3055\u308c\u307e\u3059\u3002", x + 10, ly, DIM);
        }
    }

    private void drawChip(GuiGraphicsExtractor graphics, int x, int y, String label, boolean on) {
        int color = on ? GREEN : RED;
        graphics.fill(x, y, x + 146, y + 16, on ? 0x3332FF88 : 0x33FF5548);
        border(graphics, x, y, 146, 16, color);
        graphics.text(this.font, label + ": " + onOff(on), x + 6, y + 4, color);
    }

    private void drawSubstituteChip(GuiGraphicsExtractor graphics, int x, int y) {
        boolean on = AutoBuilderConfig.autoSubstituteMaterials();
        int color = on ? GREEN : RED;
        graphics.fill(x, y, x + 146, y + 16, on ? 0x3332FF88 : 0x33FF5548);
        border(graphics, x, y, 146, 16, color);
        graphics.text(this.font, "\u7d20\u6750\u4ee3\u7528: " + onOff(on), x + 6, y + 4, color);
    }

    private void drawGrid(GuiGraphicsExtractor graphics, int step, int color) {
        for (int gx = 0; gx < this.width; gx += step) {
            graphics.fill(gx, 0, gx + 1, this.height, color);
        }
        for (int gy = 0; gy < this.height; gy += step) {
            graphics.fill(0, gy, this.width, gy + 1, color);
        }
    }

    private int panelY() {
        return Math.max(18, (this.height - panelHeight()) / 2);
    }

    private static int panelHeight() {
        return 460;
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
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
