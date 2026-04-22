package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CompositeCommand;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.synthesis.CommandScript;
import com.excudo.core.synthesis.RetargetToSlide;
import com.excudo.core.synthesis.SpecRewriter;
import com.excudo.core.synthesis.spec.CommandSpec;
import com.excudo.core.synthesis.spec.CommandSpecJson;
import com.excudo.core.synthesis.spec.SpecToCommandMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Apply a serialized CommandSpec script to a target slide.
 *
 * <p>Workflow (the reason this command exists as first-class): pair
 * with {@link SynthesizeSlideScriptCommand} to clone a slide with
 * narrow overrides. Synthesize slide S (get the JSON array of specs),
 * edit the entries that should differ on the new slide, then run this
 * command with the modified JSON targeting a fresh slide. Invariants
 * reproduce exactly; only the overridden specs diverge. Far less agent
 * effort than hand-composing every invariant shape.
 *
 * <p>Each spec's {@code slideNumber} is rewritten to the target slide
 * before execution, so the same JSON can be reused across as many
 * slides as needed without edits to the slide-number field.
 *
 * <p>SPID remapping is automatic (matches {@code ScriptRunner}): each
 * {@code AddShapeSpec}'s allocated target SPID is captured and
 * downstream specs referencing the source SPID are rewritten.
 *
 * <p>Undo: walks the per-spec executed commands in reverse order
 * calling {@code undo()} on each. The command retains the executed
 * list for the lifetime of the invoker's history, so redo (re-execute)
 * works too.
 */
public class RunSlideScriptCommand implements Command {

    private final int slideNumber;
    private final String scriptJson;
    private final PPTXOrchestrator orchestrator;
    private final Object displayAdapter;
    private final SpecToCommandMapper mapper;

    private boolean executed = false;
    private List<Command> executedInOrder = new ArrayList<>();
    private int appliedSpecCount = 0;

    public RunSlideScriptCommand(int slideNumber, String scriptJson,
                                 PPTXOrchestrator orchestrator, Object displayAdapter) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        if (scriptJson == null || scriptJson.isBlank()) {
            throw new IllegalArgumentException("Script JSON must not be empty");
        }
        this.slideNumber = slideNumber;
        this.scriptJson = scriptJson;
        this.orchestrator = orchestrator;
        this.displayAdapter = displayAdapter;
        this.mapper = new SpecToCommandMapper(orchestrator);
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }
        List<CommandSpec> specs;
        try {
            specs = CommandSpecJson.fromJsonArray(scriptJson);
        } catch (com.google.gson.JsonParseException e) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to parse script JSON: " + e.getMessage(), e);
        }
        if (specs.isEmpty()) {
            // No-op: treat as a successful run that did nothing.
            executed = true;
            return;
        }

        // Retarget every spec to this command's target slide so the
        // same serialized script can drive any slide.
        List<CommandSpec> retargeted = new ArrayList<>(specs.size());
        for (CommandSpec s : specs) {
            retargeted.add(RetargetToSlide.retarget(s, slideNumber));
        }

        CommandScript script = CommandScript.ofFlat(retargeted);
        List<CommandSpec> order = script.topologicalOrder();

        // Same SPID remapping pipeline as ScriptRunner -- duplicated
        // here so this command owns its executed-list for undo rather
        // than depending on ScriptRunner's opaque return.
        Map<Integer, Integer> spidMap = new HashMap<>();
        List<Command> executedLocal = new ArrayList<>();

        try {
            for (CommandSpec original : order) {
                CommandSpec rewritten = SpecRewriter.rewrite(original, spidMap);
                Command cmd = mapper.toCommand(rewritten);
                cmd.execute();
                executedLocal.add(cmd);
                if (rewritten instanceof CommandSpec.AddShapeSpec addSpec
                        && cmd instanceof AddShapeCommand addCmd) {
                    Integer allocated = addCmd.getCreatedSpid();
                    Integer source = addSpec.sourceSpidHint();
                    if (allocated != null && source != null) {
                        spidMap.put(source, allocated);
                    }
                }
                appliedSpecCount++;
            }
        } catch (RuntimeException failure) {
            rollback(executedLocal);
            appliedSpecCount = 0;
            throw failure instanceof CommandExecutionException cee ? cee
                : new CommandExecutionException(getDescription(), "execute",
                    "Script failed: " + failure.getMessage(), failure);
        }
        executedInOrder = executedLocal;
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Command has not been executed");
        }
        rollback(executedInOrder);
        executedInOrder = new ArrayList<>();
        appliedSpecCount = 0;
        executed = false;
    }

    /** Walk the executed list in reverse, undoing each step. Best-effort:
     *  a single undo failure doesn't stop the rest (matches
     *  CompositeCommand semantics), since leaving the remaining steps
     *  undone would compound state drift. */
    private void rollback(List<Command> executed) {
        for (int i = executed.size() - 1; i >= 0; i--) {
            Command c = executed.get(i);
            if (!c.isExecuted()) continue;
            if (!c.canUndo()) continue;
            try {
                c.undo();
            } catch (RuntimeException ignored) {
                // Continue; caller already knows the script is in a bad state.
            }
        }
    }

    @Override public boolean canUndo() { return executed && !executedInOrder.isEmpty(); }
    @Override public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "RunSlideScript(target=" + slideNumber + ", "
            + (appliedSpecCount == 0 ? "pending" : appliedSpecCount + " specs") + ")";
    }

    public int getAppliedSpecCount() { return appliedSpecCount; }

    // Keep displayAdapter referenced so the signature stays stable if
    // a future iteration surfaces per-spec progress to the display.
    @SuppressWarnings("unused")
    private Object displayAdapterSink() { return displayAdapter; }
}
