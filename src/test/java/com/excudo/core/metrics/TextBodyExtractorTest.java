package com.excudo.core.metrics;

import com.excudo.core.model.*;
import com.excudo.xml.builders.TextBodyXMLWriter;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import static org.junit.Assert.*;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Tests TextBodyExtractor round-trip: TextBody -> TextBodyXMLWriter -> DOM -> TextBodyExtractor -> TextBody.
 */
public class TextBodyExtractorTest {

    @Test
    public void testRoundTripPlainText() throws Exception {
        TextBody original = TextBody.builder()
            .bodyProperties(BodyProperties.builder().verticalAlignment("ctr").build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Hello World").fontSize(1800).build())
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        assertNotNull(extracted);
        assertEquals(1, extracted.getParagraphs().size());
        assertEquals("Hello World", extracted.getParagraphs().get(0).getRuns().get(0).getText());
        assertEquals(Integer.valueOf(1800), extracted.getParagraphs().get(0).getRuns().get(0).getFontSize());
        assertEquals("ctr", extracted.getBodyProperties().getVerticalAlignment());
    }

    @Test
    public void testRoundTripBulletParagraph() throws Exception {
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Bullet item").fontSize(1400).build())
                .marginLeft(342900)
                .indent(-342900)
                .characterBullet("\u2022", "Arial", null, null, null)
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        assertNotNull(extracted);
        TextParagraph para = extracted.getParagraphs().get(0);
        assertEquals(BulletType.CHARACTER, para.getBulletType());
        assertEquals("\u2022", para.getBulletChar());
        assertEquals("Arial", para.getBulletFont());
        assertEquals(Integer.valueOf(342900), para.getMarginLeft());
        assertEquals(Integer.valueOf(-342900), para.getIndent());
    }

    @Test
    public void testRoundTripMultipleParagraphs() throws Exception {
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Line 1").fontSize(2400).build())
                .alignment("ctr")
                .build())
            .addParagraph(TextParagraph.builder().build()) // empty
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Line 3").fontSize(1200).build())
                .alignment("l")
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        assertEquals(3, extracted.getParagraphs().size());
        assertEquals("Line 1", extracted.getParagraphs().get(0).getRuns().get(0).getText());
        assertTrue("Second paragraph should be empty", extracted.getParagraphs().get(1).isEmpty());
        assertEquals("Line 3", extracted.getParagraphs().get(2).getRuns().get(0).getText());
    }

    @Test
    public void testRoundTripParagraphBooleans() throws Exception {
        // rtl / hangingPunct / eaLnBrk / latinLnBrk are all xsd:boolean
        // round-trip-only paragraph attrs. Render-time enforcement for
        // each is a separate, larger concern.
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("hello").build())
                .rightToLeft(true)
                .hangingPunctuation(false)
                .eastAsianLineBreak(true)
                .latinLineBreak(false)
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        TextParagraph para = extracted.getParagraphs().get(0);
        assertEquals(Boolean.TRUE, para.getRightToLeft());
        assertEquals(Boolean.FALSE, para.getHangingPunctuation());
        assertEquals(Boolean.TRUE, para.getEastAsianLineBreak());
        assertEquals(Boolean.FALSE, para.getLatinLineBreak());
    }

    @Test
    public void testRoundTripDefaultTabSize() throws Exception {
        // defTabSz must round-trip even though tab characters aren't yet
        // honoured at render-time -- losing the attribute on edit would
        // strip author intent from any deck that sets custom tab spacing.
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Tabbed\tcolumn").build())
                .defaultTabSize(457200)  // 0.5 inch
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        TextParagraph para = extracted.getParagraphs().get(0);
        assertEquals(Integer.valueOf(457200), para.getDefaultTabSize());
    }

    @Test
    public void testRoundTripParagraphMargins() throws Exception {
        // marL + marR must both survive the write/extract cycle. marR
        // had no model representation historically, so any paragraph
        // that set it lost it through Excudo -- this pins the fix.
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Indented block").build())
                .marginLeft(457200)
                .marginRight(228600)
                .indent(-228600)
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        TextParagraph para = extracted.getParagraphs().get(0);
        assertEquals(Integer.valueOf(457200), para.getMarginLeft());
        assertEquals(Integer.valueOf(228600), para.getMarginRight());
        assertEquals(Integer.valueOf(-228600), para.getIndent());
    }

    @Test
    public void testRoundTripMarginRightAlone() throws Exception {
        // A paragraph with only marR (no marL) must still trigger the
        // hasParagraphProperties path and write the pPr element.
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Right-margin only").build())
                .marginRight(342900)
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        TextParagraph para = extracted.getParagraphs().get(0);
        assertNull("no marL set", para.getMarginLeft());
        assertEquals(Integer.valueOf(342900), para.getMarginRight());
    }

    @Test
    public void testRoundTripBodyInsets() throws Exception {
        TextBody original = TextBody.builder()
            .bodyProperties(BodyProperties.builder()
                .leftInset(91440)
                .topInset(45720)
                .rightInset(91440)
                .bottomInset(45720)
                .build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Test").build())
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        assertEquals(Integer.valueOf(91440), extracted.getBodyProperties().getLeftInset());
        assertEquals(Integer.valueOf(45720), extracted.getBodyProperties().getTopInset());
        assertEquals(Integer.valueOf(91440), extracted.getBodyProperties().getRightInset());
        assertEquals(Integer.valueOf(45720), extracted.getBodyProperties().getBottomInset());
    }

    @Test
    public void testRoundTripLineSpacing() throws Exception {
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Spaced").build())
                .lineSpacing(150000) // 150%
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        assertEquals(Integer.valueOf(150000), extracted.getParagraphs().get(0).getLineSpacing());
    }

    @Test
    public void testRoundTripAutonumber() throws Exception {
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Numbered").build())
                .autonumber("arabicPeriod")
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        assertEquals(BulletType.AUTONUMBER, extracted.getParagraphs().get(0).getBulletType());
        assertEquals("arabicPeriod", extracted.getParagraphs().get(0).getAutonumType());
    }

    @Test
    public void testRoundTripCapitalizationAll() throws Exception {
        // cap="all" must survive the round-trip AND getDisplayText must
        // return the uppercased string. The raw stored text is untouched.
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Hello World")
                    .capitalization("all")
                    .build())
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        TextRun run = extracted.getParagraphs().get(0).getRuns().get(0);
        assertEquals("Hello World", run.getText());
        assertEquals("all", run.getCapitalization());
        assertEquals("HELLO WORLD", run.getDisplayText());
    }

    @Test
    public void testRoundTripCapitalizationNone() throws Exception {
        // cap="none" is explicit "inherit nothing" -- must round-trip
        // but getDisplayText stays identical to the raw text.
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Hello World")
                    .capitalization("none")
                    .build())
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        TextRun run = extracted.getParagraphs().get(0).getRuns().get(0);
        assertEquals("none", run.getCapitalization());
        assertEquals("Hello World", run.getDisplayText());
    }

    @Test
    public void testRoundTripBaselineSuperscript() throws Exception {
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("E=mc").build())
                .addRun(TextRun.builder("2").baseline(30000).build())
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        var runs = extracted.getParagraphs().get(0).getRuns();
        assertEquals(2, runs.size());
        assertNull("normal text has no baseline", runs.get(0).getBaseline());
        assertEquals("superscript baseline", Integer.valueOf(30000), runs.get(1).getBaseline());
    }

    @Test
    public void testRoundTripKerningThreshold() throws Exception {
        // kern is hundredths of a point. PowerPoint convention: kern=1200
        // means kerning kicks in at 12pt and above.
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Kerned text")
                    .kerningThreshold(1200)
                    .build())
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        TextRun run = extracted.getParagraphs().get(0).getRuns().get(0);
        assertEquals(Integer.valueOf(1200), run.getKerningThreshold());
    }

    @Test
    public void testRoundTripKerningDisabled() throws Exception {
        // kern=0 explicitly disables kerning -- distinct from the
        // "inherit" null state.
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("No kerning")
                    .kerningThreshold(0)
                    .build())
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        TextRun run = extracted.getParagraphs().get(0).getRuns().get(0);
        assertEquals("kern=0 must round-trip distinctly from null",
            Integer.valueOf(0), run.getKerningThreshold());
    }

    @Test
    public void testRoundTripCharacterSpacing() throws Exception {
        // spc is hundredths of a point; positive widens, negative tightens.
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Tracked")
                    .characterSpacing(150)   // +1.5pt per char
                    .build())
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        TextRun run = extracted.getParagraphs().get(0).getRuns().get(0);
        assertEquals(Integer.valueOf(150), run.getCharacterSpacing());
    }

    @Test
    public void testRoundTripBaselineSubscript() throws Exception {
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("H").build())
                .addRun(TextRun.builder("2").baseline(-25000).build())
                .addRun(TextRun.builder("O").build())
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        var runs = extracted.getParagraphs().get(0).getRuns();
        assertEquals(3, runs.size());
        assertEquals("subscript baseline", Integer.valueOf(-25000), runs.get(1).getBaseline());
        assertNull(runs.get(2).getBaseline());
    }

    @Test
    public void testRoundTripTextRunFormatting() throws Exception {
        TextBody original = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Formatted")
                    .fontSize(2000)
                    .bold(true)
                    .italic(true)
                    .fontFamily("Courier New")
                    .schemeColor("lt1")
                    .build())
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        TextRun run = extracted.getParagraphs().get(0).getRuns().get(0);
        assertEquals("Formatted", run.getText());
        assertEquals(Integer.valueOf(2000), run.getFontSize());
        assertEquals(Boolean.TRUE, run.getBold());
        assertEquals(Boolean.TRUE, run.getItalic());
        assertEquals("Courier New", run.getFontFamily());
        assertNotNull(run.getColor());
        assertTrue(run.getColor().isScheme());
        assertEquals("lt1", run.getColor().getSchemeVal());
    }

    @Test
    public void testRoundTripAutofit() throws Exception {
        TextBody original = TextBody.builder()
            .bodyProperties(BodyProperties.builder()
                .autofit(AutofitType.NORMAL)
                .fontScale(75000)
                .build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Autofitted").build())
                .build())
            .build();

        TextBody extracted = roundTrip(original);
        assertEquals(AutofitType.NORMAL, extracted.getBodyProperties().getAutofit());
        assertEquals(Integer.valueOf(75000), extracted.getBodyProperties().getFontScale());
    }

    private TextBody roundTrip(TextBody original) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element txBody = TextBodyXMLWriter.write(doc, original);
        return TextBodyExtractor.extract(txBody);
    }
}
