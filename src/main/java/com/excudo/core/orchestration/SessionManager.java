package com.excudo.core.orchestration;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.themes.ThemeManager;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.xml.writers.SPIDManager;
import com.excudo.xml.writers.RelationshipManager;
import com.excudo.core.commands.CommandInvoker;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages the lifecycle of presentation editing sessions
 * Prevents expensive re-parsing of PPTX files by keeping orchestrator objects alive
 * Supports concurrent sessions for batch processing capabilities
 */
public class SessionManager {
    
    private static final ComponentLogger logger = Logger.getLogger("SessionManager");
    private static SessionManager instance;
    private static final Object instanceLock = new Object();
    
    // Session storage
    private final Map<String, ManagedSession> activeSessions;

    // Global active-session pointer. The Session Unification refactor
    // (plan: dreamy-greeting-cosmos.md) makes THIS the sole source of
    // truth for "which session is everybody looking at right now?".
    // Engines call setActiveSession on create/switch; observers
    // (MainController, PresentationExplorerController, ToolDispatcher)
    // read via getActiveOrchestrator. Volatile because writes happen
    // from engine threads and reads from UI / dispatcher threads;
    // single-writer discipline obviates explicit locking.
    private volatile String activeSessionId;

    // State change listeners (GUI observers, etc.)
    private final List<OrchestrationStateListener> stateListeners;

    // Cleanup scheduler
    private final ScheduledExecutorService cleanupExecutor;
    private static final long SESSION_TIMEOUT_MINUTES = 30;
    private static final long CLEANUP_INTERVAL_MINUTES = 5;
    
    private SessionManager() {
        this.activeSessions = new ConcurrentHashMap<>();
        this.stateListeners = new CopyOnWriteArrayList<>();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SessionManager-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Start cleanup task
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredSessions,
            CLEANUP_INTERVAL_MINUTES,
            CLEANUP_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        
        logger.info("SessionManager initialized with {}-minute session timeout", SESSION_TIMEOUT_MINUTES);
    }
    
    /**
     * Get singleton instance of SessionManager
     */
    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Create a new presentation editing session
     * Performs all expensive initialization operations once
     */
    public String createSession(File pptxFile) throws XMLParsingException {
        String sessionId = UUID.randomUUID().toString();
        logger.info("Creating session {} for presentation: {}", sessionId, pptxFile.getAbsolutePath());
        
        try {
            // Perform expensive initialization
            ManagedSession session = new ManagedSession(sessionId, pptxFile);
            activeSessions.put(sessionId, session);

            logger.info("Session {} created successfully with {} slides",
                       sessionId, session.getSlideCount());
            firePresentationLoaded();
            return sessionId;

        } catch (Exception e) {
            logger.error("Failed to create session {}: {}", sessionId, e.getMessage());
            throw new XMLParsingException("Failed to create session: " + e.getMessage(), e);
        }
    }

    /**
     * Create a new empty presentation session
     */
    public String createNewSession() throws XMLParsingException {
        String sessionId = UUID.randomUUID().toString();
        logger.info("Creating new empty session {}", sessionId);

        try {
            ManagedSession session = new ManagedSession(sessionId);
            activeSessions.put(sessionId, session);

            logger.info("Empty session {} created successfully", sessionId);
            firePresentationLoaded();
            return sessionId;
            
        } catch (Exception e) {
            logger.error("Failed to create new session {}: {}", sessionId, e.getMessage());
            throw new XMLParsingException("Failed to create new session: " + e.getMessage(), e);
        }
    }
    
    /**
     * Register an existing orchestrator as a session.
     * Used when the orchestrator was created outside the session lifecycle
     * (e.g. the console's standalone orchestrator entering arrange mode).
     */
    public String registerSession(PPTXOrchestratorImpl orchestrator) {
        String sessionId = UUID.randomUUID().toString();
        ManagedSession session = new ManagedSession(sessionId, orchestrator);
        activeSessions.put(sessionId, session);
        logger.info("Registered existing orchestrator as session {}", sessionId);
        return sessionId;
    }

    /**
     * Get an active session
     */
    public Optional<ManagedSession> getSession(String sessionId) {
        ManagedSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.updateLastAccessed();
            logger.debug("Accessed session {}", sessionId);
        } else {
            logger.warn("Attempted to access non-existent session {}", sessionId);
        }
        return Optional.ofNullable(session);
    }
    
    /**
     * Close a session and cleanup resources. If the closed session was
     * the active one, the active pointer is cleared and
     * {@code onActiveSessionChanged(null, null)} fires so observers
     * transition to an empty state.
     */
    public boolean closeSession(String sessionId) {
        ManagedSession session = activeSessions.remove(sessionId);
        if (session != null) {
            logger.info("Closing session {}", sessionId);
            session.cleanup();
            firePresentationClosed();
            // Clear active pointer if we just removed the active session.
            // Non-active closes do NOT touch the active pointer (invariant 3).
            if (sessionId != null && sessionId.equals(this.activeSessionId)) {
                setActiveSession(null);
            }
            return true;
        } else {
            logger.warn("Attempted to close non-existent session {}", sessionId);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Active session pointer (Session Unification)
    // ------------------------------------------------------------------

    /**
     * Point the global active-session marker at {@code sessionId}. Pass
     * null to clear (e.g. "no presentation loaded").
     *
     * <p>Fires {@link OrchestrationStateListener#onActiveSessionChanged}
     * on every registered listener on the caller's thread -- GUI
     * implementers do their own {@code Platform.runLater} hop.
     *
     * <p>Idempotent: setting the same id still fires, so callers can
     * use this as a "re-confirm" signal.
     */
    public void setActiveSession(String sessionId) {
        this.activeSessionId = sessionId;
        PPTXOrchestrator orch = getActiveOrchestrator();
        logger.debug("Active session -> {} (orchestrator={})",
            sessionId, orch == null ? "null" : "present");
        fireActiveSessionChanged(sessionId, orch);
    }

    /** @return the active session id, or null if none. */
    public String getActiveSessionId() {
        return activeSessionId;
    }

    /**
     * @return the ManagedSession for the active id, or empty if the id
     *     is null / the session has been closed since the pointer was set.
     */
    public Optional<ManagedSession> getActiveSession() {
        String id = activeSessionId;
        if (id == null) return Optional.empty();
        return Optional.ofNullable(activeSessions.get(id));
    }

    /**
     * @return the orchestrator backing the active session, or null if
     *     no session is active / the session has no orchestrator. Never
     *     throws. Observers must null-check.
     */
    public PPTXOrchestrator getActiveOrchestrator() {
        return getActiveSession().map(ManagedSession::getOrchestrator).orElse(null);
    }

    /**
     * Notify listeners that the active-session pointer moved. Called
     * automatically from {@link #setActiveSession}; public so internal
     * callers that mutate the pointer indirectly (e.g. a closeSession
     * path) can announce the change explicitly.
     */
    public void fireActiveSessionChanged(String sessionId, PPTXOrchestrator orchestrator) {
        for (OrchestrationStateListener listener : stateListeners) {
            try {
                listener.onActiveSessionChanged(sessionId, orchestrator);
            } catch (RuntimeException e) {
                logger.warn("State listener threw during onActiveSessionChanged: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Get all active session IDs
     */
    public java.util.Set<String> getActiveSessionIds() {
        return activeSessions.keySet();
    }
    
    /**
     * Get number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    /**
     * Cleanup expired sessions
     */
    private void cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        long timeoutMillis = SESSION_TIMEOUT_MINUTES * 60 * 1000;
        
        activeSessions.entrySet().removeIf(entry -> {
            ManagedSession session = entry.getValue();
            if (currentTime - session.getLastAccessed() > timeoutMillis) {
                logger.info("Cleaning up expired session {}", entry.getKey());
                session.cleanup();
                return true;
            }
            return false;
        });
    }
    
    // ------------------------------------------------------------------
    // State change listener registry
    // ------------------------------------------------------------------

    /**
     * Subscribe to presentation state change events. Typically called by the
     * GUI's MainController once at startup so it can refresh its views when
     * console commands or other code paths change the loaded presentation.
     */
    public void addStateListener(OrchestrationStateListener listener) {
        if (listener != null) {
            stateListeners.add(listener);
        }
    }

    /**
     * Unsubscribe a previously registered state change listener.
     */
    public void removeStateListener(OrchestrationStateListener listener) {
        if (listener != null) {
            stateListeners.remove(listener);
        }
    }

    /**
     * Notify listeners that a presentation was loaded or replaced.
     * Each listener is invoked independently; a misbehaving listener cannot
     * break command execution.
     */
    public void firePresentationLoaded() {
        for (OrchestrationStateListener listener : stateListeners) {
            try {
                listener.onPresentationLoaded();
            } catch (RuntimeException e) {
                logger.warn("State listener threw during onPresentationLoaded: {}", e.getMessage());
            }
        }
    }

    /**
     * Notify listeners that the active session was closed.
     */
    public void firePresentationClosed() {
        for (OrchestrationStateListener listener : stateListeners) {
            try {
                listener.onPresentationClosed();
            } catch (RuntimeException e) {
                logger.warn("State listener threw during onPresentationClosed: {}", e.getMessage());
            }
        }
    }

    /**
     * Notify listeners that the slide list changed (add/delete/reorder).
     */
    public void firePresentationStructureChanged() {
        for (OrchestrationStateListener listener : stateListeners) {
            try {
                listener.onPresentationStructureChanged();
            } catch (RuntimeException e) {
                logger.warn("State listener threw during onPresentationStructureChanged: {}", e.getMessage());
            }
        }
    }

    /**
     * Notify listeners that a single slide's contents changed.
     *
     * @param slideNumber the 1-based slide number that was modified
     */
    public void fireSlideModified(int slideNumber) {
        for (OrchestrationStateListener listener : stateListeners) {
            try {
                listener.onSlideModified(slideNumber);
            } catch (RuntimeException e) {
                logger.warn("State listener threw during onSlideModified: {}", e.getMessage());
            }
        }
    }

    /**
     * Shutdown session manager and cleanup all sessions
     */
    public void shutdown() {
        logger.info("Shutting down with {} active sessions", activeSessions.size());

        // Close all sessions
        for (ManagedSession session : activeSessions.values()) {
            logger.debug("Cleaning session {} (tempDir={})", session.getSessionId(), session.getStatus().get("tempDirectory"));
            session.cleanup();
        }
        activeSessions.clear();
        
        // Shutdown cleanup executor
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("SessionManager shutdown complete");
    }
    
    /**
     * Represents a managed presentation editing session
     * Keeps all expensive objects alive to prevent re-parsing
     */
    public static class ManagedSession {
        private final String sessionId;
        private final PPTXOrchestratorImpl orchestrator;
        private volatile long lastAccessed;
        
        // Keep expensive objects alive
        private final SPIDManager spidManager;
        private final ThemeManager themeManager;
        private final RelationshipManager relationshipManager;
        private final CommandInvoker commandInvoker;
        
        // Track resources for cleanup
        private final File originalFile;
        private final Path tempDirectory;

        // MCP session exclusivity. Holds a reference to the engine that
        // started the MCP server, or null when no server is live. Identity
        // comparison lets the owning engine keep mutating while every other
        // attached console is blocked -- the previous boolean flag blocked
        // even the owner because it couldn't distinguish them.
        private Object mcpOwner = null;
        
        /**
         * Create session from existing PPTX file
         */
        public ManagedSession(String sessionId, File pptxFile) throws XMLParsingException {
            this.sessionId = sessionId;
            this.originalFile = pptxFile;
            this.lastAccessed = System.currentTimeMillis();
            
            logger.debug("Initializing session {} with file {}", sessionId, pptxFile.getAbsolutePath());
            
            // Initialize orchestrator (expensive operation)
            this.orchestrator = new PPTXOrchestratorImpl();
            
            // Load the presentation (expensive parsing)
            ExecutionResult loadResult = orchestrator.loadPresentation(pptxFile);
            if (!loadResult.isSuccess()) {
                throw new XMLParsingException("Failed to load presentation: " + loadResult.getMessage());
            }
            
            // Get orchestration context
            Optional<OrchestrationContext> contextOpt = orchestrator.getContext();
            if (!contextOpt.isPresent()) {
                throw new XMLParsingException("Failed to get orchestration context");
            }
            
            OrchestrationContext context = contextOpt.get();
            // For the in-memory path, there is no extracted directory; tempDirectory is null.
            File extractedDir = (context.getDocument() != null) ? context.getDocument().getExtractedDir() : null;
            if (extractedDir != null) {
                this.tempDirectory = extractedDir.getParentFile() != null
                        ? extractedDir.getParentFile().toPath()
                        : extractedDir.toPath();
            } else {
                this.tempDirectory = null;
            }

            // Initialize expensive components (keep them alive)
            this.spidManager = context.getSpidManager();
            this.themeManager = new ThemeManager();
            this.relationshipManager = context.getRelationshipManager();
            this.commandInvoker = new CommandInvoker();
            
            logger.debug("Session {} initialization complete", sessionId);
        }
        
        /**
         * Create session for new empty presentation
         */
        public ManagedSession(String sessionId) throws XMLParsingException {
            this(sessionId, new PPTXOrchestratorImpl());
        }

        /**
         * Create session wrapping an existing orchestrator.
         * Used when the orchestrator already exists (e.g. arrange mode).
         */
        public ManagedSession(String sessionId, PPTXOrchestratorImpl orchestrator) {
            this.sessionId = sessionId;
            this.orchestrator = orchestrator;
            this.originalFile = null;
            this.lastAccessed = System.currentTimeMillis();
            this.tempDirectory = null;
            this.spidManager = null;
            this.themeManager = new ThemeManager();
            this.relationshipManager = null;
            this.commandInvoker = new CommandInvoker();
        }
        
        // Getters for session objects
        public String getSessionId() { return sessionId; }
        public PPTXOrchestrator getOrchestrator() { return orchestrator; }
        public SPIDManager getSPIDManager() { return spidManager; }
        public ThemeManager getThemeManager() { return themeManager; }
        public RelationshipManager getRelationshipManager() { return relationshipManager; }
        public CommandInvoker getCommandInvoker() { return commandInvoker; }
        
        public long getLastAccessed() { return lastAccessed; }
        public void updateLastAccessed() { this.lastAccessed = System.currentTimeMillis(); }
        
        public File getOriginalFile() { return originalFile; }
        public boolean hasOriginalFile() { return originalFile != null; }

        /** True while an MCP server is claiming exclusivity on this session. */
        public boolean isMcpExclusive() { return mcpOwner != null; }

        /** Claim exclusivity for the given owner (typically a console engine). */
        public void setMcpOwner(Object owner) { this.mcpOwner = owner; }

        /** Release exclusivity. Safe to call when no owner is set. */
        public void clearMcpOwner() { this.mcpOwner = null; }

        /**
         * True iff the candidate is the exact owner (identity comparison).
         * An unowned session is owned by nobody -- {@code isOwnedBy(null)}
         * on an unowned session returns false, not true from a
         * {@code null == null} accident.
         */
        public boolean isOwnedBy(Object candidate) {
            return mcpOwner != null && mcpOwner == candidate;
        }
        
        /**
         * Get presentation metadata without expensive operations
         */
        public int getSlideCount() {
            try {
                PresentationMetadata metadata = orchestrator.getPresentationMetadata();
                return metadata != null ? metadata.getSlideCount() : 0;
            } catch (Exception e) {
                logger.warn("Failed to get slide count for session {}: {}", sessionId, e.getMessage());
                return 0;
            }
        }
        
        /**
         * Get session status information
         */
        public Map<String, Object> getStatus() {
            Map<String, Object> status = new java.util.HashMap<>();
            status.put("sessionId", sessionId);
            status.put("slideCount", getSlideCount());
            status.put("hasOriginalFile", hasOriginalFile());
            status.put("originalFile", originalFile != null ? originalFile.getAbsolutePath() : null);
            status.put("lastAccessed", lastAccessed);
            status.put("tempDirectory", tempDirectory != null ? tempDirectory.toString() : null);
            return status;
        }
        
        /**
         * Cleanup session resources
         */
        public void cleanup() {
            logger.debug("Cleaning up session {}", sessionId);

            // Close PPTXDocument (releases lock file)
            if (orchestrator != null) {
                orchestrator.close();
            }

            // Determine the temp directory to clean up
            Path dirToDelete = tempDirectory;

            // If tempDirectory wasn't set (empty session that loaded a file later),
            // try to discover it from the orchestrator's context
            if (dirToDelete == null && orchestrator != null) {
                try {
                    java.util.Optional<OrchestrationContext> ctxOpt = orchestrator.getContext();
                    if (ctxOpt.isPresent()) {
                        com.excudo.core.model.PPTXDocument doc = ctxOpt.get().getDocument();
                        File extractedDir = (doc != null) ? doc.getExtractedDir() : null;
                        if (extractedDir != null) {
                            File parent = extractedDir.getParentFile();
                            if (parent != null && parent.getName().startsWith("pptx_")) {
                                dirToDelete = parent.toPath();
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Could not discover temp dir from orchestrator: {}", e.getMessage());
                }
            }

            try {
                if (dirToDelete != null && Files.exists(dirToDelete)) {
                    deleteDirectoryRecursively(dirToDelete);
                    logger.debug("Deleted temp directory for session {}", sessionId);
                }
            } catch (Exception e) {
                logger.warn("Failed to cleanup temp directory for session {}: {}", sessionId, e.getMessage());
            }
        }
        
        private void deleteDirectoryRecursively(Path directory) {
            try {
                Files.walk(directory)
                     .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                         } catch (Exception e) {
                             logger.debug("Failed to delete {}: {}", path, e.getMessage());
                         }
                     });
            } catch (Exception e) {
                logger.warn("Failed to walk directory tree {}: {}", directory, e.getMessage());
            }
        }
    }
}