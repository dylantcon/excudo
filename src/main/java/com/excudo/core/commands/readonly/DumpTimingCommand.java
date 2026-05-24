package com.excudo.core.commands.readonly;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for dumping timing XML structures with slide range support.
 * 
 * This command replicates the original dumpTiming() functionality exactly,
 * supporting slide ranges ("1", "1-5", "all") with comprehensive file logging
 * and analysis. Does not support undo since it performs no mutations.
 */
public class DumpTimingCommand implements Command {

    static final Parameter<String> RANGE = Parameter.ofString("range")
        .description("Slide range: single number (1), range (1-5), or 'all'")
        .defaultValue("1").build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Dump timing tree for a slide range")
        .parameter(RANGE)
        .example("dump-timing 1")
        .example("dump-timing 1-5")
        .example("dump-timing all")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(DumpTimingCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new DumpTimingCommand(ctx.requireDisplay(), ctx.orchestrator(), p.get(RANGE), true);
    }

    
    private final CommandDisplay display;
    private final PPTXOrchestrator orchestrator;
    private final String slideRange;
    private final boolean writeToFile;
    private boolean executed = false;
    
    
    /**
     * Create a DumpTimingCommand with slide range support (original functionality).
     * 
     * @param display the console display interface
     * @param orchestrator the orchestrator for bulk timing dumping
     * @param slideRange slide specification: "1", "1-5", "all"
     * @param writeToFile whether to write to timestamped log files (true preserves original behavior)
     */
    public DumpTimingCommand(CommandDisplay display, PPTXOrchestrator orchestrator, 
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
     * Create a DumpTimingCommand for backward compatibility (single slide).
     * This constructor provides compatibility with existing code while using bulk functionality.
     * 
     * @param display the console display interface
     * @param orchestrator the orchestrator for timing dumping
     * @param slideNumber the slide number (1-based)
     */
    public DumpTimingCommand(CommandDisplay display, PPTXOrchestrator orchestrator, int slideNumber) {
        this(display, orchestrator, String.valueOf(slideNumber), false);
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Use the bulk functionality (preserves original behavior with file logging by default)
            ExecutionResult<String> result = orchestrator.dumpTimingsBulk(slideRange, writeToFile);
            
            if (result.isSuccess() && result.getData().isPresent()) {
                String output = result.getData().get();
                
                // Display the complete output (includes file paths if writeToFile=true)
                String[] lines = output.split("\n");
                for (String line : lines) {
                    display.displayMessage(line);
                }
            } else {
                display.displayError("Failed to dump timing structures: " + result.getMessage());
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to dump timing structures: " + e.getMessage(), e);
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
        return "Dump Timing XML (" + slideRange + (writeToFile ? ", with file logging)" : ", console only)");
    }
}