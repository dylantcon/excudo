package com.excudo.core.orchestration;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import java.io.File;

/**
 * Manages animation orchestration operations including adding animations to slides.
 *
 * This service extracts animation management logic from PPTXOrchestratorImpl,
 * providing clean delegation for animation operations while centralizing
 * animation injection and configuration.
 */
public class AnimationOrchestrationManager {

    private static final ComponentLogger logger = Logger.getLogger(AnimationOrchestrationManager.class);

    private final OrchestrationContext context;
    private final SlideOrchestrationManager slideManager;

    /**
     * Create an AnimationOrchestrationManager with the given orchestration context.
     *
     * @param context The orchestration context providing access to managers and state
     * @param slideManager The slide orchestration manager for in-memory shape lookups
     */
    public AnimationOrchestrationManager(OrchestrationContext context, SlideOrchestrationManager slideManager) {
        if (context == null) {
            throw new IllegalArgumentException("OrchestrationContext cannot be null");
        }
        if (slideManager == null) {
            throw new IllegalArgumentException("SlideOrchestrationManager cannot be null");
        }
        this.context = context;
        this.slideManager = slideManager;
    }
    
    /**
     * Add animation to a slide shape.
     * 
     * @param slideNumber The slide number (1-based)
     * @param animationBinding The animation configuration to apply
     * @return Operation result containing animation ID for undo purposes
     */
    public ExecutionResult<String> addAnimation(int slideNumber, AnimationBinding animationBinding) {
        return addAnimation(slideNumber, animationBinding, null);
    }
    
    /**
     * Add animation to a slide shape with optional group ID management.
     * 
     * @param slideNumber The slide number (1-based)
     * @param animationBinding The animation configuration to apply
     * @param groupIdManager Optional group ID manager for coordinated animations
     * @return Operation result containing animation ID for undo purposes
     */
    public ExecutionResult<String> addAnimation(int slideNumber,
                                               AnimationBinding animationBinding,
                                               com.excudo.xml.writers.animations.GroupIdManager groupIdManager) {
        try {
            if (animationBinding == null) {
                return ExecutionResult.failure("AddAnimation", "Animation binding cannot be null");
            }

            // Get document from PPTXDocument or fallback to disk
            PPTXDocument pptxDoc = context.getDocument();
            org.w3c.dom.Document document;
            boolean usePptxDoc = (pptxDoc != null && pptxDoc.hasSlide(slideNumber));

            if (usePptxDoc) {
                document = pptxDoc.getSlideDocument(slideNumber);
            } else {
                File slideFile = getSlideFile(slideNumber);
                if (slideFile == null) {
                    return ExecutionResult.failure("AddAnimation", "Slide " + slideNumber + " not found");
                }
                com.excudo.xml.parsers.SlideXMLParser parser =
                    new com.excudo.xml.parsers.SlideXMLParser();
                document = parser.parseSlideDocument(slideFile);
            }

            // Create SlideXMLWriter with optional GroupIdManager
            com.excudo.xml.writers.SlideXMLWriter writer;
            if (groupIdManager != null) {
                writer = new com.excudo.xml.writers.SlideXMLWriter(
                    document, context.getSpidManager(), groupIdManager);
            } else {
                writer = new com.excudo.xml.writers.SlideXMLWriter(
                    document, context.getSpidManager());
            }

            // Apply animation based on type
            if (animationBinding.getAnimationType().isMotionPath()) {
                return addMotionPathAnimation(writer, slideNumber, animationBinding, usePptxDoc);
            } else if (animationBinding.isParagraphLevelAnimation()) {
                return addParagraphLevelAnimation(writer, slideNumber, animationBinding, usePptxDoc);
            } else {
                return addShapeLevelAnimation(writer, slideNumber, animationBinding, usePptxDoc);
            }

        } catch (Exception e) {
            logger.error("Failed to add animation to slide {}: {}", slideNumber, e.getMessage());
            return ExecutionResult.failure("AddAnimation", "Failed to add animation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Add motion path animation to a shape.
     */
    private ExecutionResult<String> addMotionPathAnimation(
            com.excudo.xml.writers.SlideXMLWriter writer,
            int slideNumber, AnimationBinding animationBinding, boolean usePptxDoc) {
        try {
            String motionPath = animationBinding.getMotionPath();
            if (motionPath == null) {
                motionPath = "M 0 0 L 0.25 0 E";
                logger.debug("Using default motion path for animation on slide {}", slideNumber);
            }

            writer.injectAnimation(animationBinding, new ShapeGeometry(0, 0, 914400, 914400));

            // Save: mark dirty or write to disk
            saveAfterAnimation(slideNumber, writer, usePptxDoc);

            String animationId = generateAnimationId(slideNumber, animationBinding, "motion");
            logger.info("Added motion path animation {} to slide {} for SPID {}",
                       animationId, slideNumber, animationBinding.getTargetSpid());

            return ExecutionResult.success("AddAnimation", animationId);

        } catch (Exception e) {
            logger.error("Failed to add motion path animation: {}", e.getMessage());
            return ExecutionResult.failure("AddAnimation", "Motion path animation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Add paragraph-level animation to a text shape.
     * Routes through the factory-based injectAnimation(binding, geometry) path,
     * which correctly produces per-paragraph pRg targeting without grpId/bldP
     * (unlike the legacy injectParagraphRangeAnimation which was invalid).
     */
    private ExecutionResult<String> addParagraphLevelAnimation(
            com.excudo.xml.writers.SlideXMLWriter writer,
            int slideNumber, AnimationBinding animationBinding, boolean usePptxDoc) {
        try {
            ShapeGeometry geometry = getShapeGeometry(slideNumber, animationBinding.getTargetSpid());
            if (geometry == null) {
                geometry = new ShapeGeometry(0, 0, 914400, 914400);
                logger.debug("Using default geometry for paragraph animation on slide {} SPID {}",
                            slideNumber, animationBinding.getTargetSpid());
            }

            AnimationBinding bindingToInject = enrichWithAnimBgIfNeeded(slideNumber, animationBinding);

            writer.injectAnimation(bindingToInject, geometry);

            saveAfterAnimation(slideNumber, writer, usePptxDoc);

            String animationId = generateAnimationId(slideNumber, animationBinding, "paragraph");
            logger.info("Added paragraph-level animation {} to slide {} for SPID {} (paragraphs {}-{})",
                       animationId, slideNumber, animationBinding.getTargetSpid(),
                       animationBinding.getParagraphStart(), animationBinding.getParagraphEnd());

            return ExecutionResult.success("AddAnimation", animationId);

        } catch (Exception e) {
            logger.error("Failed to add paragraph-level animation: {}", e.getMessage());
            return ExecutionResult.failure("AddAnimation", "Paragraph animation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Add standard shape-level animation.
     */
    private ExecutionResult<String> addShapeLevelAnimation(
            com.excudo.xml.writers.SlideXMLWriter writer,
            int slideNumber, AnimationBinding animationBinding, boolean usePptxDoc) {
        try {
            ShapeGeometry geometry = getShapeGeometry(slideNumber, animationBinding.getTargetSpid());

            if (geometry == null) {
                geometry = new ShapeGeometry(0, 0, 914400, 914400);
                logger.debug("Using default geometry for animation on slide {} SPID {}",
                            slideNumber, animationBinding.getTargetSpid());
            }

            AnimationBinding bindingToInject = enrichWithAnimBgIfNeeded(slideNumber, animationBinding);

            writer.injectAnimation(bindingToInject, geometry);

            saveAfterAnimation(slideNumber, writer, usePptxDoc);

            String animationId = generateAnimationId(slideNumber, animationBinding, "shape");
            logger.info("Added shape-level animation {} to slide {} for SPID {}",
                       animationId, slideNumber, animationBinding.getTargetSpid());

            return ExecutionResult.success("AddAnimation", animationId);

        } catch (Exception e) {
            logger.error("Failed to add shape-level animation: {}", e.getMessage());
            return ExecutionResult.failure("AddAnimation", "Shape animation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * If the target shape has a visible background (is not a text placeholder or picture),
     * rebuild the binding with the effectParam "animBg" = "1" so that the bldP entry
     * includes animBg="1" as required by PowerPoint (MS-OE376 4.6.16 / autoUpdateAnimBg).
     *
     * Uses SlideOrchestrationManager for in-memory shape lookups rather than
     * re-parsing from disk.
     */
    private AnimationBinding enrichWithAnimBgIfNeeded(int slideNumber, AnimationBinding binding) {
        SlideShape shape = lookupShape(slideNumber, binding.getTargetSpid());
        if (shape == null) {
            return binding;
        }

        SlideShape.ShapeType shapeType = shape.getType();
        if (shapeType != SlideShape.ShapeType.PLACEHOLDER
                && shapeType != SlideShape.ShapeType.PICTURE
                && shapeType != SlideShape.ShapeType.GROUP) {
            logger.debug("Shape SPID {} type {} requires animBg=1",
                        binding.getTargetSpid(), shapeType);
            return new AnimationBinding.Builder(binding)
                .effectParam("animBg", "1")
                .build();
        }
        return binding;
    }

    /**
     * Get geometry for a shape to support coordinate-based animations.
     * Uses SlideOrchestrationManager for in-memory shape lookups.
     */
    private ShapeGeometry getShapeGeometry(int slideNumber, int spid) {
        SlideShape shape = lookupShape(slideNumber, spid);
        return shape != null ? shape.getGeometry() : null;
    }

    /**
     * Look up a shape via SlideOrchestrationManager's shape registry.
     * Single point of access for shape data -- avoids redundant disk I/O.
     */
    private SlideShape lookupShape(int slideNumber, int spid) {
        try {
            ExecutionResult<ShapeRegistry> registryResult = slideManager.getShapeRegistry(slideNumber);
            if (registryResult.isSuccess() && registryResult.getData().isPresent()) {
                return registryResult.getData().get().getShape(spid);
            }
        } catch (Exception e) {
            logger.debug("Could not look up shape for slide {} SPID {}: {}",
                        slideNumber, spid, e.getMessage());
        }
        return null;
    }
    
    /**
     * Remove an animation from a slide by its timing node ID.
     *
     * @param slideNumber The slide number (1-based)
     * @param timingNodeId The cTn id of the animation to remove
     * @return Operation result
     */
    public ExecutionResult<Void> removeAnimation(int slideNumber, int timingNodeId) {
        try {
            PPTXDocument pptxDoc = context.getDocument();
            org.w3c.dom.Document document;
            boolean usePptxDoc = (pptxDoc != null && pptxDoc.hasSlide(slideNumber));

            if (usePptxDoc) {
                document = pptxDoc.getSlideDocument(slideNumber);
            } else {
                File slideFile = getSlideFile(slideNumber);
                if (slideFile == null) {
                    return ExecutionResult.failure("RemoveAnimation", "Slide " + slideNumber + " not found");
                }
                com.excudo.xml.parsers.SlideXMLParser parser =
                    new com.excudo.xml.parsers.SlideXMLParser();
                document = parser.parseSlideDocument(slideFile);
            }

            com.excudo.xml.writers.SlideXMLWriter writer =
                new com.excudo.xml.writers.SlideXMLWriter(
                    document, context.getSpidManager());

            com.excudo.xml.writers.AnimationInjector.AnimationRemovalResult result =
                writer.removeAnimation(timingNodeId);

            if (!result.isSuccess()) {
                return ExecutionResult.failure("RemoveAnimation", result.getMessage());
            }

            saveAfterAnimation(slideNumber, writer, usePptxDoc);

            logger.info("Removed animation (timingNodeId={}) from slide {}: {}",
                       timingNodeId, slideNumber, result.getMessage());
            return ExecutionResult.success("RemoveAnimation", null);

        } catch (Exception e) {
            logger.error("Failed to remove animation from slide {}: {}", slideNumber, e.getMessage());
            return ExecutionResult.failure("RemoveAnimation",
                "Failed to remove animation: " + e.getMessage(), e);
        }
    }

    /**
     * Update properties of an existing animation on a slide.
     *
     * @param slideNumber The slide number (1-based)
     * @param timingNodeId The cTn id of the animation to update
     * @param properties Map of property names to new values
     * @return Operation result
     */
    public ExecutionResult<Void> updateAnimation(int slideNumber, int timingNodeId,
                                                  java.util.Map<String, String> properties) {
        try {
            PPTXDocument pptxDoc = context.getDocument();
            org.w3c.dom.Document document;
            boolean usePptxDoc = (pptxDoc != null && pptxDoc.hasSlide(slideNumber));

            if (usePptxDoc) {
                document = pptxDoc.getSlideDocument(slideNumber);
            } else {
                File slideFile = getSlideFile(slideNumber);
                if (slideFile == null) {
                    return ExecutionResult.failure("UpdateAnimation", "Slide " + slideNumber + " not found");
                }
                com.excudo.xml.parsers.SlideXMLParser parser =
                    new com.excudo.xml.parsers.SlideXMLParser();
                document = parser.parseSlideDocument(slideFile);
            }

            com.excudo.xml.writers.SlideXMLWriter writer =
                new com.excudo.xml.writers.SlideXMLWriter(
                    document, context.getSpidManager());

            writer.updateAnimationProperties(timingNodeId, properties);

            saveAfterAnimation(slideNumber, writer, usePptxDoc);

            logger.info("Updated animation (timingNodeId={}) on slide {} with properties: {}",
                       timingNodeId, slideNumber, properties);
            return ExecutionResult.success("UpdateAnimation", null);

        } catch (Exception e) {
            logger.error("Failed to update animation on slide {}: {}", slideNumber, e.getMessage());
            return ExecutionResult.failure("UpdateAnimation",
                "Failed to update animation: " + e.getMessage(), e);
        }
    }

    /**
     * Get the file for a specific slide.
     *
     * @param slideNumber The slide number (1-based)
     * @return The slide file, or null if not found
     */
    private File getSlideFile(int slideNumber) {
        PPTXDocument pptxDoc = context.getDocument();
        if (pptxDoc != null && pptxDoc.hasSlide(slideNumber)) {
            return new File("ppt/slides/slide" + slideNumber + ".xml");
        }
        return null;
    }

    /**
     * Save slide after animation mutation: mark dirty in PPTXDocument or write to disk.
     */
    private void saveAfterAnimation(int slideNumber, com.excudo.xml.writers.SlideXMLWriter writer,
                                     boolean usePptxDoc) throws Exception {
        PPTXDocument pptxDoc = context.getDocument();
        if (pptxDoc != null) {
            pptxDoc.markSlideDirty(slideNumber);
        }
        // Invalidate ContextService cache
        com.excudo.core.services.ContextService cs = context.getContextService();
        if (cs != null) {
            cs.invalidateSlide(slideNumber);
        }
    }

    /**
     * Generate unique animation ID for tracking and undo purposes.
     */
    private String generateAnimationId(int slideNumber, AnimationBinding animationBinding, String animationType) {
        return slideNumber + "_" + animationBinding.getTargetSpid() + "_" +
               animationType + "_" + animationBinding.getClickTrigger() + "_" + System.currentTimeMillis();
    }
}