package com.excudo.core.orchestration;

/**
 * Possible states of the PPTXOrchestrator
 */
public enum OrchestratorState {
    /**
     * Orchestrator has been created but not initialized
     */
    CREATED,
    
    /**
     * Orchestrator is currently initializing
     */
    INITIALIZING,
    
    /**
     * Orchestrator is ready for operations
     */
    READY,
    
    /**
     * Orchestrator is currently executing operations
     */
    EXECUTING,
    
    /**
     * Orchestrator has active transactions pending
     */
    TRANSACTION_PENDING,
    
    /**
     * Orchestrator is performing validation
     */
    VALIDATING,
    
    /**
     * Orchestrator is finalizing operations
     */
    FINALIZING,
    
    /**
     * Orchestrator encountered an error
     */
    ERROR,
    
    /**
     * Orchestrator has been closed
     */
    CLOSED
}