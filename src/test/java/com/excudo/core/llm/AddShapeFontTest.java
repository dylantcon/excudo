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

import static org.junit.Assert.*;

/**
 * add-shape takes the text's font settings, so a text shape doesn't need a
 * set-font command after it.
 */
public class AddShapeFontTest {

    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
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
    public void fontSettingsApplyToTheNewShapesText() {
        int spid = add("{\"type\":\"add-shape\",\"slideNumber\":1,\"shapeType\":\"TEXT_BOX\",\"text\":\"513\","
            + "\"x\":0,\"y\":0,\"width\":4000000,\"height\":1300000,"
            + "\"fontSize\":80,\"fontColor\":\"FFFFFF\",\"bold\":\"true\",\"fontFamily\":\"Arial\"}");
        Element rPr = firstRunProperties(spid);
        assertEquals("8000", rPr.getAttribute("sz"));
        assertEquals("1", rPr.getAttribute("b"));
        Element srgb = (Element) rPr.getElementsByTagNameNS(A, "srgbClr").item(0);
        assertNotNull("text colour written", srgb);
        assertEquals("FFFFFF", srgb.getAttribute("val"));
        Element latin = (Element) rPr.getElementsByTagNameNS(A, "latin").item(0);
        assertEquals("Arial", latin.getAttribute("typeface"));
    }

    @Test
    public void schemeColoursWork() {
        int spid = add("{\"type\":\"add-shape\",\"slideNumber\":1,\"shapeType\":\"RECTANGLE\",\"text\":\"label\","
            + "\"x\":0,\"y\":0,\"width\":2000000,\"height\":800000,\"fontColor\":\"accent1\",\"italic\":\"true\"}");
        Element rPr = firstRunProperties(spid);
        assertEquals("1", rPr.getAttribute("i"));
        Element scheme = (Element) rPr.getElementsByTagNameNS(A, "schemeClr").item(0);
        assertNotNull(scheme);
        assertEquals("accent1", scheme.getAttribute("val"));
    }

    @Test
    public void withoutFontSettingsTheDefaultsStay() {
        int spid = add("{\"type\":\"add-shape\",\"slideNumber\":1,\"shapeType\":\"RECTANGLE\",\"text\":\"plain\","
            + "\"x\":0,\"y\":0,\"width\":2000000,\"height\":800000}");
        Element rPr = firstRunProperties(spid);
        assertFalse(rPr.hasAttribute("b"));
        assertEquals(0, rPr.getElementsByTagNameNS(A, "srgbClr").getLength());
    }

    @Test
    public void badValuesAreRejectedInsteadOfWritten() {
        // These used to be written as-is (srgbClr val="notacolor", sz="-500"):
        // invalid OOXML that PowerPoint has to repair
        String[][] cases = {
            {"\"fontColor\":\"notacolor\"", "Unrecognised colour 'notacolor'"},
            {"\"fillColor\":\"red\"", "Unrecognised colour 'red'"},
            {"\"fontSize\":-5", "Font size must be 1 to 4000 points"},
            {"\"bold\":\"maybe\"", "Expected true or false"},
        };
        for (String[] c : cases) {
            String out = run("[{\"type\":\"add-shape\",\"slideNumber\":1,\"shapeType\":\"RECTANGLE\",\"text\":\"x\","
                + "\"x\":0,\"y\":0,\"width\":900000,\"height\":500000," + c[0] + "}]");
            assertTrue(c[0] + ": " + out, out.startsWith("FAILED") && out.contains(c[1]));
        }
        assertEquals("nothing added", 1, orchestrator.getContext().get().getDocument().getSlideDocument(1)
            .getElementsByTagNameNS("*", "cNvPr").getLength());

        int spid = add("{\"type\":\"add-shape\",\"slideNumber\":1,\"shapeType\":\"RECTANGLE\",\"text\":\"x\","
            + "\"x\":0,\"y\":0,\"width\":900000,\"height\":500000}");
        String out = run("[{\"type\":\"set-font\",\"slideNumber\":1,\"targetSpid\":" + spid + ",\"color\":\"blue\"}]");
        assertTrue("set-font checks colours too: " + out, out.contains("Unrecognised colour 'blue'"));
    }

    @Test
    public void textAndBackgroundThemeNamesAreThemeColours() {
        // tx1/bg1 weren't recognised, so they were written as hex values
        int spid = add("{\"type\":\"add-shape\",\"slideNumber\":1,\"shapeType\":\"RECTANGLE\",\"text\":\"x\","
            + "\"x\":0,\"y\":0,\"width\":900000,\"height\":500000,\"fontColor\":\"#tx1\"}".replace("#tx1", "tx1"));
        Element scheme = (Element) firstRunProperties(spid).getElementsByTagNameNS(A, "schemeClr").item(0);
        assertEquals("tx1", scheme.getAttribute("val"));
    }

    private String run(String commands) {
        JsonObject input = new JsonObject();
        input.addProperty("commands", commands);
        return dispatcher.dispatch("execute_commands", input.toString());
    }

    private int add(String command) {
        JsonObject input = new JsonObject();
        input.addProperty("commands", "[" + command + "]");
        String out = dispatcher.dispatch("execute_commands", input.toString());
        assertTrue(out, out.startsWith("OK: 1 command(s) executed"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("spid (\\d+)").matcher(out);
        assertTrue(out, m.find());
        return Integer.parseInt(m.group(1));
    }

    private Element firstRunProperties(int spid) {
        NodeList ids = orchestrator.getContext().get().getDocument().getSlideDocument(1)
            .getElementsByTagNameNS("*", "cNvPr");
        for (int i = 0; i < ids.getLength(); i++) {
            Element c = (Element) ids.item(i);
            if (c.getAttribute("id").equals(String.valueOf(spid))) {
                Element sp = (Element) c.getParentNode().getParentNode();
                Element r = (Element) sp.getElementsByTagNameNS(A, "r").item(0);
                return (Element) r.getElementsByTagNameNS(A, "rPr").item(0);
            }
        }
        fail("no shape " + spid);
        return null;
    }
}
