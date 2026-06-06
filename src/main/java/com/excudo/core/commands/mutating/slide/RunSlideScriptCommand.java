package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.meta.UndoCommand;
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
import java.util.Set;

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
    /** Final source-SPID -> target-SPID map populated during execute.
     *  Exposed for tests / tooling that need to renormalize a target
     *  snapshot back to source-SPID space (the
     *  {@code StrictRoundTrip} helper does this to assert full
     *  SlideState equality after apply). Empty when execute hasn't run
     *  or no Add* specs allocated SPIDs. */
    private Map<Integer, Integer> finalSpidMap = new HashMap<>();
    /** Runner-level warnings for specs skipped at apply time (e.g.
     *  cross-layout retarget where a referenced SPID doesn't exist on
     *  the target slide). Surfaced via {@link #getRuntimeWarnings} so
     *  the GUI / agent can display what was dropped. */
    private final List<String> runtimeWarnings = new ArrayList<>();
    private int skippedSpecCount = 0;

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

                // Cross-layout retarget guard: a spec may reference a
                // SPID that exists on the SOURCE slide (a placeholder
                // SPID baked in at synth time) but not on the TARGET
                // (different layout, different SPID space). Without this
                // check the command would throw with a low-level SPID-
                // not-found message and abort the whole script -- the
                // exact bug surfaced before this guard landed. Skip
                // with a visible warning so the rest of the script
                // still applies.
                Set<Integer> required = referencedSpids(rewritten);
                if (!required.isEmpty()) {
                    Set<Integer> live = liveSpids(slideNumber);
                    Set<Integer> missing = new java.util.LinkedHashSet<>();
                    for (Integer r : required) if (!live.contains(r)) missing.add(r);
                    if (!missing.isEmpty()) {
                        runtimeWarnings.add("Skipped " + rewritten.getClass().getSimpleName()
                            + " on target slide " + slideNumber
                            + ": references SPID(s) " + missing
                            + " not present on the target (cross-layout retarget).");
                        skippedSpecCount++;
                        continue;
                    }
                }

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
                } else if (rewritten instanceof CommandSpec.AddConnectorSpec connSpec
                        && cmd instanceof com.excudo.core.commands.mutating.slide.AddConnectorCommand connCmd) {
                    Integer allocated = connCmd.getCreatedSpid();
                    Integer source = connSpec.sourceSpidHint();
                    if (allocated != null && source != null) {
                        spidMap.put(source, allocated);
                    }
                } else if (rewritten instanceof CommandSpec.AddPictureSpec picSpec
                        && cmd instanceof com.excudo.core.commands.mutating.slide.AddPictureCommand picCmd) {
                    Integer allocated = picCmd.getCreatedSpid();
                    Integer source = picSpec.sourceSpidHint();
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
        finalSpidMap = Map.copyOf(spidMap);
        executed = true;
    }

    /** Source-SPID -> allocated-target-SPID map captured during execute.
     *  Returns an empty map before execute or after a rollback. */
    public Map<Integer, Integer> getSpidMap() {
        return finalSpidMap;
    }

    /** Specs the runner skipped at apply time (cross-layout retarget,
     *  missing SPIDs). Empty when every spec applied cleanly. */
    public List<String> getRuntimeWarnings() {
        return List.copyOf(runtimeWarnings);
    }

    /** Specs that applied vs. specs skipped. {@link #getAppliedSpecCount}
     *  reports the successful subset; this reports the runner-skipped
     *  ones for cross-layout introspection. */
    public int getSkippedSpecCount() {
        return skippedSpecCount;
    }

    /** SPIDs the rewritten spec references on the target slide. Empty
     *  for SPID-creating specs (Add*, Create*) -- their primary SPID
     *  is freshly allocated, never validated against the target. */
    private static Set<Integer> referencedSpids(CommandSpec spec) {
        return switch (spec) {
            case CommandSpec.AddShapeSpec s        -> Set.of();
            case CommandSpec.AddPictureSpec s      -> Set.of();
            case CommandSpec.CreateCodeBoxSpec s   -> Set.of();
            case CommandSpec.CreateDiagramSpec s   -> Set.of();
            case CommandSpec.AddConnectorSpec s    -> nonNullSpids(s.startSpid(), s.endSpid());
            case CommandSpec.RemoveShapeSpec s     -> Set.of(s.spid());
            case CommandSpec.MoveSpec s            -> Set.of(s.spid());
            case CommandSpec.ResizeSpec s          -> Set.of(s.spid());
            case CommandSpec.RotateSpec s          -> Set.of(s.spid());
            case CommandSpec.RenameShapeSpec s     -> Set.of(s.spid());
            case CommandSpec.SetTextBoxFlagSpec s  -> Set.of(s.spid());
            case CommandSpec.SetRunFormatSpec s    -> Set.of(s.spid());
            case CommandSpec.SetTextSpec s         -> Set.of(s.spid());
            case CommandSpec.SetShapeStyleSpec s   -> Set.of(s.spid());
            case CommandSpec.ReorderSpec s         -> Set.of(s.spid());
            case CommandSpec.AddAnimationSpec s    -> Set.of(s.binding().getTargetSpid());
            case CommandSpec.RemoveAnimationSpec s -> Set.of();
            case CommandSpec.SetAnimationTimingSpec s -> Set.of();
            case CommandSpec.SetTransitionSpec s   -> Set.of();
            case CommandSpec.ClearTransitionSpec s -> Set.of();
            case CommandSpec.CreateGroupSpec s     -> Set.copyOf(s.childSpids());
            case CommandSpec.UngroupSpec s         -> Set.of(s.groupSpid());
            case CommandSpec.AddToGroupSpec s      -> Set.of(s.groupSpid(), s.childSpid());
            case CommandSpec.DetachFromGroupSpec s -> Set.of(s.childSpid());
        };
    }

    private static Set<Integer> nonNullSpids(Integer... vs) {
        Set<Integer> out = new java.util.LinkedHashSet<>();
        for (Integer v : vs) if (v != null) out.add(v);
        return out;
    }

    /** Read the SPIDs currently present on the target slide's
     *  shape registry. Re-parses on demand so any SPIDs added by
     *  earlier specs in this same script are visible. */
    private Set<Integer> liveSpids(int slideNumber) {
        var ctxOpt = orchestrator.getContext();
        if (ctxOpt.isEmpty()) return Set.of();
        var doc = ctxOpt.get().getDocument();
        if (doc == null) return Set.of();
        var parsed = doc.getParsedSlideData(slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        if (parsed == null || parsed.getShapeRegistry() == null) return Set.of();
        Set<Integer> out = new java.util.LinkedHashSet<>();
        for (var s : parsed.getShapeRegistry().getAllShapes()) out.add(s.getSpid());
        return out;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
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
