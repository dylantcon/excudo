package com.excudo.mcp;

import com.google.gson.JsonObject;

/**
 * Receives transport-level events: inbound requests, outbound responses,
 * errors, and server lifecycle. Primary consumer is {@link MCPTTYEchoFormatter}
 * which turns them into styled text for the TTY, but other uses (metrics,
 * training-data capture, unit tests) can implement their own variants.
 *
 * All methods are default no-ops so implementations can opt into only the
 * events they care about.
 */
public interface MCPFrameListener {

    /** No-op singleton used when no listener is attached. */
    MCPFrameListener NO_OP = new MCPFrameListener() {};

    default void onInbound(JsonObject request) {}

    default void onOutbound(JsonObject response) {}

    default void onError(String message) {}

    default void onLifecycle(String event) {}
}
