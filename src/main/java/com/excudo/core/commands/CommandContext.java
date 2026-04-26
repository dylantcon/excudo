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
 * @param orchestrator    the active orchestrator the Command will mutate
 * @param displayAdapter  optional display sink for advisory output (e.g.
 *                        the overlap-warning notes from add-shape). Pass
 *                        null when the caller doesn't have one (most
 *                        non-REPL paths).
 */
public record CommandContext(PPTXOrchestrator orchestrator, Object displayAdapter) {}
