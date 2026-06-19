package com.kanzakideath.schematicautobuilder;

import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutoUpdater {

    private static volatile boolean checking;
    private static volatile String status = "Idle";

    private AutoUpdater() {}

    public static void start() {
        status = "Idle";
    }

    public static boolean isChecking() {
        return checking;
    }

    public static String status() {
        return status;
    }

    public static void checkNow() {
        if (checking) {
            return;
        }
        checking = true;
        status = "Checking...";
        Thread thread = new Thread(AutoUpdater::runCheck, "SchematicAutoBuilder-Updater");
        thread.setDaemon(true);
        thread.start();
    }

    private static void runCheck() {
        try {
            String manifestUrl = readUpdateUrl();
            String manifest = getText(manifestUrl);
            if (manifest.contains("\"encoding\"") && manifest.contains("\"base64\"") && manifest.contains("\"content\"")) {
                manifest = new String(Base64.getMimeDecoder().decode(extract(manifest, "content")), StandardCharsets.UTF_8);
            }
            String version = extract(manifest, "version");
            if (version.isBlank() || currentVersion().equals(version)) {
                status = "Latest";
                return;
            }
            String url = extract(manifest, "url");
            String sha256 = extract(manifest, "sha256");
            String fileName = extract(manifest, "fileName");
            if (url.isBlank() || fileName.isBlank()) {
                status = "Invalid manifest";
                return;
            }
            byte[] jar = getBytes(url);
            if (!sha256.isBlank() && !sha256(jar).equalsIgnoreCase(sha256)) {
                status = "Checksum mismatch";
                return;
            }
            Path mods = Minecraft.getInstance().gameDirectory.toPath().resolve("mods");
            Files.createDirectories(mods);
            Files.write(mods.resolve(fileName), jar);
            status = "Downloaded " + version + ". Restart Minecraft.";
        } catch (Exception ex) {
            status = "Update failed: " + ex.getClass().getSimpleName();
        } finally {
            checking = false;
        }
    }

    private static String readUpdateUrl() throws IOException {
        try (InputStream in = AutoUpdater.class.getResourceAsStream("/assets/schematicautobuilder/update_url.txt")) {
            if (in == null) {
                throw new IOException("missing update_url.txt");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private static String currentVersion() {
        return FabricLoader.getInstance()
                .getModContainer("schematicautobuilder")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    private static String getText(String url) throws IOException, InterruptedException {
        return new String(getBytes(url), StandardCharsets.UTF_8);
    }

    private static byte[] getBytes(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static String extract(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"", Pattern.DOTALL).matcher(json);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).replace("\\n", "").replace("\\\"", "\"").trim();
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder builder = new StringBuilder();
        for (byte value : hash) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }
}
