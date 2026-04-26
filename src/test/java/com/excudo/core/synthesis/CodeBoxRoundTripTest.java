package com.excudo.core.synthesis;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.llm.ToolDispatcher;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.synthesis.spec.CommandSpec;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins Tier 4 D1/D2/D3: a code box authored via {@code create_code_box}
 * round-trips through {@code synthesize_slide_script} as a single
 * {@link CommandSpec.CreateCodeBoxSpec}, NOT as
 * AddShapeSpec × 2 + CreateGroupSpec + SetTextSpec × N.
 *
 * <p>Without the compound-primitive marker, editing the highlighted code
 * would force the agent to hand-rebuild the run tree or lose syntax
 * coloring entirely; the marker lets the synthesizer recognise the
 * compound and emit the high-level spec straight from the source code.
 */
public class CodeBoxRoundTripTest {

    private PPTXOrchestratorImpl orchestrator;
    private ToolDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "CodeBox", "slideLayout7");
        CommandFactory cf = new CommandFactory(orchestrator);
        dispatcher = new ToolDispatcher(orchestrator, cf, new CommandInvoker());
    }

    @Test
    public void groupCarriesCompoundMarkerAfterCreate() {
        String result = dispatcher.dispatch("create_code_box",
            "{\"slideNumber\":1,\"code\":\"int a = 1;\\nint b = 2;\",\"language\":\"java\"}");
        assertTrue(result, result.startsWith("Created code box"));

        SlideShape group = orchestrator.getSlideData(1).getData().orElseThrow()
            .getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.GROUP)
            .findFirst().orElseThrow();
        assertTrue("Group's name must carry the compound-primitive marker: "
                + group.getName(),
            group.getName() != null
                && group.getName().startsWith("excudo:code_box_v1:"));
        assertTrue("Marker must encode the language for round-trip re-tokenization: "
                + group.getName(),
            group.getName().contains("java"));
    }

    @Test
    public void synthesizerEmitsCreateCodeBoxSpecNotDecomposition() {
        dispatcher.dispatch("create_code_box",
            "{\"slideNumber\":1,\"code\":\"public class Hello {\\n    void f() {}\\n}\","
                + "\"language\":\"java\"}");

        // Diff slide 1 against the baseline (placeholders only) so every
        // user-authored shape is "added" -- exactly the input the
        // synthesizer sees on a fresh build.
        SlideStateBuilder builder = new SlideStateBuilder(orchestrator);
        SlideState current = builder.current(1);
        SlideState baseline = builder.baseline(1);
        var diff = SlideStateDiffer.diff(baseline, current);

        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, 1);
        List<CommandSpec> specs = result.script().topologicalOrder();

        // Exactly one CreateCodeBoxSpec, no AddShapeSpec / SetTextSpec /
        // CreateGroupSpec for the panels.
        long createSpecs = specs.stream()
            .filter(s -> s instanceof CommandSpec.CreateCodeBoxSpec).count();
        assertEquals("Synthesizer must emit a single CreateCodeBoxSpec: " + specs,
            1, createSpecs);

        long addShapeSpecs = specs.stream()
            .filter(s -> s instanceof CommandSpec.AddShapeSpec).count();
        long createGroupSpecs = specs.stream()
            .filter(s -> s instanceof CommandSpec.CreateGroupSpec).count();
        assertEquals("AddShapeSpec must be consumed by the compound emit: " + specs,
            0, addShapeSpecs);
        assertEquals("CreateGroupSpec must be consumed by the compound emit: " + specs,
            0, createGroupSpecs);

        // The code on the spec round-trips the original source.
        CommandSpec.CreateCodeBoxSpec spec = (CommandSpec.CreateCodeBoxSpec) specs.stream()
            .filter(s -> s instanceof CommandSpec.CreateCodeBoxSpec)
            .findFirst().orElseThrow();
        assertEquals("Language preserved", "java", spec.language());
        assertTrue("Code recovered from runs: " + spec.code(),
            spec.code().contains("public class Hello"));
        assertTrue("Newlines between paragraphs preserved: " + spec.code(),
            spec.code().contains("\n"));
    }

    @Test
    public void scriptRunnerReinstantiatesCodeBoxOnFreshSlide() throws Exception {
        // Source slide carries a code box.
        dispatcher.dispatch("create_code_box",
            "{\"slideNumber\":1,\"code\":\"x = 1\\ny = 2\",\"language\":\"python\"}");

        // Synthesize the script targeting slide 2 directly. The slide
        // number on each spec is the SOURCE slide; per-spec retarget
        // rewrites it to the target. Easier than retargeting an entire
        // CommandScript graph by hand.
        SlideStateBuilder builder = new SlideStateBuilder(orchestrator);
        var diff = SlideStateDiffer.diff(builder.baseline(1), builder.current(1));
        var script = ScriptSynthesizer.synthesize(diff, 1).script();

        orchestrator.createSlide(2, "Target", "slideLayout7");

        // Build a fresh script with every spec retargeted to slide 2.
        var retargeted = com.excudo.core.synthesis.CommandScript.builder();
        for (CommandSpec spec : script.topologicalOrder()) {
            retargeted.add(RetargetToSlide.retarget(spec, 2));
        }

        var runner = new ScriptRunner(orchestrator);
        var runResult = runner.run(retargeted.build(), "round-trip code box");
        assertTrue("Runner must succeed: " + runResult.getMessage(),
            runResult.isSuccess());

        // Slide 2 now has its own group tagged as a code box.
        SlideShape group2 = orchestrator.getSlideData(2).getData().orElseThrow()
            .getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.GROUP)
            .findFirst().orElseThrow();
        assertTrue("Reinstantiated group carries the same marker: " + group2.getName(),
            group2.getName() != null
                && group2.getName().startsWith("excudo:code_box_v1:python"));
    }
}
