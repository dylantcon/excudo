package com.excudo.core.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A render_slide tool_result carries text + image parts. OpenAI-format tool
 * messages are text-only, so the image must follow the run of tool messages
 * as a user image_url message instead of breaking the request.
 */
public class OpenRouterImageToolResultTest {

    @Test
    @DisplayName("image tool_result -> text tool message, then a user image message after the tool run")
    void imageMovesAfterToolMessages() {
        String assistant = "[{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"render_slide\",\"input\":{}},"
            + "{\"type\":\"tool_use\",\"id\":\"t2\",\"name\":\"get_slide_shapes\",\"input\":{}}]";
        String renderResult = "[{\"type\":\"tool_result\",\"tool_use_id\":\"t1\",\"content\":["
            + "{\"type\":\"text\",\"text\":\"Rendered slide 1\"},"
            + "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/png\",\"data\":\"QUJD\"}}]}]";
        String shapesResult = "[{\"type\":\"tool_result\",\"tool_use_id\":\"t2\",\"content\":\"2 shapes\"}]";

        OpenRouterClient client = new OpenRouterClient("key", "some/model");
        String body = client.buildRequest("sys", List.of(
            Map.of("role", "user", "content", "render it"),
            Map.of("role", "assistant", "content", assistant),
            Map.of("role", "user", "content", renderResult),
            Map.of("role", "user", "content", shapesResult)), null);

        JsonArray msgs = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("messages");
        // system, user, assistant, tool t1, tool t2, user(image)
        assertEquals(6, msgs.size());
        JsonObject t1 = msgs.get(3).getAsJsonObject();
        assertEquals("tool", t1.get("role").getAsString());
        assertEquals("Rendered slide 1", t1.get("content").getAsString());
        assertEquals("tool", msgs.get(4).getAsJsonObject().get("role").getAsString());

        JsonObject image = msgs.get(5).getAsJsonObject();
        assertEquals("user", image.get("role").getAsString());
        JsonObject part = image.getAsJsonArray("content").get(1).getAsJsonObject();
        assertEquals("image_url", part.get("type").getAsString());
        assertEquals("data:image/png;base64,QUJD",
            part.getAsJsonObject("image_url").get("url").getAsString());
    }
}
