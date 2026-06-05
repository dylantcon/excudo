package com.excudo.core.commands.readonly;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.synthesis.ScriptSynthesizer;
import com.excudo.core.synthesis.SlideState;
import com.excudo.core.synthesis.SlideStateBuilder;
import com.excudo.core.synthesis.SlideStateDiff;
import com.excudo.core.synthesis.SlideStateDiffer;
import com.excudo.core.synthesis.spec.CommandSpec;
import com.excudo.core.synthesis.spec.CommandSpecJson;

import java.util.List;

/**
 * Reverse-engineer the minimum DAG of canonical commands that, applied
 * to a slide's layout baseline, produces the slide's current state.
 * Read-only: {@link #execute()} captures the synthesized output on the
 * command instance; {@link #getScriptJson()}, {@link #getScriptSummary()},
 * and {@link #getWarnings()} surface it to callers.
 *
 * <p>Implements {@link Command} so it's invocable uniformly from the
 * MCP tool surface, console, and (later) the GUI. Undo is a no-op
 * because nothing was mutated; the executed flag toggles off so the
 * command can be re-executed if the slide state changed.
 *
 * <p>Pair with {@link RunSlideScriptCommand} for the clone-with-overrides
 * workflow: synthesize slide S, edit specs by index, run the edited
 * JSON on a fresh target slide.
 */
public class SynthesizeSlideScriptCommand implements Command {

    private final int slideNumber;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    private String scriptJson = null;
    private String scriptSummary = null;
    private List<String> warnings = List.of();
    private int specCount = 0;

    public SynthesizeSlideScriptCommand(int slideNumber, PPTXOrchestrator orchestrator) {
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
        SlideState current = builder.current(slideNumber);
        SlideState baseline = builder.baseline(slideNumber);
        if (current == null || baseline == null) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Could not resolve SlideState for slide " + slideNumber);
        }
        SlideStateDiff diff = SlideStateDiffer.diff(baseline, current);
        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, slideNumber);

        List<CommandSpec> order = result.script().topologicalOrder();
        specCount = order.size();
        scriptJson = CommandSpecJson.toJsonArray(order);
        scriptSummary = buildSummary(order);
        warnings = List.copyOf(result.warnings());
        executed = true;
    }

    @Override
    public void undo() {
        // Read-only. Flip state so the command can be re-run if the
        // slide has since mutated.
        executed = false;
        scriptJson = null;
        scriptSummary = null;
        warnings = List.of();
        specCount = 0;
    }

    @Override public boolean canUndo() { return executed; }
    @Override public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "SynthesizeSlideScript(slide=" + slideNumber + ")";
    }

    /** JSON array of {@link CommandSpec} objects, ready to feed
     *  {@link RunSlideScriptCommand}. Null until {@link #execute}
     *  completes. */
    public String getScriptJson() { return scriptJson; }

    /** Human-readable indexed summary of the specs. Null until
     *  {@link #execute} completes. */
    public String getScriptSummary() { return scriptSummary; }

    public List<String> getWarnings() { return warnings; }

    public int getSpecCount() { return specCount; }

    private static String buildSummary(List<CommandSpec> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            sb.append('[').append(i).append("] ").append(describe(order.get(i))).append('\n');
        }
        return sb.toString();
    }

    /** One-line summary of a spec for the indexed listing. Same
     *  semantics as ToolDispatcher's summarizer but standalone so the
     *  read-command doesn't depend on the MCP dispatcher. */
    private static String describe(CommandSpec s) {
        return switch (s) {
            case CommandSpec.AddShapeSpec a ->
                String.format("AddShape type=%s name=%s geom=(%d,%d %dx%d)",
                    a.shapeType(), a.name(),
                    a.geometry().getX(), a.geometry().getY(),
                    a.geometry().getWidth(), a.geometry().getHeight());
            case CommandSpec.RemoveShapeSpec r ->
                "RemoveShape spid=" + r.spid();
            case CommandSpec.MoveSpec m ->
                String.format("Move spid=%d to (%d, %d)", m.spid(), m.newX(), m.newY());
            case CommandSpec.ResizeSpec r ->
                String.format("Resize spid=%d to %dx%d", r.spid(), r.newWidth(), r.newHeight());
            case CommandSpec.RotateSpec r ->
                String.format("Rotate spid=%d to %.2f deg", r.spid(), r.newRotationDegrees());
            case CommandSpec.RenameShapeSpec r ->
                "Rename spid=" + r.spid() + " to \"" + r.newName() + "\"";
            case CommandSpec.SetTextSpec st ->
                "SetText spid=" + st.spid() + " ("
                    + st.textBody().getParagraphs().size() + " paragraphs)";
            case CommandSpec.SetShapeStyleSpec ss ->
                "SetShapeStyle spid=" + ss.spid();
            case CommandSpec.SetTextBoxFlagSpec tb ->
                "SetTextBoxFlag spid=" + tb.spid() + " " + tb.flag();
            case CommandSpec.SetRunFormatSpec rf ->
                String.format("SetRunFormat spid=%d para=%d run=%d",
                    rf.spid(), rf.paragraphIdx(), rf.runIdx());
            case CommandSpec.ReorderSpec r ->
                "Reorder spid=" + r.spid() + " " + r.direction();
            case CommandSpec.AddAnimationSpec a ->
                String.format("AddAnimation target=%d type=%s",
                    a.binding().getTargetSpid(), a.binding().getAnimationType());
            case CommandSpec.RemoveAnimationSpec r ->
                "RemoveAnimation cTn=" + r.timingNodeId();
            case CommandSpec.SetAnimationTimingSpec t ->
                "SetAnimationTiming cTn=" + t.timingNodeId();
            case CommandSpec.SetTransitionSpec tt ->
                "SetTransition " + tt.transitionType() + " " + tt.speed();
            case CommandSpec.ClearTransitionSpec c -> "ClearTransition";
            case CommandSpec.CreateGroupSpec g ->
                "CreateGroup children=" + g.childSpids();
            case CommandSpec.UngroupSpec u ->
                "Ungroup spid=" + u.groupSpid();
            case CommandSpec.AddToGroupSpec a ->
                "AddToGroup group=" + a.groupSpid() + " child=" + a.childSpid();
            case CommandSpec.DetachFromGroupSpec d ->
                "DetachFromGroup child=" + d.childSpid();
            case CommandSpec.CreateCodeBoxSpec c ->
                "CreateCodeBox lang=" + c.language();
            case CommandSpec.CreateDiagramSpec d ->
                "CreateDiagram mermaidSource=("
                    + d.mermaidSource().length() + " chars)";
            case CommandSpec.AddConnectorSpec c ->
                String.format("AddConnector type=%s start=%s end=%s",
                    c.connectorType(),
                    c.startSpid() != null ? c.startSpid() + ":" + c.startIdx() : "free",
                    c.endSpid()   != null ? c.endSpid()   + ":" + c.endIdx()   : "free");
        };
    }
}
