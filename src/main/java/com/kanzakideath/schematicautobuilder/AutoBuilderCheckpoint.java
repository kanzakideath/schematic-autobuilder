package com.kanzakideath.schematicautobuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;

public final class AutoBuilderCheckpoint {

    public record Snapshot(String schematicFile, BlockPos origin, String mode, String status, long updatedAt) {
        public boolean hasFileBuild() {
            return schematicFile != null && !schematicFile.isBlank();
        }

        public boolean hasOrigin() {
            return origin != null;
        }
    }

    private AutoBuilderCheckpoint() {}

    public static void save(String schematicFile, BlockPos origin, String mode, String status) {
        if (!AutoBuilderConfig.checkpointEnabled()) {
            return;
        }
        Properties properties = new Properties();
        properties.setProperty("schematicFile", schematicFile == null ? "" : schematicFile);
        properties.setProperty("mode", mode == null ? "" : mode);
        properties.setProperty("status", status == null ? "" : status);
        properties.setProperty("updatedAt", Long.toString(Instant.now().toEpochMilli()));
        if (origin != null) {
            properties.setProperty("origin", origin.getX() + "," + origin.getY() + "," + origin.getZ());
        }
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "Schematic AutoBuilder checkpoint");
            }
        } catch (IOException ignored) {
        }
    }

    public static Snapshot load() {
        Path path = path();
        if (!Files.exists(path)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
            return new Snapshot(
                    properties.getProperty("schematicFile", ""),
                    decodePos(properties.getProperty("origin", "")),
                    properties.getProperty("mode", ""),
                    properties.getProperty("status", ""),
                    parseLong(properties.getProperty("updatedAt", "0"))
            );
        } catch (IOException ignored) {
            return null;
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(path());
        } catch (IOException ignored) {
        }
    }

    public static String summary() {
        Snapshot snapshot = load();
        if (snapshot == null) {
            return "none";
        }
        String target = snapshot.hasFileBuild() ? snapshot.schematicFile() : "placed schematic";
        String origin = snapshot.hasOrigin()
                ? " @ " + snapshot.origin().getX() + "," + snapshot.origin().getY() + "," + snapshot.origin().getZ()
                : "";
        return target + origin + " / " + snapshot.mode();
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("autobuilder").resolve("checkpoint.properties");
    }

    private static BlockPos decodePos(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
