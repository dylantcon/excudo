package com.excudo.mcp;

import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the live MCP HTTP/SSE endpoint to {@code ~/.excudo/mcp-endpoint.json}
 * so out-of-process clients (notably the stdio bridge that Claude Desktop
 * spawns) can find the URL of the in-process server without having a stale
 * URL baked into their own config.
 *
 * <p>Lifecycle: written by {@code AbstractConsoleEngine.startMCPHttpServer}
 * once the HTTP server is bound, deleted in the {@code finally} block when
 * the serve loop returns. Best-effort on both sides -- a missing or
 * unwritable file just means clients see "Excudo not running" until next
 * launch, which is exactly the behavior we want.
 *
 * <p>File shape:
 * <pre>{@code
 *   {
 *     "url":   "http://127.0.0.1:54321/mcp/<32-hex>",
 *     "token": "<32-hex>",
 *     "pid":   12345
 *   }
 * }</pre>
 */
public final class MCPEndpointFile {

    private static final ComponentLogger logger = Logger.getLogger("MCP");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private MCPEndpointFile() {}

    /** Discovery file path: {@code ~/.excudo/mcp-endpoint.json}. */
    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home", ""), ".excudo", "mcp-endpoint.json");
    }

    /**
     * Write the endpoint descriptor at {@code path}. Creates parent
     * directories if missing. Returns true on success; failures are
     * logged and swallowed so they never block MCP server startup.
     */
    public static boolean write(Path path, String url, String token) {
        JsonObject obj = new JsonObject();
        obj.addProperty("url", url);
        obj.addProperty("token", token);
        obj.addProperty("pid", ProcessHandle.current().pid());
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(obj), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            logger.warn("Could not write MCP endpoint file at {}: {}", path, e.getMessage());
            return false;
        }
    }

    /** Convenience overload using {@link #defaultPath()}. */
    public static boolean write(String url, String token) {
        return write(defaultPath(), url, token);
    }

    /**
     * Remove the endpoint descriptor. No-op when the file is already
     * absent. Failures are logged and swallowed.
     */
    public static boolean delete(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("Could not delete MCP endpoint file at {}: {}", path, e.getMessage());
            return false;
        }
    }

    /** Convenience overload using {@link #defaultPath()}. */
    public static boolean delete() {
        return delete(defaultPath());
    }
}
