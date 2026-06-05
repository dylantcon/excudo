package com.excudo.core.synthesis.spec;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.mutating.slide.AddShapeCommand;
import com.excudo.core.commands.mutating.slide.AddAnimationCommand;
import com.excudo.core.commands.mutating.slide.ClearTransitionCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.mutating.slide.ContentEditCommand;
import com.excudo.core.commands.mutating.slide.GroupShapesCommand;
import com.excudo.core.commands.mutating.slide.MoveShapeCommand;
import com.excudo.core.commands.mutating.slide.RemoveAnimationCommand;
import com.excudo.core.commands.mutating.slide.RemoveShapeCommand;
import com.excudo.core.commands.mutating.slide.ReorderShapeCommand;
import com.excudo.core.commands.mutating.slide.ResizeShapeCommand;
import com.excudo.core.commands.mutating.slide.RotateShapeCommand;
import com.excudo.core.commands.mutating.slide.SetStyleCommand;
import com.excudo.core.commands.mutating.slide.SetTransitionCommand;
import com.excudo.core.commands.mutating.slide.UngroupCommand;
import com.excudo.core.model.TextBody;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.xml.writers.animations.GroupIdManager;

/**
 * Central translator from a {@link CommandSpec} value to a concrete
 * {@link Command} instance bound to an orchestrator. Every spec in the
 * sealed hierarchy gets one case here -- adding a new spec type is a
 * compile-time failure until a case is added, which is the point of
 * using a sealed interface + exhaustive switch.
 *
 * <p>The mapper never executes the command -- that's
 * {@code ScriptRunner}'s job (landing in 4.5). It just produces the
 * callable Command object that the runner feeds into a
 * {@code CompositeCommand}.
 */
public final class SpecToCommandMapper {

    private final PPTXOrchestrator orchestrator;

    public SpecToCommandMapper(PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("orchestrator must not be null");
        }
        this.orchestrator = orchestrator;
    }

    public Command toCommand(CommandSpec spec) {
        if (spec == null) throw new IllegalArgumentException("spec must not be null");

        return switch (spec) {
            case CommandSpec.AddShapeSpec s      -> buildAdd(s);
            case CommandSpec.RemoveShapeSpec s   -> new RemoveShapeCommand(s.slideNumber(), s.spid(), orchestrator);
            case CommandSpec.MoveSpec s          -> new MoveShapeCommand(s.slideNumber(), s.spid(),
                                                        s.newX(), s.newY(), orchestrator);
            case CommandSpec.ResizeSpec s        -> new ResizeShapeCommand(s.slideNumber(), s.spid(),
                                                        s.newWidth(), s.newHeight(), orchestrator);
            case CommandSpec.RotateSpec s        -> new RotateShapeCommand(s.slideNumber(), s.spid(),
                                                        s.newRotationDegrees(), orchestrator);
            case CommandSpec.RenameShapeSpec s   -> new com.excudo.core.commands.mutating.slide.RenameShapeCommand(
                                                        s.slideNumber(), s.spid(), s.newName(), orchestrator);
            case CommandSpec.SetTextBoxFlagSpec s -> new com.excudo.core.commands.mutating.slide.SetTextBoxFlagCommand(
                                                        s.slideNumber(), s.spid(), s.flag(), orchestrator);
            case CommandSpec.SetRunFormatSpec s   -> new com.excudo.core.commands.mutating.slide.UpdateRunFormatCommand(
                                                        s.slideNumber(), s.spid(), s.paragraphIdx(), s.runIdx(),
                                                        s.newRun(), orchestrator);
            case CommandSpec.SetTextSpec s       -> buildSetText(s);
            case CommandSpec.SetShapeStyleSpec s -> new SetStyleCommand(s.slideNumber(), s.spid(),
                                                        s.style(), orchestrator);
            case CommandSpec.ReorderSpec s       -> new ReorderShapeCommand(s.slideNumber(), s.spid(),
                                                        mapReorderDirection(s.direction()), orchestrator);
            case CommandSpec.AddAnimationSpec s  -> AddAnimationCommand.fromBinding(
                                                        s.slideNumber(), s.binding(), orchestrator,
                                                        (GroupIdManager) null);
            case CommandSpec.RemoveAnimationSpec s -> new RemoveAnimationCommand(
                                                        s.slideNumber(), s.timingNodeId(), orchestrator);
            case CommandSpec.SetAnimationTimingSpec s -> new com.excudo.core.commands.mutating.slide.UpdateAnimationTimingCommand(
                                                        s.slideNumber(), s.timingNodeId(),
                                                        s.newDuration(), s.newDelay(), orchestrator);
            case CommandSpec.SetTransitionSpec s -> new SetTransitionCommand(s.slideNumber(),
                                                        s.transitionType(), s.speed(),
                                                        s.autoAdvanceMs(), orchestrator);
            case CommandSpec.ClearTransitionSpec s -> new ClearTransitionCommand(s.slideNumber(), orchestrator);
            case CommandSpec.CreateGroupSpec s   -> new GroupShapesCommand(s.slideNumber(),
                                                        s.childSpids(), orchestrator);
            case CommandSpec.UngroupSpec s       -> new UngroupCommand(s.slideNumber(), s.groupSpid(), orchestrator);
            case CommandSpec.AddToGroupSpec s    -> new com.excudo.core.commands.mutating.slide.AddToGroupCommand(
                                                        s.slideNumber(), s.groupSpid(), s.childSpid(), orchestrator);
            case CommandSpec.DetachFromGroupSpec s -> new com.excudo.core.commands.mutating.slide.DetachFromGroupCommand(
                                                        s.slideNumber(), s.childSpid(), orchestrator);
            case CommandSpec.CreateCodeBoxSpec s -> new com.excudo.core.commands.mutating.slide.CreateCodeBoxCommand(
                                                        s.slideNumber(), s.code(), s.language(),
                                                        s.x(), s.y(), s.width(), s.height(),
                                                        s.lineNumberColor(), orchestrator);
            case CommandSpec.CreateDiagramSpec s -> new com.excudo.core.commands.mutating.slide.CreateMermaidDiagramCommand(
                                                        s.slideNumber(), s.mermaidSource(),
                                                        s.x(), s.y(), s.width(), s.height(),
                                                        orchestrator);
            case CommandSpec.AddConnectorSpec s  -> new com.excudo.core.commands.mutating.slide.AddConnectorCommand(
                                                        s.slideNumber(), s.connectorType(), s.geometry(),
                                                        s.headEnd(), s.tailEnd(), s.lineColor(), null,
                                                        s.startSpid(), s.startIdx(),
                                                        s.endSpid(), s.endIdx(),
                                                        s.customPath(), orchestrator);
        };
    }

    private Command buildAdd(CommandSpec.AddShapeSpec s) {
        // AddShapeCommand's most-specific constructor: (slide, type,
        // geom, text, name, style, alignment, isTextBox, orchestrator).
        return new AddShapeCommand(s.slideNumber(), s.shapeType(), s.geometry(),
            s.text(), s.name(), s.style(), s.alignment(), s.isTextBox(), orchestrator);
    }

    /**
     * SetTextSpec carries a typed {@link TextBody}, but
     * {@link ContentEditCommand} takes a plain String. The synthesizer
     * emits SetTextSpec after an AddShapeSpec whose {@code text} field
     * is null, then this command runs to REPLACE the placeholder's
     * empty body with the full typed body via {@code setTextBody} --
     * preserving paragraph structure, bullet levels, and run formatting.
     *
     * <p>We need a Command wrapper because ContentEditCommand only
     * accepts strings. Inline implementation keeps this surgical.
     */
    private Command buildSetText(CommandSpec.SetTextSpec s) {
        return new Command() {
            private boolean executed = false;
            private TextBody snapshot = null;

            @Override public void execute() {
                // Snapshot-based undo: extract the current TextBody of
                // the shape via the introspection surface, apply the
                // new one, keep the old for undo.
                snapshot = readCurrentTextBody(s.slideNumber(), s.spid());
                ExecutionResult<Void> r = orchestrator.setTextBody(s.slideNumber(), s.spid(), s.textBody());
                if (!r.isSuccess()) {
                    snapshot = null;
                    throw new com.excudo.core.commands.CommandExecutionException(
                        getDescription(), "execute", r.getMessage());
                }
                executed = true;
            }

            @Override public void undo() {
                if (!executed) {
                    throw new com.excudo.core.commands.CommandExecutionException(
                        getDescription(), UndoCommand.NAME, "Not executed");
                }
                if (snapshot != null) {
                    orchestrator.setTextBody(s.slideNumber(), s.spid(), snapshot);
                }
                executed = false;
                snapshot = null;
            }

            @Override public boolean canUndo() { return executed && snapshot != null; }
            @Override public boolean isExecuted() { return executed; }
            @Override public String getDescription() {
                return "SetText (typed TextBody) on SPID " + s.spid() + " slide " + s.slideNumber();
            }
        };
    }

    private TextBody readCurrentTextBody(int slideNumber, int spid) {
        // Best-effort: returns null if the shape has no parsed text body.
        // The synthesizer's SetText is typically preceded by AddShapeSpec
        // that leaves the shape with a default body, so this is usually
        // an empty TextBody rather than null.
        var ctxOpt = orchestrator.getContext();
        if (ctxOpt.isEmpty()) return null;
        var doc = ctxOpt.get().getDocument();
        if (doc == null) return null;
        var parsed = doc.getParsedSlideData(slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        if (parsed == null) return null;
        var shape = parsed.getShapeRegistry().getShape(spid);
        if (shape == null || shape.getXmlElement() == null) return null;
        var txBody = firstChildElement(shape.getXmlElement(), "txBody");
        if (txBody == null) return null;
        return com.excudo.core.metrics.TextBodyExtractor.extract(txBody);
    }

    private static org.w3c.dom.Element firstChildElement(org.w3c.dom.Element parent, String localName) {
        org.w3c.dom.NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && localName.equals(n.getLocalName())) {
                return (org.w3c.dom.Element) n;
            }
        }
        return null;
    }

    private static ReorderShapeCommand.ZOrderOperation mapReorderDirection(
            CommandSpec.ReorderSpec.Direction d) {
        return switch (d) {
            case FRONT    -> ReorderShapeCommand.ZOrderOperation.BRING_FRONT;
            case BACK     -> ReorderShapeCommand.ZOrderOperation.SEND_BACK;
            case FORWARD  -> ReorderShapeCommand.ZOrderOperation.BRING_FORWARD;
            case BACKWARD -> ReorderShapeCommand.ZOrderOperation.SEND_BACKWARD;
        };
    }
}
