package com.excudo.xml.writers;

/**
 * Represents a single slide action (creation or deletion) within a batch.
 * Used in the queue-based pipeline for deterministic rId allocation.
 */
public class SlideAction {
    
    /**
     * Action types supported by the pipeline
     */
    public enum ActionType {
        CREATE,  // Insert a new slide at position
        DELETE   // Remove existing slide at position
    }
    
    /**
     * Unique identifier for this action
     */
    private final String actionId;
    
    /**
     * Type of operation (CREATE or DELETE)
     */
    private final ActionType actionType;
    
    /**
     * Position where this slide should be inserted/deleted (1-based)
     * E.g., position 2 means "insert between slide 1 and slide 2" or "delete slide 2"
     */
    private final int position;
    
    /**
     * Type of slide to create (BLANK, TITLE_CONTENT, etc.)
     * Ignored for DELETE operations
     */
    private final String slideType;
    
    /**
     * Pre-calculated rId that this slide will receive
     * Set during the pipeline's pre-calculation phase
     */
    private String preCalculatedRId;
    
    /**
     * Title for the slide (optional)
     * Ignored for DELETE operations
     */
    private final String slideTitle;
    
    /**
     * Create a CREATE operation
     */
    public SlideAction(String actionId, int position, String slideType, String slideTitle) {
        this.actionId = actionId;
        this.actionType = ActionType.CREATE;
        this.position = position;
        this.slideType = slideType;
        this.slideTitle = slideTitle;
    }
    
    /**
     * Create a CREATE operation with default title
     */
    public SlideAction(String actionId, int position, String slideType) {
        this(actionId, position, slideType, null);
    }
    
    /**
     * Create a DELETE operation
     */
    public static SlideAction createDeleteOperation(String actionId, int position) {
        return new SlideAction(actionId, ActionType.DELETE, position, null, null);
    }
    
    /**
     * Private constructor for all operation types
     */
    private SlideAction(String actionId, ActionType actionType, int position, String slideType, String slideTitle) {
        this.actionId = actionId;
        this.actionType = actionType;
        this.position = position;
        this.slideType = slideType;
        this.slideTitle = slideTitle;
    }
    
    // Getters
    public String getActionId() { return actionId; }
    public ActionType getActionType() { return actionType; }
    public int getPosition() { return position; }
    public String getSlideType() { return slideType; }
    public String getSlideTitle() { return slideTitle; }
    public String getPreCalculatedRId() { return preCalculatedRId; }
    
    // Setter for pre-calculated rId (used by pipeline)
    public void setPreCalculatedRId(String rId) { 
        this.preCalculatedRId = rId; 
    }
    
    /**
     * Convenience methods for checking operation type
     */
    public boolean isCreateOperation() {
        return actionType == ActionType.CREATE;
    }
    
    public boolean isDeleteOperation() {
        return actionType == ActionType.DELETE;
    }
    
    @Override
    public String toString() {
        if (actionType == ActionType.DELETE) {
            return String.format("SlideAction{id='%s', operation=DELETE, pos=%d, rId='%s'}", 
                               actionId, position, preCalculatedRId);
        } else {
            return String.format("SlideAction{id='%s', operation=CREATE, pos=%d, type='%s', rId='%s'}", 
                               actionId, position, slideType, preCalculatedRId);
        }
    }
}