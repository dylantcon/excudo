package com.excudo.view.rendering.text;

import com.excudo.core.model.TextBody;
import org.junit.Test;

import static com.excudo.view.rendering.text.TextPaintTestSupport.*;
import static org.junit.Assert.*;

/**
 * Pins PowerPoint's overflow behaviour: text that exceeds the shape
 * bounds is painted in full and is NOT clipped to the shape.
 *
 * <p>Ground truth: PowerPoint's PDF export of the text-align-wrap deck
 * (slide 1, bottom-right 24pt overflow cell) and of the text-autofit
 * deck (no-autofit control cell) both show the text continuing past the
 * shape's bottom outline to the edge of the page. Clipping to the shape
 * rect — however tempting for tidiness — would diverge from ground
 * truth and lower parity, so this test forbids it.
 */
public class TextOverflowNotClippedTest {

    private static final String LOREM =
        "The quick brown fox jumps over the lazy dog while seventy-seven "
        + "wizards briskly mix quartz vials of juniper extract. "
        + "The quick brown fox jumps over the lazy dog while seventy-seven "
        + "wizards briskly mix quartz vials of juniper extract.";

    @Test
    public void overflowingTextPaintsPastTheShapeBottom() {
        // 24pt text in a 445x116 px box — the corpus overflow fixture.
        TextBody body = extract(ZERO_INSETS + "<a:p>" + run(LOREM, 2400) + "</a:p>");

        double boxHeight = 116;
        Painted p = paint(body, 445, boxHeight);

        double lastBaseline = p.surface().distinctBaselines().stream()
            .mapToDouble(Double::doubleValue).max().orElse(0);
        assertTrue("overflow text must keep painting past the shape bottom (last baseline "
                + lastBaseline + " vs box height " + boxHeight + ")",
            lastBaseline > boxHeight + 50);
        assertEquals("text painting must not clip to the shape rect",
            0, p.surface().clipRectCalls);
    }
}
