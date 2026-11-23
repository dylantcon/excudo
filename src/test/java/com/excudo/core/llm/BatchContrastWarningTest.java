package com.excudo.core.llm;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * execute_commands warns when a batch leaves text nearly invisible. The
 * contrast check only ran in validate_layout, so dark text on the dark Excudo
 * theme (where dk1 is the near-black background) went unnoticed.
 */
public class BatchContrastWarningTest {

    @Test
    public void darkTextOnTheDarkThemeIsReported() throws Exception {
        ToolDispatcher dispatcher = deck("excudo");
        String out = run(dispatcher, "[" + textBox("\"fontColor\":\"dk1\"") + "]");
        assertTrue(out, out.startsWith("OK: 1 command(s) executed"));
        assertTrue(out, out.contains("contrast, slide 1: SPID "));
        assertTrue(out, out.contains("#0D1117"));
        assertTrue("says how to fix it: " + out, out.contains("set-font"));
    }

    @Test
    public void recolouringReadableTextIsReportedToo() throws Exception {
        ToolDispatcher dispatcher = deck("excudo");
        String created = run(dispatcher, "[" + textBox(null) + "]");
        assertFalse("readable by default: " + created, created.contains("contrast"));
        String spid = created.replaceAll("(?s).*spid (\\d+).*", "$1");
        String out = run(dispatcher, "[{\"type\":\"set-font\",\"slideNumber\":1,\"targetSpid\":" + spid
            + ",\"color\":\"161B22\"}]");
        assertTrue(out, out.contains("contrast, slide 1: SPID " + spid));
    }

    @Test
    public void whiteTextOnALightThemeIsReported() throws Exception {
        String out = run(deck("minimal"), "[" + textBox("\"fontColor\":\"FFFFFF\"") + "]");
        assertTrue(out, out.contains("contrast, slide 1:"));
    }

    @Test
    public void readableTextSaysNothing() throws Exception {
        String out = run(deck("excudo"), "[" + textBox("\"fontColor\":\"E6EDF3\"") + "]");
        assertFalse(out, out.contains("contrast"));
    }

    private static ToolDispatcher deck(String theme) throws Exception {
        PPTXOrchestratorImpl orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(PresentationScaffolder.scaffoldDocument(theme));
        orchestrator.createSlide(1, "Contrast", "slideLayout7");
        return new ToolDispatcher(orchestrator, new CommandFactory(orchestrator), new CommandInvoker());
    }

    private static String textBox(String extra) {
        return "{\"type\":\"add-shape\",\"slideNumber\":1,\"shapeType\":\"TEXT_BOX\",\"text\":\"Hard to read\","
            + "\"x\":500000,\"y\":500000,\"width\":4000000,\"height\":800000"
            + (extra == null ? "" : "," + extra) + "}";
    }

    private static String run(ToolDispatcher dispatcher, String commands) {
        JsonObject input = new JsonObject();
        input.addProperty("commands", commands);
        return dispatcher.dispatch("execute_commands", input.toString());
    }
}
