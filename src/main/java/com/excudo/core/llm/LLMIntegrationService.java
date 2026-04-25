package com.excudo.core.llm;

import com.excudo.core.commands.RequestSchema;
import com.excudo.core.model.*;
import com.excudo.core.orchestration.*;
import com.excudo.core.services.ContextService;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CompositeCommand;
import com.excudo.core.geometry.IntelligentLayoutEngine;
import com.excudo.core.model.SlideShape.ShapeType;
import com.excudo.xml.parsers.SlideXMLParser;
import com.excudo.xml.writers.SPIDManager;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.smartcontent.SmartContentEnhancer;
import org.w3c.dom.*;
import java.io.File;
import java.util.*;

/**
 * Service for integrating LLM capabilities with slide editing
 * Provides methods to generate context, process commands, and apply edits
 */
public class LLMIntegrationService {
    
    private static final ComponentLogger logger = Logger.llm();
    private final IntelligentLayoutEngine layoutEngine;
    private SmartContentEnhancer contentEnhancer;
    
    public LLMIntegrationService() {
        this.layoutEngine = new IntelligentLayoutEngine();
    }
    
    /**
     * Initialize content enhancer with presentation path
     */
    public void initializeContentEnhancer(String presentationPath, String iconCacheDir) {
        this.contentEnhancer = new SmartContentEnhancer(iconCacheDir, presentationPath);
    }
    
    /**
     * Generate enhanced context using ContextService for better Model integration
     */
    public String generateEnhancedLLMContext(PPTXOrchestrator orchestrator, boolean includeDetailedContent) {
        StringBuilder context = new StringBuilder();
        
        try {
            // Get the extracted PPTX directory from orchestrator
            java.util.Optional<OrchestrationContext> orchContext = orchestrator.getContext();
            if (orchContext.isPresent()) {
                // Use shared ContextService from orchestrator
                ContextService contextService = orchestrator.getContextService();
                
                // Add presentation-level context
                context.append("ENHANCED PRESENTATION CONTEXT:\n");
                ContextService.PresentationContext presContext = contextService.getPresentationContext();
                context.append("Total slides: ").append(presContext.getSlideNumbers().size()).append("\n");
                context.append("Available themes: ").append(presContext.getAvailableThemes()).append("\n");
                context.append("Available layouts: ").append(presContext.getAvailableLayouts()).append("\n");
                // Don't show global next SPID as it's misleading
                // context.append("Next available SPID: ").append(presContext.getNextAvailableSpid()).append("\n\n");
                
                // CRITICAL: Add dynamic layout context for LLM layout selection
                context.append("\n").append(contextService.getLayoutContext()).append("\n");
                
                // Add detailed slide contexts if requested
                if (includeDetailedContent) {
                    context.append("DETAILED SLIDE CONTEXTS:\n");
                    for (Integer slideNum : presContext.getSlideNumbers()) {
                        ContextService.SlideContext slideContext = contextService.getSlideContext(slideNum);
                        context.append("\n--- Slide ").append(slideNum).append(" ---\n");
                        context.append("Existing SPIDs: ").append(slideContext.getExistingSpids()).append("\n");
                        // Show predicted SPIDs for new shapes on this slide
                        List<Integer> predictedSpids = contextService.predictSpidsForSlide(slideNum, 5);
                        context.append("Next SPIDs for new shapes on this slide: ").append(predictedSpids).append("\n");
                        
                        // Add shape details with hierarchical organization
                        ParsedSlideData slideData = slideContext.getSlideData();
                        context.append("Shapes organized by type:\n");
                        
                        Map<SlideShape.ShapeType, List<SlideShape>> groupedShapes = slideContext.getShapesGroupedByType();
                        for (Map.Entry<SlideShape.ShapeType, List<SlideShape>> entry : groupedShapes.entrySet()) {
                            context.append("  ").append(entry.getKey()).append(" (").append(entry.getValue().size()).append("):\n");
                            for (SlideShape shape : entry.getValue()) {
                                context.append("    - SPID ").append(shape.getSpid()).append(": ");
                                if (shape.getName() != null) {
                                    context.append(shape.getName()).append(" - ");
                                }
                                if (shape.hasText()) {
                                    context.append("\"").append(truncateText(shape.getTextContent(), 40)).append("\"");
                                }
                                context.append("\n");
                            }
                        }
                        
                        // Add shape hierarchy information
                        ContextService.ShapeHierarchy hierarchy = slideContext.getShapeHierarchy();
                        context.append("\nShape organization:\n");
                        context.append("  ").append(hierarchy.getSummary()).append("\n");
                        
                        if (!hierarchy.getLogicalGroups().isEmpty()) {
                            context.append("  Logical groups:\n");
                            for (ContextService.ShapeGroup group : hierarchy.getLogicalGroups()) {
                                context.append("    - ").append(group.getGroupId()).append(": ")
                                       .append(group.getDescription()).append(" (")
                                       .append(group.getShapes().size()).append(" shapes)\n");
                            }
                        }
                        
                        // Add animation context
                        ContextService.AnimationContext animContext = contextService.getAnimationContext(slideNum);
                        if (!animContext.getAnimationBindings().isEmpty()) {
                            context.append("Animations: ").append(animContext.getAnimationBindings().size()).append("\n");
                            context.append("Click sequences: ").append(animContext.getClickSequences().size()).append("\n");
                        }
                    }
                }
                
                // Add layout-aware SPID prediction for key slideTypes (CONTENT + TITLE only)
                context.append("\nUSER-ADDRESSABLE SPID PREDICTIONS:\n");

                // Calculate target slide number for new operations
                int maxSlideNumber = presContext.getSlideNumbers().isEmpty() ? 0 :
                    Collections.max(presContext.getSlideNumbers());
                int targetSlideNumber = maxSlideNumber + 1;

                String[] commonSlideTypes = {"CONTENT", "TITLE"};
                context.append("When creating slide ").append(targetSlideNumber).append(":\n\n");

                for (String slideType : commonSlideTypes) {
                    try {
                        List<Integer> userSpids = contextService.predictUserSpidsForSlideType(slideType, targetSlideNumber, 4);
                        LayoutInfo layout = contextService.getLayoutManager().findLayoutForSlideType(slideType);

                        context.append("slideType=\"").append(slideType).append("\" → ");
                        if (layout != null) {
                            context.append(layout.getLayoutId()).append(" (").append(layout.getName()).append(")\n");
                        } else {
                            context.append("default layout\n");
                        }

                        context.append("  Available SPIDs: ").append(userSpids).append("\n");

                        if (layout != null && layout.hasTitlePlaceholder() && !userSpids.isEmpty()) {
                            context.append("  Title SPID: ").append(userSpids.get(0));
                            if (userSpids.size() > 1) {
                                context.append(", Content SPIDs: ").append(userSpids.subList(1, userSpids.size()));
                            }
                        } else {
                            context.append("  Content SPIDs start at: ").append(userSpids.isEmpty() ? "3" : userSpids.get(0));
                        }
                        context.append("\n\n");

                    } catch (Exception e) {
                        logger.error("Failed to predict SPIDs for slideType " + slideType + ": " + e.getMessage());
                    }
                }
                
                return context.toString();
            }
        } catch (Exception e) {
            logger.error("Failed to generate enhanced context: " + e.getMessage());
            return "ERROR: Could not generate presentation context: " + e.getMessage() + "\n";
        }

        return "No presentation loaded.\n";
    }
    
    /**
     * Generate compact context for local/small models (e.g. Ollama 14B).
     * Produces ~250-400 tokens: one header, one line per slide, one SPID footer.
     */
    public String generateCompactLLMContext(PPTXOrchestrator orchestrator) {
        StringBuilder ctx = new StringBuilder();

        try {
            java.util.Optional<OrchestrationContext> orchContext = orchestrator.getContext();
            if (orchContext.isEmpty()) {
                return "No presentation loaded.\n";
            }

            ContextService contextService = orchestrator.getContextService();
            if (contextService == null) {
                return "No presentation context service available.\n";
            }
            ContextService.PresentationContext presContext = contextService.getPresentationContext();

            // Header: slide count + layout IDs with names
            List<Integer> slideNums = presContext.getSlideNumbers();
            ctx.append("Slides: ").append(slideNums.size());

            List<LayoutInfo> layouts = contextService.getAvailableLayoutsDetailed();
            if (!layouts.isEmpty()) {
                ctx.append(" | Layouts: ");
                for (int i = 0; i < layouts.size(); i++) {
                    if (i > 0) ctx.append(",");
                    LayoutInfo l = layouts.get(i);
                    ctx.append(l.getLayoutId()).append("(").append(l.getName()).append(")");
                }
            }
            ctx.append("\n");

            // One line per slide: SPIDs with truncated text + next available SPID
            for (int slideNum : slideNums) {
                try {
                    ContextService.SlideContext slideCtx = contextService.getSlideContext(slideNum);
                    ParsedSlideData slideData = slideCtx.getSlideData();
                    ShapeRegistry registry = slideData.getShapeRegistry();

                    ctx.append("S").append(slideNum).append(": ");
                    List<SlideShape> shapes = registry.getAllShapes();
                    if (shapes.isEmpty()) {
                        ctx.append("(empty)");
                    } else {
                        boolean first = true;
                        for (SlideShape shape : shapes) {
                            if (!first) ctx.append(" ");
                            first = false;
                            ctx.append("SPID").append(shape.getSpid());
                            // Add role label so the model knows what each shape is
                            String role = inferCompactRole(shape);
                            if (role != null) {
                                ctx.append("(").append(role).append(")");
                            }
                            if (shape.hasText() && shape.getTextContent() != null) {
                                ctx.append("=\"").append(truncateText(shape.getTextContent(), 20)).append("\"");
                            }
                        }
                    }

                    // Next available SPID for this slide
                    List<Integer> predicted = contextService.predictSpidsForSlide(slideNum, 1);
                    if (!predicted.isEmpty()) {
                        ctx.append(" | next:").append(predicted.get(predicted.size() - 1));
                    }
                    ctx.append("\n");
                } catch (Exception e) {
                    ctx.append("S").append(slideNum).append(": error\n");
                }
            }

            // Footer: SPID prediction for next new slide only (CONTENT type)
            int nextSlide = slideNums.isEmpty() ? 1 : Collections.max(slideNums) + 1;
            try {
                List<Integer> newSpids = contextService.predictUserSpidsForSlideType("CONTENT", nextSlide, 3);
                ctx.append("New slide SPIDs: ");
                boolean first = true;
                LayoutInfo contentLayout = contextService.getLayoutManager().findLayoutForSlideType("CONTENT");
                if (contentLayout != null && contentLayout.hasTitlePlaceholder() && !newSpids.isEmpty()) {
                    ctx.append(newSpids.get(0)).append("(title)");
                    for (int i = 1; i < newSpids.size(); i++) {
                        ctx.append(",").append(newSpids.get(i)).append("+(content)");
                    }
                } else {
                    for (int spid : newSpids) {
                        if (!first) ctx.append(",");
                        first = false;
                        ctx.append(spid);
                    }
                }
                ctx.append("\n");
            } catch (Exception e) {
                logger.debug("Failed to predict SPIDs for compact context: " + e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Failed to generate compact context: " + e.getMessage());
            return "No presentation context available.\n";
        }

        return ctx.toString();
    }

    /**
     * Generate context for specific slides only
     */
    public String generateSlideContext(PPTXOrchestrator orchestrator, List<Integer> slideNumbers) {
        StringBuilder context = new StringBuilder();
        
        for (Integer slideNum : slideNumbers) {
            try {
                File slideFile = orchestrator.getSlideFile(slideNum);
                if (slideFile != null && slideFile.exists()) {
                    ParsedSlideData slideData;
                    ContextService cs = orchestrator.getContextService();
                    if (cs != null) {
                        slideData = cs.getSlideContext(slideNum).getSlideData();
                    } else {
                        SlideXMLParser parser = new SlideXMLParser();
                        slideData = parser.parseSlide(slideFile);
                    }

                    SlideContentContext contentContext = new SlideContentContext(slideNum, slideData);
                    
                    context.append(String.format("\n=== SLIDE %d ===\n", slideNum));
                    
                    // Find slide metadata
                    List<SlideMetadata> allSlides = orchestrator.getAllSlideMetadata();
                    for (SlideMetadata meta : allSlides) {
                        if (meta.getSlideNumber() == slideNum) {
                            context.append(String.format("Title: %s\n", meta.getTitle()));
                            break;
                        }
                    }
                    
                    // Add SPID context
                    context.append("\n" + generateSlideSPIDContext(slideNum, orchestrator));
                    
                    context.append(contentContext.toNaturalLanguage());
                    context.append("\nEditable content:\n");
                    context.append(contentContext.toJson());
                }
            } catch (Exception e) {
                context.append(String.format("Error reading slide %d: %s\n", slideNum, e.getMessage()));
            }
        }
        
        return context.toString();
    }
    
    /**
     * Generate SPID context for a specific slide
     * Provides information about existing SPIDs and their element types
     */
    public String generateSlideSPIDContext(int slideNumber, PPTXOrchestrator orchestrator) {
        StringBuilder context = new StringBuilder();
        
        try {
            File slideFile = orchestrator.getSlideFile(slideNumber);
            if (slideFile != null && slideFile.exists()) {
                ParsedSlideData slideData;
                ContextService cs = orchestrator.getContextService();
                if (cs != null) {
                    slideData = cs.getSlideContext(slideNumber).getSlideData();
                } else {
                    SlideXMLParser parser = new SlideXMLParser();
                    slideData = parser.parseSlide(slideFile);
                }

                context.append(String.format("=== SLIDE %d SPID CONTEXT ===\n", slideNumber));
                context.append("EXISTING SHAPES (already on slide - DO NOT animate unless specifically requested):\n");
                
                ShapeRegistry shapeRegistry = slideData.getShapeRegistry();
                List<Integer> existingSpids = new ArrayList<>();
                for (SlideShape shape : shapeRegistry.getAllShapes()) {
                    existingSpids.add(shape.getSpid());
                    String shapeType = inferShapeType(shape);
                    context.append(String.format("- SPID %d: %s", shape.getSpid(), shapeType));
                    if (shape.getName() != null && !shape.getName().isEmpty()) {
                        context.append(String.format(" (name: %s)", shape.getName()));
                    }
                    if (shape.hasText() && shape.getTextContent() != null && !shape.getTextContent().isEmpty()) {
                        context.append(String.format(" - \"%s\"", 
                            shape.getTextContent().length() > 50 ? 
                            shape.getTextContent().substring(0, 47) + "..." : shape.getTextContent()));
                    }
                    context.append("\n");
                }
                
                // Calculate next available SPID using Microsoft-compatible prediction
                java.util.Optional<OrchestrationContext> contextOpt = orchestrator.getContext();
                if (contextOpt.isPresent() && contextOpt.get().getSpidManager() != null) {
                    SPIDManager spidManager = contextOpt.get().getSpidManager();
                    
                    // Use Microsoft-compatible prediction for this specific slide
                    int firstSpid = spidManager.predictSpidForShape("custom", slideNumber, false, false, null);
                    context.append(String.format("\nNext available SPID (from SPIDManager): %d\n", firstSpid));
                    
                    // SPID allocation info for new shapes
                    context.append("\nSPID ALLOCATION FOR NEW SHAPES:\n");
                    context.append("Next available SPID for new shapes: " + firstSpid + "\n");
                    context.append("When you create shapes, they will be assigned SPIDs sequentially:\n");
                    context.append("- 1st new shape → SPID " + firstSpid + "\n");
                    context.append("- 2nd new shape → SPID " + (firstSpid + 1) + "\n");
                    context.append("- 3rd new shape → SPID " + (firstSpid + 2) + "\n");
                    context.append("- 4th new shape → SPID " + (firstSpid + 3) + "\n");
                } else {
                    // Fallback to local calculation
                    Set<Integer> allSpids = shapeRegistry.getAllSpids();
                    int nextSpid = allSpids.stream()
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(0) + 1;
                    context.append(String.format("\nNext available SPID: %d\n", nextSpid));
                    
                    // SPID allocation info for new shapes (fallback)
                    context.append("\nSPID ALLOCATION FOR NEW SHAPES:\n");
                    context.append("Next available SPID for new shapes: " + nextSpid + "\n");
                    context.append("When you create shapes, they will be assigned SPIDs sequentially:\n");
                    context.append("- 1st new shape → SPID " + nextSpid + "\n");
                    context.append("- 2nd new shape → SPID " + (nextSpid + 1) + "\n");
                    context.append("- 3rd new shape → SPID " + (nextSpid + 2) + "\n");
                    context.append("- 4th new shape → SPID " + (nextSpid + 3) + "\n");
                }
                context.append("\nREMEMBER:\n");
                context.append("- Use these exact SPIDs when animating the shapes you create\n");
                context.append("- Do NOT animate existing shapes (listed above) unless explicitly requested\n");
            }
        } catch (Exception e) {
            context.append(String.format("Error reading slide %d: %s\n", slideNumber, e.getMessage()));
        }
        
        return context.toString();
    }
    
    /**
     * Infer shape type from shape data
     */
    /**
     * Short role label for compact context (e.g. "title", "subtitle", "body").
     * Returns null for shapes with no recognizable role.
     */
    private String inferCompactRole(SlideShape shape) {
        String name = shape.getName();
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.contains("subtitle")) return "subtitle";
            if (lower.contains("title")) return "title";
            if (lower.contains("content") || lower.contains("body") || lower.contains("text")) return "body";
        }
        return null;
    }

    private String inferShapeType(SlideShape shape) {
        // Check for title placeholder
        if (shape.getName() != null && shape.getName().toLowerCase().contains("title")) {
            return "Title Placeholder";
        }
        
        // Check for content placeholder
        if (shape.getTextContent() != null) {
            String text = shape.getTextContent().toLowerCase();
            if (text.contains("click to add title")) {
                return "Title Placeholder";
            }
            if (text.contains("click to add text") ||
                text.contains("click to add content")) {
                return "Content Placeholder";
            }
        }
        
        // Check geometry for common patterns
        if (shape.getGeometry() != null) {
            // Title shapes are typically at the top
            if (shape.getGeometry().getY() < 1500000) { // EMUs
                return "Title/Header Shape";
            }
        }
        
        // Default to generic shape
        return "Shape";
    }
    
    
    /**
     * Process a user request using the agentic multi-turn approach.
     *
     * The LLM selectively retrieves context via tool calls instead of receiving a monolithic dump,
     * then submits commands when ready. Only routes to agentic flow when the llmClient supports
     * the tool_use protocol (i.e. AnthropicAPIClient).
     *
     * @param userRequest    the natural-language request from the user
     * @param orchestrator   the active presentation orchestrator
     * @param llmClient      the LLM client (must be AnthropicAPIClient for full agentic behavior)
     * @param commandFactory the command factory for the current session
     * @param commandInvoker the command invoker for the current session
     * @return human-readable summary of what was done
     */
    public AgenticLLMService.AgenticResult processRequestAgenticWithUsage(String userRequest,
                                         PPTXOrchestrator orchestrator, LLMClient llmClient,
                                         CommandFactory commandFactory, CommandInvoker commandInvoker) {
        return processRequestAgenticWithUsage(userRequest, orchestrator, llmClient,
            commandFactory, commandInvoker, null, null);
    }

    public AgenticLLMService.AgenticResult processRequestAgenticWithUsage(String userRequest,
                                         PPTXOrchestrator orchestrator, LLMClient llmClient,
                                         CommandFactory commandFactory, CommandInvoker commandInvoker,
                                         AgenticLLMService.ProgressListener progressListener) {
        return processRequestAgenticWithUsage(userRequest, orchestrator, llmClient,
            commandFactory, commandInvoker, null, progressListener);
    }

    public AgenticLLMService.AgenticResult processRequestAgenticWithUsage(String userRequest,
                                         PPTXOrchestrator orchestrator, LLMClient llmClient,
                                         CommandFactory commandFactory, CommandInvoker commandInvoker,
                                         CommandDisplay displayAdapter,
                                         AgenticLLMService.ProgressListener progressListener) {
        AgenticLLMService agenticService = new AgenticLLMService(
            llmClient, orchestrator, commandFactory, commandInvoker);
        agenticService.setDisplayAdapter(displayAdapter);
        if (progressListener != null) {
            agenticService.setProgressListener(progressListener);
        }
        return agenticService.processRequestWithUsage(userRequest);
    }

    public String processRequestAgentic(String userRequest, PPTXOrchestrator orchestrator,
                                         LLMClient llmClient, CommandFactory commandFactory,
                                         CommandInvoker commandInvoker) {
        return processRequestAgenticWithUsage(userRequest, orchestrator, llmClient,
            commandFactory, commandInvoker).summary();
    }

    /**
     * Process LLM response and execute requests (simplified single-stage processing)
     */
    public BatchExecutionResult processLLMRequests(String llmResponse, PPTXOrchestrator orchestrator, CommandInvoker commandInvoker) {
        try {
            logger.debug("Processing LLM response");
            logger.debug("RAW JSON FROM LLM: {}", llmResponse);
            
            // Create CommandFactory for unified processing
            CommandFactory commandFactory = new CommandFactory(orchestrator);
            // Use session-scoped CommandInvoker passed as parameter
            
            // Get context for command creation
            var context = orchestrator.getContext().orElse(null);
            
            if (context == null) {
                logger.error("CRITICAL: No presentation context available for Command pattern");
                logger.error("This will cause all edit-content, add-animation, and enhance commands to fail");
                return new BatchExecutionResult(0, 0, 1, 
                    Collections.emptyList(),
                    Collections.singletonMap("error", "No presentation context available"));
            }
            
            // Use CommandFactory to parse JSON and create Commands
            List<Command> commands;
            try {
                // Parse JSON to LLMRequest first (LLM layer responsibility)
                RequestParser requestParser = new RequestParser();
                ExecutionResult<RequestSchema.LLMRequest> parseResult = requestParser.parseRequest(llmResponse);
                if (!parseResult.isSuccess()) {
                    String msg = "Failed to parse LLM JSON: " + parseResult.getMessage();
                    logger.error(msg);
                    // Show a truncated snippet of what the LLM actually returned
                    String snippet = llmResponse != null
                        ? llmResponse.substring(0, Math.min(llmResponse.length(), 300))
                        : "(null)";
                    logger.error("LLM response (first 300 chars): {}", snippet);
                    return new BatchExecutionResult(0, 0, 1,
                        Collections.emptyList(),
                        Collections.singletonMap("error", msg));
                }

                RequestSchema.LLMRequest request = parseResult.getData().orElse(null);
                if (request == null) {
                    String msg = "No LLM request data found in JSON";
                    logger.error(msg);
                    return new BatchExecutionResult(0, 0, 1,
                        Collections.emptyList(),
                        Collections.singletonMap("error", msg));
                }
                
                // Create CompositeCommand from parsed LLMRequest (CommandFactory responsibility)
                CompositeCommand compositeCommand = commandFactory.createCompositeFromLLMRequest(
                    request, context.getSlideCreator(), null);
                
                // Execute CompositeCommand using CommandInvoker (transaction-like behavior)
                logger.debug("DEBUG: Executing CompositeCommand: " + compositeCommand.getDescription());
                commandInvoker.executeCommand(compositeCommand);
                
                return new BatchExecutionResult(
                    compositeCommand.getCommandCount(), 
                    compositeCommand.getCommandCount(), 
                    0, 
                    Collections.singletonList("composite-command-" + System.currentTimeMillis()),
                    Collections.singletonMap("results", "All operations completed successfully - undoable as single unit")
                );
                
            } catch (IllegalArgumentException e) {
                logger.debug("Failed to create commands from LLM request: " + e.getMessage());
                return new BatchExecutionResult(0, 0, 1, 
                    Collections.emptyList(),
                    Collections.singletonMap("error", "Failed to create commands: " + e.getMessage()));
            } catch (Exception e) {
                logger.error("CompositeCommand execution failed: " + e.getMessage());
                return new BatchExecutionResult(0, 0, 1, 
                    Collections.emptyList(),
                    Collections.singletonMap("error", "Transaction failed: " + e.getMessage()));
            }
            
        } catch (Exception e) {
            logger.error("Exception in processLLMRequests: " + e.getMessage());
            logger.error("Failed to process LLM requests: " + e.getMessage(), e);
            return new BatchExecutionResult(0, 0, 1, 
                Collections.emptyList(),
                Collections.singletonMap("error", e.getMessage())
            );
        }
    }
    
    
    // REMOVED: processOperationList() - This method has been deprecated.
    // All operation processing is now handled through CommandFactory and CommandInvoker in processLLMRequests()
    
    // REMOVED: combineResults() - This method was only used by the deprecated processOperationList()
    
    // REMOVED: processContentEdit() - This business logic is now handled by ContentEditCommand via CommandFactory
    
    // REMOVED: processAnimationEdit() - This business logic is now handled by AnimationEditCommand via CommandFactory
    
    
    // REMOVED: Commented TODO section for processEnhancedContent - This business logic is now handled by EnhancedContentCommand
    
    // REMOVED: prescanForShapeCreation() - This deprecated method is no longer needed
    
    // REMOVED: calculateNextSpidFromDocument() - This helper method was not used by any remaining methods
    
    /**
     * Parse slide XML helper
     */
    private Document parseSlideXML(File xmlFile) throws Exception {
        return XMLFactoryProvider.parseDocument(xmlFile);
    }
    
    // REMOVED: sortOperationsForProcessing() - Helper method was only used by deprecated processOperationList()
    
    // REMOVED: getOperationTypeOrder() - Helper method was only used by deprecated sortOperationsForProcessing()
    
    // REMOVED: getSlideNumberFromOperation() - Helper method was only used by deprecated sortOperationsForProcessing()
    
    // REMOVED: buildSpidMapForSlide() - Helper method was only used by deprecated processOperationList()
    
    /**
     * Truncate text for display
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    // REMOVED: processSlideCopy() - This business logic is now handled by CopySlideCommand via CommandFactory
    
    // REMOVED: processSlideDeletion() - This business logic is now handled by DeleteSlideCommand via CommandFactory
    
    // REMOVED: processEnhancedContent() - This business logic is now handled by EnhancedContentCommand via CommandFactory
    
    // REMOVED: getOrCreateSmartContentEnhancer() - Helper method was only used by the deprecated processEnhancedContent()
    
    
    // ========== DEPRECATED TWO-STAGE PROCESSING METHODS (REMOVED) ==========
    // The following methods have been removed to eliminate redundant LLM API calls:
    // - analyzeForSlideCreation() 
    // - processSlideCreationOnly()
    // - manipulateRequestString()
    // - generateUpdatedContextWithSPIDPrediction()
    // - generateSPIDPredictionWithOriginalContext()
    // - SlideCreationAnalysis class
    //
    // SPID prediction is now handled efficiently by the enhanced context generation
    // using slideLayout inspection, eliminating the need for two-stage processing.
}
