package com.excudo.view.rendering.text;

import com.excudo.core.model.TextBody;
import com.excudo.core.rendering.surface.SurfacePaint;
import com.excudo.test.utils.RecordingRenderSurface;
import org.junit.Test;

import static com.excudo.view.rendering.text.TextPaintTestSupport.*;
import static org.junit.Assert.*;

/**
 * Bullet decorations, pinned against PowerPoint's export of the
 * text-bullets corpus deck ("Red oversized bullet" cell): buSzPct
 * scales the bullet relative to the FIRST RUN's size (not a theme
 * constant), buClr colors it, and the bullet baseline coincides with
 * the first line's text baseline.
 */
public class TextBulletStyleTest {

    @Test
    public void buSzPctAndBuClrStyleTheBullet() {
        TextBody body = extract(ZERO_INSETS
            + "<a:p><a:pPr>"
            + "<a:buClr><a:srgbClr val=\"C00000\"/></a:buClr>"
            + "<a:buSzPct val=\"150000\"/>"
            + "<a:buFont typeface=\"Arial\"/><a:buChar char=\"●\"/>"
            + "</a:pPr>" + run("Red oversized bullet", 1400) + "</a:p>");

        Painted p = paint(body, 445, 200);

        RecordingRenderSurface.TextCall bullet = null;
        RecordingRenderSurface.TextCall firstText = null;
        for (var c : p.surface().textCalls) {
            if (c.text().equals("●") && bullet == null) bullet = c;
            if (c.text().startsWith("Red") && firstText == null) firstText = c;
        }
        assertNotNull("bullet glyph must be painted", bullet);
        assertNotNull(firstText);

        // 150% of the 14pt run = 21pt = 28px.
        assertEquals("buSzPct scales the bullet relative to the run size",
            14.0 * 1.5 * 96 / 72, bullet.font().sizePx(), 0.3);

        assertTrue("bullet fill must be a solid paint",
            bullet.fill() instanceof SurfacePaint.Solid);
        SurfacePaint.Solid solid = (SurfacePaint.Solid) bullet.fill();
        assertEquals("buClr must color the bullet", 0xFFC00000, solid.argb());

        // The bullet shares the first line's baseline.
        assertEquals("bullet baseline must coincide with the first text baseline",
            firstText.baselineY(), bullet.baselineY(), 0.5);
        // ...and the first line's text starts after the bullet, not on top of it.
        assertTrue("first-line text must start right of the bullet glyph",
            firstText.x() > bullet.x());
    }

    @Test
    public void pictureBulletExtractsRelationshipId() {
        TextBody body = extract(ZERO_INSETS
            + "<a:p><a:pPr>"
            + "<a:buBlip><a:blip r:embed=\"rId7\" xmlns:r=\""
            + "http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/></a:buBlip>"
            + "</a:pPr>" + run("picture bulleted", 1400) + "</a:p>");

        var para = body.getParagraphs().get(0);
        assertEquals(com.excudo.core.model.BulletType.PICTURE, para.getBulletType());
        assertEquals("rId7", para.getBulletImageRelId());
    }

    @Test
    public void autoNumberRendersInRunFontAndSize() {
        TextBody body = extract(ZERO_INSETS
            + "<a:p><a:pPr><a:buAutoNum type=\"arabicPeriod\"/></a:pPr>"
            + run("numbered item", 1400) + "</a:p>");

        Painted p = paint(body, 445, 200);

        RecordingRenderSurface.TextCall number = null;
        for (var c : p.surface().textCalls) {
            if (c.text().equals("1.")) { number = c; break; }
        }
        assertNotNull("auto-number must be painted", number);
        assertEquals("auto-number uses the run's font family (PowerPoint renders "
            + "unstyled numbers in the paragraph's text font)",
            "Calibri", number.font().family());
        assertEquals("auto-number uses the run's size",
            14.0 * 96 / 72, number.font().sizePx(), 0.1);
    }
}
