package com.excudo.core.commands.mutating.notes;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for adding speaker notes to a slide.
 *
 * Creates or updates notes on the specified slide, handling all OOXML
 * plumbing: notes slide XML, relationships, and content type registration.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code add-notes} derives from the class name.
 */
public class AddNotesCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").required().build();
    static final Parameter<String> TEXT = Parameter.ofString("text")
        .description("Notes text content").required().variableLength(true).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Add speaker notes to a slide")
        .parameter(SLIDE)
        .parameter(TEXT)
        .example("add-notes 1 \"Speaker notes for slide 1\"")
        .example("add-notes --slide 2 --text \"Key talking points for this slide\"")
        .build();

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new AddNotesCommand(p.get(SLIDE), p.get(TEXT), ctx.orchestrator());
    }

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
