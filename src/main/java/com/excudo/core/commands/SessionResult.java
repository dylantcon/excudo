package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import java.io.File;

/**
 * Unified interface for session creation results used by all session creation Command implementations.
 * 
 * This replaces the 2+ duplicate SessionCreationResult interfaces that were scattered
 * across LoadCommand and SessionCreateCommand, providing a single point of contract
 * for session creation result information.
 * 
 * Used by session creation operations to return both success/failure status
 * and the associated session state information.
 */
public interface SessionResult {
    
    /**
     * Check if the session creation was successful.
     * 
     * @return true if the session was created successfully, false otherwise
     */
    boolean isSuccess();
    
    /**
     * Get the ID of the created session.
     * 
     * @return the session ID, or null if creation failed
     */
    String getSessionId();
    
    /**
     * Get the orchestrator for the created session.
     * 
     * @return the PPTXOrchestrator, or null if creation failed
     */
    PPTXOrchestrator getOrchestrator();
    
    /**
     * Get the LLM handler for the created session.
     * 
     * @return the LLM handler (Object to avoid circular dependencies), or null if creation failed
     */
    Object getLlmHandler();
    
    /**
     * Get the current file for the created session.
     * 
     * @return the current File, or null if creation failed or no file was loaded
     */
    File getCurrentFile();
    
    /**
     * Get the error message if session creation failed.
     * 
     * @return the error message, or null if creation succeeded
     */
    String getErrorMessage();
}