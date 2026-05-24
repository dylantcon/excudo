package com.excudo.core.commands.meta;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionContext;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import java.io.File;

/**
 * GoF Command for saving a PowerPoint presentation file.
 * 
 * This command contains the actual save logic extracted from AbstractConsoleEngine.
 * Handles file validation and saving without circular dependencies.
 * Does not support undo since it changes filesystem state.
 */
public class SaveCommand implements Command {
    
    private final CommandSessionContext sessionContext;
    private final CommandDisplay display;
    private final String filename; // null to use current filename
    private boolean executed = false;
    
    /**
     * Create a SaveCommand.
     * 
     * @param sessionContext the current session context
     * @param display the console display interface
     * @param filename the file to save to (null to use current filename)
     */
    public SaveCommand(CommandSessionContext sessionContext, CommandDisplay display, String filename) {
        if (sessionContext == null) {
            throw new IllegalArgumentException("CommandSessionContext cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.sessionContext = sessionContext;
        this.display = display;
        this.filename = filename != null ? filename.trim() : null;
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
            
            // Determine target file
            File targetFile;
            if (filename != null && !filename.isEmpty()) {
                targetFile = new File(filename);
                if (!filename.toLowerCase().endsWith(".pptx")) {
                    display.displayError("File must be a PowerPoint (.pptx) file: " + filename);
                    executed = true;
                    return;
                }
            } else {
                targetFile = sessionContext.getCurrentFile();
                if (targetFile == null) {
                    display.displayError("No filename specified and no current file. Use 'save <filename>' instead.");
                    executed = true;
                    return;
                }
            }
            
            ExecutionResult<File> result = orchestrator.savePresentation(targetFile);

            if (result.isSuccess()) {
                // Agents driving us over MCP are stateless w.r.t. the server's
                // filesystem and need three things to verify the write landed:
                // where the file is, how big it is, and whether the path they
                // passed matches where it actually resolved. Echo all three.
                long bytes = targetFile.length();
                String abs = targetFile.getAbsolutePath();
                String msg;
                if (filename != null && !filename.isEmpty() && !filename.equals(abs)) {
                    msg = "Saved to " + filename
                        + " (" + bytes + " bytes, resolved to " + abs + ")";
                } else {
                    msg = "Saved to " + abs + " (" + bytes + " bytes)";
                }
                display.displaySuccess(msg);
                sessionContext.setCurrentFile(targetFile);
                CommandInvoker invoker = sessionContext.getCurrentCommandInvoker();
                if (invoker != null) {
                    invoker.markSavePoint();
                }
            } else {
                display.displayError("Failed to save: " + result.getMessage());
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to save presentation: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME, 
            "SaveCommand cannot be undone - it changes filesystem state");
    }
    
    @Override
    public boolean canUndo() {
        return false; // Filesystem operation - cannot be undone
    }
    
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    @Override
    public String getDescription() {
        return filename != null ? "Save presentation to: " + filename : "Save presentation";
    }
}