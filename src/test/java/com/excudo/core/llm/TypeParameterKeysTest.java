package com.excudo.core.llm;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.RequestSchema;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.google.gson.JsonParser;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.Assert.*;

/**
 * "type" names the command in execute_commands, so a command parameter called
 * "type" could never be sent: set-transition failed every way the agent tried.
 * Those parameters now have their own agent-facing names (transitionType,
 * layoutType, placeholderType), and when "command" names the command, "type"
 * is read as a parameter.
 */
public class TypeParameterKeysTest {

    private PPTXOrchestratorImpl orchestrator;
    private ToolDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "One", "slideLayout7");
        orchestrator.createSlide(2, "Two", "slideLayout7");
        dispatcher = new ToolDispatcher(orchestrator, new CommandFactory(orchestrator), new CommandInvoker());
    }

    @Test
    public void transitionTypeSetsTheTransition() {
        String out = run("[{\"type\":\"set-transition\",\"slideNumber\":1,\"transitionType\":\"fade\",\"speed\":\"med\"}]");
        assertFalse(out, out.contains("FAILED"));
        assertEquals("fade", transitionOn(1));
    }

    @Test
    public void typeIsAParameterWhenCommandNamesTheCommand() {
        RequestSchema.ActionRequest a = ToolDispatcher.parseOneAction(JsonParser.parseString(
            "{\"command\":\"set-transition\",\"slideNumber\":2,\"type\":\"fade\"}").getAsJsonObject());
        assertEquals("set-transition", a.getType());
        assertEquals("fade", a.getParameters().get("type"));

        String out = run("[{\"command\":\"set-transition\",\"slideNumber\":2,\"type\":\"fade\"}]");
        assertFalse(out, out.contains("FAILED"));
        assertEquals("fade", transitionOn(2));
    }

    @Test
    public void typeStillNamesTheCommandWithoutACommandKey() {
        RequestSchema.ActionRequest a = ToolDispatcher.parseOneAction(JsonParser.parseString(
            "{\"type\":\"add-placeholder\",\"placeholderType\":\"body\"}").getAsJsonObject());
        assertEquals("add-placeholder", a.getType());
        assertEquals("body", a.getParameters().get("placeholderType"));
        String err = ToolDispatcher.validateActionStrictly(a);
        assertTrue("placeholderType is a known key: " + err,
            err == null || !(err.contains("Unknown parameter") || err.contains("'type'")));
    }

    @Test
    public void schemaShowsTheAgentFacingNames() {
        String out = dispatcher.dispatch("get_command_schemas",
            "{\"commands\":[\"set-transition\",\"add-layout\",\"add-placeholder\"]}");
        assertTrue(out, out.contains("transitionType"));
        assertTrue(out, out.contains("layoutType"));
        assertTrue(out, out.contains("placeholderType"));
    }

    private String run(String commands) {
        return dispatcher.dispatch("execute_commands",
            "{\"commands\":" + com.google.gson.JsonParser.parseString("\"" + commands.replace("\"", "\\\"") + "\"") + "}");
    }

    /** The transition element's local name on the slide, e.g. "fade", or null. */
    private String transitionOn(int slide) {
        NodeList t = orchestrator.getContext().get().getDocument().getSlideDocument(slide)
            .getElementsByTagNameNS("*", "transition");
        if (t.getLength() == 0) return null;
        for (org.w3c.dom.Node n = t.item(0).getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) return e.getLocalName();
        }
        return null;
    }
}
