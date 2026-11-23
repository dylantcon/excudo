package com.excudo.core.llm;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * execute_commands names the shapes each command created, so the agent can
 * style or animate them without listing the slide's shapes first.
 */
public class BatchCreatedIdsTest {

    private PPTXOrchestratorImpl orchestrator;
    private ToolDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(PresentationScaffolder.scaffoldDocument("excudo"));
        orchestrator.createSlide(1, "One", "slideLayout7");
        dispatcher = new ToolDispatcher(orchestrator, new CommandFactory(orchestrator), new CommandInvoker());
    }

    @Test
    public void eachCreatedShapeIsReportedWithItsCommand() {
        List<Integer> before = spids();
        String out = run("[" + shape("A", 0) + "," + shape("B", 600_000)
            + ",{\"type\":\"set-transition\",\"slideNumber\":1,\"transitionType\":\"fade\"}]");
        List<Integer> added = new ArrayList<>(spids());
        added.removeAll(before);
        assertEquals("two shapes added: " + added, 2, added.size());

        assertTrue(out, out.startsWith("OK: 3 command(s) executed"));
        assertTrue(out, out.contains("created #1 add-shape: spid " + added.get(0)));
        assertTrue(out, out.contains("created #2 add-shape: spid " + added.get(1)));
        assertFalse("set-transition creates nothing: " + out, out.contains("#3"));
    }

    @Test
    public void groupingReportsTheNewGroup() {
        run("[" + shape("A", 0) + "," + shape("B", 600_000) + "]");
        List<Integer> shapes = spids();
        int a = shapes.get(shapes.size() - 2), b = shapes.get(shapes.size() - 1);
        String out = run("[{\"type\":\"group-shapes\",\"slideNumber\":1,\"spids\":\"" + a + "," + b + "\"}]");
        assertTrue(out, out.startsWith("OK: 1 command(s) executed"));
        assertTrue(out, out.matches("(?s).*created #1 group-shapes: spid \\d+.*"));
    }

    private static String shape(String text, long y) {
        return "{\"type\":\"add-shape\",\"slideNumber\":1,\"shapeType\":\"RECTANGLE\",\"text\":\"" + text
            + "\",\"x\":0,\"y\":" + y + ",\"width\":900000,\"height\":500000}";
    }

    private String run(String commands) {
        JsonObject input = new JsonObject();
        input.addProperty("commands", commands);
        return dispatcher.dispatch("execute_commands", input.toString());
    }

    private List<Integer> spids() {
        List<Integer> out = new ArrayList<>();
        NodeList ids = orchestrator.getContext().get().getDocument().getSlideDocument(1)
            .getElementsByTagNameNS("*", "cNvPr");
        for (int i = 0; i < ids.getLength(); i++) out.add(Integer.parseInt(((Element) ids.item(i)).getAttribute("id")));
        return out;
    }
}
