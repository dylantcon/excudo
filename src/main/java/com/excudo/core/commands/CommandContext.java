package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;

/**
 * Ambient services threaded into Command construction.
 *
 * <p>{@link com.excudo.core.parsing.CommandParameters} carries the per-call
 * parameter values (slide number, shape type, geometry, ...). It does NOT
 * carry the contextual references every Command needs to do useful work —
 * the orchestrator that actually mutates the presentation, the optional
 * display adapter that warnings/notes get routed to. Those live here.
 *
 * <p>Used by the class-keyed registry pattern: each Command class declares
 *
 * <pre>{@code
 * public static Command fromParameters(CommandParameters p, CommandContext ctx) { ... }
 * }</pre>
 *
 * <p>The split between parameter values and ambient services is intentional
 * — it keeps {@link com.excudo.core.parsing.CommandParameters} a pure value
 * object (easy to construct in tests, no orchestrator needed) and lets the
 * factory inject the orchestrator separately.
 *
 * <p>The REPL {@code displayAdapter} is a console "god object" that
 * implements {@link CommandDisplay}, {@link CommandSessionContext},
 * {@link CommandSessionManager}, and {@link LLMContext} simultaneously.
 * Commands that need one of those capabilities pull it via the typed
 * {@code require*()} accessors below, which cast the adapter and throw a
 * clear message when the current dispatch path didn't supply one (the LLM
 * path passes {@code null}, so session/manager/LLM-only commands fail loudly
 * rather than with a raw {@link ClassCastException}).
 *
 * @param orchestrator    the active orchestrator the Command will mutate
 * @param displayAdapter  optional multi-capability adapter for advisory
 *                        output, session/history, and LLM context. Pass null
 *                        when the caller doesn't have one (most non-REPL paths).
 */
public record CommandContext(PPTXOrchestrator orchestrator, Object displayAdapter,
                              com.excudo.xml.writers.animations.GroupIdManager groupIdManager) {

    /** Two-arg back-compat constructor; null group-id manager. Used by callers
     *  (tests, non-REPL paths) that don't need animation group ID allocation. */
    public CommandContext(PPTXOrchestrator orchestrator, Object displayAdapter) {
        this(orchestrator, displayAdapter, null);
    }

    /** The display sink, or null when none was supplied. */
    public CommandDisplay display() {
        return displayAdapter instanceof CommandDisplay d ? d : null;
    }

    /** Display sink, required. */
    public CommandDisplay requireDisplay() {
        if (displayAdapter instanceof CommandDisplay d) return d;
        throw new IllegalStateException(
            "This command requires a display adapter; none on this dispatch path.");
    }

    /** Session/history context (undo/redo/history/save/session commands). */
    public CommandSessionContext requireSession() {
        if (displayAdapter instanceof CommandSessionContext s) return s;
        throw new IllegalStateException(
            "This command requires a REPL session context; none on this dispatch path.");
    }

    /** Session lifecycle manager (load/new/session-* commands). */
    public CommandSessionManager requireSessionManager() {
        if (displayAdapter instanceof CommandSessionManager m) return m;
        throw new IllegalStateException(
            "This command requires a session manager; none on this dispatch path.");
    }

    /** LLM context (llm / llm-config commands). */
    public LLMContext requireLlmContext() {
        if (displayAdapter instanceof LLMContext c) return c;
        throw new IllegalStateException(
            "LLM commands require LLMContext support; none on this dispatch path.");
    }

    /** Icon context (icon command). */
    public IconContext requireIconContext() {
        if (displayAdapter instanceof IconContext c) return c;
        throw new IllegalStateException(
            "Icon commands require IconContext support; none on this dispatch path.");
    }
}
