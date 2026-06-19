package com.kanzakideath.schematicautobuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AutoBuilderScreen extends Screen {

    private static final int PANEL = 0xDD061015;
    private static final int CYAN = 0xFF19EAF2;
    private static final int GREEN = 0xFF72FFAA;
    private static final int ORANGE = 0xFFFFB545;
    private static final int RED = 0xFFFF5548;
    private static final int TEXT = 0xFFEAFDFF;
    private static final int DIM = 0xFF8DB9C1;
    private static final int BLOCK = 0x66000000;

    public AutoBuilderScreen() {
        super(Component.literal("Schematic Auto Builder"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(680, this.width - 36);
        int x = (this.width - panelWidth) / 2;
        int y = panelY();
        int full = panelWidth - 48;
        int half = (full - 8) / 2;
        int row = 24;
        int gap = 6;
        int by = y + 134;

        addRenderableWidget(button(x + 24, by, half, "AUTO BUILD MODE: START", () -> {
            AutoBuildController.startFullAutoBuild();
            refresh();
        }));
        addRenderableWidget(button(x + 32 + half, by, half, "CLEAR AREA MODE: BARITONE", this::openBaritoneClearMode));
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
        addRenderableWidget(button(x + 32 + half, by, half, "FETCH MATERIALS NOW", () -> {
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
        by += row + gap + 20;

        addRenderableWidget(button(x + 24, by, half, "CLEAR MATERIAL CHESTS", () -> {
            AutoBuilderConfig.clearMaterialChests();
            refresh();
        }));
        addRenderableWidget(button(x + 32 + half, by, half, "CHECK UPDATE", () -> {
            AutoUpdater.checkNow();
            refresh();
        }));
        by += row + gap;

        addRenderableWidget(button(x + 24, by, full, "CLOSE", () -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(null);
            }
        }));
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

        graphics.centeredText(this.font, "S C H E M A T I C   A U T O   B U I L D E R", this.width / 2, y + 18, TEXT);
        graphics.centeredText(this.font, "MODE SELECT / MATERIAL REFILL / UPDATE TERMINAL", this.width / 2, y + 34, CYAN);
        graphics.centeredText(this.font, "Assign keys in Minecraft Controls. No default J binding is used.", this.width / 2, y + 52, DIM);

        drawChip(graphics, x + 24, y + 72, "AUTO REFILL", AutoBuilderConfig.autoFetchMaterials());
        drawChip(graphics, x + 182, y + 72, "AUTO RESUME", AutoBuilderConfig.startBuildAfterFetch());
        drawChip(graphics, x + 340, y + 72, "AUTO CRAFT", AutoBuilderConfig.autoCraftMaterials());
        drawChip(graphics, x + 498, y + 72, "CHESTS " + AutoBuilderConfig.materialChestCount(), AutoBuilderConfig.materialChestCount() > 0);
        graphics.text(this.font, "MODE: " + AutoBuildController.modeName(), x + 24, y + 94, modeColor());
        graphics.text(this.font, "BARITONE: " + BaritoneBridge.status(), x + 182, y + 94, BaritoneBridge.isAvailable() ? GREEN : RED);

        int sx = x + 24;
        int sy = y + 108;
        section(graphics, sx, sy, panelWidth - 48, 84, "01  MODE", "Auto build: placed schematic / Clear area: Baritone terrain menu");
        section(graphics, sx, sy + 96, panelWidth - 48, 104, "02  MATERIALS", "Register material chests, Baritone storage chests, refill, and crafting");
        section(graphics, sx, sy + 212, panelWidth - 48, 74, "03  MAINTENANCE", "Clear registrations, update, and close");

        graphics.text(this.font, "SCHEMATIC: " + BaritoneBridge.openSchematicStatus(), x + 24, y + panelHeight - 58, DIM);
        graphics.text(this.font, "AUTO: " + AutoBuildController.status(), x + 24, y + panelHeight - 42, statusColor(AutoBuildController.status()));
        graphics.text(this.font, "REFILL: " + MaterialChestProcess.status(), x + 24, y + panelHeight - 26, statusColor(MaterialChestProcess.status()));
        graphics.text(this.font, "UPDATE: " + AutoUpdater.status(), x + 360, y + panelHeight - 26, DIM);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    private Button button(int x, int y, int width, String label, Runnable action) {
        return Button.builder(Component.literal(label), button -> action.run()).bounds(x, y, width, 24).build();
    }

    private void refresh() {
        clearWidgets();
        init();
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
        AutoBuildController.message("Baritone clear area menu was not found", ChatFormatting.YELLOW);
        refresh();
    }

    private static String pauseLabel() {
        return AutoBuildController.isPaused() ? "RESUME CURRENT MODE" : "PAUSE CURRENT MODE";
    }

    private static String autoFetchLabel() {
        return "AUTO REFILL: " + (AutoBuilderConfig.autoFetchMaterials() ? "ON" : "OFF");
    }

    private static String autoResumeLabel() {
        return "AUTO RESUME: " + (AutoBuilderConfig.startBuildAfterFetch() ? "ON" : "OFF");
    }

    private static String autoCraftLabel() {
        return "AUTO CRAFT MISSING MATERIALS: " + (AutoBuilderConfig.autoCraftMaterials() ? "ON" : "OFF");
    }

    private static String materialChestRegisterLabel() {
        return MaterialChestProcess.isRegisteringChests()
                ? "STOP MATERIAL CHEST REGISTRATION"
                : "REGISTER MATERIAL CHESTS";
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
        if (value != null && (value.contains("Shortage") || value.contains("\u8cc7\u6750"))) {
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

    private void drawChip(GuiGraphicsExtractor graphics, int x, int y, String label, boolean on) {
        int color = on ? GREEN : RED;
        graphics.fill(x, y, x + 146, y + 16, on ? 0x3332FF88 : 0x33FF5548);
        border(graphics, x, y, 146, 16, color);
        graphics.text(this.font, label + ": " + (on ? "ON" : "OFF"), x + 6, y + 4, color);
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
        return 430;
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
