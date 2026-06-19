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

    private static final List<BlockPos> MATERIAL_CHESTS = new ArrayList<>();
    private static boolean autoFetchMaterials = true;
    private static boolean autoCraftMaterials = true;
    private static boolean startBuildAfterFetch = true;
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
}
