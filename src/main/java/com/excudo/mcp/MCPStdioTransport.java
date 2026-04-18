package com.excudo.mcp;

import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.function.Function;

/**
 * Stdio transport: reads line-delimited JSON-RPC from the given InputStream,
 * dispatches each request to the handler, and writes the response to the
 * given PrintStream. Blocks in {@link #serve} until EOF or {@link #stop()}.
 *
 * The PrintStream passed in must be the real stdout captured BEFORE any
 * logger initialisation runs (LoggingManager will otherwise claim System.out
 * and pollute the JSON-RPC stream). See {@link com.excudo.console.MCPConsoleEngine}
 * for the capture.
 */
public class MCPStdioTransport implements MCPTransport {

    private static final ComponentLogger logger = Logger.getLogger("MCP");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final InputStream in;
    private final PrintStream out;
    private volatile boolean running = false;

    public MCPStdioTransport(InputStream in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public void serve(Function<JsonObject, JsonObject> handler) throws IOException {
        running = true;
        logger.info("MCP stdio transport started, reading JSON-RPC from stdin");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    JsonObject request = JsonParser.parseString(line).getAsJsonObject();
                    JsonObject response = handler.apply(request);
                    if (response != null) {
                        writeFrame(response);
                    }
                } catch (JsonSyntaxException e) {
                    writeFrame(JsonRpcFrames.error(null, JsonRpcFrames.PARSE_ERROR,
                        "Parse error: " + e.getMessage()));
                } catch (Exception e) {
                    logger.error("Error handling request: {}", e.getMessage());
                    writeFrame(JsonRpcFrames.error(null, JsonRpcFrames.INTERNAL_ERROR,
                        "Internal error: " + e.getMessage()));
                }
            }
        } finally {
            logger.info("MCP stdio transport stopped");
            running = false;
        }
    }

    @Override
    public void pushNotification(JsonObject notification) {
        writeFrame(notification);
    }

    @Override
    public void stop() {
        running = false;
    }

    private void writeFrame(JsonObject frame) {
        out.println(GSON.toJson(frame));
        out.flush();
    }
}
