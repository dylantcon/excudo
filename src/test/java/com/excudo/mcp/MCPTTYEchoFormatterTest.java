package com.excudo.mcp;

import com.excudo.console.ConsoleStyle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests the one-line summary shape {@link MCPTTYEchoFormatter} produces
 * for each event kind. The formatter is the piece the user directly sees
 * while watching the console during an MCP session, so it's worth
 * pinning the exact wording with tests.
 */
public class MCPTTYEchoFormatterTest {

    private List<Emission> captured;
    private MCPTTYEchoFormatter formatter;

    @Before
    public void setUp() {
        captured = new ArrayList<>();
        formatter = new MCPTTYEchoFormatter((text, style) ->
            captured.add(new Emission(text, style)));
    }

    // ========== Inbound ==========

    @Test
    public void inboundToolsCallShowsMethodAndToolName() {
        formatter.onInbound(parse(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"," +
            "\"params\":{\"name\":\"get_presentation_overview\",\"arguments\":{}}}"));

        Emission e = only();
        assertEquals(ConsoleStyle.ACCENT, e.style);
        assertTrue(e.text.startsWith("→ "));
        assertTrue(e.text.contains("tools/call"));
        assertTrue(e.text.contains("get_presentation_overview"));
    }

    @Test
    public void inboundToolsCallWithArgsShowsFlattenedArgs() {
        formatter.onInbound(parse(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"," +
            "\"params\":{\"name\":\"add_shape\",\"arguments\":{\"slide\":1,\"type\":\"rect\"}}}"));

        assertTrue(only().text.contains("\"slide\":1"));
    }

    @Test
    public void inboundBareMethodShowsOnlyMethod() {
        formatter.onInbound(parse(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"));
        assertEquals("→ tools/list", only().text);
    }

    // ========== Outbound ==========

    @Test
    public void outboundToolResultShowsContentTextBlock() {
        formatter.onOutbound(parse(
            "{\"jsonrpc\":\"2.0\",\"id\":1," +
            "\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"5 slides loaded\"}]}}"));

        Emission e = only();
        assertEquals(ConsoleStyle.DIM, e.style);
        assertTrue(e.text.startsWith("← "));
        assertTrue(e.text.contains("5 slides loaded"));
    }

    @Test
    public void outboundToolResultMarksErrorFlag() {
        formatter.onOutbound(parse(
            "{\"jsonrpc\":\"2.0\",\"id\":1," +
            "\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"no session\"}]," +
            "\"isError\":true}}"));

        assertTrue(only().text.contains("error: no session"));
    }

    @Test
    public void outboundErrorFrameShowsMessage() {
        formatter.onOutbound(parse(
            "{\"jsonrpc\":\"2.0\",\"id\":1," +
            "\"error\":{\"code\":-32601,\"message\":\"Method not found: foo\"}}"));

        Emission e = only();
        assertEquals(ConsoleStyle.DIM, e.style);
        assertTrue(e.text.contains("Method not found: foo"));
    }

    @Test
    public void outboundTruncatesLongResults() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) sb.append("x");
        formatter.onOutbound(parse(
            "{\"jsonrpc\":\"2.0\",\"id\":1," +
            "\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"" + sb + "\"}]}}"));

        assertTrue("should include ellipsis", only().text.endsWith("..."));
        assertTrue("should be bounded", only().text.length() < 600);
    }

    @Test
    public void outboundFlattensNewlinesInResult() {
        formatter.onOutbound(parse(
            "{\"jsonrpc\":\"2.0\",\"id\":1," +
            "\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"line 1\\nline 2\\n\\nline 3\"}]}}"));

        assertFalse("no raw newlines in summary", only().text.contains("\n"));
        assertTrue(only().text.contains("line 1 line 2 line 3"));
    }

    // ========== Error / Lifecycle ==========

    @Test
    public void onErrorEmitsErrorStyle() {
        formatter.onError("parse failed");
        Emission e = only();
        assertEquals(ConsoleStyle.ERROR, e.style);
        assertEquals("⚠ parse failed", e.text);
    }

    @Test
    public void onLifecycleEmitsHeaderStyle() {
        formatter.onLifecycle("server started on 127.0.0.1:44321");
        Emission e = only();
        assertEquals(ConsoleStyle.HEADER, e.style);
        assertEquals("server started on 127.0.0.1:44321", e.text);
    }

    // ========== Helpers ==========

    private JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private Emission only() {
        assertEquals("expected exactly one emission", 1, captured.size());
        return captured.get(0);
    }

    private record Emission(String text, ConsoleStyle style) {}
}
