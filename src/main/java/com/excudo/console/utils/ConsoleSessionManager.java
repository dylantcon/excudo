package com.excudo.console.utils;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.core.orchestration.PresentationMetadata;
import com.excudo.core.results.ExecutionResult;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.console.LLMConsoleHandler;

import java.io.File;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Utility class for console session management operations.
 * Centralizes session creation, listing, switching, and closing logic
 * to eliminate DRY violations in AbstractConsoleEngine.
 */
public class ConsoleSessionManager {
    
    private final SessionManager sessionManager;
    
    // Callbacks for console interaction
    private final Consumer<String> displayMessage;
    private final Consumer<String> displayError;
    private final Consumer<String> displaySuccess;
    
    /**
     * Create a ConsoleSessionManager.
     * 
     * @param sessionManager the underlying session manager
     * @param displayMessage callback for displaying messages
     * @param displayError callback for displaying errors
     * @param displaySuccess callback for displaying success messages
     */
    public ConsoleSessionManager(SessionManager sessionManager,
                                Consumer<String> displayMessage,
                                Consumer<String> displayError,
                                Consumer<String> displaySuccess) {
        if (sessionManager == null) {
            throw new IllegalArgumentException("SessionManager cannot be null");
        }
        if (displayMessage == null || displayError == null || displaySuccess == null) {
            throw new IllegalArgumentException("Display callbacks cannot be null");
        }
        
        this.sessionManager = sessionManager;
        this.displayMessage = displayMessage;
        this.displayError = displayError;
        this.displaySuccess = displaySuccess;
    }
    
    /**
     * Session creation result containing session information.
     */
    public static class SessionCreationResult {
        private final boolean success;
        private final String sessionId;
        private final PPTXOrchestrator orchestrator;
        private final LLMConsoleHandler llmHandler;
        private final File currentFile;
        private final String errorMessage;
        
        private SessionCreationResult(boolean success, String sessionId, PPTXOrchestrator orchestrator,
                                     LLMConsoleHandler llmHandler, File currentFile, String errorMessage) {
            this.success = success;
            this.sessionId = sessionId;
            this.orchestrator = orchestrator;
            this.llmHandler = llmHandler;
            this.currentFile = currentFile;
            this.errorMessage = errorMessage;
        }
        
        public static SessionCreationResult success(String sessionId, PPTXOrchestrator orchestrator,
                                                   LLMConsoleHandler llmHandler, File currentFile) {
            return new SessionCreationResult(true, sessionId, orchestrator, llmHandler, currentFile, null);
        }
        
        public static SessionCreationResult failure(String errorMessage) {
            return new SessionCreationResult(false, null, null, null, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public String getSessionId() { return sessionId; }
        public PPTXOrchestrator getOrchestrator() { return orchestrator; }
        public LLMConsoleHandler getLlmHandler() { return llmHandler; }
        public File getCurrentFile() { return currentFile; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Session switch result containing session information.
     */
    public static class SessionSwitchResult {
        private final boolean success;
        private final String sessionId;
        private final PPTXOrchestrator orchestrator;
        private final LLMConsoleHandler llmHandler;
        private final String errorMessage;
        
        private SessionSwitchResult(boolean success, String sessionId, PPTXOrchestrator orchestrator,
                                   LLMConsoleHandler llmHandler, String errorMessage) {
            this.success = success;
            this.sessionId = sessionId;
            this.orchestrator = orchestrator;
            this.llmHandler = llmHandler;
            this.errorMessage = errorMessage;
        }
        
        public static SessionSwitchResult success(String sessionId, PPTXOrchestrator orchestrator,
                                                 LLMConsoleHandler llmHandler) {
            return new SessionSwitchResult(true, sessionId, orchestrator, llmHandler, null);
        }
        
        public static SessionSwitchResult failure(String errorMessage) {
            return new SessionSwitchResult(false, null, null, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public String getSessionId() { return sessionId; }
        public PPTXOrchestrator getOrchestrator() { return orchestrator; }
        public LLMConsoleHandler getLlmHandler() { return llmHandler; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Create a new session with a presentation file.
     * 
     * @param filename the presentation file path
     * @return SessionCreationResult with session details or error
     */
    public SessionCreationResult createSession(String filename) {
        try {
            File file = new File(filename);
            if (!file.exists()) {
                return SessionCreationResult.failure("File not found: " + filename);
            }
            
            String sessionId = sessionManager.createNewSession();
            Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(sessionId);

            if (sessionOpt.isPresent()) {
                SessionManager.ManagedSession session = sessionOpt.get();
                ExecutionResult<PresentationMetadata> result = session.getOrchestrator().loadPresentation(file);

                if (result.isSuccess()) {
                    PPTXOrchestrator orchestrator = session.getOrchestrator();
                    LLMConsoleHandler llmHandler = new LLMConsoleHandler(orchestrator);

                    return SessionCreationResult.success(sessionId, orchestrator, llmHandler, file);
                } else {
                    sessionManager.closeSession(sessionId);
                    return SessionCreationResult.failure("Failed to load presentation in session: " + result.getMessage());
                }
            } else {
                return SessionCreationResult.failure("Failed to create session");
            }
        } catch (XMLParsingException e) {
            return SessionCreationResult.failure("Session creation failed: " + e.getMessage());
        }
    }
    
    /**
     * Create an empty session without loading a presentation.
     *
     * @return SessionCreationResult with session details or error
     */
    public SessionCreationResult createEmptySession() {
        try {
            String sessionId = sessionManager.createNewSession();

            Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(sessionId);
            if (sessionOpt.isPresent()) {
                PPTXOrchestrator orchestrator = sessionOpt.get().getOrchestrator();
                LLMConsoleHandler llmHandler = new LLMConsoleHandler(orchestrator);

                return SessionCreationResult.success(sessionId, orchestrator, llmHandler, null);
            } else {
                return SessionCreationResult.failure("Failed to create empty session");
            }
        } catch (XMLParsingException e) {
            return SessionCreationResult.failure("Session creation failed: " + e.getMessage());
        }
    }

    /**
     * Register an existing orchestrator as a session.
     * Returns the session ID, or null on failure.
     */
    public String registerExistingOrchestrator(PPTXOrchestrator orchestrator) {
        if (orchestrator instanceof PPTXOrchestratorImpl impl) {
            return sessionManager.registerSession(impl);
        }
        return null;
    }
    
    /**
     * List all active sessions with current session marking.
     * 
     * @param currentSessionId the currently active session ID (can be null)
     */
    public void listSessions(String currentSessionId) {
        Set<String> sessionIds = sessionManager.getActiveSessionIds();
        
        displayMessage.accept("");
        displayMessage.accept("Active Sessions (" + sessionIds.size() + " total):");
        
        if (sessionIds.isEmpty()) {
            displayMessage.accept("  No active sessions");
        } else {
            for (String sessionId : sessionIds) {
                String marker = sessionId.equals(currentSessionId) ? " [CURRENT]" : "";
                displayMessage.accept("  " + sessionId + marker);
            }
        }
        
        if (currentSessionId != null) {
            displayMessage.accept("");
            displayMessage.accept("Current session: " + currentSessionId);
        }
    }
    
    /**
     * Show information about a specific session.
     * 
     * @param sessionId the session ID to show info for
     */
    public void showSessionInfo(String sessionId) {
        Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(sessionId);
        
        if (sessionOpt.isEmpty()) {
            displayError.accept("Session not found: " + sessionId);
            return;
        }
        
        SessionManager.ManagedSession session = sessionOpt.get();
        
        displayMessage.accept("");
        displayMessage.accept("=== Session Information ===");
        displayMessage.accept("Session ID: " + sessionId);
        displayMessage.accept("Last Accessed: " + new java.util.Date(session.getLastAccessed()));
        
        if (!session.getOrchestrator().getContext().isEmpty()) {
            try {
                var metadata = session.getOrchestrator().getPresentationMetadata();
                displayMessage.accept("Loaded Presentation:");
                displayMessage.accept("  Title: " + metadata.getTitle());
                displayMessage.accept("  Slides: " + metadata.getSlideCount());
            } catch (Exception e) {
                displayMessage.accept("Presentation: Error getting metadata - " + e.getMessage());
            }
        } else {
            displayMessage.accept("Presentation: None loaded");
        }
    }
    
    /**
     * Close a session and handle cleanup.
     * 
     * @param sessionId the session ID to close
     * @param currentSessionId the currently active session ID
     * @return true if session was closed successfully
     */
    public boolean closeSession(String sessionId, String currentSessionId) {
        boolean closed = sessionManager.closeSession(sessionId);
        if (closed) {
            displaySuccess.accept("Session closed: " + sessionId);
            if (sessionId.equals(currentSessionId)) {
                displayMessage.accept("Current session cleared - use 'session create' or 'session switch' to work with a presentation");
            }
        } else {
            displayError.accept("Failed to close session: " + sessionId);
        }
        return closed;
    }
    
    /**
     * Switch to a different session.
     * 
     * @param sessionId the session ID to switch to
     * @return SessionSwitchResult with session details or error
     */
    public SessionSwitchResult switchSession(String sessionId) {
        Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(sessionId);
        
        if (sessionOpt.isEmpty()) {
            return SessionSwitchResult.failure("Session not found: " + sessionId);
        }
        
        SessionManager.ManagedSession session = sessionOpt.get();
        PPTXOrchestrator orchestrator = session.getOrchestrator();
        LLMConsoleHandler llmHandler = new LLMConsoleHandler(orchestrator);
        
        // Check if presentation is loaded and provide appropriate feedback
        if (!orchestrator.getContext().isEmpty()) {
            try {
                var metadata = orchestrator.getPresentationMetadata();
                displaySuccess.accept("Switched to session: " + sessionId);
                displayMessage.accept("  Loaded presentation: " + metadata.getTitle());
                displayMessage.accept("  Slides: " + metadata.getSlideCount());
            } catch (Exception e) {
                displaySuccess.accept("Switched to session: " + sessionId);
                displayMessage.accept("  Presentation loaded but metadata unavailable");
            }
        } else {
            displaySuccess.accept("Switched to session: " + sessionId);
            displayMessage.accept("  No presentation loaded - use 'load <file>' to load one");
        }
        
        return SessionSwitchResult.success(sessionId, orchestrator, llmHandler);
    }
    
    /**
     * Get the underlying SessionManager instance.
     * 
     * @return the SessionManager
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }
}