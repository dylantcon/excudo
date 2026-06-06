package com.excudo.core.synthesis;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.BlipRef;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextParagraph;
import com.excudo.core.model.TextRun;
import com.excudo.core.model.TransitionType;
import com.excudo.core.synthesis.spec.CommandSpec;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Exhaustive over {@code CommandSpec.permits}: for each spec type,
 * assert {@link RetargetToSlide#retarget} rewrites only the slide
 * number, leaving every other field untouched. The sealed switch in
 * RetargetToSlide mirrors the one here; adding a new permits entry
 * without a case in both production and test fails compilation.
 *
 * <p>Identity short-circuit: when {@code spec.slideNumber() ==
 * targetSlide}, retarget returns the same instance (no allocation).
 */
public class RetargetToSlideTest {

    private static final int SRC_SLIDE = 1;
    private static final int TGT_SLIDE = 5;
    private static final int SPID = 10;
    private static final ShapeGeometry GEOM = new ShapeGeometry(0, 0, 1000, 1000);

    @Test public void allSpecs_retargetSlideNumber() {
        for (Class<?> raw : CommandSpec.class.getPermittedSubclasses()) {
            @SuppressWarnings("unchecked")
            Class<? extends CommandSpec> cls = (Class<? extends CommandSpec>) raw;
            CommandSpec sample = sampleOf(cls);
            CommandSpec retargeted = RetargetToSlide.retarget(sample, TGT_SLIDE);
            assertEquals("Retarget must change slideNumber for " + cls.getSimpleName(),
                TGT_SLIDE, retargeted.slideNumber());
            assertEquals("Spec type must be preserved for " + cls.getSimpleName(),
                cls, retargeted.getClass());
            assertSpecFieldsPreserved(sample, retargeted);
        }
    }

    @Test public void retargetToSameSlide_isIdentity() {
        for (Class<?> raw : CommandSpec.class.getPermittedSubclasses()) {
            @SuppressWarnings("unchecked")
            Class<? extends CommandSpec> cls = (Class<? extends CommandSpec>) raw;
            CommandSpec sample = sampleOf(cls);
            CommandSpec out = RetargetToSlide.retarget(sample, sample.slideNumber());
            assertSame("Retarget to same slide must return identity for "
                + cls.getSimpleName(), sample, out);
        }
    }

    // ===================================================================
    // Per-spec field-preservation switch. Catches any case in production
    // RetargetToSlide that silently drops a field while changing slide.
    // ===================================================================

    @SuppressWarnings("PatternVariableHidesField")
    private static void assertSpecFieldsPreserved(CommandSpec original, CommandSpec retargeted) {
        switch (original) {
            case CommandSpec.AddShapeSpec s -> {
                CommandSpec.AddShapeSpec r = (CommandSpec.AddShapeSpec) retargeted;
                assertEquals("shapeType", s.shapeType(), r.shapeType());
                assertEquals("geometry", s.geometry(), r.geometry());
                assertEquals("text", s.text(), r.text());
                assertEquals("name", s.name(), r.name());
                assertEquals("style", s.style(), r.style());
                assertEquals("alignment", s.alignment(), r.alignment());
                assertEquals("isTextBox", s.isTextBox(), r.isTextBox());
                assertEquals("sourceSpidHint", s.sourceSpidHint(), r.sourceSpidHint());
            }
            case CommandSpec.RemoveShapeSpec s -> assertEquals(s.spid(), ((CommandSpec.RemoveShapeSpec) retargeted).spid());
            case CommandSpec.MoveSpec s -> {
                CommandSpec.MoveSpec r = (CommandSpec.MoveSpec) retargeted;
                assertEquals(s.spid(), r.spid());
                assertEquals(s.newX(), r.newX());
                assertEquals(s.newY(), r.newY());
            }
            case CommandSpec.ResizeSpec s -> {
                CommandSpec.ResizeSpec r = (CommandSpec.ResizeSpec) retargeted;
                assertEquals(s.spid(), r.spid());
                assertEquals(s.newWidth(), r.newWidth());
                assertEquals(s.newHeight(), r.newHeight());
            }
            case CommandSpec.RotateSpec s -> {
                CommandSpec.RotateSpec r = (CommandSpec.RotateSpec) retargeted;
                assertEquals(s.spid(), r.spid());
                assertEquals(s.newRotationDegrees(), r.newRotationDegrees(), 0.001);
            }
            case CommandSpec.RenameShapeSpec s -> {
                CommandSpec.RenameShapeSpec r = (CommandSpec.RenameShapeSpec) retargeted;
                assertEquals(s.spid(), r.spid());
                assertEquals(s.newName(), r.newName());
            }
            case CommandSpec.SetTextBoxFlagSpec s -> {
                CommandSpec.SetTextBoxFlagSpec r = (CommandSpec.SetTextBoxFlagSpec) retargeted;
                assertEquals(s.spid(), r.spid());
                assertEquals(s.flag(), r.flag());
            }
            case CommandSpec.SetRunFormatSpec s -> {
                CommandSpec.SetRunFormatSpec r = (CommandSpec.SetRunFormatSpec) retargeted;
                assertEquals(s.spid(), r.spid());
                assertEquals(s.paragraphIdx(), r.paragraphIdx());
                assertEquals(s.runIdx(), r.runIdx());
                assertEquals(s.newRun(), r.newRun());
            }
            case CommandSpec.SetTextSpec s -> {
                CommandSpec.SetTextSpec r = (CommandSpec.SetTextSpec) retargeted;
                assertEquals(s.spid(), r.spid());
                assertEquals(s.textBody(), r.textBody());
            }
            case CommandSpec.SetShapeStyleSpec s -> {
                CommandSpec.SetShapeStyleSpec r = (CommandSpec.SetShapeStyleSpec) retargeted;
                assertEquals(s.spid(), r.spid());
                assertEquals(s.style(), r.style());
            }
            case CommandSpec.ReorderSpec s -> {
                CommandSpec.ReorderSpec r = (CommandSpec.ReorderSpec) retargeted;
                assertEquals(s.spid(), r.spid());
                assertEquals(s.direction(), r.direction());
            }
            case CommandSpec.AddAnimationSpec s -> assertEquals(s.binding(),
                ((CommandSpec.AddAnimationSpec) retargeted).binding());
            case CommandSpec.RemoveAnimationSpec s -> assertEquals(s.timingNodeId(),
                ((CommandSpec.RemoveAnimationSpec) retargeted).timingNodeId());
            case CommandSpec.SetAnimationTimingSpec s -> {
                CommandSpec.SetAnimationTimingSpec r = (CommandSpec.SetAnimationTimingSpec) retargeted;
                assertEquals(s.timingNodeId(), r.timingNodeId());
                assertEquals(s.newDuration(), r.newDuration());
                assertEquals(s.newDelay(), r.newDelay());
            }
            case CommandSpec.SetTransitionSpec s -> {
                CommandSpec.SetTransitionSpec r = (CommandSpec.SetTransitionSpec) retargeted;
                assertEquals(s.transitionType(), r.transitionType());
                assertEquals(s.speed(), r.speed());
                assertEquals(s.autoAdvanceMs(), r.autoAdvanceMs());
            }
            case CommandSpec.ClearTransitionSpec s -> {
                /* only slide number; nothing else to check */
            }
            case CommandSpec.CreateGroupSpec s -> {
                CommandSpec.CreateGroupSpec r = (CommandSpec.CreateGroupSpec) retargeted;
                assertEquals(s.childSpids(), r.childSpids());
                assertEquals(s.groupName(), r.groupName());
            }
            case CommandSpec.UngroupSpec s -> assertEquals(s.groupSpid(),
                ((CommandSpec.UngroupSpec) retargeted).groupSpid());
            case CommandSpec.AddToGroupSpec s -> {
                CommandSpec.AddToGroupSpec r = (CommandSpec.AddToGroupSpec) retargeted;
                assertEquals(s.groupSpid(), r.groupSpid());
                assertEquals(s.childSpid(), r.childSpid());
            }
            case CommandSpec.DetachFromGroupSpec s -> assertEquals(s.childSpid(),
                ((CommandSpec.DetachFromGroupSpec) retargeted).childSpid());
            case CommandSpec.CreateCodeBoxSpec s -> {
                CommandSpec.CreateCodeBoxSpec r = (CommandSpec.CreateCodeBoxSpec) retargeted;
                assertEquals(s.language(), r.language());
                assertEquals(s.code(), r.code());
                assertEquals(s.x(), r.x());
                assertEquals(s.y(), r.y());
                assertEquals(s.width(), r.width());
                assertEquals(s.height(), r.height());
                assertEquals(s.lineNumberColor(), r.lineNumberColor());
                assertEquals(s.sourceSpidHint(), r.sourceSpidHint());
            }
            case CommandSpec.CreateDiagramSpec s -> {
                CommandSpec.CreateDiagramSpec r = (CommandSpec.CreateDiagramSpec) retargeted;
                assertEquals(s.mermaidSource(), r.mermaidSource());
                assertEquals(s.x(), r.x());
                assertEquals(s.y(), r.y());
                assertEquals(s.width(), r.width());
                assertEquals(s.height(), r.height());
                assertEquals(s.sourceSpidHint(), r.sourceSpidHint());
            }
            case CommandSpec.AddConnectorSpec s -> {
                CommandSpec.AddConnectorSpec r = (CommandSpec.AddConnectorSpec) retargeted;
                assertEquals(s.connectorType(), r.connectorType());
                assertEquals(s.geometry(), r.geometry());
                assertEquals(s.headEnd(), r.headEnd());
                assertEquals(s.tailEnd(), r.tailEnd());
                assertEquals(s.lineColor(), r.lineColor());
                assertEquals(s.startSpid(), r.startSpid());
                assertEquals(s.startIdx(), r.startIdx());
                assertEquals(s.endSpid(), r.endSpid());
                assertEquals(s.endIdx(), r.endIdx());
                assertEquals(s.customPath(), r.customPath());
                assertEquals(s.name(), r.name());
                assertEquals(s.sourceSpidHint(), r.sourceSpidHint());
            }
            case CommandSpec.AddPictureSpec s -> {
                CommandSpec.AddPictureSpec r = (CommandSpec.AddPictureSpec) retargeted;
                assertEquals(s.blipRef(), r.blipRef());
                assertEquals(s.geometry(), r.geometry());
                assertEquals(s.name(), r.name());
                assertEquals(s.sourceSpidHint(), r.sourceSpidHint());
            }
        }
    }

    private static CommandSpec sampleOf(Class<? extends CommandSpec> cls) {
        return switch (cls.getSimpleName()) {
            case "AddShapeSpec"            -> new CommandSpec.AddShapeSpec(SRC_SLIDE, SlideShape.ShapeType.RECTANGLE, GEOM, "T", "N", null, "ctr", false, SPID);
            case "RemoveShapeSpec"         -> new CommandSpec.RemoveShapeSpec(SRC_SLIDE, SPID);
            case "MoveSpec"                -> new CommandSpec.MoveSpec(SRC_SLIDE, SPID, 100L, 200L);
            case "ResizeSpec"              -> new CommandSpec.ResizeSpec(SRC_SLIDE, SPID, 500L, 600L);
            case "RotateSpec"              -> new CommandSpec.RotateSpec(SRC_SLIDE, SPID, 45.0);
            case "RenameShapeSpec"         -> new CommandSpec.RenameShapeSpec(SRC_SLIDE, SPID, "X");
            case "SetTextBoxFlagSpec"      -> new CommandSpec.SetTextBoxFlagSpec(SRC_SLIDE, SPID, true);
            case "SetRunFormatSpec"        -> new CommandSpec.SetRunFormatSpec(SRC_SLIDE, SPID, 1, 2, TextRun.builder("hi").build());
            case "SetTextSpec"             -> new CommandSpec.SetTextSpec(SRC_SLIDE, SPID, TextBody.builder().addParagraph(TextParagraph.builder().addRun(TextRun.builder("hi").build()).build()).build());
            case "SetShapeStyleSpec"       -> new CommandSpec.SetShapeStyleSpec(SRC_SLIDE, SPID, ShapeStyle.defaultStyle());
            case "ReorderSpec"             -> new CommandSpec.ReorderSpec(SRC_SLIDE, SPID, CommandSpec.ReorderSpec.Direction.BACKWARD);
            case "AddAnimationSpec"        -> new CommandSpec.AddAnimationSpec(SRC_SLIDE, AnimationBinding.builder().target(SPID).type(AnimationType.APPEAR).durationMs(500).build());
            case "RemoveAnimationSpec"     -> new CommandSpec.RemoveAnimationSpec(SRC_SLIDE, 99);
            case "SetAnimationTimingSpec"  -> new CommandSpec.SetAnimationTimingSpec(SRC_SLIDE, 99, "750", "50");
            case "SetTransitionSpec"       -> new CommandSpec.SetTransitionSpec(SRC_SLIDE, TransitionType.FADE, "slow", 3000);
            case "ClearTransitionSpec"     -> new CommandSpec.ClearTransitionSpec(SRC_SLIDE);
            case "CreateGroupSpec"         -> new CommandSpec.CreateGroupSpec(SRC_SLIDE, List.of(SPID, 99), "G");
            case "UngroupSpec"             -> new CommandSpec.UngroupSpec(SRC_SLIDE, SPID);
            case "AddToGroupSpec"          -> new CommandSpec.AddToGroupSpec(SRC_SLIDE, SPID, 99);
            case "DetachFromGroupSpec"     -> new CommandSpec.DetachFromGroupSpec(SRC_SLIDE, SPID);
            case "CreateCodeBoxSpec"       -> new CommandSpec.CreateCodeBoxSpec(SRC_SLIDE, "java", "code", 0L, 0L, 100L, 100L, "FF0000", SPID);
            case "CreateDiagramSpec"       -> new CommandSpec.CreateDiagramSpec(SRC_SLIDE, "graph TD\n A --> B", 0L, 0L, 100L, 100L, SPID);
            case "AddConnectorSpec"        -> new CommandSpec.AddConnectorSpec(SRC_SLIDE, "elbow", GEOM, "triangle", "arrow", "000000", SPID, 1, 99, 1, null, "C", SPID);
            case "AddPictureSpec"          -> new CommandSpec.AddPictureSpec(SRC_SLIDE, BlipRef.of("ppt/media/image1.png"), GEOM, "Pic", SPID);
            default -> throw new AssertionError(
                "permits entry without coverage in RetargetToSlideTest: " + cls.getName());
        };
    }
}
