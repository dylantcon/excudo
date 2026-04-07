package com.excudo.view.rendering;

import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.excudo.core.utils.XMLFactoryProvider;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.util.*;

/**
 * Viewport culling system for efficient rendering optimization.
 * 
 * Only renders shapes that are visible within the current viewport bounds,
 * dramatically improving performance for large slides with many elements.
 */
public class ViewportCuller {
    
    private final XPath xpath;
    
    // Viewport bounds in EMUs (PowerPoint coordinate system)
    // Default to full slide dimensions so nothing is culled before updateViewport() is called
    private long viewportLeft = 0;
    private long viewportTop = 0;
    private long viewportRight = 12192000L;   // Standard slide width
    private long viewportBottom = 6858000L;   // Standard slide height
    
    // Culling statistics
    private int totalShapes;
    private int culledShapes;
    private int renderedShapes;
    
    public ViewportCuller() {
        this.xpath = XMLFactoryProvider.createXPath();
    }
    
    /**
     * Update viewport bounds based on canvas dimensions and zoom level
     */
    public void updateViewport(double canvasWidth, double canvasHeight, double zoomFactor, 
                              double panX, double panY) {
        
        // Convert canvas coordinates to PowerPoint EMUs
        // Standard slide is 10" x 7.5" = 9144000 x 6858000 EMUs
        double slideWidthEMU = 9144000.0;
        double slideHeightEMU = 6858000.0;
        
        // Calculate visible area in EMUs accounting for zoom and pan
        double visibleWidthEMU = slideWidthEMU / zoomFactor;
        double visibleHeightEMU = slideHeightEMU / zoomFactor;
        
        // Calculate viewport bounds with pan offset
        this.viewportLeft = (long) (panX * slideWidthEMU / zoomFactor);
        this.viewportTop = (long) (panY * slideHeightEMU / zoomFactor);
        this.viewportRight = this.viewportLeft + (long) visibleWidthEMU;
        this.viewportBottom = this.viewportTop + (long) visibleHeightEMU;
        
        resetCullingStats();
    }
    
    /**
     * Filter shapes to only those visible in the current viewport
     */
    public List<Element> cullShapesForViewport(NodeList allShapes) {
        List<Element> visibleShapes = new ArrayList<>();
        totalShapes = allShapes.getLength();
        
        for (int i = 0; i < allShapes.getLength(); i++) {
            Element shapeElement = (Element) allShapes.item(i);
            
            if (isShapeInViewport(shapeElement)) {
                visibleShapes.add(shapeElement);
                renderedShapes++;
            } else {
                culledShapes++;
            }
        }
        
        return visibleShapes;
    }
    
    /**
     * Check if a shape intersects with the current viewport
     */
    public boolean isShapeInViewport(Element shapeElement) {
        try {
            ShapeGeometry geometry = extractShapeGeometry(shapeElement);
            if (geometry == null) {
                // If we can't determine geometry, include it to be safe
                return true;
            }
            
            return isGeometryInViewport(geometry);
            
        } catch (Exception e) {
            // If extraction fails, include the shape to avoid missing content
            return true;
        }
    }
    
    /**
     * Check if a SlideShape intersects with the current viewport
     */
    public boolean isShapeInViewport(SlideShape shape) {
        if (shape == null || shape.getGeometry() == null) {
            return true; // Include shapes without geometry to be safe
        }
        
        return isGeometryInViewport(shape.getGeometry());
    }
    
    /**
     * Check if geometry intersects with viewport bounds
     */
    private boolean isGeometryInViewport(ShapeGeometry geometry) {
        long shapeLeft = geometry.getX();
        long shapeTop = geometry.getY();
        long shapeRight = shapeLeft + geometry.getWidth();
        long shapeBottom = shapeTop + geometry.getHeight();
        
        // Check for intersection (overlap)
        return !(shapeRight <= viewportLeft || 
                shapeLeft >= viewportRight || 
                shapeBottom <= viewportTop || 
                shapeTop >= viewportBottom);
    }
    
    /**
     * Extract shape geometry from XML element
     */
    private ShapeGeometry extractShapeGeometry(Element shapeElement) {
        try {
            // Extract position and size from transform
            String xStr = (String) xpath.evaluate(".//a:xfrm/a:off/@x", shapeElement, XPathConstants.STRING);
            String yStr = (String) xpath.evaluate(".//a:xfrm/a:off/@y", shapeElement, XPathConstants.STRING);
            String cxStr = (String) xpath.evaluate(".//a:xfrm/a:ext/@cx", shapeElement, XPathConstants.STRING);
            String cyStr = (String) xpath.evaluate(".//a:xfrm/a:ext/@cy", shapeElement, XPathConstants.STRING);
            
            if (xStr.isEmpty() || yStr.isEmpty() || cxStr.isEmpty() || cyStr.isEmpty()) {
                return null;
            }
            
            long x = Long.parseLong(xStr);
            long y = Long.parseLong(yStr);
            long cx = Long.parseLong(cxStr);
            long cy = Long.parseLong(cyStr);
            
            return new ShapeGeometry(x, y, cx, cy);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get culling efficiency metrics
     */
    public CullingStats getCullingStats() {
        return new CullingStats(totalShapes, renderedShapes, culledShapes);
    }
    
    /**
     * Reset culling statistics
     */
    private void resetCullingStats() {
        totalShapes = 0;
        renderedShapes = 0;
        culledShapes = 0;
    }
    
    /**
     * Calculate buffer zone around viewport for smoother scrolling
     */
    public void setViewportBuffer(double bufferPercentage) {
        if (bufferPercentage <= 0) return;
        
        long widthBuffer = (long) ((viewportRight - viewportLeft) * bufferPercentage);
        long heightBuffer = (long) ((viewportBottom - viewportTop) * bufferPercentage);
        
        viewportLeft -= widthBuffer;
        viewportTop -= heightBuffer;
        viewportRight += widthBuffer;
        viewportBottom += heightBuffer;
    }
    
    /**
     * Check if viewport culling is beneficial for current slide
     */
    public boolean shouldUseCulling(int totalShapeCount) {
        // Only use culling if there are enough shapes to benefit from it
        return totalShapeCount > 20;
    }
    
    /**
     * Get current viewport bounds for debugging
     */
    public ViewportBounds getViewportBounds() {
        return new ViewportBounds(viewportLeft, viewportTop, viewportRight, viewportBottom);
    }
    
    // ========== INNER CLASSES ==========
    
    /**
     * Viewport culling statistics
     */
    public static class CullingStats {
        private final int totalShapes;
        private final int renderedShapes;
        private final int culledShapes;
        
        public CullingStats(int totalShapes, int renderedShapes, int culledShapes) {
            this.totalShapes = totalShapes;
            this.renderedShapes = renderedShapes;
            this.culledShapes = culledShapes;
        }
        
        public int getTotalShapes() { return totalShapes; }
        public int getRenderedShapes() { return renderedShapes; }
        public int getCulledShapes() { return culledShapes; }
        
        public double getCullingEfficiency() {
            return totalShapes > 0 ? (double) culledShapes / totalShapes * 100.0 : 0.0;
        }
        
        public double getRenderPercentage() {
            return totalShapes > 0 ? (double) renderedShapes / totalShapes * 100.0 : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("Culling[total=%d, rendered=%d, culled=%d, efficiency=%.1f%%]",
                totalShapes, renderedShapes, culledShapes, getCullingEfficiency());
        }
    }
    
    /**
     * Viewport bounds representation
     */
    public static class ViewportBounds {
        private final long left, top, right, bottom;
        
        public ViewportBounds(long left, long top, long right, long bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
        
        public long getLeft() { return left; }
        public long getTop() { return top; }
        public long getRight() { return right; }
        public long getBottom() { return bottom; }
        
        public long getWidth() { return right - left; }
        public long getHeight() { return bottom - top; }
        
        @Override
        public String toString() {
            return String.format("Viewport[%d,%d to %d,%d (%dx%d)]",
                left, top, right, bottom, getWidth(), getHeight());
        }
    }
}