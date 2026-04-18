package com.excudo.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link MCPProtocolHandler}. Covers the JSON-RPC frames
 * the handler produces for every method it understands without standing
 * up a real orchestrator -- tool-call dispatch is exercised separately
 * through ToolDispatcher's own tests.
 *
 * Null is passed as the ToolDispatcher because every test case here
 * handles a method that never reaches dispatch. tools/call coverage
 * lives at the integration layer.
 */
public class MCPProtocolHandlerTest {

    private final MCPProtocolHandler handler = new MCPProtocolHandler(null);

    // -------- initialize --------

    @Test
    public void initializeReturnsProtocolVersionAndServerInfo() {
        JsonObject req = parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}");
        JsonObject resp = handler.handleRequest(req);

        assertNotNull(resp);
        assertEquals("2.0", resp.get("jsonrpc").getAsString());
        assertEquals(1, resp.get("id").getAsInt());

        JsonObject result = resp.getAsJsonObject("result");
        assertEquals("2024-11-05", result.get("protocolVersion").getAsString());

        JsonObject serverInfo = result.getAsJsonObject("serverInfo");
        assertEquals("excudo", serverInfo.get("name").getAsString());
        assertNotNull(serverInfo.get("version"));

        assertTrue("capabilities must include tools", result.getAsJsonObject("capabilities").has("tools"));
    }

    // -------- tools/list --------

    @Test
    public void toolsListReturnsNonEmptyToolArray() {
        JsonObject req = parse("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        JsonObject resp = handler.handleRequest(req);

        assertNotNull(resp);
        JsonObject result = resp.getAsJsonObject("result");
        assertTrue("tools array must be non-empty",
            result.getAsJsonArray("tools").size() > 0);
    }

    @Test
    public void toolsListEntriesHaveNameDescriptionAndInputSchema() {
        JsonObject req = parse("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}");
        JsonObject resp = handler.handleRequest(req);

        JsonObject firstTool = resp.getAsJsonObject("result")
            .getAsJsonArray("tools").get(0).getAsJsonObject();

        assertTrue(firstTool.has("name"));
        assertTrue(firstTool.has("description"));
        assertTrue(firstTool.has("inputSchema"));
        assertFalse("name must be non-empty", firstTool.get("name").getAsString().isEmpty());
    }

    @Test
    public void toolsListOmitsFetchToolSchemas() {
        JsonObject req = parse("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/list\"}");
        JsonObject resp = handler.handleRequest(req);

        for (JsonElement el : resp.getAsJsonObject("result").getAsJsonArray("tools")) {
            String name = el.getAsJsonObject().get("name").getAsString();
            assertNotEquals("fetch_tool_schemas is an LLM-API helper, not meaningful over MCP",
                "fetch_tool_schemas", name);
        }
    }

    // -------- ping --------

    @Test
    public void pingReturnsEmptyResultObject() {
        JsonObject req = parse("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"ping\"}");
        JsonObject resp = handler.handleRequest(req);

        assertNotNull(resp);
        assertEquals(0, resp.getAsJsonObject("result").size());
    }

    // -------- notifications/initialized --------

    @Test
    public void initializedNotificationReturnsNull() {
        JsonObject req = parse("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        assertNull(handler.handleRequest(req));
    }

    // -------- unknown method --------

    @Test
    public void unknownMethodReturnsMethodNotFoundError() {
        JsonObject req = parse("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"bogus/method\"}");
        JsonObject resp = handler.handleRequest(req);

        JsonObject error = resp.getAsJsonObject("error");
        assertEquals(JsonRpcFrames.METHOD_NOT_FOUND, error.get("code").getAsInt());
        assertTrue(error.get("message").getAsString().contains("bogus/method"));
    }

    // -------- id preservation --------

    @Test
    public void responseIdMatchesRequestIdForStringIds() {
        JsonObject req = parse("{\"jsonrpc\":\"2.0\",\"id\":\"abc-123\",\"method\":\"ping\"}");
        JsonObject resp = handler.handleRequest(req);
        assertEquals("abc-123", resp.get("id").getAsString());
    }

    // -------- helper --------

    private JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
