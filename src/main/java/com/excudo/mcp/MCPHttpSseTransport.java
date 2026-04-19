package com.excudo.mcp;

import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * HTTP/SSE transport for in-process MCP serving. Uses JDK 21's built-in
 * {@link HttpServer} so no external dependency is needed.
 *
 * Binds 127.0.0.1 only by default. A random 32-char hex token is baked
 * into every URL path ({@code /mcp/&lt;token&gt;}) as drive-by protection
 * against other processes on the local machine. Requests to unknown paths
 * get the server's default 404, leaking no info about whether the token
 * is valid.
 *
 * Endpoints:
 * <ul>
 *   <li>{@code POST /mcp/&lt;token&gt;} — JSON-RPC request body, JSON response.</li>
 *   <li>{@code GET /mcp/&lt;token&gt;/stream} — Server-Sent Events; notifications
 *       pushed via {@link #pushNotification(JsonObject)} are fanned out to
 *       every live subscriber.</li>
 * </ul>
 *
 * Thread model: a small cached pool accepts connections; calls to the
 * protocol handler are serialised via a monitor so the underlying
 * {@code ToolDispatcher} (not guaranteed thread-safe) only sees one
 * request at a time.
 */
public class MCPHttpSseTransport implements MCPTransport, AutoCloseable {

    private static final ComponentLogger logger = Logger.getLogger("MCP");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int TOKEN_BYTES = 16; // 32 hex chars

    private final String bindHost;
    private final int configuredPort;
    private final String token;
    private final Object handlerLock = new Object();
    private final CountDownLatch stopLatch = new CountDownLatch(1);
    private final List<SseSubscriber> sseSubscribers = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private Function<JsonObject, JsonObject> handler;
    private MCPFrameListener frameListener = MCPFrameListener.NO_OP;
    private volatile boolean started = false;

    public MCPHttpSseTransport() {
        this("127.0.0.1", 0);
    }

    public MCPHttpSseTransport(String bindHost, int port) {
        this.bindHost = bindHost;
        this.configuredPort = port;
        this.token = generateToken();
    }

    // ========== Lifecycle ==========

    /**
     * Bind the listening socket and register endpoints. Safe to call before
     * {@link #serve} if the caller needs {@link #getUrl()} / {@link #getPort()}
     * before handing off to the serve loop. Idempotent.
     */
    public synchronized void bind() throws IOException {
        if (server != null) return;
        server = HttpServer.create(new InetSocketAddress(bindHost, configuredPort), 0);
        server.createContext("/mcp/" + token, new RequestHandler());
        server.createContext("/mcp/" + token + "/stream", new SseHandler());
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mcp-http");
            t.setDaemon(true);
            return t;
        }));
    }

    @Override
    public void serve(Function<JsonObject, JsonObject> handler) throws IOException {
        if (server == null) bind();
        this.handler = handler;
        started = true;
        server.start();
        logger.info("MCP HTTP/SSE transport listening on {}", getUrl());
        frameListener.onLifecycle("MCP HTTP server listening on " + getUrl());
        try {
            stopLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            server.stop(0);
            for (SseSubscriber sub : sseSubscribers) sub.close();
            sseSubscribers.clear();
            logger.info("MCP HTTP/SSE transport stopped");
            frameListener.onLifecycle("MCP HTTP server stopped");
        }
    }

    /**
     * Attach a listener to observe inbound/outbound frames, errors, and
     * lifecycle events. Passing null restores the no-op listener. Safe to
     * call before or after {@link #serve}.
     */
    public void setFrameListener(MCPFrameListener listener) {
        this.frameListener = (listener == null) ? MCPFrameListener.NO_OP : listener;
    }

    @Override
    public void stop() {
        stopLatch.countDown();
        // If serve() is blocking on the latch, it will unblock and call
        // server.stop(0) itself. But if stop() is called without serve()
        // ever running (e.g., from a test that bound but never served),
        // the socket is still held; release it here.
        HttpServer s = this.server;
        if (s != null && !started) {
            try { s.stop(0); } catch (Exception ignored) {}
            this.server = null;
        }
    }

    @Override
    public void close() {
        stop();
    }

    // ========== Public accessors ==========

    public int getPort() {
        ensureBound();
        return server.getAddress().getPort();
    }

    /** True after {@link #bind()} has allocated the listening socket. */
    public boolean isBound() {
        return server != null;
    }

    public String getToken() {
        return token;
    }

    public String getUrl() {
        return "http://" + bindHost + ":" + getPort() + "/mcp/" + token;
    }

    public String getStreamUrl() {
        return getUrl() + "/stream";
    }

    // ========== Notifications (SSE) ==========

    @Override
    public void pushNotification(JsonObject notification) {
        String frame = "event: message\ndata: " + GSON.toJson(notification) + "\n\n";
        byte[] bytes = frame.getBytes(StandardCharsets.UTF_8);
        for (SseSubscriber sub : sseSubscribers) {
            sub.push(bytes);
        }
    }

    // ========== HTTP handlers ==========

    private class RequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    sendPlain(ex, 405, "Method Not Allowed");
                    return;
                }

                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (body.isEmpty()) {
                    sendJson(ex, 400, JsonRpcFrames.error(null,
                        JsonRpcFrames.INVALID_REQUEST, "Empty request body"));
                    return;
                }

                JsonObject request;
                try {
                    request = JsonParser.parseString(body).getAsJsonObject();
                } catch (JsonSyntaxException e) {
                    frameListener.onError("Parse error: " + e.getMessage());
                    sendJson(ex, 400, JsonRpcFrames.error(null,
                        JsonRpcFrames.PARSE_ERROR, "Parse error: " + e.getMessage()));
                    return;
                }

                frameListener.onInbound(request);

                JsonObject response;
                synchronized (handlerLock) {
                    response = handler.apply(request);
                }

                if (response == null) {
                    // Notification request -- 204 No Content
                    ex.sendResponseHeaders(204, -1);
                    ex.close();
                } else {
                    frameListener.onOutbound(response);
                    sendJson(ex, 200, response);
                }
            } catch (Exception e) {
                logger.error("HTTP handler error: {}", e.getMessage());
                frameListener.onError("Internal error: " + e.getMessage());
                try {
                    sendJson(ex, 500, JsonRpcFrames.error(null,
                        JsonRpcFrames.INTERNAL_ERROR, "Internal error: " + e.getMessage()));
                } catch (IOException ignored) {
                    // exchange already closed; nothing we can do
                }
            }
        }
    }

    private class SseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendPlain(ex, 405, "Method Not Allowed");
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.getResponseHeaders().set("Connection", "keep-alive");
            ex.sendResponseHeaders(200, 0);

            SseSubscriber sub = new SseSubscriber(ex.getResponseBody());
            sseSubscribers.add(sub);
            frameListener.onLifecycle("SSE client connected");
            // Push an initial comment so the client sees the stream has opened.
            sub.push(": connected\n\n".getBytes(StandardCharsets.UTF_8));

            try {
                sub.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                sseSubscribers.remove(sub);
                frameListener.onLifecycle("SSE client disconnected");
                try { ex.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ========== SSE subscriber ==========

    private static class SseSubscriber {
        private final OutputStream out;
        private final CountDownLatch closed = new CountDownLatch(1);

        SseSubscriber(OutputStream out) {
            this.out = out;
        }

        void push(byte[] frame) {
            try {
                synchronized (out) {
                    out.write(frame);
                    out.flush();
                }
            } catch (IOException e) {
                closed.countDown();
            }
        }

        void await() throws InterruptedException {
            closed.await();
        }

        void close() {
            closed.countDown();
        }
    }

    // ========== Helpers ==========

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        StringBuilder hex = new StringBuilder(TOKEN_BYTES * 2);
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private void ensureBound() {
        if (server == null) {
            throw new IllegalStateException("Transport not bound yet. Call bind() or serve() first.");
        }
    }

    private static void sendPlain(HttpExchange ex, int status, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static void sendJson(HttpExchange ex, int status, JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
