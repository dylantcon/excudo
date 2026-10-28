package com.excudo.core.llm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Transient memory for one arrange-mode session: the message history the
 * agent sees across turns, plus the deferred tools it has already
 * discovered. Lives only in memory -- created on entering arrange mode and
 * discarded on /exit or /clear.
 *
 * <p>Invariants kept by {@link AgenticLLMService}: the history starts with a
 * user turn whose content is a plain string (the request), roles alternate,
 * and every completed turn ends with an assistant message. A failed turn is
 * rolled back with {@link #rollbackTo(int)} so a dangling tool_use can never
 * reach the next request.
 */
public class ArrangeConversation {

    /** Oldest whole turns are dropped beyond this many. */
    static final int MAX_REMEMBERED_TURNS = 12;

    private final String id = UUID.randomUUID().toString();
    private final List<Map<String, Object>> messages = new ArrayList<>();
    private final Set<String> activeToolNames = new LinkedHashSet<>();
    private final List<String> pendingNotes = new ArrayList<>();

    // Command reference the agent has fetched, kept apart from the history:
    // it is static documentation, so it survives compaction, turn trimming,
    // rollback, and /clear. Access-ordered so the least recently fetched
    // entries go first when over the cap.
    static final int MAX_REFERENCE_COMMANDS = 30;
    private final Map<String, String> commandReference =
        new java.util.LinkedHashMap<>(16, 0.75f, true);
    private boolean commandListLoaded = false;
    private int turns = 0;
    private int totalInputTokens = 0;
    private int totalOutputTokens = 0;
    private double totalCost = 0.0;
    private int totalCacheReadTokens = 0;

    public String getId() { return id; }

    /** Live, mutable history; the agent loop appends to it directly. */
    List<Map<String, Object>> messages() { return messages; }

    /** Deferred tools activated via fetch_tool_schemas in earlier turns. */
    Set<String> activeToolNames() { return activeToolNames; }

    public int getTurnCount() { return turns; }
    public boolean isEmpty() { return messages.isEmpty(); }
    public int getTotalInputTokens() { return totalInputTokens; }
    public int getTotalOutputTokens() { return totalOutputTokens; }
    public double getTotalCost() { return totalCost; }
    public int getTotalCacheReadTokens() { return totalCacheReadTokens; }

    /**
     * Record something that happened outside the agent (e.g. the user ran a
     * console command between turns). Delivered at the start of the next
     * user turn so roles keep alternating.
     */
    public void addNote(String note) {
        if (note != null && !note.isBlank()) pendingNotes.add(note);
    }

    /** Pending notes as a prefix for the next request (kept until the turn succeeds). */
    String notesPrefix() {
        if (pendingNotes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("[Since your last turn:\n");
        for (String note : pendingNotes) sb.append("- ").append(note).append("\n");
        sb.append("]\n\n");
        return sb.toString();
    }

    void clearNotes() {
        pendingNotes.clear();
    }

    int checkpoint() { return messages.size(); }

    void rollbackTo(int checkpoint) {
        while (messages.size() > checkpoint) messages.remove(messages.size() - 1);
    }

    void completeTurn(int inputTokens, int outputTokens, double cost, int cacheReadTokens) {
        turns++;
        totalCacheReadTokens += cacheReadTokens;
        totalInputTokens += inputTokens;
        totalOutputTokens += outputTokens;
        totalCost += cost;
    }

    /**
     * Drop the oldest whole turns so at most {@code maxTurns} remain. A turn
     * starts at a user message whose content is a plain string (tool results
     * are user messages too, but carry block lists).
     */
    void trimToRecentTurns(int maxTurns) {
        List<Integer> turnStarts = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> m = messages.get(i);
            if ("user".equals(m.get("role")) && m.get("content") instanceof String) {
                turnStarts.add(i);
            }
        }
        if (turnStarts.size() <= maxTurns) return;
        int cut = turnStarts.get(turnStarts.size() - maxTurns);
        messages.subList(0, cut).clear();
    }

    /** Keep the reference entry for a command the agent looked up. */
    void rememberCommandReference(String commandName, String referenceText) {
        if (commandName == null || referenceText == null || referenceText.isBlank()) return;
        commandReference.put(commandName, referenceText.strip());
        while (commandReference.size() > MAX_REFERENCE_COMMANDS) {
            String eldest = commandReference.keySet().iterator().next();
            commandReference.remove(eldest);
        }
    }

    void markCommandListLoaded() {
        commandListLoaded = true;
    }

    public java.util.Set<String> getReferencedCommands() {
        return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(commandReference.keySet()));
    }

    boolean isCommandListLoaded() {
        return commandListLoaded;
    }

    /**
     * System-prompt section listing the reference already fetched, so the
     * agent doesn't spend a round re-fetching it every turn. Empty when
     * nothing has been fetched yet.
     */
    String referencePromptSection() {
        StringBuilder sb = new StringBuilder();
        if (commandListLoaded) {
            sb.append("COMMANDS AVAILABLE via execute_commands (list loaded earlier; no need to call list_commands): ")
              .append(String.join(", ", com.excudo.core.parsing.CommandRegistry.getLlmEnabledCommandNames()))
              .append("\n\n");
        }
        if (commandReference.isEmpty()) return sb.toString();
        sb.append("COMMAND REFERENCE ALREADY LOADED (fetched earlier in this conversation; ")
          .append("authoritative -- do not call get_command_schemas again for these):\n\n");
        // Iterate a copy: reading an access-ordered map through get() would reorder it.
        for (String text : new ArrayList<>(commandReference.values())) {
            sb.append(text).append("\n\n");
        }
        return sb.toString();
    }

    /** Forget the conversation (the /clear command). The command reference is kept. */
    public void clear() {
        messages.clear();
        activeToolNames.clear();
        pendingNotes.clear();
        turns = 0;
    }
}
