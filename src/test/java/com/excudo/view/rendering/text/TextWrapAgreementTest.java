package com.excudo.view.rendering.text;

import com.excudo.core.metrics.MeasuredText;
import com.excudo.core.model.TextBody;
import org.junit.Test;

import java.util.List;

import static com.excudo.view.rendering.text.TextPaintTestSupport.*;
import static org.junit.Assert.*;

/**
 * Pins the single-wrap-authority contract: the painter must paint
 * EXACTLY the lines TextMeasurer measured. Before A3 the painter
 * re-wrapped with the render surface's own width engine at a
 * differently-computed font size, so painted line breaks disagreed
 * with measured ones — which broke vertical centering, wrap position
 * and every downstream alignment computation.
 */
public class TextWrapAgreementTest {

    private static final String LOREM =
        "The quick brown fox jumps over the lazy dog while seventy-seven "
        + "wizards briskly mix quartz vials of juniper extract.";

    @Test
    public void paintedLineBreaksMatchMeasuredLines() {
        // 445 px box — same geometry as the text-align-wrap corpus cells.
        TextBody body = extract(ZERO_INSETS
            + "<a:p>" + run(LOREM, 1400) + "</a:p>"
            + "<a:p>" + run("Antidisestablishmentarianism supercalifragilisticexpialidocious",
                            1600) + "</a:p>");

        Painted p = paint(body, 445, 400);

        int measuredLines = p.measured().getParagraphs().stream()
            .mapToInt(MeasuredText.ParagraphMeasurement::getLineCount).sum();
        List<Double> painted = p.surface().distinctBaselines();

        assertTrue("fixture must actually wrap (got " + measuredLines + " lines)",
            measuredLines >= 4);
        assertEquals("painter must paint exactly the measured lines — a mismatch means "
                + "a second wrap engine is deciding line breaks",
            measuredLines, painted.size());
    }

    @Test
    public void longWordNearBoundaryBreaksBetweenWordsLikePowerPoint() {
        // PowerPoint's export of this exact fixture (text-align-wrap
        // deck, slide 2) puts each long word on its own line.
        TextBody body = extract(ZERO_INSETS
            + "<a:p>" + run("Antidisestablishmentarianism supercalifragilisticexpialidocious",
                            1600) + "</a:p>");

        Painted p = paint(body, 445, 200);

        assertEquals("16pt Calibri in a 445px box must break between the two long words",
            2, p.measured().getParagraphs().get(0).getLineCount());
        assertEquals(2, p.surface().distinctBaselines().size());
    }

    @Test
    public void fontSizeConvertsPointsToPixels() {
        // 14 pt at 96 DPI is 18.67 px. The pre-A3 painter passed the
        // point value straight through as pixels, rendering all text at
        // 75% of its true size — the single largest text-parity error.
        TextBody body = extract(ZERO_INSETS + "<a:p>" + run("Size check", 1400) + "</a:p>");

        Painted p = paint(body, 445, 200);

        assertFalse(p.surface().textCalls.isEmpty());
        double sizePx = p.surface().textCalls.get(0).font().sizePx();
        assertEquals("14pt must paint at 14 * 96/72 px", 14.0 * 96 / 72, sizePx, 0.01);
    }
}
