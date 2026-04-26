package com.excudo.core.llm;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.parsing.CommandParameters;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Pins the batch-shape of {@code get_slide_shapes}: a single tool call
 * can resolve multiple slides via {@code slideNumbers} (array or "all"),
 * eliminating the round-trip explosion that
 * {@code parsing-decks.md} flagged for 79-slide decks.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Single-slide back-compat ({@code slideNumber} integer).</li>
 *   <li>Explicit array ({@code slideNumbers: [1,2,3]}).</li>
 *   <li>{@code "all"} sentinel expands to every slide in the deck.</li>
 *   <li>Malformed array entries fail loudly rather than silently dropping.</li>
 * </ul>
 */
public class GetSlideShapesBatchTest {

    private PPTXOrchestratorImpl orchestrator;
    private ToolDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "S1", "slideLayout2");
        orchestrator.createSlide(2, "S2", "slideLayout2");
        orchestrator.createSlide(3, "S3", "slideLayout2");
        CommandFactory cf = new CommandFactory(orchestrator);
        dispatcher = new ToolDispatcher(orchestrator, cf, new CommandInvoker());

        addRect(1, "Marker_S1");
        addRect(2, "Marker_S2");
        addRect(3, "Marker_S3");
    }

    @Test
    public void singleSlideNumberShowsOneSection() {
        String out = dispatcher.dispatch("get_slide_shapes", "{\"slideNumber\":2}");
        assertTrue(out, out.contains("Slide 2:"));
        assertFalse("Single-slide call must not include other slides: " + out,
            out.contains("Slide 1:") || out.contains("Slide 3:"));
        assertTrue("Slide 2 marker must appear: " + out, out.contains("Marker_S2"));
    }

    @Test
    public void slideNumbersArrayShowsRequestedSlidesInOrder() {
        String out = dispatcher.dispatch("get_slide_shapes", "{\"slideNumbers\":[3,1]}");
        int s3 = out.indexOf("Slide 3:");
        int s1 = out.indexOf("Slide 1:");
        assertTrue("Slide 3 section must appear: " + out, s3 >= 0);
        assertTrue("Slide 1 section must appear: " + out, s1 >= 0);
        assertTrue("Caller order honored: 3 before 1: " + out, s3 < s1);
        assertFalse("Unrequested slide must not leak in: " + out, out.contains("Slide 2:"));
    }

    @Test
    public void slideNumbersAllExpandsToEverySlide() {
        String out = dispatcher.dispatch("get_slide_shapes", "{\"slideNumbers\":\"all\"}");
        assertTrue("'all' covers slide 1: " + out, out.contains("Slide 1:"));
        assertTrue("'all' covers slide 2: " + out, out.contains("Slide 2:"));
        assertTrue("'all' covers slide 3: " + out, out.contains("Slide 3:"));
        assertTrue("All markers present: " + out,
            out.contains("Marker_S1") && out.contains("Marker_S2") && out.contains("Marker_S3"));
    }

    @Test
    public void slideNumbersWithNonIntegerEntryReturnsLoudError() {
        String out = dispatcher.dispatch("get_slide_shapes", "{\"slideNumbers\":[1,\"bad\",3]}");
        assertTrue("Malformed array element must be flagged: " + out,
            out.startsWith("Error:") && out.contains("integers"));
    }

    @Test
    public void slideNumbersRangeStringExpands() {
        String out = dispatcher.dispatch("get_slide_shapes", "{\"slideNumbers\":\"1-3\"}");
        assertTrue("Range covers slide 1: " + out, out.contains("Slide 1:"));
        assertTrue("Range covers slide 2: " + out, out.contains("Slide 2:"));
        assertTrue("Range covers slide 3: " + out, out.contains("Slide 3:"));
    }

    @Test
    public void slideNumbersCommaSeparatedRangesAndSingles() {
        String out = dispatcher.dispatch("get_slide_shapes", "{\"slideNumbers\":\"1,3\"}");
        assertTrue("Slide 1 included: " + out, out.contains("Slide 1:"));
        assertTrue("Slide 3 included: " + out, out.contains("Slide 3:"));
        assertFalse("Slide 2 excluded by comma form: " + out, out.contains("Slide 2:"));
    }

    @Test
    public void rangeWithReversedEndpointsFailsLoud() {
        String out = dispatcher.dispatch("get_slide_shapes", "{\"slideNumbers\":\"5-1\"}");
        assertTrue("Reversed range must be flagged: " + out,
            out.startsWith("Error:") && out.contains("end must be >= start"));
    }

    @Test
    public void noSlideArgumentReturnsLoudError() {
        String out = dispatcher.dispatch("get_slide_shapes", "{}");
        assertTrue("Missing slide arg must be flagged: " + out,
            out.startsWith("Error:") && out.contains("slideNumber"));
    }

    @Test
    public void pictureBearingSlideEmitsRenderHint() throws Exception {
        // Inject a PICTURE-typed SlideShape directly so we don't need a real
        // image asset in the test corpus -- slideHasImageContent only inspects
        // shape type + blipFill descendants on the XML, both visible to the
        // dispatcher via the registry.
        var registry = orchestrator.getSlideData(2).getData().orElseThrow().getShapeRegistry();
        registry.addShape(new com.excudo.core.model.SlideShape(
            999, "TestPic", com.excudo.core.model.SlideShape.ShapeType.PICTURE, null,
            new com.excudo.core.model.ShapeGeometry(0, 0, 1_000_000, 1_000_000), null));

        String out = dispatcher.dispatch("get_slide_shapes", "{\"slideNumber\":2}");
        assertTrue("Image-bearing slide must surface a render_slide hint: " + out,
            out.contains("contains image content") && out.contains("render_slide"));

        // Sanity: a shape-only slide (no PICTURE) must NOT carry the hint --
        // catches a regression where the note fires on every slide.
        String plain = dispatcher.dispatch("get_slide_shapes", "{\"slideNumber\":1}");
        assertFalse("Plain rectangle slide should not advertise an image: " + plain,
            plain.contains("contains image content"));
    }

    private void addRect(int slide, String name) {
        com.excudo.core.commands.Command cmd =
            com.excudo.core.commands.CommandClassRegistry.createFromParameters(
                new CommandParameters("add-shape", Map.of(
                    "slide", String.valueOf(slide),
                    "shape-type", "RECTANGLE",
                    "text", name,
                    "x", "1000000", "y", "1000000",
                    "width", "2000000", "height", "1000000")),
                new com.excudo.core.commands.CommandContext(orchestrator, null));
        cmd.execute();
    }
}
