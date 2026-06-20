package com.kanzakideath.schematicautobuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

public final class AutoBuilderDiagnostics {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static String lastExportPath = "";

    private AutoBuilderDiagnostics() {}

    public static String lastExportPath() {
        return lastExportPath == null ? "" : lastExportPath;
    }

    public static String export(String reason) {
        if (!AutoBuilderConfig.diagnosisLogEnabled()) {
            return "";
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return "";
        }
        Path directory = minecraft.gameDirectory.toPath().resolve("autobuilder").resolve("diagnostics");
        String fileName = "diagnosis-" + FILE_STAMP.format(LocalDateTime.now()) + ".txt";
        Path output = directory.resolve(fileName);
        try {
            Files.createDirectories(directory);
            Files.writeString(output, body(reason), StandardCharsets.UTF_8);
            lastExportPath = output.toAbsolutePath().toString();
            return lastExportPath;
        } catch (IOException ignored) {
            return "";
        }
    }

    private static String body(String reason) {
        AutoBuilderStatusSnapshot snapshot = AutoBuildController.statusSnapshot();
        BaritoneBridge.BuildStats stats = BaritoneBridge.buildStats();
        Set<Item> neededItems = BaritoneBridge.currentNeededBuildItems();
        List<String> materialPlan = BaritoneBridge.materialPlanSummaries(32);
        List<String> unfinished = BaritoneBridge.unfinishedBlockSummaries(32);

        StringBuilder builder = new StringBuilder();
        builder.append("Schematic AutoBuilder diagnosis").append('\n');
        builder.append("time=").append(LocalDateTime.now()).append('\n');
        builder.append("reason=").append(nullToBlank(reason)).append('\n');
        builder.append("mode=").append(AutoBuildController.modeName()).append('\n');
        builder.append("status=").append(AutoBuildController.status()).append('\n');
        builder.append("hudMode=").append(snapshot.mode()).append('\n');
        builder.append("hudState=").append(snapshot.state()).append('\n');
        builder.append("hudAction=").append(snapshot.action()).append('\n');
        builder.append("progress=").append(snapshot.progress()).append('\n');
        builder.append("totalBlocks=").append(stats.totalBlocks()).append('\n');
        builder.append("doneBlocks=").append(stats.doneBlocks()).append('\n');
        builder.append("remainingBlocks=").append(stats.remainingBlocks()).append('\n');
        builder.append("failedBlocks=").append(stats.failedBlocks()).append('\n');
        builder.append("unreachableBlocks=").append(stats.unreachableBlocks()).append('\n');
        builder.append("target=").append(snapshot.target()).append('\n');
        builder.append("baritone=").append(BaritoneBridge.hudStatus()).append('\n');
        builder.append("openSchematic=").append(BaritoneBridge.openSchematicStatus()).append('\n');
        builder.append("schematicFile=").append(BaritoneBridge.schematicFileStatus()).append('\n');
        builder.append("safetyMode=").append(AutoBuilderConfig.safetyMode()).append('\n');
        builder.append("dryRun=").append(AutoBuilderConfig.dryRunMode()).append('\n');
        builder.append("autoFetch=").append(AutoBuilderConfig.autoFetchMaterials()).append('\n');
        builder.append("autoCraft=").append(AutoBuilderConfig.autoCraftMaterials()).append('\n');
        builder.append("autoSubstitute=").append(AutoBuilderConfig.autoSubstituteMaterials()).append('\n');
        builder.append("topDown=").append(AutoBuilderConfig.topDownBuild()).append('\n');
        builder.append("materialChestCount=").append(AutoBuilderConfig.materialChestCount()).append('\n');
        builder.append("materialChests=").append(chestList()).append('\n');
        builder.append("neededItems=").append(itemList(neededItems)).append('\n');
        builder.append('\n').append("Material plan").append('\n');
        appendList(builder, materialPlan);
        builder.append('\n').append("Unfinished blocks").append('\n');
        appendList(builder, unfinished);
        return builder.toString();
    }

    private static String chestList() {
        StringJoiner joiner = new StringJoiner("; ");
        for (BlockPos pos : AutoBuilderConfig.materialChests()) {
            joiner.add(pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }
        return joiner.toString();
    }

    private static String itemList(Set<Item> items) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Item item : items) {
            joiner.add(String.valueOf(item));
        }
        return joiner.toString();
    }

    private static void appendList(StringBuilder builder, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            builder.append("- none").append('\n');
            return;
        }
        for (String line : lines) {
            builder.append("- ").append(line).append('\n');
        }
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
