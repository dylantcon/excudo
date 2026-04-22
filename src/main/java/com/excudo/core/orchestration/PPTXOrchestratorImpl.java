package com.excudo.core.orchestration;
import com.excudo.core.results.SlideExecutionResult;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.results.ExecutionResult;
// Transaction support removed - using Command pattern instead
import com.excudo.core.validation.ValidationResult;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.PPTXDocument;
import com.excudo.xml.writers.SlideCreator;
import com.excudo.xml.writers.RelationshipManager;
import com.excudo.xml.writers.SPIDManager;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.Collections;

/**
 * Implementation of PPTXOrchestrator interface that coordinates between
 * the legacy orchestrator and the modern interface-based architecture.
 *
 * This class serves as the primary Controller in the MVC architecture,
 * bridging Model operations with View requirements.
 */
public class PPTXOrchestratorImpl implements PPTXOrchestrator {

    // ========== CORE COMPONENTS ==========

    // private final com.excudo.orchestration.PPTXOrchestrator legacyOrchestrator;
    // TransactionManager removed - using Command pattern instead
    private final Map<String, OrchestrationContext> contexts;
    private static final ComponentLogger logger = Logger.getLogger(PPTXOrchestratorImpl.class);

    // ========== STATE ==========

    private OrchestrationContext currentContext;
    private OrchestratorState currentState;

    // ========== OPERATION MANAGERS ==========

    private ShapeOrchestrationManager shapeOrchestrationManager;
    private SlideOrchestrationManager slideOrchestrationManager;
    private PresentationOrchestrationManager presentationOrchestrationManager;
    private ValidationOrchestrationManager validationOrchestrationManager;
    private LLMOrchestrationManager llmOrchestrationManager;
    private AnimationOrchestrationManager animationOrchestrationManager;
    private BatchExecutionOrchestrationManager batchExecutionOrchestrationManager;
    private LayoutOrchestrationManager layoutOrchestrationManager;
    private SlideMasterOrchestrationManager slideMasterOrchestrationManager;
    private UtilityOrchestrationManager utilityOrchestrationManager;

    public PPTXOrchestratorImpl() throws XMLParsingException {
        // this.legacyOrchestrator = new com.excudo.orchestration.PPTXOrchestrator();
        // TransactionManager removed - using Command pattern instead
        this.contexts = new ConcurrentHashMap<>();
        this.currentState = OrchestratorState.CREATED;

        // Initialize operation managers
        // ShapeOrchestrationManager will be initialized when context is available
    }

    // ========== INITIALIZATION ==========

    /**
     * Initialize from an extracted PPTX directory.
     * Loads the directory into a PPTXDocument and delegates to initialize(PPTXDocument).
     */
    @Override
    public ExecutionResult<OrchestrationContext> initialize(File pptxDirectory) {
        try {
            if (pptxDirectory == null || !pptxDirectory.exists() || !pptxDirectory.isDirectory()) {
                return ExecutionResult.failure("Initialize", "Invalid PPTX directory: " + pptxDirectory);
            }

            PPTXDocument pptxDocument = PPTXDocument.load(pptxDirectory, null);
            return initialize(pptxDocument);

        } catch (Exception e) {
            return ExecutionResult.failure("Initialize", "Failed to initialize orchestrator: " + e.getMessage(), e);
        }
    }

    /**
     * Initialize from a PPTXDocument directly (for from-scratch presentations).
     * Constructs all managers from ParsedPresentationState -- no temp dirs, no disk I/O.
     */
    @Override
    public ExecutionResult<OrchestrationContext> initialize(PPTXDocument pptxDocument) {
        try {
            SPIDManager.resetInstance();

            // Parse all derived state from in-memory DOMs in a single pass
            com.excudo.core.model.PPTXDocumentParser.ParsedPresentationState parsedState =
                com.excudo.core.model.PPTXDocumentParser.parse(pptxDocument);

            // Construct managers from parsed state -- no File, no disk
            com.excudo.core.model.LayoutManager layoutManager =
                new com.excudo.core.model.LayoutManager(parsedState.getLayouts());
            RelationshipManager relationshipManager =
                new RelationshipManager(pptxDocument, parsedState);
            SPIDManager spidManager =
                SPIDManager.createFromParsedState(parsedState, layoutManager);
            spidManager.setRelationshipManager(relationshipManager);

            SlideCreator slideCreator = new SlideCreator(relationshipManager, spidManager, layoutManager);
            slideCreator.setPPTXDocument(pptxDocument);

            // Notes registry from parsed state
            com.excudo.utils.NotesSlideRegistry notesSlideRegistry = new com.excudo.utils.NotesSlideRegistry();
            notesSlideRegistry.loadFromParsedState(parsedState.getNotesToSlideMap());

            // Layout/theme init from PPTXDocument
            com.excudo.xml.parsers.SlideXMLParser.SlideLayoutParser.initialize(pptxDocument);
            com.excudo.core.themes.ThemeManager.initialize(pptxDocument);

            Map<String, Object> contextData = new HashMap<>();
            contextData.put("initialized", true);
            contextData.put("inMemory", true);

            OrchestrationContext context = new OrchestrationContext(
                pptxDocument, parsedState, slideCreator, relationshipManager, spidManager,
                notesSlideRegistry, layoutManager, contextData
            );

            String contextId = "inmemory-" + System.currentTimeMillis();
            contexts.put(contextId, context);
            this.currentContext = context;
            this.currentState = OrchestratorState.READY;

            this.shapeOrchestrationManager = new ShapeOrchestrationManager(context);
            this.slideOrchestrationManager = new SlideOrchestrationManager(context);
            this.presentationOrchestrationManager = new PresentationOrchestrationManager(context);
            this.validationOrchestrationManager = new ValidationOrchestrationManager(context);
            this.llmOrchestrationManager = new LLMOrchestrationManager(context);
            this.animationOrchestrationManager = new AnimationOrchestrationManager(context, slideOrchestrationManager);
            this.batchExecutionOrchestrationManager = new BatchExecutionOrchestrationManager(context, slideOrchestrationManager);
            this.layoutOrchestrationManager = new LayoutOrchestrationManager(context);
            this.slideMasterOrchestrationManager = new SlideMasterOrchestrationManager(context);
            this.utilityOrchestrationManager = new UtilityOrchestrationManager(this);

            logger.debug("Initialized orchestrator from PPTXDocument (fully in-memory, no stub dir)");
            return ExecutionResult.success("Initialize", context);

        } catch (Exception e) {
            return ExecutionResult.failure("Initialize", "Failed to initialize from PPTXDocument: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<OrchestrationContext> getContext() {
        return Optional.ofNullable(currentContext);
    }

    // ========== SLIDE OPERATIONS ==========

    @Override
    public SlideExecutionResult createSlide(int position, String title) {
        if (currentContext == null) {
            return SlideExecutionResult.slideActionFailed("Create Slide", position, "Orchestrator not initialized");
        }
        return slideOrchestrationManager.createSlide(position, title);
    }

    @Override
    public SlideExecutionResult createSlide(int position, String title, String layoutId) {
        if (currentContext == null) {
            return SlideExecutionResult.slideActionFailed("Create Slide", position, "Orchestrator not initialized");
        }
        return slideOrchestrationManager.createSlide(position, title, layoutId);
    }

    @Override
    public SlideExecutionResult copySlide(int sourceSlide, int targetPosition, String newTitle) {
        if (currentContext == null) {
            return SlideExecutionResult.slideActionFailed("Copy Slide", targetPosition, "Orchestrator not initialized");
        }

        try {
            // Implementation would use existing slide copy functionality
            Set<Integer> originalSpids = Set.of(1000 + sourceSlide);
            Set<Integer> newSpids = Set.of(1000 + targetPosition);

            return SlideExecutionResult.slideCopied(sourceSlide, targetPosition, originalSpids, newSpids);
        } catch (Exception e) {
            return SlideExecutionResult.slideActionFailed("Copy Slide", targetPosition, e);
        }
    }

    @Override
    public SlideExecutionResult deleteSlide(int slideNumber) {
        if (currentContext == null) {
            return SlideExecutionResult.slideActionFailed("Delete Slide", slideNumber, "Orchestrator not initialized");
        }

        // Delegate to slide manager for deletion logic
        return slideOrchestrationManager.deleteSlide(slideNumber);
    }

    @Override
    public SlideExecutionResult restoreSlide(int slideNumber, String slideData,
                                           String relationshipData, String notesData) {
        if (currentContext == null) {
            return SlideExecutionResult.slideActionFailed("Restore Slide", slideNumber, "Orchestrator not initialized");
        }

        // Delegate to slide manager for restoration logic
        return slideOrchestrationManager.restoreSlide(slideNumber, slideData, relationshipData, notesData);
    }

    @Override
    public SlideExecutionResult moveSlide(int fromPosition, int toPosition) {
        if (currentContext == null) {
            return SlideExecutionResult.slideActionFailed("Move Slide", fromPosition, "Orchestrator not initialized");
        }

        // Delegate to slide manager for move logic
        return slideOrchestrationManager.moveSlide(fromPosition, toPosition);
    }

    // ========== BATCH OPERATIONS ==========

    @Override
    public ExecutionResult<BatchExecutionResult> createSlidesInBatch(List<SlideSpecification> slideSpecs) {
        if (currentContext == null) {
            return ExecutionResult.failure("Batch Create Slides", "Orchestrator not initialized");
        }

        // Delegate to batch execution manager for slide creation
        return batchExecutionOrchestrationManager.createSlidesInBatch(slideSpecs);
    }

    // executeOperationBatch method removed - vestigial Operation semantics replaced by Command pattern
    // Use CommandInvoker.executeCommand() with CompositeCommand for batch operations

    // ========== TRANSACTION MANAGEMENT ==========
    // NOTE: Transaction support removed - using Command pattern with CommandInvoker instead

    // ========== PRESENTATION MANAGEMENT ==========

    @Override
    public ExecutionResult<PresentationMetadata> createNewPresentation(String themeId) {
        try {
            PPTXDocument doc = PresentationScaffolder.scaffoldDocument(themeId);

            ExecutionResult<OrchestrationContext> initResult = initialize(doc);
            if (!initResult.isSuccess()) {
                return ExecutionResult.failure("New Presentation",
                        "Failed to initialize scaffolded presentation: " + initResult.getMessage());
            }

            if (currentContext != null) {
                currentContext.getContextData().put("createdFromScratch", true);
                currentContext.getContextData().put("themeId", themeId);
            }

            PresentationMetadata metadata = presentationOrchestrationManager.getPresentationMetadata();
            logger.info("Created new presentation from scratch with theme: " + themeId);
            return ExecutionResult.success("New Presentation", metadata);

        } catch (Exception e) {
            return ExecutionResult.failure("New Presentation",
                    "Failed to create new presentation: " + e.getMessage(), e);
        }
    }

    @Override
    public ExecutionResult<PresentationMetadata> loadPresentation(File pptxFile) {
        try {
            // Load PPTX directly into memory -- no extraction to temp directory
            PPTXDocument pptxDocument = PPTXDocument.loadFromZip(pptxFile);

            // Initialize orchestrator from in-memory document
            ExecutionResult<OrchestrationContext> initResult = initialize(pptxDocument);
            if (!initResult.isSuccess()) {
                return ExecutionResult.failure("Load Presentation", "Failed to initialize: " + initResult.getMessage());
            }

            // Store original filename in context
            if (currentContext != null) {
                currentContext.getContextData().put("originalFileName", pptxFile.getName());
                currentContext.getContextData().put("originalFilePath", pptxFile.getAbsolutePath());
            }

            // Delegate to presentation manager for loading logic
            return presentationOrchestrationManager.loadPresentation(pptxFile);

        } catch (Exception e) {
            return ExecutionResult.failure("Load Presentation", "Failed to load presentation: " + e.getMessage(), e);
        }
    }

    @Override
    public ExecutionResult<File> savePresentation(File outputFile) {
        if (currentContext == null) {
            return ExecutionResult.failure("Save Presentation", "No presentation loaded");
        }

        // If PPTXDocument is available, use it for dirty-only serialization
        PPTXDocument pptxDoc = currentContext.getDocument();
        if (pptxDoc != null) {
            try {
                // Run finalization (app properties, content types) -- operates on PPTXDocument directly
                ExecutionResult<FinalizationResult> finResult = presentationOrchestrationManager.finalizeOperations();
                if (!finResult.isSuccess()) {
                    logger.warn("Finalization had issues before save: {}", finResult.getMessage());
                }
                pptxDoc.save(outputFile);
                logger.info("Saved presentation via PPTXDocument to: {}", outputFile.getAbsolutePath());
                return ExecutionResult.success("Save Presentation", outputFile);
            } catch (Exception e) {
                logger.error("PPTXDocument save failed: {}", e.getMessage());
                return ExecutionResult.failure("Save Presentation", "Failed to save: " + e.getMessage(), e);
            }
        }

        // Fallback to legacy save path
        return presentationOrchestrationManager.savePresentation(outputFile);
    }

    @Override
    public ValidationResult validatePresentation() {
        if (currentContext == null) {
            return ValidationResult.failure("No presentation loaded");
        }

        // Delegate to validation manager for presentation validation logic
        return validationOrchestrationManager.validatePresentation();
    }

    @Override
    public ExecutionResult<FinalizationResult> finalizeOperations() {
        if (currentContext == null) {
            return ExecutionResult.failure("Finalize Operations", "No presentation loaded");
        }

        // Delegate to presentation manager for finalization logic
        return presentationOrchestrationManager.finalizeOperations();
    }

    // ========== STATE MANAGEMENT ==========

    @Override
    public OrchestratorState getState() {
        return currentState;
    }


    // ========== UTILITY METHODS ==========

    private String generateContextId(File directory) {
        return "ctx_" + directory.getName() + "_" + System.currentTimeMillis();
    }


    // ========== BRIDGE TO LEGACY ORCHESTRATOR ==========

    /**
     * Get access to the legacy orchestrator for operations not yet
     * migrated to the interface
     */
    // public com.excudo.orchestration.PPTXOrchestrator getLegacyOrchestrator() {
    //     return legacyOrchestrator;
    // }


    // ========== MISSING INTERFACE METHODS ==========

    @Override
    public ValidationResult validateSlide(int slideNumber) {
        if (currentContext == null) {
            return ValidationResult.failure("No presentation loaded");
        }

        // Delegate to validation manager for slide validation logic
        return validationOrchestrationManager.validateSlide(slideNumber);
    }

    // validateOperation method removed - vestigial Operation semantics replaced by Command pattern
    // Use Command constructors with validation or pre-execution checks instead

    @Override
    public PresentationMetadata getPresentationMetadata() {
        if (currentContext == null) {
            return new PresentationMetadata(0, "No presentation", Instant.now(), List.of());
        }

        // Delegate to presentation manager for metadata logic
        return presentationOrchestrationManager.getPresentationMetadata();
    }

    @Override
    public boolean isDarkTheme() {
        if (presentationOrchestrationManager == null) {
            return false;
        }
        return presentationOrchestrationManager.isDarkTheme();
    }

    @Override
    public Optional<SlideMetadata> getSlideMetadata(int slideNumber) {
        if (currentContext == null) {
            return Optional.empty();
        }

        // Delegate to presentation manager for slide metadata logic
        return presentationOrchestrationManager.getSlideMetadata(slideNumber);
    }

    @Override
    public List<SlideMetadata> getAllSlideMetadata() {
        if (currentContext == null) {
            return new ArrayList<>();
        }

        // Delegate to presentation manager for all slide metadata logic
        return presentationOrchestrationManager.getAllSlideMetadata();
    }

    @Override
    public String generateOperationSummary(int operationCount) {
        if (currentContext == null) {
            return "No presentation context available for operation summary";
        }

        // Delegate to LLM manager for operation summary generation
        return llmOrchestrationManager.generateOperationSummary(operationCount);
    }

    @Override
    public Map<String, Object> getLLMIntegrationData() {
        if (currentContext == null) {
            Map<String, Object> data = new HashMap<>();
            data.put("state", currentState.toString());
            data.put("hasContext", false);
            data.put("error", "No presentation context available");
            return data;
        }

        // Delegate to LLM manager for integration data collection
        Map<String, Object> data = llmOrchestrationManager.getLLMIntegrationData();
        data.put("state", currentState.toString()); // Add current state
        return data;
    }

    @Override
    public ExecutionResult<BatchExecutionResult> applyLLMSuggestions(List<LLMSuggestion> suggestions) {
        if (currentContext == null) {
            return ExecutionResult.failure("Apply LLM Suggestions", "No presentation loaded");
        }

        // Delegate to LLM manager for suggestion application
        return llmOrchestrationManager.applyLLMSuggestions(suggestions);
    }

    @Override
    public void close() {
        if (currentContext != null) {
            // Close PPTXDocument (deletes lock file)
            PPTXDocument pptxDoc = currentContext.getDocument();
            if (pptxDoc != null) {
                pptxDoc.close();
            }
            currentContext = null;
        }
        this.currentState = OrchestratorState.CLOSED;
    }


    // ========== SLIDE DELETION HELPERS ==========


    /**
     * Get the file for a specific slide
     * @param slideNumber The slide number (1-based)
     * @return The slide XML file, or null if not found
     */
    @Override
    public File getSlideFile(int slideNumber) {
        if (currentContext == null) {
            return null;
        }
        return slideOrchestrationManager.getSlideFile(slideNumber);
    }


    // ========== SHAPE TEXT OPERATIONS ==========

    /**
     * Get the current text content of a shape
     * @param slideNumber The slide number (1-based)
     * @param spid The SPID of the shape
     * @return The current text content, or null if not found
     */
    @Override
    public String getShapeText(int slideNumber, int spid) {
        return shapeOrchestrationManager.getShapeText(slideNumber, spid);
    }

    @Override
    public ExecutionResult<ParsedSlideData> getSlideData(int slideNumber) {
        if (currentContext == null) {
            return ExecutionResult.failure("GetSlideData", "Orchestrator not initialized");
        }
        return slideOrchestrationManager.getSlideData(slideNumber);
    }

    @Override
    public ExecutionResult<ShapeRegistry> getShapeRegistry(int slideNumber) {
        if (currentContext == null) {
            return ExecutionResult.failure("GetShapeRegistry", "Orchestrator not initialized");
        }
        return slideOrchestrationManager.getShapeRegistry(slideNumber);
    }

    /**
     * Update the text content of a shape
     * @param slideNumber The slide number (1-based)
     * @param spid The SPID of the shape to edit
     * @param newText The new text content
     * @return Result indicating success/failure of the text update
     */
    @Override
    public ExecutionResult<Void> editShapeText(int slideNumber, int spid, String newText) {
        return shapeOrchestrationManager.editShapeText(slideNumber, spid, newText);
    }

    @Override
    public ExecutionResult<Void> setTextBody(int slideNumber, int spid, com.excudo.core.model.TextBody textBody) {
        return shapeOrchestrationManager.setTextBody(slideNumber, spid, textBody);
    }

    @Override
    public ExecutionResult<Void> setBodyProperties(int slideNumber, int spid,
                                                   com.excudo.core.model.BodyProperties bodyProperties, boolean textBox) {
        return shapeOrchestrationManager.setBodyProperties(slideNumber, spid, bodyProperties, textBox);
    }


    // ========== SMART CONTENT OPERATIONS ==========

    /**
     * Inject enhanced content using SmartContentEnhancer.
     */
    @Override
    public ExecutionResult<List<Integer>> injectEnhancedContent(int slideNumber, String iconKeyword,
                                                               String templateStyle, Map<String, Object> geometry) {
        return shapeOrchestrationManager.injectEnhancedContent(slideNumber, iconKeyword, templateStyle, geometry);
    }

    /**
     * Add a new shape to a slide using the ShapeFactory system.
     */
    @Override
    public ExecutionResult<Integer> addShape(int slideNumber, SlideShape.ShapeType shapeType,
                                           ShapeGeometry geometry, String text, String shapeName) {
        return shapeOrchestrationManager.addShape(slideNumber, shapeType, geometry, text, shapeName);
    }

    @Override
    public ExecutionResult<Integer> addShape(int slideNumber, SlideShape.ShapeType shapeType,
                                           ShapeGeometry geometry, String text, String shapeName,
                                           ShapeStyle style) {
        return shapeOrchestrationManager.addShape(slideNumber, shapeType, geometry, text, shapeName, style);
    }

    /**
     * Remove a shape from a slide by SPID using SlideXMLWriter.
     * For pictures, this also handles media relationship and file cleanup.
     */
    @Override
    public ExecutionResult<Void> removeShape(int slideNumber, int spid) {
        return shapeOrchestrationManager.removeShape(slideNumber, spid);
    }

    @Override
    public ExecutionResult<Void> updateShapeGeometry(int slideNumber, int spid, ShapeGeometry newGeometry) {
        return shapeOrchestrationManager.updateShapeGeometry(slideNumber, spid, newGeometry);
    }

    @Override
    public ExecutionResult<Void> updateShapeName(int slideNumber, int spid, String newName) {
        return shapeOrchestrationManager.updateShapeName(slideNumber, spid, newName);
    }

    @Override
    public ExecutionResult<Void> updateShapeTextBoxFlag(int slideNumber, int spid, boolean flag) {
        return shapeOrchestrationManager.updateShapeTextBoxFlag(slideNumber, spid, flag);
    }

    @Override
    public ExecutionResult<Void> updateRunFormat(int slideNumber, int spid,
                                                 int paragraphIdx, int runIdx,
                                                 com.excudo.core.model.TextRun newRun) {
        return shapeOrchestrationManager.updateRunFormat(slideNumber, spid, paragraphIdx, runIdx, newRun);
    }

    @Override
    public ExecutionResult<Void> addToGroup(int slideNumber, int groupSpid, int childSpid) {
        return shapeOrchestrationManager.addToGroup(slideNumber, groupSpid, childSpid);
    }

    @Override
    public ExecutionResult<Void> detachFromGroup(int slideNumber, int childSpid) {
        return shapeOrchestrationManager.detachFromGroup(slideNumber, childSpid);
    }

    @Override
    public ExecutionResult<Void> reorderShape(int slideNumber, int spid, String operation) {
        return shapeOrchestrationManager.reorderShape(slideNumber, spid, operation);
    }

    @Override
    public ExecutionResult<Integer> addConnector(int slideNumber, String connectorType, ShapeGeometry geometry,
            String headEnd, String tailEnd, String lineColor, String lineStyle,
            Integer startSpid, Integer startIdx, Integer endSpid, Integer endIdx, String customPath) {
        return shapeOrchestrationManager.addConnector(slideNumber, connectorType, geometry,
            headEnd, tailEnd, lineColor, lineStyle, startSpid, startIdx, endSpid, endIdx, customPath);
    }

    @Override
    public ExecutionResult<org.w3c.dom.Element> captureShapeElement(int slideNumber, int spid) {
        return shapeOrchestrationManager.captureShapeElement(slideNumber, spid);
    }

    @Override
    public ExecutionResult<Void> restoreShape(int slideNumber, org.w3c.dom.Element removedElement) {
        return shapeOrchestrationManager.restoreShape(slideNumber, removedElement);
    }

    @Override
    public ExecutionResult<Integer> groupShapes(int slideNumber, java.util.List<Integer> spids) {
        return shapeOrchestrationManager.groupShapes(slideNumber, spids);
    }

    @Override
    public ExecutionResult<java.util.List<Integer>> ungroupShape(int slideNumber, int groupSpid) {
        return shapeOrchestrationManager.ungroupShape(slideNumber, groupSpid);
    }

    @Override
    public ExecutionResult<Void> copyShapeStyle(int slideNumber, int sourceSpid,
            java.util.List<Integer> targetSpids) {
        return shapeOrchestrationManager.copyShapeStyle(slideNumber, sourceSpid, targetSpids);
    }

    @Override
    public ExecutionResult<Void> updateShapeTextProperties(int slideNumber, int spid,
            java.util.Map<String, Object> fontProperties) {
        return shapeOrchestrationManager.updateShapeTextProperties(slideNumber, spid, fontProperties);
    }

    @Override
    public ExecutionResult<Void> updateShapeStyle(int slideNumber, int spid,
            com.excudo.core.model.ShapeStyle style) {
        return shapeOrchestrationManager.updateShapeStyle(slideNumber, spid, style);
    }

    @Override
    public ExecutionResult<Integer> duplicateShape(int slideNumber, int spid,
            long offsetX, long offsetY) {
        return shapeOrchestrationManager.duplicateShape(slideNumber, spid, offsetX, offsetY);
    }

    @Override
    public ExecutionResult<String> addAnimation(int slideNumber,
                                               com.excudo.core.model.AnimationBinding animationBinding) {
        if (currentContext == null) {
            return ExecutionResult.failure("AddAnimation", "No orchestration context available");
        }

        // Delegate to animation manager
        return animationOrchestrationManager.addAnimation(slideNumber, animationBinding);
    }

    @Override
    public ExecutionResult<String> addAnimation(int slideNumber,
                                               com.excudo.core.model.AnimationBinding animationBinding,
                                               com.excudo.xml.writers.animations.GroupIdManager groupIdManager) {
        if (currentContext == null) {
            return ExecutionResult.failure("AddAnimation", "No orchestration context available");
        }

        // Delegate to animation manager with optional GroupIdManager
        return animationOrchestrationManager.addAnimation(slideNumber, animationBinding, groupIdManager);
    }

    @Override
    public ExecutionResult<Void> removeAnimation(int slideNumber, int timingNodeId) {
        if (currentContext == null) {
            return ExecutionResult.failure("RemoveAnimation", "No orchestration context available");
        }
        return animationOrchestrationManager.removeAnimation(slideNumber, timingNodeId);
    }

    @Override
    public ExecutionResult<Void> updateAnimation(int slideNumber, int timingNodeId, Map<String, String> properties) {
        if (currentContext == null) {
            return ExecutionResult.failure("UpdateAnimation", "No orchestration context available");
        }
        return animationOrchestrationManager.updateAnimation(slideNumber, timingNodeId, properties);
    }

    // ========== BULLET POINT OPERATIONS ==========

    @Override
    public ExecutionResult<String> editBulletPoint(int slideNumber, int spid, String operation,
                                                  int bulletIndex, String newText, String bulletStyle) {
        return shapeOrchestrationManager.editBulletPoint(slideNumber, spid, operation, bulletIndex, newText, bulletStyle);
    }

    // ========== ACTION BUTTON OPERATIONS ==========

    @Override
    public ExecutionResult<Void> setAction(int slideNumber, int spid, String actionType, String soundFile) {
        if (currentContext == null) {
            return ExecutionResult.failure("SetAction", "No orchestration context available");
        }
        return shapeOrchestrationManager.setAction(slideNumber, spid, actionType, soundFile);
    }

    // ========== NOTES OPERATIONS ==========

    @Override
    public ExecutionResult<Void> addNotes(int slideNumber, String notesText) {
        if (currentContext == null) {
            return ExecutionResult.failure("AddNotes", "Orchestrator not initialized");
        }
        try {
            // Notes creation requires PPTXDocument DOM operations
            PPTXDocument pptxDoc = currentContext.getDocument();
            if (pptxDoc == null) {
                return ExecutionResult.failure("Add Notes", "No PPTXDocument available");
            }
            // TODO: Migrate notes creation to in-memory DOM operations
            throw new UnsupportedOperationException("addNotes not yet migrated to in-memory path");
        } catch (Exception e) {
            logger.error("Failed to add notes to slide {}: {}", slideNumber, e.getMessage());
            return ExecutionResult.failure("AddNotes", "Failed: " + e.getMessage(), e);
        }
    }

    private void createNotesSlideOnExtracted(java.io.File extractedDir, int slideNumber, int notesNum, String text) throws Exception {
        org.w3c.dom.Document doc = com.excudo.core.utils.XMLFactoryProvider.createDocument();

        org.w3c.dom.Element notes = doc.createElementNS(com.excudo.core.utils.XMLConstants.PRESENTATION_NS, "p:notes");
        notes.setAttribute("xmlns:a", com.excudo.core.utils.XMLConstants.DRAWING_NS);
        notes.setAttribute("xmlns:r", com.excudo.core.utils.XMLConstants.RELATIONSHIPS_NS);
        notes.setAttribute("xmlns:p", com.excudo.core.utils.XMLConstants.PRESENTATION_NS);
        doc.appendChild(notes);

        org.w3c.dom.Element cSld = doc.createElementNS(com.excudo.core.utils.XMLConstants.PRESENTATION_NS, "p:cSld");
        notes.appendChild(cSld);

        // Shape tree
        org.w3c.dom.Element spTree = doc.createElementNS(com.excudo.core.utils.XMLConstants.PRESENTATION_NS, "p:spTree");
        cSld.appendChild(spTree);

        // Group shape properties
        org.w3c.dom.Element nvGrpSpPr = doc.createElementNS(com.excudo.core.utils.XMLConstants.PRESENTATION_NS, "p:nvGrpSpPr");
        org.w3c.dom.Element grpCNvPr = doc.createElementNS(com.excudo.core.utils.XMLConstants.PRESENTATION_NS, "p:cNvPr");
        grpCNvPr.setAttribute("id", "1");
        grpCNvPr.setAttribute("name", "");
        nvGrpSpPr.appendChild(grpCNvPr);
        nvGrpSpPr.appendChild(doc.createElementNS(com.excudo.core.utils.XMLConstants.PRESENTATION_NS, "p:cNvGrpSpPr"));
        nvGrpSpPr.appendChild(doc.createElementNS(com.excudo.core.utils.XMLConstants.PRESENTATION_NS, "p:nvPr"));
        spTree.appendChild(nvGrpSpPr);

        org.w3c.dom.Element grpSpPr = doc.createElementNS(com.excudo.core.utils.XMLConstants.PRESENTATION_NS, "p:grpSpPr");
        spTree.appendChild(grpSpPr);

        // Slide image placeholder
        spTree.appendChild(createNotesPlaceholder(doc, "2", "Slide Image Placeholder 1", "sldImg", true, null));

        // Notes text placeholder with content
        spTree.appendChild(createNotesPlaceholder(doc, "3", "Notes Placeholder 2", "body", false, text));

        // Slide number placeholder
        spTree.appendChild(createNotesPlaceholder(doc, "4", "Slide Number Placeholder 3", "sldNum", false, null));

        // Color mapping override
        org.w3c.dom.Element clrMapOvr = doc.createElementNS(com.excudo.core.utils.XMLConstants.PRESENTATION_NS, "p:clrMapOvr");
        org.w3c.dom.Element masterClrMapping = doc.createElementNS(com.excudo.core.utils.XMLConstants.DRAWING_NS, "a:masterClrMapping");
        clrMapOvr.appendChild(masterClrMapping);
        notes.appendChild(clrMapOvr);

        // Save notes slide
        java.io.File notesFile = new java.io.File(extractedDir, "ppt/notesSlides/notesSlide" + notesNum + ".xml");
        com.excudo.core.utils.OOXMLAttributeOrder.serialize(doc, notesFile);

        // Create notes slide rels
        createNotesRels(extractedDir, slideNumber, notesNum);

        // Update slide rels to reference notes slide
        updateSlideRelsForNotes(extractedDir, slideNumber, notesNum);

        // Update [Content_Types].xml
        updateContentTypesForNotes(extractedDir, notesNum);
    }

    private org.w3c.dom.Element createNotesPlaceholder(org.w3c.dom.Document doc, String id, String name,
            String phType, boolean noRotNoAspect, String text) {
        String PNS = com.excudo.core.utils.XMLConstants.PRESENTATION_NS;
        String DNS = com.excudo.core.utils.XMLConstants.DRAWING_NS;

        org.w3c.dom.Element sp = doc.createElementNS(PNS, "p:sp");
        org.w3c.dom.Element nvSpPr = doc.createElementNS(PNS, "p:nvSpPr");

        org.w3c.dom.Element cNvPr = doc.createElementNS(PNS, "p:cNvPr");
        cNvPr.setAttribute("id", id);
        cNvPr.setAttribute("name", name);
        nvSpPr.appendChild(cNvPr);

        org.w3c.dom.Element cNvSpPr = doc.createElementNS(PNS, "p:cNvSpPr");
        org.w3c.dom.Element spLocks = doc.createElementNS(DNS, "a:spLocks");
        spLocks.setAttribute("noGrp", "1");
        if (noRotNoAspect) {
            spLocks.setAttribute("noRot", "1");
            spLocks.setAttribute("noChangeAspect", "1");
        }
        cNvSpPr.appendChild(spLocks);
        nvSpPr.appendChild(cNvSpPr);

        org.w3c.dom.Element nvPr = doc.createElementNS(PNS, "p:nvPr");
        org.w3c.dom.Element ph = doc.createElementNS(PNS, "p:ph");
        ph.setAttribute("type", phType);
        if ("body".equals(phType)) {
            ph.setAttribute("idx", "1");
        } else if ("sldNum".equals(phType)) {
            ph.setAttribute("sz", "quarter");
            ph.setAttribute("idx", "5");
        }
        nvPr.appendChild(ph);
        nvSpPr.appendChild(nvPr);
        sp.appendChild(nvSpPr);

        sp.appendChild(doc.createElementNS(PNS, "p:spPr"));

        // Add text body for notes placeholder
        if (text != null && "body".equals(phType)) {
            org.w3c.dom.Element txBody = doc.createElementNS(PNS, "p:txBody");
            org.w3c.dom.Element bodyPr = doc.createElementNS(DNS, "a:bodyPr");
            txBody.appendChild(bodyPr);
            org.w3c.dom.Element lstStyle = doc.createElementNS(DNS, "a:lstStyle");
            txBody.appendChild(lstStyle);

            // Split text into paragraphs
            String[] lines = text.split("\\n");
            for (String line : lines) {
                org.w3c.dom.Element p = doc.createElementNS(DNS, "a:p");
                org.w3c.dom.Element r = doc.createElementNS(DNS, "a:r");
                org.w3c.dom.Element rPr = doc.createElementNS(DNS, "a:rPr");
                rPr.setAttribute("lang", "en-US");
                rPr.setAttribute("dirty", "0");
                r.appendChild(rPr);
                org.w3c.dom.Element t = doc.createElementNS(DNS, "a:t");
                t.setTextContent(line);
                r.appendChild(t);
                p.appendChild(r);
                txBody.appendChild(p);
            }
            sp.appendChild(txBody);
        }

        return sp;
    }

    private void createNotesRels(java.io.File extractedDir, int slideNumber, int notesNum) throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"../slides/slide" + slideNumber + ".xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesMaster\" Target=\"../notesMasters/notesMaster1.xml\"/>" +
            "</Relationships>";
        java.io.File relsFile = new java.io.File(extractedDir, "ppt/notesSlides/_rels/notesSlide" + notesNum + ".xml.rels");
        relsFile.getParentFile().mkdirs();
        java.nio.file.Files.writeString(relsFile.toPath(), xml);
    }

    private void updateSlideRelsForNotes(java.io.File extractedDir, int slideNumber, int notesNum) throws Exception {
        java.io.File slideRelsFile = new java.io.File(extractedDir, "ppt/slides/_rels/slide" + slideNumber + ".xml.rels");
        if (!slideRelsFile.exists()) {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide\" Target=\"../notesSlides/notesSlide" + notesNum + ".xml\"/>" +
                "</Relationships>";
            java.nio.file.Files.writeString(slideRelsFile.toPath(), xml);
            return;
        }

        String content = java.nio.file.Files.readString(slideRelsFile.toPath());
        // Find next available rId
        int maxRId = 0;
        java.util.regex.Pattern rIdPat = java.util.regex.Pattern.compile("Id=\"rId(\\d+)\"");
        java.util.regex.Matcher m = rIdPat.matcher(content);
        while (m.find()) {
            maxRId = Math.max(maxRId, Integer.parseInt(m.group(1)));
        }
        String newRId = "rId" + (maxRId + 1);

        // Insert before closing tag
        String newRel = "<Relationship Id=\"" + newRId + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide\" Target=\"../notesSlides/notesSlide" + notesNum + ".xml\"/>";
        content = content.replace("</Relationships>", newRel + "</Relationships>");
        java.nio.file.Files.writeString(slideRelsFile.toPath(), content);
    }

    private void updateContentTypesForNotes(java.io.File extractedDir, int notesNum) throws Exception {
        java.io.File ctFile = new java.io.File(extractedDir, "[Content_Types].xml");
        if (!ctFile.exists()) return;

        String content = java.nio.file.Files.readString(ctFile.toPath());
        String partName = "/ppt/notesSlides/notesSlide" + notesNum + ".xml";

        if (content.contains(partName)) return; // Already registered

        String override = "<Override PartName=\"" + partName + "\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml\"/>";
        content = content.replace("</Types>", override + "</Types>");
        java.nio.file.Files.writeString(ctFile.toPath(), content);
    }

    private void updateNotesContent(java.io.File notesFile, String newText) throws Exception {
        org.w3c.dom.Document doc = com.excudo.core.utils.XMLFactoryProvider.parseDocument(notesFile);

        // Find the notes placeholder body (ph type="body")
        javax.xml.xpath.XPath xpath = com.excudo.core.utils.XMLFactoryProvider.createXPath();
        org.w3c.dom.Element bodyPh = (org.w3c.dom.Element) xpath.evaluate(
            "//*[local-name()='ph'][@type='body']/ancestor::*[local-name()='sp']",
            doc, javax.xml.xpath.XPathConstants.NODE);

        if (bodyPh == null) return;

        // Find txBody
        org.w3c.dom.Element txBody = (org.w3c.dom.Element) xpath.evaluate(
            ".//*[local-name()='txBody']", bodyPh, javax.xml.xpath.XPathConstants.NODE);

        if (txBody == null) return;

        // Remove existing paragraphs
        org.w3c.dom.NodeList existingPs = txBody.getElementsByTagNameNS(
            com.excudo.core.utils.XMLConstants.DRAWING_NS, "p");
        while (existingPs.getLength() > 0) {
            txBody.removeChild(existingPs.item(0));
        }

        // Add new paragraphs
        String DNS = com.excudo.core.utils.XMLConstants.DRAWING_NS;
        for (String line : newText.split("\\n")) {
            org.w3c.dom.Element p = doc.createElementNS(DNS, "a:p");
            org.w3c.dom.Element r = doc.createElementNS(DNS, "a:r");
            org.w3c.dom.Element rPr = doc.createElementNS(DNS, "a:rPr");
            rPr.setAttribute("lang", "en-US");
            rPr.setAttribute("dirty", "0");
            r.appendChild(rPr);
            org.w3c.dom.Element t = doc.createElementNS(DNS, "a:t");
            t.setTextContent(line);
            r.appendChild(t);
            p.appendChild(r);
            txBody.appendChild(p);
        }

        com.excudo.core.utils.OOXMLAttributeOrder.serialize(doc, notesFile);
    }

    // ========== TRANSITION OPERATIONS ==========

    @Override
    public ExecutionResult<Void> setTransition(int slideNumber, com.excudo.core.model.TransitionType transitionType,
                                                String speed, Integer advanceTimeMs) {
        if (currentContext == null) {
            return ExecutionResult.failure("SetTransition", "No orchestration context available");
        }
        try {
            PPTXDocument pptxDoc = currentContext.getDocument();
            org.w3c.dom.Document doc;
            boolean usePptxDoc = (pptxDoc != null && pptxDoc.hasSlide(slideNumber));

            if (usePptxDoc) {
                doc = pptxDoc.getSlideDocument(slideNumber);
            } else {
                File slideFile = getSlideFile(slideNumber);
                if (slideFile == null || !slideFile.exists()) {
                    return ExecutionResult.failure("SetTransition", "Slide " + slideNumber + " not found");
                }
                doc = com.excudo.core.utils.XMLFactoryProvider.parseDocument(slideFile);
            }

            com.excudo.xml.writers.TransitionInjector.setTransition(doc, transitionType, speed, advanceTimeMs, null);

            if (usePptxDoc) {
                pptxDoc.markSlideDirty(slideNumber);
            } else {
                File slideFile = getSlideFile(slideNumber);
                com.excudo.core.utils.OOXMLAttributeOrder.serialize(doc, slideFile);
            }

            if (currentContext.getContextService() != null) {
                currentContext.getContextService().invalidateSlide(slideNumber);
            }
            logger.info("Set {} transition on slide {}", transitionType.getUserFriendlyName(), slideNumber);
            return ExecutionResult.success("SetTransition", null);
        } catch (Exception e) {
            return ExecutionResult.failure("SetTransition",
                "Failed to set transition on slide " + slideNumber + ": " + e.getMessage());
        }
    }

    @Override
    public ExecutionResult<Void> removeTransition(int slideNumber) {
        if (currentContext == null) {
            return ExecutionResult.failure("RemoveTransition", "No orchestration context available");
        }
        try {
            PPTXDocument pptxDoc = currentContext.getDocument();
            org.w3c.dom.Document doc;
            boolean usePptxDoc = (pptxDoc != null && pptxDoc.hasSlide(slideNumber));

            if (usePptxDoc) {
                doc = pptxDoc.getSlideDocument(slideNumber);
            } else {
                File slideFile = getSlideFile(slideNumber);
                if (slideFile == null || !slideFile.exists()) {
                    return ExecutionResult.failure("RemoveTransition", "Slide " + slideNumber + " not found");
                }
                doc = com.excudo.core.utils.XMLFactoryProvider.parseDocument(slideFile);
            }

            com.excudo.xml.writers.TransitionInjector.removeTransition(doc);

            if (usePptxDoc) {
                pptxDoc.markSlideDirty(slideNumber);
            } else {
                File slideFile = getSlideFile(slideNumber);
                com.excudo.core.utils.OOXMLAttributeOrder.serialize(doc, slideFile);
            }

            if (currentContext.getContextService() != null) {
                currentContext.getContextService().invalidateSlide(slideNumber);
            }
            logger.info("Removed transition from slide {}", slideNumber);
            return ExecutionResult.success("RemoveTransition", null);
        } catch (Exception e) {
            return ExecutionResult.failure("RemoveTransition",
                "Failed to remove transition from slide " + slideNumber + ": " + e.getMessage());
        }
    }

    // ========== SLIDE MASTER CRUD ==========

    @Override
    public ExecutionResult<Void> editMasterStyle(String target, int level, java.util.Map<String, Object> updates) {
        if (currentContext == null) {
            return ExecutionResult.failure("EditMasterStyle", "No orchestration context available");
        }
        return slideMasterOrchestrationManager.editMasterStyle(target, level, updates);
    }

    @Override
    public java.util.Map<String, com.excudo.core.themes.TextLevelStyle[]> getMasterStyles() {
        if (slideMasterOrchestrationManager == null) {
            return java.util.Collections.emptyMap();
        }
        return slideMasterOrchestrationManager.getMasterStyles();
    }

    @Override
    public ExecutionResult<Void> setClrMap(String logicalColor, String themeColor) {
        if (currentContext == null) {
            return ExecutionResult.failure("SetClrMap", "No orchestration context available");
        }
        return slideMasterOrchestrationManager.setClrMap(logicalColor, themeColor);
    }

    @Override
    public java.util.Map<String, String> getClrMap() {
        if (slideMasterOrchestrationManager == null) {
            return java.util.Collections.emptyMap();
        }
        return slideMasterOrchestrationManager.getClrMap();
    }

    @Override
    public String getBackgroundColorHex(int slideNumber) {
        if (slideMasterOrchestrationManager == null) return null;
        return slideMasterOrchestrationManager.getBackgroundColorHex(slideNumber);
    }

    @Override
    public ExecutionResult<Void> setMasterBackground(int fillIndex, String schemeColor) {
        if (currentContext == null) {
            return ExecutionResult.failure("SetMasterBg", "No orchestration context available");
        }
        return slideMasterOrchestrationManager.setBackground(fillIndex, schemeColor);
    }

    @Override
    public ExecutionResult<Void> setObjectDefaults(String fontColor, Integer lineWidth) {
        if (currentContext == null) {
            return ExecutionResult.failure("SetObjectDefaults", "No orchestration context available");
        }
        return slideMasterOrchestrationManager.setObjectDefaults(fontColor, lineWidth, null);
    }

    @Override
    public java.util.Map<String, Object> getMasterInfo() {
        if (slideMasterOrchestrationManager == null) {
            return java.util.Collections.emptyMap();
        }
        return slideMasterOrchestrationManager.getMasterInfo();
    }

    // ========== SLIDE LAYOUT CRUD ==========

    @Override
    public ExecutionResult<String> duplicateLayout(String sourceLayoutId, String newName) {
        if (currentContext == null) {
            return ExecutionResult.failure("DuplicateLayout", "No orchestration context available");
        }
        return layoutOrchestrationManager.duplicateLayout(sourceLayoutId, newName);
    }

    @Override
    public ExecutionResult<Void> deleteLayout(String layoutId) {
        if (currentContext == null) {
            return ExecutionResult.failure("DeleteLayout", "No orchestration context available");
        }
        return layoutOrchestrationManager.deleteLayout(layoutId);
    }

    @Override
    public ExecutionResult<Void> renameLayout(String layoutId, String newName) {
        if (currentContext == null) {
            return ExecutionResult.failure("RenameLayout", "No orchestration context available");
        }
        return layoutOrchestrationManager.renameLayout(layoutId, newName);
    }

    @Override
    public ExecutionResult<Void> addPlaceholder(String layoutId, String type, int idx,
                                                 long x, long y, long cx, long cy) {
        if (currentContext == null) {
            return ExecutionResult.failure("AddPlaceholder", "No orchestration context available");
        }
        com.excudo.core.themes.PlaceholderDefinition ph =
            new com.excudo.core.themes.PlaceholderDefinition(type, idx, x, y, cx, cy);
        return layoutOrchestrationManager.addPlaceholder(layoutId, ph);
    }

    @Override
    public ExecutionResult<Void> removePlaceholder(String layoutId, int idx) {
        if (currentContext == null) {
            return ExecutionResult.failure("RemovePlaceholder", "No orchestration context available");
        }
        return layoutOrchestrationManager.removePlaceholder(layoutId, idx);
    }

    // ========== UTILITY OPERATIONS ==========

    @Override
    public List<com.excudo.core.model.AnimationType> getAvailableAnimationTypes() {
        return utilityOrchestrationManager.getAvailableAnimationTypes();
    }

    @Override
    public ExecutionResult<String> dumpTimingXML(int slideNumber) {
        if (currentContext == null) {
            return ExecutionResult.failure("DumpTimingXML", "No presentation loaded");
        }
        return utilityOrchestrationManager.dumpTimingXML(slideNumber);
    }

    @Override
    public ExecutionResult<String> dumpTimingsBulk(String slideRange, boolean writeToFile) {
        if (currentContext == null) {
            return ExecutionResult.failure("DumpTimingsBulk", "No presentation loaded");
        }
        return utilityOrchestrationManager.dumpTimingsBulk(slideRange, writeToFile);
    }

    @Override
    public ExecutionResult<String> dumpShapeXML(int slideNumber, int spid) {
        if (currentContext == null) {
            return ExecutionResult.failure("DumpShapeXML", "No presentation loaded");
        }
        return utilityOrchestrationManager.dumpShapeXML(slideNumber, spid);
    }

    @Override
    public ExecutionResult<String> dumpShapesBulk(String slideRange, boolean writeToFile) {
        if (currentContext == null) {
            return ExecutionResult.failure("DumpShapesBulk", "No presentation loaded");
        }
        return utilityOrchestrationManager.dumpShapesBulk(slideRange, writeToFile);
    }


    private void deleteDirectoryQuietly(java.io.File dir) {
        if (dir == null || !dir.exists()) return;
        try {
            java.nio.file.Files.walk(dir.toPath())
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try { java.nio.file.Files.delete(path); } catch (Exception ignored) {}
                    });
        } catch (Exception e) {
            logger.warn("Failed to clean up temp directory {}: {}", dir.getName(), e.getMessage());
        }
    }
}
