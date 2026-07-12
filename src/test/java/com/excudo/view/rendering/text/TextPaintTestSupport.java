package com.excudo.view.rendering.text;

import com.excudo.core.metrics.MeasuredText;
import com.excudo.core.metrics.TextBodyExtractor;
import com.excudo.core.metrics.TextMeasurer;
import com.excudo.core.model.TextBody;
import com.excudo.test.utils.RecordingRenderSurface;
import com.excudo.view.rendering.CoordinateMapper;
import com.excudo.view.rendering.RenderingContext;
import javafx.geometry.Rectangle2D;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shared fixture plumbing for TextPainter/TextMeasurer behavioural
 * tests. Fixtures are authored as raw txBody DrawingML and extracted
 * through {@link TextBodyExtractor} — the same path production takes —
 * then measured and painted onto a {@link RecordingRenderSurface}.
 *
 * <p>The canvas is 1280x720 for a 12192000 EMU slide, so the composite
 * scale is exactly 1.0 and expected pixel values are easy to derive by
 * hand (1 pt = 4/3 px).
 */
final class TextPaintTestSupport {

    static final String P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main";
    static final String A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";

    private TextPaintTestSupport() {}

    /** Parse a txBody's inner XML (bodyPr + paragraphs) into a p:txBody element. */
    static Element txBody(String innerXml) {
        String xml = "<p:txBody xmlns:p=\"" + P_NS + "\" xmlns:a=\"" + A_NS + "\">"
            + innerXml + "</p:txBody>";
        return parse(xml);
    }

    static Element parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            return f.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("bad test fixture XML", e);
        }
    }

    static TextBody extract(String innerXml) {
        return TextBodyExtractor.extract(txBody(innerXml));
    }

    /** A run with explicit Calibri family, size (centipoints) and black color. */
    static String run(String text, int sizeCentiPt) {
        return run(text, sizeCentiPt, "");
    }

    /** A run with extra rPr attributes (e.g. {@code spc="300" cap="small"}). */
    static String run(String text, int sizeCentiPt, String extraRprAttrs) {
        return "<a:r><a:rPr sz=\"" + sizeCentiPt + "\" " + extraRprAttrs + ">"
            + "<a:solidFill><a:srgbClr val=\"000000\"/></a:solidFill>"
            + "<a:latin typeface=\"Calibri\"/></a:rPr>"
            + "<a:t>" + text + "</a:t></a:r>";
    }

    static final String ZERO_INSETS = "<a:bodyPr lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\"/>";

    record Painted(RecordingRenderSurface surface, MeasuredText measured) {}

    /**
     * Measure {@code body} against {@code widthPx} and paint it into a
     * box of {@code widthPx x heightPx} at the canvas origin. Uses the
     * legacy two-arg measure / six-arg paint entry points so the fixture
     * compiles (and demonstrably fails) against the pre-A3 pipeline.
     */
    static Painted paint(TextBody body, double widthPx, double heightPx) {
        long widthEmu = Math.round(widthPx * 9525.0);
        MeasuredText measured = TextMeasurer.measure(body, widthEmu);
        RecordingRenderSurface surface = new RecordingRenderSurface(1280, 720);
        RenderingContext ctx = new RenderingContext(surface, new CoordinateMapper(1280, 720));
        TextPainter.paint(body, measured, new Rectangle2D(0, 0, widthPx, heightPx),
            ctx, null, null);
        return new Painted(surface, measured);
    }
}
