package com.excudo.core.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The exact request body sent to the Messages API: prompt-cache breakpoints
 * and reasoning blocks passed through unmodified.
 */
public class AnthropicRequestBodyTest {

    private static final String TOOLS = "[{\"name\":\"a\",\"input_schema\":{\"type\":\"object\"}},"
        + "{\"name\":\"b\",\"input_schema\":{\"type\":\"object\"}}]";

    private static JsonObject body(List<Map<String, String>> messages, String tools) {
        String json = new AnthropicAPIClient("sk-ant-test", "claude-opus-5-5")
            .buildRequestBodyWithTools("You edit decks.", messages, tools);
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static boolean cached(JsonObject o) {
        return o.has("cache_control") && "ephemeral".equals(o.getAsJsonObject("cache_control").get("type").getAsString());
    }

    @Test
    void cacheBreakpointsOnToolsSystemAndNewestMessage() {
        JsonObject body = body(List.of(
            Map.of("role", "user", "content", "first request"),
            Map.of("role", "assistant", "content", "done"),
            Map.of("role", "user", "content", "second request")), TOOLS);

        JsonArray tools = body.getAsJsonArray("tools");
        assertFalse(cached(tools.get(0).getAsJsonObject()), "only the last tool carries the breakpoint");
        assertTrue(cached(tools.get(1).getAsJsonObject()));

        JsonObject system = body.getAsJsonArray("system").get(0).getAsJsonObject();
        assertEquals("You edit decks.", system.get("text").getAsString());
        assertTrue(cached(system));

        JsonArray messages = body.getAsJsonArray("messages");
        assertTrue(messages.get(0).getAsJsonObject().get("content").isJsonPrimitive(), "older messages untouched");
        JsonArray last = messages.get(2).getAsJsonObject().getAsJsonArray("content");
        assertEquals("second request", last.get(0).getAsJsonObject().get("text").getAsString());
        assertTrue(cached(last.get(0).getAsJsonObject()));
    }

    @Test
    void reasoningBlocksPassThroughAndNeverCarryTheBreakpoint() {
        String assistantBlocks = "[{\"type\":\"thinking\",\"thinking\":\"plan it\",\"signature\":\"sig==\"},"
            + "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"a\",\"input\":{}}]";
        String toolResults = "[{\"type\":\"tool_result\",\"tool_use_id\":\"toolu_1\",\"content\":\"ok\"}]";
        JsonObject body = body(List.of(
            Map.of("role", "user", "content", "go"),
            Map.of("role", "assistant", "content", assistantBlocks),
            Map.of("role", "user", "content", toolResults)), TOOLS);

        JsonArray messages = body.getAsJsonArray("messages");
        JsonObject thinking = messages.get(1).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("plan it", thinking.get("thinking").getAsString());
        assertEquals("sig==", thinking.get("signature").getAsString());
        assertFalse(cached(thinking));
        assertTrue(cached(messages.get(2).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject()),
            "tool_result block carries the breakpoint");
    }

    @Test
    void emptyToolListStaysValid() {
        JsonObject body = body(List.of(Map.of("role", "user", "content", "summarize")), "[]");
        assertEquals(0, body.getAsJsonArray("tools").size());
    }
}
