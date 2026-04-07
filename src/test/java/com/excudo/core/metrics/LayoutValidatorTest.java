package com.excudo.core.metrics;

import com.excudo.core.model.*;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import static org.junit.Assert.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;

/**
 * Tests LayoutValidator: text overflow, shape overlap, off-slide, text too small.
 */
public class LayoutValidatorTest {

    private final LayoutValidator validator = new LayoutValidator();

    @Test
    public void testValidSlideReturnsNoIssues() {
        SlideShape shape = createTextShape(1, "Title",
            838200, 365125, 7772400, 1470025,
            "Short title", 2400);

        List<LayoutIssue> issues = validator.validate(List.of(shape));
        assertEquals("Valid slide should have no issues", 0, issues.size());
    }

    @Test
    public void testDetectsOffSlideRight() {
        // Shape extends beyond right edge (12192000 EMU)
        SlideShape shape = createShapeWithGeometry(1, "OffRight",
            11000000, 100000, 2000000, 500000);

        List<LayoutIssue> issues = validator.validate(List.of(shape));
        assertTrue("Should detect off-slide shape",
            issues.stream().anyMatch(i -> i.getType() == LayoutIssue.Type.OFF_SLIDE));
    }

    @Test
    public void testDetectsOffSlideNegative() {
        SlideShape shape = createShapeWithGeometry(1, "OffLeft",
            -100000, 100000, 500000, 500000);

        List<LayoutIssue> issues = validator.validate(List.of(shape));
        assertTrue("Should detect negative coordinate",
            issues.stream().anyMatch(i -> i.getType() == LayoutIssue.Type.OFF_SLIDE));
    }

    @Test
    public void testDetectsShapeOverlap() {
        SlideShape a = createShapeWithGeometry(1, "ShapeA",
            1000000, 1000000, 2000000, 1000000);
        SlideShape b = createShapeWithGeometry(2, "ShapeB",
            2000000, 1000000, 2000000, 1000000);

        List<LayoutIssue> issues = validator.validate(List.of(a, b));
        assertTrue("Should detect overlapping shapes",
            issues.stream().anyMatch(i -> i.getType() == LayoutIssue.Type.SHAPE_OVERLAP));
    }

    @Test
    public void testNoOverlapWhenSeparated() {
        SlideShape a = createShapeWithGeometry(1, "ShapeA",
            0, 0, 1000000, 1000000);
        SlideShape b = createShapeWithGeometry(2, "ShapeB",
            5000000, 0, 1000000, 1000000);

        List<LayoutIssue> issues = validator.validate(List.of(a, b));
        assertTrue("Should have no overlap issues",
            issues.stream().noneMatch(i -> i.getType() == LayoutIssue.Type.SHAPE_OVERLAP));
    }

    @Test
    public void testDetectsTextTooSmall() {
        SlideShape shape = createTextShape(1, "Tiny",
            0, 0, 5000000, 5000000,
            "Tiny text", 600); // 6pt

        List<LayoutIssue> issues = validator.validate(List.of(shape));
        assertTrue("Should detect text too small",
            issues.stream().anyMatch(i -> i.getType() == LayoutIssue.Type.TEXT_TOO_SMALL));
    }

    @Test
    public void testTextOverflow() {
        // Tiny shape with large text
        SlideShape shape = createTextShape(1, "Overflow",
            0, 0, 2000000, 200000, // very short height
            "This is a very long text that will definitely overflow this tiny shape because it has too much content for the available height",
            2400); // 24pt

        List<LayoutIssue> issues = validator.validate(List.of(shape));
        assertTrue("Should detect text overflow",
            issues.stream().anyMatch(i -> i.getType() == LayoutIssue.Type.TEXT_OVERFLOW));
    }

    // ---- Helpers ----

    private SlideShape createShapeWithGeometry(int spid, String name,
                                                long x, long y, long w, long h) {
        return new SlideShape(spid, name, SlideShape.ShapeType.RECTANGLE, null,
            new ShapeGeometry(x, y, w, h), null);
    }

    private SlideShape createTextShape(int spid, String name,
                                        long x, long y, long w, long h,
                                        String text, int fontSize) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element sp = doc.createElementNS(
                "http://schemas.openxmlformats.org/presentationml/2006/main", "p:sp");

            TextBody body = TextBody.builder()
                .bodyProperties(BodyProperties.builder()
                    .leftInset(91440).topInset(45720).rightInset(91440).bottomInset(45720)
                    .build())
                .addParagraph(TextParagraph.builder()
                    .addRun(TextRun.builder(text).fontSize(fontSize).build())
                    .build())
                .build();

            Element txBody = com.excudo.xml.builders.TextBodyXMLWriter.write(doc, body);
            sp.appendChild(txBody);
            doc.appendChild(sp);

            return new SlideShape(spid, name, SlideShape.ShapeType.RECTANGLE, text,
                new ShapeGeometry(x, y, w, h), sp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
