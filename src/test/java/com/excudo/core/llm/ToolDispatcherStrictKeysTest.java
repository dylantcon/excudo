package com.excudo.core.llm;

import com.excudo.core.commands.RequestSchema;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Pins the strict-keys validation in {@code execute_commands}. The bug
 * class this guards against came out of the 2026-04-22 beta sessions:
 *
 * <ul>
 *   <li>{@code {"type":"show-shape"}} returned OK and no-op'd because
 *       show-shape is in the registry (REPL command) but not LLM-enabled.</li>
 *   <li>{@code move {"targetSpid":N,"slideNumber":1,...}} returned OK and
 *       no-op'd because the canonical key was {@code spid} and the bridge
 *       passed unknown keys through to the factory which then read null.</li>
 * </ul>
 *
 * After the strict-keys pass and the {@code .llmName("targetSpid")}
 * backfill on move/resize/reorder, both payloads must now fail loudly
 * with actionable error messages.
 */
public class ToolDispatcherStrictKeysTest {

    @Test
    public void unknownActionTypeIsRejectedWithFuzzyMatch() {
        RequestSchema.ActionRequest action = action("ad-shape", Map.of(
            "slideNumber", "1",
            "shapeType", "RECTANGLE"));

        String err = ToolDispatcher.validateActionStrictly(action);

        assertNotNull("unknown action type must reject", err);
        assertTrue(err, err.contains("Unknown command type 'ad-shape'"));
        // FuzzyMatcher should suggest add-shape
        assertTrue(err, err.contains("add-shape"));
    }

    @Test
    public void replOnlyCommandIsRejectedFromExecuteCommands() {
        // show-shape is in CommandRegistry (REPL display command) but
        // .llmEnabled defaults to false. Calling it via execute_commands
        // used to return OK and silently no-op.
        RequestSchema.ActionRequest action = action("show-shape", Map.of(
            "slide", "1",
            "spid", "2"));

        String err = ToolDispatcher.validateActionStrictly(action);

        assertNotNull("REPL-only commands must reject", err);
        assertTrue(err, err.contains("REPL/internal command"));
    }

    @Test
    public void unknownParameterKeyIsRejectedWithFuzzyMatch() {
        // 'targetSpd' is a typo of 'targetSpid' (the llmName alias).
        RequestSchema.ActionRequest action = action("set-font", Map.of(
            "slideNumber", "1",
            "targetSpd", "2",          // typo
            "fontFamily", "Arial"));

        String err = ToolDispatcher.validateActionStrictly(action);

        assertNotNull("typo'd key must reject", err);
        assertTrue(err, err.contains("Unknown parameter"));
        assertTrue(err, err.contains("targetSpd"));
        // FuzzyMatcher should suggest targetSpid
        assertTrue(err, err.contains("targetSpid"));
    }

    @Test
    public void moveAcceptsTargetSpidAfterAliasBackfill() {
        // Pre-fix: 'move' had no llmName alias, so this payload's
        // 'targetSpid' was treated as an unknown key, passed through to
        // the factory, and silently dropped. Post-backfill, the alias
        // resolves to canonical 'spid' and validates clean.
        RequestSchema.ActionRequest action = action("move", Map.of(
            "slideNumber", "1",
            "targetSpid", "2",
            "x", "100pt",
            "y", "200pt"));

        String err = ToolDispatcher.validateActionStrictly(action);

        assertNull("move targetSpid must validate after backfill", err);
    }

    @Test
    public void resizeAcceptsTargetSpidAfterAliasBackfill() {
        RequestSchema.ActionRequest action = action("resize", Map.of(
            "slideNumber", "1",
            "targetSpid", "2",
            "width", "400pt",
            "height", "300pt"));

        String err = ToolDispatcher.validateActionStrictly(action);

        assertNull(err);
    }

    @Test
    public void reorderAcceptsTargetSpidAfterAliasBackfill() {
        RequestSchema.ActionRequest action = action("reorder", Map.of(
            "slideNumber", "1",
            "targetSpid", "2",
            "direction", "front"));

        String err = ToolDispatcher.validateActionStrictly(action);

        assertNull(err);
    }

    @Test
    public void setStyleAcceptsCamelCaseColorNames() {
        // Pre-fix: 'set-style' only declared 'fill-color' / 'line-color'
        // (canonical hyphenated). After llmName backfill, the camelCase
        // 'fillColor'/'lineColor' (matching add-shape) is accepted.
        RequestSchema.ActionRequest action = action("set-style", Map.of(
            "slideNumber", "1",
            "targetSpid", "2",
            "fillColor", "#FF5733",
            "lineColor", "accent1"));

        String err = ToolDispatcher.validateActionStrictly(action);

        assertNull(err);
    }

    @Test
    public void canonicalKeyNamesStillAcceptedAlongsideLlmAliases() {
        // The canonical 'spid' must still validate; this is used by the
        // REPL parser path and we don't want to break it just because
        // the LLM-facing alias is preferred for the agent surface.
        RequestSchema.ActionRequest action = action("move", Map.of(
            "slide", "1",
            "spid", "2",
            "x", "100pt",
            "y", "200pt"));

        String err = ToolDispatcher.validateActionStrictly(action);

        assertNull(err);
    }

    @Test
    public void missingTypeFieldRejectsCleanly() {
        RequestSchema.ActionRequest action = action(null, Map.of("slideNumber", "1"));

        String err = ToolDispatcher.validateActionStrictly(action);

        assertNotNull(err);
        assertTrue(err, err.contains("Missing 'type' field"));
    }

    private static RequestSchema.ActionRequest action(String type, Map<String, ?> params) {
        Map<String, Object> p = new HashMap<>(params);
        return new RequestSchema.ActionRequest(type, p, null, null);
    }
}
