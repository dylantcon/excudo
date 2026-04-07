package com.excudo.xml.writers.animations;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete AnimationFactory implementation for pure scale animations.
 * Handles zoom and size change animations using p:animScale pattern.
 * 
 * Based on timing dump analysis, pure scale animations use:
 * - p:animScale element for size/zoom transformations
 * - Optional p:set element for visibility control
 * 
 * These animations change the scale of shapes using PowerPoint's
 * scale expressions and transformation matrices.
 * 
 * Supported animations (12 pure scale animations from timing dump analysis):
 * - ZOOM: Basic zoom in/out
 * - Various grow/shrink effects (excluding coordinated ones like Grow_and_Turn)
 * 
 * OOXML Pattern: p:animScale (+ optional p:set)
 * 
 * Note: Excludes composite animations like Grow_and_Turn which use
 * coordinated p:anim + p:animEffect patterns - those go to CompositeEntranceFactory.
 */
public class ScaleAnimationFactory extends AbstractAnimationFactory {
    
    /**
     * Animation types supported by this factory.
     * Pure scale animations using p:animScale elements only.
     */
    private static final AnimationType[] SUPPORTED_ANIMATIONS = {
        AnimationType.GROW_SHRINK
        // Note: ZOOM is handled by dedicated ZoomAnimationFactory (registered earlier)
    };
    
    /**
     * Create animation elements for scale animations.
     * Returns p:animScale element with optional p:set for visibility.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @param geometry The target shape geometry for scale calculations
     * @return List containing p:animScale and optional p:set elements
     */
    @Override
    public List<Element> createAnimationElements(Document document, AnimationBinding binding, ShapeGeometry geometry) {
        List<Element> elements = new ArrayList<>();
        
        // For entrance animations, add visibility set first
        if (binding.isEntranceAnimation()) {
            elements.add(createVisibilitySet(document, binding, "visible"));
        }
        
        // Create scale animation
        Element animScale = createScaleAnimation(document, binding, geometry);
        elements.add(animScale);
        
        // For exit animations, add visibility set after scale
        if (binding.isExitAnimation()) {
            elements.add(createVisibilitySet(document, binding, "hidden"));
        }
        
        return elements;
    }
    
    /**
     * Create p:animScale element for size/zoom transformations.
     * Uses PowerPoint's scale expressions and transformation values.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @param geometry The target shape geometry for scale calculations
     * @return The p:animScale element
     */
    private Element createScaleAnimation(Document document, AnimationBinding binding, ShapeGeometry geometry) {
        Element animScale = document.createElementNS(PRESENTATION_NS, "p:animScale");
        
        // Calculate scale values based on animation type
        ScaleValues scaleValues = calculateScaleValues(binding.getAnimationType(), binding.isEntranceAnimation(), binding);
        
        // Create common behavior for scale animation (must come first per OOXML spec)
        Element cBhvr = createCommonBehavior(document, binding, "0", binding.getDuration());
        cBhvr.setAttribute("additive", "base");
        animScale.appendChild(cBhvr);
        
        // Create 'by' element with x and y attributes (OOXML standard - must come after cBhvr)
        Element byElement = document.createElementNS(PRESENTATION_NS, "p:by");
        String[] scaleComponents = parseScaleValues(scaleValues.scaleBy);
        byElement.setAttribute("x", scaleComponents[0]);
        byElement.setAttribute("y", scaleComponents[1]);
        animScale.appendChild(byElement);

        // Note: Oracle confirms animScale has NO attrNameLst (despite MS-OE376 4.6.6a
        // suggesting ScaleX/ScaleY). PowerPoint's own output omits it entirely.

        return animScale;
    }
    
    /**
     * Calculate scale values for specific animation types.
     * Based on PowerPoint's scale transformation patterns from timing dump analysis.
     * 
     * @param animationType The scale animation type
     * @param isEntrance Whether this is an entrance animation
     * @return Scale values for the animation
     */
    private ScaleValues calculateScaleValues(AnimationType animationType, boolean isEntrance, AnimationBinding binding) {
        // Use PowerPoint's scale expressions based on timing dump analysis
        // MS-OE376 4.6.6(b): zoomContents not supported by PowerPoint -- omitted entirely
        return switch (animationType) {
            case ZOOM -> new ScaleValues(isEntrance ? "1,1" : "0,0");

            case GROW_SHRINK -> {
                // Read scalePercent from effectParams, default 150
                double scaleFactor = 1.5;
                String scaleParam = binding.getEffectParam(AnimationBinding.PARAM_SCALE_PERCENT);
                if (scaleParam != null) {
                    try { scaleFactor = Integer.parseInt(scaleParam) / 100.0; } catch (NumberFormatException ignored) {}
                }
                String sv = String.format("%.1f,%.1f", scaleFactor, scaleFactor);
                yield new ScaleValues(sv);
            }

            default -> new ScaleValues(isEntrance ? "1,1" : "0,0");
        };
    }
    
    /**
     * Parse scale values from comma-separated format to OOXML integer format.
     * Converts "1,1" to ["100000", "100000"] (PowerPoint scale units)
     */
    private String[] parseScaleValues(String scaleBy) {
        String[] parts = scaleBy.split(",");
        if (parts.length != 2) {
            // Fallback for invalid format
            return new String[]{"100000", "100000"};
        }
        
        // Convert scale factors to PowerPoint's integer format
        // PowerPoint uses 100000 = 100% (normal size), 150000 = 150%, etc.
        String xScale = convertScaleToInteger(parts[0].trim());
        String yScale = convertScaleToInteger(parts[1].trim());
        
        return new String[]{xScale, yScale};
    }
    
    /**
     * Convert scale factor to PowerPoint's integer format.
     */
    private String convertScaleToInteger(String scaleFactor) {
        try {
            double scale = Double.parseDouble(scaleFactor);
            // PowerPoint uses 100000 = 100%, so multiply by 100000
            int scaleInt = (int)(scale * 100000);
            return String.valueOf(scaleInt);
        } catch (NumberFormatException e) {
            // Default to 100% scale if parsing fails
            return "100000";
        }
    }
    
    /**
     * Helper class for scale transformation values.
     */
    private static class ScaleValues {
        final String scaleBy;

        ScaleValues(String scaleBy) {
            this.scaleBy = scaleBy;
        }
    }
    
    /**
     * Get the animation types supported by this factory.
     * 
     * @return Array of supported scale animation types
     */
    @Override
    public AnimationType[] getSupportedAnimationTypes() {
        return SUPPORTED_ANIMATIONS.clone(); // Return defensive copy
    }
    
    /**
     * Get the OOXML pattern this factory generates.
     * 
     * @return Pattern description for debugging and validation
     */
    @Override
    public String getOoxmlPattern() {
        return "p:animScale";
    }
    
    /**
     * Check if animation type is supported by this factory.
     * Handles pure scale animations only (excludes composite ones).
     * 
     * @param animationType The animation type to check
     * @return true if this factory can create the animation type
     */
    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        // Check if it's a pure scale animation (not composite like Grow_and_Turn)
        switch (animationType) {
            case GROW_SHRINK:
                return true;
            default:
                return false;
        }
    }

}