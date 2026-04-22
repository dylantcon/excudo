package com.excudo.core.llm;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pins the output shape of list_animation_types and list_trigger_types.
 * These are pure-discovery tools agents call before authoring animations,
 * so their contract is "every category is represented and at least one
 * named effect per category surfaces." Catches regressions where a
 * factory is added to one grouping but not the other, or where the
 * vocabulary drifts away from the add-animation parameter surface.
 */
public class ListAnimationTypesToolTest {

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
    public void listAnimationTypesEnumeratesEveryCategoryHeading() {
        String out = dispatcher.dispatch("list_animation_types", "{}");
        assertNotNull(out);
        assertTrue("ENTRANCE heading missing:\n" + out, out.contains("ENTRANCE"));
        assertTrue("EMPHASIS heading missing:\n" + out, out.contains("EMPHASIS"));
        assertTrue("EXIT heading missing:\n" + out,     out.contains("EXIT"));
        assertTrue("MOTION PATHS heading missing:\n" + out, out.contains("MOTION PATHS"));
    }

    @Test
    public void listAnimationTypesIncludesKnownEntranceEffect() {
        String out = dispatcher.dispatch("list_animation_types", "{}");
        // FADE is the canonical entrance effect; if this ever drops out,
        // the grouping has regressed and callers of add-animation will
        // have nothing to pick from.
        String fadeName = AnimationType.FADE.getUserFriendlyName();
        assertTrue("Expected entrance effect " + fadeName + " in listing:\n" + out,
            out.contains(fadeName));
    }

    @Test
    public void listAnimationTypesIncludesMotionPathEffect() {
        String out = dispatcher.dispatch("list_animation_types", "{}");
        String motionLinear = AnimationType.MOTION_LINEAR.getUserFriendlyName();
        assertTrue("Expected motion-path effect " + motionLinear + " in listing:\n" + out,
            out.contains(motionLinear));
    }

    @Test
    public void listTriggerTypesNamesAllThreeWithSemantics() {
        String out = dispatcher.dispatch("list_trigger_types", "{}");
        assertNotNull(out);
        assertTrue("on-click missing:\n" + out,        out.contains("on-click"));
        assertTrue("with-previous missing:\n" + out,   out.contains("with-previous"));
        assertTrue("after-previous missing:\n" + out,  out.contains("after-previous"));
        // Semantic blurbs, not just bare names -- the whole point of this
        // tool is that agents don't have to guess what each trigger means.
        assertTrue("on-click semantics missing (expected 'click'):\n" + out,
            out.toLowerCase().contains("click sequence") || out.toLowerCase().contains("user click"));
        assertTrue("after-previous semantics missing (expected 'completes' or 'automatic'):\n" + out,
            out.toLowerCase().contains("completes") || out.toLowerCase().contains("automatic"));
    }
}
