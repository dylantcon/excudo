package com.excudo.core.llm;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.test.utils.StubPPTXOrchestrator;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pins discovery-alignment items from the 2026-04-22 beta-roadmap:
 * <ul>
 *   <li>C2 -- {@code list_shape_types} mirrors {@code list_animation_types} /
 *       {@code list_trigger_types}, exposing every {@code SlideShape.ShapeType}
 *       value so agents stop guessing at names like {@code OVAL}.</li>
 *   <li>C3 -- {@code get_command_schemas} explains the distinction when an
 *       agent asks about a top-level MCP tool (e.g. {@code create_code_box})
 *       instead of returning an unhelpful "no commands matched".</li>
 *   <li>C4 -- {@code list_animation_types} EXIT section now flags that
 *       exit animations reuse entrance enum values via {@code direction=out}.</li>
 * </ul>
 */
public class DiscoveryToolsTest {

    private ToolDispatcher dispatcher;

    @Before
    public void setUp() {
        StubPPTXOrchestrator orch = new StubPPTXOrchestrator();
        CommandFactory cf = new CommandFactory(orch);
        dispatcher = new ToolDispatcher(orch, cf, new CommandInvoker());
    }

    @Test
    public void listShapeTypesIncludesEveryEnumConstant() {
        String out = dispatcher.dispatch("list_shape_types", "{}");
        for (com.excudo.core.model.SlideShape.ShapeType t :
                com.excudo.core.model.SlideShape.ShapeType.values()) {
            assertTrue("Shape type " + t.name() + " missing from list_shape_types: " + out,
                out.contains(t.name()));
        }
        // OOXML preset cross-reference present for at least one well-known case.
        assertTrue("Expected RECTANGLE -> rect mapping: " + out, out.contains("RECTANGLE -> rect"));
        // TEXT_BOX alias documented.
        assertTrue("TEXT_BOX alias must be advertised: " + out,
            out.contains("TEXT_BOX") && out.contains("alias"));
    }

    @Test
    public void getCommandSchemasFlagsTopLevelMcpToolNames() {
        String out = dispatcher.dispatch("get_command_schemas",
            "{\"commands\":\"create_code_box\"}");
        assertTrue("Top-level MCP tool name must be flagged distinctly: " + out,
            out.contains("top-level MCP tool"));
        assertTrue("Should suggest calling the tool directly: " + out,
            out.contains("excudo:create_code_box"));
    }

    @Test
    public void getCommandSchemasFlagsDashedTopLevelToolNames() {
        // Agents sometimes spell tool names with dashes when the canonical
        // form is underscores -- the fallback should normalise.
        String out = dispatcher.dispatch("get_command_schemas",
            "{\"commands\":\"create-code-box\"}");
        assertTrue("Dashed form should still detect the top-level tool: " + out,
            out.contains("top-level MCP tool"));
    }

    @Test
    public void listAnimationTypesExitSectionExplainsDirection() {
        String out = dispatcher.dispatch("list_animation_types", "{}");
        int exitIdx = out.indexOf("EXIT (remove content)");
        assertTrue("EXIT section must be present: " + out, exitIdx > 0);
        String afterExit = out.substring(exitIdx);
        assertTrue("EXIT section must explain direction=out: " + afterExit,
            afterExit.contains("direction=out"));
    }
}
