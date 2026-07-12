package com.excudo.view.rendering.text;

import com.excudo.core.model.TextBody;
import com.excudo.test.utils.RecordingRenderSurface;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.excudo.view.rendering.text.TextPaintTestSupport.*;
import static org.junit.Assert.*;

/**
 * Justified paragraphs (algn="just") must distribute the residual line
 * width across inter-word gaps so every wrapped line ends flush at the
 * wrap edge; the final line stays left-aligned. Before A3, "just" was
 * treated as left alignment.
 */
public class TextJustifyTest {

    private static final String LOREM =
        "The quick brown fox jumps over the lazy dog while seventy-seven "
        + "wizards briskly mix quartz vials of juniper extract.";

    @Test
    public void justifiedLinesEndFlushAtWrapWidth() {
        double boxWidth = 445;
        TextBody body = extract(ZERO_INSETS
            + "<a:p><a:pPr algn=\"just\"/>" + run(LOREM, 1800) + "</a:p>");

        Painted p = paint(body, boxWidth, 400);
        RecordingRenderSurface s = p.surface();

        // Group painted calls by line (baseline Y).
        Map<Double, List<RecordingRenderSurface.TextCall>> byLine = new LinkedHashMap<>();
        for (RecordingRenderSurface.TextCall c : s.textCalls) {
            byLine.computeIfAbsent(Math.round(c.baselineY() * 8) / 8.0,
                k -> new ArrayList<>()).add(c);
        }
        List<Double> baselines = new ArrayList<>(byLine.keySet());
        baselines.sort(Double::compareTo);
        assertTrue("fixture must wrap to at least 3 lines", baselines.size() >= 3);

        for (int i = 0; i < baselines.size() - 1; i++) {
            double rightEdge = 0;
            for (RecordingRenderSurface.TextCall c : byLine.get(baselines.get(i))) {
                String ink = c.text().stripTrailing();
                if (ink.isEmpty()) continue;
                rightEdge = Math.max(rightEdge, c.x() + s.advanceOf(c.font(), ink));
            }
            assertEquals("non-final justified line " + (i + 1)
                    + " must end flush at the wrap edge",
                boxWidth, rightEdge, 6.0);
        }

        // Last line stays left-aligned: it must start at the origin and
        // end clearly short of the wrap edge for this fixture.
        double lastRight = 0;
        double lastLeft = Double.MAX_VALUE;
        for (RecordingRenderSurface.TextCall c : byLine.get(baselines.get(baselines.size() - 1))) {
            String ink = c.text().stripTrailing();
            if (ink.isEmpty()) continue;
            lastLeft = Math.min(lastLeft, c.x());
            lastRight = Math.max(lastRight, c.x() + s.advanceOf(c.font(), ink));
        }
        assertEquals("last line starts at the text origin", 0.0, lastLeft, 1.0);
        assertTrue("last line must NOT be stretched to the wrap edge (right="
            + lastRight + ")", lastRight < boxWidth - 10);
    }
}
