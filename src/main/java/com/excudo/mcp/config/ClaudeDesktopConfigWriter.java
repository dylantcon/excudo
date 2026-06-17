package com.excudo.mcp.config;

import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
     * Excudo's stdio bridge script. Creates the config file (and parent
     * dirs) if missing. Writes a {@code .bak} copy of the previous
     * contents when the file already existed.
     *
     * <p>The bridge presents a healthy MCP handshake to Claude Desktop
     * regardless of whether Excudo is running -- so Claude no longer
     * shows "failed to attach" warnings on launch when the app is off.
     * The bridge proxies real tool calls to the live HTTP/SSE server
     * (discovered via {@code ~/.excudo/mcp-endpoint.json}) and exposes a
     * {@code launch_excudo} tool that starts the GUI on demand.
     */
    public static Result register(Path configPath, Path bridgeScript) {
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

        // Build a fresh entry from scratch so any stale fields from an
        // older Excudo install (e.g. a previous "url" or "mcp-remote"
        // shape) get fully overwritten rather than merged.
        JsonObject entry = new JsonObject();
        entry.addProperty("command", resolvePythonCommand());
        JsonArray args = new JsonArray();
        args.add(bridgeScript.toAbsolutePath().toString());
        entry.add("args", args);
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

    /**
     * Resolve a Python interpreter for the stdio bridge command written into
     * Claude Desktop's config. Claude Desktop spawns MCP servers with a
     * minimal environment, so a bare {@code "python3"} often doesn't resolve
     * -- especially on Windows, where the launcher is usually {@code python}
     * or {@code py}, not {@code python3}. Prefer the absolute interpreter that
     * launched Excudo ({@code pc.py} exports {@code EXCUDO_PYTHON=sys.executable}),
     * else search PATH for an absolute match, else a best-effort fallback.
     */
    static String resolvePythonCommand() {
        return resolvePythonCommand(System.getenv("EXCUDO_PYTHON"),
            System.getenv("PATH"), System.getProperty("os.name", ""));
    }

    /** Pure resolution logic, package-private for testing. */
    static String resolvePythonCommand(String fromLauncher, String pathEnv, String osName) {
        if (fromLauncher != null && !fromLauncher.isBlank()
                && Files.isRegularFile(Path.of(fromLauncher))) {
            return fromLauncher;
        }
        boolean windows = osName.toLowerCase().contains("win");
        java.util.List<String> names = windows
            ? java.util.List.of("python3.exe", "python.exe", "py.exe")
            : java.util.List.of("python3", "python");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                if (dir.isBlank()) continue;
                for (String name : names) {
                    Path candidate = Path.of(dir, name);
                    if (Files.isRegularFile(candidate)) {
                        return candidate.toAbsolutePath().toString();
                    }
                }
            }
        }
        return windows ? "python" : "python3";
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
