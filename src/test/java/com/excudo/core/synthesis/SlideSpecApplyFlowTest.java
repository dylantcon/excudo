package com.excudo.core.synthesis;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.mutating.slide.RunSlideScriptCommand;
import com.excudo.core.llm.ToolDispatcher;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.synthesis.spec.CommandSpec;
import com.excudo.core.synthesis.spec.CommandSpecJson;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Tests the synthesis flow as the GUI actually invokes it.
 *
 * <p>The GUI's {@code SlideSpecController.applyToNewSlide} /
 * {@code applyToExistingSlide} buttons do this end-to-end:
 *
 * <pre>
 *   SlideStateBuilder.current(source)
 *     -> SlideStateDiffer.diff(baseline, current)
 *     -> ScriptSynthesizer.synthesize(diff, source)
 *     -> CommandSpecJson.toJsonArray(specs)                  // ZIP through JSON
 *     -> RunSlideScriptCommand(target, json, orchestrator, null).execute()
 *         -> CommandSpecJson.fromJsonArray(json)
 *         -> RetargetToSlide.retarget(spec, target)
 *         -> SpecRewriter.rewrite(spec, spidMap)
 *         -> SpecToCommandMapper.toCommand(spec)
 *         -> cmd.execute()
 * </pre>
 *
 * <p>The GUI button itself is a thin wrapper around this chain. Bugs the
 * user sees ("I can't duplicate this image in the GUI") live in the chain,
 * not in JavaFX. So this test bypasses the bootstrap and asserts on the
 * actual mutation outcome on the target slide -- which is what the user
 * sees in the canvas after pressing the button.
 *
 * <p>Each scenario asserts <em>what the user expects after pressing apply</em>:
 * shape count, shape types, media survival, marker survival. Failures are
 * a punch list of GUI-visible regressions.
 */
public class SlideSpecApplyFlowTest {

    private static final File PICTURE_FIXTURE =
        new File("test-pptx-samples/generalist_test_file.pptx");

    // ===================================================================
    // Smoke: a single rectangle round-trips through JSON+RunScript
    // ===================================================================

    @Test
    public void rectangle_applyToNewSlide_reproducesShapeOnTarget() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Source", "slideLayout7");
        orch.createSlide(2, "Target", "slideLayout7");
        orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 3_000_000, 2_000_000),
            "", "R1", ShapeStyle.defaultStyle());

        applyViaGuiFlow(orch, 1, 2);

        // Target now has the same shape type set as source.
        assertEquals("Target should have the rectangle after applying",
            1, countShapesOfType(orch, 2, SlideShape.ShapeType.RECTANGLE));
    }

    // ===================================================================
    // User's named failure mode: picture duplication via the GUI
    // ===================================================================

    /**
     * The user said "I can't duplicate images in the GUI." Before fix:
     * the GUI's serialized spec list included AddShapeSpec(PICTURE,...);
     * RunSlideScriptCommand handed it to AddShapeCommand which threw at
     * SPID allocation, aborting the whole apply. After fix: the
     * synthesizer skips PICTURE with a warning AND AddShapeCommand
     * fails clean at PICTURE rather than via SPID-allocation noise. The
     * net result is the GUI's serialized script never contains a PICTURE
     * AddShape, so the apply doesn't abort for picture-bearing decks.
     *
     * <p>Scoped to the JSON layer (not full apply-to-new-slide) because
     * loaded-fixture slides use different layouts than freshly created
     * blank slides; cross-layout SPID retarget is a separate concern
     * tracked outside this test.
     */
    @Test
    public void picture_synthAndJsonRoundTrip_dropsAddShapePictureCleanly() throws Exception {
        assumeTrue("Picture fixture not present: " + PICTURE_FIXTURE, PICTURE_FIXTURE.exists());
        PPTXOrchestratorImpl orch = loadOrchestratorFromFile(PICTURE_FIXTURE);

        int sourceSlide = firstSlideWithPicture(orch);
        assumeTrue("Fixture has no slide with a picture", sourceSlide > 0);

        SlideStateBuilder builder = new SlideStateBuilder(orch);
        SlideStateDiff diff = SlideStateDiffer.diff(
            builder.baseline(sourceSlide), builder.current(sourceSlide));
        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, sourceSlide);

        // No AddShapeSpec(PICTURE,...) in the synthesized output; if it
        // were there, the GUI's RunSlideScriptCommand would throw at
        // AddShapeCommand's PICTURE check (the user's GUI failure mode).
        boolean anyPic = result.script().topologicalOrder().stream()
            .filter(s -> s instanceof CommandSpec.AddShapeSpec)
            .map(s -> (CommandSpec.AddShapeSpec) s)
            .anyMatch(s -> s.shapeType() == SlideShape.ShapeType.PICTURE);
        assertFalse("Synthesizer must skip PICTURE adds so GUI apply doesn't "
            + "abort. specs=" + result.script().topologicalOrder(), anyPic);

        // Warning naming the skipped picture must surface so the user
        // sees what was dropped -- no silent fallback.
        assertTrue("A picture-skip warning must be in the synthesizer's "
            + "warnings list. warnings=" + result.warnings(),
            result.warnings().stream().anyMatch(w -> w.toLowerCase().contains("picture")));

        // JSON round-trip preserves the skip (no spec reappears via
        // serialization noise).
        String json = CommandSpecJson.toJsonArray(result.script().topologicalOrder());
        boolean picAfterJson = CommandSpecJson.fromJsonArray(json).stream()
            .filter(s -> s instanceof CommandSpec.AddShapeSpec)
            .map(s -> (CommandSpec.AddShapeSpec) s)
            .anyMatch(s -> s.shapeType() == SlideShape.ShapeType.PICTURE);
        assertFalse("No PICTURE AddShapeSpec must reappear after JSON round-trip",
            picAfterJson);
    }

    // ===================================================================
    // Compound primitives round-trip through the GUI JSON/Run flow
    // ===================================================================

    @Test
    public void codeBox_applyToNewSlide_recreatesGroupWithMarker() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Source", "slideLayout7");
        orch.createSlide(2, "Target", "slideLayout7");
        CommandFactory cf = new CommandFactory(orch);
        ToolDispatcher dispatcher = new ToolDispatcher(orch, cf, new CommandInvoker());
        String created = dispatcher.dispatch("create_code_box",
            "{\"slideNumber\":1,\"code\":\"int x = 1;\",\"language\":\"java\"}");
        assertTrue("Code box creation must succeed: " + created,
            created.startsWith("Created"));

        applyViaGuiFlow(orch, 1, 2);

        SlideShape group = firstGroup(orch, 2);
        assertNotNull("Target slide should have a GROUP after apply", group);
        assertNotNull("Group name should carry the code-box marker", group.getName());
        assertTrue("Marker must round-trip through JSON+run: name=" + group.getName(),
            group.getName().startsWith("excudo:code_box_v1:"));
    }

    @Test
    public void mermaid_applyToNewSlide_recreatesGroupWithMarker() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Source", "slideLayout7");
        orch.createSlide(2, "Target", "slideLayout7");
        CommandFactory cf = new CommandFactory(orch);
        ToolDispatcher dispatcher = new ToolDispatcher(orch, cf, new CommandInvoker());
        String created = dispatcher.dispatch("create_diagram",
            "{\"slideNumber\":1,\"mermaid\":\"graph TD\\n  A --> B\"}");
        assertFalse("Diagram creation must succeed: " + created,
            created.startsWith("Error"));

        applyViaGuiFlow(orch, 1, 2);

        SlideShape group = firstGroup(orch, 2);
        assertNotNull("Target slide should have a GROUP after apply", group);
        assertNotNull("Group name should carry the diagram marker", group.getName());
        assertTrue("Marker must round-trip through JSON+run: name=" + group.getName(),
            group.getName().startsWith("excudo:diagram_v1:"));
    }

    // ===================================================================
    // Connector: the silent-drop case the GUI inherits
    // ===================================================================

    @Test
    public void connector_applyToNewSlide_isSilentlyDropped() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Source", "slideLayout7");
        orch.createSlide(2, "Target", "slideLayout7");

        var a = orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1_000_000, 1_000_000), "",
            "A", ShapeStyle.defaultStyle());
        var b = orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(3_000_000, 0, 1_000_000, 1_000_000), "",
            "B", ShapeStyle.defaultStyle());
        var connRes = orch.addConnector(1, "straight",
            new ShapeGeometry(1_000_000, 500_000, 2_000_000, 0),
            "none", "triangle", "000000", "solid",
            a.getData().orElseThrow(), 1,
            b.getData().orElseThrow(), 1, null);
        assertTrue("addConnector must succeed: " + connRes.getMessage(),
            connRes.isSuccess());

        applyViaGuiFlow(orch, 1, 2);

        int connectorsOnTarget = countConnectors(orch, 2);
        int rectsOnTarget = countShapesOfType(orch, 2, SlideShape.ShapeType.RECTANGLE);

        // Ideal: connector on target. Current: connector silently dropped,
        // only the two rectangles round-trip.
        assertEquals("Target should have both rectangles", 2, rectsOnTarget);
        assertTrue("After GUI apply the target should have at least one "
            + "connector (currently dropped silently by SlideStateBuilder/"
            + "ScriptSynthesizer). count=" + connectorsOnTarget,
            connectorsOnTarget >= 1);
    }

    // ===================================================================
    // Cross-cutting: JSON serialization survives every spec type
    // ===================================================================

    /**
     * Builds a slide that exercises every shape-spec-emitting feature
     * (geometry, style, text, group, code box). Synthesizes the script,
     * JSON-serializes, deserializes, asserts the resulting spec list is
     * <em>list-equal</em> -- a stricter bar than "executes without error".
     * If a future spec type forgets a JSON adapter the round-trip drops
     * it; this fails loudly when that happens.
     */
    @Test
    public void jsonRoundTrip_preservesSpecListAcrossMultipleSpecTypes() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Source", "slideLayout7");
        orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1_000_000, 1_000_000), "Hello",
            "R1", ShapeStyle.defaultStyle());
        orch.addShape(1, SlideShape.ShapeType.ELLIPSE,
            new ShapeGeometry(2_000_000, 0, 1_000_000, 1_000_000), "World",
            "E1", ShapeStyle.defaultStyle());

        SlideStateBuilder builder = new SlideStateBuilder(orch);
        SlideStateDiff diff = SlideStateDiffer.diff(
            builder.baseline(1), builder.current(1));
        List<CommandSpec> original = ScriptSynthesizer.synthesize(diff, 1)
            .script().topologicalOrder();

        String json = CommandSpecJson.toJsonArray(original);
        List<CommandSpec> reparsed = CommandSpecJson.fromJsonArray(json);

        assertEquals("JSON round-trip must preserve the spec count",
            original.size(), reparsed.size());

        // Per-spec equality. Records are value-equal, so a missing JSON
        // adapter or a field that didn't ride through will fail here.
        for (int i = 0; i < original.size(); i++) {
            assertEquals("Spec " + i + " (" + original.get(i).getClass().getSimpleName()
                + ") must survive JSON round-trip identically",
                original.get(i), reparsed.get(i));
        }
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    /**
     * Mirrors {@code SlideSpecController.applyToNewSlide}: synthesize from
     * source, JSON-serialize, wrap in RunSlideScriptCommand targeting the
     * target slide, execute via invoker. Returns the executed command so
     * tests can assert on appliedSpecCount.
     */
    private static RunSlideScriptCommand applyViaGuiFlow(PPTXOrchestratorImpl orch,
            int sourceSlide, int targetSlide) {
        SlideStateBuilder builder = new SlideStateBuilder(orch);
        SlideStateDiff diff = SlideStateDiffer.diff(
            builder.baseline(sourceSlide), builder.current(sourceSlide));
        List<CommandSpec> specs = ScriptSynthesizer.synthesize(diff, sourceSlide)
            .script().topologicalOrder();
        String json = CommandSpecJson.toJsonArray(specs);

        RunSlideScriptCommand cmd = new RunSlideScriptCommand(
            targetSlide, json, orch, null);
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(cmd);
        return cmd;
    }

    private static int countShapesOfType(PPTXOrchestratorImpl orch, int slideNumber,
            SlideShape.ShapeType type) {
        ShapeRegistry reg = parsedReg(orch, slideNumber);
        if (reg == null) return 0;
        return (int) reg.getAllShapes().stream()
            .filter(s -> s.getType() == type).count();
    }

    private static int countConnectors(PPTXOrchestratorImpl orch, int slideNumber) {
        ShapeRegistry reg = parsedReg(orch, slideNumber);
        if (reg == null) return 0;
        return (int) reg.getAllShapes().stream()
            .filter(s -> s.getType().name().contains("CONNECTOR"))
            .count();
    }

    private static SlideShape firstGroup(PPTXOrchestratorImpl orch, int slideNumber) {
        ShapeRegistry reg = parsedReg(orch, slideNumber);
        if (reg == null) return null;
        return reg.getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.GROUP)
            .findFirst().orElse(null);
    }

    private static int firstSlideWithPicture(PPTXOrchestratorImpl orch) {
        PPTXDocument doc = orch.getContext().get().getDocument();
        for (Integer n : doc.getSlideNumbers()) {
            if (countShapesOfType(orch, n, SlideShape.ShapeType.PICTURE) > 0) return n;
        }
        return -1;
    }

    private static int newBlankSlideAfter(PPTXOrchestratorImpl orch, int after) {
        int target = orch.getContext().get().getDocument().getSlideNumbers().size() + 1;
        var res = orch.createSlide(target, "Target", "slideLayout7");
        assertTrue("createSlide must succeed: " + res.getMessage(), res.isSuccess());
        return target;
    }

    private static ShapeRegistry parsedReg(PPTXOrchestratorImpl orch, int slideNumber) {
        ParsedSlideData parsed = orch.getContext().get().getDocument().getParsedSlideData(
            slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        return parsed == null ? null : parsed.getShapeRegistry();
    }

    private static PPTXOrchestratorImpl newScaffolded() {
        try {
            PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
            PPTXOrchestratorImpl orch = new PPTXOrchestratorImpl();
            orch.initialize(doc);
            return orch;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static PPTXOrchestratorImpl loadOrchestratorFromFile(File f) throws Exception {
        PPTXDocument doc = PPTXDocument.loadFromZip(f);
        PPTXOrchestratorImpl orch = new PPTXOrchestratorImpl();
        orch.initialize(doc);
        return orch;
    }
}
