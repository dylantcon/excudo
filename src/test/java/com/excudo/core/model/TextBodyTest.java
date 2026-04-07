package com.excudo.core.model;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Tests for TextBody model construction and fromPlainText parsing.
 */
public class TextBodyTest {

    @Test
    public void testFromPlainTextSimple() {
        TextBody body = TextBody.fromPlainText("Hello World", false);
        assertEquals(1, body.getParagraphs().size());

        TextParagraph para = body.getParagraphs().get(0);
        assertEquals(1, para.getRuns().size());
        assertEquals("Hello World", para.getRuns().get(0).getText());
        assertFalse(body.isPlaceholder());
    }

    @Test
    public void testFromPlainTextMultiline() {
        TextBody body = TextBody.fromPlainText("Line 1\nLine 2\nLine 3", false);
        assertEquals(3, body.getParagraphs().size());
        assertEquals("Line 1", body.getParagraphs().get(0).getRuns().get(0).getText());
        assertEquals("Line 2", body.getParagraphs().get(1).getRuns().get(0).getText());
        assertEquals("Line 3", body.getParagraphs().get(2).getRuns().get(0).getText());
    }

    @Test
    public void testFromPlainTextEmpty() {
        TextBody body = TextBody.fromPlainText("", false);
        assertEquals(1, body.getParagraphs().size());
        assertTrue(body.getParagraphs().get(0).isEmpty());
    }

    @Test
    public void testFromPlainTextNull() {
        TextBody body = TextBody.fromPlainText(null, false);
        assertEquals(1, body.getParagraphs().size());
        assertTrue(body.getParagraphs().get(0).isEmpty());
    }

    @Test
    public void testFromPlainTextBullets() {
        TextBody body = TextBody.fromPlainText("- First\n- Second\n- Third", false);
        assertEquals(3, body.getParagraphs().size());

        for (TextParagraph para : body.getParagraphs()) {
            assertEquals(BulletType.CHARACTER, para.getBulletType());
            assertEquals("\u2022", para.getBulletChar());
            assertEquals("Arial", para.getBulletFont());
            assertEquals(0, para.getLevel());
            assertNotNull(para.getMarginLeft());
            assertEquals(342900, para.getMarginLeft().intValue());
            assertNotNull(para.getIndent());
            assertEquals(-342900, para.getIndent().intValue());
        }
    }

    @Test
    public void testFromPlainTextBulletsPlaceholder() {
        TextBody body = TextBody.fromPlainText("- First\n- Second", true);
        assertEquals(2, body.getParagraphs().size());

        for (TextParagraph para : body.getParagraphs()) {
            assertEquals(BulletType.INHERITED, para.getBulletType());
            assertNull(para.getBulletChar());
        }
    }

    @Test
    public void testFromPlainTextNestedBullets() {
        TextBody body = TextBody.fromPlainText("- Level 0\n  - Level 1\n    - Level 2", false);
        assertEquals(3, body.getParagraphs().size());
        assertEquals(0, body.getParagraphs().get(0).getLevel());
        assertEquals(1, body.getParagraphs().get(1).getLevel());
        assertEquals(2, body.getParagraphs().get(2).getLevel());

        assertEquals(342900, body.getParagraphs().get(0).getMarginLeft().intValue());
        assertEquals(685800, body.getParagraphs().get(1).getMarginLeft().intValue());
        assertEquals(1028700, body.getParagraphs().get(2).getMarginLeft().intValue());
    }

    @Test
    public void testFromPlainTextNumbered() {
        TextBody body = TextBody.fromPlainText("1. First\n2. Second", false);
        assertEquals(2, body.getParagraphs().size());

        for (TextParagraph para : body.getParagraphs()) {
            assertEquals(BulletType.AUTONUMBER, para.getBulletType());
            assertEquals("arabicPeriod", para.getAutonumType());
        }
    }

    @Test
    public void testFromPlainTextMixed() {
        TextBody body = TextBody.fromPlainText("Title\n- Bullet one\n- Bullet two\nRegular", false);
        assertEquals(4, body.getParagraphs().size());

        assertEquals(BulletType.NONE, body.getParagraphs().get(0).getBulletType());
        assertEquals(BulletType.CHARACTER, body.getParagraphs().get(1).getBulletType());
        assertEquals(BulletType.CHARACTER, body.getParagraphs().get(2).getBulletType());
        assertEquals(BulletType.NONE, body.getParagraphs().get(3).getBulletType());
    }

    @Test
    public void testFromPlainTextNonPlaceholderFormatting() {
        TextBody body = TextBody.fromPlainText("Hello", false);
        TextRun run = body.getParagraphs().get(0).getRuns().get(0);

        assertEquals(Integer.valueOf(1800), run.getFontSize());
        assertNotNull(run.getColor());
        assertTrue(run.getColor().isScheme());
        assertEquals("lt1", run.getColor().getSchemeVal());
    }

    @Test
    public void testFromPlainTextPlaceholderNoExplicitFormatting() {
        TextBody body = TextBody.fromPlainText("Hello", true);
        TextRun run = body.getParagraphs().get(0).getRuns().get(0);

        assertNull(run.getFontSize());
        assertNull(run.getColor());
    }

    @Test
    public void testFromPlainTextEmptyLine() {
        TextBody body = TextBody.fromPlainText("Before\n\nAfter", false);
        assertEquals(3, body.getParagraphs().size());
        assertFalse(body.getParagraphs().get(0).isEmpty());
        assertTrue(body.getParagraphs().get(1).isEmpty());
        assertFalse(body.getParagraphs().get(2).isEmpty());
    }

    @Test
    public void testBuilderManual() {
        TextBody body = TextBody.builder()
                .addParagraph(TextParagraph.builder()
                        .addRun(TextRun.builder("Bold text").bold(true).fontSize(2400).build())
                        .alignment("ctr")
                        .build())
                .bodyProperties(BodyProperties.builder()
                        .verticalAlignment("ctr")
                        .build())
                .build();

        assertEquals(1, body.getParagraphs().size());
        assertEquals("ctr", body.getParagraphs().get(0).getAlignment());
        assertTrue(body.getParagraphs().get(0).getRuns().get(0).getBold());
        assertEquals(Integer.valueOf(2400), body.getParagraphs().get(0).getRuns().get(0).getFontSize());
        assertEquals("ctr", body.getBodyProperties().getVerticalAlignment());
    }

    @Test
    public void testTextRunBuilder() {
        TextRun run = TextRun.builder("test")
                .fontSize(2400)
                .bold(true)
                .italic(true)
                .underline("sng")
                .fontFamily("Arial")
                .hexColor("FF0000")
                .language("fr-FR")
                .build();

        assertEquals("test", run.getText());
        assertEquals(Integer.valueOf(2400), run.getFontSize());
        assertTrue(run.getBold());
        assertTrue(run.getItalic());
        assertEquals("sng", run.getUnderline());
        assertEquals("Arial", run.getFontFamily());
        assertFalse(run.getColor().isScheme());
        assertEquals("FF0000", run.getColor().getHexVal());
        assertEquals("fr-FR", run.getLanguage());
    }

    @Test
    public void testTextColorScheme() {
        TextColor c = TextColor.scheme("accent1");
        assertTrue(c.isScheme());
        assertEquals("accent1", c.getSchemeVal());
        assertNull(c.getHexVal());
    }

    @Test
    public void testTextColorHex() {
        TextColor c = TextColor.hex("#00FF00");
        assertFalse(c.isScheme());
        assertEquals("00FF00", c.getHexVal());
        assertNull(c.getSchemeVal());
    }

    @Test
    public void testBodyPropertiesDefault() {
        BodyProperties bp = BodyProperties.DEFAULT;
        assertNull(bp.getVerticalAlignment());
        assertNull(bp.getWrap());
        assertNull(bp.getAutofit());
        assertFalse(bp.isRtlCol());
    }

    @Test
    public void testBodyPropertiesBuilder() {
        BodyProperties bp = BodyProperties.builder()
                .verticalAlignment("ctr")
                .wrap("square")
                .autofit(AutofitType.NORMAL)
                .fontScale(92500)
                .leftInset(91440)
                .numColumns(2)
                .build();

        assertEquals("ctr", bp.getVerticalAlignment());
        assertEquals("square", bp.getWrap());
        assertEquals(AutofitType.NORMAL, bp.getAutofit());
        assertEquals(Integer.valueOf(92500), bp.getFontScale());
        assertEquals(Integer.valueOf(91440), bp.getLeftInset());
        assertEquals(Integer.valueOf(2), bp.getNumColumns());
    }

    @Test
    public void testParagraphIsEmpty() {
        assertTrue(TextParagraph.builder().build().isEmpty());
        assertTrue(TextParagraph.builder().addText("").build().isEmpty());
        assertFalse(TextParagraph.builder().addText("hello").build().isEmpty());
    }
}
