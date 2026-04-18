package com.excudo.mcp;

import com.excudo.console.ConsoleStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.function.BiConsumer;

/**
 * Converts transport events into styled one-line summaries for the TTY.
 * Attaches to an {@link MCPTransport} via {@code setFrameListener} and
 * routes each frame through a {@link BiConsumer BiConsumer&lt;String,ConsoleStyle&gt;}
 * — typically {@code engine::displayStyled} on an AbstractConsoleEngine.
 *
 * Formatting rules:
 * <ul>
 *   <li>Inbound request → {@code "→ method argsSummary"} in {@link ConsoleStyle#ACCENT}</li>
 *   <li>Outbound response → {@code "← resultSummary"} in {@link ConsoleStyle#DIM},
 *       truncated to {@value #MAX_SUMMARY} chars with newlines flattened</li>
 *   <li>Error → {@code "⚠ message"} in {@link ConsoleStyle#ERROR}</li>
 *   <li>Lifecycle (server started / stopped / SSE connected) → {@link ConsoleStyle#HEADER}</li>
 * </ul>
 *
 * Raw JSON would be unreadable at MCP's frame sizes (tool catalogs are
 * kilobytes of schema). The summary picks out the interesting fields:
 * tool name for {@code tools/call}, the first content text block for
 * tool responses, and the error message for protocol errors.
 */
public class MCPTTYEchoFormatter implements MCPFrameListener {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_SUMMARY = 500;

    private final BiConsumer<String, ConsoleStyle> sink;

    public MCPTTYEchoFormatter(BiConsumer<String, ConsoleStyle> sink) {
        this.sink = sink;
    }

    @Override
    public void onInbound(JsonObject request) {
        sink.accept("→ " + summarizeInbound(request), ConsoleStyle.ACCENT);
    }

    @Override
    public void onOutbound(JsonObject response) {
        sink.accept("← " + summarizeOutbound(response), ConsoleStyle.DIM);
    }

    @Override
    public void onError(String message) {
        sink.accept("⚠ " + message, ConsoleStyle.ERROR);
    }

    @Override
    public void onLifecycle(String event) {
        sink.accept(event, ConsoleStyle.HEADER);
    }

    // ========== Summarization ==========

    private String summarizeInbound(JsonObject request) {
        String method = request.has("method") ? request.get("method").getAsString() : "?";

        if ("tools/call".equals(method) && request.has("params")) {
            JsonObject params = request.getAsJsonObject("params");
            String toolName = params.has("name") ? params.get("name").getAsString() : "?";
            String args = "";
            if (params.has("arguments") && !params.get("arguments").isJsonNull()) {
                args = GSON.toJson(params.get("arguments"));
            }
            if (args.isEmpty() || "{}".equals(args)) {
                return method + " " + toolName;
            }
            return method + " " + toolName + " " + truncate(flatten(args));
        }

        return method;
    }

    private String summarizeOutbound(JsonObject response) {
        if (response.has("error")) {
            JsonObject err = response.getAsJsonObject("error");
            String msg = err.has("message") ? err.get("message").getAsString() : "unknown error";
            return "error: " + truncate(flatten(msg));
        }

        if (!response.has("result") || response.get("result").isJsonNull()) {
            return "(no result)";
        }

        JsonObject result = response.getAsJsonObject("result");

        // tools/call response: content[0].text is the tool output
        if (result.has("content")) {
            JsonArray content = result.getAsJsonArray("content");
            if (content.size() > 0) {
                JsonObject block = content.get(0).getAsJsonObject();
                if (block.has("text")) {
                    String text = block.get("text").getAsString();
                    boolean isError = result.has("isError") && result.get("isError").getAsBoolean();
                    return (isError ? "error: " : "") + truncate(flatten(text));
                }
            }
        }

        // initialize / tools/list / ping: serialize compactly
        return truncate(flatten(GSON.toJson(result)));
    }

    private static String flatten(String s) {
        return s.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String s) {
        if (s.length() <= MAX_SUMMARY) return s;
        return s.substring(0, MAX_SUMMARY) + "...";
    }
}
