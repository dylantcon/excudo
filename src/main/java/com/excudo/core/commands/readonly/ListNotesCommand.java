package com.excudo.core.commands.readonly;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionContext;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.inspection.NotesManager;

/**
 * GoF Command for listing notes content from slides.
 * 
 * This command displays notes content for either a specific slide or all slides
 * in the presentation. Provides read-only access to slide notes without modification.
 */
public class ListNotesCommand implements Command {
    
    private final CommandSessionContext sessionContext;
    private final CommandDisplay display;
    private final Integer slideNumber; // null for all slides
    private boolean executed = false;
    
    /**
     * Create a ListNotesCommand.
     * 
     * @param sessionContext the session context for accessing orchestrator
     * @param display the console display interface
     * @param slideNumber the slide number to show notes for (null for all slides)
     */
    public ListNotesCommand(CommandSessionContext sessionContext, CommandDisplay display, Integer slideNumber) {
        if (sessionContext == null) {
            throw new IllegalArgumentException("CommandSessionContext cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.sessionContext = sessionContext;
        this.display = display;
        this.slideNumber = slideNumber;
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            PPTXOrchestrator orchestrator = sessionContext.getCurrentOrchestrator();
            if (orchestrator == null) {
                display.displayError("No active session. Use 'load' or 'session create' first.");
                executed = true;
                return;
            }
            
            // Use NotesManager to handle the notes listing (same logic as original AbstractConsoleEngine)
            NotesManager notesManager = new NotesManager(
                display::displayMessage,
                display::displayError
            );
            
            // Convert Integer slideNumber to String slideArg (as expected by NotesManager)
            String slideArg = slideNumber != null ? slideNumber.toString() : null;
            
            notesManager.handleListNotes(orchestrator, slideArg);
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to list notes: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME, 
            "ListNotesCommand is a read-only operation and cannot be undone");
    }
    
    @Override
    public boolean canUndo() {
        return false; // Read-only operation
    }
    
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    @Override
    public String getDescription() {
        return slideNumber != null ? 
            "List notes for slide " + slideNumber :
            "List notes for all slides";
    }
}