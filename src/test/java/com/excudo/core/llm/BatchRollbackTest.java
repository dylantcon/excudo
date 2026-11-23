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

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * A batch applies as a whole. It used to run every command it could, so one
 * failure left the slide half-done ("39 OK, 1 failed") with no word on which
 * commands had run.
 */
public class BatchRollbackTest {

    private static final String BAD_FONT = "{\"type\":\"set-font\",\"slideNumber\":1,\"targetSpid\":999,\"size\":20}";

    private PPTXOrchestratorImpl orchestrator;
    private CommandInvoker invoker;
    private ToolDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(PresentationScaffolder.scaffoldDocument("excudo"));
        orchestrator.createSlide(1, "One", "slideLayout7");
        invoker = new CommandInvoker();
        dispatcher = new ToolDispatcher(orchestrator, new CommandFactory(orchestrator), invoker);
    }

    @Test
    public void aFailureUndoesTheCommandsBeforeItAndSkipsTheRest() {
        List<Integer> before = spids();
        String out = run("[" + shape("A") + "," + shape("B") + "," + BAD_FONT + "," + shape("C") + "]");

        assertEquals("slide as before the batch", before, spids());
        assertTrue(out, out.startsWith("FAILED at #3 of 4: set-font"));
        assertTrue(out, out.contains("Nothing was applied: the 2 commands before it were undone and the 1 after it wasn't run."));
        assertFalse("no ids for shapes that are gone: " + out, out.contains("created"));
        assertFalse("rolled-back commands can't be undone again", invoker.canUndo());
    }

    @Test
    public void aValidationFailureAlsoRollsBack() {
        List<Integer> before = spids();
        String out = run("[" + shape("A") + ",{\"type\":\"set-font\",\"slideNumber\":1,\"targetSpd\":3}]");
        assertEquals(before, spids());
        assertTrue(out, out.startsWith("FAILED at #2 of 2: set-font - Unknown parameter"));
        assertTrue(out, out.contains("the 1 command before it was undone."));
    }

    @Test
    public void aFailingFirstCommandRunsNothing() {
        String out = run("[" + BAD_FONT + "," + shape("A") + "," + shape("B") + "]");
        assertTrue(out, out.contains("Nothing was applied; the 2 after it weren't run."));
        assertEquals(1, spids().size()); // the slide's group root only
    }

    @Test
    public void earlierBatchesAreUntouchedAndStillUndoable() {
        assertTrue(run("[" + shape("kept") + "]").startsWith("OK"));
        List<Integer> withKept = spids();
        run("[" + shape("A") + "," + BAD_FONT + "]");
        assertEquals(withKept, spids());
        assertTrue(invoker.canUndo());
        assertTrue(invoker.undo());
        assertEquals(1, spids().size());
    }

    @Test
    public void aBatchWithACommandThatCantBeUndoneIsNotRolledBack() {
        // Only save, new-presentation and load can't be undone among the agent's
        // commands, and they need a REPL session, so this checks the invoker's
        // decision directly: such a batch is kept (and listed), not half-undone
        List<String> log = new ArrayList<>();
        Stub a = new Stub("a", true, log), b = new Stub("b", false, log), c = new Stub("c", true, log);
        CommandInvoker inv = new CommandInvoker();
        inv.executeCommand(a);
        inv.executeCommand(b);
        inv.executeCommand(c);
        assertFalse(inv.canRollBack(List.of(a, b, c)));

        CommandInvoker clean = new CommandInvoker();
        Stub d = new Stub("d", true, log), e = new Stub("e", true, log);
        clean.executeCommand(d);
        clean.executeCommand(e);
        assertTrue(clean.canRollBack(List.of(d, e)));
        clean.rollBack(List.of(d, e));
        assertEquals("newest first", List.of("undo e", "undo d"), log);
        assertFalse(clean.canUndo());
    }

    /** A command that records its undo, and may refuse one. */
    private static final class Stub implements com.excudo.core.commands.Command {
        private final String name;
        private final boolean undoable;
        private final List<String> log;
        private boolean executed;
        Stub(String name, boolean undoable, List<String> log) { this.name = name; this.undoable = undoable; this.log = log; }
        @Override public void execute() { executed = true; }
        @Override public void undo() { log.add("undo " + name); executed = false; }
        @Override public boolean canUndo() { return undoable && executed; }
        @Override public String getDescription() { return name; }
        @Override public boolean isExecuted() { return executed; }
    }

    private static String shape(String text) {
        return "{\"type\":\"add-shape\",\"slideNumber\":1,\"shapeType\":\"RECTANGLE\",\"text\":\"" + text
            + "\",\"x\":0,\"y\":0,\"width\":900000,\"height\":500000}";
    }

    private String run(String commands) {
        JsonObject input = new JsonObject();
        input.addProperty("commands", commands);
        return dispatcher.dispatch("execute_commands", input.toString());
    }

    private List<Integer> spids() {
        List<Integer> out = new ArrayList<>();
        NodeList ids = orchestrator.getContext().get().getDocument().getSlideDocument(1)
            .getElementsByTagNameNS("*", "cNvPr");
        for (int i = 0; i < ids.getLength(); i++) out.add(Integer.parseInt(((Element) ids.item(i)).getAttribute("id")));
        return out;
    }
}
