package com.excudo.core.synthesis;

import com.excudo.core.commands.mutating.slide.AddShapeCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.synthesis.spec.CommandSpec;
import com.excudo.core.synthesis.spec.SpecToCommandMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Execute a {@link CommandScript} against an orchestrator, applying
 * per-spec SPID remapping so specs that reference source-slide SPIDs
 * get rewritten to the post-add target-slide SPIDs before execution.
 *
 * <p>The remapping story is why this class can't just build a
 * {@code CompositeCommand} up front: the runner needs to capture each
 * {@link AddShapeCommand}'s allocated SPID and use it to rewrite
 * subsequent specs in the topo order. So the runner manages its own
 * execution loop + undo stack, matching {@code CompositeCommand}'s
 * rollback behavior.
 */
public final class ScriptRunner {

    private final PPTXOrchestrator orchestrator;
    private final SpecToCommandMapper mapper;

    public ScriptRunner(PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("orchestrator must not be null");
        }
        this.orchestrator = orchestrator;
        this.mapper = new SpecToCommandMapper(orchestrator);
    }

    /**
     * Topo-sort the script, execute each spec after rewriting any
     * SPID references, and accumulate source-SPID &rarr; target-SPID
     * mappings as {@code AddShapeSpec}s allocate fresh SPIDs. On any
     * failure, every successfully-executed command is undone in
     * reverse order.
     */
    public ExecutionResult<Void> run(CommandScript script, String description) {
        if (script == null) {
            return ExecutionResult.failure("ScriptRunner",
                "script must not be null");
        }
        if (script.isEmpty()) {
            return ExecutionResult.success("ScriptRunner", null);
        }
        String desc = (description == null || description.isBlank())
            ? "Run CommandScript (" + script.size() + " specs)"
            : description;

        List<CommandSpec> order = script.topologicalOrder();
        Map<Integer, Integer> spidMap = new HashMap<>();
        List<Command> executed = new ArrayList<>(order.size());

        try {
            for (CommandSpec original : order) {
                CommandSpec rewritten = SpecRewriter.rewrite(original, spidMap);
                Command cmd = mapper.toCommand(rewritten);
                cmd.execute();
                executed.add(cmd);

                // If the just-executed spec allocated a fresh SPID,
                // record the mapping so downstream specs referencing
                // the original's source SPID get rewritten.
                if (rewritten instanceof CommandSpec.AddShapeSpec addSpec
                        && cmd instanceof AddShapeCommand addCmd) {
                    Integer allocated = addCmd.getCreatedSpid();
                    Integer source = addSpec.sourceSpidHint();
                    if (allocated != null && source != null) {
                        spidMap.put(source, allocated);
                    }
                }
                // CreateCodeBoxSpec creates a tagged group + 2 children;
                // downstream specs reference the GROUP, so we map source
                // group SPID -> the freshly allocated group SPID. The
                // children's SPIDs aren't visible to downstream specs
                // that survived synthesis (the synthesizer consumed them).
                if (rewritten instanceof CommandSpec.CreateCodeBoxSpec ccbSpec
                        && cmd instanceof com.excudo.core.commands.mutating.slide.CreateCodeBoxCommand ccbCmd) {
                    Integer allocatedGroup = ccbCmd.getGroupSpid();
                    Integer source = ccbSpec.sourceSpidHint();
                    if (allocatedGroup != null && source != null) {
                        spidMap.put(source, allocatedGroup);
                    }
                }
            }
            return ExecutionResult.success("ScriptRunner", null);
        } catch (CommandExecutionException e) {
            rollback(executed);
            return ExecutionResult.failure("ScriptRunner",
                "Script failed: " + e.getMessage() + " -- rolled back " + executed.size()
                    + " prior command(s).", e);
        } catch (RuntimeException e) {
            rollback(executed);
            return ExecutionResult.failure("ScriptRunner",
                "Script failed (runtime): " + e.getMessage() + " -- rolled back "
                    + executed.size() + " prior command(s).", e);
        }
    }

    /** Undo every command in reverse order of execution. Best-effort:
     *  an undo failure on one command is logged but doesn't stop the
     *  walk, because leaving MORE commands un-undone would compound
     *  the problem. Matches {@code CompositeCommand.undo()} semantics. */
    private void rollback(List<Command> executed) {
        for (int i = executed.size() - 1; i >= 0; i--) {
            Command cmd = executed.get(i);
            if (!cmd.isExecuted()) continue;
            try {
                cmd.undo();
            } catch (RuntimeException rollbackFailure) {
                // Log and continue -- the next command in the stack
                // might still undo successfully. This preserves as much
                // of the pre-script state as possible.
                System.err.println("ScriptRunner rollback step failed for "
                    + cmd.getDescription() + ": " + rollbackFailure.getMessage());
            }
        }
    }

    /** Unwind an already-run script. Delegates to the composite's undo;
     *  requires {@link #run} to have succeeded on the same composite
     *  instance, which this method doesn't track -- callers manage the
     *  pairing when they need undo semantics. For the synthesizer's
     *  normal revert-to-baseline flow, see {@code ClearSlideContentCommand}
     *  (Tier 4.8), which calls {@link #run} with the INVERSE script
     *  rather than undoing the forward one. */
    @SuppressWarnings("unused")
    private static final String UNDO_NOTE = "";
}
