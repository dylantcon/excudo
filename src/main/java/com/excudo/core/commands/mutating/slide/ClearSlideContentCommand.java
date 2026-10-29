package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.synthesis.CommandScript;
import com.excudo.core.synthesis.ScriptRunner;
import com.excudo.core.synthesis.ShapeSnapshot;
import com.excudo.core.synthesis.SlideState;
import com.excudo.core.synthesis.SlideStateBuilder;
import com.excudo.core.synthesis.spec.CommandSpec;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Revert a slide to its layout baseline: remove every user-added shape,
 * every animation, any slide-level transition override, and the text in
 * every remaining shape. Placeholders that came from the layout are
 * preserved. A {@code scope} narrows the clear to some of those parts
 * ({@code shapes}, {@code text}, {@code animations}, {@code transition},
 * comma-separated); the default {@code all} clears every part.
 *
 * <p>Built on top of the synthesizer's state infrastructure: the command
 * diffs (baseline &larr; current), inverts the resulting spec set into
 * removal specs, and hands them to {@link ScriptRunner}. Undo replays
 * the pre-clear state by emitting the add-specs implied by the
 * snapshot. "Clear-slide-content is a UX wrapper around
 * synthesize-then-invert-then-run," and that's literally what this
 * class does.
 *
 * <p>Self-registers via {@link CommandClassRegistry}: the canonical name
 * {@code clear-slide-content} derives from the class name.
 */
public class ClearSlideContentCommand implements Command {

    /** What a clear can wipe. {@code all} in the scope parameter means every part. */
    public enum Part { SHAPES, TEXT, ANIMATIONS, TRANSITION }

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<String> SCOPE = Parameter.ofString("scope")
        .description("What to clear: all (default), or one or more of shapes, text, "
            + "animations, transition, comma-separated (e.g. 'animations,transition'). "
            + "shapes removes user-added shapes (and their animations); text empties the "
            + "text of every shape, placeholders included, keeping the shapes; animations "
            + "removes every animation; transition drops a slide-level transition override.")
        .defaultValue("all").build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Clear a slide back to its layout baseline, or only its shapes, text, animations or transition")
        .llmEnabled(true)
        .llmDescription("Clear a slide. Default scope 'all' reverts it to its layout baseline: "
            + "removes every user-added shape, every animation, any slide-level transition "
            + "override, and empties placeholder text. Pass scope to clear only some parts "
            + "(shapes, text, animations, transition; comma-separated). Layout-originated "
            + "placeholders are never removed. Undoable.")
        .parameter(SLIDE)
        .parameter(SCOPE)
        .example("clear-slide-content 3")
        .example("clear-slide-content 3 animations")
        .example("clear-slide-content 3 text")
        .example("clear-slide-content --slide 3 --scope animations,transition")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ClearSlideContentCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ClearSlideContentCommand(p.get(SLIDE), parseScope(p.get(SCOPE)), ctx.orchestrator());
    }

    /** "all" or a comma-separated list of part names, case-insensitive. */
    public static Set<Part> parseScope(String scope) {
        if (scope == null || scope.isBlank()) return EnumSet.allOf(Part.class);
        Set<Part> parts = EnumSet.noneOf(Part.class);
        for (String token : scope.split("[,\\s]+")) {
            String t = token.trim().toLowerCase(java.util.Locale.ROOT);
            if (t.isEmpty()) continue;
            if (t.equals("all")) return EnumSet.allOf(Part.class);
            switch (t) {
                case "shapes", "shape" -> parts.add(Part.SHAPES);
                case "text" -> parts.add(Part.TEXT);
                case "animations", "animation" -> parts.add(Part.ANIMATIONS);
                case "transition", "transitions" -> parts.add(Part.TRANSITION);
                default -> throw new IllegalArgumentException("Unknown clear scope '" + token
                    + "'. Use all, or any of: shapes, text, animations, transition");
            }
        }
        if (parts.isEmpty()) return EnumSet.allOf(Part.class);
        return parts;
    }

    private final int slideNumber;
    private final Set<Part> scope;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    /** Snapshot of the current state taken at execute time. Used to
     *  rebuild an add-script for undo. */
    private SlideState preClearSnapshot = null;

    public ClearSlideContentCommand(int slideNumber, PPTXOrchestrator orchestrator) {
        this(slideNumber, EnumSet.allOf(Part.class), orchestrator);
    }

    public ClearSlideContentCommand(int slideNumber, Set<Part> scope, PPTXOrchestrator orchestrator) {
        if (scope == null || scope.isEmpty()) {
            throw new IllegalArgumentException("Clear scope cannot be empty");
        }
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        this.slideNumber = slideNumber;
        this.scope = EnumSet.copyOf(scope);
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
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Command has not been executed");
        }
        if (preClearSnapshot == null) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "No pre-clear snapshot available for undo");
        }
        // Build a restore-script that re-adds everything present in the
        // snapshot but missing from the current (post-clear) state.
        SlideStateBuilder builder = new SlideStateBuilder(orchestrator);
        SlideState nowState = builder.current(slideNumber);
        if (nowState == null) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Slide state unavailable for undo");
        }
        CommandScript restoreScript = buildRestoreScript(nowState, preClearSnapshot);
        if (!restoreScript.isEmpty()) {
            ScriptRunner runner = new ScriptRunner(orchestrator);
            ExecutionResult<Void> r = runner.run(restoreScript, "Undo clear slide " + slideNumber);
            if (!r.isSuccess()) {
                throw new CommandExecutionException(getDescription(), UndoCommand.NAME, r.getMessage());
            }
        }
        executed = false;
        preClearSnapshot = null;
    }

    @Override public boolean canUndo() { return executed && preClearSnapshot != null; }
    @Override public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        if (scope.size() == Part.values().length) {
            return "Clear user-added content on slide " + slideNumber;
        }
        StringBuilder parts = new StringBuilder();
        for (Part part : scope) {
            if (parts.length() > 0) parts.append(", ");
            parts.append(part.name().toLowerCase(java.util.Locale.ROOT));
        }
        return "Clear " + parts + " on slide " + slideNumber;
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
        Set<Integer> removedSpids = new HashSet<>();
        for (ShapeSnapshot s : sortedShapes) {
            if (scope.contains(Part.SHAPES) && !isLayoutShape(s)) {
                removedSpids.add(s.spid());
                continue;
            }
            // Shapes that stay keep their geometry and style; only the text goes.
            if (scope.contains(Part.TEXT) && hasText(s)) {
                b.add(new CommandSpec.SetTextSpec(slideNumber, s.spid(), blankText(s.textBody())));
            }
        }

        // Remove animations: all of them, or (shapes without animations in
        // scope) the ones whose target is going away, so none is left
        // pointing at nothing. Missing-id animations can't be removed
        // per-cTn today; they survive the clear rather than getting
        // mass-stripped via a blunt timing-tree wipe (the synthesizer
        // convention).
        for (var anim : current.animations()) {
            int id = anim.getTimingNodeId();
            if (id <= 0) continue;
            if (scope.contains(Part.ANIMATIONS) || removedSpids.contains(anim.getTargetSpid())) {
                b.add(new CommandSpec.RemoveAnimationSpec(slideNumber, id));
            }
        }

        for (ShapeSnapshot s : sortedShapes) {
            if (removedSpids.contains(s.spid())) {
                b.add(new CommandSpec.RemoveShapeSpec(slideNumber, s.spid()));
            }
        }

        // If the slide has a SLIDE-level transition override, clear it so
        // the slide falls back to layout/master inheritance.
        if (scope.contains(Part.TRANSITION)
                && current.transition() != null
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
            if (nowState.shapes().containsKey(s.spid())) {
                // Surviving shape: put back text the clear emptied.
                if (scope.contains(Part.TEXT) && hasText(s)) {
                    b.add(new CommandSpec.SetTextSpec(slideNumber, s.spid(), s.textBody()));
                }
                continue;
            }
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
        Set<Integer> survivingIds = new HashSet<>();
        for (var anim : nowState.animations()) survivingIds.add(anim.getTimingNodeId());
        for (var anim : snapshot.animations()) {
            // A narrower scope leaves some animations in place; don't duplicate them.
            if (anim.getTimingNodeId() > 0 && survivingIds.contains(anim.getTimingNodeId())) continue;
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
        if (scope.contains(Part.TRANSITION)
                && snapshot.transition() != null
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

    private static boolean hasText(ShapeSnapshot s) {
        return s.textBody() != null && !flattenPlain(s.textBody()).isEmpty();
    }

    /** One empty paragraph, keeping the body properties (insets, anchoring, autofit). */
    private static com.excudo.core.model.TextBody blankText(com.excudo.core.model.TextBody body) {
        var empty = com.excudo.core.model.TextBody.fromPlainText("", body.isPlaceholder());
        var tb = com.excudo.core.model.TextBody.builder()
            .placeholder(body.isPlaceholder())
            .bodyProperties(body.getBodyProperties());
        empty.getParagraphs().forEach(tb::addParagraph);
        return tb.build();
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
