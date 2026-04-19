package com.excudo.mcp.config;

import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads and edits Claude Desktop's MCP config file. The config is a
 * single JSON file whose location is platform-dependent; this class
 * locates it, parses it, sets the {@code mcpServers.excudo} entry (or
 * removes it for deregister), and writes back with a {@code .bak}
 * copy of the previous contents.
 *
 * Everything is best-effort: if the file doesn't exist at the expected
 * path, {@link #register} creates it; if Claude Desktop isn't installed,
 * the caller gets a result telling them so and can print an appropriate
 * message without crashing. The MCP server's lifecycle doesn't depend
 * on config-file success.
 */
public final class ClaudeDesktopConfigWriter {

    private static final ComponentLogger logger = Logger.getLogger("MCP");
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    /** The key Excudo uses inside {@code mcpServers}. */
    public static final String SERVER_KEY = "excudo";

    private ClaudeDesktopConfigWriter() {}

    /**
     * Result of a config-write attempt. {@code configFound} says whether
     * a config file path was located on disk (or would be creatable at
     * the detected path); {@code written} says whether the file was
     * successfully updated.
     */
    public record Result(boolean configFound, Path configPath, boolean written, String message) {
        public static Result notFound(Path candidate) {
            return new Result(false, candidate, false,
                "Claude Desktop config not found at " + candidate);
        }
        public static Result ok(Path path, String msg) {
            return new Result(true, path, true, msg);
        }
        public static Result failure(Path path, String msg) {
            return new Result(true, path, false, msg);
        }
    }

    // ========== Path detection ==========

    /**
     * Detect the platform-appropriate Claude Desktop config path. The
     * file may or may not exist; {@link Files#exists} tells you which.
     */
    public static Path detectConfigPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", "");

        if (os.contains("mac") || os.contains("darwin")) {
            return Path.of(home, "Library", "Application Support", "Claude", "claude_desktop_config.json");
        }
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isEmpty()) {
                appData = Path.of(home, "AppData", "Roaming").toString();
            }
            return Path.of(appData, "Claude", "claude_desktop_config.json");
        }
        // Linux / BSD / other unix
        return Path.of(home, ".config", "Claude", "claude_desktop_config.json");
    }

    // ========== Register ==========

    /**
     * Add or replace the {@code mcpServers.excudo} entry so it points at
     * the given URL. Creates the config file (and parent dirs) if missing.
     * Writes a {@code .bak} copy of the previous contents when the file
     * already existed.
     */
    public static Result register(Path configPath, String serverUrl) {
        JsonObject root;
        boolean fileExisted = Files.exists(configPath);

        if (fileExisted) {
            try {
                String body = Files.readString(configPath, StandardCharsets.UTF_8).trim();
                if (body.isEmpty()) {
                    root = new JsonObject();
                } else {
                    JsonElement parsed = JsonParser.parseString(body);
                    if (!parsed.isJsonObject()) {
                        return Result.failure(configPath,
                            "Config file exists but is not a JSON object: " + configPath);
                    }
                    root = parsed.getAsJsonObject();
                }
            } catch (Exception e) {
                return Result.failure(configPath, "Failed to read config: " + e.getMessage());
            }
        } else {
            root = new JsonObject();
        }

        JsonObject mcpServers = root.has("mcpServers") && root.get("mcpServers").isJsonObject()
            ? root.getAsJsonObject("mcpServers")
            : new JsonObject();

        JsonObject entry = new JsonObject();
        entry.addProperty("url", serverUrl);
        mcpServers.add(SERVER_KEY, entry);
        root.add("mcpServers", mcpServers);

        try {
            if (fileExisted) {
                Path backup = configPath.resolveSibling(configPath.getFileName() + ".bak");
                Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.createDirectories(configPath.getParent());
            }
            Files.writeString(configPath, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Result.failure(configPath, "Failed to write config: " + e.getMessage());
        }

        logger.info("Registered Excudo with Claude Desktop at {}", configPath);
        String verb = fileExisted ? "Updated" : "Created";
        return Result.ok(configPath, verb + " Claude Desktop config with Excudo server URL");
    }

    // ========== Deregister ==========

    /**
     * Remove the {@code mcpServers.excudo} entry from the config. If the
     * file or the key doesn't exist, returns a result reflecting that.
     */
    public static Result deregister(Path configPath) {
        if (!Files.exists(configPath)) {
            return Result.notFound(configPath);
        }

        JsonObject root;
        try {
            String body = Files.readString(configPath, StandardCharsets.UTF_8).trim();
            if (body.isEmpty()) {
                return new Result(true, configPath, false,
                    "Config file is empty; nothing to deregister");
            }
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                return Result.failure(configPath, "Config is not a JSON object");
            }
            root = parsed.getAsJsonObject();
        } catch (Exception e) {
            return Result.failure(configPath, "Failed to read config: " + e.getMessage());
        }

        if (!root.has("mcpServers") || !root.get("mcpServers").isJsonObject()) {
            return new Result(true, configPath, false,
                "No mcpServers section in config; nothing to deregister");
        }

        JsonObject mcpServers = root.getAsJsonObject("mcpServers");
        if (!mcpServers.has(SERVER_KEY)) {
            return new Result(true, configPath, false,
                "Excudo entry not present in config; nothing to deregister");
        }

        mcpServers.remove(SERVER_KEY);

        try {
            Path backup = configPath.resolveSibling(configPath.getFileName() + ".bak");
            Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(configPath, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Result.failure(configPath, "Failed to write config: " + e.getMessage());
        }

        logger.info("Deregistered Excudo from Claude Desktop at {}", configPath);
        return Result.ok(configPath, "Removed Excudo entry from Claude Desktop config");
    }
}
