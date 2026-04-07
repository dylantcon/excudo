package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for editing bullet point content and formatting.
 * 
 * This command allows modifying bullet point text, adding/removing
 * bullet points, and changing bullet point styling with undo capability.
 */
public class BulletPointEditCommand implements Command {
    
    private final int slideNumber;
    private final int spid;
    private final String operation; // "add", "edit", "remove", "reorder"
    private final int bulletIndex; // 0-based index for specific bullet point
    private final String newText;
    private final String bulletStyle; // optional bullet style
    private final PPTXOrchestrator orchestrator;
    
    private boolean executed = false;
    private String originalText = null;
    private String originalStyle = null;
    
    /**
     * Create a BulletPointEditCommand.
     * 
     * @param slideNumber the slide number containing the shape
     * @param spid the SPID of the text shape to edit
     * @param operation the operation type (add, edit, remove, reorder)
     * @param bulletIndex the bullet point index (0-based, -1 for append)
     * @param newText the new text content (null for remove operation)
     * @param bulletStyle the bullet style (null to keep current)
     * @param orchestrator the PPTX orchestrator for execution
     */
    public BulletPointEditCommand(int slideNumber, int spid, String operation, 
                                 int bulletIndex, String newText, String bulletStyle,
                                 PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (operation == null || operation.trim().isEmpty()) {
            throw new IllegalArgumentException("Operation cannot be null or empty");
        }
        
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.operation = operation.toLowerCase().trim();
        this.bulletIndex = bulletIndex;
        this.newText = newText;
        this.bulletStyle = bulletStyle;
        this.orchestrator = orchestrator;
        
        // Validate operation type
        if (!isValidOperation(this.operation)) {
            throw new IllegalArgumentException("Invalid operation: " + operation + 
                ". Valid operations are: add, edit, remove, reorder");
        }
    }
    
    /**
     * Execute the bullet point edit command.
     * 
     * @throws CommandExecutionException if the operation fails
     */
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Capture original text for undo before modifying
            originalText = orchestrator.getShapeText(slideNumber, spid);

            // Use the orchestrator's editBulletPoint method
            ExecutionResult<String> result = orchestrator.editBulletPoint(
                slideNumber, spid, operation, bulletIndex, newText, bulletStyle);

            if (!result.isSuccess()) {
                throw new CommandExecutionException(
                    getDescription(),
                    "execute",
                    "Orchestrator operation failed: " + result.getMessage()
                );
            }

            executed = true;
            
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "execute", 
                "Failed to " + operation + " bullet point: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Undo the bullet point edit operation.
     * 
     * @throws CommandExecutionException if the undo operation fails
     */
    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo", "Command has not been executed");
        }
        
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo", "Command cannot be undone");
        }
        
        try {
            // Restore the full original text content captured before execute()
            com.excudo.core.results.ExecutionResult<Void> result =
                orchestrator.editShapeText(slideNumber, spid, originalText);

            if (!result.isSuccess()) {
                throw new CommandExecutionException(
                    getDescription(), "undo",
                    "Failed to restore original text: " + result.getMessage());
            }

            executed = false;
            
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "undo", 
                "Failed to undo bullet point " + operation + ": " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Check if this command can be undone.
     * 
     * @return true if the command can be undone
     */
    @Override
    public boolean canUndo() {
        return executed && originalText != null;
    }
    
    /**
     * Get the description of this command.
     * 
     * @return description of the bullet point operation
     */
    @Override
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(operation.substring(0, 1).toUpperCase())
            .append(operation.substring(1))
            .append(" bullet point");
        
        if (bulletIndex >= 0) {
            desc.append(" #").append(bulletIndex + 1);
        }
        
        desc.append(" on SPID ").append(spid).append(" (slide ").append(slideNumber).append(")");
        
        if (newText != null && !newText.trim().isEmpty()) {
            String preview = newText.length() > 30 ? newText.substring(0, 30) + "..." : newText;
            desc.append(": \"").append(preview).append("\"");
        }
        
        return desc.toString();
    }
    
    /**
     * Check if this command has been executed.
     * 
     * @return true if execute() has been called successfully
     */
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    /**
     * Validate if the operation type is supported.
     * 
     * @param operation the operation to validate
     * @return true if the operation is valid
     */
    private boolean isValidOperation(String operation) {
        return "add".equals(operation) || 
               "edit".equals(operation) || 
               "remove".equals(operation) || 
               "reorder".equals(operation);
    }
    
    // ========== GETTERS ==========
    
    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
    public String getOperation() { return operation; }
    public int getBulletIndex() { return bulletIndex; }
    public String getNewText() { return newText; }
    public String getBulletStyle() { return bulletStyle; }
    public String getOriginalText() { return originalText; }
    public String getOriginalStyle() { return originalStyle; }
}