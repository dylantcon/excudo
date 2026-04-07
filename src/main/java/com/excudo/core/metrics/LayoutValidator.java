package com.excudo.core.metrics;

import com.excudo.core.geometry.ArrangeEngine;
import com.excudo.core.geometry.SATCollisionDetector;
import com.excudo.core.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates entire slide layouts for common issues: text overflow, shape overlap,
 * shapes off-slide, and text too small to read.
 */
public final class LayoutValidator {

    private static final long SLIDE_WIDTH = ArrangeEngine.SLIDE_WIDTH;
    private static final long SLIDE_HEIGHT = ArrangeEngine.SLIDE_HEIGHT;
    private static final int MIN_READABLE_FONT_SIZE = 800; // 8pt in hundredths

    private final SATCollisionDetector collisionDetector;

    public LayoutValidator() {
        this.collisionDetector = new SATCollisionDetector();
    }

    /**
     * Validate all shapes on a slide and return detected issues.
     *
     * @param shapes list of shapes on the slide
     * @return list of layout issues (empty = valid)
     */
    public List<LayoutIssue> validate(List<SlideShape> shapes) {
        List<LayoutIssue> issues = new ArrayList<>();

        for (SlideShape shape : shapes) {
            checkOffSlide(shape, issues);
            checkTextOverflow(shape, issues);
            checkTextTooSmall(shape, issues);
        }

        checkShapeOverlaps(shapes, issues);

        return issues;
    }

    private void checkOffSlide(SlideShape shape, List<LayoutIssue> issues) {
        ShapeGeometry geo = shape.getGeometry();
        if (geo == null) return;

        long right = geo.getX() + geo.getWidth();
        long bottom = geo.getY() + geo.getHeight();

        if (geo.getX() < 0 || geo.getY() < 0 || right > SLIDE_WIDTH || bottom > SLIDE_HEIGHT) {
            issues.add(new LayoutIssue(
                LayoutIssue.Type.OFF_SLIDE,
                new int[]{shape.getSpid()},
                String.format("Shape '%s' (SPID %d) extends beyond slide boundaries. "
                    + "Bounds: (%d,%d)-(%d,%d), Slide: (0,0)-(%d,%d)",
                    shape.getName(), shape.getSpid(),
                    geo.getX(), geo.getY(), right, bottom,
                    SLIDE_WIDTH, SLIDE_HEIGHT),
                "Move or resize the shape to fit within the slide."
            ));
        }
    }

    private void checkTextOverflow(SlideShape shape, List<LayoutIssue> issues) {
        if (!shape.hasText() || shape.getGeometry() == null) return;

        // Extract text body from shape's XML element
        org.w3c.dom.Element xmlElement = shape.getXmlElement();
        if (xmlElement == null) return;

        TextBody body = TextBodyExtractor.extractFromShape(xmlElement);
        if (body == null) return;

        long shapeWidth = shape.getGeometry().getWidth();
        long shapeHeight = shape.getGeometry().getHeight();

        MeasuredText measured = TextMeasurer.measure(body, shapeWidth);
        if (measured.overflows(shapeHeight)) {
            long overflow = measured.getTotalHeightEmu() - shapeHeight;
            double overflowPt = overflow / 12700.0;
            issues.add(new LayoutIssue(
                LayoutIssue.Type.TEXT_OVERFLOW,
                new int[]{shape.getSpid()},
                String.format("Text in '%s' (SPID %d) overflows by %.1f pt. "
                    + "Text needs %d EMU, shape height is %d EMU.",
                    shape.getName(), shape.getSpid(),
                    overflowPt, measured.getTotalHeightEmu(), shapeHeight),
                "Reduce font size, shorten text, or increase shape height."
            ));
        }
    }

    private void checkTextTooSmall(SlideShape shape, List<LayoutIssue> issues) {
        if (!shape.hasText()) return;

        org.w3c.dom.Element xmlElement = shape.getXmlElement();
        if (xmlElement == null) return;

        TextBody body = TextBodyExtractor.extractFromShape(xmlElement);
        if (body == null) return;

        for (TextParagraph para : body.getParagraphs()) {
            for (TextRun run : para.getRuns()) {
                if (run.getFontSize() != null && run.getFontSize() < MIN_READABLE_FONT_SIZE) {
                    issues.add(new LayoutIssue(
                        LayoutIssue.Type.TEXT_TOO_SMALL,
                        new int[]{shape.getSpid()},
                        String.format("Text in '%s' (SPID %d) has font size %.1f pt, "
                            + "below minimum readable size of %.1f pt.",
                            shape.getName(), shape.getSpid(),
                            run.getFontSize() / 100.0, MIN_READABLE_FONT_SIZE / 100.0),
                        "Increase font size to at least 8pt for readability."
                    ));
                    return; // One issue per shape is enough
                }
            }
        }
    }

    private void checkShapeOverlaps(List<SlideShape> shapes, List<LayoutIssue> issues) {
        for (int i = 0; i < shapes.size(); i++) {
            SlideShape a = shapes.get(i);
            if (a.getGeometry() == null) continue;

            for (int j = i + 1; j < shapes.size(); j++) {
                SlideShape b = shapes.get(j);
                if (b.getGeometry() == null) continue;

                if (collisionDetector.areShapesColliding(a, b)) {
                    issues.add(new LayoutIssue(
                        LayoutIssue.Type.SHAPE_OVERLAP,
                        new int[]{a.getSpid(), b.getSpid()},
                        String.format("Shapes '%s' (SPID %d) and '%s' (SPID %d) overlap.",
                            a.getName(), a.getSpid(), b.getName(), b.getSpid()),
                        "Reposition shapes to eliminate overlap."
                    ));
                }
            }
        }
    }
}
