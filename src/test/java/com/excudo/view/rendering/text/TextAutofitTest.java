package com.excudo.view.rendering.text;

import com.excudo.core.model.TextBody;
import org.junit.Test;

import java.util.List;

import static com.excudo.view.rendering.text.TextPaintTestSupport.*;
import static org.junit.Assert.*;

/**
 * normAutofit stores the fontScale / lnSpcReduction PowerPoint computed
 * when it shrank the text; honoring them verbatim is exact — no fitting
 * algorithm. Before A3, fontScale only kicked in when our own overflow
 * heuristic fired (so a fitting box ignored it entirely) and
 * lnSpcReduction was dropped by the extractor.
 *
 * <p>spAutoFit needs no shrink: the stored shape geometry already
 * reflects the grown box (see TextOverflowNotClippedTest for the
 * companion overflow behaviour).
 */
public class TextAutofitTest {

    private static final String LONG =
        "Shrink-to-fit text keeps reducing the font scale until the whole "
        + "paragraph fits inside its box. Shrink-to-fit text keeps reducing "
        + "the font scale until the whole paragraph fits inside its box.";

    @Test
    public void storedFontScaleAppliesDeterministically() {
        // On-ladder fontScale 40000 on a 20pt run = 8pt glyphs =
        // 10.667px, regardless of whether the text would overflow.
        // (PowerPoint's PDF export of the text-autofit corpus deck draws
        // the fontScale="40000" box at exactly 8pt.)
        TextBody body = extract(
            "<a:bodyPr lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\">"
            + "<a:normAutofit fontScale=\"40000\"/></a:bodyPr>"
            + "<a:p>" + run("Scale me", 2000) + "</a:p>");

        Painted p = paint(body, 500, 300);

        assertFalse(p.surface().textCalls.isEmpty());
        for (var call : p.surface().textCalls) {
            assertEquals("stored fontScale must scale the painted font size",
                20.0 * 0.40 * 96 / 72, call.font().sizePx(), 0.05);
        }
    }

    @Test
    public void offLadderFontScaleSnapsToPowerPointLadder() {
        // PowerPoint's own autofit engine only produces scales from its
        // ladder (100, 92.5, 85, 77.5, 70, 65, 60, ... 25 %); it snaps
        // foreign off-ladder values when laying out. Ground truth: the
        // corpus deck stores fontScale="62500" on a 20pt run and
        // PowerPoint's PDF export draws it at 12.96pt = 65%, not 12.5pt.
        TextBody body = extract(
            "<a:bodyPr lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\">"
            + "<a:normAutofit fontScale=\"62500\"/></a:bodyPr>"
            + "<a:p>" + run("Scale me", 2000) + "</a:p>");

        Painted p = paint(body, 500, 300);

        assertFalse(p.surface().textCalls.isEmpty());
        for (var call : p.surface().textCalls) {
            assertEquals("off-ladder fontScale must snap to the 65% ladder step",
                20.0 * 0.65 * 96 / 72, call.font().sizePx(), 0.05);
        }
    }

    @Test
    public void lnSpcReductionShrinksLinePitch() {
        String paragraphs = "<a:p>" + run(LONG, 2000) + "</a:p>";

        TextBody plain = extract(
            "<a:bodyPr lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\">"
            + "<a:normAutofit fontScale=\"100000\"/></a:bodyPr>" + paragraphs);
        TextBody reduced = extract(
            "<a:bodyPr lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\">"
            + "<a:normAutofit fontScale=\"100000\" lnSpcReduction=\"20000\"/></a:bodyPr>"
            + paragraphs);

        double pitchPlain = firstPitch(paint(plain, 445, 600));
        double pitchReduced = firstPitch(paint(reduced, 445, 600));

        assertEquals("lnSpcReduction=20000 must shrink the line pitch to 80%",
            0.8, pitchReduced / pitchPlain, 0.02);
    }

    private static double firstPitch(Painted p) {
        List<Double> baselines = p.surface().distinctBaselines();
        assertTrue("fixture must wrap to at least 2 lines", baselines.size() >= 2);
        return baselines.get(1) - baselines.get(0);
    }
}
