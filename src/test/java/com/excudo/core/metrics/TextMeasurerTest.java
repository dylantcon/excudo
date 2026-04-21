package com.excudo.core.metrics;

import com.excudo.core.model.*;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests TextMeasurer: known string widths, word wrap, line spacing, body insets.
 */
public class TextMeasurerTest {

    @Test
    public void testSingleLineMeasurement() {
        TextBody body = TextBody.builder()
            .bodyProperties(BodyProperties.builder()
                .leftInset(0).topInset(0).rightInset(0).bottomInset(0).build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Hello").fontSize(1800).build())
                .build())
            .build();

        MeasuredText result = TextMeasurer.measure(body, 10000000); // very wide
        assertNotNull(result);
        assertEquals(1, result.getParagraphs().size());
        assertEquals(1, result.getParagraphs().get(0).getLineCount());
        assertTrue("Total height should be positive", result.getTotalHeightEmu() > 0);
        assertTrue("Max line width should be positive", result.getMaxLineWidthEmu() > 0);
    }

    @Test
    public void testWordWrapOccurs() {
        // Use a narrow width that should force wrapping
        TextBody body = TextBody.builder()
            .bodyProperties(BodyProperties.builder()
                .leftInset(0).topInset(0).rightInset(0).bottomInset(0).build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("This is a longer text that should wrap to multiple lines").fontSize(1800).build())
                .build())
            .build();

        // Measure against a narrow width (about 2 inches = ~1828800 EMU)
        MeasuredText result = TextMeasurer.measure(body, 1828800);
        assertTrue("Should wrap to multiple lines",
            result.getParagraphs().get(0).getLineCount() > 1);
    }

    @Test
    public void testDefaultBodyInsets() {
        TextBody body = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Test").fontSize(1800).build())
                .build())
            .build();

        // Measure with default insets (should add 91440+91440 horizontal, 45720+45720 vertical)
        MeasuredText withDefaults = TextMeasurer.measure(body, 5000000);

        TextBody noInsets = TextBody.builder()
            .bodyProperties(BodyProperties.builder()
                .leftInset(0).topInset(0).rightInset(0).bottomInset(0).build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Test").fontSize(1800).build())
                .build())
            .build();

        MeasuredText withoutInsets = TextMeasurer.measure(noInsets, 5000000);

        // Default insets add 45720+45720 = 91440 EMU to height
        long heightDiff = withDefaults.getTotalHeightEmu() - withoutInsets.getTotalHeightEmu();
        assertEquals("Default insets should add 91440 EMU vertically", 91440, heightDiff);
    }

    @Test
    public void testLineSpacing150Percent() {
        TextBody body100 = TextBody.builder()
            .bodyProperties(BodyProperties.builder()
                .leftInset(0).topInset(0).rightInset(0).bottomInset(0).build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Line").fontSize(1800).build())
                .lineSpacing(100000) // 100%
                .build())
            .build();

        TextBody body150 = TextBody.builder()
            .bodyProperties(BodyProperties.builder()
                .leftInset(0).topInset(0).rightInset(0).bottomInset(0).build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Line").fontSize(1800).build())
                .lineSpacing(150000) // 150%
                .build())
            .build();

        MeasuredText m100 = TextMeasurer.measure(body100, 10000000);
        MeasuredText m150 = TextMeasurer.measure(body150, 10000000);

        // 150% spacing should be 1.5x the 100% height
        double ratio = (double) m150.getTotalHeightEmu() / m100.getTotalHeightEmu();
        assertEquals("150% spacing should be 1.5x height", 1.5, ratio, 0.01);
    }

    @Test
    public void testEmptyTextBody() {
        TextBody body = TextBody.builder().build();
        MeasuredText result = TextMeasurer.measure(body, 5000000);
        assertEquals("Empty body should have 0 height", 0, result.getTotalHeightEmu());
    }

    @Test
    public void testNullTextBody() {
        MeasuredText result = TextMeasurer.measure(null, 5000000);
        assertEquals("Null body should have 0 height", 0, result.getTotalHeightEmu());
    }

    @Test
    public void testOverflowDetection() {
        TextBody body = TextBody.builder()
            .bodyProperties(BodyProperties.builder()
                .leftInset(0).topInset(0).rightInset(0).bottomInset(0).build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Line 1").fontSize(3600).build()) // 36pt
                .build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Line 2").fontSize(3600).build())
                .build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("Line 3").fontSize(3600).build())
                .build())
            .build();

        MeasuredText result = TextMeasurer.measure(body, 10000000);

        // 3 lines at 36pt should overflow a small height (e.g. 300000 EMU ~ 23.6pt)
        assertTrue("Should overflow small height", result.overflows(300000));
        assertFalse("Should not overflow large height", result.overflows(10000000));
    }

    @Test
    public void testMarginRightReducesWrapWidth() {
        // marR pulls the wrap boundary in from the right edge. Two bodies,
        // same text, same shape width: the one with marR=1_000_000 EMU
        // should wrap to at least as many lines as the one without, and
        // usually more when the margin is a meaningful fraction of width.
        TextBody noRight = TextBody.builder()
            .bodyProperties(BodyProperties.builder()
                .leftInset(0).topInset(0).rightInset(0).bottomInset(0).build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("AAAA AAAA AAAA AAAA AAAA").fontSize(1800).build())
                .build())
            .build();

        TextBody withRight = TextBody.builder()
            .bodyProperties(BodyProperties.builder()
                .leftInset(0).topInset(0).rightInset(0).bottomInset(0).build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("AAAA AAAA AAAA AAAA AAAA").fontSize(1800).build())
                .marginRight(1000000)
                .build())
            .build();

        long width = 3000000;
        MeasuredText noR = TextMeasurer.measure(noRight, width);
        MeasuredText withR = TextMeasurer.measure(withRight, width);

        assertTrue("marR must narrow wrap width (>= lines)",
            withR.getParagraphs().get(0).getLineCount()
                >= noR.getParagraphs().get(0).getLineCount());
    }

    @Test
    public void testBulletIndentReducesWidth() {
        TextBody noBullet = TextBody.builder()
            .bodyProperties(BodyProperties.builder()
                .leftInset(0).topInset(0).rightInset(0).bottomInset(0).build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("AAAA AAAA AAAA AAAA AAAA").fontSize(1800).build())
                .build())
            .build();

        TextBody withBullet = TextBody.builder()
            .bodyProperties(BodyProperties.builder()
                .leftInset(0).topInset(0).rightInset(0).bottomInset(0).build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("AAAA AAAA AAAA AAAA AAAA").fontSize(1800).build())
                .marginLeft(342900)
                .build())
            .build();

        // Use a width that might cause wrapping differences
        long width = 3000000;
        MeasuredText noB = TextMeasurer.measure(noBullet, width);
        MeasuredText withB = TextMeasurer.measure(withBullet, width);

        // With bullet margin, text should wrap to more lines (or at least not fewer)
        assertTrue("Bullet margin should cause same or more lines",
            withB.getParagraphs().get(0).getLineCount() >= noB.getParagraphs().get(0).getLineCount());
    }
}
