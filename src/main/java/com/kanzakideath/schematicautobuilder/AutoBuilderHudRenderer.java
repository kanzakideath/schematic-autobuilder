package com.kanzakideath.schematicautobuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AutoBuilderHudRenderer {

    private static final int WHITE = 0xFFEAFDFF;
    private static final int DIM = 0xFFB8C7CC;
    private static final int CYAN = 0xFF35EAF2;
    private static final int GREEN = 0xFF66FF9A;
    private static final int YELLOW = 0xFFFFD45A;
    private static final int ORANGE = 0xFFFFA042;
    private static final int RED = 0xFFFF5548;
    private static final int BLUE = 0xFF6FA8FF;

    private AutoBuilderHudRenderer() {}

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null || !AutoBuilderConfig.hudEnabled()) {
            return;
        }

        AutoBuilderStatusSnapshot snapshot = AutoBuildController.statusSnapshot();
        boolean active = AutoBuildController.isRunning() || MaterialChestProcess.isRegisteringChests();
        if (!active && !AutoBuilderConfig.hudDetailed() && !AutoBuilderConfig.hudShowDebug()) {
            return;
        }

        Font font = minecraft.font;
        List<HudLine> lines = AutoBuilderConfig.hudDetailed() ? detailedLines(snapshot) : compactLines(snapshot);
        if (lines.isEmpty()) {
            return;
        }

        int padding = 6;
        int lineHeight = Math.max(9, AutoBuilderConfig.hudTextScalePercent() / 10);
        int width = 0;
        for (HudLine line : lines) {
            width = Math.max(width, font.width(line.text()));
        }
        width += padding * 2;
        int height = padding * 2 + lines.size() * lineHeight;
        int[] xy = panelPosition(minecraft, width, height);
        int x = xy[0];
        int y = xy[1];
        int alpha = AutoBuilderConfig.hudOpacity() & 0xFF;
        int bg = (alpha << 24) | 0x000000;
        int border = stateColor(snapshot.state());

        graphics.fill(x, y, x + width, y + height, bg);
        border(graphics, x, y, width, height, border);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, fade(border, 120));

        int ty = y + padding;
        for (HudLine line : lines) {
            graphics.text(font, line.text(), x + padding, ty, line.color());
            ty += lineHeight;
        }
    }

    private static List<HudLine> compactLines(AutoBuilderStatusSnapshot snapshot) {
        List<HudLine> lines = new ArrayList<>();
        lines.add(new HudLine("AutoBuilder: " + snapshot.state(), stateColor(snapshot.state())));
        lines.add(new HudLine("進捗: " + percent(snapshot.progress()) + " | 残り: " + snapshot.remainingBlocks(), WHITE));
        if (AutoBuilderConfig.hudShowMissingMaterials()) {
            lines.add(new HudLine("素材不足: " + missingText(snapshot) + " | Chest: " + snapshot.materialChestCount(), missingColor(snapshot)));
        }
        if (AutoBuilderConfig.hudShowBaritoneStatus()) {
            lines.add(new HudLine("Baritone: " + snapshot.baritoneStatus(), DIM));
        }
        if (AutoBuilderConfig.hudClickControls()) {
            lines.add(new HudLine("操作: メニュー/一時停止キーで制御", DIM));
        }
        return lines;
    }

    private static List<HudLine> detailedLines(AutoBuilderStatusSnapshot snapshot) {
        List<HudLine> lines = new ArrayList<>();
        lines.add(new HudLine("AutoBuilder Status", CYAN));
        lines.add(new HudLine("MODE: " + snapshot.mode(), WHITE));
        lines.add(new HudLine("STATE: " + snapshot.state(), stateColor(snapshot.state())));
        lines.add(new HudLine("ACTION: " + snapshot.action(), DIM));
        lines.add(new HudLine("PROGRESS: " + percent(snapshot.progress()), WHITE));
        lines.add(new HudLine("DONE: " + snapshot.doneBlocks() + " / " + snapshot.totalBlocks(), WHITE));
        lines.add(new HudLine("REMAINING: " + snapshot.remainingBlocks(), WHITE));
        lines.add(new HudLine("FAILED: " + snapshot.failedBlocks(), snapshot.failedBlocks() > 0 ? ORANGE : DIM));
        lines.add(new HudLine("UNREACHABLE: " + snapshot.unreachableBlocks(), snapshot.unreachableBlocks() > 0 ? ORANGE : DIM));
        if (AutoBuilderConfig.hudShowMissingMaterials()) {
            lines.add(new HudLine("MISSING: " + missingText(snapshot), missingColor(snapshot)));
        }
        lines.add(new HudLine("CHESTS: " + snapshot.materialChestCount() + " registered", DIM));
        if (AutoBuilderConfig.hudShowBaritoneStatus()) {
            lines.add(new HudLine("BARITONE: " + snapshot.baritoneStatus(), DIM));
        }
        if (AutoBuilderConfig.hudShowTarget() && !snapshot.target().isBlank()) {
            lines.add(new HudLine("TARGET: " + snapshot.target(), CYAN));
        }
        if (AutoBuilderConfig.hudShowEta()) {
            lines.add(new HudLine("ETA: " + snapshot.eta(), DIM));
        }
        if (AutoBuilderConfig.hudShowDebug()) {
            lines.add(new HudLine("DEBUG: updated " + snapshot.updatedAtMs(), 0xFF8CFFEF));
        }
        if (AutoBuilderConfig.hudClickControls()) {
            lines.add(new HudLine("CONTROL: menu / pause / stop keys", DIM));
        }
        return lines;
    }

    private static int[] panelPosition(Minecraft minecraft, int width, int height) {
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        AutoBuilderConfig.HudPosition position = AutoBuilderConfig.hudPosition();
        int offsetX = AutoBuilderConfig.hudXOffset();
        int offsetY = AutoBuilderConfig.hudYOffset();
        int x = switch (position) {
            case LEFT_BOTTOM, LEFT_TOP -> offsetX;
            case RIGHT_BOTTOM, RIGHT_TOP -> screenWidth - width - offsetX;
        };
        int y = switch (position) {
            case LEFT_TOP, RIGHT_TOP -> offsetY;
            case LEFT_BOTTOM, RIGHT_BOTTOM -> screenHeight - height - offsetY;
        };
        x = Math.max(2, Math.min(screenWidth - width - 2, x));
        y = Math.max(2, Math.min(screenHeight - height - 2, y));
        return new int[]{x, y};
    }

    private static int stateColor(String state) {
        if (state == null) {
            return DIM;
        }
        if (state.contains("実行") || state.contains("補充") || state.contains("作成") || state.contains("登録")) {
            return GREEN;
        }
        if (state.contains("一時停止")) {
            return YELLOW;
        }
        if (state.contains("不足")) {
            return ORANGE;
        }
        if (state.contains("エラー")) {
            return RED;
        }
        if (state.contains("完了")) {
            return BLUE;
        }
        return CYAN;
    }

    private static int missingColor(AutoBuilderStatusSnapshot snapshot) {
        return snapshot.missingMaterialTypes() > 0 ? ORANGE : DIM;
    }

    private static String missingText(AutoBuilderStatusSnapshot snapshot) {
        if (snapshot.missingMaterialTypes() <= 0) {
            return "0種";
        }
        if (snapshot.missingMaterialItems() < 0) {
            return snapshot.missingMaterialTypes() + "種";
        }
        return snapshot.missingMaterialTypes() + "種 / " + snapshot.missingMaterialItems() + "個";
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static int fade(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private record HudLine(String text, int color) {}
}
