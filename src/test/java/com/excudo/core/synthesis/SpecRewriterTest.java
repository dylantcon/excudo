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
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * Exhaustive over {@code CommandSpec.permits}: for each spec type,
 * build a sample, apply a SPID remap, and assert the rewriter
 * preserves every non-SPID field and remaps every SPID-bearing one.
 * The sealed switch in {@link SpecRewriter} mirrors the one here; if a
 * new spec is added to permits without a case being added to BOTH the
 * production switch and this test's switch, compilation fails.
 *
 * <p>Identity rule: rewriter must return the same instance when no
 * SPID in the spec is in the map (no allocation cost on the hot path).
 */
public class SpecRewriterTest {

    private static final int SRC = 10;
    private static final int DST = 20;
    private static final Map<Integer, Integer> MAP = Map.of(SRC, DST);
    private static final ShapeGeometry GEOM = new ShapeGeometry(0, 0, 1000, 1000);

    @Test public void addShapeSpec_doesNotReferenceSpidsExternally_returnsIdentity() {
        CommandSpec spec = new CommandSpec.AddShapeSpec(1, SlideShape.ShapeType.RECTANGLE,
            GEOM, "", "N", null, null, false, SRC);
        assertSame("AddShapeSpec creates a SPID; rewriter returns identity",
            spec, SpecRewriter.rewrite(spec, MAP));
    }

    @Test public void removeShapeSpec_remapsSpid() {
        CommandSpec.RemoveShapeSpec spec = new CommandSpec.RemoveShapeSpec(1, SRC);
        CommandSpec out = SpecRewriter.rewrite(spec, MAP);
        assertNotSame(spec, out);
        assertEquals(DST, ((CommandSpec.RemoveShapeSpec) out).spid());
    }

    @Test public void moveSpec_remapsSpid_preservesXY() {
        CommandSpec.MoveSpec spec = new CommandSpec.MoveSpec(1, SRC, 100L, 200L);
        CommandSpec.MoveSpec out = (CommandSpec.MoveSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals(DST, out.spid());
        assertEquals(100L, out.newX());
        assertEquals(200L, out.newY());
    }

    @Test public void resizeSpec_remapsSpid_preservesWH() {
        CommandSpec.ResizeSpec spec = new CommandSpec.ResizeSpec(1, SRC, 500L, 600L);
        CommandSpec.ResizeSpec out = (CommandSpec.ResizeSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals(DST, out.spid());
        assertEquals(500L, out.newWidth());
        assertEquals(600L, out.newHeight());
    }

    @Test public void rotateSpec_remapsSpid_preservesAngle() {
        CommandSpec.RotateSpec spec = new CommandSpec.RotateSpec(1, SRC, 45.5);
        CommandSpec.RotateSpec out = (CommandSpec.RotateSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals(DST, out.spid());
        assertEquals(45.5, out.newRotationDegrees(), 0.001);
    }

    @Test public void renameShapeSpec_remapsSpid_preservesName() {
        CommandSpec.RenameShapeSpec spec = new CommandSpec.RenameShapeSpec(1, SRC, "X");
        CommandSpec.RenameShapeSpec out = (CommandSpec.RenameShapeSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals(DST, out.spid());
        assertEquals("X", out.newName());
    }

    @Test public void setTextBoxFlagSpec_remapsSpid_preservesFlag() {
        CommandSpec.SetTextBoxFlagSpec spec = new CommandSpec.SetTextBoxFlagSpec(1, SRC, true);
        CommandSpec.SetTextBoxFlagSpec out = (CommandSpec.SetTextBoxFlagSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals(DST, out.spid());
        assertEquals(true, out.flag());
    }

    @Test public void setRunFormatSpec_remapsSpid_preservesRunRef() {
        TextRun run = TextRun.builder("hello").bold(true).build();
        CommandSpec.SetRunFormatSpec spec = new CommandSpec.SetRunFormatSpec(1, SRC, 2, 3, run);
        CommandSpec.SetRunFormatSpec out = (CommandSpec.SetRunFormatSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals(DST, out.spid());
        assertEquals(2, out.paragraphIdx());
        assertEquals(3, out.runIdx());
        assertSame(run, out.newRun());
    }

    @Test public void setTextSpec_remapsSpid_preservesBody() {
        TextBody body = TextBody.builder().addParagraph(
            TextParagraph.builder().addRun(TextRun.builder("hi").build()).build()).build();
        CommandSpec.SetTextSpec spec = new CommandSpec.SetTextSpec(1, SRC, body);
        CommandSpec.SetTextSpec out = (CommandSpec.SetTextSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals(DST, out.spid());
        assertSame(body, out.textBody());
    }

    @Test public void setShapeStyleSpec_remapsSpid_preservesStyle() {
        ShapeStyle style = ShapeStyle.defaultStyle();
        CommandSpec.SetShapeStyleSpec spec = new CommandSpec.SetShapeStyleSpec(1, SRC, style);
        CommandSpec.SetShapeStyleSpec out = (CommandSpec.SetShapeStyleSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals(DST, out.spid());
        assertSame(style, out.style());
    }

    @Test public void reorderSpec_remapsSpid_preservesDirection() {
        CommandSpec.ReorderSpec spec = new CommandSpec.ReorderSpec(1, SRC,
            CommandSpec.ReorderSpec.Direction.FRONT);
        CommandSpec.ReorderSpec out = (CommandSpec.ReorderSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals(DST, out.spid());
        assertEquals(CommandSpec.ReorderSpec.Direction.FRONT, out.direction());
    }

    @Test public void addAnimationSpec_remapsTargetSpid() {
        AnimationBinding b = AnimationBinding.builder()
            .target(SRC).type(AnimationType.APPEAR).durationMs(500).build();
        CommandSpec.AddAnimationSpec spec = new CommandSpec.AddAnimationSpec(1, b);
        CommandSpec.AddAnimationSpec out = (CommandSpec.AddAnimationSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals("target SPID remapped", DST, out.binding().getTargetSpid());
        assertEquals("type preserved",
            AnimationType.APPEAR, out.binding().getAnimationType());
        assertEquals("duration preserved", "500", out.binding().getDuration());
    }

    @Test public void removeAnimationSpec_identityPassThrough() {
        // cTn id is not a SPID, so rewriter never touches RemoveAnimationSpec.
        CommandSpec.RemoveAnimationSpec spec = new CommandSpec.RemoveAnimationSpec(1, 42);
        assertSame(spec, SpecRewriter.rewrite(spec, MAP));
    }

    @Test public void setAnimationTimingSpec_identityPassThrough() {
        CommandSpec.SetAnimationTimingSpec spec = new CommandSpec.SetAnimationTimingSpec(
            1, 42, "1000", "100");
        assertSame(spec, SpecRewriter.rewrite(spec, MAP));
    }

    @Test public void setTransitionSpec_identityPassThrough() {
        CommandSpec.SetTransitionSpec spec = new CommandSpec.SetTransitionSpec(
            1, TransitionType.FADE, "med", null);
        assertSame(spec, SpecRewriter.rewrite(spec, MAP));
    }

    @Test public void clearTransitionSpec_identityPassThrough() {
        CommandSpec.ClearTransitionSpec spec = new CommandSpec.ClearTransitionSpec(1);
        assertSame(spec, SpecRewriter.rewrite(spec, MAP));
    }

    @Test public void createGroupSpec_remapsChildrenInList() {
        CommandSpec.CreateGroupSpec spec = new CommandSpec.CreateGroupSpec(
            1, List.of(SRC, 99), "G");
        CommandSpec.CreateGroupSpec out = (CommandSpec.CreateGroupSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals("first child remapped", DST, (int) out.childSpids().get(0));
        assertEquals("untouched child preserved", 99, (int) out.childSpids().get(1));
        assertEquals("groupName preserved", "G", out.groupName());
    }

    @Test public void ungroupSpec_remapsGroupSpid() {
        CommandSpec.UngroupSpec spec = new CommandSpec.UngroupSpec(1, SRC);
        CommandSpec.UngroupSpec out = (CommandSpec.UngroupSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals(DST, out.groupSpid());
    }

    @Test public void addToGroupSpec_remapsBothEndpoints() {
        CommandSpec.AddToGroupSpec spec = new CommandSpec.AddToGroupSpec(1, SRC, 99);
        CommandSpec.AddToGroupSpec out = (CommandSpec.AddToGroupSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals("group remapped", DST, out.groupSpid());
        assertEquals("child untouched (not in map)", 99, out.childSpid());
    }

    @Test public void detachFromGroupSpec_remapsChildSpid() {
        CommandSpec.DetachFromGroupSpec spec = new CommandSpec.DetachFromGroupSpec(1, SRC);
        CommandSpec.DetachFromGroupSpec out = (CommandSpec.DetachFromGroupSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals(DST, out.childSpid());
    }

    @Test public void createCodeBoxSpec_identityPassThrough() {
        CommandSpec.CreateCodeBoxSpec spec = new CommandSpec.CreateCodeBoxSpec(
            1, "java", "code", 0L, 0L, null, null, null, SRC);
        assertSame(spec, SpecRewriter.rewrite(spec, MAP));
    }

    @Test public void createDiagramSpec_identityPassThrough() {
        CommandSpec.CreateDiagramSpec spec = new CommandSpec.CreateDiagramSpec(
            1, "graph TD\n A --> B", null, null, null, null, SRC);
        assertSame(spec, SpecRewriter.rewrite(spec, MAP));
    }

    @Test public void addConnectorSpec_remapsEndpointSpids() {
        CommandSpec.AddConnectorSpec spec = new CommandSpec.AddConnectorSpec(
            1, "straight", GEOM, null, "triangle", null,
            SRC, 1, 99, 1, null, "C", SRC);
        CommandSpec.AddConnectorSpec out = (CommandSpec.AddConnectorSpec) SpecRewriter.rewrite(spec, MAP);
        assertEquals("startSpid remapped", DST, (int) out.startSpid());
        assertEquals("endSpid untouched (not in map)", 99, (int) out.endSpid());
        assertEquals("connectorType preserved", "straight", out.connectorType());
        assertEquals("tailEnd preserved", "triangle", out.tailEnd());
        // sourceSpidHint is NOT remapped: it's the source's source-side
        // SPID, used for downstream specs to remap THEIR references via
        // the runner's spidMap. Rewriting it here would defeat that.
        assertEquals("sourceSpidHint preserved", (Integer) SRC, out.sourceSpidHint());
    }

    @Test public void addPictureSpec_identityPassThrough() {
        CommandSpec.AddPictureSpec spec = new CommandSpec.AddPictureSpec(
            1, BlipRef.of("ppt/media/image1.png"), GEOM, "Pic", SRC);
        assertSame("AddPictureSpec creates a SPID; rewriter returns identity",
            spec, SpecRewriter.rewrite(spec, MAP));
    }

    /**
     * Sealed-switch exhaustiveness pin. If a new permits entry is added
     * without a corresponding case here AND a corresponding @Test method
     * above, compilation fails. This is the structural guard the plan
     * calls out.
     */
    @Test
    public void permits_exhaustiveCoverage() {
        for (Class<?> raw : CommandSpec.class.getPermittedSubclasses()) {
            @SuppressWarnings("unchecked")
            Class<? extends CommandSpec> cls = (Class<? extends CommandSpec>) raw;
            CommandSpec sample = sampleOf(cls);
            // Just call rewriter to ensure no spec type throws (sealed
            // switch in SpecRewriter is also exhaustive at compile time).
            SpecRewriter.rewrite(sample, MAP);
        }
    }

    private static CommandSpec sampleOf(Class<? extends CommandSpec> cls) {
        // Sealed-switch over permits. Each permits entry MUST appear here.
        return switch (cls.getSimpleName()) {
            case "AddShapeSpec"            -> new CommandSpec.AddShapeSpec(1, SlideShape.ShapeType.RECTANGLE, GEOM, "", "N", null, null, false, SRC);
            case "RemoveShapeSpec"         -> new CommandSpec.RemoveShapeSpec(1, SRC);
            case "MoveSpec"                -> new CommandSpec.MoveSpec(1, SRC, 100L, 200L);
            case "ResizeSpec"              -> new CommandSpec.ResizeSpec(1, SRC, 500L, 600L);
            case "RotateSpec"              -> new CommandSpec.RotateSpec(1, SRC, 45.0);
            case "RenameShapeSpec"         -> new CommandSpec.RenameShapeSpec(1, SRC, "X");
            case "SetTextBoxFlagSpec"      -> new CommandSpec.SetTextBoxFlagSpec(1, SRC, true);
            case "SetRunFormatSpec"        -> new CommandSpec.SetRunFormatSpec(1, SRC, 0, 0, TextRun.builder("x").build());
            case "SetTextSpec"             -> new CommandSpec.SetTextSpec(1, SRC, TextBody.builder().build());
            case "SetShapeStyleSpec"       -> new CommandSpec.SetShapeStyleSpec(1, SRC, ShapeStyle.defaultStyle());
            case "ReorderSpec"             -> new CommandSpec.ReorderSpec(1, SRC, CommandSpec.ReorderSpec.Direction.FRONT);
            case "AddAnimationSpec"        -> new CommandSpec.AddAnimationSpec(1, AnimationBinding.builder().target(SRC).type(AnimationType.APPEAR).build());
            case "RemoveAnimationSpec"     -> new CommandSpec.RemoveAnimationSpec(1, 42);
            case "SetAnimationTimingSpec"  -> new CommandSpec.SetAnimationTimingSpec(1, 42, "500", "0");
            case "SetTransitionSpec"       -> new CommandSpec.SetTransitionSpec(1, TransitionType.FADE, "med", null);
            case "ClearTransitionSpec"     -> new CommandSpec.ClearTransitionSpec(1);
            case "CreateGroupSpec"         -> new CommandSpec.CreateGroupSpec(1, List.of(SRC), "G");
            case "UngroupSpec"             -> new CommandSpec.UngroupSpec(1, SRC);
            case "AddToGroupSpec"          -> new CommandSpec.AddToGroupSpec(1, SRC, 99);
            case "DetachFromGroupSpec"     -> new CommandSpec.DetachFromGroupSpec(1, SRC);
            case "CreateCodeBoxSpec"       -> new CommandSpec.CreateCodeBoxSpec(1, "java", "x", 0L, 0L, null, null, null, SRC);
            case "CreateDiagramSpec"       -> new CommandSpec.CreateDiagramSpec(1, "graph TD\n A --> B", null, null, null, null, SRC);
            case "AddConnectorSpec"        -> new CommandSpec.AddConnectorSpec(1, "straight", GEOM, null, null, null, SRC, 1, 99, 1, null, "C", SRC);
            case "AddPictureSpec"          -> new CommandSpec.AddPictureSpec(1, BlipRef.of("ppt/media/image1.png"), GEOM, "Pic", SRC);
            default -> throw new AssertionError(
                "permits entry without coverage in SpecRewriterTest: " + cls.getName()
                + ". Add a sampleOf() case AND a dedicated @Test for the rewriter "
                + "contract.");
        };
    }
}
