package com.excudo.xml.writers;

import com.excudo.core.model.*;
import com.excudo.core.utils.XMLConstants;
import com.excudo.xml.builders.TextBodyXMLWriter;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

/**
 * Verifies that TextBodyXMLWriter produces correct OOXML DOM output
 * for all supported text formatting scenarios.
 */
public class TextBodyXMLWriterTest {

    private Document document;

    @Before
    public void setUp() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        document = builder.newDocument();
    }

    @Test
    public void testPlainTextNonPlaceholder() {
        Element txBody = writeFromPlainText("Hello World", false);
        NodeList paragraphs = txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "p");
        assertEquals(1, paragraphs.getLength());

        Element rPr = getRunProperties(paragraphs.item(0));
        assertEquals("1800", rPr.getAttribute("sz"));
        assertSchemeColor(rPr, "lt1");
    }

    @Test
    public void testPlainTextPlaceholder() {
        Element txBody = writeFromPlainText("Hello World", true);
        Element rPr = getRunProperties(txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "p").item(0));
        assertEquals("", rPr.getAttribute("sz"));
        assertEquals(0, rPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "solidFill").getLength());
    }

    @Test
    public void testMultilineText() {
        Element txBody = writeFromPlainText("Line 1\nLine 2\nLine 3", false);
        assertEquals(3, txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "p").getLength());
    }

    @Test
    public void testEmptyText() {
        Element txBody = writeFromPlainText("", false);
        NodeList paragraphs = txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "p");
        assertEquals(1, paragraphs.getLength());
        // Empty paragraph should have endParaRPr
        assertEquals(1, ((Element) paragraphs.item(0))
                .getElementsByTagNameNS(XMLConstants.DRAWING_NS, "endParaRPr").getLength());
    }

    @Test
    public void testNullText() {
        Element txBody = writeFromPlainText(null, false);
        assertEquals(1, txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "p").getLength());
    }

    @Test
    public void testBulletListNonPlaceholder() {
        Element txBody = writeFromPlainText("- First item\n- Second item\n- Third item", false);
        NodeList paragraphs = txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "p");
        assertEquals(3, paragraphs.getLength());

        for (int i = 0; i < 3; i++) {
            Element p = (Element) paragraphs.item(i);
            Element pPr = (Element) p.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "pPr").item(0);
            assertNotNull("Paragraph " + i + " should have properties", pPr);
            assertEquals("342900", pPr.getAttribute("marL"));
            assertEquals("-342900", pPr.getAttribute("indent"));

            NodeList buChars = pPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "buChar");
            assertEquals(1, buChars.getLength());
            assertEquals("\u2022", ((Element) buChars.item(0)).getAttribute("char"));

            NodeList buFonts = pPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "buFont");
            assertEquals(1, buFonts.getLength());
            assertEquals("Arial", ((Element) buFonts.item(0)).getAttribute("typeface"));
        }
    }

    @Test
    public void testBulletListPlaceholder() {
        Element txBody = writeFromPlainText("- First item\n- Second item", true);
        NodeList paragraphs = txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "p");
        assertEquals(2, paragraphs.getLength());

        for (int i = 0; i < 2; i++) {
            Element p = (Element) paragraphs.item(i);
            // Placeholder bullets should NOT have buChar/buFont
            assertEquals(0, p.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "buChar").getLength());
            assertEquals(0, p.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "buFont").getLength());
        }
    }

    @Test
    public void testNestedBullets() {
        Element txBody = writeFromPlainText("- Level 0\n  - Level 1\n    - Level 2", false);
        NodeList paragraphs = txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "p");
        assertEquals(3, paragraphs.getLength());

        assertParagraphLevel(paragraphs.item(0), "", "342900");
        assertParagraphLevel(paragraphs.item(1), "1", "685800");
        assertParagraphLevel(paragraphs.item(2), "2", "1028700");
    }

    @Test
    public void testNumberedList() {
        Element txBody = writeFromPlainText("1. First\n2. Second\n3. Third", false);
        NodeList paragraphs = txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "p");
        assertEquals(3, paragraphs.getLength());

        for (int i = 0; i < 3; i++) {
            Element pPr = (Element) ((Element) paragraphs.item(i))
                    .getElementsByTagNameNS(XMLConstants.DRAWING_NS, "pPr").item(0);
            NodeList buAutoNums = pPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "buAutoNum");
            assertEquals(1, buAutoNums.getLength());
            assertEquals("arabicPeriod", ((Element) buAutoNums.item(0)).getAttribute("type"));
        }
    }

    @Test
    public void testMixedContent() {
        Element txBody = writeFromPlainText("Title\n- Bullet one\n- Bullet two\nRegular text", false);
        NodeList paragraphs = txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "p");
        assertEquals(4, paragraphs.getLength());

        // Title: no bullet
        assertEquals(0, ((Element) paragraphs.item(0))
                .getElementsByTagNameNS(XMLConstants.DRAWING_NS, "buChar").getLength());
        // Bullets: have buChar
        assertEquals(1, ((Element) paragraphs.item(1))
                .getElementsByTagNameNS(XMLConstants.DRAWING_NS, "buChar").getLength());
        assertEquals(1, ((Element) paragraphs.item(2))
                .getElementsByTagNameNS(XMLConstants.DRAWING_NS, "buChar").getLength());
        // Regular: no bullet
        assertEquals(0, ((Element) paragraphs.item(3))
                .getElementsByTagNameNS(XMLConstants.DRAWING_NS, "buChar").getLength());
    }

    @Test
    public void testEmptyLines() {
        Element txBody = writeFromPlainText("Before\n\nAfter", false);
        NodeList paragraphs = txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "p");
        assertEquals(3, paragraphs.getLength());

        // Middle paragraph should be empty with endParaRPr
        Element middle = (Element) paragraphs.item(1);
        assertEquals(0, middle.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "r").getLength());
        assertEquals(1, middle.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "endParaRPr").getLength());
    }

    @Test
    public void testWriteCreatesCompleteTxBody() {
        TextBody body = TextBody.fromPlainText("Test text", false);
        Element txBody = TextBodyXMLWriter.write(document, body);

        assertEquals("p:txBody", txBody.getNodeName());
        assertEquals(1, txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "bodyPr").getLength());
        assertEquals(1, txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "lstStyle").getLength());
        assertTrue(txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "p").getLength() > 0);
    }

    @Test
    public void testWriteWithBodyProperties() {
        TextBody body = TextBody.builder()
                .bodyProperties(BodyProperties.builder()
                        .verticalAlignment("ctr")
                        .wrap("square")
                        .autofit(AutofitType.NORMAL)
                        .fontScale(92500)
                        .build())
                .addParagraph(TextParagraph.builder().addText("Test").build())
                .build();

        Element txBody = TextBodyXMLWriter.write(document, body);
        Element bodyPr = (Element) txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "bodyPr").item(0);

        assertEquals("ctr", bodyPr.getAttribute("anchor"));
        assertEquals("square", bodyPr.getAttribute("wrap"));

        NodeList normAutofit = bodyPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "normAutofit");
        assertEquals(1, normAutofit.getLength());
        assertEquals("92500", ((Element) normAutofit.item(0)).getAttribute("fontScale"));
    }

    @Test
    public void testRichTextFormatting() {
        TextBody body = TextBody.builder()
                .addParagraph(TextParagraph.builder()
                        .addRun(TextRun.builder("Bold")
                                .bold(true)
                                .fontSize(2400)
                                .hexColor("FF0000")
                                .fontFamily("Arial")
                                .build())
                        .alignment("ctr")
                        .build())
                .build();

        Element txBody = TextBodyXMLWriter.write(document, body);
        Element rPr = (Element) txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "rPr").item(0);

        assertEquals("1", rPr.getAttribute("b"));
        assertEquals("2400", rPr.getAttribute("sz"));

        NodeList srgbClrs = rPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "srgbClr");
        assertEquals(1, srgbClrs.getLength());
        assertEquals("FF0000", ((Element) srgbClrs.item(0)).getAttribute("val"));

        NodeList latins = rPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "latin");
        assertEquals(1, latins.getLength());
        assertEquals("Arial", ((Element) latins.item(0)).getAttribute("typeface"));
    }

    @Test
    public void testShapeAutofitType() {
        TextBody body = TextBody.builder()
                .bodyProperties(BodyProperties.builder().autofit(AutofitType.SHAPE).build())
                .addParagraph(TextParagraph.builder().addText("Fit").build())
                .build();

        Element txBody = TextBodyXMLWriter.write(document, body);
        Element bodyPr = (Element) txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "bodyPr").item(0);
        assertEquals(1, bodyPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "spAutoFit").getLength());
    }

    @Test
    public void testNoAutofitType() {
        TextBody body = TextBody.builder()
                .bodyProperties(BodyProperties.builder().autofit(AutofitType.NONE).build())
                .addParagraph(TextParagraph.builder().addText("No fit").build())
                .build();

        Element txBody = TextBodyXMLWriter.write(document, body);
        Element bodyPr = (Element) txBody.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "bodyPr").item(0);
        assertEquals(1, bodyPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "noAutofit").getLength());
    }

    // --- Helpers ---

    private Element writeFromPlainText(String text, boolean isPlaceholder) {
        Element txBody = document.createElementNS(XMLConstants.DRAWING_NS, "a:txBody");
        TextBody body = TextBody.fromPlainText(text, isPlaceholder);
        TextBodyXMLWriter.writeParagraphs(document, txBody, body);
        return txBody;
    }

    private Element getRunProperties(Node paragraph) {
        Element r = (Element) ((Element) paragraph).getElementsByTagNameNS(XMLConstants.DRAWING_NS, "r").item(0);
        return (Element) r.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "rPr").item(0);
    }

    private void assertSchemeColor(Element rPr, String expected) {
        NodeList fills = rPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "solidFill");
        assertEquals(1, fills.getLength());
        Element schemeClr = (Element) ((Element) fills.item(0))
                .getElementsByTagNameNS(XMLConstants.DRAWING_NS, "schemeClr").item(0);
        assertNotNull(schemeClr);
        assertEquals(expected, schemeClr.getAttribute("val"));
    }

    private void assertParagraphLevel(Node paragraph, String expectedLvl, String expectedMarL) {
        Element pPr = (Element) ((Element) paragraph)
                .getElementsByTagNameNS(XMLConstants.DRAWING_NS, "pPr").item(0);
        assertNotNull(pPr);
        assertEquals(expectedLvl, pPr.getAttribute("lvl"));
        assertEquals(expectedMarL, pPr.getAttribute("marL"));
    }
}
