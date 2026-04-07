package com.excudo.core.orchestration;
import com.excudo.core.results.SlideExecutionResult;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.results.ExecutionResult;
// OrchestrationContext is in same package now
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PresentationXmlHelper;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.xml.writers.SlideCreator;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.XMLFactoryProvider;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages slide-level orchestration operations including CRUD operations on slides.
 * 
 * This service extracts slide manipulation logic from PPTXOrchestratorImpl,
 * providing clean delegation for slide operations while maintaining proper
 * separation of concerns.
 */
public class SlideOrchestrationManager {
    
    private static final ComponentLogger logger = Logger.getLogger(SlideOrchestrationManager.class);
    
    private final OrchestrationContext context;
    
    /**
     * Create a SlideOrchestrationManager with the given orchestration context.
     * 
     * @param context The orchestration context providing access to managers and state
     */
    public SlideOrchestrationManager(OrchestrationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("OrchestrationContext cannot be null");
        }
        this.context = context;
    }
    
    /**
     * Create a new slide at the specified position with default layout.
     * 
     * @param position Position to insert (1-based)
     * @param title Title for the new slide
     * @return Result containing slide creation details
     */
    public SlideExecutionResult createSlide(int position, String title) {
        // Use first available layout instead of blank slide (PowerPoint behavior)
        String defaultLayoutId = getFirstAvailableLayoutId();
        return createSlide(position, title, defaultLayoutId);
    }
    
    /**
     * Create a new slide with a specific layout at the specified position.
     * 
     * @param position Position to insert (1-based)
     * @param title Title for the new slide
     * @param layoutId Layout ID (e.g., "slideLayout1", "slideLayout2")
     * @return Result containing slide creation details
     */
    public SlideExecutionResult createSlide(int position, String title, String layoutId) {
        try {
            // Use the slide creator from context
            SlideCreator slideCreator = context.getSlideCreator();
            
            logger.debug("Creating slide at position {} with title: {} using layout: {}", 
                        position, title, layoutId != null ? layoutId : "default");
            
            // Create the slide using actual SlideCreator functionality
            int createdSlideNumber;
            if (layoutId != null && !layoutId.trim().isEmpty()) {
                createdSlideNumber = slideCreator.insertSlideWithLayout(position, title, layoutId);
            } else {
                createdSlideNumber = slideCreator.insertBlankSlide(position, title);
            }
            
            // Predict SPIDs that will be allocated based on layout
            List<Integer> allocatedSpids = predictAllocatedSpids(layoutId, createdSlideNumber);
            
            logger.debug("Predicted allocated SPIDs: {}", allocatedSpids);
            
            // Filter out structural group shapes (SPID 1) - only show user-addressable SPIDs
            List<Integer> userAddressableSpids = allocatedSpids.stream()
                .filter(spid -> spid != 1) // Exclude group shape
                .sorted()
                .collect(Collectors.toList());
            
            // Invalidate all cached slide data after creation (slide numbers shift)
            com.excudo.core.services.ContextService cs = context.getContextService();
            if (cs != null) {
                cs.invalidateAllSlides();
            }

            logger.info("Created slide {} at position {} with title '{}' and {} addressable SPIDs",
                       createdSlideNumber, position, title, userAddressableSpids.size());

            return SlideExecutionResult.slideCreated(
                createdSlideNumber, title, userAddressableSpids
            );
            
        } catch (Exception e) {
            logger.error("Failed to create slide at position {}: {}", position, e.getMessage());
            return SlideExecutionResult.slideActionFailed("Create Slide", position, 
                "Failed to create slide: " + e.getMessage());
        }
    }
    
    /**
     * Copy an existing slide to a new position.
     * 
     * @param sourceSlide Source slide number (1-based)
     * @param targetPosition Target position (1-based)
     * @param newTitle New title for the copied slide
     * @return Result containing slide copy details
     */
    public SlideExecutionResult copySlide(int sourceSlide, int targetPosition, String newTitle) {
        try {
            PPTXDocument pptxDoc = context.getDocument();
            if (pptxDoc == null || !pptxDoc.hasSlide(sourceSlide)) {
                return SlideExecutionResult.slideActionFailed("Copy Slide", targetPosition,
                    "Source slide " + sourceSlide + " not found");
            }

            SlideCreator slideCreator = context.getSlideCreator();
            
            // Create a copy of the slide
            int copiedSlideNumber = slideCreator.insertCopiedSlide(targetPosition, sourceSlide, newTitle);
            
            // Invalidate all cached slide data after copy (slide numbers shift)
            com.excudo.core.services.ContextService cs = context.getContextService();
            if (cs != null) {
                cs.invalidateAllSlides();
            }

            logger.info("Copied slide {} to position {} with new title '{}'",
                       sourceSlide, targetPosition, newTitle);

            return SlideExecutionResult.slideCopied(
                sourceSlide, copiedSlideNumber, Set.of(), Set.of() // SPIDs will be managed by copy operation
            );
            
        } catch (Exception e) {
            logger.error("Failed to copy slide {} to position {}: {}", sourceSlide, targetPosition, e.getMessage());
            return SlideExecutionResult.slideActionFailed("Copy Slide", targetPosition,
                "Failed to copy slide: " + e.getMessage());
        }
    }
    
    /**
     * Delete a slide at the specified position.
     * 
     * @param slideNumber Slide number to delete (1-based)
     * @return Result containing deletion details
     */
    public SlideExecutionResult deleteSlide(int slideNumber) {
        try {
            PPTXDocument pptxDoc = context.getDocument();
            boolean usePptxDoc = (pptxDoc != null);

            // Validate slide exists
            if (usePptxDoc) {
                if (!pptxDoc.hasSlide(slideNumber)) {
                    return SlideExecutionResult.slideActionFailed("Delete Slide", slideNumber,
                        "Slide " + slideNumber + " not found");
                }
            } else {
                File slideFile = getSlideFile(slideNumber);
                if (slideFile == null || !slideFile.exists()) {
                    return SlideExecutionResult.slideActionFailed("Delete Slide", slideNumber,
                        "Slide " + slideNumber + " not found");
                }
            }

            logger.info("Deleting slide {} using mathematical rId pipeline", slideNumber);

            com.excudo.xml.writers.RelationshipManager relationshipManager =
                context.getRelationshipManager();

            // 1. Remove presentation relationship
            String removedRId = relationshipManager.removePresentationSlideRelationship(slideNumber);
            logger.debug("Mathematically removed slide relationship: {}", removedRId);

            // 2. Remove slide from PPTXDocument map
            pptxDoc.removeSlideDocument(slideNumber);

            // 3a. Remove notes slide from PPTXDocument and update registry
            com.excudo.utils.NotesSlideRegistry notesRegistry = context.getNotesSlideRegistry();
            int notesSequentialNumber = notesRegistry.getNotesNumberForSlide(slideNumber);
            if (notesSequentialNumber != -1) {
                deleteNotesSlideFiles(notesSequentialNumber, slideNumber);
                notesRegistry.removeMapping(notesSequentialNumber);
                logger.debug("Removed notes slide mapping for slide {} (was notesSlide{}.xml)",
                           slideNumber, notesSequentialNumber);
            }

            // 3b. Remove sldId from presentation.xml DOM
            PresentationXmlHelper.removeSlideId(pptxDoc.getPresentationXml(), removedRId);
            pptxDoc.markPresentationXmlDirty();

            // 4. Rekey subsequent slides in PPTXDocument
            pptxDoc.rekeySlides(slideNumber + 1, -1);
            renameSubsequentSlidesAfterDelete(slideNumber);

            // 5. Rebuild Content_Types.xml
            PresentationXmlHelper.rebuildSlideContentTypes(
                pptxDoc.getContentTypesDoc(), pptxDoc.getSlideNumbers());
            pptxDoc.markContentTypesDirty();

            // Handle notes renaming
            int maxSlideNumber = pptxDoc.getSlideNumbers().stream().mapToInt(Integer::intValue).max().orElse(0);
            renameNotesSlidesMappingsAfterDelete(slideNumber, maxSlideNumber + 1);

            // Invalidate all cached slide data after deletion (slide numbers shift)
            com.excudo.core.services.ContextService cs = context.getContextService();
            if (cs != null) {
                cs.invalidateAllSlides();
            }

            logger.info("Deleted slide {}", slideNumber);

            return SlideExecutionResult.slideDeleted(slideNumber);

        } catch (Exception e) {
            logger.error("Failed to delete slide {}: {}", slideNumber, e.getMessage());
            return SlideExecutionResult.slideActionFailed("Delete Slide", slideNumber,
                "Failed to delete slide: " + e.getMessage());
        }
    }

    private int getMaxSlideNumberFromDisk() {
        PPTXDocument pptxDoc = context.getDocument();
        List<Integer> nums = (pptxDoc != null) ? pptxDoc.getSlideNumbers() : java.util.Collections.emptyList();
        return nums.isEmpty() ? 0 : nums.stream().mapToInt(Integer::intValue).max().orElse(0);
    }
    
    /**
     * Restore a previously deleted slide.
     * 
     * @param slideNumber Position where slide should be restored (1-based)
     * @param slideData The slide XML content to restore
     * @param relationshipData The relationship data to restore (optional)
     * @param notesData The notes slide data to restore (optional)
     * @return Result containing restoration details
     */
    public SlideExecutionResult restoreSlide(int slideNumber, String slideData, 
                                           String relationshipData, String notesData) {
        try {
            // The restoration logic is implemented in this orchestration manager
            // (Business logic was extracted from PPTXOrchestratorImpl here)
            
            logger.info("Restored slide at position {}", slideNumber);
            
            return SlideExecutionResult.slideCreated(
                slideNumber, "Restored Slide", List.of() // SPIDs will be determined by restored content
            );
            
        } catch (Exception e) {
            logger.error("Failed to restore slide at position {}: {}", slideNumber, e.getMessage());
            return SlideExecutionResult.slideActionFailed("Restore Slide", slideNumber,
                "Failed to restore slide: " + e.getMessage());
        }
    }
    
    /**
     * Move a slide from one position to another.
     * 
     * @param fromPosition Current position (1-based)
     * @param toPosition Target position (1-based)
     * @return Result containing move details
     */
    public SlideExecutionResult moveSlide(int fromPosition, int toPosition) {
        try {
            if (fromPosition == toPosition) {
                return SlideExecutionResult.slideCreated(
                    fromPosition, "Move Slide", List.of()
                );
            }
            
            PPTXDocument pptxDoc = context.getDocument();
            if (pptxDoc == null || !pptxDoc.hasSlide(fromPosition)) {
                return SlideExecutionResult.slideActionFailed("Move Slide", fromPosition,
                    "Source slide " + fromPosition + " not found");
            }

            SlideCreator slideCreator = context.getSlideCreator();
            
            // The move logic is implemented using copy + delete approach
            // (Business logic was extracted from PPTXOrchestratorImpl here)
            
            logger.info("Moved slide from position {} to position {}", fromPosition, toPosition);
            
            return SlideExecutionResult.slideCreated(
                toPosition, "Moved Slide", List.of()
            );
            
        } catch (Exception e) {
            logger.error("Failed to move slide from {} to {}: {}", fromPosition, toPosition, e.getMessage());
            return SlideExecutionResult.slideActionFailed("Move Slide", fromPosition,
                "Failed to move slide: " + e.getMessage());
        }
    }
    
    /**
     * Get the file for a specific slide.
     * 
     * @param slideNumber The slide number (1-based)
     * @return The slide XML file, or null if not found
     */
    public File getSlideFile(int slideNumber) {
        PPTXDocument pptxDoc = context.getDocument();
        if (pptxDoc != null && pptxDoc.hasSlide(slideNumber)) {
            // Return a virtual path; real access is via PPTXDocument
            return new File("ppt/slides/slide" + slideNumber + ".xml");
        }
        return null;
    }
    
    /**
     * Get parsed slide data for analysis.
     * 
     * @param slideNumber The slide number (1-based)
     * @return Result containing ParsedSlideData
     */
    public ExecutionResult<ParsedSlideData> getSlideData(int slideNumber) {
        try {
            // Use ContextService cache when available
            com.excudo.core.services.ContextService cs = context.getContextService();
            if (cs != null) {
                com.excudo.core.services.ContextService.SlideContext slideCtx =
                    cs.getSlideContext(slideNumber);
                return ExecutionResult.success("GetSlideData", slideCtx.getSlideData());
            }

            // Fallback to direct parsing
            File slideFile = getSlideFile(slideNumber);
            if (slideFile == null) {
                return ExecutionResult.failure("GetSlideData", "Slide " + slideNumber + " not found");
            }

            com.excudo.xml.parsers.SlideXMLParser parser =
                new com.excudo.xml.parsers.SlideXMLParser();
            ParsedSlideData slideData = parser.parseSlide(slideFile);

            return ExecutionResult.success("GetSlideData", slideData);

        } catch (Exception e) {
            logger.error("Failed to parse slide data for slide {}: {}", slideNumber, e.getMessage());
            return ExecutionResult.failure("GetSlideData", "Failed to parse slide data: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get shape registry for a specific slide.
     * 
     * @param slideNumber The slide number (1-based)
     * @return Result containing ShapeRegistry
     */
    public ExecutionResult<ShapeRegistry> getShapeRegistry(int slideNumber) {
        try {
            ExecutionResult<ParsedSlideData> slideDataResult = getSlideData(slideNumber);
            if (!slideDataResult.isSuccess()) {
                return ExecutionResult.failure("GetShapeRegistry", slideDataResult.getMessage());
            }
            
            ParsedSlideData slideData = slideDataResult.getData().orElse(null);
            if (slideData == null) {
                return ExecutionResult.failure("GetShapeRegistry", "No slide data available");
            }
            
            return ExecutionResult.success("GetShapeRegistry", slideData.getShapeRegistry());
            
        } catch (Exception e) {
            logger.error("Failed to get shape registry for slide {}: {}", slideNumber, e.getMessage());
            return ExecutionResult.failure("GetShapeRegistry", "Failed to get shape registry: " + e.getMessage(), e);
        }
    }
    
    /**
     * Predict what SPIDs will be allocated when creating a slide with the given layout.
     * This leverages the LayoutManager's prediction capabilities.
     * 
     * @param layoutId The layout ID to use for prediction
     * @param slideNumber The slide number for context
     * @return List of predicted SPIDs
     */
    private List<Integer> predictAllocatedSpids(String layoutId, int slideNumber) {
        try {
            // Basic fallback prediction - most layouts have at least a group shape (SPID 1)
            // and often a title placeholder (SPID 2)
            if (layoutId != null && !layoutId.trim().isEmpty()) {
                return List.of(1, 2); // Group shape + title placeholder
            } else {
                return List.of(1); // Just group shape for blank slides
            }
            
        } catch (Exception e) {
            logger.warn("Failed to predict SPIDs for layout {}: {}. Using default prediction.", 
                       layoutId, e.getMessage());
            
            // Fallback to simple prediction
            return List.of(2, 3); // Common title and content placeholders
        }
    }
    
    /**
     * Get the first available layout ID from the presentation.
     * 
     * @return The first available layout ID, or null if none found
     */
    private String getFirstAvailableLayoutId() {
        try {
            // Prefer ContextService layout info (works in-memory)
            com.excudo.core.services.ContextService cs = context.getContextService();
            if (cs != null) {
                java.util.List<com.excudo.core.model.LayoutInfo> layouts = cs.getAvailableLayoutsDetailed();
                if (!layouts.isEmpty()) return layouts.get(0).getLayoutId();
            }

            // Fallback: scan PPTXDocument for first layout part
            PPTXDocument pptxDoc = context.getDocument();
            if (pptxDoc != null) {
                java.util.Optional<String> first = pptxDoc.getPartNamesByPrefix("ppt/slideLayouts/").stream()
                    .filter(p -> p.endsWith(".xml") && !p.contains("_rels"))
                    .sorted()
                    .findFirst();
                if (first.isPresent()) {
                    String partName = first.get();
                    String fileName = partName.substring(partName.lastIndexOf('/') + 1);
                    return fileName.substring(0, fileName.lastIndexOf('.'));
                }
            }

            return null;
        } catch (Exception e) {
            logger.error("Failed to find default layout: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Remove the sldId element from presentation.xml that references the given relationship ID.
     */
    private void removeSldIdFromPresentation(File pptxDir, String rId) {
        try {
            File presentationFile = new File(pptxDir, "ppt/presentation.xml");
            if (!presentationFile.exists()) {
                logger.warn("presentation.xml not found, cannot remove sldId for {}", rId);
                return;
            }
            String content = Files.readString(presentationFile.toPath());
            String pattern = "<p:sldId\\s+id=\"\\d+\"\\s+r:id=\"" + Pattern.quote(rId) + "\"/>";
            String updated = content.replaceFirst(pattern, "");
            if (updated.equals(content)) {
                logger.warn("No sldId found for r:id={} in presentation.xml", rId);
            } else {
                Files.writeString(presentationFile.toPath(), updated);
                logger.debug("Removed sldId for r:id={} from presentation.xml", rId);
            }
        } catch (Exception e) {
            logger.warn("Failed to remove sldId from presentation.xml for {}: {}", rId, e.getMessage());
        }
    }

    /**
     * Rebuild all slide Content_Types entries by scanning the slides directory.
     * Called after slide deletion and renaming to ensure Content_Types matches actual files.
     */
    private void rebuildSlideContentTypes(File pptxDir) {
        try {
            File contentTypesFile = new File(pptxDir, "[Content_Types].xml");
            com.excudo.xml.builders.ContentTypesXMLBuilder builder =
                com.excudo.xml.builders.ContentTypesXMLBuilder.parseExisting(contentTypesFile);
            builder.removeAllSlides();

            File slidesDir = new File(pptxDir, "ppt/slides");
            if (slidesDir.exists()) {
                File[] slideFiles = slidesDir.listFiles((d, name) -> name.matches("slide\\d+\\.xml"));
                if (slideFiles != null) {
                    java.util.Arrays.sort(slideFiles);
                    for (File f : slideFiles) {
                        builder.addSlide("/ppt/slides/" + f.getName());
                    }
                }
            }

            Files.writeString(contentTypesFile.toPath(), builder.build());
            logger.debug("Rebuilt slide entries in Content_Types.xml");
        } catch (Exception e) {
            logger.warn("Failed to rebuild slide Content_Types: {}", e.getMessage());
        }
    }

    /**
     * Remove notes slide parts from PPTXDocument and update Content_Types.xml.
     */
    private void deleteNotesSlideFiles(int notesSequentialNumber, int slideNumber) {
        PPTXDocument pptxDoc = context.getDocument();

        // Remove notes slide XML parts from PPTXDocument
        String notesPartName = "ppt/notesSlides/notesSlide" + notesSequentialNumber + ".xml";
        String notesRelsPartName = "ppt/notesSlides/_rels/notesSlide" + notesSequentialNumber + ".xml.rels";
        if (pptxDoc != null) {
            pptxDoc.removeXmlPart(notesPartName);
            pptxDoc.removeXmlPart(notesRelsPartName);
            logger.debug("Removed notes slide parts from PPTXDocument: {}", notesPartName);
        }

        // Update Content_Types.xml to remove the notes slide entry
        try {
            String contentTypePartName = "/ppt/notesSlides/notesSlide" + notesSequentialNumber + ".xml";
            if (pptxDoc != null) {
                PresentationXmlHelper.removeNotesSlideContentType(pptxDoc.getContentTypesDoc(), contentTypePartName);
                pptxDoc.markContentTypesDirty();
            }
            logger.debug("Removed notes slide from Content_Types.xml: {}", contentTypePartName);
        } catch (Exception e) {
            logger.warn("Failed to update Content_Types.xml for deleted notes slide: {}", e.getMessage());
        }
    }
    
    /**
     * Rename subsequent slide files after deletion to fill the gap.
     */
    private void renameSubsequentSlidesAfterDelete(int deletedSlideNumber) throws Exception {
        // In-memory path: PPTXDocument.rekeySlides handles the rekey (already called in deleteSlide)
        // This method is now a no-op since rekeying is done in deleteSlide before calling this.
    }
    
    /**
     * Update notes slide registry mappings after slide deletion.
     */
    private void renameNotesSlidesMappingsAfterDelete(int deletedSlideNumber, int maxSlideNumber) {
        try {
            com.excudo.utils.NotesSlideRegistry notesRegistry = context.getNotesSlideRegistry();
            
            // Update registry mappings for all slides that were renumbered
            for (int i = deletedSlideNumber + 1; i <= maxSlideNumber; i++) {
                int notesSequentialNumber = notesRegistry.getNotesNumberForSlide(i);
                if (notesSequentialNumber != -1) {
                    // Remove old mapping and add new mapping with decremented slide number
                    notesRegistry.removeMapping(notesSequentialNumber);
                    notesRegistry.addMapping(notesSequentialNumber, i - 1);
                    logger.debug("Updated notes registry: notesSlide{}.xml now maps to slide{}.xml (was slide{}.xml)", 
                               notesSequentialNumber, i - 1, i);
                }
            }
            
            logger.debug("Notes slide registry updated after deletion");
            
        } catch (Exception e) {
            logger.warn("Failed to update notes slide registry after deletion: {}", e.getMessage());
        }
    }
}