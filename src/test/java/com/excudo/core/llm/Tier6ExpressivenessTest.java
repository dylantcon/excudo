package com.excudo.core.llm;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.model.AnimationBinding;
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
 * Pins Tier 6 authoring expressiveness: alpha on add-shape / set-style,
 * delayMs / durationMs on add-animation, lineNumberColor on create_code_box.
 *
 * <p>Each test exercises the full path from CommandParameters through the
 * dispatch layer down to the model state, so a regression in any of the
 * intermediate factories surfaces here.
 */
public class Tier6ExpressivenessTest {

    private PPTXOrchestratorImpl orchestrator;
    private CommandContext ctx;
    private CommandFactory factory;
    private ToolDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Tier6", "slideLayout7");
        ctx = new CommandContext(orchestrator, null);
        factory = new CommandFactory(orchestrator);
        dispatcher = new ToolDispatcher(orchestrator, factory, new CommandInvoker());
    }

    @Test
    public void addShapeWithFillAlphaEmitsAlphaElement() throws Exception {
        Command cmd = CommandClassRegistry.createFromParameters(
            new CommandParameters("add-shape", Map.of(
                "slide", "1",
                "shape-type", "RECTANGLE",
                "text", "alpha-rect",
                "x", "1000000", "y", "1000000",
                "width", "2000000", "height", "1000000",
                "fill-color", "5B9BD5",
                "fill-alpha", "40")),
            ctx);
        cmd.execute();
        SlideShape shape = orchestrator.getSlideData(1).getData().orElseThrow()
            .getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getTextContent() != null && s.getTextContent().contains("alpha-rect"))
            .findFirst().orElseThrow();
        // a:alpha val is positive-fixed-percent: 40% -> 40000.
        String xml = elementToString(shape.getXmlElement());
        assertTrue("Shape XML must include a:alpha child on solidFill: " + xml,
            xml.contains("a:alpha") && xml.contains("val=\"40000\""));
    }

    @Test
    public void setStyleAcceptsAlphaParams() throws Exception {
        // Add a plain rect first.
        Command add = CommandClassRegistry.createFromParameters(
            new CommandParameters("add-shape", Map.of(
                "slide", "1", "shape-type", "RECTANGLE", "text", "set-alpha",
                "x", "1000000", "y", "1000000",
                "width", "2000000", "height", "1000000")),
            ctx);
        add.execute();
        SlideShape shape = orchestrator.getSlideData(1).getData().orElseThrow()
            .getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getTextContent() != null && s.getTextContent().contains("set-alpha"))
            .findFirst().orElseThrow();

        // Apply set-style with explicit alpha.
        CommandParameters setStyle = new CommandParameters("set-style", Map.of(
            "slide", "1",
            "spid", String.valueOf(shape.getSpid()),
            "fill-color", "FF0000",
            "fill-alpha", "25"));
        Command styleCmd = factory.createCommand(setStyle, null);
        styleCmd.execute();

        // Re-fetch to read the updated DOM.
        SlideShape updated = orchestrator.getSlideData(1).getData().orElseThrow()
            .getShapeRegistry().getShape(shape.getSpid());
        String xml = elementToString(updated.getXmlElement());
        assertTrue("set-style must emit a:alpha at 25% (val=25000): " + xml,
            xml.contains("a:alpha") && xml.contains("val=\"25000\""));
    }

    @Test
    public void addAnimationDelayMsReachesBinding() throws Exception {
        // Need a target shape first.
        Command add = CommandClassRegistry.createFromParameters(
            new CommandParameters("add-shape", Map.of(
                "slide", "1", "shape-type", "RECTANGLE", "text", "anim-target",
                "x", "1000000", "y", "1000000",
                "width", "2000000", "height", "1000000")),
            ctx);
        add.execute();
        int spid = orchestrator.getSlideData(1).getData().orElseThrow()
            .getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getTextContent() != null && s.getTextContent().contains("anim-target"))
            .findFirst().orElseThrow().getSpid();

        CommandParameters animParams = CommandParameters.builder("add-animation")
            .addParam("slide", 1)
            .addParam("spid", spid)
            .addParam("type", "fade")
            .addParam("trigger", "on-click")
            .addParam("delay", 250)
            .addParam("duration", 800)
            .build();
        factory.createCommand(animParams, null).execute();

        AnimationBinding binding = orchestrator.getSlideData(1).getData().orElseThrow()
            .getAnimationBindings().stream()
            .filter(b -> b.getTargetSpid() == spid)
            .findFirst().orElseThrow();
        assertEquals("delayMs must reach the binding", "250", binding.getDelay());
        assertEquals("durationMs must reach the binding", "800", binding.getDuration());
    }

    @Test
    public void createCodeBoxAcceptsCustomLineNumberColor() {
        String json = "{\"slideNumber\":1,\"code\":\"int x = 1;\\nint y = 2;\","
            + "\"language\":\"java\",\"lineNumberColor\":\"FF8800\"}";
        String result = dispatcher.dispatch("create_code_box", json);
        assertTrue("create_code_box must succeed: " + result,
            result.startsWith("Created code box"));

        // Find the line-number panel and verify its first run carries the
        // requested color, not the dim-gray default.
        SlideShape lineNumPanel = orchestrator.getSlideData(1).getData().orElseThrow()
            .getShapeRegistry().getAllShapes().stream()
            .filter(s -> "LineNumbers".equals(s.getName()))
            .findFirst().orElseThrow();
        String xml = elementToString(lineNumPanel.getXmlElement());
        assertTrue("Line-number gutter color must be the override: " + xml,
            xml.contains("FF8800"));
        assertFalse("Default 858585 must not appear in override path: " + xml,
            xml.contains("858585"));
    }

    private static String elementToString(org.w3c.dom.Element el) {
        try {
            javax.xml.transform.TransformerFactory tf =
                javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer t = tf.newTransformer();
            t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            java.io.StringWriter sw = new java.io.StringWriter();
            t.transform(new javax.xml.transform.dom.DOMSource(el),
                new javax.xml.transform.stream.StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            return "<<serialize failed: " + e.getMessage() + ">>";
        }
    }
}
