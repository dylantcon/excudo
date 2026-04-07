package com.excudo.core.templates;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.xml.builders.ShapeXMLBuilder;
import org.w3c.dom.*;
import java.util.*;

/**
 * Factory for creating beautiful template shapes with icons and text
 */
public class TemplateShapeFactory {
    
    private final Document document;
    private final String presentationNamespace;
    private final String drawingNamespace;
    
    public TemplateShapeFactory(Document document) {
        this.document = document;
        this.presentationNamespace = "http://schemas.openxmlformats.org/presentationml/2006/main";
        this.drawingNamespace = "http://schemas.openxmlformats.org/drawingml/2006/main";
    }
    
    /**
     * Template shape styles
     */
    public enum TemplateStyle {
        MODERN_CARD("Modern card with icon sidebar"),
        FLOATING_BUBBLE("Floating bubble with centered icon"),
        CORPORATE_BLOCK("Corporate block with header icon"),
        MINIMAL_LINE("Minimal with line separator"),
        GRADIENT_GLOW("Gradient background with glow effect"),
        MATERIAL_DESIGN("Material Design inspired");
        
        private final String description;
        
        TemplateStyle(String description) {
            this.description = description;
        }
    }
    
    /**
     * Create an icon+text template shape
     */
    public TemplateShapeResult createIconTextShape(int spid, String text, String iconRelId,
                                                   ShapeGeometry geometry, TemplateStyle style) {
        Element shapeGroup = createShapeGroup(spid, geometry);
        
        switch (style) {
            case MODERN_CARD:
                return createModernCard(shapeGroup, spid, text, iconRelId, geometry);
            case FLOATING_BUBBLE:
                return createFloatingBubble(shapeGroup, spid, text, iconRelId, geometry);
            case CORPORATE_BLOCK:
                return createCorporateBlock(shapeGroup, spid, text, iconRelId, geometry);
            case MINIMAL_LINE:
                return createMinimalLine(shapeGroup, spid, text, iconRelId, geometry);
            case GRADIENT_GLOW:
                return createGradientGlow(shapeGroup, spid, text, iconRelId, geometry);
            case MATERIAL_DESIGN:
                return createMaterialDesign(shapeGroup, spid, text, iconRelId, geometry);
            default:
                return createModernCard(shapeGroup, spid, text, iconRelId, geometry);
        }
    }
    
    /**
     * Create modern card style (icon sidebar + text area)
     */
    private TemplateShapeResult createModernCard(Element shapeGroup, int spid, String text, 
                                                String iconRelId, ShapeGeometry geometry) {
        long iconWidth = geometry.getHeight(); // Square icon area
        long textAreaX = geometry.getX() + iconWidth;
        long textAreaWidth = geometry.getWidth() - iconWidth;
        
        // Create main background shape
        Element bgShape = createRoundedRectShape(
            spid + "_bg",
            geometry,
            "#FFFFFF",
            "#E0E0E0",
            1,
            10
        );
        shapeGroup.appendChild(bgShape);
        
        // Create icon background (darker shade)
        Element iconBg = createRoundedRectShape(
            spid + "_iconbg",
            new ShapeGeometry(geometry.getX(), geometry.getY(), iconWidth, geometry.getHeight()),
            "#2196F3",
            null,
            0,
            10
        );
        shapeGroup.appendChild(iconBg);
        
        // Add icon image
        if (iconRelId != null) {
            Element iconPic = createPictureShape(
                spid + "_icon",
                iconRelId,
                new ShapeGeometry(
                    geometry.getX() + iconWidth * 0.25,
                    geometry.getY() + geometry.getHeight() * 0.25,
                    iconWidth * 0.5,
                    geometry.getHeight() * 0.5
                )
            );
            shapeGroup.appendChild(iconPic);
        }
        
        // Add text shape
        Element textShape = createTextShape(
            spid + "_text",
            text,
            new ShapeGeometry(
                textAreaX + 200000, // Padding
                geometry.getY() + 200000,
                textAreaWidth - 400000,
                geometry.getHeight() - 400000
            ),
            18,
            "#333333"
        );
        shapeGroup.appendChild(textShape);
        
        return new TemplateShapeResult(shapeGroup, Arrays.asList(
            spid + "_bg", spid + "_iconbg", spid + "_icon", spid + "_text"
        ));
    }
    
    /**
     * Create floating bubble style
     */
    private TemplateShapeResult createFloatingBubble(Element shapeGroup, int spid, String text,
                                                     String iconRelId, ShapeGeometry geometry) {
        // Create circular/bubble background with shadow
        Element bubble = createRoundedRectShape(
            spid + "_bubble",
            geometry,
            "#FFFFFF",
            null,
            0,
            50 // High corner radius for bubble effect
        );
        
        // Add shadow effect
        addShadowEffect(bubble, 10, 0.3);
        shapeGroup.appendChild(bubble);
        
        // Center icon at top
        if (iconRelId != null) {
            long iconSize = Math.min(geometry.getWidth(), geometry.getHeight()) / 3;
            Element icon = createPictureShape(
                spid + "_icon",
                iconRelId,
                new ShapeGeometry(
                    geometry.getX() + (geometry.getWidth() - iconSize) / 2,
                    geometry.getY() + geometry.getHeight() * 0.15,
                    iconSize,
                    iconSize
                )
            );
            shapeGroup.appendChild(icon);
        }
        
        // Text below icon
        Element textShape = createTextShape(
            spid + "_text",
            text,
            new ShapeGeometry(
                geometry.getX() + geometry.getWidth() * 0.1,
                geometry.getY() + geometry.getHeight() * 0.5,
                geometry.getWidth() * 0.8,
                geometry.getHeight() * 0.4
            ),
            16,
            "#666666"
        );
        // Center align text
        centerAlignText(textShape);
        shapeGroup.appendChild(textShape);
        
        return new TemplateShapeResult(shapeGroup, Arrays.asList(
            spid + "_bubble", spid + "_icon", spid + "_text"
        ));
    }
    
    /**
     * Create corporate block style
     */
    private TemplateShapeResult createCorporateBlock(Element shapeGroup, int spid, String text,
                                                    String iconRelId, ShapeGeometry geometry) {
        // Main block with subtle gradient
        Element mainBlock = createGradientRectShape(
            spid + "_block",
            geometry,
            "#1A237E", // Dark blue
            "#283593", // Lighter blue
            "#E8E8E8",
            1,
            5
        );
        shapeGroup.appendChild(mainBlock);
        
        // Header area with icon
        long headerHeight = geometry.getHeight() / 3;
        
        if (iconRelId != null) {
            long iconSize = headerHeight * 0.6;
            Element icon = createPictureShape(
                spid + "_icon",
                iconRelId,
                new ShapeGeometry(
                    geometry.getX() + headerHeight * 0.2,
                    geometry.getY() + headerHeight * 0.2,
                    iconSize,
                    iconSize
                )
            );
            shapeGroup.appendChild(icon);
        }
        
        // Text in body area
        Element textShape = createTextShape(
            spid + "_text",
            text,
            new ShapeGeometry(
                geometry.getX() + geometry.getWidth() * 0.05,
                geometry.getY() + headerHeight + 100000,
                geometry.getWidth() * 0.9,
                geometry.getHeight() - headerHeight - 200000
            ),
            16,
            "#FFFFFF"
        );
        shapeGroup.appendChild(textShape);
        
        return new TemplateShapeResult(shapeGroup, Arrays.asList(
            spid + "_block", spid + "_icon", spid + "_text"
        ));
    }
    
    /**
     * Create material design style
     */
    private TemplateShapeResult createMaterialDesign(Element shapeGroup, int spid, String text,
                                                    String iconRelId, ShapeGeometry geometry) {
        // Card with material shadow
        Element card = createRoundedRectShape(
            spid + "_card",
            geometry,
            "#FFFFFF",
            null,
            0,
            4
        );
        addShadowEffect(card, 8, 0.2);
        shapeGroup.appendChild(card);
        
        // Colored accent bar at top
        long accentHeight = geometry.getHeight() / 8;
        Element accent = createRectShape(
            spid + "_accent",
            new ShapeGeometry(
                geometry.getX(),
                geometry.getY(),
                geometry.getWidth(),
                accentHeight
            ),
            "#4CAF50", // Material green
            null,
            0
        );
        shapeGroup.appendChild(accent);
        
        // Icon with circular background
        if (iconRelId != null) {
            long iconContainerSize = Math.min(geometry.getWidth(), geometry.getHeight()) / 4;
            long iconX = geometry.getX() + geometry.getWidth() * 0.05;
            long iconY = geometry.getY() + accentHeight + 100000;
            
            // Circular background for icon
            Element iconBg = createCircleShape(
                spid + "_iconbg",
                new ShapeGeometry(iconX, iconY, iconContainerSize, iconContainerSize),
                "#E8F5E9", // Light green
                null,
                0
            );
            shapeGroup.appendChild(iconBg);
            
            // Icon
            long iconSize = iconContainerSize * 0.6;
            long iconOffset = (iconContainerSize - iconSize) / 2;
            Element icon = createPictureShape(
                spid + "_icon",
                iconRelId,
                new ShapeGeometry(
                    iconX + iconOffset,
                    iconY + iconOffset,
                    iconSize,
                    iconSize
                )
            );
            shapeGroup.appendChild(icon);
        }
        
        // Text
        Element textShape = createTextShape(
            spid + "_text",
            text,
            new ShapeGeometry(
                geometry.getX() + geometry.getWidth() * 0.35,
                geometry.getY() + accentHeight + 100000,
                geometry.getWidth() * 0.6,
                geometry.getHeight() - accentHeight - 200000
            ),
            16,
            "#212121"
        );
        shapeGroup.appendChild(textShape);
        
        return new TemplateShapeResult(shapeGroup, Arrays.asList(
            spid + "_card", spid + "_accent", spid + "_iconbg", 
            spid + "_icon", spid + "_text"
        ));
    }
    
    // Simplified implementations for other styles
    private TemplateShapeResult createMinimalLine(Element shapeGroup, int spid, String text,
                                                 String iconRelId, ShapeGeometry geometry) {
        return createModernCard(shapeGroup, spid, text, iconRelId, geometry);
    }
    
    private TemplateShapeResult createGradientGlow(Element shapeGroup, int spid, String text,
                                                   String iconRelId, ShapeGeometry geometry) {
        return createModernCard(shapeGroup, spid, text, iconRelId, geometry);
    }
    
    // Helper methods for shape creation
    private Element createShapeGroup(int spid, ShapeGeometry geometry) {
        Element grpSp = document.createElementNS(presentationNamespace, "p:grpSp");
        
        // Non-visual properties
        Element nvGrpSpPr = document.createElementNS(presentationNamespace, "p:nvGrpSpPr");
        Element cNvPr = document.createElementNS(presentationNamespace, "p:cNvPr");
        cNvPr.setAttribute("id", String.valueOf(spid));
        cNvPr.setAttribute("name", "Template Group " + spid);
        nvGrpSpPr.appendChild(cNvPr);
        nvGrpSpPr.appendChild(document.createElementNS(presentationNamespace, "p:cNvGrpSpPr"));
        nvGrpSpPr.appendChild(document.createElementNS(presentationNamespace, "p:nvPr"));
        grpSp.appendChild(nvGrpSpPr);
        
        // Group shape properties
        Element grpSpPr = document.createElementNS(presentationNamespace, "p:grpSpPr");
        Element xfrm = createTransform(geometry);
        grpSpPr.appendChild(xfrm);
        grpSp.appendChild(grpSpPr);
        
        return grpSp;
    }
    
    private Element createRoundedRectShape(String id, ShapeGeometry geometry, String fillColor,
                                          String borderColor, int borderWidth, int cornerRadius) {
        // Implementation would create full shape XML
        // Simplified for brevity
        Element shape = document.createElementNS(presentationNamespace, "p:sp");
        // Add shape properties...
        return shape;
    }
    
    private Element createGradientRectShape(String id, ShapeGeometry geometry, 
                                           String color1, String color2, String borderColor,
                                           int borderWidth, int cornerRadius) {
        // Implementation would create gradient fill
        Element shape = document.createElementNS(presentationNamespace, "p:sp");
        // Add gradient properties...
        return shape;
    }
    
    private Element createCircleShape(String id, ShapeGeometry geometry, String fillColor,
                                     String borderColor, int borderWidth) {
        // Implementation would create circle/ellipse
        Element shape = document.createElementNS(presentationNamespace, "p:sp");
        // Add circle properties...
        return shape;
    }
    
    private Element createRectShape(String id, ShapeGeometry geometry, String fillColor,
                                   String borderColor, int borderWidth) {
        // Implementation would create rectangle
        Element shape = document.createElementNS(presentationNamespace, "p:sp");
        // Add rect properties...
        return shape;
    }
    
    private Element createTextShape(String id, String text, ShapeGeometry geometry,
                                   int fontSize, String color) {
        // Implementation would create text shape
        Element shape = document.createElementNS(presentationNamespace, "p:sp");
        // Add text properties...
        return shape;
    }
    
    private Element createPictureShape(String id, String relId, ShapeGeometry geometry) {
        // Implementation would create picture shape
        Element pic = document.createElementNS(presentationNamespace, "p:pic");
        // Add picture properties...
        return pic;
    }
    
    private Element createTransform(ShapeGeometry geometry) {
        Element xfrm = document.createElementNS(drawingNamespace, "a:xfrm");
        
        Element off = document.createElementNS(drawingNamespace, "a:off");
        off.setAttribute("x", String.valueOf(geometry.getX()));
        off.setAttribute("y", String.valueOf(geometry.getY()));
        xfrm.appendChild(off);
        
        Element ext = document.createElementNS(drawingNamespace, "a:ext");
        ext.setAttribute("cx", String.valueOf(geometry.getWidth()));
        ext.setAttribute("cy", String.valueOf(geometry.getHeight()));
        xfrm.appendChild(ext);
        
        return xfrm;
    }
    
    private void addShadowEffect(Element shape, int radius, double opacity) {
        // Add shadow effect to shape properties
    }
    
    private void centerAlignText(Element textShape) {
        // Center align text in shape
    }
}

class TemplateShapeResult {
    private final Element shapeGroup;
    private final List<String> componentIds;
    
    public TemplateShapeResult(Element shapeGroup, List<String> componentIds) {
        this.shapeGroup = shapeGroup;
        this.componentIds = componentIds;
    }
    
    public Element getShapeGroup() { return shapeGroup; }
    public List<String> getComponentIds() { return componentIds; }
}