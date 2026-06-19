package com.kanzakideath.schematicautobuilder;

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

    public AutoBuilderScreen() {
        super(Component.literal("Schematic Auto Builder"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(620, this.width - 36);
        int x = (this.width - panelWidth) / 2;
        int y = Math.max(28, (this.height - 356) / 2);
        int row = 24;
        int gap = 6;
        int buttonY = y + 108;
        int full = panelWidth - 48;
        int half = (full - 6) / 2;

        addRenderableWidget(button(x + 24, buttonY, full, "START FULL AUTO BUILD", () -> {
            AutoBuildController.startFullAutoBuild();
            refresh();
        }));

        addRenderableWidget(button(x + 24, buttonY + (row + gap), full, pauseLabel(), () -> {
            AutoBuildController.togglePause();
            refresh();
        }));

        addRenderableWidget(button(x + 24, buttonY + (row + gap) * 2, half, "REGISTER TARGET CHEST", () -> {
            MaterialChestProcess.registerLookedAtChest(Minecraft.getInstance());
            refresh();
        }));

        addRenderableWidget(button(x + 30 + half, buttonY + (row + gap) * 2, half, "FETCH MATERIALS NOW", () -> {
            AutoBuildController.fetchMaterialsOnly();
            refresh();
        }));

        addRenderableWidget(button(x + 24, buttonY + (row + gap) * 3, half, autoFetchLabel(), () -> {
            AutoBuilderConfig.toggleAutoFetchMaterials();
            refresh();
        }));

        addRenderableWidget(button(x + 30 + half, buttonY + (row + gap) * 3, half, autoResumeLabel(), () -> {
            AutoBuilderConfig.toggleStartBuildAfterFetch();
            refresh();
        }));

        addRenderableWidget(button(x + 24, buttonY + (row + gap) * 4, half, "CLEAR CHEST LIST", () -> {
            AutoBuilderConfig.clearMaterialChests();
            refresh();
        }));

        addRenderableWidget(button(x + 30 + half, buttonY + (row + gap) * 4, half, "CHECK UPDATE", () -> {
            AutoUpdater.checkNow();
            refresh();
        }));

        addRenderableWidget(button(x + 24, buttonY + (row + gap) * 5, full, "CLOSE", () -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(null);
            }
        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        extractTransparentBackground(graphics);
        int panelWidth = Math.min(620, this.width - 36);
        int panelHeight = 356;
        int x = (this.width - panelWidth) / 2;
        int y = Math.max(28, (this.height - panelHeight) / 2);

        graphics.fill(0, 0, this.width, this.height, 0xD8000508);
        drawGrid(graphics, 28, 0x2219EAF2);
        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL);
        border(graphics, x, y, panelWidth, panelHeight, CYAN);
        graphics.fill(x + 10, y + 10, x + panelWidth - 10, y + panelHeight - 10, 0x66000000);
        border(graphics, x + 10, y + 10, panelWidth - 20, panelHeight - 20, 0x6619EAF2);

        graphics.centeredText(this.font, "S C H E M A T I C   A U T O   B U I L D E R", this.width / 2, y + 18, TEXT);
        graphics.centeredText(this.font, "BARITONE / LITEMATICA CONTROL TERMINAL", this.width / 2, y + 34, CYAN);
        graphics.centeredText(this.font, "Keys are configurable in Minecraft Controls: Open Menu, Pause Automation", this.width / 2, y + 52, DIM);

        drawChip(graphics, x + 24, y + 72, "AUTO REFILL", AutoBuilderConfig.autoFetchMaterials());
        drawChip(graphics, x + 170, y + 72, "AUTO RESUME", AutoBuilderConfig.startBuildAfterFetch());
        drawChip(graphics, x + 316, y + 72, "CHESTS " + AutoBuilderConfig.materialChestCount(), AutoBuilderConfig.materialChestCount() > 0);
        graphics.text(this.font, "MODE: " + AutoBuildController.modeName(), x + 24, y + 92, modeColor());
        graphics.text(this.font, "BARITONE: " + BaritoneBridge.status(), x + 170, y + 92, BaritoneBridge.isAvailable() ? GREEN : RED);

        graphics.text(this.font, "SCHEMATIC: " + BaritoneBridge.openSchematicStatus(), x + 24, y + panelHeight - 58, DIM);
        graphics.text(this.font, "AUTO: " + AutoBuildController.status(), x + 24, y + panelHeight - 42, DIM);
        graphics.text(this.font, "REFILL: " + MaterialChestProcess.status(), x + 24, y + panelHeight - 26, DIM);
        graphics.text(this.font, "UPDATE: " + AutoUpdater.status(), x + 314, y + panelHeight - 26, DIM);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    private Button button(int x, int y, int width, String label, Runnable action) {
        return Button.builder(Component.literal(label), button -> action.run()).bounds(x, y, width, 24).build();
    }

    private void refresh() {
        clearWidgets();
        init();
    }

    private static String pauseLabel() {
        return AutoBuildController.isPaused() ? "RESUME AUTOMATION" : "PAUSE AUTOMATION";
    }

    private static String autoFetchLabel() {
        return "AUTO REFILL: " + (AutoBuilderConfig.autoFetchMaterials() ? "ON" : "OFF");
    }

    private static String autoResumeLabel() {
        return "AUTO RESUME: " + (AutoBuilderConfig.startBuildAfterFetch() ? "ON" : "OFF");
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

    private void drawChip(GuiGraphicsExtractor graphics, int x, int y, String label, boolean on) {
        int color = on ? GREEN : RED;
        graphics.fill(x, y, x + 128, y + 16, on ? 0x3332FF88 : 0x33FF5548);
        border(graphics, x, y, 128, 16, color);
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

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
