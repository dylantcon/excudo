package com.excudo.core.synthesis;

import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.mutating.slide.RunSlideScriptCommand;
import com.excudo.core.model.BlipRef;
import com.excudo.core.model.MediaElement;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.synthesis.spec.CommandSpec;
import com.excudo.core.synthesis.spec.CommandSpecJson;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * End-to-end strict round-trip for the picture channel: load a deck
 * containing a picture, synthesize its slide, JSON-serialize, apply to
 * a freshly created blank target slide in the same deck, and verify
 * the target ends with a picture that points at the same media bytes
 * as the source.
 *
 * <p>Together with {@code SynthesisStressTest.pictureDuplication_*}
 * (snapshot/synth) and {@code SlideSpecApplyFlowTest.picture_*}
 * (JSON), this covers the full pipeline.
 */
public class PictureRoundTripTest {

    private static final File PICTURE_FIXTURE =
        new File("test-pptx-samples/generalist_test_file.pptx");

    @Test
    public void picture_endToEndRoundTrip_targetCarriesSameMediaBytes() throws Exception {
        assumeTrue("Picture fixture not present", PICTURE_FIXTURE.exists());
        PPTXOrchestratorImpl orch = loadOrchestrator(PICTURE_FIXTURE);

        // Find a slide with a picture.
        int sourceSlide = firstSlideWithPicture(orch);
        assumeTrue("Fixture has no picture-bearing slide", sourceSlide > 0);

        SlideShape sourcePic = firstPictureShape(orch, sourceSlide);
        assertNotNull(sourcePic);
        int sourcePicSpid = sourcePic.getSpid();

        // Create a target slide using the SAME layout as the source so
        // unrelated placeholder-related specs don't cross-layout-fail.
        // For the generalist deck, slideLayout1 (Title Slide) is the
        // layout the picture lives on.
        String sourceLayoutId = currentLayoutFor(orch, sourceSlide);
        assertNotNull("Source slide must have a resolvable layout", sourceLayoutId);
        int targetSlide = orch.getContext().get().getDocument().getSlideNumbers().size() + 1;
        var created = orch.createSlide(targetSlide, "PictureTarget", sourceLayoutId);
        assertTrue("createSlide must succeed: " + created.getMessage(), created.isSuccess());

        int targetPicsBefore = countPictures(orch, targetSlide);

        // GUI-equivalent apply flow.
        SlideStateBuilder builder = new SlideStateBuilder(orch);
        SlideStateDiff diff = SlideStateDiffer.diff(
            builder.baseline(sourceSlide), builder.current(sourceSlide));
        List<CommandSpec> specs = ScriptSynthesizer.synthesize(diff, sourceSlide)
            .script().topologicalOrder();
        String json = CommandSpecJson.toJsonArray(specs);
        RunSlideScriptCommand cmd = new RunSlideScriptCommand(
            targetSlide, json, orch, null);
        new CommandInvoker().executeCommand(cmd);
        assertTrue("RunSlideScriptCommand must succeed without aborting on picture",
            cmd.isExecuted());

        // Target gained exactly one picture.
        int targetPicsAfter = countPictures(orch, targetSlide);
        assertEquals("Target slide must gain exactly one picture after apply",
            targetPicsBefore + 1, targetPicsAfter);

        // The new picture's media part resolves to the SAME bytes as
        // the source picture's. This is the strict assertion: SAME
        // OPC part name (in-deck media sharing), not a fresh copy.
        SlideShape targetPic = firstPictureShape(orch, targetSlide);
        assertNotNull(targetPic);

        PPTXDocument doc = orch.getContext().get().getDocument();
        BlipRef sourceBlip = blipRefFor(doc, sourceSlide, sourcePic.getSpid(), orch);
        BlipRef targetBlip = blipRefFor(doc, targetSlide, targetPic.getSpid(), orch);
        assertNotNull("Source picture must have a resolvable BlipRef", sourceBlip);
        assertNotNull("Target picture must have a resolvable BlipRef", targetBlip);
        assertEquals("Source and target media-part names must match (in-deck "
            + "media is shared via separate rIds pointing at the same part)",
            sourceBlip.mediaPartName(), targetBlip.mediaPartName());

        MediaElement sourceMedia = doc.getMediaPart(sourceBlip.mediaPartName());
        MediaElement targetMedia = doc.getMediaPart(targetBlip.mediaPartName());
        assertNotNull("Source media part must exist", sourceMedia);
        assertSame("Target reuses the source media part (in-process sharing)",
            sourceMedia, targetMedia);
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private static PPTXOrchestratorImpl loadOrchestrator(File f) throws Exception {
        PPTXDocument doc = PPTXDocument.loadFromZip(f);
        PPTXOrchestratorImpl orch = new PPTXOrchestratorImpl();
        orch.initialize(doc);
        return orch;
    }

    private static int firstSlideWithPicture(PPTXOrchestratorImpl orch) {
        PPTXDocument doc = orch.getContext().get().getDocument();
        for (Integer n : doc.getSlideNumbers()) {
            if (countPictures(orch, n) > 0) return n;
        }
        return -1;
    }

    private static int countPictures(PPTXOrchestratorImpl orch, int slideNumber) {
        ShapeRegistry reg = parsedReg(orch, slideNumber);
        if (reg == null) return 0;
        return (int) reg.getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.PICTURE).count();
    }

    private static SlideShape firstPictureShape(PPTXOrchestratorImpl orch, int slideNumber) {
        ShapeRegistry reg = parsedReg(orch, slideNumber);
        if (reg == null) return null;
        return reg.getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.PICTURE)
            .findFirst().orElse(null);
    }

    private static ShapeRegistry parsedReg(PPTXOrchestratorImpl orch, int slideNumber) {
        ParsedSlideData parsed = orch.getContext().get().getDocument().getParsedSlideData(
            slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        return parsed == null ? null : parsed.getShapeRegistry();
    }

    private static String currentLayoutFor(PPTXOrchestratorImpl orch, int slideNumber) {
        ParsedSlideData parsed = orch.getContext().get().getDocument().getParsedSlideData(
            slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        return parsed == null ? null : parsed.getLayoutId();
    }

    /** Reuse {@link SlideStateBuilder}'s BlipRef extraction by snapshotting
     *  the slide and pulling the picture's snapshot out. Keeps the test
     *  honest -- if a refactor changes BlipRef semantics, this test
     *  validates the new contract too. */
    private static BlipRef blipRefFor(PPTXDocument doc, int slideNumber, int spid,
            PPTXOrchestratorImpl orch) {
        SlideStateBuilder builder = new SlideStateBuilder(orch);
        SlideState state = builder.current(slideNumber);
        if (state == null) return null;
        Optional<ShapeSnapshot> snap = state.shapes().values().stream()
            .filter(s -> s.spid() == spid).findFirst();
        return snap.map(ShapeSnapshot::blipRef).orElse(null);
    }
}
