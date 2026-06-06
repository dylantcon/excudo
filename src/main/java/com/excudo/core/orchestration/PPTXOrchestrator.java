package com.excudo.core.orchestration;
import com.excudo.core.results.SlideExecutionResult;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.results.ExecutionResult;
// Transaction support removed - using Command pattern instead
import com.excudo.core.validation.ValidationResult;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeStyle;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * High-level orchestrator for PowerPoint presentation operations.
 * Provides a fluent API for complex presentation manipulations with
 * transaction support, validation, and LLM integration capabilities.
 * 
 * This is the primary interface for the Model layer in the MVC architecture.
 */
public interface PPTXOrchestrator {
    
    // ========== INITIALIZATION ==========
    
    /**
     * Initialize orchestrator with an extracted PPTX directory
     * @param pptxDirectory The extracted PPTX directory
     * @return Result indicating success/failure of initialization
     */
    ExecutionResult<OrchestrationContext> initialize(File pptxDirectory);

    /**
     * Initialize orchestrator with an in-memory PPTXDocument.
     * Materializes to a temp directory for backward-compatible file-based components.
     */
    default ExecutionResult<OrchestrationContext> initialize(PPTXDocument pptxDocument) {
        throw new UnsupportedOperationException("In-memory initialization not supported by this implementation");
    }

    /**
     * Get the current orchestration context
     * @return Current context or empty if not initialized
     */
    java.util.Optional<OrchestrationContext> getContext();
    
    // ========== SLIDE OPERATIONS ==========
    
    /**
     * Create a new blank slide at the specified position
     * @param position Position to insert (1-based)
     * @param title Title for the new slide
     * @return Result containing slide creation details
     */
    SlideExecutionResult createSlide(int position, String title);
    
    /**
     * Create a new slide with a specific layout at the specified position
     * @param position Position to insert (1-based)
     * @param title Title for the new slide
     * @param layoutId Layout ID (e.g., "slideLayout1", "slideLayout2")
     * @return Result containing slide creation details
     */
    SlideExecutionResult createSlide(int position, String title, String layoutId);
    
    /**
     * Copy an existing slide to a new position
     * @param sourceSlide Source slide number (1-based)
     * @param targetPosition Target position (1-based) 
     * @param newTitle New title for the copied slide
     * @return Result containing slide copy details
     */
    SlideExecutionResult copySlide(int sourceSlide, int targetPosition, String newTitle);
    
    /**
     * Delete a slide at the specified position
     * @param slideNumber Slide number to delete (1-based)
     * @return Result containing deletion details
     */
    SlideExecutionResult deleteSlide(int slideNumber);
    
    /**
     * Restore a previously deleted slide by leveraging existing createSlide logic
     * @param slideNumber Position where slide should be restored (1-based)
     * @param slideData The slide XML content to restore
     * @param relationshipData The relationship data to restore (optional)
     * @param notesData The notes slide data to restore (optional)
     * @return Result containing restoration details
     */
    SlideExecutionResult restoreSlide(int slideNumber, String slideData, 
                                    String relationshipData, String notesData);
    
    /**
     * Move a slide from one position to another
     * @param fromPosition Current position (1-based)
     * @param toPosition Target position (1-based)
     * @return Result containing move details
     */
    SlideExecutionResult moveSlide(int fromPosition, int toPosition);
    
    // ========== BATCH OPERATIONS ==========
    
    /**
     * Create multiple slides in a single transaction
     * @param slideSpecs List of slide specifications
     * @return Result containing batch creation details
     */
    ExecutionResult<BatchExecutionResult> createSlidesInBatch(List<SlideSpecification> slideSpecs);
    
    // executeOperationBatch method removed - vestigial Operation semantics replaced by Command pattern
    
    // ========== TRANSACTION MANAGEMENT ==========
    // NOTE: Transaction support replaced by Command pattern with undo/redo
    // Use CommandInvoker for transaction-like behavior with rollback support
    
    // ========== VALIDATION ==========
    
    /**
     * Validate the entire presentation for consistency
     * @return Comprehensive validation result
     */
    ValidationResult validatePresentation();
    
    /**
     * Validate a specific slide
     * @param slideNumber Slide number to validate (1-based)
     * @return Validation result for the slide
     */
    ValidationResult validateSlide(int slideNumber);
    
    /**
     * Validate a proposed operation without executing it
     * @param operation Operation to validate
     * @return Validation result
     */
    // validateOperation method removed - vestigial Operation semantics replaced by Command pattern
    // Use Command.canExecute() or validation in Command constructors instead
    
    // ========== PRESENTATION METADATA ==========
    
    /**
     * Check whether the active theme uses a dark background.
     * @return true if the slide master's clrMap maps bg1 to dk1
     */
    boolean isDarkTheme();

    /**
     * Get metadata about the current presentation
     * @return Presentation metadata
     */
    PresentationMetadata getPresentationMetadata();
    
    /**
     * Get information about a specific slide
     * @param slideNumber Slide number (1-based)
     * @return Slide metadata
     */
    java.util.Optional<SlideMetadata> getSlideMetadata(int slideNumber);
    
    /**
     * Get list of all slides with basic metadata
     * @return List of slide metadata
     */
    List<SlideMetadata> getAllSlideMetadata();
    
    // ========== LLM INTEGRATION ==========
    
    /**
     * Generate a human-readable summary of recent operations
     * @param operationCount Number of recent operations to include
     * @return Summary text suitable for LLM context
     */
    String generateOperationSummary(int operationCount);
    
    /**
     * Get structured data for LLM integration
     * @return Map of key-value pairs for LLM context
     */
    Map<String, Object> getLLMIntegrationData();
    
    /**
     * Apply LLM-suggested changes with validation
     * @param suggestions List of LLM-generated suggestions
     * @return Results of applying suggestions
     */
    ExecutionResult<BatchExecutionResult> applyLLMSuggestions(List<LLMSuggestion> suggestions);
    
    // ========== SMART CONTENT OPERATIONS ==========
    
    /**
     * Inject enhanced content (icons, images, smart shapes) into a slide.
     * Uses SmartContentEnhancer for intelligent positioning and content analysis.
     * 
     * @param slideNumber The slide number (1-based)
     * @param iconKeyword The keyword for content search
     * @param templateStyle The template style to apply (optional)
     * @param geometry The geometry parameters for placement
     * @return Result containing list of added shape SPIDs for undo support
     */
    ExecutionResult<List<Integer>> injectEnhancedContent(int slideNumber, String iconKeyword, 
                                                        String templateStyle, Map<String, Object> geometry);
    
    /**
     * Add a new shape to a slide using the ShapeFactory system.
     * 
     * @param slideNumber The slide number (1-based)
     * @param shapeType The type of shape to create
     * @param geometry The shape geometry (position and size)
     * @param text The text content (can be null for non-text shapes)
     * @param shapeName The name for the shape
     * @return Result containing the created shape SPID
     */
    ExecutionResult<Integer> addShape(int slideNumber, SlideShape.ShapeType shapeType,
                                     ShapeGeometry geometry, String text, String shapeName);

    ExecutionResult<Integer> addShape(int slideNumber, SlideShape.ShapeType shapeType,
                                     ShapeGeometry geometry, String text, String shapeName,
                                     ShapeStyle style);
    
    /**
     * Remove a shape from a slide by SPID.
     * Used for undo operations and shape management.
     * 
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID to remove
     * @return Result indicating success/failure of removal
     */
    ExecutionResult<Void> removeShape(int slideNumber, int spid);

    /**
     * Add a connector shape (p:cxnSp) to a slide.
     *
     * @param slideNumber The slide number (1-based)
     * @param connectorType Connector type: line, straight, elbow, curved
     * @param geometry Position and size in EMUs
     * @param headEnd Head end type (none, triangle, arrow) or null
     * @param tailEnd Tail end type (none, triangle, arrow) or null
     * @param lineColor Line color as hex or scheme name, or null for theme default
     * @param startSpid SPID of the shape to connect from, or null
     * @param startIdx Connection point index on start shape, or null
     * @param endSpid SPID of the shape to connect to, or null
     * @param endIdx Connection point index on end shape, or null
     * @param customPath Custom geometry path notation, or null for preset
     * @return Result containing the created connector SPID
     */
    ExecutionResult<Integer> addConnector(int slideNumber, String connectorType, ShapeGeometry geometry,
        String headEnd, String tailEnd, String lineColor, String lineStyle,
        Integer startSpid, Integer startIdx, Integer endSpid, Integer endIdx, String customPath);

    /**
     * Add a picture ({@code <p:pic>}) to a slide pointing at media bytes
     * that already live in the deck. The {@code blipRef.mediaPartName}
     * must resolve to an existing {@link com.excudo.core.model.MediaElement}
     * in {@link com.excudo.core.model.PPTXDocument}; the implementation
     * allocates a fresh slide-local rId via the relationship manager,
     * allocates a SPID, and writes the {@code <p:pic>} element via the
     * shape writer. Fails loudly if the media part is missing -- never
     * silently produces an empty picture frame.
     *
     * @param slideNumber 1-based slide number
     * @param blipRef     the canonical media-part reference (mediaPartName required)
     * @param geometry    position + size on the slide
     * @param name        optional name for the cNvPr element; null falls
     *                    back to "Picture {SPID}"
     * @return Result containing the allocated picture SPID
     */
    ExecutionResult<Integer> addPictureShape(int slideNumber,
        com.excudo.core.model.BlipRef blipRef,
        ShapeGeometry geometry,
        String name);

    /**
     * Get the current text content of a shape.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @return The text content, or null if not found
     */
    String getShapeText(int slideNumber, int spid);

    /**
     * Edit the text content of a shape, replacing its existing text.
     * Automatically handles bullet point detection and formatting.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @param newText The new text content
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> editShapeText(int slideNumber, int spid, String newText);

    /**
     * Replace a shape's text body with a richly-formatted TextBody model.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @param textBody The new text body model
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> setTextBody(int slideNumber, int spid, com.excudo.core.model.TextBody textBody);

    /**
     * Set body properties (a:bodyPr) on a shape, and optionally mark it as a textbox.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @param bodyProperties The body properties to apply
     * @param textBox Whether to mark the shape as a textbox (cNvSpPr/@txBox="1")
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> setBodyProperties(int slideNumber, int spid,
                                            com.excudo.core.model.BodyProperties bodyProperties, boolean textBox);

    /**
     * Capture a shape's DOM element as a deep clone, without modifying the slide.
     * Used by Commands to snapshot state before destructive operations (undo support).
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID to capture
     * @return Result containing the cloned DOM element
     */
    ExecutionResult<org.w3c.dom.Element> captureShapeElement(int slideNumber, int spid);

    /**
     * Restore a previously captured shape element back into the slide's spTree.
     * Used for undo of remove-shape operations.
     *
     * @param slideNumber The slide number (1-based)
     * @param removedElement The cloned DOM element to restore
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> restoreShape(int slideNumber, org.w3c.dom.Element removedElement);
    
    /**
     * Group multiple shapes into a single p:grpSp element.
     *
     * @param slideNumber The slide number (1-based)
     * @param spids       List of SPIDs to include in the group (must be at least 2)
     * @return Result containing the SPID allocated to the new group element
     */
    ExecutionResult<Integer> groupShapes(int slideNumber, java.util.List<Integer> spids);

    /**
     * Dissolve a group shape, promoting its children back into the spTree.
     *
     * @param slideNumber The slide number (1-based)
     * @param groupSpid   SPID of the p:grpSp element to dissolve
     * @return Result containing the list of child SPIDs that were promoted
     */
    ExecutionResult<java.util.List<Integer>> ungroupShape(int slideNumber, int groupSpid);

    /**
     * Copy the visual style (spPr) from one shape to one or more target shapes.
     * Each target's position and size are preserved.
     *
     * @param slideNumber The slide number (1-based)
     * @param sourceSpid  SPID of the shape whose style to copy
     * @param targetSpids List of SPIDs to restyle
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> copyShapeStyle(int slideNumber, int sourceSpid,
                                         java.util.List<Integer> targetSpids);

    /**
     * Update font properties on all text runs in an existing shape.
     *
     * @param slideNumber    The slide number (1-based)
     * @param spid           The shape SPID
     * @param fontProperties Map of font properties: "family", "size", "bold", "italic",
     *                       "underline", "color"
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> updateShapeTextProperties(int slideNumber, int spid,
                                                    java.util.Map<String, Object> fontProperties);

    /**
     * Apply a ShapeStyle (fill, line, theme ref) to an existing shape.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid        The shape SPID
     * @param style       The ShapeStyle to apply
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> updateShapeStyle(int slideNumber, int spid, ShapeStyle style);

    /**
     * Duplicate a shape within the same slide, offsetting the clone's position.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid        SPID of the shape to duplicate
     * @param offsetX     Horizontal offset in EMUs for the clone's position
     * @param offsetY     Vertical offset in EMUs for the clone's position
     * @return Result containing the new SPID allocated to the clone
     */
    ExecutionResult<Integer> duplicateShape(int slideNumber, int spid, long offsetX, long offsetY);

    /**
     * Add animation using an AnimationBinding.
     * This is the preferred method as it leverages the enhanced AnimationBinding
     * with full parameter support and type safety.
     * 
     * @param slideNumber The slide number (1-based)
     * @param animationBinding The complete animation specification
     * @return Result containing the animation ID for undo purposes
     */
    ExecutionResult<String> addAnimation(int slideNumber, AnimationBinding animationBinding);
    
    /**
     * Add animation using an AnimationBinding with session-scoped GroupIdManager.
     * This ensures proper group ID management across multiple animations in the same session.
     *
     * @param slideNumber The slide number (1-based)
     * @param animationBinding The complete animation specification
     * @param groupIdManager The session-scoped GroupIdManager for consistent group ID allocation
     * @return Result containing the animation ID for undo purposes
     */
    ExecutionResult<String> addAnimation(int slideNumber, AnimationBinding animationBinding,
                                       com.excudo.xml.writers.animations.GroupIdManager groupIdManager);

    /**
     * Remove an animation from a slide by its timing node ID.
     *
     * @param slideNumber The slide number (1-based)
     * @param timingNodeId The cTn id of the animation to remove
     * @return Result indicating success/failure of removal
     */
    ExecutionResult<Void> removeAnimation(int slideNumber, int timingNodeId);

    /**
     * Update properties of an existing animation on a slide.
     *
     * @param slideNumber The slide number (1-based)
     * @param timingNodeId The cTn id of the animation to update
     * @param properties Map of property names to new values (duration, delay, presetSubtype)
     * @return Result indicating success/failure of update
     */
    ExecutionResult<Void> updateAnimation(int slideNumber, int timingNodeId, Map<String, String> properties);
    
    /**
     * Edit bullet point content and formatting.
     * 
     * @param slideNumber The slide number (1-based)
     * @param spid The SPID of the text shape containing bullet points
     * @param operation The operation type: "add", "edit", "remove", "reorder"
     * @param bulletIndex The bullet point index (0-based, -1 for append)
     * @param newText The new text content (null for remove operation)
     * @param bulletStyle The bullet style (null to keep current)
     * @return Result containing operation details for undo support
     */
    ExecutionResult<String> editBulletPoint(int slideNumber, int spid, String operation,
                                          int bulletIndex, String newText, String bulletStyle);

    /**
     * Set a hyperlink action on a shape (action buttons, navigation).
     * Optionally embeds an audio file to play when the action triggers.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID to attach the action to
     * @param actionType The action type: nextslide, previousslide, firstslide, lastslide, endshow, noaction
     * @param soundFile Optional audio file path to embed (null for no sound)
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> setAction(int slideNumber, int spid, String actionType, String soundFile);

    /**
     * Add or update speaker notes on a slide.
     * Creates a new notes slide if none exists, or updates existing notes content.
     *
     * @param slideNumber The slide number (1-based)
     * @param notesText The notes text content (supports newlines for multiple paragraphs)
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> addNotes(int slideNumber, String notesText);

    // ========== TRANSITION OPERATIONS ==========

    /**
     * Set a slide transition effect.
     *
     * @param slideNumber The slide number (1-based)
     * @param transitionType The transition type
     * @param speed Speed: "slow", "med", "fast" (null defaults to "med")
     * @param advanceTimeMs Auto-advance time in milliseconds (null for manual only)
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> setTransition(int slideNumber, com.excudo.core.model.TransitionType transitionType,
                                        String speed, Integer advanceTimeMs);

    /**
     * Remove a transition from a slide.
     *
     * @param slideNumber The slide number (1-based)
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> removeTransition(int slideNumber);

    /**
     * Update the geometry (position/size) of an existing shape.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @param newGeometry The new geometry to apply
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> updateShapeGeometry(int slideNumber, int spid, ShapeGeometry newGeometry);

    /**
     * Rename a shape via the {@code cNvPr/@name} attribute.
     *
     * @param slideNumber the slide containing the shape
     * @param spid        the shape SPID
     * @param newName     the new name (null treated as empty string)
     * @return result indicating success / failure
     */
    ExecutionResult<Void> updateShapeName(int slideNumber, int spid, String newName);

    /**
     * Toggle the {@code cNvSpPr/@txBox} marker on a shape in place.
     * Doesn't touch geometry, style, or text body -- it's a narrow
     * metadata flip.
     */
    ExecutionResult<Void> updateShapeTextBoxFlag(int slideNumber, int spid, boolean flag);

    /**
     * Replace the run at {@code (paragraphIdx, runIdx)} on a shape's
     * txBody with a freshly-rendered run from {@code newRun}. Narrower
     * than {@code setTextBody} -- updates a single run's formatting in
     * place, preserving every other paragraph and run.
     */
    ExecutionResult<Void> updateRunFormat(int slideNumber, int spid,
                                          int paragraphIdx, int runIdx,
                                          com.excudo.core.model.TextRun newRun);

    /** Move a top-level shape into an existing group, preserving its
     *  visual position via the inverse coordinate transform. */
    ExecutionResult<Void> addToGroup(int slideNumber, int groupSpid, int childSpid);

    /** Move a grouped child out to top-level, preserving visual position. */
    ExecutionResult<Void> detachFromGroup(int slideNumber, int childSpid);

    /**
     * Reorder a shape's z-order position in the slide's shape tree.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @param operation Z-order operation: BRING_FRONT, SEND_BACK, BRING_FORWARD, SEND_BACKWARD
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> reorderShape(int slideNumber, int spid, String operation);

    // ========== SLIDE MASTER CRUD ==========

    /**
     * Edit a text style level on the slide master.
     * @param target "title", "body", or "other"
     * @param level 1-9 (maps to a:lvl1pPr through a:lvl9pPr)
     * @param updates Map of property names to values
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> editMasterStyle(String target, int level, java.util.Map<String, Object> updates);

    /**
     * Get current text styles from the slide master.
     * @return Map with keys titleStyle/bodyStyle/otherStyle, each mapping to TextLevelStyle[]
     */
    java.util.Map<String, com.excudo.core.themes.TextLevelStyle[]> getMasterStyles();

    /**
     * Set a color mapping attribute on the slide master's p:clrMap.
     * @param logicalColor e.g., "bg1", "tx1"
     * @param themeColor e.g., "dk1", "lt1"
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> setClrMap(String logicalColor, String themeColor);

    /**
     * Get the current color map from the slide master.
     * @return Map of logical color names to theme color names
     */
    java.util.Map<String, String> getClrMap();

    /**
     * Resolve the background color hex for a slide (walks slide > master hierarchy).
     * @return hex string like "#103C3F", or null if no background defined
     */
    String getBackgroundColorHex(int slideNumber);

    /**
     * Resolve the slide's effective background as a typed descriptor.
     * Returns a {@link com.excudo.core.model.SlideBackground.BlipImage}
     * for theme wallpaper-style backgrounds (with duotone info populated
     * when present), {@link com.excudo.core.model.SlideBackground.Solid}
     * for flat color backgrounds, or null when none is defined.
     */
    com.excudo.core.model.SlideBackground getSlideBackground(int slideNumber);

    /**
     * Set the slide master background.
     * @param fillIndex bgRef idx value
     * @param schemeColor scheme color ref
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> setMasterBackground(int fillIndex, String schemeColor);

    /**
     * Populate a:objectDefaults in theme1.xml.
     * @param fontColor scheme color ref for default shape text
     * @param lineWidth line width in EMUs, or null
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> setObjectDefaults(String fontColor, Integer lineWidth);

    /**
     * Get a summary of the slide master state.
     * @return Map containing clrMap, txStyles, background, placeholder count
     */
    java.util.Map<String, Object> getMasterInfo();

    // ========== SLIDE LAYOUT CRUD ==========

    /**
     * Duplicate an existing slide layout.
     * @param sourceLayoutId Layout to duplicate (e.g., "slideLayout2")
     * @param newName Display name for the new layout
     * @return Result containing the new layout ID
     */
    ExecutionResult<String> duplicateLayout(String sourceLayoutId, String newName);

    /**
     * Delete a slide layout. Fails if any slides reference it.
     * Renumbers subsequent layouts to maintain contiguous numbering.
     * @param layoutId Layout to delete (e.g., "slideLayout10")
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> deleteLayout(String layoutId);

    /**
     * Rename a layout's display name.
     * @param layoutId Layout to rename
     * @param newName New display name
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> renameLayout(String layoutId, String newName);

    /**
     * Add a placeholder to an existing layout.
     * @param layoutId The layout to modify
     * @param type Placeholder type (e.g., "obj", "pic", "title")
     * @param idx Placeholder index
     * @param x X position in EMUs
     * @param y Y position in EMUs
     * @param cx Width in EMUs
     * @param cy Height in EMUs
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> addPlaceholder(String layoutId, String type, int idx,
                                          long x, long y, long cx, long cy);

    /**
     * Remove a placeholder from a layout by its idx.
     * @param layoutId The layout to modify
     * @param idx Placeholder index to remove
     * @return Result indicating success/failure
     */
    ExecutionResult<Void> removePlaceholder(String layoutId, int idx);
    
    // ========== FILE OPERATIONS ==========
    
    /**
     * Create a new empty presentation from scratch with the specified theme.
     * No seeder/template file required.
     * @param themeId Theme to apply (e.g. "minimal", "corporate", "academic")
     * @return Result containing presentation metadata
     */
    ExecutionResult<PresentationMetadata> createNewPresentation(String themeId);

    /**
     * Load a presentation from a PPTX file
     * @param pptxFile The PPTX file to load
     * @return Result containing presentation metadata or loading errors
     */
    ExecutionResult<PresentationMetadata> loadPresentation(File pptxFile);
    
    /**
     * Get the file for a specific slide
     * @param slideNumber The slide number (1-based)
     * @return The slide XML file, or null if not found
     */
    File getSlideFile(int slideNumber);
    
    /**
     * Save the current presentation to a PPTX file
     * @param pptxFile The file to save to
     * @return Result indicating success/failure of saving
     */
    ExecutionResult<File> savePresentation(File pptxFile);
    
    // ========== LIFECYCLE ==========
    
    /**
     * Finalize all operations and prepare for PPTX packaging
     * @return Result indicating readiness for packaging
     */
    ExecutionResult<FinalizationResult> finalizeOperations();
    
    /**
     * Finalize the presentation (alias for finalizeOperations for compatibility)
     * @return Result indicating readiness for packaging
     */
    default ExecutionResult<FinalizationResult> finalizePresentation() {
        return finalizeOperations();
    }
    
    /**
     * Clean up resources and close orchestrator
     */
    void close();
    
    /**
     * Get current orchestrator state
     * @return Current state of the orchestrator
     */
    OrchestratorState getState();
    
    /**
     * Get the shared ContextService for cached slide reads.
     * Returns null if the orchestrator is not initialized.
     */
    default com.excudo.core.services.ContextService getContextService() {
        return getContext().map(OrchestrationContext::getContextService).orElse(null);
    }

    /**
     * Get parsed slide data for analysis
     * @param slideNumber The slide number (1-based)
     * @return Result containing ParsedSlideData
     */
    ExecutionResult<com.excudo.core.model.ParsedSlideData> getSlideData(int slideNumber);
    
    /**
     * Get shape registry for a specific slide
     * @param slideNumber The slide number (1-based)
     * @return Result containing ShapeRegistry
     */
    ExecutionResult<com.excudo.core.model.ShapeRegistry> getShapeRegistry(int slideNumber);
    
    // ========== UTILITY OPERATIONS ==========
    
    /**
     * Get all available animation types.
     * @return List of all animation types with descriptions
     */
    List<com.excudo.core.model.AnimationType> getAvailableAnimationTypes();
    
    /**
     * Dump timing XML structure from a slide for analysis.
     * @param slideNumber The slide number (1-based)
     * @return Result containing the timing XML as a string
     */
    ExecutionResult<String> dumpTimingXML(int slideNumber);
    
    /**
     * Dump timing structures in bulk with slide range support (matching original functionality).
     * @param slideRange slide specification: "1", "1-5", "all"
     * @param writeToFile whether to write to timestamped log files
     * @return Result with summary of dumped timing structures
     */
    ExecutionResult<String> dumpTimingsBulk(String slideRange, boolean writeToFile);
    
    /**
     * Dump shape XML structure for analysis.
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @return Result containing the shape XML as a string
     */
    ExecutionResult<String> dumpShapeXML(int slideNumber, int spid);
    
    /**
     * Dump shapes in bulk with slide range support (matching original functionality).
     * @param slideRange slide specification: "1", "1-5", "all"
     * @param writeToFile whether to write to timestamped log files
     * @return Result with summary of dumped shapes
     */
    ExecutionResult<String> dumpShapesBulk(String slideRange, boolean writeToFile);
    
}