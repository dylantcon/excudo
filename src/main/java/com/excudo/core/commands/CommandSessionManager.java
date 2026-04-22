package com.excudo.core.commands;

import com.excudo.core.commands.meta.LoadCommand;
import com.excudo.core.commands.meta.SessionCreateCommand;
import com.excudo.core.commands.meta.SessionListCommand;

import java.util.Map;

/**
 * Unified interface for session management operations used by all session management Command implementations.
 * 
 * This replaces the 6+ duplicate SessionManager interfaces that were scattered
 * across individual command classes (LoadCommand.SessionManager, SessionCreateCommand.SessionManager,
 * SessionListCommand.SessionManager, etc.), providing a single point of contract
 * for all session management operations.
 * 
 * Implementations should handle creation, listing, querying, and closing of sessions.
 */
public interface CommandSessionManager {
    
    /**
     * Create a new session with a presentation file.
     * 
     * @param filename the presentation file to load into the new session
     * @return the result of session creation
     */
    SessionResult createSession(String filename);
    
    /**
     * Create a new empty session without loading a file.
     * 
     * @return the result of session creation
     */
    SessionResult createEmptySession();
    
    /**
     * Get all active sessions.
     * 
     * @return a map of session ID to session information for all active sessions
     */
    Map<String, SessionInfo> getAllSessions();
    
    /**
     * Get information about a specific session.
     * 
     * @param sessionId the session ID to query
     * @return session information, or null if session not found
     */
    SessionInfo getSessionInfo(String sessionId);
    
    /**
     * Switch to a different session.
     *
     * @param sessionId the session ID to switch to
     * @return a SessionResult with the session's orchestrator and state, or error
     */
    SessionResult switchToSession(String sessionId);

    /**
     * Close a specific session.
     *
     * @param sessionId the session ID to close
     * @return true if session was successfully closed, false if session not found
     */
    boolean closeSession(String sessionId);
    
    /**
     * Interface for session information returned by session queries.
     * Consolidates all the different SessionInfo interfaces from individual commands.
     */
    public interface SessionInfo {
        String getSessionId();
        String getFilename();
        boolean isActive();
        long getCreationTime();
        int getSlideCount();
        String getTitle();
    }
}