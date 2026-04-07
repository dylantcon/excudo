package com.excudo.view.console;

import com.excudo.console.AbstractConsoleEngine;
import com.excudo.console.LLMConsoleHandler;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.orchestration.BatchExecutionResult;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.TextArea;

import java.util.Scanner;
import java.util.function.Consumer;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * JavaFX/UI implementation of ConsoleEngine for GUI integration.
 * Provides rich text formatting, async operations, and UI component integration.
 */
public class UIConsoleEngine extends AbstractConsoleEngine {
    
    // UI-specific components
    private TextArea outputArea;
    private Consumer<String> outputHandler;
    private Consumer<String> errorHandler;
    private Scanner inputScanner;
    
    // LLM workflow state
    private boolean llmMode = false;
    private String pendingCommand = null;
    private LLMConsoleHandler.LLMEditProposal pendingProposal = null;
    
    // Command system for UI extensions
    private Map<String, String> commandDescriptions;
    private Map<String, List<String>> commandParameters;
    
    // Status callback for UI updates
    private Consumer<String> statusHandler;
    
    public UIConsoleEngine(TextArea outputArea) {
        this.outputArea = outputArea;
        this.outputHandler = this::appendToOutput;
        this.errorHandler = this::appendError;
        this.statusHandler = text -> {}; // Default no-op
        initializeUICommandSystem();
    }
    
    @Override
    public void initialize(PPTXOrchestrator orchestrator) {
        super.initialize(orchestrator);
        addOrchestratorCommands();
    }
    
    private void initializeUICommandSystem() {
        // Initialize UI-specific command descriptions
        commandDescriptions = new HashMap<>();
        commandDescriptions.put("help", "Show available commands and usage");
        commandDescriptions.put("status", "Display system status information");
        commandDescriptions.put("clear", "Clear console output");
        commandDescriptions.put("approve", "Execute pending LLM operations");
        commandDescriptions.put("deny", "Cancel pending LLM operations");
        commandDescriptions.put("details", "Show full LLM response details");
        
        // Initialize command parameters
        commandParameters = new HashMap<>();
    }
    
    private void addOrchestratorCommands() {
        if (orchestrator == null) return;
        
        // Add orchestrator-specific commands for UI
        commandDescriptions.put("createSlide", "Create a new slide at specified position");
        commandDescriptions.put("deleteSlide", "Delete slide at specified position");
        commandDescriptions.put("duplicateSlide", "Duplicate slide from source to target position");
        commandDescriptions.put("moveSlide", "Move slide from one position to another");
        commandDescriptions.put("getShapesOnSlide", "Get all shapes on a specific slide");
        
        // Add parameters for orchestrator commands
        commandParameters.put("createSlide", List.of("<position>", "[layout]", "[title]"));
        commandParameters.put("deleteSlide", List.of("<slideNumber>"));
        commandParameters.put("duplicateSlide", List.of("<sourceSlide>", "<targetPosition>"));
        commandParameters.put("moveSlide", List.of("<fromPosition>", "<toPosition>"));
        commandParameters.put("getShapesOnSlide", List.of("<slideNumber>"));
    }
    
    @Override
    public void executeCommand(String command) {
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        
        // Execute on JavaFX Application Thread for UI updates
        Platform.runLater(() -> {
            appendCommand(command);
            
            // Handle LLM mode vs direct command mode
            if (llmMode && pendingCommand == null) {
                // LLM mode - process as natural language
                executeLlmCommand(command);
            } else if (pendingCommand != null) {
                // Handle pending transaction approval workflow
                handlePendingTransaction(command);
            } else {
                // Check for UI-specific commands first
                if (handleUISpecificCommand(cmd, parts)) {
                    return; // Command was handled
                }
                // Fall back to common command handling
                super.executeCommand(command);
            }
        });
    }
    
    /**
     * Handle UI-specific commands that aren't in the base class
     */
    private boolean handleUISpecificCommand(String cmd, String[] parts) {
        switch (cmd) {
            case "approve":
                if (pendingProposal != null) {
                    executeApprovedProposal();
                } else {
                    displayError("No pending proposal to approve");
                }
                return true;
                
            case "deny":
                if (pendingProposal != null) {
                    denyProposal();
                } else {
                    displayError("No pending proposal to deny");
                }
                return true;
                
            case "details":
                if (pendingProposal != null) {
                    showProposalDetails();
                } else {
                    displayError("No pending proposal to show details for");
                }
                return true;
                
            case "clear":
                clearOutput();
                return true;
                
            case "status":
                showStatus();
                return true;
                
            default:
                return false; // Command not handled by UI-specific logic
        }
    }
    
    /**
     * UI-specific LLM command handling with async processing
     */
    @Override
    protected void handleLLMCommand(String subCommand) {
        if (orchestrator == null) {
            displayError("No presentation loaded. Please load a presentation first.");
            return;
        }
        
        if (llmHandler == null) {
            displayError("LLM Console Handler not initialized. Try loading a presentation.");
            return;
        }
        
        statusHandler.accept("Processing with LLM: " + subCommand);
        displayMessage("Analyzing request with AI...");
        
        // Stage 1: Generate proposal using async task
        Task<LLMConsoleHandler.LLMEditProposal> proposalTask = new Task<LLMConsoleHandler.LLMEditProposal>() {
            @Override
            protected LLMConsoleHandler.LLMEditProposal call() throws Exception {
                updateMessage("Generating presentation context...");
                updateMessage("Sending request to LLM...");
                return llmHandler.processEditRequest(subCommand);
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    LLMConsoleHandler.LLMEditProposal proposal = getValue();
                    showProposalForApproval(proposal, subCommand);
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    Throwable exception = getException();
                    displayError("LLM processing failed: " + exception.getMessage());
                    statusHandler.accept("Ready");
                });
            }
        };
        
        // Execute task in background
        Thread proposalThread = new Thread(proposalTask);
        proposalThread.setDaemon(true);
        proposalThread.start();
    }
    
    /**
     * UI-specific display methods using JavaFX components
     */
    @Override
    public void displayMessage(String message) {
        outputHandler.accept(message);
    }
    
    @Override
    public void displayError(String message) {
        errorHandler.accept(message);
    }
    
    @Override
    public void displaySuccess(String message) {
        appendSuccess(message);
    }
    
    
    // UI-specific LLM workflow methods
    private void executeLlmCommand(String command) {
        handleLLMCommand(command);
    }
    
    private void showProposalForApproval(LLMConsoleHandler.LLMEditProposal proposal, String originalCommand) {
        this.pendingProposal = proposal;
        this.pendingCommand = originalCommand;
        
        displayMessage("");
        displayMessage("AI Analysis Complete!");
        displayMessage("");
        displayMessage("Proposed Operations:");
        displayMessage(proposal.getOperationsSummary());
        displayMessage("");
        displayMessage("Commands:");
        displayMessage("  approve  - Execute the proposed operations");
        displayMessage("  deny     - Cancel the operations");
        displayMessage("  details  - Show full AI response");
        displayMessage("");
        
        statusHandler.accept("Waiting for approval (type 'approve' or 'deny')");
    }
    
    private void handlePendingTransaction(String command) {
        String cmd = command.toLowerCase().trim();
        
        switch (cmd) {
            case "approve":
                executeApprovedProposal();
                break;
            case "deny":
                denyProposal();
                break;
            case "details":
                showProposalDetails();
                break;
            default:
                displayError("Invalid command. Use 'approve', 'deny', or 'details'");
        }
    }
    
    private void executeApprovedProposal() {
        if (pendingProposal == null) {
            displayError("No pending proposal to execute");
            return;
        }
        
        displayMessage("Executing approved operations...");
        statusHandler.accept("Executing LLM operations...");
        
        Task<BatchExecutionResult> executeTask = new Task<BatchExecutionResult>() {
            @Override
            protected BatchExecutionResult call() throws Exception {
                CommandInvoker invoker = getCurrentCommandInvoker();
                if (invoker == null) {
                    throw new Exception("No active session for command execution");
                }
                return llmHandler.executeApprovedProposal(pendingProposal, invoker);
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    BatchExecutionResult result = getValue();
                    if (result.isSuccess()) {
                        displaySuccess("Operations completed successfully!");
                        displayMessage("  " + result.getSuccessfulOperations() + "/" + 
                                      result.getTotalOperations() + " operations succeeded");
                    } else {
                        displayError("Some operations failed: " +
                                   result.getFailedOperations() + "/" + 
                                   result.getTotalOperations() + " failed");
                    }
                    
                    clearPendingTransaction();
                    statusHandler.accept("Ready");
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    displayError("Execution failed: " + getException().getMessage());
                    clearPendingTransaction();
                    statusHandler.accept("Ready");
                });
            }
        };
        
        Thread executeThread = new Thread(executeTask);
        executeThread.setDaemon(true);
        executeThread.start();
    }
    
    private void denyProposal() {
        displayMessage("Operations cancelled by user.");
        clearPendingTransaction();
        statusHandler.accept("Ready");
    }
    
    private void showProposalDetails() {
        if (pendingProposal == null) {
            displayError("No pending proposal to show details for");
            return;
        }
        
        displayMessage("");
        displayMessage("=== Full AI Response ===");
        displayMessage(pendingProposal.getFullLLMResponse());
        displayMessage("");
        displayMessage("=== JSON Command ===");
        displayMessage(pendingProposal.getJsonCommand());
        displayMessage("");
    }
    
    private void clearPendingTransaction() {
        pendingCommand = null;
        pendingProposal = null;
    }
    
    private void showStatus() {
        if (orchestrator == null) {
            displayMessage("System: Not initialized");
            return;
        }
        
        displayMessage("");
        displayMessage("=== System Status ===");
        displayMessage("Presentation: " + getPresentationStatus());
        displayMessage("LLM Mode: " + (llmMode ? "Enabled" : "Disabled"));
        displayMessage("Pending Transaction: " + (pendingCommand != null ? "Yes" : "No"));
        
        if (pendingCommand != null) {
            displayMessage("  Command: " + pendingCommand);
        }
        
        displayMessage("");
    }
    
    // UI-specific output methods
    private void appendToOutput(String text) {
        if (outputArea != null) {
            Platform.runLater(() -> {
                outputArea.appendText(text + "\n");
            });
        }
    }
    
    private void appendError(String text) {
        if (outputArea != null) {
            Platform.runLater(() -> {
                // In a real implementation, this could use rich text formatting for red error text
                outputArea.appendText("[ERROR] " + text + "\n");
            });
        }
    }
    
    private void appendSuccess(String text) {
        if (outputArea != null) {
            Platform.runLater(() -> {
                // In a real implementation, this could use rich text formatting for green success text
                outputArea.appendText(text + "\n");
            });
        }
    }
    
    private void appendCommand(String command) {
        if (outputArea != null) {
            Platform.runLater(() -> {
                outputArea.appendText("> " + command + "\n");
            });
        }
    }
    
    private void clearOutput() {
        if (outputArea != null) {
            Platform.runLater(() -> {
                outputArea.clear();
            });
        }
    }
    
    // UI-specific methods for integration
    public void setOutputHandler(Consumer<String> outputHandler) {
        this.outputHandler = outputHandler;
    }
    
    public void setErrorHandler(Consumer<String> errorHandler) {
        this.errorHandler = errorHandler;
    }
    
    public void setStatusHandler(Consumer<String> statusHandler) {
        this.statusHandler = statusHandler;
    }
    
    // LLM mode control for UI integration
    public void setLlmMode(boolean llmMode) {
        this.llmMode = llmMode;
    }
    
    public boolean isLlmMode() {
        return llmMode;
    }
    
    public boolean hasPendingTransaction() {
        return pendingCommand != null && pendingProposal != null;
    }
    
    public String getPendingCommand() {
        return pendingCommand;
    }
}