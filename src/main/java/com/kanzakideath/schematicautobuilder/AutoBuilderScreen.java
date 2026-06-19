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
        int width = Math.min(560, this.width - 36);
        int x = (this.width - width) / 2;
        int y = Math.max(44, (this.height - 310) / 2);
        int row = 24;
        int gap = 6;

        addRenderableWidget(button(x + 24, y + 72, width - 48, "設計図自動建築を開始", () -> {
            if (BaritoneBridge.startPlacedSchematicBuild() && this.minecraft != null) {
                this.minecraft.setScreenAndShow(null);
            }
        }));

        addRenderableWidget(button(x + 24, y + 72 + (row + gap), width - 48, MaterialChestProcess.isRunning() ? "素材補給を停止" : "登録チェストから素材補給", () -> {
            MaterialChestProcess.start();
            refresh();
        }));

        addRenderableWidget(button(x + 24, y + 72 + (row + gap) * 2, width - 48, "見ているチェストを素材チェスト登録", () -> {
            MaterialChestProcess.registerLookedAtChest(Minecraft.getInstance());
            refresh();
        }));

        addRenderableWidget(button(x + 24, y + 72 + (row + gap) * 3, (width - 54) / 2, autoFetchLabel(), () -> {
            AutoBuilderConfig.toggleAutoFetchMaterials();
            refresh();
        }));

        addRenderableWidget(button(x + 30 + (width - 54) / 2, y + 72 + (row + gap) * 3, (width - 54) / 2, startAfterFetchLabel(), () -> {
            AutoBuilderConfig.toggleStartBuildAfterFetch();
            refresh();
        }));

        addRenderableWidget(button(x + 24, y + 72 + (row + gap) * 4, (width - 54) / 2, "素材チェスト全削除", () -> {
            AutoBuilderConfig.clearMaterialChests();
            refresh();
        }));

        addRenderableWidget(button(x + 30 + (width - 54) / 2, y + 72 + (row + gap) * 4, (width - 54) / 2, "更新確認", () -> {
            AutoUpdater.checkNow();
            refresh();
        }));

        addRenderableWidget(button(x + 24, y + 72 + (row + gap) * 5, width - 48, "閉じる", () -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(null);
            }
        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        extractTransparentBackground(graphics);
        int width = Math.min(560, this.width - 36);
        int height = 310;
        int x = (this.width - width) / 2;
        int y = Math.max(44, (this.height - height) / 2);

        graphics.fill(0, 0, this.width, this.height, 0xD8000508);
        drawGrid(graphics, 28, 0x2219EAF2);
        graphics.fill(x, y, x + width, y + height, PANEL);
        border(graphics, x, y, width, height, CYAN);
        graphics.fill(x + 10, y + 10, x + width - 10, y + height - 10, 0x66000000);
        border(graphics, x + 10, y + 10, width - 20, height - 20, 0x6619EAF2);

        graphics.centeredText(this.font, "S C H E M A T I C   A U T O   B U I L D E R", this.width / 2, y + 22, TEXT);
        graphics.centeredText(this.font, "BARITONE / LITEMATICA CONTROL TERMINAL", this.width / 2, y + 40, CYAN);

        graphics.text(this.font, "Baritone: " + BaritoneBridge.status(), x + 24, y + 56, BaritoneBridge.isAvailable() ? GREEN : RED);
        graphics.text(this.font, "素材チェスト: " + AutoBuilderConfig.materialChestCount(), x + 220, y + 56, AutoBuilderConfig.materialChestCount() > 0 ? GREEN : ORANGE);
        graphics.text(this.font, "補給状態: " + MaterialChestProcess.status(), x + 24, y + height - 42, DIM);
        graphics.text(this.font, "更新: " + AutoUpdater.status(), x + 24, y + height - 26, DIM);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    private Button button(int x, int y, int width, String label, Runnable action) {
        return Button.builder(Component.literal(label), button -> action.run()).bounds(x, y, width, 24).build();
    }

    private void refresh() {
        clearWidgets();
        init();
    }

    private static String autoFetchLabel() {
        return "自動素材補給: " + (AutoBuilderConfig.autoFetchMaterials() ? "ON" : "OFF");
    }

    private static String startAfterFetchLabel() {
        return "補給後に開始: " + (AutoBuilderConfig.startBuildAfterFetch() ? "ON" : "OFF");
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

