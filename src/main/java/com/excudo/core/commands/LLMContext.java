package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;

/**
 * Interface for providing LLM (AI) capabilities to commands.
 * 
 * This interface enables LLM commands to access required components without
 * creating compilation dependencies between the core.commands package
 * and specific console implementations. Only classes that provide LLM
 * functionality need to implement this interface.
 *
 * The interface returns LLMHandler to provide a strongly-typed abstraction
 * over concrete implementations, maintaining clean architectural boundaries
 * without coupling core.commands to the console package.
 */
public interface LLMContext {

    /**
     * Get the LLM handler for processing AI-powered operations.
     *
     * @return the LLMHandler, or null if LLM functionality is not available
     */
    LLMHandler getLLMHandler();
    
    /**
     * Get the current orchestrator for the session.
     * 
     * @return the current PPTXOrchestrator, or null if no session is active
     */
    PPTXOrchestrator getCurrentOrchestrator();
    
    /**
     * Get the CommandInvoker for the current session.
     * 
     * @return the CommandInvoker for the current session, or null if no session is active
     */
    CommandInvoker getCurrentCommandInvoker();
    
    /**
     * Check if LLM functionality is available in this context.
     * 
     * @return true if LLM operations can be performed, false otherwise
     */
    default boolean isLLMAvailable() {
        return getLLMHandler() != null && 
               getCurrentOrchestrator() != null && 
               getCurrentCommandInvoker() != null;
    }
}