package com.excudo.view.rendering.text;

import com.excudo.core.model.TextBody;
import com.excudo.core.rendering.surface.SurfaceFont;
import com.excudo.test.utils.RecordingRenderSurface;
import org.junit.Test;

import static com.excudo.view.rendering.text.TextPaintTestSupport.*;
import static org.junit.Assert.*;

/**
 * Character tracking (rPr/@spc) and small caps (cap="small"), pinned
 * against PowerPoint's export of the text-spacing-caps corpus deck.
 * Before A3, tracking only influenced measured wrap widths (words were
 * painted whole with their natural advances, so +3pt tracking rendered
 * identically to none) and cap="small" fell back to the raw text.
 */
public class TextTrackingSmallCapsTest {

    @Test
    public void positiveTrackingSpreadsPaintedGlyphs() {
        String text = "Expanded tracking demo";
        TextBody body = extract(ZERO_INSETS
            + "<a:p>" + run(text, 1600, "spc=\"300\"") + "</a:p>");

        Painted p = paint(body, 800, 200);
        RecordingRenderSurface s = p.surface();
        assertFalse(s.textCalls.isEmpty());

        double left = Double.MAX_VALUE;
        double right = 0;
        for (var c : s.textCalls) {
            left = Math.min(left, c.x());
            right = Math.max(right, c.x() + s.advanceOf(c.font(), c.text()));
        }
        double paintedWidth = right - left;

        // Natural width of the phrase at 16pt (21.33px) Calibri, per the
        // same AWT engine the production surface uses.
        double natural = s.advanceOf(
            SurfaceFont.of("Calibri", 16.0 * 96 / 72), text);
        // +3pt tracking = 4px extra advance per character (22 chars = 88px).
        assertTrue("painted width " + paintedWidth + " must include tracking (natural="
                + natural + ", expected >= natural + 70)",
            paintedWidth >= natural + 70);
    }

    @Test
    public void smallCapsRendersLowercaseAsSmallUppercase() {
        TextBody body = extract(ZERO_INSETS
            + "<a:p>" + run("Small Caps", 1600, "cap=\"small\"") + "</a:p>");

        Painted p = paint(body, 800, 200);

        double fullPx = 16.0 * 96 / 72;   // 21.33
        double smallPx = fullPx * 0.8;    // 17.07

        boolean sawFullS = false;
        boolean sawSmallMALL = false;
        for (var c : p.surface().textCalls) {
            if (c.text().equals("S") && Math.abs(c.font().sizePx() - fullPx) < 0.1) {
                sawFullS = true;
            }
            if (c.text().equals("MALL") && Math.abs(c.font().sizePx() - smallPx) < 0.1) {
                sawSmallMALL = true;
            }
            assertFalse("small-caps text must not paint raw lowercase",
                c.text().equals("Small") || c.text().contains("mall"));
        }
        assertTrue("leading capital must keep the full size", sawFullS);
        assertTrue("lowercase letters must paint as uppercase at 80% size", sawSmallMALL);
    }
}
