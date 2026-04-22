package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.synthesis.CommandScript;
import com.excudo.core.synthesis.ScriptRunner;
import com.excudo.core.synthesis.ShapeSnapshot;
import com.excudo.core.synthesis.SlideState;
import com.excudo.core.synthesis.SlideStateBuilder;
import com.excudo.core.synthesis.spec.CommandSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Revert a slide to its layout baseline: remove every user-added shape,
 * every animation, and any slide-level transition override. Placeholders
 * that came from the layout are preserved.
 *
 * <p>Built on top of the synthesizer's state infrastructure: the command
 * diffs (baseline &larr; current), inverts the resulting spec set into
 * removal specs, and hands them to {@link ScriptRunner}. Undo replays
 * the pre-clear state by emitting the add-specs implied by the
 * snapshot. "Clear-slide-content is a UX wrapper around
 * synthesize-then-invert-then-run," and that's literally what this
 * class does.
 */
public class ClearSlideContentCommand implements Command {

    private final int slideNumber;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    /** Snapshot of the current state taken at execute time. Used to
     *  rebuild an add-script for undo. */
    private SlideState preClearSnapshot = null;

    public ClearSlideContentCommand(int slideNumber, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        this.slideNumber = slideNumber;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }
        SlideStateBuilder builder = new SlideStateBuilder(orchestrator);
        SlideState current  = builder.current(slideNumber);
        SlideState baseline = builder.baseline(slideNumber);
        if (current == null || baseline == null) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Could not resolve SlideState for slide " + slideNumber);
        }
        preClearSnapshot = current;

        CommandScript clearScript = buildClearScript(current, baseline);
        if (clearScript.isEmpty()) {
            executed = true;
            return; // Nothing to clear; treat as a successful no-op.
        }

        ScriptRunner runner = new ScriptRunner(orchestrator);
        ExecutionResult<Void> r = runner.run(clearScript, "ClearSlideContent " + slideNumber);
        if (!r.isSuccess()) {
            preClearSnapshot = null;
            throw new CommandExecutionException(getDescription(), "execute", r.getMessage());
        }
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Command has not been executed");
        }
        if (preClearSnapshot == null) {
            throw new CommandExecutionException(getDescription(), "undo",
                "No pre-clear snapshot available for undo");
        }
        // Build a restore-script that re-adds everything present in the
        // snapshot but missing from the current (post-clear) state.
        SlideStateBuilder builder = new SlideStateBuilder(orchestrator);
        SlideState nowState = builder.current(slideNumber);
        if (nowState == null) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Slide state unavailable for undo");
        }
        CommandScript restoreScript = buildRestoreScript(nowState, preClearSnapshot);
        if (!restoreScript.isEmpty()) {
            ScriptRunner runner = new ScriptRunner(orchestrator);
            ExecutionResult<Void> r = runner.run(restoreScript, "Undo clear slide " + slideNumber);
            if (!r.isSuccess()) {
                throw new CommandExecutionException(getDescription(), "undo", r.getMessage());
            }
        }
        executed = false;
        preClearSnapshot = null;
    }

    @Override public boolean canUndo() { return executed && preClearSnapshot != null; }
    @Override public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "Clear user-added content on slide " + slideNumber;
    }

    // ========== Script composition ==========

    private CommandScript buildClearScript(SlideState current, SlideState baseline) {
        CommandScript.Builder b = CommandScript.builder();

        // Remove every shape that is genuinely user-added. Two
        // criteria (both must be false to preserve the shape):
        //   - Shape is a placeholder (has <p:ph> in its nvSpPr);
        //     determined via the parser-observed flag on the snapshot's
        //     underlying SlideShape. Placeholders inherit from the
        //     layout and should never be removed by a content clear.
        //   - Shape is the root spTree group (type == GROUP with no
        //     parent group); the spTree itself isn't removable.
        // Iterate in ascending-SPID order so removals happen oldest-
        // first -- not strictly required for correctness but keeps the
        // script deterministic.
        // Iterate in ascending-SPID order so removals happen in a
        // deterministic order; not strictly required for correctness
        // but makes the script hash stable.
        java.util.List<ShapeSnapshot> sortedShapes = new ArrayList<>(current.shapes().values());
        sortedShapes.sort((x, y) -> Integer.compare(x.spid(), y.spid()));
        for (ShapeSnapshot s : sortedShapes) {
            if (isLayoutShape(s)) continue;
            b.add(new CommandSpec.RemoveShapeSpec(slideNumber, s.spid()));
        }

        // Remove every animation that still has a resolvable cTn id.
        // Missing-id animations can't be removed per-cTn today; they
        // survive the clear rather than getting mass-stripped via a
        // blunt timing-tree wipe (the synthesizer convention).
        for (var anim : current.animations()) {
            int id = anim.getTimingNodeId();
            if (id > 0) {
                b.add(new CommandSpec.RemoveAnimationSpec(slideNumber, id));
            }
        }

        // If the slide has a SLIDE-level transition override, clear it so
        // the slide falls back to layout/master inheritance.
        if (current.transition() != null
                && current.transition().source() == com.excudo.core.introspection.TransitionDescriptor.Source.SLIDE) {
            b.add(new CommandSpec.ClearTransitionSpec(slideNumber));
        }

        return b.build();
    }

    /** Restore-script: re-adds shapes + animations + transition present
     *  in the snapshot but missing from the current post-clear state. */
    private CommandScript buildRestoreScript(SlideState nowState, SlideState snapshot) {
        CommandScript.Builder b = CommandScript.builder();
        List<CommandSpec> added = new ArrayList<>();

        for (ShapeSnapshot s : snapshot.shapes().values()) {
            if (nowState.shapes().containsKey(s.spid())) continue;
            if (isLayoutShape(s)) continue; // never re-add layout artifacts
            CommandSpec.AddShapeSpec addSpec = new CommandSpec.AddShapeSpec(
                slideNumber, s.type(), s.geometry(),
                flattenPlain(s.textBody()), s.name(), s.style(), null, s.isTextBox(),
                s.spid());
            added.add(addSpec);
            b.add(addSpec);
        }
        // Re-register animations with SPID remapping. Register AddAnimationSpec
        // after the shape adds so the script-runner's spidMap has already
        // caught up by the time the animations execute.
        for (var anim : snapshot.animations()) {
            CommandSpec animSpec = new CommandSpec.AddAnimationSpec(slideNumber, anim);
            b.add(animSpec);
            // Tie the animation to the shape add that will allocate the
            // mapped target SPID, so the runner executes the add first.
            for (CommandSpec addSpec : added) {
                if (addSpec instanceof CommandSpec.AddShapeSpec ass
                        && ass.sourceSpidHint() != null
                        && ass.sourceSpidHint() == anim.getTargetSpid()) {
                    b.dependsOn(animSpec, addSpec);
                }
            }
        }
        // Transition restoration.
        if (snapshot.transition() != null
                && snapshot.transition().source() == com.excudo.core.introspection.TransitionDescriptor.Source.SLIDE) {
            var t = snapshot.transition();
            b.add(new CommandSpec.SetTransitionSpec(slideNumber, t.type(), t.speed(), t.autoAdvanceMs()));
        }
        return b.build();
    }

    /** True when the shape is a layout-originated artifact that a
     *  content clear must preserve: placeholders and the root spTree
     *  group. The SlideShape type codes both cases. */
    private static boolean isLayoutShape(ShapeSnapshot s) {
        if (s.type() == com.excudo.core.model.SlideShape.ShapeType.PLACEHOLDER) return true;
        // Root-level group with SPID 1 is PowerPoint's top-level spTree
        // group; leave it alone so the slide doesn't lose its container.
        if (s.type() == com.excudo.core.model.SlideShape.ShapeType.GROUP
                && s.spid() == 1
                && s.parentGroupSpid() == null) {
            return true;
        }
        return false;
    }

    private static String flattenPlain(com.excudo.core.model.TextBody body) {
        if (body == null) return "";
        StringBuilder sb = new StringBuilder();
        var paragraphs = body.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (i > 0) sb.append('\n');
            for (var run : paragraphs.get(i).getRuns()) {
                String t = run.getText();
                if (t != null) sb.append(t);
            }
        }
        return sb.toString();
    }
}
