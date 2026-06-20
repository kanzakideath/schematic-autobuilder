package com.kanzakideath.schematicautobuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public final class AutoBuilderConfig {

    public enum HudPosition {
        LEFT_BOTTOM,
        LEFT_TOP,
        RIGHT_BOTTOM,
        RIGHT_TOP
    }

    public enum SafetyMode {
        NORMAL,
        STABLE,
        COMPLETE
    }

    private static final List<BlockPos> MATERIAL_CHESTS = new ArrayList<>();
    private static boolean autoFetchMaterials = true;
    private static boolean autoCraftMaterials = true;
    private static boolean startBuildAfterFetch = true;
    private static boolean topDownBuild;
    private static boolean litematicaLayerSync = true;
    private static boolean autoSubstituteMaterials = true;
    private static boolean hudEnabled = true;
    private static HudPosition hudPosition = HudPosition.LEFT_BOTTOM;
    private static int hudXOffset = 8;
    private static int hudYOffset = 58;
    private static boolean hudDetailed;
    private static int hudOpacity = 150;
    private static int hudTextScalePercent = 100;
    private static boolean hudShowMissingMaterials = true;
    private static boolean hudShowBaritoneStatus = true;
    private static boolean hudShowTarget = true;
    private static boolean hudShowEta = true;
    private static boolean hudShowDebug;
    private static SafetyMode safetyMode = SafetyMode.STABLE;
    private static boolean diagnosisLogEnabled = true;
    private static boolean checkpointEnabled = true;
    private static boolean dryRunMode;
    private static boolean protectUtilityBlocks = true;
    private static boolean protectRedstoneBlocks = true;
    private static boolean hudClickControls = true;
    private static boolean showUnfinishedList = true;
    private static boolean materialPlannerEnabled = true;
    private static int keyMigrationVersion;

    private AutoBuilderConfig() {}

    public static synchronized void load() {
        MATERIAL_CHESTS.clear();
        Properties properties = new Properties();
        Path path = configPath();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            } catch (IOException ignored) {
            }
        }
        autoFetchMaterials = Boolean.parseBoolean(properties.getProperty("autoFetchMaterials", "true"));
        autoCraftMaterials = Boolean.parseBoolean(properties.getProperty("autoCraftMaterials", "true"));
        startBuildAfterFetch = Boolean.parseBoolean(properties.getProperty("startBuildAfterFetch", "true"));
        topDownBuild = Boolean.parseBoolean(properties.getProperty("topDownBuild", "false"));
        litematicaLayerSync = Boolean.parseBoolean(properties.getProperty("litematicaLayerSync", "true"));
        autoSubstituteMaterials = Boolean.parseBoolean(properties.getProperty("autoSubstituteMaterials", "true"));
        hudEnabled = Boolean.parseBoolean(properties.getProperty("hudEnabled", "true"));
        hudPosition = parseHudPosition(properties.getProperty("hudPosition", HudPosition.LEFT_BOTTOM.name()));
        hudXOffset = clamp(parseInt(properties.getProperty("hudXOffset", "8")), -400, 400);
        hudYOffset = clamp(parseInt(properties.getProperty("hudYOffset", "58")), -400, 400);
        hudDetailed = Boolean.parseBoolean(properties.getProperty("hudDetailed", "false"));
        hudOpacity = clamp(parseInt(properties.getProperty("hudOpacity", "150")), 32, 220);
        hudTextScalePercent = clamp(parseInt(properties.getProperty("hudTextScalePercent", "100")), 70, 140);
        hudShowMissingMaterials = Boolean.parseBoolean(properties.getProperty("hudShowMissingMaterials", "true"));
        hudShowBaritoneStatus = Boolean.parseBoolean(properties.getProperty("hudShowBaritoneStatus", "true"));
        hudShowTarget = Boolean.parseBoolean(properties.getProperty("hudShowTarget", "true"));
        hudShowEta = Boolean.parseBoolean(properties.getProperty("hudShowEta", "true"));
        hudShowDebug = Boolean.parseBoolean(properties.getProperty("hudShowDebug", "false"));
        safetyMode = parseSafetyMode(properties.getProperty("safetyMode", SafetyMode.STABLE.name()));
        diagnosisLogEnabled = Boolean.parseBoolean(properties.getProperty("diagnosisLogEnabled", "true"));
        checkpointEnabled = Boolean.parseBoolean(properties.getProperty("checkpointEnabled", "true"));
        dryRunMode = Boolean.parseBoolean(properties.getProperty("dryRunMode", "false"));
        protectUtilityBlocks = Boolean.parseBoolean(properties.getProperty("protectUtilityBlocks", "true"));
        protectRedstoneBlocks = Boolean.parseBoolean(properties.getProperty("protectRedstoneBlocks", "true"));
        hudClickControls = Boolean.parseBoolean(properties.getProperty("hudClickControls", "true"));
        showUnfinishedList = Boolean.parseBoolean(properties.getProperty("showUnfinishedList", "true"));
        materialPlannerEnabled = Boolean.parseBoolean(properties.getProperty("materialPlannerEnabled", "true"));
        keyMigrationVersion = parseInt(properties.getProperty("keyMigrationVersion", "0"));
        for (String encoded : properties.getProperty("materialChests", "").split(";")) {
            BlockPos pos = decodePos(encoded);
            if (pos != null && !MATERIAL_CHESTS.contains(pos)) {
                MATERIAL_CHESTS.add(pos);
            }
        }
    }

    public static synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty("autoFetchMaterials", Boolean.toString(autoFetchMaterials));
        properties.setProperty("autoCraftMaterials", Boolean.toString(autoCraftMaterials));
        properties.setProperty("startBuildAfterFetch", Boolean.toString(startBuildAfterFetch));
        properties.setProperty("topDownBuild", Boolean.toString(topDownBuild));
        properties.setProperty("litematicaLayerSync", Boolean.toString(litematicaLayerSync));
        properties.setProperty("autoSubstituteMaterials", Boolean.toString(autoSubstituteMaterials));
        properties.setProperty("hudEnabled", Boolean.toString(hudEnabled));
        properties.setProperty("hudPosition", hudPosition.name());
        properties.setProperty("hudXOffset", Integer.toString(hudXOffset));
        properties.setProperty("hudYOffset", Integer.toString(hudYOffset));
        properties.setProperty("hudDetailed", Boolean.toString(hudDetailed));
        properties.setProperty("hudOpacity", Integer.toString(hudOpacity));
        properties.setProperty("hudTextScalePercent", Integer.toString(hudTextScalePercent));
        properties.setProperty("hudShowMissingMaterials", Boolean.toString(hudShowMissingMaterials));
        properties.setProperty("hudShowBaritoneStatus", Boolean.toString(hudShowBaritoneStatus));
        properties.setProperty("hudShowTarget", Boolean.toString(hudShowTarget));
        properties.setProperty("hudShowEta", Boolean.toString(hudShowEta));
        properties.setProperty("hudShowDebug", Boolean.toString(hudShowDebug));
        properties.setProperty("safetyMode", safetyMode.name());
        properties.setProperty("diagnosisLogEnabled", Boolean.toString(diagnosisLogEnabled));
        properties.setProperty("checkpointEnabled", Boolean.toString(checkpointEnabled));
        properties.setProperty("dryRunMode", Boolean.toString(dryRunMode));
        properties.setProperty("protectUtilityBlocks", Boolean.toString(protectUtilityBlocks));
        properties.setProperty("protectRedstoneBlocks", Boolean.toString(protectRedstoneBlocks));
        properties.setProperty("hudClickControls", Boolean.toString(hudClickControls));
        properties.setProperty("showUnfinishedList", Boolean.toString(showUnfinishedList));
        properties.setProperty("materialPlannerEnabled", Boolean.toString(materialPlannerEnabled));
        properties.setProperty("keyMigrationVersion", Integer.toString(keyMigrationVersion));
        List<String> encoded = new ArrayList<>();
        for (BlockPos pos : MATERIAL_CHESTS) {
            encoded.add(encodePos(pos));
        }
        properties.setProperty("materialChests", String.join(";", encoded));
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "Schematic Auto Builder");
            }
        } catch (IOException ignored) {
        }
    }

    public static synchronized boolean autoFetchMaterials() {
        return autoFetchMaterials;
    }

    public static synchronized void toggleAutoFetchMaterials() {
        autoFetchMaterials = !autoFetchMaterials;
        save();
    }

    public static synchronized boolean autoCraftMaterials() {
        return autoCraftMaterials;
    }

    public static synchronized void toggleAutoCraftMaterials() {
        autoCraftMaterials = !autoCraftMaterials;
        save();
    }

    public static synchronized boolean startBuildAfterFetch() {
        return startBuildAfterFetch;
    }

    public static synchronized void toggleStartBuildAfterFetch() {
        startBuildAfterFetch = !startBuildAfterFetch;
        save();
    }

    public static synchronized boolean topDownBuild() {
        return topDownBuild;
    }

    public static synchronized void toggleTopDownBuild() {
        topDownBuild = !topDownBuild;
        save();
    }

    public static synchronized boolean litematicaLayerSync() {
        return litematicaLayerSync;
    }

    public static synchronized void toggleLitematicaLayerSync() {
        litematicaLayerSync = !litematicaLayerSync;
        save();
    }

    public static synchronized boolean autoSubstituteMaterials() {
        return autoSubstituteMaterials;
    }

    public static synchronized void toggleAutoSubstituteMaterials() {
        autoSubstituteMaterials = !autoSubstituteMaterials;
        save();
    }

    public static synchronized boolean hudEnabled() {
        return hudEnabled;
    }

    public static synchronized void toggleHudEnabled() {
        hudEnabled = !hudEnabled;
        save();
    }

    public static synchronized HudPosition hudPosition() {
        return hudPosition;
    }

    public static synchronized void cycleHudPosition() {
        HudPosition[] values = HudPosition.values();
        hudPosition = values[(hudPosition.ordinal() + 1) % values.length];
        save();
    }

    public static synchronized int hudXOffset() {
        return hudXOffset;
    }

    public static synchronized void adjustHudXOffset(int delta) {
        hudXOffset = clamp(hudXOffset + delta, -400, 400);
        save();
    }

    public static synchronized int hudYOffset() {
        return hudYOffset;
    }

    public static synchronized void adjustHudYOffset(int delta) {
        hudYOffset = clamp(hudYOffset + delta, -400, 400);
        save();
    }

    public static synchronized boolean hudDetailed() {
        return hudDetailed;
    }

    public static synchronized void toggleHudDetailed() {
        hudDetailed = !hudDetailed;
        save();
    }

    public static synchronized int hudOpacity() {
        return hudOpacity;
    }

    public static synchronized void adjustHudOpacity(int delta) {
        hudOpacity = clamp(hudOpacity + delta, 32, 220);
        save();
    }

    public static synchronized int hudTextScalePercent() {
        return hudTextScalePercent;
    }

    public static synchronized void adjustHudTextScalePercent(int delta) {
        hudTextScalePercent = clamp(hudTextScalePercent + delta, 70, 140);
        save();
    }

    public static synchronized boolean hudShowMissingMaterials() {
        return hudShowMissingMaterials;
    }

    public static synchronized void toggleHudShowMissingMaterials() {
        hudShowMissingMaterials = !hudShowMissingMaterials;
        save();
    }

    public static synchronized boolean hudShowBaritoneStatus() {
        return hudShowBaritoneStatus;
    }

    public static synchronized void toggleHudShowBaritoneStatus() {
        hudShowBaritoneStatus = !hudShowBaritoneStatus;
        save();
    }

    public static synchronized boolean hudShowTarget() {
        return hudShowTarget;
    }

    public static synchronized void toggleHudShowTarget() {
        hudShowTarget = !hudShowTarget;
        save();
    }

    public static synchronized boolean hudShowEta() {
        return hudShowEta;
    }

    public static synchronized void toggleHudShowEta() {
        hudShowEta = !hudShowEta;
        save();
    }

    public static synchronized boolean hudShowDebug() {
        return hudShowDebug;
    }

    public static synchronized void toggleHudShowDebug() {
        hudShowDebug = !hudShowDebug;
        save();
    }

    public static synchronized SafetyMode safetyMode() {
        return safetyMode;
    }

    public static synchronized void cycleSafetyMode() {
        SafetyMode[] values = SafetyMode.values();
        safetyMode = values[(safetyMode.ordinal() + 1) % values.length];
        save();
    }

    public static synchronized boolean diagnosisLogEnabled() {
        return diagnosisLogEnabled;
    }

    public static synchronized void toggleDiagnosisLogEnabled() {
        diagnosisLogEnabled = !diagnosisLogEnabled;
        save();
    }

    public static synchronized boolean checkpointEnabled() {
        return checkpointEnabled;
    }

    public static synchronized void toggleCheckpointEnabled() {
        checkpointEnabled = !checkpointEnabled;
        save();
    }

    public static synchronized boolean dryRunMode() {
        return dryRunMode;
    }

    public static synchronized void toggleDryRunMode() {
        dryRunMode = !dryRunMode;
        save();
    }

    public static synchronized boolean protectUtilityBlocks() {
        return protectUtilityBlocks;
    }

    public static synchronized void toggleProtectUtilityBlocks() {
        protectUtilityBlocks = !protectUtilityBlocks;
        save();
    }

    public static synchronized boolean protectRedstoneBlocks() {
        return protectRedstoneBlocks;
    }

    public static synchronized void toggleProtectRedstoneBlocks() {
        protectRedstoneBlocks = !protectRedstoneBlocks;
        save();
    }

    public static synchronized boolean hudClickControls() {
        return hudClickControls;
    }

    public static synchronized void toggleHudClickControls() {
        hudClickControls = !hudClickControls;
        save();
    }

    public static synchronized boolean showUnfinishedList() {
        return showUnfinishedList;
    }

    public static synchronized void toggleShowUnfinishedList() {
        showUnfinishedList = !showUnfinishedList;
        save();
    }

    public static synchronized boolean materialPlannerEnabled() {
        return materialPlannerEnabled;
    }

    public static synchronized void toggleMaterialPlannerEnabled() {
        materialPlannerEnabled = !materialPlannerEnabled;
        save();
    }

    public static synchronized void addMaterialChest(BlockPos pos) {
        if (!MATERIAL_CHESTS.contains(pos)) {
            MATERIAL_CHESTS.add(pos.immutable());
            MATERIAL_CHESTS.sort(Comparator
                    .comparingInt((BlockPos chestPos) -> chestPos.getX())
                    .thenComparingInt(chestPos -> chestPos.getY())
                    .thenComparingInt(chestPos -> chestPos.getZ()));
            save();
        }
    }

    public static synchronized void clearMaterialChests() {
        MATERIAL_CHESTS.clear();
        save();
    }

    public static synchronized void removeMaterialChest(BlockPos pos) {
        if (pos != null && MATERIAL_CHESTS.remove(pos)) {
            save();
        }
    }

    public static synchronized int materialChestCount() {
        return MATERIAL_CHESTS.size();
    }

    public static synchronized List<BlockPos> materialChests() {
        return new ArrayList<>(MATERIAL_CHESTS);
    }

    public static synchronized int keyMigrationVersion() {
        return keyMigrationVersion;
    }

    public static synchronized void markKeyMigrationVersion(int version) {
        keyMigrationVersion = Math.max(keyMigrationVersion, version);
        save();
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("schematic-autobuilder.properties");
    }

    private static String encodePos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static BlockPos decodePos(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.trim().split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static HudPosition parseHudPosition(String value) {
        try {
            return HudPosition.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return HudPosition.LEFT_BOTTOM;
        }
    }

    private static SafetyMode parseSafetyMode(String value) {
        try {
            return SafetyMode.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return SafetyMode.STABLE;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
