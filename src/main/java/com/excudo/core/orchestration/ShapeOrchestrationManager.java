package com.excudo.core.orchestration;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.orchestration.OrchestrationContext;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.OOXMLAttributeOrder;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Manages shape-level orchestration operations with centralized XML handling.
 * 
 * This service extracts shape manipulation logic from PPTXOrchestratorImpl,
 * providing clean delegation for shape operations while centralizing
 * XML parsing/saving boilerplate.
 */
public class ShapeOrchestrationManager {
    
    private static final ComponentLogger logger = Logger.getLogger(ShapeOrchestrationManager.class);
    
    private final OrchestrationContext context;
    
    /**
     * Create a ShapeOrchestrationManager with the given orchestration context.
     * 
     * @param context The orchestration context providing access to managers and state
     */
    public ShapeOrchestrationManager(OrchestrationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("OrchestrationContext cannot be null");
        }
        this.context = context;
    }
    
    /**
     * Get text content from a shape without XML boilerplate.
     * 
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @return The shape text content, or null if not found
     */
    public String getShapeText(int slideNumber, int spid) {
        try {
            com.excudo.core.services.ContextService cs = context.getContextService();
            if (cs != null) {
                com.excudo.core.services.ContextService.SlideContext slideCtx =
                    cs.getSlideContext(slideNumber);
                com.excudo.core.model.SlideShape shape =
                    slideCtx.getSlideData().getShapeRegistry().getShape(spid);
                return shape != null ? shape.getTextContent() : null;
            }

            // Fallback if ContextService unavailable
            File slideFile = getSlideFile(slideNumber);
            if (slideFile == null) {
                return null;
            }
            com.excudo.xml.parsers.SlideXMLParser parser =
                new com.excudo.xml.parsers.SlideXMLParser();
            com.excudo.core.model.ParsedSlideData slideData =
                parser.parseSlide(slideFile);
            com.excudo.core.model.SlideShape shape =
                slideData.getShapeRegistry().getShape(spid);
            return shape != null ? shape.getTextContent() : null;

        } catch (Exception e) {
            logger.error("Failed to get shape text for SPID {} on slide {}: {}", spid, slideNumber, e.getMessage());
            return null;
        }
    }
    
    /**
     * Edit shape text using centralized XML handling.
     * 
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @param newText The new text content
     * @return Operation result
     */
    public ExecutionResult<Void> editShapeText(int slideNumber, int spid, String newText) {
        return performShapeXMLOperation(slideNumber, "EditShapeText", (document, writer) -> {
            writer.updateShapeText(spid, newText);
            return null; // Void operation
        });
    }

    /**
     * Replace a shape's text body with a richly-formatted TextBody model.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @param textBody The new text body model
     * @return Operation result
     */
    public ExecutionResult<Void> setTextBody(int slideNumber, int spid, com.excudo.core.model.TextBody textBody) {
        return performShapeXMLOperation(slideNumber, "SetTextBody", (document, writer) -> {
            writer.replaceTextBody(spid, textBody);
            return null;
        });
    }

    /**
     * Add a shape using centralized XML handling.
     * 
     * @param slideNumber The slide number (1-based)
     * @param shapeType The type of shape to create
     * @param geometry The shape geometry
     * @param text The shape text content
     * @param shapeName The shape name
     * @return Operation result containing the new shape SPID
     */
    public ExecutionResult<Integer> addShape(int slideNumber, SlideShape.ShapeType shapeType,
                                           ShapeGeometry geometry, String text, String shapeName) {
        return addShape(slideNumber, shapeType, geometry, text, shapeName, null);
    }

    public ExecutionResult<Integer> addShape(int slideNumber, SlideShape.ShapeType shapeType,
                                           ShapeGeometry geometry, String text, String shapeName,
                                           ShapeStyle style) {
        return performShapeXMLOperation(slideNumber, "AddShape", (document, writer) -> {
            int newSpid = writer.injectBasicShapeWithSlideContext(shapeType, geometry, text, shapeName, slideNumber, style);
            logger.info("Added {} shape '{}' with SPID {} to slide {}", shapeType, shapeName, newSpid, slideNumber);
            return newSpid;
        });
    }
    
    /**
     * Remove a shape using centralized XML handling.
     * 
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID to remove
     * @return Operation result
     */
    public ExecutionResult<Void> removeShape(int slideNumber, int spid) {
        return performShapeXMLOperation(slideNumber, "RemoveShape", (document, writer) -> {
            logger.info("Attempting to remove shape with SPID {} from slide {}", spid, slideNumber);

            // Remove the shape and handle media cleanup
            com.excudo.xml.writers.SlideXMLWriter.ShapeRemovalResult removalResult =
                writer.removeShapeBySpid(spid);

            // Clean up media relationships if needed
            if (removalResult.hasMediaRelationship()) {
                String relationshipId = removalResult.getRelationshipId();
                logger.info("Shape {} is a picture with relationship {} - performing media cleanup", spid, relationshipId);
                try {
                    boolean mediaRemoved = context.getRelationshipManager()
                        .removeMediaRelationship(slideNumber, relationshipId);
                    if (mediaRemoved) {
                        logger.info("Successfully removed media relationship {} for picture {}", relationshipId, spid);
                    } else {
                        logger.warn("Failed to remove media relationship {} for picture {}", relationshipId, spid);
                    }
                } catch (Exception mediaException) {
                    logger.warn("Error removing media relationship for picture {}: {}", spid, mediaException.getMessage());
                }
            }

            return null;
        });
    }

    /**
     * Capture a shape's DOM element from the slide without modifying it.
     * Used by Commands to snapshot state before destructive operations (undo support).
     */
    public ExecutionResult<org.w3c.dom.Element> captureShapeElement(int slideNumber, int spid) {
        return performShapeXMLOperation(slideNumber, "CaptureShape", (document, writer) -> {
            org.w3c.dom.Element element = writer.findShapeBySpid(spid);
            if (element == null) {
                throw new com.excudo.exceptions.XMLParsingException("Shape with SPID " + spid + " not found on slide " + slideNumber);
            }
            // Deep-clone so the snapshot survives DOM mutations
            return (org.w3c.dom.Element) element.cloneNode(true);
        });
    }

    /**
     * Restore a previously removed shape element back into the slide.
     * Used for undo of remove-shape operations.
     */
    public ExecutionResult<Void> restoreShape(int slideNumber, org.w3c.dom.Element removedElement) {
        return performShapeXMLOperation(slideNumber, "RestoreShape", (document, writer) -> {
            logger.info("Restoring shape element to slide {}", slideNumber);
            writer.restoreShape(removedElement);
            return null;
        });
    }

    /**
     * Inject enhanced content (SmartContent) using centralized handling.
     * 
     * @param slideNumber The slide number (1-based)
     * @param iconKeyword The keyword for content search
     * @param templateStyle The template style to apply
     * @param geometry The geometry parameters
     * @return Operation result containing list of added shape SPIDs
     */
    public ExecutionResult<List<Integer>> injectEnhancedContent(int slideNumber, String iconKeyword,
                                                               String templateStyle, Map<String, Object> geometry) {
        try {
            PPTXDocument pptxDoc = context.getDocument();
            if (pptxDoc == null || !pptxDoc.hasSlide(slideNumber)) {
                return ExecutionResult.failure("InjectEnhancedContent", "Slide " + slideNumber + " not found");
            }

            // Parse slide data from PPTXDocument DOM
            com.excudo.xml.parsers.SlideXMLParser parser =
                new com.excudo.xml.parsers.SlideXMLParser();
            com.excudo.core.model.ParsedSlideData slideData =
                parser.parseSlide(pptxDoc.getSlideDocument(slideNumber), slideNumber);

            // Create SmartContentEnhancer and inject content
            String iconCacheDir = System.getProperty("user.home") + "/.pc-cache/icons";
            String presentationPath = "in-memory";
            com.excudo.core.smartcontent.SmartContentEnhancer enhancer =
                new com.excudo.core.smartcontent.SmartContentEnhancer(
                    iconCacheDir,
                    presentationPath,
                    context.getNotesSlideRegistry(),
                    context.getRelationshipManager()
                );

            // Extract placement preference if provided in geometry map
            com.excudo.core.geometry.IntelligentLayoutEngine.PlacementPreference placementPref =
                com.excudo.core.geometry.IntelligentLayoutEngine.PlacementPreference.AUTO;
            if (geometry != null && geometry.containsKey("placement")) {
                String placementStr = String.valueOf(geometry.get("placement"));
                placementPref = resolveplacementPreference(placementStr);
            }

            // SmartContentEnhancer owns file I/O for injections it performs.
            // Do NOT parse/save the document here -- that causes double-save data loss.
            ExecutionResult<com.excudo.core.model.SlideShape> iconResult =
                enhancer.injectIcon(iconKeyword, slideData, slideNumber, placementPref);

            if (!iconResult.isSuccess()) {
                return ExecutionResult.failure("InjectEnhancedContent", iconResult.getMessage());
            }

            // Get the added shape's SPID
            List<Integer> addedSpids = new java.util.ArrayList<>();
            if (iconResult.getData().isPresent()) {
                com.excudo.core.model.SlideShape addedShape = iconResult.getData().get();
                addedSpids.add(addedShape.getSpid());
            }

            return ExecutionResult.success("InjectEnhancedContent", addedSpids);

        } catch (Exception e) {
            logger.error("Failed to inject enhanced content on slide {}: {}", slideNumber, e.getMessage());
            return ExecutionResult.failure("InjectEnhancedContent", "Failed to inject content: " + e.getMessage(), e);
        }
    }
    
    private com.excudo.core.geometry.IntelligentLayoutEngine.PlacementPreference resolveplacementPreference(String placement) {
        if (placement == null) return com.excudo.core.geometry.IntelligentLayoutEngine.PlacementPreference.AUTO;
        return switch (placement.toLowerCase().replace("_", "-")) {
            case "top-right"    -> com.excudo.core.geometry.IntelligentLayoutEngine.PlacementPreference.TOP_RIGHT;
            case "top-left"     -> com.excudo.core.geometry.IntelligentLayoutEngine.PlacementPreference.TOP_LEFT;
            case "bottom-right" -> com.excudo.core.geometry.IntelligentLayoutEngine.PlacementPreference.BOTTOM_RIGHT;
            case "bottom-left"  -> com.excudo.core.geometry.IntelligentLayoutEngine.PlacementPreference.BOTTOM_LEFT;
            case "center"       -> com.excudo.core.geometry.IntelligentLayoutEngine.PlacementPreference.CENTER;
            default             -> com.excudo.core.geometry.IntelligentLayoutEngine.PlacementPreference.AUTO;
        };
    }

    /**
     * Centralized XML operation handler to eliminate boilerplate.
     *
     * @param slideNumber slide number for operation
     * @param operationName name for logging and error messages
     * @param operation the XML operation to perform
     * @return operation result
     */
    private <T> ExecutionResult<T> performShapeXMLOperation(int slideNumber, String operationName,
                                                           ShapeXMLOperation<T> operation) {
        try {
            // Use PPTXDocument for in-memory DOM access when available
            PPTXDocument pptxDoc = context.getDocument();
            org.w3c.dom.Document document;

            if (pptxDoc != null && pptxDoc.hasSlide(slideNumber)) {
                document = pptxDoc.getSlideDocument(slideNumber);
            } else {
                // Fallback to disk parsing
                File slideFile = getSlideFile(slideNumber);
                if (slideFile == null) {
                    return ExecutionResult.failure(operationName, "Slide " + slideNumber + " not found");
                }
                com.excudo.xml.parsers.SlideXMLParser parser =
                    new com.excudo.xml.parsers.SlideXMLParser();
                document = parser.parseSlideDocument(slideFile);
            }

            // Create SlideXMLWriter
            com.excudo.xml.writers.SlideXMLWriter writer =
                new com.excudo.xml.writers.SlideXMLWriter(document, context.getSpidManager());

            // Execute the specific operation
            T result = operation.execute(document, writer);

            if (pptxDoc != null && pptxDoc.hasSlide(slideNumber)) {
                // Mark dirty -- serialization deferred to save()
                pptxDoc.markSlideDirty(slideNumber);
            } else {
                // Fallback: serialize to disk immediately
                File slideFile = getSlideFile(slideNumber);
                if (slideFile != null) {
                    saveDocumentToFile(document, slideFile);
                }
            }

            // Invalidate cached slide data after mutation
            com.excudo.core.services.ContextService cs = context.getContextService();
            if (cs != null) {
                cs.invalidateSlide(slideNumber);
            }

            // Notify state listeners (GUI, live preview, etc.) that this
            // slide has changed. Every shape mutation comes through this
            // method, so firing once here replaces the per-command
            // one-off calls that only AddShapeCommand remembered to do.
            // Removing the duplicate fire from AddShapeCommand is a
            // companion change; firing twice is a minor perf cost but
            // not correctness-relevant (listeners dedupe by slide id in
            // practice), and we keep the single source of truth here.
            SessionManager.getInstance().fireSlideModified(slideNumber);

            return ExecutionResult.success(operationName, result);

        } catch (Exception e) {
            logger.error("Failed {} on slide {}: {}", operationName, slideNumber, e.getMessage());
            return ExecutionResult.failure(operationName, "Failed " + operationName + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Centralized document saving to eliminate transformer boilerplate.
     * 
     * @param document The DOM document to save
     * @param file The target file
     * @throws Exception if saving fails
     */
    private void saveDocumentToFile(org.w3c.dom.Document document, File file) throws Exception {
        OOXMLAttributeOrder.serialize(document, file);
    }
    
    /**
     * Get the file for a specific slide from the orchestration context.
     * 
     * @param slideNumber The slide number (1-based)
     * @return The slide file, or null if not found
     */
    private File getSlideFile(int slideNumber) {
        PPTXDocument pptxDoc = context.getDocument();
        if (pptxDoc != null && pptxDoc.hasSlide(slideNumber)) {
            // Virtual path; real DOM access uses PPTXDocument
            return new File("ppt/slides/slide" + slideNumber + ".xml");
        }
        return null;
    }
    
    /**
     * Set body properties (a:bodyPr) on a shape, optionally marking it as a textbox.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @param bodyProperties The body properties to apply
     * @param textBox Whether to mark the shape as a textbox
     * @return Operation result
     */
    public ExecutionResult<Void> setBodyProperties(int slideNumber, int spid,
            com.excudo.core.model.BodyProperties bodyProperties, boolean textBox) {
        return performShapeXMLOperation(slideNumber, "SetBodyProperties", (document, writer) -> {
            writer.updateBodyProperties(spid, bodyProperties, textBox);
            return null;
        });
    }

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
    public ExecutionResult<String> editBulletPoint(int slideNumber, int spid, String operation, 
                                                  int bulletIndex, String newText, String bulletStyle) {
        try {
            File slideFile = getSlideFile(slideNumber);
            if (slideFile == null) {
                return ExecutionResult.failure("EditBulletPoint", "Slide " + slideNumber + " not found");
            }
            
            // Get current text content to work with
            String currentText = getShapeText(slideNumber, spid);
            if (currentText == null) {
                return ExecutionResult.failure("EditBulletPoint", "Shape " + spid + " not found on slide " + slideNumber);
            }
            
            // Parse current text into bullet points
            List<String> bulletPoints = parseBulletPointsFromText(currentText);
            
            // Perform the requested operation
            String resultText;
            String operationDescription;
            
            switch (operation.toLowerCase().trim()) {
                case "add":
                    if (bulletIndex == -1 || bulletIndex >= bulletPoints.size()) {
                        // Append to end
                        bulletPoints.add("- " + (newText != null ? newText : "New bullet point"));
                        operationDescription = "Added bullet point at end";
                    } else {
                        // Insert at specific index
                        bulletPoints.add(bulletIndex, "- " + (newText != null ? newText : "New bullet point"));
                        operationDescription = "Added bullet point at index " + bulletIndex;
                    }
                    break;
                    
                case "edit":
                    if (bulletIndex < 0 || bulletIndex >= bulletPoints.size()) {
                        return ExecutionResult.failure("EditBulletPoint", "Bullet index " + bulletIndex + " out of range");
                    }
                    String originalBullet = bulletPoints.get(bulletIndex);
                    String prefix = originalBullet.startsWith("- ") ? "- " : "";
                    bulletPoints.set(bulletIndex, prefix + (newText != null ? newText : ""));
                    operationDescription = "Edited bullet point at index " + bulletIndex;
                    break;
                    
                case "remove":
                    if (bulletIndex < 0 || bulletIndex >= bulletPoints.size()) {
                        return ExecutionResult.failure("EditBulletPoint", "Bullet index " + bulletIndex + " out of range");
                    }
                    bulletPoints.remove(bulletIndex);
                    operationDescription = "Removed bullet point at index " + bulletIndex;
                    break;
                    
                case "reorder":
                    if (bulletIndex < 0 || bulletIndex >= bulletPoints.size()) {
                        return ExecutionResult.failure("EditBulletPoint", "Source bullet index " + bulletIndex + " out of range");
                    }
                    try {
                        int targetIndex = Integer.parseInt(newText);
                        if (targetIndex < 0 || targetIndex >= bulletPoints.size()) {
                            return ExecutionResult.failure("EditBulletPoint", "Target bullet index " + targetIndex + " out of range");
                        }
                        String movedBullet = bulletPoints.remove(bulletIndex);
                        bulletPoints.add(targetIndex, movedBullet);
                        operationDescription = "Moved bullet point from index " + bulletIndex + " to " + targetIndex;
                    } catch (NumberFormatException e) {
                        return ExecutionResult.failure("EditBulletPoint", "Invalid target index for reorder: " + newText);
                    }
                    break;
                    
                default:
                    return ExecutionResult.failure("EditBulletPoint", "Unsupported operation: " + operation);
            }
            
            // Convert bullet points back to text
            resultText = String.join("\n", bulletPoints);
            
            // Update the shape text using existing infrastructure
            ExecutionResult<Void> updateResult = editShapeText(slideNumber, spid, resultText);
            if (!updateResult.isSuccess()) {
                return ExecutionResult.failure("EditBulletPoint", "Failed to update shape text: " + updateResult.getMessage());
            }
            
            logger.info("Bullet point operation '{}' completed on slide {} SPID {}: {}", 
                       operation, slideNumber, spid, operationDescription);
            
            return ExecutionResult.success("EditBulletPoint", operationDescription);
            
        } catch (Exception e) {
            logger.error("Failed to edit bullet point on slide {}: {}", slideNumber, e.getMessage());
            return ExecutionResult.failure("EditBulletPoint", "Failed to edit bullet point: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse text content into individual bullet points.
     * Handles both markdown-style bullets (- text) and plain text.
     * 
     * @param text The text to parse
     * @return List of bullet point strings
     */
    private List<String> parseBulletPointsFromText(String text) {
        List<String> bulletPoints = new java.util.ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return bulletPoints;
        }
        
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                // Ensure bullet format
                if (!line.startsWith("- ") && !line.startsWith("• ")) {
                    line = "- " + line;
                }
                bulletPoints.add(line);
            }
        }
        
        return bulletPoints;
    }

    /**
     * Set a hyperlink action on a shape, with optional audio embedding.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID to attach the action to
     * @param actionType The action type (nextslide, previousslide, etc.)
     * @param soundFile Optional audio file path (null for no sound)
     * @return Operation result
     */
    public ExecutionResult<Void> setAction(int slideNumber, int spid, String actionType, String soundFile) {
        return performShapeXMLOperation(slideNumber, "SetAction", (document, writer) -> {
            String rIdForSound = null;

            // Handle audio file embedding
            if (soundFile != null && !soundFile.isEmpty()) {
                java.io.File audioFile = new java.io.File(soundFile);
                if (audioFile.exists()) {
                    // Audio embedding requires disk path; not yet supported in in-memory mode
                    throw new UnsupportedOperationException("Audio embedding not yet migrated to in-memory path");
                } else {
                    logger.warn("Audio file not found: {}", soundFile);
                }
            }

            writer.injectHyperlink(spid, actionType, rIdForSound,
                soundFile != null ? new java.io.File(soundFile).getName() : null);
            return null;
        });
    }

    private void updateContentTypesForAudio(String extension) {
        // Audio content type updates are handled via PPTXDocument in the in-memory path.
        // Not yet implemented; caller already throws UnsupportedOperationException.
    }

    /**
     * Update geometry (position/size) of an existing shape.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @param newGeometry The new geometry to apply
     * @return Operation result
     */
    public ExecutionResult<Void> updateShapeGeometry(int slideNumber, int spid,
            com.excudo.core.model.ShapeGeometry newGeometry) {
        return performShapeXMLOperation(slideNumber, "UpdateShapeGeometry", (document, writer) -> {
            writer.updateShapeGeometry(spid, newGeometry);
            return null;
        });
    }

    /** Rename a shape via the {@code cNvPr/@name} attribute. */
    public ExecutionResult<Void> updateShapeName(int slideNumber, int spid, String newName) {
        return performShapeXMLOperation(slideNumber, "UpdateShapeName", (document, writer) -> {
            writer.updateShapeName(spid, newName);
            return null;
        });
    }

    /** Toggle the {@code cNvSpPr/@txBox} marker on a shape in place. */
    public ExecutionResult<Void> updateShapeTextBoxFlag(int slideNumber, int spid, boolean flag) {
        return performShapeXMLOperation(slideNumber, "UpdateShapeTextBoxFlag", (document, writer) -> {
            writer.updateShapeTextBoxFlag(spid, flag);
            return null;
        });
    }

    /** Replace the run at {@code (paragraphIdx, runIdx)} on a shape. */
    public ExecutionResult<Void> updateRunFormat(int slideNumber, int spid,
                                                 int paragraphIdx, int runIdx,
                                                 com.excudo.core.model.TextRun newRun) {
        return performShapeXMLOperation(slideNumber, "UpdateRunFormat", (document, writer) -> {
            writer.updateRunFormat(spid, paragraphIdx, runIdx, newRun);
            return null;
        });
    }

    /** Move an existing top-level shape into a group, preserving its
     *  slide-space visual position through the inverse coordinate transform. */
    public ExecutionResult<Void> addToGroup(int slideNumber, int groupSpid, int childSpid) {
        return performShapeXMLOperation(slideNumber, "AddToGroup", (document, writer) -> {
            writer.addToGroup(groupSpid, childSpid);
            return null;
        });
    }

    /** Move a grouped child out to top-level, preserving slide-space position. */
    public ExecutionResult<Void> detachFromGroup(int slideNumber, int childSpid) {
        return performShapeXMLOperation(slideNumber, "DetachFromGroup", (document, writer) -> {
            writer.detachFromGroup(childSpid);
            return null;
        });
    }

    /**
     * Reorder a shape's z-order position in the slide's shape tree.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid The shape SPID
     * @param operation Z-order operation name
     * @return Operation result
     */
    public ExecutionResult<Void> reorderShape(int slideNumber, int spid, String operation) {
        return performShapeXMLOperation(slideNumber, "ReorderShape", (document, writer) -> {
            writer.reorderShape(spid, operation);
            return null;
        });
    }

    /**
     * Group multiple shapes into a single p:grpSp element.
     *
     * @param slideNumber The slide number (1-based)
     * @param spids       List of SPIDs to include in the group
     * @return Operation result containing the new group SPID
     */
    public ExecutionResult<Integer> groupShapes(int slideNumber, java.util.List<Integer> spids) {
        return performShapeXMLOperation(slideNumber, "GroupShapes", (document, writer) -> {
            int groupSpid = writer.groupShapes(spids);
            logger.info("Grouped {} shapes into group SPID {} on slide {}", spids.size(), groupSpid, slideNumber);
            return groupSpid;
        });
    }

    /**
     * Dissolve a group shape, promoting its children back into the spTree.
     *
     * @param slideNumber The slide number (1-based)
     * @param groupSpid   SPID of the p:grpSp element to dissolve
     * @return Operation result containing the list of promoted child SPIDs
     */
    public ExecutionResult<java.util.List<Integer>> ungroupShape(int slideNumber, int groupSpid) {
        return performShapeXMLOperation(slideNumber, "UngroupShape", (document, writer) -> {
            java.util.List<Integer> childSpids = writer.ungroupShape(groupSpid);
            logger.info("Ungrouped SPID {} into {} shapes on slide {}", groupSpid, childSpids.size(), slideNumber);
            return childSpids;
        });
    }

    /**
     * Copy the visual style (spPr) from one shape to one or more target shapes.
     *
     * @param slideNumber The slide number (1-based)
     * @param sourceSpid  SPID of the source shape
     * @param targetSpids List of target SPIDs to restyle
     * @return Operation result
     */
    public ExecutionResult<Void> copyShapeStyle(int slideNumber, int sourceSpid,
            java.util.List<Integer> targetSpids) {
        return performShapeXMLOperation(slideNumber, "CopyShapeStyle", (document, writer) -> {
            writer.copyShapeStyle(sourceSpid, targetSpids);
            return null;
        });
    }

    /**
     * Update font properties on all text runs in a shape.
     *
     * @param slideNumber   The slide number (1-based)
     * @param spid          The shape SPID
     * @param fontProperties Map of font properties to apply
     * @return Operation result
     */
    public ExecutionResult<Void> updateShapeTextProperties(int slideNumber, int spid,
            java.util.Map<String, Object> fontProperties) {
        return performShapeXMLOperation(slideNumber, "UpdateShapeTextProperties", (document, writer) -> {
            writer.updateShapeTextProperties(spid, fontProperties);
            return null;
        });
    }

    /**
     * Apply a ShapeStyle (fill, line, theme ref) to an existing shape.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid        The shape SPID
     * @param style       The ShapeStyle to apply
     * @return Operation result
     */
    public ExecutionResult<Void> updateShapeStyle(int slideNumber, int spid, ShapeStyle style) {
        return performShapeXMLOperation(slideNumber, "UpdateShapeStyle", (document, writer) -> {
            writer.updateShapeStyle(spid, style);
            return null;
        });
    }

    /**
     * Duplicate a shape within the same slide.
     *
     * @param slideNumber The slide number (1-based)
     * @param spid        The shape SPID to duplicate
     * @param offsetX     Horizontal offset in EMUs for the clone
     * @param offsetY     Vertical offset in EMUs for the clone
     * @return Operation result containing the new SPID
     */
    public ExecutionResult<Integer> duplicateShape(int slideNumber, int spid,
            long offsetX, long offsetY) {
        return performShapeXMLOperation(slideNumber, "DuplicateShape", (document, writer) -> {
            int newSpid = writer.duplicateShape(spid, offsetX, offsetY);
            logger.info("Duplicated shape SPID {} as SPID {} on slide {}", spid, newSpid, slideNumber);
            return newSpid;
        });
    }

    /**
     * Add a connector shape (p:cxnSp) to a slide.
     */
    public ExecutionResult<Integer> addConnector(int slideNumber, String connectorType, ShapeGeometry geometry,
            String headEnd, String tailEnd, String lineColor, String lineStyle,
            Integer startSpid, Integer startIdx, Integer endSpid, Integer endIdx, String customPath) {
        return performShapeXMLOperation(slideNumber, "AddConnector", (document, writer) -> {
            int spid = writer.injectConnectorShape(connectorType, geometry, headEnd, tailEnd, lineColor, lineStyle,
                startSpid, startIdx, endSpid, endIdx, customPath, slideNumber);
            logger.info("Added {} connector with SPID {} to slide {}", connectorType, spid, slideNumber);
            return spid;
        });
    }

    /**
     * Add a picture shape (p:pic) to a slide pointing at media that
     * already exists in the deck. Allocates a fresh slide-local rId
     * via the relationship manager; fails loudly if {@code blipRef
     * .mediaPartName} doesn't resolve to a known media part (no silent
     * empty-frame fallback per CLAUDE.md).
     */
    public ExecutionResult<Integer> addPictureShape(int slideNumber,
            com.excudo.core.model.BlipRef blipRef,
            ShapeGeometry geometry, String name) {
        return performShapeXMLOperation(slideNumber, "AddPictureShape", (document, writer) -> {
            String partName = blipRef.mediaPartName();
            com.excudo.core.model.MediaElement media = context.getDocument().getMediaPart(partName);
            if (media == null) {
                throw new IllegalStateException(
                    "AddPictureShape: media part '" + partName + "' not in deck. "
                    + "Cross-deck picture apply requires the media bytes to be "
                    + "transferred first; tracked as a deferred backlog item.");
            }
            // Slide rels live in ppt/slides/_rels/slideN.xml.rels; targets
            // are relative to that folder, so "ppt/media/imageN.png" becomes
            // "../media/imageN.png".
            String target = partName.startsWith("ppt/")
                ? "../" + partName.substring("ppt/".length())
                : partName;
            String mediaType = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";
            String rId = context.getRelationshipManager()
                .addMediaRelationship(slideNumber, mediaType, target);
            int spid = writer.injectPictureShape(name, rId, geometry, slideNumber);
            logger.info("Added picture SPID {} (rId {} -> {}) to slide {}",
                spid, rId, partName, slideNumber);
            return spid;
        });
    }

    /**
     * Functional interface for shape XML operations.
     */
    @FunctionalInterface
    private interface ShapeXMLOperation<T> {
        T execute(org.w3c.dom.Document document, com.excudo.xml.writers.SlideXMLWriter writer) throws Exception;
    }
}