package com.excudo.core.metrics;

import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextParagraph;
import com.excudo.core.model.TextRun;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins the Tier-A math fallback in {@link TextBodyExtractor}: when a
 * paragraph contains an OMML subtree (a14:m / m:oMath / m:oMathPara),
 * the extractor concatenates every {@code <m:t>} text leaf into a single
 * italic Cambria Math run, in document order. Without this, the formula
 * is invisible -- the prior behaviour was to ignore non-{@code a:r}
 * children of paragraphs entirely.
 *
 * <p>Tier C will replace the flat run with a typed {@code MathBody},
 * but the fallback keeps math visible in the meantime.
 */
public class TextBodyExtractorMathFallbackTest {

    @Test
    public void embeddedMathBecomesItalicCambriaRun() throws Exception {
        // PowerPoint authoring pattern from /tmp/Uncertainty Quantification.pptx
        // slide 10: a regular text run + an inline a14:m wrapper around oMath.
        TextBody body = parseTxBody("""
            <p:txBody xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main'
                      xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'
                      xmlns:a14='http://schemas.microsoft.com/office/drawing/2010/main'
                      xmlns:m='http://schemas.openxmlformats.org/officeDocument/2006/math'>
              <a:bodyPr/>
              <a:p>
                <a:r><a:rPr lang='en-US'/><a:t>Predicted: </a:t></a:r>
                <a14:m>
                  <m:oMath>
                    <m:r><m:t>y</m:t></m:r>
                    <m:r><m:t>(</m:t></m:r>
                    <m:r><m:t>x</m:t></m:r>
                    <m:r><m:t>*) ~ </m:t></m:r>
                    <m:r><m:t>N</m:t></m:r>
                  </m:oMath>
                </a14:m>
              </a:p>
            </p:txBody>
            """);

        List<TextRun> runs = body.getParagraphs().get(0).getRuns();
        assertEquals("Plain run + math fallback run", 2, runs.size());
        assertEquals("Plain text preserved", "Predicted: ", runs.get(0).getText());

        TextRun mathRun = runs.get(1);
        assertEquals("Math text concatenated in document order",
            "y(x*) ~ N", mathRun.getText());
        assertEquals("Math run uses Cambria Math",
            "Cambria Math", mathRun.getFontFamily());
        assertEquals("Math run is italic", Boolean.TRUE, mathRun.getItalic());
    }

    @Test
    public void bareOMathWithoutA14WrapperAlsoExtracts() throws Exception {
        // Some PowerPoint docs nest m:oMath directly without the a14:m
        // wrapper (e.g. when not living inside an mc:Choice). Cover both.
        TextBody body = parseTxBody("""
            <p:txBody xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main'
                      xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'
                      xmlns:m='http://schemas.openxmlformats.org/officeDocument/2006/math'>
              <a:bodyPr/>
              <a:p>
                <m:oMath>
                  <m:r><m:t>E</m:t></m:r>
                  <m:r><m:t>=mc</m:t></m:r>
                  <m:r><m:t>2</m:t></m:r>
                </m:oMath>
              </a:p>
            </p:txBody>
            """);

        List<TextRun> runs = body.getParagraphs().get(0).getRuns();
        assertEquals(1, runs.size());
        assertEquals("E=mc2", runs.get(0).getText());
    }

    @Test
    public void emptyMathSubtreeProducesNoRun() throws Exception {
        TextBody body = parseTxBody("""
            <p:txBody xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main'
                      xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'
                      xmlns:a14='http://schemas.microsoft.com/office/drawing/2010/main'
                      xmlns:m='http://schemas.openxmlformats.org/officeDocument/2006/math'>
              <a:bodyPr/>
              <a:p>
                <a:r><a:t>just text</a:t></a:r>
                <a14:m><m:oMath/></a14:m>
              </a:p>
            </p:txBody>
            """);

        List<TextRun> runs = body.getParagraphs().get(0).getRuns();
        assertEquals("Empty math contributes no run", 1, runs.size());
        assertEquals("just text", runs.get(0).getText());
    }

    @Test
    public void deeplyNestedMathTextStillFlattens() throws Exception {
        // m:f (fraction) wraps m:num + m:den, each wrapping their own runs.
        // The fallback walks recursively so nested element kinds don't
        // hide their leaf text.
        TextBody body = parseTxBody("""
            <p:txBody xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main'
                      xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'
                      xmlns:m='http://schemas.openxmlformats.org/officeDocument/2006/math'>
              <a:bodyPr/>
              <a:p>
                <m:oMath>
                  <m:f>
                    <m:num><m:r><m:t>a</m:t></m:r></m:num>
                    <m:den><m:r><m:t>b</m:t></m:r></m:den>
                  </m:f>
                </m:oMath>
              </a:p>
            </p:txBody>
            """);

        List<TextRun> runs = body.getParagraphs().get(0).getRuns();
        // Tier A flattens "a/b" to "ab"; Tier C will preserve the
        // numerator-over-denominator structure. The pin here is that
        // we don't lose the leaves.
        assertEquals(1, runs.size());
        assertEquals("ab", runs.get(0).getText());
    }

    private TextBody parseTxBody(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return TextBodyExtractor.extract((Element) doc.getDocumentElement());
    }
}
