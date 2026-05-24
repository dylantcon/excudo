package com.excudo.core.commands.readonly;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for dumping shape XML structures with slide range support.
 * 
 * This command replicates the original dumpShape() functionality exactly,
 * supporting slide ranges ("1", "1-5", "all") with comprehensive file logging
 * and analysis. Does not support undo since it performs no mutations.
 */
public class DumpShapeCommand implements Command {
    
    private final CommandDisplay display;
    private final PPTXOrchestrator orchestrator;
    private final String slideRange;
    private final boolean writeToFile;
    private boolean executed = false;
    
    
    /**
     * Create a DumpShapeCommand with slide range support (original functionality).
     * 
     * @param display the console display interface
     * @param orchestrator the orchestrator for bulk shape dumping
     * @param slideRange slide specification: "1", "1-5", "all"
     * @param writeToFile whether to write to timestamped log files (true preserves original behavior)
     */
    public DumpShapeCommand(CommandDisplay display, PPTXOrchestrator orchestrator, 
                           String slideRange, boolean writeToFile) {
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideRange == null || slideRange.trim().isEmpty()) {
            throw new IllegalArgumentException("Slide range cannot be null or empty");
        }
        this.display = display;
        this.orchestrator = orchestrator;
        this.slideRange = slideRange.trim();
        this.writeToFile = writeToFile;
    }
    
    /**
     * Create a DumpShapeCommand for backward compatibility (single slide/SPID).
     * This constructor provides compatibility with existing code while using bulk functionality.
     * 
     * @param display the console display interface
     * @param orchestrator the orchestrator for shape dumping
     * @param slideNumber the slide number (1-based)
     * @param spid the shape SPID (currently ignored, dumps all shapes on slide)
     */
    public DumpShapeCommand(CommandDisplay display, PPTXOrchestrator orchestrator, 
                           int slideNumber, int spid) {
        this(display, orchestrator, String.valueOf(slideNumber), false);
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Use the bulk functionality (preserves original behavior with file logging by default)
            ExecutionResult<String> result = orchestrator.dumpShapesBulk(slideRange, writeToFile);
            
            if (result.isSuccess() && result.getData().isPresent()) {
                String output = result.getData().get();
                
                // Display the complete output (includes file paths if writeToFile=true)
                String[] lines = output.split("\n");
                for (String line : lines) {
                    display.displayMessage(line);
                }
            } else {
                display.displayError("Failed to dump shapes: " + result.getMessage());
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to dump shapes: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME, 
            "Cannot undo a read-only dump operation");
    }
    
    @Override
    public boolean canUndo() {
        return false; // Read-only operation cannot be undone
    }
    
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    @Override
    public String getDescription() {
        return "Dump Shapes (" + slideRange + (writeToFile ? ", with file logging)" : ", console only)");
    }
}