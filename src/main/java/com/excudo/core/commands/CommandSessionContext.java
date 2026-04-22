package com.excudo.core.commands;

import com.excudo.core.commands.meta.LoadCommand;
import com.excudo.core.commands.meta.SaveCommand;
import com.excudo.core.commands.readonly.ShowShapeCommand;

import com.excudo.core.orchestration.PPTXOrchestrator;
import java.io.File;

/**
 * Unified interface for session context operations used by all session-aware Command implementations.
 * 
 * This replaces the 7+ duplicate SessionContext interfaces that were scattered
 * across individual command classes (LoadCommand.SessionContext, SaveCommand.SessionContext,
 * ShowShapeCommand.SessionContext, etc.), providing a single point of contract
 * for all session context operations.
 * 
 * Implementations should manage the current session state including the active
 * orchestrator, current file, and session identifiers.
 */
public interface CommandSessionContext {
    
    /**
     * Get the current session ID.
     * 
     * @return the current session ID, or null if no session is active
     */
    String getCurrentSessionId();
    
    /**
     * Get the current orchestrator for the active session.
     * 
     * @return the current PPTXOrchestrator, or null if no session is active
     */
    PPTXOrchestrator getCurrentOrchestrator();
    
    /**
     * Get the current file being edited.
     * 
     * @return the current File, or null if no file is loaded
     */
    File getCurrentFile();
    
    /**
     * Set the current file being edited.
     * 
     * @param file the file to set as current
     */
    void setCurrentFile(File file);
    
    /**
     * Set the current session with all its associated state.
     * 
     * @param sessionId the session ID
     * @param orchestrator the PPTXOrchestrator for this session
     * @param llmHandler the LLM handler for this session (Object to avoid circular dependencies)
     * @param currentFile the current file for this session
     */
    void setCurrentSession(String sessionId, PPTXOrchestrator orchestrator, 
                          Object llmHandler, File currentFile);
    
    /**
     * Clear the current session, resetting all session state.
     * Used when closing a session or switching to no active session.
     */
    void clearCurrentSession();
    
    /**
     * Get the CommandInvoker for the current session.
     * Used by undo/redo and history commands to access command history.
     * 
     * @return the CommandInvoker for the current session, or null if no session is active
     */
    CommandInvoker getCurrentCommandInvoker();
}