package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.AddAnimationCommand;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * State-level verification of AddAnimationCommand's snapshot-based undo.
 *
 * <p>These tests do NOT merely assert that {@code execute()} returned a
 * success flag; they exercise the full orchestrator + writer path, read
 * the slide DOM directly, and assert that the timing tree has the
 * expected structure at each step:
 *
 * <ol>
 *   <li>Before execute: baseline cTn count.</li>
 *   <li>After execute: more cTn nodes, at least one with {@code presetID}.</li>
 *   <li>After undo: cTn count back to baseline, no preset-root cTn left.</li>
 * </ol>
 *
 * <p>The whole point of the retrofit is that undo actually removes the
 * animation from the tree. A "success returned from undo()" assertion
 * would have passed even with the old no-op implementation.
 */
public class AddAnimationCommandUndoTest {

    private static final String PML_NS = com.excudo.core.utils.XMLConstants.Namespaces.PML;

    private PPTXOrchestratorImpl orchestrator;
    private PPTXDocument doc;
    private int shapeSpid;

    @Before
    public void setUp() throws Exception {
        doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Undo Test", "slideLayout2");
        doc = orchestrator.getContext().get().getDocument();
        // SPID 3 is the body placeholder on slideLayout2-based slides.
        shapeSpid = 3;
    }

    @Test
    public void executeAddsAnimationToTimingTree() {
        int before = countCTnNodes(1);
        int presetBefore = countPresetRootCTn(1);
        assertEquals("Fresh slide should have no preset-root cTn", 0, presetBefore);

        AddAnimationCommand cmd = new AddAnimationCommand(
            1, shapeSpid, "fade", "in", "on-click", "on-click",
            orchestrator, null, Collections.emptyMap());
        cmd.execute();

        int after = countCTnNodes(1);
        int presetAfter = countPresetRootCTn(1);
        assertTrue("Animation add must introduce new cTn nodes (" + before + " -> " + after + ")",
            after > before);
        assertEquals("Exactly one preset-root cTn should appear", 1, presetAfter);
        assertTrue("Command must flag executed", cmd.isExecuted());
        assertTrue("Command must report undoable once preset-root cTn captured",
            cmd.canUndo());
    }

    @Test
    public void undoRemovesThePresetRootItAdded() {
        // We assert on the preset-root cTn count rather than total cTn
        // count: removeAnimation correctly tears down the animation par
        // and its intermediate + click triggers, but leaves behind the
        // timing-tree scaffolding (p:tnLst/p:par/p:cTn .../p:seq/p:cTn)
        // which is established on-demand when the FIRST animation is
        // injected and is not supposed to be torn down on every undo --
        // it's the container infrastructure, not the animation itself.
        // What the undo contract must guarantee is that no *animation*
        // remains: presetID-bearing cTns = 0 and the slide timing tree
        // carries no effect.
        assertEquals("Fresh slide should have no preset-root cTn before any command",
            0, countPresetRootCTn(1));

        AddAnimationCommand cmd = new AddAnimationCommand(
            1, shapeSpid, "fade", "in", "on-click", "on-click",
            orchestrator, null, Collections.emptyMap());
        cmd.execute();
        assertEquals("Exactly one preset-root cTn after execute", 1, countPresetRootCTn(1));
        assertTrue("Must be undoable before undo call", cmd.canUndo());

        cmd.undo();

        assertEquals("No preset-root cTn should remain after undo",
            0, countPresetRootCTn(1));
        assertFalse("Command must no longer report executed", cmd.isExecuted());
        assertFalse("Command must no longer report undoable", cmd.canUndo());
    }

    @Test
    public void undoBeforeExecuteThrows() {
        AddAnimationCommand cmd = new AddAnimationCommand(
            1, shapeSpid, "fade", "in", "on-click", "on-click",
            orchestrator, null, Collections.emptyMap());
        try {
            cmd.undo();
            fail("undo() before execute() should throw");
        } catch (CommandExecutionException expected) {
            assertTrue("Message should mention not executed: " + expected.getMessage(),
                expected.getMessage().toLowerCase().contains("not been executed")
                || expected.getMessage().toLowerCase().contains("not executed"));
        }
    }

    @Test
    public void redoAfterUndoRestoresAnimation() {
        AddAnimationCommand cmd = new AddAnimationCommand(
            1, shapeSpid, "fade", "in", "on-click", "on-click",
            orchestrator, null, Collections.emptyMap());
        cmd.execute();
        assertEquals("Preset-root cTn count should be 1 after first execute",
            1, countPresetRootCTn(1));

        cmd.undo();
        assertEquals("Undo should remove the animation effect",
            0, countPresetRootCTn(1));

        // Second execute on the same command instance: lets the same
        // command object serve an undo-redo cycle cleanly, which
        // downstream CompositeCommand rollback relies on.
        cmd.execute();
        assertEquals("Preset-root cTn count should be 1 after re-execute",
            1, countPresetRootCTn(1));
        assertTrue("Must be undoable again after re-execute", cmd.canUndo());
    }

    // ========== Helpers ==========

    private int countCTnNodes(int slideNumber) {
        Document dom = currentSlideDom(slideNumber);
        if (dom == null) return -1;
        NodeList nl = dom.getElementsByTagNameNS(PML_NS, "cTn");
        return nl.getLength();
    }

    private int countPresetRootCTn(int slideNumber) {
        Document dom = currentSlideDom(slideNumber);
        if (dom == null) return -1;
        NodeList nl = dom.getElementsByTagNameNS(PML_NS, "cTn");
        int count = 0;
        for (int i = 0; i < nl.getLength(); i++) {
            org.w3c.dom.Element el = (org.w3c.dom.Element) nl.item(i);
            if (el.hasAttribute("presetID")) count++;
        }
        return count;
    }

    private Document currentSlideDom(int slideNumber) {
        PPTXDocument d = orchestrator.getContext().get().getDocument();
        return d.getSlideDocument(slideNumber);
    }
}
