package com.excudo.mcp;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.function.Function;

/**
 * Wire transport for MCP JSON-RPC frames. Implementations own the byte
 * channel (stdio, HTTP/SSE, ...) and are responsible for parsing inbound
 * frames, invoking the handler, serializing the handler's response, and
 * writing it back.
 *
 * The handler is a pure function of request-to-response -- it returns
 * null for notifications (no reply expected) and a JsonObject otherwise.
 * Parse errors are handled by the transport directly via {@link JsonRpcFrames}
 * since no handler is ever called with malformed input.
 */
public interface MCPTransport {

    /**
     * Start serving. Blocks until the transport is closed (e.g., stdin EOF,
     * HTTP server stopped). Each inbound JSON-RPC request is passed to
     * {@code handler}; the handler's return value (if non-null) is sent
     * back to the peer.
     */
    void serve(Function<JsonObject, JsonObject> handler) throws IOException;

    /**
     * Push a server-initiated notification. Implementations that do not
     * support server-push (e.g., plain stdio request/response) are free
     * to no-op. Reserved for future MCP notifications / progress frames.
     */
    default void pushNotification(JsonObject notification) {
        // default no-op
    }

    /**
     * Signal the transport to stop serving. Safe to call from any thread.
     * {@link #serve(Function)} will unblock and return.
     */
    void stop();
}
