package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for duplicating a shape within the same slide.
 *
 * The duplicate is placed at an offset from the original. On undo, the newly
 * created shape is removed by its allocated SPID. The original is left untouched.
 *
 * Default offset is 457200 EMU (0.5 inch) in both axes, matching PowerPoint's
 * native Ctrl+D behaviour.
 */
public class DuplicateShapeCommand implements Command {

    /** Default offset for duplicated shape: 0.5 inch in EMU. */
    public static final long DEFAULT_OFFSET_EMU = 457200L;

    private final int slideNumber;
    private final int spid;
    private final long offsetX;
    private final long offsetY;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private Integer newSpid = null;

    /**
     * Duplicate with default 0.5-inch offset on both axes.
     *
     * @param slideNumber  Slide number (1-based)
     * @param spid         SPID of the shape to duplicate
     * @param orchestrator PPTXOrchestrator instance
     */
    public DuplicateShapeCommand(int slideNumber, int spid, PPTXOrchestrator orchestrator) {
        this(slideNumber, spid, DEFAULT_OFFSET_EMU, DEFAULT_OFFSET_EMU, orchestrator);
    }

    /**
     * Duplicate with explicit positional offset.
     *
     * @param slideNumber  Slide number (1-based)
     * @param spid         SPID of the shape to duplicate
     * @param offsetX      Horizontal offset in EMUs added to the original X
     * @param offsetY      Vertical offset in EMUs added to the original Y
     * @param orchestrator PPTXOrchestrator instance
     */
    public DuplicateShapeCommand(int slideNumber, int spid, long offsetX, long offsetY,
                                 PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        if (spid <= 0) {
            throw new IllegalArgumentException("SPID must be positive");
        }
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }

        ExecutionResult<Integer> result =
            orchestrator.duplicateShape(slideNumber, spid, offsetX, offsetY);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to duplicate shape: " + result.getMessage());
        }

        newSpid = result.getData().orElse(null);
        if (newSpid == null) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Duplicate succeeded but no SPID was returned");
        }

        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Command has not been executed");
        }
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "No duplicated SPID available for undo");
        }

        ExecutionResult<Void> removeResult = orchestrator.removeShape(slideNumber, newSpid);
        if (!removeResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Failed to remove duplicated shape SPID " + newSpid + ": " + removeResult.getMessage());
        }

        executed = false;
        newSpid = null;
    }

    @Override
    public boolean canUndo() {
        return executed && newSpid != null;
    }

    @Override
    public String getDescription() {
        return String.format("Duplicate shape SPID %d on slide %d (offset %d, %d EMU)",
            spid, slideNumber, offsetX, offsetY);
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
    public Integer getNewSpid() { return newSpid; }
    public long getOffsetX() { return offsetX; }
    public long getOffsetY() { return offsetY; }
}
