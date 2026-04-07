package com.excudo.core.geometry;

import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.geometry.SATCollisionDetector.Vector2D;
import com.excudo.core.geometry.SATCollisionDetector.Polygon;
import com.excudo.core.utils.XMLFactoryProvider;
import org.w3c.dom.Element;
import javax.xml.xpath.*;
import java.util.*;

/**
 * Converts PowerPoint shapes to polygon representations for SAT collision detection.
 * 
 * This class handles the complex task of translating various PowerPoint shape types
 * into geometric polygons that can be used with the SAT algorithm.
 */
public class ShapeToPolygonConverter {
    
    private final XPath xpath;
    
    public ShapeToPolygonConverter() {
        this.xpath = XMLFactoryProvider.createXPath();
    }
    
    /**
     * Convert a SlideShape to a Polygon for SAT collision detection
     */
    public Polygon convertToPolygon(SlideShape shape) {
        if (shape == null || shape.getGeometry() == null) {
            return null;
        }
        
        ShapeGeometry geometry = shape.getGeometry();
        SlideShape.ShapeType type = shape.getType();
        
        // Handle different shape types
        switch (type) {
            case CUSTOM_GEOMETRY:
                return convertCustomGeometry(shape);
            case TRIANGLE:
                return createTriangle(geometry);
            case DIAMOND:
                return createDiamond(geometry);
            case PENTAGON:
                return createPentagon(geometry);
            case HEXAGON:
                return createHexagon(geometry);
            case HEPTAGON:
                return createHeptagon(geometry);
            case OCTAGON:
                return createOctagon(geometry);
            case DECAGON:
                return createDecagon(geometry);
            case DODECAGON:
                return createDodecagon(geometry);
            case STAR_4_POINTS:
                return createStar(geometry, 4);
            case STAR_5_POINTS:
                return createStar(geometry, 5);
            case STAR_6_POINTS:
                return createStar(geometry, 6);
            case STAR_7_POINTS:
                return createStar(geometry, 7);
            case STAR_8_POINTS:
                return createStar(geometry, 8);
            case STAR_10_POINTS:
                return createStar(geometry, 10);
            case STAR_12_POINTS:
                return createStar(geometry, 12);
            case STAR_16_POINTS:
                return createStar(geometry, 16);
            case STAR_24_POINTS:
                return createStar(geometry, 24);
            case STAR_32_POINTS:
                return createStar(geometry, 32);
            case RIGHT_ARROW:
            case LEFT_ARROW:
            case UP_ARROW:
            case DOWN_ARROW:
                return createBasicArrow(geometry, type);
            case HEART:
                return createHeart(geometry);
            case LIGHTNING_BOLT:
                return createLightningBolt(geometry);
            case WAVE:
                return createWave(geometry, false);
            case DOUBLE_WAVE:
                return createWave(geometry, true);
            case EXPLOSION_8_POINTS:
                return createExplosion(geometry, 8);
            case EXPLOSION_14_POINTS:
                return createExplosion(geometry, 14);
            default:
                // For rectangular shapes and others, create a simple rectangle
                return createRectangle(geometry);
        }
    }
    
    /**
     * Extract custom geometry from PowerPoint XML
     */
    private Polygon convertCustomGeometry(SlideShape shape) {
        try {
            Element xmlElement = shape.getXmlElement();
            if (xmlElement == null) {
                return createRectangle(shape.getGeometry());
            }
            
            // Look for custom geometry path data
            Element custGeom = (Element) xpath.evaluate(".//a:custGeom", xmlElement, XPathConstants.NODE);
            if (custGeom == null) {
                return createRectangle(shape.getGeometry());
            }
            
            // Parse path list for move/line commands
            Element pathLst = (Element) xpath.evaluate(".//a:pathLst", custGeom, XPathConstants.NODE);
            if (pathLst == null) {
                return createRectangle(shape.getGeometry());
            }
            
            return parseCustomPath(pathLst, shape.getGeometry());
            
        } catch (Exception e) {
            // Fallback to rectangle if parsing fails
            return createRectangle(shape.getGeometry());
        }
    }
    
    /**
     * Parse custom geometry path commands
     */
    private Polygon parseCustomPath(Element pathLst, ShapeGeometry geometry) {
        List<Vector2D> vertices = new ArrayList<>();
        
        try {
            // Get all move and line commands
            // This is a simplified parser - full OOXML path parsing is quite complex
            
            // For now, create a reasonable polygon approximation
            // TODO: Implement full OOXML path parsing for moveTo, lineTo, curveTo, etc.
            
            // Fallback to octagon for complex custom shapes
            return createOctagon(geometry);
            
        } catch (Exception e) {
            return createRectangle(geometry);
        }
    }
    
    /**
     * Create a simple rectangle polygon
     */
    private Polygon createRectangle(ShapeGeometry geometry) {
        List<Vector2D> vertices = Arrays.asList(
            new Vector2D(geometry.getX(), geometry.getY()),
            new Vector2D(geometry.getX() + geometry.getWidth(), geometry.getY()),
            new Vector2D(geometry.getX() + geometry.getWidth(), geometry.getY() + geometry.getHeight()),
            new Vector2D(geometry.getX(), geometry.getY() + geometry.getHeight())
        );
        return new Polygon(vertices);
    }
    
    /**
     * Create a triangle polygon
     */
    private Polygon createTriangle(ShapeGeometry geometry) {
        double centerX = geometry.getX() + geometry.getWidth() / 2.0;
        double y = geometry.getY();
        double bottom = geometry.getY() + geometry.getHeight();
        double left = geometry.getX();
        double right = geometry.getX() + geometry.getWidth();
        
        List<Vector2D> vertices = Arrays.asList(
            new Vector2D(centerX, y),      // Top point
            new Vector2D(right, bottom),   // Bottom right
            new Vector2D(left, bottom)     // Bottom left
        );
        return new Polygon(vertices);
    }
    
    /**
     * Create a diamond polygon
     */
    private Polygon createDiamond(ShapeGeometry geometry) {
        double centerX = geometry.getX() + geometry.getWidth() / 2.0;
        double centerY = geometry.getY() + geometry.getHeight() / 2.0;
        double left = geometry.getX();
        double right = geometry.getX() + geometry.getWidth();
        double top = geometry.getY();
        double bottom = geometry.getY() + geometry.getHeight();
        
        List<Vector2D> vertices = Arrays.asList(
            new Vector2D(centerX, top),    // Top
            new Vector2D(right, centerY),  // Right
            new Vector2D(centerX, bottom), // Bottom
            new Vector2D(left, centerY)    // Left
        );
        return new Polygon(vertices);
    }
    
    /**
     * Create a regular polygon with specified number of sides
     */
    private Polygon createRegularPolygon(ShapeGeometry geometry, int sides) {
        List<Vector2D> vertices = new ArrayList<>();
        
        double centerX = geometry.getX() + geometry.getWidth() / 2.0;
        double centerY = geometry.getY() + geometry.getHeight() / 2.0;
        double radiusX = geometry.getWidth() / 2.0;
        double radiusY = geometry.getHeight() / 2.0;
        
        for (int i = 0; i < sides; i++) {
            double angle = 2 * Math.PI * i / sides - Math.PI / 2; // Start from top
            double x = centerX + radiusX * Math.cos(angle);
            double y = centerY + radiusY * Math.sin(angle);
            vertices.add(new Vector2D(x, y));
        }
        
        return new Polygon(vertices);
    }
    
    private Polygon createPentagon(ShapeGeometry geometry) {
        return createRegularPolygon(geometry, 5);
    }
    
    private Polygon createHexagon(ShapeGeometry geometry) {
        return createRegularPolygon(geometry, 6);
    }
    
    private Polygon createHeptagon(ShapeGeometry geometry) {
        return createRegularPolygon(geometry, 7);
    }
    
    private Polygon createOctagon(ShapeGeometry geometry) {
        return createRegularPolygon(geometry, 8);
    }
    
    private Polygon createDecagon(ShapeGeometry geometry) {
        return createRegularPolygon(geometry, 10);
    }
    
    private Polygon createDodecagon(ShapeGeometry geometry) {
        return createRegularPolygon(geometry, 12);
    }
    
    /**
     * Create a star polygon with inner and outer points
     */
    private Polygon createStar(ShapeGeometry geometry, int points) {
        List<Vector2D> vertices = new ArrayList<>();
        
        double centerX = geometry.getX() + geometry.getWidth() / 2.0;
        double centerY = geometry.getY() + geometry.getHeight() / 2.0;
        double outerRadiusX = geometry.getWidth() / 2.0;
        double outerRadiusY = geometry.getHeight() / 2.0;
        double innerRadiusX = outerRadiusX * 0.4; // Inner radius is 40% of outer
        double innerRadiusY = outerRadiusY * 0.4;
        
        for (int i = 0; i < points * 2; i++) {
            double angle = 2 * Math.PI * i / (points * 2) - Math.PI / 2;
            double radiusX, radiusY;
            
            if (i % 2 == 0) {
                // Outer point
                radiusX = outerRadiusX;
                radiusY = outerRadiusY;
            } else {
                // Inner point
                radiusX = innerRadiusX;
                radiusY = innerRadiusY;
            }
            
            double x = centerX + radiusX * Math.cos(angle);
            double y = centerY + radiusY * Math.sin(angle);
            vertices.add(new Vector2D(x, y));
        }
        
        return new Polygon(vertices);
    }
    
    /**
     * Create basic arrow shapes
     */
    private Polygon createBasicArrow(ShapeGeometry geometry, SlideShape.ShapeType type) {
        List<Vector2D> vertices = new ArrayList<>();
        
        double x = geometry.getX();
        double y = geometry.getY();
        double width = geometry.getWidth();
        double height = geometry.getHeight();
        
        switch (type) {
            case RIGHT_ARROW:
                vertices.addAll(Arrays.asList(
                    new Vector2D(x, y + height * 0.25),           // Top left of shaft
                    new Vector2D(x + width * 0.6, y + height * 0.25), // Top of arrow
                    new Vector2D(x + width * 0.6, y),            // Top of head
                    new Vector2D(x + width, y + height * 0.5),   // Arrow point
                    new Vector2D(x + width * 0.6, y + height),   // Bottom of head
                    new Vector2D(x + width * 0.6, y + height * 0.75), // Bottom of arrow
                    new Vector2D(x, y + height * 0.75)           // Bottom left of shaft
                ));
                break;
            case LEFT_ARROW:
                vertices.addAll(Arrays.asList(
                    new Vector2D(x, y + height * 0.5),           // Arrow point
                    new Vector2D(x + width * 0.4, y),           // Top of head
                    new Vector2D(x + width * 0.4, y + height * 0.25), // Top of arrow
                    new Vector2D(x + width, y + height * 0.25),  // Top right of shaft
                    new Vector2D(x + width, y + height * 0.75),  // Bottom right of shaft
                    new Vector2D(x + width * 0.4, y + height * 0.75), // Bottom of arrow
                    new Vector2D(x + width * 0.4, y + height)   // Bottom of head
                ));
                break;
            case UP_ARROW:
                vertices.addAll(Arrays.asList(
                    new Vector2D(x + width * 0.5, y),           // Arrow point
                    new Vector2D(x + width, y + height * 0.4),  // Right of head
                    new Vector2D(x + width * 0.75, y + height * 0.4), // Right of arrow
                    new Vector2D(x + width * 0.75, y + height), // Bottom right of shaft
                    new Vector2D(x + width * 0.25, y + height), // Bottom left of shaft
                    new Vector2D(x + width * 0.25, y + height * 0.4), // Left of arrow
                    new Vector2D(x, y + height * 0.4)           // Left of head
                ));
                break;
            case DOWN_ARROW:
                vertices.addAll(Arrays.asList(
                    new Vector2D(x + width * 0.25, y),          // Top left of shaft
                    new Vector2D(x + width * 0.75, y),          // Top right of shaft
                    new Vector2D(x + width * 0.75, y + height * 0.6), // Right of arrow
                    new Vector2D(x + width, y + height * 0.6),  // Right of head
                    new Vector2D(x + width * 0.5, y + height),  // Arrow point
                    new Vector2D(x, y + height * 0.6),          // Left of head
                    new Vector2D(x + width * 0.25, y + height * 0.6) // Left of arrow
                ));
                break;
            default:
                return createRectangle(geometry);
        }
        
        return new Polygon(vertices);
    }
    
    /**
     * Create a heart shape (simplified polygon approximation)
     */
    private Polygon createHeart(ShapeGeometry geometry) {
        List<Vector2D> vertices = new ArrayList<>();
        
        double x = geometry.getX();
        double y = geometry.getY();
        double width = geometry.getWidth();
        double height = geometry.getHeight();
        double centerX = x + width * 0.5;
        
        // Simplified heart shape with 12 points
        vertices.addAll(Arrays.asList(
            new Vector2D(centerX, y + height * 0.3),         // Top center
            new Vector2D(x + width * 0.25, y),               // Left top curve
            new Vector2D(x, y + height * 0.2),               // Left curve
            new Vector2D(x, y + height * 0.4),               // Left side
            new Vector2D(x + width * 0.1, y + height * 0.6), // Left lower
            new Vector2D(centerX, y + height),               // Bottom point
            new Vector2D(x + width * 0.9, y + height * 0.6), // Right lower
            new Vector2D(x + width, y + height * 0.4),       // Right side
            new Vector2D(x + width, y + height * 0.2),       // Right curve
            new Vector2D(x + width * 0.75, y),               // Right top curve
            new Vector2D(x + width * 0.6, y + height * 0.1), // Right top
            new Vector2D(x + width * 0.4, y + height * 0.1)  // Left top
        ));
        
        return new Polygon(vertices);
    }
    
    /**
     * Create a lightning bolt shape
     */
    private Polygon createLightningBolt(ShapeGeometry geometry) {
        List<Vector2D> vertices = new ArrayList<>();
        
        double x = geometry.getX();
        double y = geometry.getY();
        double width = geometry.getWidth();
        double height = geometry.getHeight();
        
        // Simplified lightning bolt with jagged edges
        vertices.addAll(Arrays.asList(
            new Vector2D(x + width * 0.3, y),                // Top
            new Vector2D(x + width * 0.6, y + height * 0.3), // Upper right
            new Vector2D(x + width * 0.4, y + height * 0.35),// Upper middle
            new Vector2D(x + width, y + height * 0.7),       // Right point
            new Vector2D(x + width * 0.6, y + height * 0.75),// Lower right
            new Vector2D(x + width * 0.7, y + height),       // Bottom
            new Vector2D(x + width * 0.4, y + height * 0.7), // Lower middle
            new Vector2D(x, y + height * 0.3),               // Left point
            new Vector2D(x + width * 0.2, y + height * 0.25) // Upper left
        ));
        
        return new Polygon(vertices);
    }
    
    /**
     * Create a wave shape
     */
    private Polygon createWave(ShapeGeometry geometry, boolean doubleWave) {
        List<Vector2D> vertices = new ArrayList<>();
        
        double x = geometry.getX();
        double y = geometry.getY();
        double width = geometry.getWidth();
        double height = geometry.getHeight();
        double centerY = y + height * 0.5;
        double amplitude = height * 0.25;
        
        int points = 20; // Number of points for wave approximation
        
        // Top of wave
        for (int i = 0; i <= points; i++) {
            double progress = (double) i / points;
            double waveX = x + width * progress;
            double waveY = centerY - amplitude * Math.sin(progress * Math.PI * (doubleWave ? 4 : 2));
            vertices.add(new Vector2D(waveX, waveY));
        }
        
        // Bottom of wave (reverse order)
        for (int i = points; i >= 0; i--) {
            double progress = (double) i / points;
            double waveX = x + width * progress;
            double waveY = centerY + amplitude * Math.sin(progress * Math.PI * (doubleWave ? 4 : 2));
            vertices.add(new Vector2D(waveX, waveY));
        }
        
        return new Polygon(vertices);
    }
    
    /**
     * Create an explosion shape (star with irregular points)
     */
    private Polygon createExplosion(ShapeGeometry geometry, int points) {
        List<Vector2D> vertices = new ArrayList<>();
        
        double centerX = geometry.getX() + geometry.getWidth() / 2.0;
        double centerY = geometry.getY() + geometry.getHeight() / 2.0;
        double maxRadiusX = geometry.getWidth() / 2.0;
        double maxRadiusY = geometry.getHeight() / 2.0;
        
        Random random = new Random(42); // Fixed seed for consistency
        
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points - Math.PI / 2;
            
            // Vary the radius for irregular explosion effect
            double radiusVariation = 0.7 + random.nextDouble() * 0.6; // 70% to 130% of max radius
            double radiusX = maxRadiusX * radiusVariation;
            double radiusY = maxRadiusY * radiusVariation;
            
            double x = centerX + radiusX * Math.cos(angle);
            double y = centerY + radiusY * Math.sin(angle);
            vertices.add(new Vector2D(x, y));
        }
        
        return new Polygon(vertices);
    }
    
}