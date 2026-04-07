package com.excudo.core.orchestration;

import com.excudo.core.validation.ValidationResult;
import com.excudo.xml.writers.SlideCreator;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

/**
 * Manages validation orchestration operations including presentation, slide, and operation validation.
 * 
 * This service extracts validation logic from PPTXOrchestratorImpl,
 * providing clean delegation for validation operations while centralizing
 * validation business logic.
 */
public class ValidationOrchestrationManager {
    
    private static final ComponentLogger logger = Logger.getLogger(ValidationOrchestrationManager.class);
    
    private final OrchestrationContext context;
    
    /**
     * Create a ValidationOrchestrationManager with the given orchestration context.
     * 
     * @param context The orchestration context providing access to managers and state
     */
    public ValidationOrchestrationManager(OrchestrationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("OrchestrationContext cannot be null");
        }
        this.context = context;
    }
    
    /**
     * Validate the entire presentation structure.
     * 
     * @return Validation result indicating success or failure with details
     */
    public ValidationResult validatePresentation() {
        try {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            // Use SlideCreator's validation which checks SPIDs and relationships
            SlideCreator slideCreator = context.getSlideCreator();
            SlideCreator.ValidationSummary validationSummary = slideCreator.validatePresentation();
            
            // Collect errors and warnings from SlideCreator validation
            if (validationSummary.hasErrors()) {
                errors.addAll(validationSummary.getErrors());
            }
            
            if (validationSummary.hasWarnings()) {
                warnings.addAll(validationSummary.getWarnings());
            }
            
            // Additional validation checks
            
            // 1. Check extracted directory structure (only when disk path is available)
            File extractedDir = (context.getDocument() != null) ? context.getDocument().getExtractedDir() : null;
            if (extractedDir != null && !validateDirectoryStructure(extractedDir, errors, warnings)) {
                logger.warn("Directory structure validation found issues");
            }
            
            // 2. Validate SPID manager state
            if (!validateSpidManagerState(errors, warnings)) {
                logger.warn("SPID manager validation found issues");
            }
            
            // 3. Validate relationship manager state
            if (!validateRelationshipManagerState(errors, warnings)) {
                logger.warn("Relationship manager validation found issues");
            }
            
            // Determine result based on errors
            if (!errors.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder("Validation found errors:\\n");
                for (String error : errors) {
                    errorMessage.append("  - ").append(error).append("\\n");
                }
                
                if (!warnings.isEmpty()) {
                    errorMessage.append("Warnings:\\n");
                    for (String warning : warnings) {
                        errorMessage.append("  - ").append(warning).append("\\n");
                    }
                }
                
                return ValidationResult.failure(errorMessage.toString());
            } else {
                // Success, optionally with warnings
                if (!warnings.isEmpty()) {
                    logger.info("Validation successful with {} warnings", warnings.size());
                    for (String warning : warnings) {
                        logger.warn("Validation warning: {}", warning);
                    }
                }
                return ValidationResult.success();
            }
            
        } catch (Exception e) {
            logger.error("Presentation validation failed: {}", e.getMessage());
            return ValidationResult.failure("Validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Validate a specific slide.
     * 
     * @param slideNumber The slide number to validate (1-based)
     * @return Validation result for the specific slide
     */
    public ValidationResult validateSlide(int slideNumber) {
        try {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            // Check if slide file exists
            File slideFile = getSlideFile(slideNumber);
            if (slideFile == null || !slideFile.exists()) {
                return ValidationResult.failure("Slide " + slideNumber + " does not exist");
            }
            
            // Parse and validate slide structure
            try {
                com.excudo.xml.parsers.SlideXMLParser parser = 
                    new com.excudo.xml.parsers.SlideXMLParser();
                com.excudo.core.model.ParsedSlideData slideData = 
                    parser.parseSlide(slideFile);
                
                // Validate shapes have valid SPIDs
                com.excudo.core.model.ShapeRegistry registry = slideData.getShapeRegistry();
                for (com.excudo.core.model.SlideShape shape : registry.getAllShapes()) {
                    if (shape.getSpid() <= 0) {
                        errors.add("Shape with invalid SPID: " + shape.getSpid());
                    }
                    
                    // Check for duplicate SPIDs within slide
                    long spidCount = registry.getAllShapes().stream()
                        .mapToInt(com.excudo.core.model.SlideShape::getSpid)
                        .filter(spid -> spid == shape.getSpid())
                        .count();
                    if (spidCount > 1) {
                        errors.add("Duplicate SPID " + shape.getSpid() + " found in slide " + slideNumber);
                    }
                }
                
                // Validate animation bindings reference valid SPIDs
                for (com.excudo.core.model.AnimationBinding binding : slideData.getAnimationBindings()) {
                    int targetSpid = binding.getTargetSpid();
                    if (registry.getShape(targetSpid) == null) {
                        errors.add("Animation references non-existent SPID: " + targetSpid);
                    }
                }
                
                logger.info("Validated slide {} with {} shapes and {} animations", 
                           slideNumber, registry.getAllShapes().size(), slideData.getAnimationBindings().size());
                
            } catch (Exception e) {
                errors.add("Failed to parse slide " + slideNumber + ": " + e.getMessage());
            }
            
            // Check relationships file (in-memory: check PPTXDocument part)
            com.excudo.core.model.PPTXDocument pptxDoc = context.getDocument();
            boolean hasRels = (pptxDoc != null) &&
                pptxDoc.hasPart("ppt/slides/_rels/slide" + slideNumber + ".xml.rels");
            if (!hasRels) {
                warnings.add("Relationships part missing for slide " + slideNumber);
            }
            
            if (!errors.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder("Slide " + slideNumber + " validation errors:\\n");
                for (String error : errors) {
                    errorMessage.append("  - ").append(error).append("\\n");
                }
                return ValidationResult.failure(errorMessage.toString());
            }
            
            return ValidationResult.success();
            
        } catch (Exception e) {
            logger.error("Slide {} validation failed: {}", slideNumber, e.getMessage());
            return ValidationResult.failure("Slide validation failed: " + e.getMessage());
        }
    }
    
    private File getSlideFile(int slideNumber) {
        // Prefer in-memory check via PPTXDocument
        com.excudo.core.model.PPTXDocument pptxDoc = context.getDocument();
        if (pptxDoc != null && pptxDoc.hasSlide(slideNumber)) {
            File extractedDir = pptxDoc.getExtractedDir();
            if (extractedDir != null) {
                File slideFile = new File(new File(extractedDir, "ppt/slides"), "slide" + slideNumber + ".xml");
                return slideFile.exists() ? slideFile : null;
            }
            // In-memory only: return a virtual file (exists check will fail - caller handles this)
            return new File("ppt/slides/slide" + slideNumber + ".xml");
        }
        return null;
    }
    
    /**
     * Validate PPTX directory structure has all required components.
     */
    private boolean validateDirectoryStructure(File extractedDir, List<String> errors, List<String> warnings) {
        boolean isValid = true;
        
        if (!extractedDir.exists() || !extractedDir.isDirectory()) {
            errors.add("PPTX extracted directory is invalid: " + extractedDir);
            return false;
        }
        
        // Check for required directories
        File[] requiredDirs = {
            new File(extractedDir, "ppt"),
            new File(extractedDir, "ppt/slides"),
            new File(extractedDir, "ppt/slideLayouts"),
            new File(extractedDir, "ppt/slideMasters"),
            new File(extractedDir, "_rels")
        };
        
        for (File dir : requiredDirs) {
            if (!dir.exists() || !dir.isDirectory()) {
                errors.add("Required directory missing: " + dir.getName());
                isValid = false;
            }
        }
        
        // Check for required files
        File[] requiredFiles = {
            new File(extractedDir, "[Content_Types].xml"),
            new File(extractedDir, "_rels/.rels"),
            new File(extractedDir, "ppt/presentation.xml"),
            new File(extractedDir, "ppt/_rels/presentation.xml.rels")
        };
        
        for (File file : requiredFiles) {
            if (!file.exists() || !file.isFile()) {
                errors.add("Required file missing: " + file.getName());
                isValid = false;
            }
        }
        
        return isValid;
    }
    
    /**
     * Validate SPID manager is in a consistent state.
     */
    private boolean validateSpidManagerState(List<String> errors, List<String> warnings) {
        try {
            com.excudo.xml.writers.SPIDManager spidManager = context.getSpidManager();
            
            if (spidManager == null) {
                errors.add("SPID manager not initialized");
                return false;
            }
            
            // SPID manager validation would check for duplicate SPIDs across slides
            // For now, this is a placeholder for more sophisticated validation
            logger.debug("SPID manager validation passed");
            
            return true;
            
        } catch (Exception e) {
            errors.add("SPID manager validation failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Validate relationship manager is in a consistent state.
     */
    private boolean validateRelationshipManagerState(List<String> errors, List<String> warnings) {
        try {
            com.excudo.xml.writers.RelationshipManager relationshipManager = 
                context.getRelationshipManager();
            
            if (relationshipManager == null) {
                errors.add("Relationship manager not initialized");
                return false;
            }
            
            // Relationship manager validation would check for orphaned relationships
            // For now, this is a placeholder for more sophisticated validation
            logger.debug("Relationship manager validation passed");
            
            return true;
            
        } catch (Exception e) {
            errors.add("Relationship manager validation failed: " + e.getMessage());
            return false;
        }
    }
}