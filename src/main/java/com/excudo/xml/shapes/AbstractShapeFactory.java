package com.excudo.xml.shapes;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.model.TextBody;
import com.excudo.xml.builders.TextBodyXMLWriter;

/**
 * Abstract base implementation of ShapeFactory using Template Method pattern.
 * Provides common OOXML structure creation and delegates shape-specific logic
 * to concrete subclasses.
 * 
 * This class implements the invariant parts of shape creation:
 * - Non-visual properties (nvSpPr)
 * - Transform and geometry structure
 * - Text body creation
 * 
 * Subclasses implement the variant parts:
 * - Preset geometry (different prst values)
 * - Shape-specific properties
 * - Supported shape types
 */
public abstract class AbstractShapeFactory implements ShapeFactory {
    
    /**
     * Template method for creating a complete shape element.
     * Follows the OOXML structure: p:sp -> nvSpPr, spPr, txBody
     */
    @Override
    public final Element createShape(Document document, SlideShape.ShapeType shapeType, 
                                   int spid, String name, ShapeGeometry geometry, String text) {
        
        // Create the main shape element
        Element shape = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:sp");
        
        // Add non-visual properties (common to all shapes)
        Element nvSpPr = createNonVisualProperties(document, spid, name);
        shape.appendChild(nvSpPr);
        
        // Add shape properties (delegates to subclass for preset geometry)
        Element spPr = createShapeProperties(document, shapeType, geometry);
        shape.appendChild(spPr);
        
        // Add text body -- always present for preset shapes (matches PowerPoint native behavior).
        // When text is null or empty, emit an empty txBody with center alignment (anchor/algn=ctr).
        // When text is provided, use the full TextBody writer.
        Element txBody = createTextBody(document, text);
        shape.appendChild(txBody);

        return shape;
    }
    
    /**
     * Template method for creating shape properties.
     * Combines common transform structure with shape-specific preset geometry.
     */
    @Override
    public final Element createShapeProperties(Document document, SlideShape.ShapeType shapeType, 
                                             ShapeGeometry geometry) {
        
        Element spPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
        
        // Add transform (common to all shapes)
        Element xfrm = createTransform(document, geometry);
        spPr.appendChild(xfrm);
        
        // Add preset geometry (shape-specific)
        Element prstGeom = createPresetGeometry(document, shapeType);
        spPr.appendChild(prstGeom);
        
        return spPr;
    }
    
    /**
     * Common implementation for non-visual properties.
     * Structure is identical for all shape types.
     */
    @Override
    public final Element createNonVisualProperties(Document document, int spid, String name) {
        Element nvSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvSpPr");
        
        // Connection properties
        Element cNvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
        cNvPr.setAttribute("id", String.valueOf(spid));
        cNvPr.setAttribute("name", name);
        nvSpPr.appendChild(cNvPr);
        
        // Shape connection properties
        Element cNvSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvSpPr");
        nvSpPr.appendChild(cNvSpPr);
        
        // Non-visual properties
        Element nvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr");
        nvSpPr.appendChild(nvPr);
        
        return nvSpPr;
    }
    
    /**
     * Common implementation for text body creation.
     * Builds DOM from TextBody model for non-placeholder shapes.
     *
     * When text is null or empty, returns an empty txBody with center alignment
     * (anchor="ctr" on bodyPr and algn="ctr" on the paragraph). This matches the
     * native PowerPoint behavior where every inserted shape receives an empty text body.
     * When text is provided, delegates to TextBodyXMLWriter via TextBody.fromPlainText.
     */
    @Override
    public final Element createTextBody(Document document, String text) {
        if (text != null && !text.trim().isEmpty()) {
            return TextBodyXMLWriter.write(document, TextBody.fromPlainText(text, false));
        }

        // Empty txBody with center alignment -- matches PowerPoint's default for preset shapes
        String drawingNs = com.excudo.core.utils.XMLConstants.DRAWING_NS;
        String presNs    = com.excudo.core.utils.XMLConstants.PRESENTATION_NS;
        Element txBody = document.createElementNS(presNs, "p:txBody");

        Element bodyPr = document.createElementNS(drawingNs, "a:bodyPr");
        bodyPr.setAttribute("anchor", "ctr");
        txBody.appendChild(bodyPr);

        Element lstStyle = document.createElementNS(drawingNs, "a:lstStyle");
        txBody.appendChild(lstStyle);

        Element p = document.createElementNS(drawingNs, "a:p");
        Element pPr = document.createElementNS(drawingNs, "a:pPr");
        pPr.setAttribute("algn", "ctr");
        p.appendChild(pPr);
        Element endParaRPr = document.createElementNS(drawingNs, "a:endParaRPr");
        endParaRPr.setAttribute("lang", "en-US");
        p.appendChild(endParaRPr);
        txBody.appendChild(p);

        return txBody;
    }
    
    /**
     * Create transform element (common to all shapes).
     * 
     * @param document The XML document
     * @param geometry The shape geometry
     * @return The transform element
     */
    protected final Element createTransform(Document document, ShapeGeometry geometry) {
        Element xfrm = document.createElementNS(XMLConstants.DRAWING_NS, "a:xfrm");
        
        // Offset (position)
        Element off = document.createElementNS(XMLConstants.DRAWING_NS, "a:off");
        off.setAttribute("x", String.valueOf(geometry.getX()));
        off.setAttribute("y", String.valueOf(geometry.getY()));
        xfrm.appendChild(off);
        
        // Extents (size)
        Element ext = document.createElementNS(XMLConstants.DRAWING_NS, "a:ext");
        ext.setAttribute("cx", String.valueOf(geometry.getWidth()));
        ext.setAttribute("cy", String.valueOf(geometry.getHeight()));
        xfrm.appendChild(ext);
        
        return xfrm;
    }
    
    /**
     * Create basic preset geometry element with avLst.
     * Subclasses can override this for shape-specific adjustments.
     * 
     * @param document The XML document
     * @param prstValue The preset value (e.g., "rect", "ellipse")
     * @return The preset geometry element
     */
    protected Element createBasicPresetGeometry(Document document, String prstValue) {
        Element prstGeom = document.createElementNS(XMLConstants.DRAWING_NS, "a:prstGeom");
        prstGeom.setAttribute("prst", prstValue);
        
        // Add adjustment value list (required for OOXML compliance)
        Element avLst = document.createElementNS(XMLConstants.DRAWING_NS, "a:avLst");
        prstGeom.appendChild(avLst);
        
        return prstGeom;
    }
    
    /**
     * Abstract method for creating preset geometry.
     * Subclasses implement this to provide shape-specific prst values.
     * 
     * @param document The XML document
     * @param shapeType The shape type
     * @return The preset geometry element
     */
    @Override
    public abstract Element createPresetGeometry(Document document, SlideShape.ShapeType shapeType);
    
    /**
     * Abstract method for supported shape types.
     * Subclasses implement this to declare their capabilities.
     * 
     * @return Array of supported shape types
     */
    @Override
    public abstract SlideShape.ShapeType[] getSupportedShapeTypes();
    
    /**
     * Default implementation checks if shape type is in supported types.
     * Subclasses can override for more complex logic.
     * 
     * @param shapeType The shape type to check
     * @return true if supported
     */
    @Override
    public boolean supportsShapeType(SlideShape.ShapeType shapeType) {
        for (SlideShape.ShapeType supportedType : getSupportedShapeTypes()) {
            if (supportedType == shapeType) {
                return true;
            }
        }
        return false;
    }
}