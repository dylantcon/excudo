package com.excudo.core.llm;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Dispatch-level coverage for the script-authoring MCP tools. Proves
 * the tools are reachable through the core tool set and that the
 * synthesize → run pipeline round-trips an agent-visible workflow.
 */
public class ScriptToolsDispatchTest {

    private PPTXOrchestratorImpl orchestrator;
    private ToolDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Source", "slideLayout7");
        orchestrator.createSlide(2, "Target", "slideLayout7");
        orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 2_000_000, 1_000_000),
            "", "Rect", ShapeStyle.defaultStyle());
        CommandFactory cf = new CommandFactory(orchestrator);
        dispatcher = new ToolDispatcher(orchestrator, cf, new CommandInvoker());
    }

    @Test
    public void scriptAuthoringToolsAreListedInCoreToolset() {
        boolean hasSynth = false;
        boolean hasRun = false;
        for (var t : LLMToolDefinitions.getCoreTools()) {
            if ("synthesize_slide_script".equals(t.name())) hasSynth = true;
            if ("run_slide_script".equals(t.name()))        hasRun = true;
        }
        assertTrue("synthesize_slide_script must be a CORE tool (not deferred)", hasSynth);
        assertTrue("run_slide_script must be a CORE tool (not deferred)", hasRun);
    }

    @Test
    public void synthesizeSlideScript_returnsIndexedSummaryAndJson() {
        String out = dispatcher.dispatch("synthesize_slide_script",
            "{\"slideNumber\":1}");
        assertNotNull(out);
        assertTrue("Output must name the slide: " + out,
            out.contains("slide 1"));
        assertTrue("Output must include an indexed listing (starts with '[0]'): " + out,
            out.contains("[0]"));
        assertTrue("Output must include the JSON array: " + out,
            out.contains("_type"));
        assertTrue("Output must include the workflow hint: " + out,
            out.contains("Workflow"));
    }

    @Test
    public void runSlideScript_appliesToTargetAndReportsCount() {
        String synth = dispatcher.dispatch("synthesize_slide_script",
            "{\"slideNumber\":1}");
        // Extract the JSON array that starts after the "JSON for ..." marker.
        int jsonStart = synth.indexOf("[{");
        assertTrue("Synth output must carry a JSON array starting with [{", jsonStart > 0);
        int jsonEnd = synth.lastIndexOf("}]") + 2;
        String json = synth.substring(jsonStart, jsonEnd);

        // Dispatch run_slide_script with that JSON, targeting slide 2.
        String escaped = com.google.gson.JsonParser.parseString(
            com.google.gson.JsonParser.parseString(
                "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .getAsString()).toString();
        // Simpler: build the input as a JsonObject to avoid escaping pitfalls.
        com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
        payload.addProperty("slideNumber", 2);
        payload.addProperty("script", json);
        String out = dispatcher.dispatch("run_slide_script", payload.toString());
        assertNotNull(out);
        assertTrue("Output must report successful application: " + out,
            out.contains("All successful"));

        // Verify the shape landed on slide 2.
        var doc = orchestrator.getContext().get().getDocument();
        var parsed = doc.getParsedSlideData(2,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        long rectCount = parsed.getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.RECTANGLE)
            .count();
        assertEquals("Target slide must now carry the cloned rectangle",
            1, rectCount);
    }

    @Test
    public void runSlideScript_rejectsMissingScript() {
        String out = dispatcher.dispatch("run_slide_script",
            "{\"slideNumber\":2}");
        assertTrue("Missing script must be reported: " + out,
            out.toLowerCase().contains("script is required"));
    }

    @Test
    public void runSlideScript_rejectsMalformedJson() {
        com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
        payload.addProperty("slideNumber", 2);
        payload.addProperty("script", "not-json");
        String out = dispatcher.dispatch("run_slide_script", payload.toString());
        assertTrue("Malformed JSON must be reported as FAILED: " + out,
            out.toUpperCase().contains("FAIL"));
    }

    // Silence unused 'escaped'/'dispatcher'-unrelated imports warnings.
    @SuppressWarnings("unused")
    private static final String UNUSED = "";
}
