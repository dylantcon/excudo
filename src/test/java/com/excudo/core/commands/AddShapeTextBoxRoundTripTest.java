package com.excudo.core.commands;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.llm.ToolDispatcher;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.parsing.CommandParameters;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Pins TEXT_BOX round-trip: when add-shape is called with shapeType
 * TEXT_BOX, the resulting OOXML carries cNvSpPr/@txBox="1", and the
 * parser re-reads that flag into SlideShape.isTextBox(). Display layer
 * (ToolDispatcher.handleGetSlideShapes) reports the shape as "TEXT_BOX"
 * rather than "RECTANGLE".
 *
 * The behaviour mirrors PowerPoint's own output: TEXT_BOX is structurally
 * a rectangle preset but carries the spec's authorial-intent marker on
 * its non-visual properties.
 */
public class AddShapeTextBoxRoundTripTest {

    private PPTXOrchestratorImpl orchestrator;
    private CommandContext ctx;
    private ToolDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "TBT", "slideLayout2");
        ctx = new CommandContext(orchestrator, null);
        CommandFactory cf = new CommandFactory(orchestrator);
        dispatcher = new ToolDispatcher(orchestrator, cf, new CommandInvoker());
    }

    @Test
    public void addShapeTextBoxAliasSetsTxBoxMarker() throws Exception {
        CommandParameters parsed = new CommandParameters("add-shape", Map.of(
            "slide", "1",
            "shape-type", "TEXT_BOX",
            "text", "I am a text box",
            "x", "1000000", "y", "1000000",
            "width", "3000000", "height", "1000000"));
        Command cmd = CommandClassRegistry.createFromParameters(parsed, ctx);
        cmd.execute();

        // The just-created shape should report isTextBox()==true via the
        // parser path -- which means the writer set cNvSpPr/@txBox="1"
        // and the parser read it back into the model field.
        SlideShape created = findByText("I am a text box");
        assertNotNull("Text box shape must be discoverable in the registry", created);
        assertTrue("Newly-created TEXT_BOX should round-trip with isTextBox()==true",
            created.isTextBox());
    }

    @Test
    public void addShapeRectangleDoesNotSetTxBoxMarker() throws Exception {
        CommandParameters parsed = new CommandParameters("add-shape", Map.of(
            "slide", "1",
            "shape-type", "RECTANGLE",
            "text", "Just a rectangle",
            "x", "1000000", "y", "2500000",
            "width", "3000000", "height", "1000000"));
        Command cmd = CommandClassRegistry.createFromParameters(parsed, ctx);
        cmd.execute();

        SlideShape created = findByText("Just a rectangle");
        assertNotNull(created);
        assertFalse("A plain RECTANGLE must NOT carry the txBox marker",
            created.isTextBox());
    }

    @Test
    public void getSlideShapesDisplaysTextBoxLabel() throws Exception {
        // Add one TEXT_BOX and one styled RECTANGLE; verify the
        // get_slide_shapes MCP tool labels them differently.
        CommandClassRegistry.createFromParameters(new CommandParameters("add-shape", Map.of(
            "slide", "1", "shape-type", "TEXT_BOX", "text", "TXTBOX_LABEL",
            "x", "0", "y", "0", "width", "1000000", "height", "500000")), ctx).execute();
        CommandClassRegistry.createFromParameters(new CommandParameters("add-shape", Map.of(
            "slide", "1", "shape-type", "RECTANGLE", "text", "RECT_LABEL",
            "x", "0", "y", "1000000", "width", "1000000", "height", "500000")), ctx).execute();

        String out = dispatcher.dispatch("get_slide_shapes",
            "{\"slideNumber\":1}");
        // The text box line should say TEXT_BOX, the rectangle line RECTANGLE.
        // Search for "TEXT_BOX" preceding "TXTBOX_LABEL" in the output.
        int textBoxLine = out.indexOf("TXTBOX_LABEL");
        int rectLine = out.indexOf("RECT_LABEL");
        assertTrue("Both labels should be present", textBoxLine > 0 && rectLine > 0);

        String textBoxSection = out.substring(Math.max(0, textBoxLine - 50), textBoxLine);
        String rectSection = out.substring(Math.max(0, rectLine - 50), rectLine);
        assertTrue("TEXT_BOX line should display TEXT_BOX type, got:\n" + textBoxSection,
            textBoxSection.contains("TEXT_BOX"));
        assertTrue("RECTANGLE line should display RECTANGLE type (not TEXT_BOX), got:\n" + rectSection,
            rectSection.contains("RECTANGLE") && !rectSection.contains("TEXT_BOX"));
    }

    private SlideShape findByText(String text) throws Exception {
        return orchestrator.getSlideData(1).getData().orElseThrow()
            .getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getTextContent() != null && s.getTextContent().contains(text))
            .findFirst().orElse(null);
    }
}
