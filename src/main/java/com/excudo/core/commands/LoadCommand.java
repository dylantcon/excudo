package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.PresentationMetadata;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.core.results.ExecutionResult;
import java.io.File;

/**
 * GoF Command for loading a PowerPoint presentation file.
 * 
 * Handles both session creation (if needed) and file loading using composition.
 * If no session exists, delegates to SessionCreateCommand first.
 * If session exists, loads directly into current orchestrator.
 * Does not support undo since it changes system state fundamentally.
 */
public class LoadCommand implements Command {
    
    private final CommandSessionManager sessionManager;
    private final CommandSessionContext sessionContext;
    private final CommandDisplay display;
    private final String filename;
    private boolean executed = false;
    
    
    
    /**
     * Create a LoadCommand.
     * 
     * @param sessionManager the session manager for creating sessions if needed
     * @param sessionContext the session context for checking current state
     * @param display the console display interface
     * @param filename the presentation file to load
     */
    public LoadCommand(CommandSessionManager sessionManager, CommandSessionContext sessionContext,
                      CommandDisplay display, String filename) {
        if (sessionManager == null) {
            throw new IllegalArgumentException("CommandSessionManager cannot be null");
        }
        if (sessionContext == null) {
            throw new IllegalArgumentException("CommandSessionContext cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        this.sessionManager = sessionManager;
        this.sessionContext = sessionContext;
        this.display = display;
        this.filename = filename.trim();
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Validate file exists and is readable
            File file = new File(filename);
            if (!file.exists()) {
                display.displayError("File not found: " + filename);
                executed = true;
                return;
            }
            
            if (!file.canRead()) {
                display.displayError("Cannot read file: " + filename);
                executed = true;
                return;
            }
            
            if (!filename.toLowerCase().endsWith(".pptx")) {
                display.displayError("File must be a PowerPoint (.pptx) file: " + filename);
                executed = true;
                return;
            }
            
            // Check if we need to create a session first
            if (sessionContext.getCurrentSessionId() == null) {
                // No session exists - create one with the file
                SessionResult result = sessionManager.createSession(filename);

                if (result.isSuccess()) {
                    sessionContext.setCurrentSession(
                        result.getSessionId(),
                        result.getOrchestrator(),
                        result.getLlmHandler(),
                        result.getCurrentFile()
                    );
                    // Get slide count from orchestrator metadata
                    int slideCount = 0;
                    try {
                        var metadata = result.getOrchestrator().getPresentationMetadata();
                        slideCount = metadata.getSlideCount();
                    } catch (Exception ignored) {}
                    display.displaySuccess("Loaded " + file.getName() + " (" + slideCount + " slides)");
                } else {
                    display.displayError("Failed to load: " + result.getErrorMessage());
                }
            } else {
                // Session exists - just load the file into current orchestrator
                PPTXOrchestrator orchestrator = sessionContext.getCurrentOrchestrator();
                ExecutionResult<PresentationMetadata> result = orchestrator.loadPresentation(file);

                if (result.isSuccess()) {
                    var metadata = result.getData().orElse(null);
                    int slideCount = metadata != null ? metadata.getSlideCount() : 0;
                    display.displaySuccess("Loaded " + file.getName() + " (" + slideCount + " slides)");

                    // Update session context with new file
                    sessionContext.setCurrentSession(
                        sessionContext.getCurrentSessionId(),
                        orchestrator,
                        null, // LLM handler preserved by console layer
                        file
                    );

                    // Notify state listeners (GUI explorer/preview refresh).
                    // The createSession path already fires via SessionManager;
                    // this in-place reload must fire explicitly.
                    SessionManager.getInstance().firePresentationLoaded();
                } else {
                    display.displayError("Failed to load: " + result.getMessage());
                }
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to load presentation " + filename + ": " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo", 
            "LoadCommand cannot be undone - it changes system state completely");
    }
    
    @Override
    public boolean canUndo() {
        return false; // System operation - cannot be undone
    }
    
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    @Override
    public String getDescription() {
        return "Load presentation: " + filename;
    }
}