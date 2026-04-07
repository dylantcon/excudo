package com.excudo.xml.shapes;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.SlideShape;

/**
 * Concrete ShapeFactory implementation for basic geometric shapes.
 * Handles rectangle, ellipse, triangle, diamond, and other basic shapes
 * that only require preset geometry without complex adjustments.
 * 
 * This factory leverages the OOXML preset values stored in ShapeType enum
 * to eliminate hardcoded shape type assumptions.
 */
public class BasicShapeFactory extends AbstractShapeFactory {
    
    /**
     * Basic shapes supported by this factory.
     * These shapes only require preset geometry without complex adjustments.
     */
    private static final SlideShape.ShapeType[] SUPPORTED_SHAPES = {
        // Basic Shapes
        SlideShape.ShapeType.RECTANGLE,
        SlideShape.ShapeType.ELLIPSE,
        SlideShape.ShapeType.TRIANGLE,
        SlideShape.ShapeType.DIAMOND,
        SlideShape.ShapeType.PARALLELOGRAM,
        SlideShape.ShapeType.TRAPEZOID,
        SlideShape.ShapeType.PENTAGON,
        SlideShape.ShapeType.HEXAGON,
        SlideShape.ShapeType.HEPTAGON,
        SlideShape.ShapeType.OCTAGON,
        SlideShape.ShapeType.DECAGON,
        SlideShape.ShapeType.DODECAGON,
        
        // Rectangle Variants
        SlideShape.ShapeType.ROUNDED_RECTANGLE,
        SlideShape.ShapeType.SNIP_SINGLE_CORNER_RECTANGLE,
        SlideShape.ShapeType.SNIP_SAME_SIDE_CORNER_RECTANGLE,
        SlideShape.ShapeType.SNIP_DIAGONAL_CORNER_RECTANGLE,
        SlideShape.ShapeType.ROUND_SINGLE_CORNER_RECTANGLE,
        SlideShape.ShapeType.ROUND_SAME_SIDE_CORNER_RECTANGLE,
        SlideShape.ShapeType.ROUND_DIAGONAL_CORNER_RECTANGLE,
        SlideShape.ShapeType.SNIP_ROUND_RECTANGLE,
        
        // Stars and Explosions
        SlideShape.ShapeType.STAR_4_POINTS,
        SlideShape.ShapeType.STAR_5_POINTS,
        SlideShape.ShapeType.STAR_6_POINTS,
        SlideShape.ShapeType.STAR_7_POINTS,
        SlideShape.ShapeType.STAR_8_POINTS,
        SlideShape.ShapeType.STAR_10_POINTS,
        SlideShape.ShapeType.STAR_12_POINTS,
        SlideShape.ShapeType.STAR_16_POINTS,
        SlideShape.ShapeType.STAR_24_POINTS,
        SlideShape.ShapeType.STAR_32_POINTS,
        SlideShape.ShapeType.EXPLOSION_8_POINTS,
        SlideShape.ShapeType.EXPLOSION_14_POINTS,
        
        // Banners and Ribbons
        SlideShape.ShapeType.RIBBON,
        SlideShape.ShapeType.RIBBON_2,
        SlideShape.ShapeType.ELLIPSE_RIBBON,
        SlideShape.ShapeType.ELLIPSE_RIBBON_2,
        SlideShape.ShapeType.VERTICAL_SCROLL,
        SlideShape.ShapeType.HORIZONTAL_SCROLL,

        // Arrows - Basic
        SlideShape.ShapeType.RIGHT_ARROW,
        SlideShape.ShapeType.LEFT_ARROW,
        SlideShape.ShapeType.UP_ARROW,
        SlideShape.ShapeType.DOWN_ARROW,
        SlideShape.ShapeType.LEFT_RIGHT_ARROW,
        SlideShape.ShapeType.UP_DOWN_ARROW,
        SlideShape.ShapeType.QUAD_ARROW,
        SlideShape.ShapeType.LEFT_RIGHT_UP_ARROW,
        SlideShape.ShapeType.BENT_ARROW,
        SlideShape.ShapeType.UTURN_ARROW,
        SlideShape.ShapeType.LEFT_UP_ARROW,
        SlideShape.ShapeType.BENT_UP_ARROW,
        SlideShape.ShapeType.CURVED_RIGHT_ARROW,
        SlideShape.ShapeType.CURVED_LEFT_ARROW,
        SlideShape.ShapeType.CURVED_UP_ARROW,
        SlideShape.ShapeType.CURVED_DOWN_ARROW,
        SlideShape.ShapeType.STRIPED_RIGHT_ARROW,
        SlideShape.ShapeType.NOTCHED_RIGHT_ARROW,
        SlideShape.ShapeType.CIRCULAR_ARROW,

        // Arrow Callouts
        SlideShape.ShapeType.RIGHT_ARROW_CALLOUT,
        SlideShape.ShapeType.LEFT_ARROW_CALLOUT,
        SlideShape.ShapeType.UP_ARROW_CALLOUT,
        SlideShape.ShapeType.DOWN_ARROW_CALLOUT,
        SlideShape.ShapeType.LEFT_RIGHT_ARROW_CALLOUT,
        SlideShape.ShapeType.QUAD_ARROW_CALLOUT,

        // Flowchart Shapes
        SlideShape.ShapeType.FLOWCHART_PROCESS,
        SlideShape.ShapeType.FLOWCHART_DECISION,
        SlideShape.ShapeType.FLOWCHART_INPUT_OUTPUT,
        SlideShape.ShapeType.FLOWCHART_PREDEFINED_PROCESS,
        SlideShape.ShapeType.FLOWCHART_INTERNAL_STORAGE,
        SlideShape.ShapeType.FLOWCHART_DOCUMENT,
        SlideShape.ShapeType.FLOWCHART_MULTIDOCUMENT,
        SlideShape.ShapeType.FLOWCHART_TERMINATOR,
        SlideShape.ShapeType.FLOWCHART_PREPARATION,
        SlideShape.ShapeType.FLOWCHART_MANUAL_INPUT,
        SlideShape.ShapeType.FLOWCHART_MANUAL_OPERATION,
        SlideShape.ShapeType.FLOWCHART_CONNECTOR,
        SlideShape.ShapeType.FLOWCHART_PUNCHED_CARD,
        SlideShape.ShapeType.FLOWCHART_PUNCHED_TAPE,
        SlideShape.ShapeType.FLOWCHART_SUMMING_JUNCTION,
        SlideShape.ShapeType.FLOWCHART_OR,
        SlideShape.ShapeType.FLOWCHART_COLLATE,
        SlideShape.ShapeType.FLOWCHART_SORT,
        SlideShape.ShapeType.FLOWCHART_EXTRACT,
        SlideShape.ShapeType.FLOWCHART_MERGE,
        SlideShape.ShapeType.FLOWCHART_OFFLINE_STORAGE,
        SlideShape.ShapeType.FLOWCHART_ONLINE_STORAGE,
        SlideShape.ShapeType.FLOWCHART_MAGNETIC_TAPE,
        SlideShape.ShapeType.FLOWCHART_MAGNETIC_DISK,
        SlideShape.ShapeType.FLOWCHART_MAGNETIC_DRUM,
        SlideShape.ShapeType.FLOWCHART_DISPLAY,
        SlideShape.ShapeType.FLOWCHART_DELAY,
        SlideShape.ShapeType.FLOWCHART_ALTERNATE_PROCESS,
        SlideShape.ShapeType.FLOWCHART_OFFPAGE_CONNECTOR,

        // Callouts
        SlideShape.ShapeType.RECTANGULAR_CALLOUT,
        SlideShape.ShapeType.ROUNDED_RECTANGULAR_CALLOUT,
        SlideShape.ShapeType.OVAL_CALLOUT,
        SlideShape.ShapeType.LINE_CALLOUT_1,
        SlideShape.ShapeType.LINE_CALLOUT_2,
        SlideShape.ShapeType.LINE_CALLOUT_3,
        SlideShape.ShapeType.ACCENT_CALLOUT_1,
        SlideShape.ShapeType.ACCENT_CALLOUT_2,
        SlideShape.ShapeType.ACCENT_CALLOUT_3,
        SlideShape.ShapeType.ACCENT_BORDER_CALLOUT_1,
        SlideShape.ShapeType.ACCENT_BORDER_CALLOUT_2,
        SlideShape.ShapeType.ACCENT_BORDER_CALLOUT_3,
        SlideShape.ShapeType.WEDGE_RECT_CALLOUT,
        SlideShape.ShapeType.WEDGE_ROUND_RECT_CALLOUT,
        SlideShape.ShapeType.WEDGE_ELLIPSE_CALLOUT,
        SlideShape.ShapeType.CLOUD_CALLOUT,

        // Action Buttons
        SlideShape.ShapeType.ACTION_BUTTON_BLANK,
        SlideShape.ShapeType.ACTION_BUTTON_HOME,
        SlideShape.ShapeType.ACTION_BUTTON_HELP,
        SlideShape.ShapeType.ACTION_BUTTON_INFORMATION,
        SlideShape.ShapeType.ACTION_BUTTON_BACK_OR_PREVIOUS,
        SlideShape.ShapeType.ACTION_BUTTON_FORWARD_OR_NEXT,
        SlideShape.ShapeType.ACTION_BUTTON_BEGINNING,
        SlideShape.ShapeType.ACTION_BUTTON_END,
        SlideShape.ShapeType.ACTION_BUTTON_RETURN,
        SlideShape.ShapeType.ACTION_BUTTON_DOCUMENT,
        SlideShape.ShapeType.ACTION_BUTTON_SOUND,
        SlideShape.ShapeType.ACTION_BUTTON_MOVIE,

        // Special Shapes
        SlideShape.ShapeType.LINE,
        SlideShape.ShapeType.ARC,
        SlideShape.ShapeType.PIE,
        SlideShape.ShapeType.CHORD,
        SlideShape.ShapeType.FRAME,
        SlideShape.ShapeType.HALF_FRAME,
        SlideShape.ShapeType.L_SHAPE,
        SlideShape.ShapeType.DIAGONAL_STRIPE,
        SlideShape.ShapeType.CROSS,
        SlideShape.ShapeType.PLAQUE,
        SlideShape.ShapeType.DONUT,
        SlideShape.ShapeType.BLOCK_ARC,
        SlideShape.ShapeType.FOLDED_CORNER,
        SlideShape.ShapeType.BEVEL,

        // Math Symbols
        SlideShape.ShapeType.PLUS,
        SlideShape.ShapeType.MINUS,
        SlideShape.ShapeType.MULTIPLY,
        SlideShape.ShapeType.DIVIDE,
        SlideShape.ShapeType.EQUAL,
        SlideShape.ShapeType.NOT_EQUAL,

        // Symbols
        SlideShape.ShapeType.HEART,
        SlideShape.ShapeType.LIGHTNING_BOLT,
        SlideShape.ShapeType.SUN,
        SlideShape.ShapeType.MOON,
        SlideShape.ShapeType.CLOUD,
        SlideShape.ShapeType.SMILEY_FACE,
        SlideShape.ShapeType.IRREGULAR_SEAL_1,
        SlideShape.ShapeType.IRREGULAR_SEAL_2,
        SlideShape.ShapeType.NO_SMOKING,
        SlideShape.ShapeType.RIGHT_TRIANGLE,
        SlideShape.ShapeType.TEARDROP,
        SlideShape.ShapeType.HOME_PLATE,
        SlideShape.ShapeType.CHEVRON,
        SlideShape.ShapeType.CAN,
        SlideShape.ShapeType.CUBE,

        // Connectors
        SlideShape.ShapeType.STRAIGHT_CONNECTOR,
        SlideShape.ShapeType.ELBOW_CONNECTOR,
        SlideShape.ShapeType.CURVED_CONNECTOR,

        // Brackets and Braces
        SlideShape.ShapeType.LEFT_BRACKET,
        SlideShape.ShapeType.RIGHT_BRACKET,
        SlideShape.ShapeType.LEFT_BRACE,
        SlideShape.ShapeType.RIGHT_BRACE,
        SlideShape.ShapeType.BRACKET_PAIR,
        SlideShape.ShapeType.BRACE_PAIR,

        // Wave
        SlideShape.ShapeType.WAVE,
        SlideShape.ShapeType.DOUBLE_WAVE
    };
    
    /**
     * Create preset geometry for basic shapes.
     * Uses the OOXML preset value stored in the ShapeType enum.
     * 
     * @param document The XML document
     * @param shapeType The shape type
     * @return The preset geometry element
     */
    @Override
    public Element createPresetGeometry(Document document, SlideShape.ShapeType shapeType) {
        // Validate that this factory supports the shape type
        if (!supportsShapeType(shapeType)) {
            throw new IllegalArgumentException("BasicShapeFactory does not support shape type: " + shapeType);
        }
        
        // Get the OOXML preset value from the enum
        String prstValue = shapeType.getOoxmlPreset();
        
        // Handle shapes without OOXML preset (should not happen for basic shapes)
        if (prstValue == null || prstValue.trim().isEmpty()) {
            throw new IllegalStateException("Shape type " + shapeType + " has no OOXML preset value");
        }
        
        // Create basic preset geometry with the correct prst value
        return createBasicPresetGeometry(document, prstValue);
    }
    
    /**
     * Get the shape types supported by this factory.
     * 
     * @return Array of supported basic shape types
     */
    @Override
    public SlideShape.ShapeType[] getSupportedShapeTypes() {
        return SUPPORTED_SHAPES.clone(); // Return defensive copy
    }
    
    /**
     * Check if shape type is supported by this factory.
     * Optimized lookup for basic shapes.
     * 
     * @param shapeType The shape type to check
     * @return true if this factory can create the shape type
     */
    @Override
    public boolean supportsShapeType(SlideShape.ShapeType shapeType) {
        // Quick check for shapes that have OOXML presets
        if (shapeType.hasOoxmlPreset()) {
            // Check if it's in our supported list
            return super.supportsShapeType(shapeType);
        }
        
        // Shapes without OOXML presets are not basic shapes
        return false;
    }
}