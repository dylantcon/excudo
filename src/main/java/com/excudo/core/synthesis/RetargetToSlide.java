package com.excudo.core.synthesis;

import com.excudo.core.synthesis.spec.CommandSpec;

/**
 * Return a copy of a {@link CommandSpec} with its {@code slideNumber}
 * field rewritten to the given target. Enables scripts synthesized
 * from one slide to be replayed on another without the caller having
 * to rewrite every spec by hand.
 *
 * <p>Sealed-switch exhaustive over every v1 spec type; adding a new
 * spec is a compile-time failure here until retargeting is covered,
 * which is the point.
 */
public final class RetargetToSlide {

    private RetargetToSlide() {}

    public static CommandSpec retarget(CommandSpec spec, int targetSlide) {
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
        if (spec.slideNumber() == targetSlide) return spec;

        return switch (spec) {
            case CommandSpec.AddShapeSpec s      -> new CommandSpec.AddShapeSpec(
                targetSlide, s.shapeType(), s.geometry(), s.text(), s.name(),
                s.style(), s.alignment(), s.isTextBox(), s.sourceSpidHint());
            case CommandSpec.RemoveShapeSpec s   -> new CommandSpec.RemoveShapeSpec(targetSlide, s.spid());
            case CommandSpec.MoveSpec s          -> new CommandSpec.MoveSpec(
                targetSlide, s.spid(), s.newX(), s.newY());
            case CommandSpec.ResizeSpec s        -> new CommandSpec.ResizeSpec(
                targetSlide, s.spid(), s.newWidth(), s.newHeight());
            case CommandSpec.RotateSpec s        -> new CommandSpec.RotateSpec(
                targetSlide, s.spid(), s.newRotationDegrees());
            case CommandSpec.RenameShapeSpec s   -> new CommandSpec.RenameShapeSpec(
                targetSlide, s.spid(), s.newName());
            case CommandSpec.SetTextSpec s       -> new CommandSpec.SetTextSpec(
                targetSlide, s.spid(), s.textBody());
            case CommandSpec.SetShapeStyleSpec s -> new CommandSpec.SetShapeStyleSpec(
                targetSlide, s.spid(), s.style());
            case CommandSpec.SetTextBoxFlagSpec s -> new CommandSpec.SetTextBoxFlagSpec(
                targetSlide, s.spid(), s.flag());
            case CommandSpec.SetRunFormatSpec s  -> new CommandSpec.SetRunFormatSpec(
                targetSlide, s.spid(), s.paragraphIdx(), s.runIdx(), s.newRun());
            case CommandSpec.ReorderSpec s       -> new CommandSpec.ReorderSpec(
                targetSlide, s.spid(), s.direction());
            case CommandSpec.AddAnimationSpec s  -> new CommandSpec.AddAnimationSpec(
                targetSlide, s.binding());
            case CommandSpec.RemoveAnimationSpec s -> new CommandSpec.RemoveAnimationSpec(
                targetSlide, s.timingNodeId());
            case CommandSpec.SetAnimationTimingSpec s -> new CommandSpec.SetAnimationTimingSpec(
                targetSlide, s.timingNodeId(), s.newDuration(), s.newDelay());
            case CommandSpec.SetTransitionSpec s -> new CommandSpec.SetTransitionSpec(
                targetSlide, s.transitionType(), s.speed(), s.autoAdvanceMs());
            case CommandSpec.ClearTransitionSpec s -> new CommandSpec.ClearTransitionSpec(targetSlide);
            case CommandSpec.CreateGroupSpec s   -> new CommandSpec.CreateGroupSpec(
                targetSlide, s.childSpids(), s.groupName());
            case CommandSpec.UngroupSpec s       -> new CommandSpec.UngroupSpec(
                targetSlide, s.groupSpid());
            case CommandSpec.AddToGroupSpec s    -> new CommandSpec.AddToGroupSpec(
                targetSlide, s.groupSpid(), s.childSpid());
            case CommandSpec.DetachFromGroupSpec s -> new CommandSpec.DetachFromGroupSpec(
                targetSlide, s.childSpid());
            case CommandSpec.CreateCodeBoxSpec s -> new CommandSpec.CreateCodeBoxSpec(
                targetSlide, s.language(), s.code(), s.x(), s.y(),
                s.width(), s.height(), s.lineNumberColor(), s.sourceSpidHint());
            case CommandSpec.CreateDiagramSpec s -> new CommandSpec.CreateDiagramSpec(
                targetSlide, s.mermaidSource(), s.x(), s.y(),
                s.width(), s.height(), s.sourceSpidHint());
            case CommandSpec.AddConnectorSpec s  -> new CommandSpec.AddConnectorSpec(
                targetSlide, s.connectorType(), s.geometry(),
                s.headEnd(), s.tailEnd(), s.lineColor(),
                s.startSpid(), s.startIdx(), s.endSpid(), s.endIdx(),
                s.customPath(), s.name(), s.sourceSpidHint());
        };
    }
}
