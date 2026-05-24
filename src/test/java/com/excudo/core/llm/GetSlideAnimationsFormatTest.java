package com.excudo.core.llm;

import com.excudo.core.commands.mutating.slide.AddShapeCommand;
import com.excudo.core.commands.mutating.slide.AddAnimationCommand;
import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.parsing.CommandParameters;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Pins the {@code get_slide_animations} output format. The 2026-04-22
 * beta sessions flagged that the LLM couldn't tell {@code on-click} from
 * {@code with-previous} from a read because the trigger column was
 * missing and a duplicate type column was filling the slot.
 *
 * <p>New format: {@code Target SPID <n> | <type> | <trigger> | <duration>ms}.
 * Batch shape ({@code slideNumbers}) mirrors {@code get_slide_shapes}.
 */
public class GetSlideAnimationsFormatTest {

    private PPTXOrchestratorImpl orchestrator;
    private ToolDispatcher dispatcher;
    private int rectSpid1;
    private int rectSpid2;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Anim", "slideLayout2");
        CommandFactory cf = new CommandFactory(orchestrator);
        dispatcher = new ToolDispatcher(orchestrator, cf, new CommandInvoker());

        rectSpid1 = addRect(1, "AnimTarget1");
        rectSpid2 = addRect(1, "AnimTarget2");

        // Two animations: one on-click, one with-previous, so we can
        // confirm the trigger column distinguishes them.
        addAnimation(1, rectSpid1, "fade", "on-click");
        addAnimation(1, rectSpid2, "fade", "with-previous");
    }

    @Test
    public void outputIncludesTriggerColumnAndDurationSuffix() {
        String out = dispatcher.dispatch("get_slide_animations", "{\"slideNumber\":1}");
        assertTrue("Output should mention the on-click trigger: " + out,
            out.contains("on-click"));
        assertTrue("Output should mention the with-previous trigger: " + out,
            out.contains("with-previous"));
        assertTrue("Duration must carry an explicit ms suffix: " + out,
            out.contains("ms"));
    }

    @Test
    public void outputDoesNotDuplicateAnimationTypeColumn() {
        // Old format had | <enum> | <name> | (e.g. "FADE | Fade |") --
        // both columns derived from the same animation type. New format
        // replaces the second with the trigger.
        String out = dispatcher.dispatch("get_slide_animations", "{\"slideNumber\":1}");
        // The literal "Fade |" (titlecase enum-name display) shouldn't appear
        // immediately after "FADE |" anymore.
        assertFalse("Duplicate type column must be gone: " + out,
            out.contains("FADE | Fade"));
    }

    @Test
    public void batchSlideNumbersHonored() {
        // Re-create a second slide with no animations and ensure the
        // batch query renders a section per slide.
        try { orchestrator.createSlide(2, "Empty", "slideLayout2"); } catch (Exception ignored) {}
        String out = dispatcher.dispatch("get_slide_animations", "{\"slideNumbers\":[1,2]}");
        assertTrue(out, out.contains("Animations on slide 1:"));
        assertTrue(out, out.contains("Animations on slide 2:"));
        assertTrue("Slide 2 (no animations) should render an explicit (none): " + out,
            out.contains("(none)"));
    }

    private int addRect(int slide, String text) {
        com.excudo.core.commands.Command cmd =
            com.excudo.core.commands.CommandClassRegistry.createFromParameters(
                new CommandParameters(AddShapeCommand.NAME, Map.of(
                    "slide", String.valueOf(slide),
                    "shape-type", "RECTANGLE",
                    "text", text,
                    "x", "1000000", "y", "1000000",
                    "width", "2000000", "height", "1000000")),
                new com.excudo.core.commands.CommandContext(orchestrator, null));
        cmd.execute();
        // The command's createdSpid is on the AddShapeCommand instance;
        // we don't have a typed reference, so look it up by text.
        return orchestrator.getSlideData(slide).getData().orElseThrow()
            .getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getTextContent() != null && s.getTextContent().contains(text))
            .findFirst().orElseThrow().getSpid();
    }

    private void addAnimation(int slide, int spid, String type, String trigger) {
        var cmdParams = CommandParameters.builder(AddAnimationCommand.NAME)
            .addParam("slide", slide)
            .addParam("spid", spid)
            .addParam("type", type)
            .addParam("trigger", trigger)
            .build();
        var cf = new CommandFactory(orchestrator);
        com.excudo.core.commands.Command cmd = cf.createCommand(cmdParams, null);
        cmd.execute();
    }
}
