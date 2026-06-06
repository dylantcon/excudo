package com.excudo.core.synthesis;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
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
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Exercises the synthesis vocabulary against the failure modes that
 * historically caused agentic edits to silently mangle decks:
 *
 * <ul>
 *   <li>Picture duplication via the synthesis path (no media field on
 *       {@code AddShapeSpec} or {@code ShapeSnapshot}).</li>
 *   <li>Picture duplication via the command path (DOM clone — type-agnostic).</li>
 *   <li>Image-fill on a regular shape ({@code ShapeStyle}/{@code ShapeFill}
 *       carry no blip ref).</li>
 *   <li>Connector endpoint preservation ({@code AddShapeSpec} carries no
 *       startSpid/endSpid).</li>
 *   <li>Compound-marker survival across save+reload (the in-memory
 *       round-trip tests don't exercise the ZIP serialization path).</li>
 * </ul>
 *
 * <p>Each scenario is structured so the assertion expresses the
 * <em>ideal</em> behavior. A failing test is a punch-list item; a
 * passing test confirms the easy path already works.
 */
public class SynthesisStressTest {

    private static final File PICTURE_FIXTURE =
        new File("test-pptx-samples/generalist_test_file.pptx");

    // ===================================================================
    // Picture duplication: synthesis path
    // ===================================================================

    /**
     * Loads a deck that contains a picture, snapshots it, and runs the
     * synthesizer over the diff. With the picture channel in place the
     * synthesizer must emit a typed {@link CommandSpec.AddPictureSpec}
     * carrying the source picture's {@link com.excudo.core.model.BlipRef}
     * (resolved canonical OPC part name) rather than skipping the
     * picture or emitting an unrunnable AddShapeSpec.
     */
    @Test
    public void pictureDuplication_synthesisPath_emitsAddPictureSpec() throws Exception {
        assumeTrue("Picture fixture not present: " + PICTURE_FIXTURE, PICTURE_FIXTURE.exists());
        PPTXOrchestratorImpl orch = loadOrchestratorFromFile(PICTURE_FIXTURE);

        SlidePictureRef pic = firstPicture(orch);
        assumeTrue("Fixture has no picture shape", pic != null);

        SlideStateBuilder builder = new SlideStateBuilder(orch);
        SlideStateDiff diff = SlideStateDiffer.diff(
            builder.baseline(pic.slideNumber), builder.current(pic.slideNumber));
        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, pic.slideNumber);

        // No AddShapeSpec for PICTURE -- still verboten because
        // AddShapeCommand's PICTURE guard would reject it.
        boolean strayShapeSpec = result.script().topologicalOrder().stream()
            .filter(s -> s instanceof CommandSpec.AddShapeSpec)
            .map(s -> (CommandSpec.AddShapeSpec) s)
            .anyMatch(s -> s.shapeType() == SlideShape.ShapeType.PICTURE);
        assertFalse("PICTURE must NOT flow through AddShapeSpec",
            strayShapeSpec);

        // AddPictureSpec for the source picture carrying its BlipRef.
        List<CommandSpec.AddPictureSpec> picSpecs = result.script().topologicalOrder().stream()
            .filter(s -> s instanceof CommandSpec.AddPictureSpec)
            .map(s -> (CommandSpec.AddPictureSpec) s)
            .filter(s -> s.sourceSpidHint() != null && s.sourceSpidHint() == pic.spid)
            .toList();
        assertEquals("Synthesizer must emit exactly one AddPictureSpec for "
            + "the source picture SPID " + pic.spid, 1, picSpecs.size());
        CommandSpec.AddPictureSpec spec = picSpecs.get(0);
        assertNotNull("BlipRef.mediaPartName must be populated",
            spec.blipRef().mediaPartName());
        assertTrue("mediaPartName must look like an OPC media path: "
            + spec.blipRef().mediaPartName(),
            spec.blipRef().mediaPartName().startsWith("ppt/media/"));
    }

    // ===================================================================
    // Picture duplication: command path (DuplicateShapeCommand)
    // ===================================================================

    /**
     * DuplicateShapeCommand clones the shape's DOM element. For a picture
     * that means the {@code <p:pic>} element is deep-cloned, which carries
     * the {@code <a:blip r:embed="..."/>} reference. Whether the cloned
     * picture renders correctly after save+reload is the bar.
     */
    @Test
    public void pictureDuplication_commandPath_preservesBlipRefAfterSaveReload() throws Exception {
        assumeTrue("Picture fixture not present: " + PICTURE_FIXTURE, PICTURE_FIXTURE.exists());
        PPTXOrchestratorImpl orch = loadOrchestratorFromFile(PICTURE_FIXTURE);

        SlidePictureRef pic = firstPicture(orch);
        assumeTrue("Fixture has no picture shape", pic != null);

        int countBefore = countPictures(orch, pic.slideNumber);

        // Duplicate the picture in place via the DOM-clone command.
        var dup = orch.duplicateShape(pic.slideNumber, pic.spid, 457200L, 457200L);
        assertTrue("DuplicateShapeCommand must report success: " + dup.getMessage(),
            dup.isSuccess());
        Integer newSpid = dup.getData().orElse(null);
        assertNotNull("Duplicated SPID must be returned", newSpid);

        // Save + reload via the on-disk path. This exercises the ZIP
        // serializer, the rels rewrite, and the parser's picture
        // reconstruction -- all the places a media-bearing shape could
        // silently degrade.
        Path tmp = Files.createTempFile("excudo-picdup-", ".pptx");
        try {
            orch.getContext().get().getDocument().save(tmp.toFile());

            PPTXOrchestratorImpl reloaded = loadOrchestratorFromFile(tmp.toFile());
            int countAfter = countPictures(reloaded, pic.slideNumber);
            assertEquals("Picture count on slide must increase by exactly one after duplicate+save+reload",
                countBefore + 1, countAfter);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ===================================================================
    // ShapeSnapshot / ShapeStyle: no blip / image-fill channel
    // ===================================================================

    /**
     * Picture-channel landed: ShapeSnapshot now has a blipRef field.
     * Lock the contract here so a future refactor that drops it (or
     * renames it without updating SlideStateBuilder + ScriptSynthesizer
     * + AddPictureSpec atomically) fails loudly.
     */
    @Test
    public void shapeSnapshot_hasBlipRefField() throws Exception {
        ShapeSnapshot.class.getDeclaredField("blipRef");
    }

    @Test
    public void shapeStyle_hasNoBlipFillField() {
        String[] blipFillCandidates = {
            "blipFill", "imageFill", "pictureFill", "blip", "mediaFill"
        };
        for (String field : blipFillCandidates) {
            try {
                ShapeStyle.class.getDeclaredField(field);
                fail("ShapeStyle now has a '" + field + "' field -- the "
                    + "STYLE differ in SlideStateDiffer + SetShapeStyleSpec "
                    + "must update to compare/emit it.");
            } catch (NoSuchFieldException ignored) {
                // expected for now
            }
        }
    }

    // ===================================================================
    // Compound-marker survival across save+reload
    // ===================================================================

    /**
     * The existing CodeBoxRoundTripTest verifies in-memory recognition.
     * This one writes the deck to disk, reloads, and verifies the
     * compound marker survives the ZIP+parse round-trip and the
     * synthesizer still emits a single CreateCodeBoxSpec.
     */
    @Test
    public void codeBoxMarker_survivesSaveReloadAndStillEmitsCompoundSpec() throws Exception {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "CodeBox", "slideLayout7");
        CommandFactory cf = new CommandFactory(orch);
        ToolDispatcher dispatcher = new ToolDispatcher(orch, cf, new CommandInvoker());

        String created = dispatcher.dispatch("create_code_box",
            "{\"slideNumber\":1,\"code\":\"int x = 1;\\nint y = 2;\",\"language\":\"java\"}");
        assertTrue("Create must succeed: " + created, created.startsWith("Created"));

        Path tmp = Files.createTempFile("excudo-codebox-", ".pptx");
        try {
            orch.getContext().get().getDocument().save(tmp.toFile());

            PPTXOrchestratorImpl reloaded = loadOrchestratorFromFile(tmp.toFile());
            SlideShape group = reloaded.getSlideData(1).getData().orElseThrow()
                .getShapeRegistry().getAllShapes().stream()
                .filter(s -> s.getType() == SlideShape.ShapeType.GROUP)
                .findFirst().orElse(null);
            assertNotNull("Code box group must survive save+reload", group);
            assertTrue("Marker prefix must survive: name=" + group.getName(),
                group.getName() != null
                    && group.getName().startsWith("excudo:code_box_v1:"));

            SlideStateBuilder builder = new SlideStateBuilder(reloaded);
            SlideStateDiff diff = SlideStateDiffer.diff(builder.baseline(1), builder.current(1));
            List<CommandSpec> specs = ScriptSynthesizer.synthesize(diff, 1)
                .script().topologicalOrder();
            long compound = specs.stream()
                .filter(s -> s instanceof CommandSpec.CreateCodeBoxSpec).count();
            assertEquals("After save+reload synthesizer must still emit a single CreateCodeBoxSpec, "
                + "not decompose. specs=" + specs, 1, compound);
            long addShapes = specs.stream()
                .filter(s -> s instanceof CommandSpec.AddShapeSpec).count();
            assertEquals("AddShapeSpecs must be consumed by the compound: specs=" + specs,
                0, addShapes);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Same as the code box case but for Mermaid. The marker carries the
     * base64-encoded mermaid source; it has to survive ZIP+parse.
     */
    @Test
    public void mermaidMarker_survivesSaveReloadAndStillEmitsCompoundSpec() throws Exception {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Diagram", "slideLayout7");
        CommandFactory cf = new CommandFactory(orch);
        ToolDispatcher dispatcher = new ToolDispatcher(orch, cf, new CommandInvoker());

        String mermaid = "graph TD\\n  A --> B\\n  B --> C";
        String created = dispatcher.dispatch("create_diagram",
            "{\"slideNumber\":1,\"mermaid\":\"" + mermaid + "\"}");
        assertFalse("create_diagram must succeed: " + created, created.startsWith("Error"));

        Path tmp = Files.createTempFile("excudo-mermaid-", ".pptx");
        try {
            orch.getContext().get().getDocument().save(tmp.toFile());

            PPTXOrchestratorImpl reloaded = loadOrchestratorFromFile(tmp.toFile());
            SlideShape group = reloaded.getSlideData(1).getData().orElseThrow()
                .getShapeRegistry().getAllShapes().stream()
                .filter(s -> s.getType() == SlideShape.ShapeType.GROUP)
                .findFirst().orElse(null);
            assertNotNull("Mermaid group must survive save+reload", group);
            assertTrue("Marker prefix must survive: name=" + group.getName(),
                group.getName() != null
                    && group.getName().startsWith("excudo:diagram_v1:"));

            String recovered = com.excudo.core.commands.mutating.slide.CreateMermaidDiagramCommand
                .sourceFromTag(group.getName());
            assertNotNull("Mermaid source must decode from the marker after save+reload",
                recovered);
            assertTrue("Decoded source carries the arrows: " + recovered,
                recovered.contains("A --> B") && recovered.contains("B --> C"));

            SlideStateBuilder builder = new SlideStateBuilder(reloaded);
            SlideStateDiff diff = SlideStateDiffer.diff(builder.baseline(1), builder.current(1));
            List<CommandSpec> specs = ScriptSynthesizer.synthesize(diff, 1)
                .script().topologicalOrder();
            long compound = specs.stream()
                .filter(s -> s instanceof CommandSpec.CreateDiagramSpec).count();
            assertEquals("After save+reload synthesizer must still emit a single CreateDiagramSpec, "
                + "not decompose. specs=" + specs, 1, compound);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ===================================================================
    // Connector endpoint preservation
    // ===================================================================

    /**
     * Add a connector between two shapes via the connector command and
     * run it through synthesis. The synthesizer must emit a typed
     * {@link CommandSpec.AddConnectorSpec} carrying both endpoint SPID
     * bindings -- not an {@code AddShapeSpec} (which has no endpoint
     * channel) and not nothing at all (which is what happened before
     * the parser XPath was widened to include {@code p:cxnSp}).
     */
    @Test
    public void connectorEndpoints_preservedInSynthesis() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Connectors", "slideLayout7");
        var r1 = orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1_000_000, 1_000_000), "",
            "A", ShapeStyle.defaultStyle());
        var r2 = orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(3_000_000, 0, 1_000_000, 1_000_000), "",
            "B", ShapeStyle.defaultStyle());
        Integer aSpid = r1.getData().orElse(null);
        Integer bSpid = r2.getData().orElse(null);
        assertNotNull(aSpid);
        assertNotNull(bSpid);

        var conn = orch.addConnector(1, "straight",
            new ShapeGeometry(1_000_000, 500_000, 2_000_000, 0),
            "none", "triangle", "000000", "solid",
            aSpid, 1, bSpid, 1, null);
        assertTrue("addConnector must succeed: " + conn.getMessage(), conn.isSuccess());

        SlideStateBuilder builder = new SlideStateBuilder(orch);
        SlideStateDiff diff = SlideStateDiffer.diff(builder.baseline(1), builder.current(1));
        List<CommandSpec> specs = ScriptSynthesizer.synthesize(diff, 1)
            .script().topologicalOrder();

        // Exactly one AddConnectorSpec, with the expected endpoint
        // bindings round-tripped from the snapshot's ConnectorAttachment.
        List<CommandSpec.AddConnectorSpec> connSpecs = specs.stream()
            .filter(s -> s instanceof CommandSpec.AddConnectorSpec)
            .map(s -> (CommandSpec.AddConnectorSpec) s)
            .toList();
        assertEquals("Synthesizer must emit exactly one AddConnectorSpec. specs=" + specs,
            1, connSpecs.size());
        CommandSpec.AddConnectorSpec spec = connSpecs.get(0);
        assertEquals("startSpid round-trips", aSpid, spec.startSpid());
        assertEquals("endSpid round-trips",   bSpid, spec.endSpid());
        assertEquals("connector preset preserved", "straight", spec.connectorType());

        // The connector must not double-emit through the generic
        // AddShapeSpec path.
        boolean stray = specs.stream()
            .filter(s -> s instanceof CommandSpec.AddShapeSpec)
            .map(s -> (CommandSpec.AddShapeSpec) s)
            .anyMatch(s -> s.shapeType() == SlideShape.ShapeType.CONNECTION);
        assertFalse("Connector must not also appear as a generic AddShapeSpec. specs=" + specs,
            stray);
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private record SlidePictureRef(int slideNumber, int spid) {}

    private static SlidePictureRef firstPicture(PPTXOrchestratorImpl orch) {
        PPTXDocument doc = orch.getContext().get().getDocument();
        for (Integer n : doc.getSlideNumbers()) {
            ParsedSlideData parsed = doc.getParsedSlideData(n,
                (dom, sn) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, sn));
            if (parsed == null) continue;
            ShapeRegistry reg = parsed.getShapeRegistry();
            if (reg == null) continue;
            for (SlideShape s : reg.getAllShapes()) {
                if (s.getType() == SlideShape.ShapeType.PICTURE) {
                    return new SlidePictureRef(n, s.getSpid());
                }
            }
        }
        return null;
    }

    private static int countPictures(PPTXOrchestratorImpl orch, int slideNumber) {
        ParsedSlideData parsed = orch.getContext().get().getDocument().getParsedSlideData(
            slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        if (parsed == null || parsed.getShapeRegistry() == null) return 0;
        return (int) parsed.getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.PICTURE)
            .count();
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
