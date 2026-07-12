package com.excudo.core.metrics;

import com.excudo.core.model.TextBody;
import org.junit.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Two wrap-authority inputs the measurer used to drop entirely:
 * explicit line breaks ({@code <a:br/>}, authored by python-pptx for
 * "\n" and by PowerPoint for Shift+Enter) and {@code bodyPr wrap="none"}
 * (text boxes that must not wrap regardless of width).
 */
public class TextLineBreakAndNoWrapTest {

    private static final String P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";

    private static TextBody extract(String innerXml) {
        String xml = "<p:txBody xmlns:p=\"" + P_NS + "\" xmlns:a=\"" + A_NS + "\">"
            + innerXml + "</p:txBody>";
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            Element el = f.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
            return TextBodyExtractor.extract(el);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String run(String text) {
        return "<a:r><a:rPr sz=\"1400\"><a:latin typeface=\"Calibri\"/></a:rPr>"
            + "<a:t>" + text + "</a:t></a:r>";
    }

    @Test
    public void explicitBreakForcesNewLine() {
        TextBody body = extract(
            "<a:bodyPr lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\"/>"
            + "<a:p>" + run("Word wrap on.") + "<a:br/>" + run("Second line.") + "</a:p>");

        // Very wide box: without the break everything fits on one line.
        MeasuredText measured = TextMeasurer.measure(body, 10_000_000);
        assertEquals("<a:br/> must force a second line even when width allows one",
            2, measured.getParagraphs().get(0).getLineCount());
    }

    @Test
    public void wrapNoneNeverWraps() {
        String longText = "No wrap: The quick brown fox jumps over the lazy dog while "
            + "seventy-seven wizards briskly mix quartz vials of juniper extract.";
        TextBody body = extract(
            "<a:bodyPr wrap=\"none\" lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\"/>"
            + "<a:p>" + run(longText) + "</a:p>");

        // Narrow box: wrapping would need many lines; wrap="none" forbids it.
        MeasuredText measured = TextMeasurer.measure(body, 2_000_000);
        assertEquals("wrap=\"none\" text must stay on a single line",
            1, measured.getParagraphs().get(0).getLineCount());
    }
}
