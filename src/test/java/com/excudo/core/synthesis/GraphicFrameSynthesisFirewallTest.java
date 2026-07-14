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
 * <p>The A6 table work extends {@code SlideXMLParser} to see
 * {@code p:graphicFrame} shapes that are currently invisible to the
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
 * <p><b>Documented policy today (pre-A6 parser)</b>: the shape-extraction
 * XPath selects only {@code p:sp | p:pic | p:grpSp | p:cxnSp}, so a
 * {@code p:graphicFrame} never reaches the ShapeRegistry, the snapshot
 * builder, or the synthesizer — tables synthesize to nothing, with no
 * warning. When the A6 parser makes tables visible, the policy becomes
 * skip-with-visible-warning (there is no AddTableSpec in the v1 spec
 * vocabulary, and an {@code AddShapeSpec} would fail injection because
 * TABLE has no OOXML preset geometry). That flip is a deliberate,
 * reviewed change: {@link #assertGraphicFramePolicy} is updated IN THE
 * SAME COMMIT as the parser change, with the expected warning count
 * moving from 0 to 1 per table.
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
        assertEquals("Pre-A6 policy: graphicFrames are invisible to synthesis "
            + "and every fixture slide otherwise synthesizes warning-free, so "
            + "the warnings channel must be EMPTY. warnings=" + r.warnings(),
            List.of(), r.warnings());
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
