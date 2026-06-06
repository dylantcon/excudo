package com.excudo.core.synthesis;

import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.mutating.slide.RunSlideScriptCommand;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.synthesis.spec.CommandSpec;
import com.excudo.core.synthesis.spec.CommandSpecJson;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Cross-layout retarget contract: when a synthesized script references
 * SPIDs that exist on the SOURCE slide but not the TARGET (different
 * layout, different placeholder SPID space), the runner must SKIP
 * those specs with a visible warning rather than throw and abort the
 * whole script.
 *
 * <p>This was the latent failure mode that surfaced when applying a
 * generalist-deck source slide to a fresh blank target slide: a
 * SetStyleSpec on a layout-baseline placeholder SPID that didn't exist
 * on the target aborted the apply with a low-level SPID-not-found
 * message. The runner-level skip-with-warning is the chosen mitigation
 * (plan: happy-wandering-octopus.md Phase 3); the SPID-remap-via-
 * layout-equivalence alternative is deferred.
 */
public class CrossLayoutRetargetTest {

    @Test
    public void specReferencingMissingSpid_skipsWithRuntimeWarning() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Src", "slideLayout7");
        orch.createSlide(2, "Tgt", "slideLayout7");

        // Use a SPID well above any plausible allocation on the target.
        // This simulates the real cross-layout case: a source slide with
        // a placeholder SPID baked in at synth time that doesn't appear
        // on the target's smaller-allocation slide.
        int orphanedSpid = 99_999;
        CommandSpec.MoveSpec orphanedMove =
            new CommandSpec.MoveSpec(1, orphanedSpid, 5_000_000, 5_000_000);
        CommandSpec.AddShapeSpec add = new CommandSpec.AddShapeSpec(
            1, SlideShape.ShapeType.ELLIPSE,
            new ShapeGeometry(2_000_000, 0, 1_000_000, 1_000_000), "",
            "E", null, null, false);
        String json = CommandSpecJson.toJsonArray(List.of(add, orphanedMove));

        RunSlideScriptCommand cmd = new RunSlideScriptCommand(2, json, orch, null);
        new CommandInvoker().executeCommand(cmd);

        assertTrue("Apply must not throw on cross-layout missing SPID", cmd.isExecuted());
        assertEquals("AddShape applied; Move skipped", 1, cmd.getAppliedSpecCount());
        assertEquals("Exactly one spec was skipped", 1, cmd.getSkippedSpecCount());
        assertEquals("Runtime warnings must surface the skip", 1, cmd.getRuntimeWarnings().size());
        String warning = cmd.getRuntimeWarnings().get(0);
        assertTrue("Warning must name the spec type: " + warning,
            warning.contains("MoveSpec"));
        assertTrue("Warning must name the missing SPID: " + warning,
            warning.contains(String.valueOf(orphanedSpid)));
        assertTrue("Warning must name the target slide: " + warning,
            warning.contains("slide 2"));
    }

    @Test
    public void allSpecsValid_runtimeWarningsIsEmpty() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Src", "slideLayout7");
        orch.createSlide(2, "Tgt", "slideLayout7");
        orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1_000_000, 1_000_000), "",
            "R", ShapeStyle.defaultStyle());

        SlideStateBuilder builder = new SlideStateBuilder(orch);
        SlideStateDiff diff = SlideStateDiffer.diff(builder.baseline(1), builder.current(1));
        List<CommandSpec> specs = ScriptSynthesizer.synthesize(diff, 1)
            .script().topologicalOrder();
        String json = CommandSpecJson.toJsonArray(specs);
        RunSlideScriptCommand cmd = new RunSlideScriptCommand(2, json, orch, null);
        new CommandInvoker().executeCommand(cmd);

        assertEquals("No specs skipped on a clean same-layout apply",
            0, cmd.getSkippedSpecCount());
        assertTrue("Runtime warnings must be empty: " + cmd.getRuntimeWarnings(),
            cmd.getRuntimeWarnings().isEmpty());
    }

    @Test
    public void connectorWithMissingEndpoint_skipsWithWarning() {
        // Connector references endpoint SPID 99999 (nonexistent on target).
        // Runner must skip with warning, not throw.
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Src", "slideLayout7");
        orch.createSlide(2, "Tgt", "slideLayout7");

        CommandSpec.AddConnectorSpec orphanedConn = new CommandSpec.AddConnectorSpec(
            1, "straight", new ShapeGeometry(0, 0, 100, 0),
            null, null, null, 99_999, 1, 99_998, 1, null, "Orphan", null);
        String json = CommandSpecJson.toJsonArray(List.of(orphanedConn));

        RunSlideScriptCommand cmd = new RunSlideScriptCommand(2, json, orch, null);
        new CommandInvoker().executeCommand(cmd);

        assertTrue("Apply must not throw", cmd.isExecuted());
        assertEquals(0, cmd.getAppliedSpecCount());
        assertEquals(1, cmd.getSkippedSpecCount());
        assertEquals(1, cmd.getRuntimeWarnings().size());
        String warning = cmd.getRuntimeWarnings().get(0);
        assertTrue("Warning must name the connector spec: " + warning,
            warning.contains("AddConnectorSpec"));
    }

    private static PPTXOrchestratorImpl newScaffolded() {
        try {
            PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
            PPTXOrchestratorImpl orch = new PPTXOrchestratorImpl();
            orch.initialize(doc);
            return orch;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
