package com.excudo.core.synthesis;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.synthesis.spec.CommandSpec;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * NEUTRALITY FIREWALL for {@code p:graphicFrame} content (tables, charts).
 *
 * <p>The A6 table work extended {@code SlideXMLParser} to see
 * {@code p:graphicFrame} shapes that were previously invisible to the
 * whole synthesis stack. This suite pins, against real decks, that the
 * parser extension does not silently alter what {@link ScriptSynthesizer}
 * emits:
 *
 * <ul>
 *   <li>The spec sequence synthesized for NON-table content on slides
 *       that also carry tables must not change.</li>
 *   <li>graphicFrame content itself must be handled by a documented,
 *       deliberate policy — never an exception, never a garbage
 *       {@code AddShapeSpec} that the runner cannot execute.</li>
 * </ul>
 *
 * <p><b>Documented policy (A6 parser)</b>: {@code a:tbl} graphicFrames
 * parse into TABLE-typed shapes for the renderer, and the synthesizer
 * deliberately skips them with exactly one visible warning per table —
 * there is no AddTableSpec in the v1 spec vocabulary, and an
 * {@code AddShapeSpec} would fail injection because TABLE has no OOXML
 * preset geometry. Non-table graphicFrames (charts, SmartArt, OLE)
 * remain unparsed pending their own phases and still synthesize to
 * nothing. This policy assert was flipped from expect-zero-warnings to
 * expect-one-per-table in the SAME commit that widened the parser
 * XPath — the deliberate, reviewed decision this firewall exists to
 * force. When a table spec vocabulary lands (B-phase), these pins flip
 * again to the new specs.
 *
 * <p>Unlike the renderer suites this test is deliberately green-first:
 * it pins the status quo so parser work cannot regress it unnoticed.
 * Fixtures HARD-FAIL when missing — a skipped firewall is no firewall.
 */
public class GraphicFrameSynthesisFirewallTest {

    private static final File TABLES_BASIC =
        new File("parity-corpus/tables-basic/deck.pptx");
    private static final File TABLES_MERGES =
        new File("parity-corpus/tables-merges/deck.pptx");
    private static final File STRESS_DECK =
        new File("test-pptx-samples/textel-crud/native/stress_test_complex_text.pptx");

    // ===================================================================
    // Table-only decks: the parity corpus decks contain exactly one
    // graphicFrame table and nothing else on slide 1.
    // ===================================================================

    @Test
    public void tablesBasicDeck_tableOnlySlide_synthesizesNoSpecs() throws Exception {
        ScriptSynthesizer.Result r = synthSlide(TABLES_BASIC, 1);
        assertGraphicFramePolicy(r, 1);
        assertEquals("A table-only slide must synthesize ZERO specs (the table "
            + "follows the graphicFrame policy, nothing else exists to replay): "
            + describe(r), 0, r.script().topologicalOrder().size());
    }

    @Test
    public void tablesMergesDeck_tableOnlySlide_synthesizesNoSpecs() throws Exception {
        ScriptSynthesizer.Result r = synthSlide(TABLES_MERGES, 1);
        assertGraphicFramePolicy(r, 1);
        assertEquals("A table-only slide must synthesize ZERO specs: "
            + describe(r), 0, r.script().topologicalOrder().size());
    }

    // ===================================================================
    // Mixed-content deck: slides 8 and 9 of the stress deck carry a
    // title placeholder, text boxes, content placeholders, hexagons AND
    // one graphicFrame table each. The non-table spec sequence is the
    // tripwire: graphicFrame parsing must not add, drop or reorder any
    // of these.
    // ===================================================================

    @Test
    public void stressDeckSlide8_nonTableSpecSequenceIsStable() throws Exception {
        ScriptSynthesizer.Result r = synthSlide(STRESS_DECK, 8);
        assertGraphicFramePolicy(r, 1);
        assertEquals("Slide 8 non-table content must synthesize the exact "
            + "pre-A6 spec multiset: " + describe(r),
            nonTableSpecMultiset(STRESS_DECK, 8),
            classNames(r.script().topologicalOrder()));
    }

    @Test
    public void stressDeckSlide9_nonTableSpecSequenceIsStable() throws Exception {
        ScriptSynthesizer.Result r = synthSlide(STRESS_DECK, 9);
        assertGraphicFramePolicy(r, 1);
        assertEquals("Slide 9 non-table content must synthesize the exact "
            + "pre-A6 spec multiset: " + describe(r),
            nonTableSpecMultiset(STRESS_DECK, 9),
            classNames(r.script().topologicalOrder()));
    }

    /**
     * The literal spec-class multiset observed on the pre-A6 synthesizer
     * for the stress deck's table slides. Hardcoded (not recomputed) so
     * any parser-side drift shows up as a diff against these lists.
     * Both slides synthesize the same 18-spec multiset: six AddShapeSpec
     * (one PLACEHOLDER-typed content placeholder the projector doesn't
     * match, two snip-corner text boxes, two hexagons, one plain text
     * box), title Move/Rename/SetText, six more SetTextSpec, the slide
     * transition, and two fade animations on the text box.
     */
    private static List<String> nonTableSpecMultiset(File deck, int slide) {
        return List.of(
            "AddAnimationSpec", "AddAnimationSpec",
            "AddShapeSpec", "AddShapeSpec", "AddShapeSpec",
            "AddShapeSpec", "AddShapeSpec", "AddShapeSpec",
            "MoveSpec", "RenameShapeSpec",
            "SetTextSpec", "SetTextSpec", "SetTextSpec", "SetTextSpec",
            "SetTextSpec", "SetTextSpec", "SetTextSpec",
            "SetTransitionSpec");
    }

    // ===================================================================
    // Policy assertions
    // ===================================================================

    /**
     * The documented graphicFrame policy, asserted on every fixture.
     * Pre-A6 parser: graphicFrames are invisible — no spec of any kind
     * may reference them and no warnings mention them ({@code
     * tablesPresent} documents how many tables the fixture slide carries
     * so the post-A6 flip to one skip-warning per table is a one-line,
     * reviewed change in the parser commit).
     */
    private static void assertGraphicFramePolicy(ScriptSynthesizer.Result r,
            int tablesPresent) {
        assertTrue("fixture slide must actually carry a table", tablesPresent > 0);

        // No spec of any kind may target a table. AddShapeSpec(TABLE)
        // is the specific garbage the policy forbids (unrunnable: no
        // OOXML preset geometry).
        boolean strayTableAdd = r.script().topologicalOrder().stream()
            .filter(s -> s instanceof CommandSpec.AddShapeSpec)
            .map(s -> (CommandSpec.AddShapeSpec) s)
            .anyMatch(s -> s.shapeType() == com.excudo.core.model.SlideShape.ShapeType.TABLE);
        assertFalse("TABLE must never flow through AddShapeSpec", strayTableAdd);

        // Exactly one visible skip warning per table, and nothing else:
        // every fixture slide otherwise synthesizes warning-free, so any
        // extra warning is drift.
        List<String> tableWarnings = r.warnings().stream()
            .filter(w -> w.contains("graphicFrame table")).toList();
        assertEquals("Each parsed table must surface exactly one visible skip "
            + "warning (a silent drop is forbidden). warnings=" + r.warnings(),
            tablesPresent, tableWarnings.size());
        assertEquals("No warnings besides the table skips may appear. warnings="
            + r.warnings(), tablesPresent, r.warnings().size());
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private static ScriptSynthesizer.Result synthSlide(File deck, int slide) throws Exception {
        assertTrue("Fixture missing (hard failure, never skip): " + deck.getAbsolutePath(),
            deck.isFile());
        PPTXDocument doc = PPTXDocument.loadFromZip(deck);
        PPTXOrchestratorImpl orch = new PPTXOrchestratorImpl();
        orch.initialize(doc);

        SlideStateBuilder b = new SlideStateBuilder(orch);
        SlideState baseline = b.baseline(slide);
        SlideState current = b.current(slide);
        assertNotNull("baseline state must resolve for " + deck.getName()
            + " slide " + slide, baseline);
        assertNotNull("current state must resolve for " + deck.getName()
            + " slide " + slide, current);

        SlideStateDiff diff = SlideStateDiffer.diff(baseline, current);
        return ScriptSynthesizer.synthesize(diff, slide);
    }

    private static List<String> classNames(List<CommandSpec> specs) {
        return specs.stream().map(s -> s.getClass().getSimpleName()).sorted().toList();
    }

    private static String describe(ScriptSynthesizer.Result r) {
        return r.script().topologicalOrder().stream()
            .map(Object::toString).toList() + " warnings=" + r.warnings();
    }
}
