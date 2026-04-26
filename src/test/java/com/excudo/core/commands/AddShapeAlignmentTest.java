package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.AddShapeCommand;

import com.excudo.core.metrics.TextBodyExtractor;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextParagraph;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pins add-shape paragraph alignment behaviour. Beta report flagged that
 * code or indented text inside a shape rendered center-aligned and lost
 * its leading whitespace because the only path was the default algn="ctr".
 * The alignment override is applied as a post-creation TextBody rewrite.
 *
 * Tests assert on resulting TextBody state -- not on add-shape's exit
 * code -- so a regression in the override path surfaces immediately.
 */
public class AddShapeAlignmentTest {

    private PPTXOrchestratorImpl orchestrator;
    private CommandContext ctx;
    private static final ShapeGeometry GEO =
        new ShapeGeometry(1_000_000, 1_000_000, 3_000_000, 1_000_000);

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Align Test", "slideLayout2");
        ctx = new CommandContext(orchestrator, null);
    }

    @Test
    public void leftAlignmentOverridesDefault() throws Exception {
        AddShapeCommand cmd = new AddShapeCommand(
            1, SlideShape.ShapeType.RECTANGLE, GEO,
            "left text", "L", null, "l", orchestrator);
        cmd.execute();
        TextParagraph p = readFirstParagraph(cmd.getCreatedSpid());
        assertEquals("Paragraph alignment should be l (left)", "l", p.getAlignment());
    }

    @Test
    public void rightAlignmentOverridesDefault() throws Exception {
        AddShapeCommand cmd = new AddShapeCommand(
            1, SlideShape.ShapeType.RECTANGLE, GEO,
            "right text", "R", null, "r", orchestrator);
        cmd.execute();
        TextParagraph p = readFirstParagraph(cmd.getCreatedSpid());
        assertEquals("r", p.getAlignment());
    }

    @Test
    public void justifyAlignmentOverridesDefault() throws Exception {
        AddShapeCommand cmd = new AddShapeCommand(
            1, SlideShape.ShapeType.RECTANGLE, GEO,
            "justify text", "J", null, "just", orchestrator);
        cmd.execute();
        TextParagraph p = readFirstParagraph(cmd.getCreatedSpid());
        assertEquals("just", p.getAlignment());
    }

    @Test
    public void nullAlignmentLeavesDefaultBehavior() throws Exception {
        AddShapeCommand cmd = new AddShapeCommand(
            1, SlideShape.ShapeType.RECTANGLE, GEO,
            "default text", "D", null, null, orchestrator);
        cmd.execute();
        TextParagraph p = readFirstParagraph(cmd.getCreatedSpid());
        // Default behaviour for a non-placeholder rectangle with text is
        // center-align (set in TextBody.fromPlainText for plain
        // non-bulleted lines on non-placeholder shapes).
        assertEquals("Default should remain ctr when no override passed",
            "ctr", p.getAlignment());
    }

    @Test
    public void emptyShapeAlsoGetsAlignmentOverride() throws Exception {
        // Empty shape uses the AbstractShapeFactory empty-body path that
        // hardcodes algn="ctr" on the lone end-paragraph. The override
        // rewrites it so subsequent edits inherit the right alignment.
        AddShapeCommand cmd = new AddShapeCommand(
            1, SlideShape.ShapeType.RECTANGLE, GEO,
            "", "Empty", null, "l", orchestrator);
        cmd.execute();
        TextParagraph p = readFirstParagraph(cmd.getCreatedSpid());
        assertEquals("Empty shape's lone paragraph should also honor the override",
            "l", p.getAlignment());
    }

    @Test
    public void alignmentAliasesAreNormalized() throws Exception {
        // Aliases (left/center/right/justify) should reach the same
        // canonical OOXML token. Drives the parse path through
        // AddShapeCommand.fromParameters -> ShapeCommandFactory.normalizeAlignment.
        var parsed = new com.excudo.core.parsing.CommandParameters("add-shape",
            java.util.Map.of(
                "slide", "1",
                "shape-type", "RECTANGLE",
                "text", "aliased",
                "x", "1000000", "y", "1000000",
                "width", "3000000", "height", "1000000",
                "align", "left"));
        Command cmd = CommandClassRegistry.createFromParameters(parsed, ctx);
        cmd.execute();
        // The created SPID is the next available; instead of plumbing it
        // back, find the most recently added rectangle with "aliased" text.
        SlideShape found = orchestrator.getSlideData(1).getData().orElseThrow()
            .getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getXmlElement() != null)
            .filter(s -> s.getTextContent() != null && s.getTextContent().contains("aliased"))
            .findFirst().orElse(null);
        assertNotNull("Should have created the aliased shape", found);
        TextBody body = TextBodyExtractor.extractFromShape(found.getXmlElement());
        assertEquals("'left' alias should normalize to 'l'",
            "l", body.getParagraphs().get(0).getAlignment());
    }

    @Test
    public void invalidAlignmentTokenFailsLoud() {
        var parsed = new com.excudo.core.parsing.CommandParameters("add-shape",
            java.util.Map.of(
                "slide", "1", "shape-type", "RECTANGLE", "text", "x",
                "x", "0", "y", "0", "width", "100", "height", "100",
                "align", "diagonal"));
        try {
            CommandClassRegistry.createFromParameters(parsed, ctx);
            fail("Unrecognised alignment 'diagonal' should throw");
        } catch (IllegalArgumentException e) {
            assertTrue("Error must list valid alignment tokens",
                e.getMessage().toLowerCase().contains("l/left"));
        }
    }

    private TextParagraph readFirstParagraph(int spid) throws Exception {
        SlideShape shape = orchestrator.getSlideData(1).getData().orElseThrow()
            .getShapeRegistry().getShape(spid);
        assertNotNull(shape);
        TextBody body = TextBodyExtractor.extractFromShape(shape.getXmlElement());
        assertNotNull(body);
        assertFalse("Shape should have at least one paragraph", body.getParagraphs().isEmpty());
        return body.getParagraphs().get(0);
    }
}
