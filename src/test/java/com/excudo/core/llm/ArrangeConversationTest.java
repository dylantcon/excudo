package com.excudo.core.llm;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Arrange-mode memory: history carries across turns, failed turns roll
 * back cleanly, and the request sent to the API stays well-formed.
 */
public class ArrangeConversationTest {

    /** Replays scripted rounds and records every message list it was sent. */
    private static class ScriptedClient extends LLMClient {
        final Deque<Object> script = new ArrayDeque<>();
        final List<List<Map<String, String>>> sent = new ArrayList<>();
        final List<String> toolsSent = new ArrayList<>();
        final List<String> systemPrompts = new ArrayList<>();

        ScriptedClient() { super("stub-key", "stub-model"); }

        ScriptedClient text(String reply) {
            script.add(List.of(new APIResponse("text", reply, null, null, null)));
            return this;
        }

        ScriptedClient toolUse(String name, String inputJson, String id) {
            script.add(List.of(new APIResponse("tool_use", null, name, inputJson, id)));
            return this;
        }

        ScriptedClient fail(int status) {
            script.add(new LLMApiException("scripted failure", status));
            return this;
        }

        @Override
        public List<APIResponse> sendMessageWithTools(String systemPrompt,
                                                      List<Map<String, String>> messages,
                                                      String toolsJson) {
            List<Map<String, String>> copy = new ArrayList<>();
            for (Map<String, String> m : messages) copy.add(new HashMap<>(m));
            sent.add(copy);
            toolsSent.add(toolsJson);
            systemPrompts.add(systemPrompt);
            Object next = script.poll();
            if (next instanceof LLMApiException e) throw e;
            @SuppressWarnings("unchecked")
            List<APIResponse> responses = (List<APIResponse>) next;
            return responses != null ? responses : List.of(new APIResponse("text", "done", null, null, null));
        }

        @Override
        public ExecutionResult<String> sendMessage(String systemPrompt, String userMessage) {
            return ExecutionResult.success("stub", "unused");
        }

        @Override
        public String getProviderName() { return "Scripted"; }
    }

    private static AgenticLLMService service(ScriptedClient client, ArrangeConversation convo) {
        StubPPTXOrchestrator stub = new StubPPTXOrchestrator();
        AgenticLLMService s = new AgenticLLMService(client, stub, new CommandFactory(stub), new CommandInvoker());
        s.setConversation(convo);
        return s;
    }

    private static void assertAlternates(List<Map<String, String>> messages) {
        assertEquals("user", messages.get(0).get("role"), "history must start with a user turn");
        for (int i = 1; i < messages.size(); i++) {
            assertNotEquals(messages.get(i - 1).get("role"), messages.get(i).get("role"),
                "roles must alternate at index " + i);
        }
    }

    @Test
    void secondTurnSeesFirstTurn() {
        ScriptedClient client = new ScriptedClient().text("Made the title slide.").text("Renamed it.");
        ArrangeConversation convo = new ArrangeConversation();

        service(client, convo).processRequestWithUsage("Add a title slide");
        service(client, convo).processRequestWithUsage("Now rename it");

        List<Map<String, String>> turn2 = client.sent.get(1);
        assertEquals(3, turn2.size(), "turn 2 = turn-1 request, turn-1 reply, turn-2 request");
        assertTrue(turn2.get(0).get("content").contains("Add a title slide"));
        assertEquals("Made the title slide.", turn2.get(1).get("content"));
        assertTrue(turn2.get(2).get("content").contains("Now rename it"));
        assertAlternates(turn2);
        assertEquals(2, convo.getTurnCount());
    }

    @Test
    void withoutConversationEachRequestStartsFresh() {
        ScriptedClient client = new ScriptedClient().text("one").text("two");
        service(client, null).processRequestWithUsage("first");
        service(client, null).processRequestWithUsage("second");
        assertEquals(1, client.sent.get(1).size());
    }

    @Test
    void toolRoundsAndDiscoveredToolsCarryOver() {
        String deferred = LLMToolDefinitions.getDeferredTools().get(0).name();
        ScriptedClient client = new ScriptedClient()
            .toolUse("fetch_tool_schemas", "{\"tool_names\":[\"" + deferred + "\"]}", "toolu_1")
            .text("Fetched it.")
            .text("Still here.");
        ArrangeConversation convo = new ArrangeConversation();

        service(client, convo).processRequestWithUsage("Get that tool");
        service(client, convo).processRequestWithUsage("Thanks");

        List<Map<String, String>> turn2 = client.sent.get(client.sent.size() - 1);
        assertAlternates(turn2);
        assertTrue(turn2.stream().anyMatch(m -> m.get("content").contains("toolu_1")),
            "turn 2 should still contain the turn-1 tool exchange");
        assertTrue(convo.activeToolNames().contains(deferred));
        assertTrue(client.toolsSent.get(client.toolsSent.size() - 1).contains(deferred),
            "a tool discovered in turn 1 stays available in turn 2");
    }

    @Test
    void failedTurnRollsBackAndKeepsHistoryValid() {
        ScriptedClient client = new ScriptedClient()
            .text("First done.")
            .toolUse("fetch_tool_schemas", "{\"tool_names\":[]}", "toolu_x")
            .fail(529)
            .text("Recovered.");
        ArrangeConversation convo = new ArrangeConversation();

        service(client, convo).processRequestWithUsage("first");
        int afterFirst = convo.messages().size();

        assertThrows(LLMApiException.class,
            () -> service(client, convo).processRequestWithUsage("this one fails mid-turn"));
        assertEquals(afterFirst, convo.messages().size(), "failed turn is rolled back entirely");

        service(client, convo).processRequestWithUsage("try again");
        List<Map<String, String>> retry = client.sent.get(client.sent.size() - 1);
        assertAlternates(retry);
        assertFalse(retry.stream().anyMatch(m -> m.get("content").contains("toolu_x")),
            "no dangling tool_use from the failed turn");
        assertEquals(2, convo.getTurnCount());
    }

    @Test
    void notesReachTheNextRequestOnce() {
        ScriptedClient client = new ScriptedClient().text("a").text("b").text("c");
        ArrangeConversation convo = new ArrangeConversation();

        service(client, convo).processRequestWithUsage("first");
        convo.addNote("The user ran the console command: delete 2");
        service(client, convo).processRequestWithUsage("second");
        service(client, convo).processRequestWithUsage("third");

        List<Map<String, String>> turn2 = client.sent.get(1);
        assertTrue(turn2.get(turn2.size() - 1).get("content").contains("delete 2"));
        List<Map<String, String>> turn3 = client.sent.get(2);
        assertFalse(turn3.get(turn3.size() - 1).get("content").contains("delete 2"),
            "a note is delivered once");
    }

    @Test
    void oldTurnsAreTrimmedOnTurnBoundaries() {
        ScriptedClient client = new ScriptedClient();
        ArrangeConversation convo = new ArrangeConversation();
        int turns = ArrangeConversation.MAX_REMEMBERED_TURNS + 3;
        for (int i = 0; i < turns; i++) {
            client.text("reply " + i);
            service(client, convo).processRequestWithUsage("request " + i);
        }
        List<Map<String, String>> last = client.sent.get(client.sent.size() - 1);
        assertAlternates(last);
        assertFalse(last.get(0).get("content").contains("request 0"), "oldest turn dropped");
        assertTrue(last.get(last.size() - 1).get("content").contains("request " + (turns - 1)));
    }

    @Test
    void jsonLookingTextBlocksStayStrings() {
        // Previously any {...} string field was parsed into a JSON object, which
        // the API rejects for text / tool_result content.
        ScriptedClient client = new ScriptedClient();
        client.script.add(List.of(
            new LLMClient.APIResponse("text", "{\"plan\":1}", null, null, null),
            new LLMClient.APIResponse("tool_use", null, "fetch_tool_schemas",
                "{\"tool_names\":[]}", "toolu_t")));
        client.text("ok");
        service(client, new ArrangeConversation()).processRequestWithUsage("go");

        String assistantBlocks = client.sent.get(1).get(1).get("content");
        assertTrue(assistantBlocks.contains("\"text\":\"{\\\"plan\\\":1}\""),
            "text block must stay a string: " + assistantBlocks);
        assertTrue(assistantBlocks.contains("\"input\":{\"tool_names\":[]}"),
            "tool_use input must be a JSON object: " + assistantBlocks);
    }

    @Test
    void fetchedCommandReferenceIsKeptSeparatelyAcrossTurns() {
        ScriptedClient client = new ScriptedClient()
            .toolUse("get_command_schemas", "{\"commands\":[\"add-shape\"]}", "toolu_s")
            .text("Looked it up.")
            .text("Second turn.");
        ArrangeConversation convo = new ArrangeConversation();

        service(client, convo).processRequestWithUsage("What does add-shape take?");
        assertFalse(client.systemPrompts.get(0).contains("COMMAND REFERENCE ALREADY LOADED"),
            "nothing loaded before the first lookup");

        service(client, convo).processRequestWithUsage("Thanks");
        String turn2Prompt = client.systemPrompts.get(client.systemPrompts.size() - 1);
        assertTrue(turn2Prompt.contains("COMMAND REFERENCE ALREADY LOADED"));
        assertTrue(turn2Prompt.contains("add-shape:"), "add-shape reference carried into turn 2");
        assertEquals(java.util.Set.of("add-shape"), convo.getReferencedCommands());
    }

    @Test
    void referenceSurvivesRollbackAndClear() {
        ScriptedClient client = new ScriptedClient()
            .toolUse("get_command_schemas", "{\"commands\":[\"set-font\"]}", "toolu_r")
            .fail(503)
            .text("after clear");
        ArrangeConversation convo = new ArrangeConversation();

        assertThrows(LLMApiException.class,
            () -> service(client, convo).processRequestWithUsage("fails after lookup"));
        assertTrue(convo.messages().isEmpty(), "history rolled back");
        assertTrue(convo.getReferencedCommands().contains("set-font"), "reference kept through rollback");

        convo.clear();
        service(client, convo).processRequestWithUsage("fresh start");
        assertTrue(client.systemPrompts.get(client.systemPrompts.size() - 1).contains("set-font:"),
            "reference kept through /clear");
    }

    @Test
    void referenceIsCapped() {
        ArrangeConversation convo = new ArrangeConversation();
        for (int i = 0; i < ArrangeConversation.MAX_REFERENCE_COMMANDS + 5; i++) {
            convo.rememberCommandReference("cmd-" + i, "cmd-" + i + ":\n  x (STRING)");
        }
        assertEquals(ArrangeConversation.MAX_REFERENCE_COMMANDS, convo.getReferencedCommands().size());
        assertFalse(convo.getReferencedCommands().contains("cmd-0"), "least recently fetched dropped first");
    }

    @Test
    void clearForgetsEverything() {
        ScriptedClient client = new ScriptedClient().text("a").text("b");
        ArrangeConversation convo = new ArrangeConversation();
        service(client, convo).processRequestWithUsage("first");
        convo.clear();
        service(client, convo).processRequestWithUsage("second");
        assertEquals(1, client.sent.get(1).size());
    }
}
