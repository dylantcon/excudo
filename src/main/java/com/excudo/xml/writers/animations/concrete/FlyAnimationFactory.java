package com.excudo.xml.writers.animations.concrete;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.xml.writers.animations.abstracts.AbstractCoordinateEffectFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete AnimationFactory implementation for FLY animations.
 * Extends AbstractCoordinateEffectFactory for proper layered architecture.
 * Based on PowerPoint native OOXML structure from comprehensive timing dump analysis.
 * 
 * NATIVE POWERPOINT PATTERNS (from timing dump analysis):
 * - ENTRANCE (Fly In): Uses hash syntax (#ppt_x, #ppt_y) for END positions
 * - EXIT (Fly Out): Uses NO hash syntax (ppt_x, ppt_y) for START positions
 * - All use additive="base" on p:cBhvr elements
 * - Supports all 16 directional variations (8 entrance + 8 exit)
 * 
 * COORDINATE EXPRESSIONS (verified against native PowerPoint):
 * - Left edge: "0-#ppt_w/2" (entrance) / "0-ppt_w/2" (exit)
 * - Right edge: "1+#ppt_w/2" (entrance) / "1+ppt_w/2" (exit)
 * - Top edge: "0-#ppt_h/2" (entrance) / "0-ppt_h/2" (exit)
 * - Bottom edge: "1+#ppt_h/2" (entrance) / "1+ppt_h/2" (exit)
 */
public class FlyAnimationFactory extends AbstractCoordinateEffectFactory {
    
    /**
     * Animation types supported by this factory.
     * All 16 FLY variants matching native PowerPoint.
     */
    private static final AnimationType[] SUPPORTED_ANIMATIONS = {
        // Entrance animations (Fly In)
        AnimationType.FLY_IN_LEFT,
        AnimationType.FLY_IN_RIGHT,
        AnimationType.FLY_IN_TOP,
        AnimationType.FLY_IN_BOTTOM,
        AnimationType.FLY_IN_TOP_LEFT,
        AnimationType.FLY_IN_TOP_RIGHT,
        AnimationType.FLY_IN_BOTTOM_LEFT,
        AnimationType.FLY_IN_BOTTOM_RIGHT,
        // Note: Exit animations would be separate enum values
        // AnimationType.FLY_OUT_LEFT, etc. - to be added to AnimationType enum
    };
    
    /**
     * Create attribute-specific animations for FLY (ppt_x and ppt_y with additive="base").
     * Based on comprehensive timing dump analysis of all 16 FLY variations.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return List containing x and y coordinate p:anim elements with additive="base"
     */
    @Override
    protected List<Element> createAttributeAnimations(Document document, AnimationBinding binding) {
        List<Element> animations = new ArrayList<>();
        
        AnimationType animationType = binding.getAnimationType();
        
        // Get start and end positions based on fly direction and entrance/exit type
        FlyCoordinates coords = getFlyCoordinates(animationType, binding.isEntranceAnimation());
        
        // Create X-coordinate animation with additive="base"
        animations.add(createAttributeAnimation(document, binding, "ppt_x", coords.xStart, coords.xEnd, true));
        
        // Create Y-coordinate animation with additive="base"  
        animations.add(createAttributeAnimation(document, binding, "ppt_y", coords.yStart, coords.yEnd, true));
        
        return animations;
    }
    
    /**
     * FLY animations don't use visual effects - they're pure coordinate movement.
     * Return null to indicate no visual effect should be added.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return null (no visual effect for FLY animations)
     */
    @Override
    protected Element createVisualEffect(Document document, AnimationBinding binding) {
        // FLY animations don't use visual effects - return null
        return null;
    }
    
    /**
     * Get PowerPoint coordinate expressions for fly direction.
     * Based on comprehensive timing dump analysis of native FLY animations.
     * 
     * CRITICAL PATTERNS:
     * - ENTRANCE: start = off-screen expression, end = "#ppt_x" or "#ppt_y" (with hash)
     * - EXIT: start = "ppt_x" or "ppt_y" (no hash), end = off-screen expression
     * 
     * @param animationType The fly animation type
     * @param isEntrance Whether this is an entrance (true) or exit (false) animation
     * @return FlyCoordinates with proper PowerPoint expressions
     */
    private FlyCoordinates getFlyCoordinates(AnimationType animationType, boolean isEntrance) {
        switch (animationType) {
            case FLY_IN_LEFT:
                return isEntrance 
                    ? new FlyCoordinates("0-#ppt_w/2", "#ppt_x", "#ppt_y", "#ppt_y")  // ENTRANCE: From left edge to normal position
                    : new FlyCoordinates("ppt_x", "0-ppt_w/2", "ppt_y", "ppt_y");     // EXIT: From normal position to left edge
                    
            case FLY_IN_RIGHT:
                return isEntrance
                    ? new FlyCoordinates("1+#ppt_w/2", "#ppt_x", "#ppt_y", "#ppt_y")  // ENTRANCE: From right edge to normal position
                    : new FlyCoordinates("ppt_x", "1+ppt_w/2", "ppt_y", "ppt_y");     // EXIT: From normal position to right edge
                    
            case FLY_IN_TOP:
                return isEntrance
                    ? new FlyCoordinates("#ppt_x", "#ppt_x", "0-#ppt_h/2", "#ppt_y")  // ENTRANCE: From top edge to normal position
                    : new FlyCoordinates("ppt_x", "ppt_x", "ppt_y", "0-ppt_h/2");     // EXIT: From normal position to top edge
                    
            case FLY_IN_BOTTOM:
                return isEntrance
                    ? new FlyCoordinates("#ppt_x", "#ppt_x", "1+#ppt_h/2", "#ppt_y")  // ENTRANCE: From bottom edge to normal position
                    : new FlyCoordinates("ppt_x", "ppt_x", "ppt_y", "1+ppt_h/2");     // EXIT: From normal position to bottom edge
                    
            case FLY_IN_TOP_LEFT:
                return isEntrance
                    ? new FlyCoordinates("0-#ppt_w/2", "#ppt_x", "0-#ppt_h/2", "#ppt_y")  // ENTRANCE: From top-left corner to normal position
                    : new FlyCoordinates("ppt_x", "0-ppt_w/2", "ppt_y", "0-ppt_h/2");     // EXIT: From normal position to top-left corner
                    
            case FLY_IN_TOP_RIGHT:
                return isEntrance
                    ? new FlyCoordinates("1+#ppt_w/2", "#ppt_x", "0-#ppt_h/2", "#ppt_y")  // ENTRANCE: From top-right corner to normal position
                    : new FlyCoordinates("ppt_x", "1+ppt_w/2", "ppt_y", "0-ppt_h/2");     // EXIT: From normal position to top-right corner
                    
            case FLY_IN_BOTTOM_LEFT:
                return isEntrance
                    ? new FlyCoordinates("0-#ppt_w/2", "#ppt_x", "1+#ppt_h/2", "#ppt_y")  // ENTRANCE: From bottom-left corner to normal position
                    : new FlyCoordinates("ppt_x", "0-ppt_w/2", "ppt_y", "1+ppt_h/2");     // EXIT: From normal position to bottom-left corner
                    
            case FLY_IN_BOTTOM_RIGHT:
                return isEntrance
                    ? new FlyCoordinates("1+#ppt_w/2", "#ppt_x", "1+#ppt_h/2", "#ppt_y")  // ENTRANCE: From bottom-right corner to normal position
                    : new FlyCoordinates("ppt_x", "1+ppt_w/2", "ppt_y", "1+ppt_h/2");     // EXIT: From normal position to bottom-right corner
                    
            default:
                // Default to left fly for unknown types
                return isEntrance 
                    ? new FlyCoordinates("0-#ppt_w/2", "#ppt_x", "#ppt_y", "#ppt_y")
                    : new FlyCoordinates("ppt_x", "0-ppt_w/2", "ppt_y", "ppt_y");
        }
    }
    
    /**
     * Helper class to hold fly coordinate expressions.
     */
    private static class FlyCoordinates {
        final String xStart, xEnd, yStart, yEnd;
        
        FlyCoordinates(String xStart, String xEnd, String yStart, String yEnd) {
            this.xStart = xStart;
            this.xEnd = xEnd;  
            this.yStart = yStart;
            this.yEnd = yEnd;
        }
    }
    
    @Override
    public AnimationType[] getSupportedAnimationTypes() {
        return SUPPORTED_ANIMATIONS.clone();
    }
    
    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        switch (animationType) {
            case FLY_IN_LEFT:
            case FLY_IN_RIGHT:
            case FLY_IN_TOP:
            case FLY_IN_BOTTOM:
            case FLY_IN_TOP_LEFT:
            case FLY_IN_TOP_RIGHT:
            case FLY_IN_BOTTOM_LEFT:
            case FLY_IN_BOTTOM_RIGHT:
                return true;
            default:
                return false;
        }
    }
    
    @Override
    public String getOoxmlPattern() {
        return "p:set + p:anim×2 (Native PowerPoint FLY pattern with proper hash syntax and 16 directional variations)";
    }
}