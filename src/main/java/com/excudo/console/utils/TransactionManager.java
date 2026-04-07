package com.excudo.console.utils;

import com.excudo.core.orchestration.PPTXOrchestrator;
import java.util.function.Consumer;

/**
 * Utility class for transaction-related console operations.
 * Centralizes transaction management stub methods to eliminate code duplication
 * in AbstractConsoleEngine.
 */
public class TransactionManager {
    
    private final Consumer<String> displayMessage;
    private final Consumer<String> displayError;
    
    public TransactionManager(Consumer<String> displayMessage, Consumer<String> displayError) {
        this.displayMessage = displayMessage;
        this.displayError = displayError;
    }
    
    /**
     * Handle rollback command - rolls back a specific transaction by ID
     */
    public void handleRollback(PPTXOrchestrator sessionOrchestrator, String transactionId) {
        // Validate session and orchestrator
        ConsoleCommandValidator.ValidationResult validation = ConsoleCommandValidator.validateSessionOrchestrator(sessionOrchestrator);
        if (!validation.isValid()) {
            displayError.accept(validation.getErrorMessage());
            return;
        }
        
        try {
            displayMessage.accept("Rolling back transaction: " + transactionId);
            displayMessage.accept("Transaction rollback by ID is not yet fully implemented.");
            displayMessage.accept("Individual operations support rollback but need transaction management integration.");
            
        } catch (Exception e) {
            displayError.accept("Rollback failed: " + e.getMessage());
        }
    }
    
    /**
     * Handle list transactions command - shows recent transactions
     */
    public void handleListTransactions(PPTXOrchestrator sessionOrchestrator) {
        // Validate session and orchestrator
        ConsoleCommandValidator.ValidationResult validation = ConsoleCommandValidator.validateSessionOrchestrator(sessionOrchestrator);
        if (!validation.isValid()) {
            displayError.accept(validation.getErrorMessage());
            return;
        }
        
        try {
            displayMessage.accept("RECENT TRANSACTIONS:");
            displayMessage.accept("====================");
            displayMessage.accept("Transaction tracking is not yet fully implemented.");
            displayMessage.accept("When implemented, this will show:");
            displayMessage.accept("  • Transaction ID and timestamp");
            displayMessage.accept("  • Operation type (slide-creation, content-edit, etc.)");
            displayMessage.accept("  • Success/failure status");
            displayMessage.accept("  • Undo availability");
            
        } catch (Exception e) {
            displayError.accept("Failed to list transactions: " + e.getMessage());
        }
    }
    
    /**
     * Handle transaction history command - shows transaction history with limit
     */
    public void handleTransactionHistory(PPTXOrchestrator sessionOrchestrator, int limit) {
        // Validate session and orchestrator
        ConsoleCommandValidator.ValidationResult validation = ConsoleCommandValidator.validateSessionOrchestrator(sessionOrchestrator);
        if (!validation.isValid()) {
            displayError.accept(validation.getErrorMessage());
            return;
        }
        
        try {
            displayMessage.accept("TRANSACTION HISTORY (last " + limit + "):");
            displayMessage.accept("=========================================");
            displayMessage.accept("Transaction history tracking is not yet fully implemented.");
            displayMessage.accept("When implemented, this will show detailed history including:");
            displayMessage.accept("  • Chronological list of all operations");
            displayMessage.accept("  • Parameter details for each transaction");
            displayMessage.accept("  • Before/after state information");
            displayMessage.accept("  • Rollback chains and dependencies");
            
        } catch (Exception e) {
            displayError.accept("Failed to get transaction history: " + e.getMessage());
        }
    }
}