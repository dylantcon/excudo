package com.excudo.core.commands.meta;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionContext;
import com.excudo.core.commands.CommandSessionManager;
import com.excudo.core.commands.SessionResult;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.PresentationMetadata;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;
import java.io.File;

/**
 * GoF Command for loading a PowerPoint presentation file.
 *
 * Handles both session creation (if needed) and file loading using composition.
 * If no session exists, delegates to SessionCreateCommand first.
 * If session exists, loads directly into current orchestrator.
 * Does not support undo since it changes system state fundamentally.
 *
 * <p>Self-registers via {@link CommandClassRegistry}: canonical name
 * {@code load} derives from the class.
 */
public class LoadCommand implements Command {

    static final Parameter<String> FILENAME = Parameter.ofString("filename")
        .description("PPTX file to load").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Load an existing PowerPoint presentation from disk")
        .llmEnabled(true)
        .llmDescription("DESTRUCTIVE: replaces the current in-memory presentation "
            + "with the file at the given path. Any unsaved work is lost. Use when "
            + "asked to edit an existing deck rather than create a new one.")
        .parameter(FILENAME)
        .example("load presentation.pptx")
        .example("load /path/to/file.pptx")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(LoadCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new LoadCommand(ctx.requireSessionManager(), ctx.requireSession(),
            ctx.requireDisplay(), p.get(FILENAME));
    }
    
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
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME, 
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