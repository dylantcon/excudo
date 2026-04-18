package com.excudo.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.Assert.*;

/**
 * Round-trip tests for the HTTP transport. Spins up a real server on an
 * ephemeral localhost port, sends JSON-RPC requests via {@link HttpClient},
 * and asserts the wire response matches what {@link MCPProtocolHandler}
 * would have produced directly. Tool-call dispatch is not exercised here
 * (that's ToolDispatcher's test surface); we stick to protocol methods
 * the handler resolves without touching the dispatcher.
 */
public class MCPHttpSseTransportTest {

    private MCPHttpSseTransport transport;
    private Thread serverThread;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    @Before
    public void setUp() throws Exception {
        transport = new MCPHttpSseTransport();
        transport.bind();
        MCPProtocolHandler handler = new MCPProtocolHandler(null);
        serverThread = new Thread(() -> {
            try {
                transport.serve(handler::handleRequest);
            } catch (Exception e) {
                // serve throws on stop -- expected
            }
        }, "mcp-test-server");
        serverThread.setDaemon(true);
        serverThread.start();
        // The server is already bound via bind() so getUrl() is valid;
        // serve() only adds the start() call and blocks.
    }

    @After
    public void tearDown() throws Exception {
        if (transport != null) transport.stop();
        if (serverThread != null) serverThread.join(2000);
    }

    // ========== Binding / URL ==========

    @Test
    public void bindAllocatesEphemeralLocalhostPort() {
        assertTrue("port should be > 0", transport.getPort() > 0);
        assertTrue(transport.getUrl().startsWith("http://127.0.0.1:"));
        assertTrue(transport.getUrl().endsWith("/mcp/" + transport.getToken()));
    }

    @Test
    public void tokenIs32HexChars() {
        String token = transport.getToken();
        assertEquals(32, token.length());
        assertTrue("token must be lowercase hex", token.matches("[0-9a-f]{32}"));
    }

    // ========== POST /mcp/{token} ==========

    @Test
    public void postInitializeReturnsServerInfo() throws Exception {
        JsonObject resp = postJson(transport.getUrl(),
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}");

        assertEquals(1, resp.get("id").getAsInt());
        JsonObject info = resp.getAsJsonObject("result").getAsJsonObject("serverInfo");
        assertEquals("excudo", info.get("name").getAsString());
    }

    @Test
    public void postToolsListReturnsToolsArray() throws Exception {
        JsonObject resp = postJson(transport.getUrl(),
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");

        assertTrue(resp.getAsJsonObject("result").getAsJsonArray("tools").size() > 0);
    }

    @Test
    public void postPingReturnsEmptyResult() throws Exception {
        JsonObject resp = postJson(transport.getUrl(),
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}");

        assertEquals(0, resp.getAsJsonObject("result").size());
    }

    // ========== Token validation (via HttpServer's default 404) ==========

    @Test
    public void wrongTokenReturns404() throws Exception {
        String wrongUrl = "http://127.0.0.1:" + transport.getPort() + "/mcp/deadbeef";
        HttpResponse<String> resp = http.send(
            HttpRequest.newBuilder().uri(URI.create(wrongUrl))
                .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(404, resp.statusCode());
    }

    // ========== Parse errors ==========

    @Test
    public void malformedBodyReturns400WithParseError() throws Exception {
        HttpResponse<String> raw = http.send(
            HttpRequest.newBuilder().uri(URI.create(transport.getUrl()))
                .POST(HttpRequest.BodyPublishers.ofString("{not valid json"))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertEquals(400, raw.statusCode());
        JsonObject frame = JsonParser.parseString(raw.body()).getAsJsonObject();
        assertEquals(JsonRpcFrames.PARSE_ERROR,
            frame.getAsJsonObject("error").get("code").getAsInt());
    }

    // ========== Method filtering ==========

    @Test
    public void getOnRequestEndpointReturns405() throws Exception {
        HttpResponse<String> resp = http.send(
            HttpRequest.newBuilder().uri(URI.create(transport.getUrl()))
                .GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(405, resp.statusCode());
    }

    // ========== Notification (no id) returns 204 ==========

    @Test
    public void notificationRequestReturns204NoContent() throws Exception {
        HttpResponse<String> resp = http.send(
            HttpRequest.newBuilder().uri(URI.create(transport.getUrl()))
                .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(204, resp.statusCode());
    }

    // ========== Helper ==========

    private JsonObject postJson(String url, String body) throws Exception {
        HttpResponse<String> resp = http.send(
            HttpRequest.newBuilder().uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals("expected HTTP 200, got " + resp.statusCode() + ": " + resp.body(),
            200, resp.statusCode());
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }
}
