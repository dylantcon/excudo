package com.excudo.core.commands;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests CompositeCommand batching and atomic undo/redo.
 */
public class CompositeCommandTest {

    @Test
    public void executesAllSubCommands() {
        List<String> log = new ArrayList<>();
        CompositeCommand composite = new CompositeCommand(
            List.of(cmd(log, "A"), cmd(log, "B"), cmd(log, "C")),
            "batch op");

        composite.execute();
        assertEquals(List.of("exec:A", "exec:B", "exec:C"), log);
    }

    @Test
    public void undoRevertsInReverseOrder() {
        List<String> log = new ArrayList<>();
        CompositeCommand composite = new CompositeCommand(
            List.of(cmd(log, "A"), cmd(log, "B"), cmd(log, "C")),
            "batch op");

        composite.execute();
        log.clear();
        composite.undo();
        assertEquals(List.of("undo:C", "undo:B", "undo:A"), log);
    }

    @Test
    public void descriptionPreserved() {
        CompositeCommand composite = new CompositeCommand(
            List.of(cmd(new ArrayList<>(), "x")),
            "Add 3 shapes");
        assertEquals("Add 3 shapes", composite.getDescription());
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyCompositeThrows() {
        new CompositeCommand(List.of(), "empty");
    }

    @Test
    public void singleSubCommand() {
        List<String> log = new ArrayList<>();
        CompositeCommand composite = new CompositeCommand(
            List.of(cmd(log, "only")),
            "single");

        composite.execute();
        assertEquals(1, log.size());
        composite.undo();
        assertEquals(2, log.size());
        assertEquals("undo:only", log.get(1));
    }

    private Command cmd(List<String> log, String id) {
        return new Command() {
            private boolean executed = false;
            @Override public void execute() { log.add("exec:" + id); executed = true; }
            @Override public void undo() { log.add("undo:" + id); executed = false; }
            @Override public boolean canUndo() { return executed; }
            @Override public String getDescription() { return "cmd:" + id; }
            @Override public boolean isExecuted() { return executed; }
        };
    }
}
