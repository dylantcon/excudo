package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for adding speaker notes to a slide.
 *
 * Creates or updates notes on the specified slide, handling all OOXML
 * plumbing: notes slide XML, relationships, and content type registration.
 */
public class AddNotesCommand implements Command {

    private final int slideNumber;
    private final String notesText;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public AddNotesCommand(int slideNumber, String notesText, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        if (notesText == null || notesText.isEmpty()) throw new IllegalArgumentException("Notes text cannot be empty");
        if (slideNumber <= 0) throw new IllegalArgumentException("Slide number must be positive");
        this.slideNumber = slideNumber;
        this.notesText = notesText;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) throw new CommandExecutionException(getDescription(), "execute", "Already executed");
        try {
            ExecutionResult<Void> result = orchestrator.addNotes(slideNumber, notesText);
            if (result.isSuccess()) {
                executed = true;
            } else {
                throw new CommandExecutionException(getDescription(), "execute", "Failed: " + result.getMessage());
            }
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", e.getMessage(), e);
        }
    }

    @Override
    public void undo() {
        if (!executed) throw new CommandExecutionException(getDescription(), "undo", "Not executed");
        throw new CommandExecutionException(getDescription(), "undo", "Notes addition cannot be undone");
    }

    @Override
    public boolean canUndo() { return false; }

    @Override
    public String getDescription() {
        return String.format("Add notes to slide %d: \"%s\"", slideNumber,
            notesText.length() > 50 ? notesText.substring(0, 50) + "..." : notesText);
    }

    @Override
    public boolean isExecuted() { return executed; }

    public int getSlideNumber() { return slideNumber; }
}
