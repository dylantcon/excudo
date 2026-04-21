package com.excudo.core.llm;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pins the get_available_layouts MCP tool's response shape now that
 * placeholder geometry is inlined per layout. Agents can pick a layout
 * from the listing alone, without instantiating a slide just to inspect
 * its placeholder bounds. Catches regressions in either direction
 * (geometry dropped, or unit/format scrambled).
 */
public class GetAvailableLayoutsFormatTest {

    private ToolDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");
        PPTXOrchestratorImpl orch = new PPTXOrchestratorImpl();
        orch.initialize(doc);
        CommandFactory cf = new CommandFactory(orch);
        dispatcher = new ToolDispatcher(orch, cf, new CommandInvoker());
    }

    @Test
    public void layoutListingIncludesPlaceholderGeometryWithEMUAndInches() {
        String out = dispatcher.dispatch("get_available_layouts", "{}");
        assertNotNull(out);
        assertTrue("Listing must start with the heading",
            out.startsWith("Available layouts:"));
        assertTrue("Listing must reference at least one slideLayout",
            out.contains("slideLayout"));

        // EMU coordinates with the @ (x, y) WIDTHxHEIGHT EMU shape.
        // Specific geometry values vary per bundled layout, so we check
        // the structural tokens rather than concrete numbers.
        assertTrue("Listing must include EMU geometry tokens, got:\n" + out,
            out.matches("(?s).*@ \\(\\d+, \\d+\\) \\d+x\\d+ EMU.*"));

        // Parenthesized inches summary alongside EMU.
        assertTrue("Listing must include the parenthesized inches summary, got:\n" + out,
            out.matches("(?s).*\\(~\\d+\\.\\d{2}in x ~\\d+\\.\\d{2}in\\).*"));
    }

    @Test
    public void titleSlideLayoutSurfacesTitleAndSubtitlePlaceholders() {
        String out = dispatcher.dispatch("get_available_layouts", "{}");
        // slideLayout1 in the bundled minimal theme is the Title Slide
        // (title + subtitle). Both placeholders should appear in the
        // listing for that entry.
        int titleIdx = out.indexOf("slideLayout1");
        assertTrue("slideLayout1 must appear", titleIdx >= 0);

        // Trim to the slideLayout1 section (until the next slideLayoutN
        // line or end-of-string) to scope the assertion.
        int nextLayout = out.indexOf("slideLayout", titleIdx + 1);
        String section = nextLayout > 0
            ? out.substring(titleIdx, nextLayout)
            : out.substring(titleIdx);

        assertTrue("Title Slide layout should reference its title placeholder; got:\n" + section,
            section.contains("title") || section.contains("ctrTitle"));
        assertTrue("Title Slide layout should reference its subtitle placeholder; got:\n" + section,
            section.contains("subTitle"));
    }
}
