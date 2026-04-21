package com.excudo.view.rendering.shapes;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.MediaElement;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.view.rendering.RenderingContext;
import com.excudo.view.rendering.SlideRenderContext;
import com.excudo.view.rendering.surface.RenderSurface;
import com.excudo.view.rendering.surface.SurfaceFont;
import com.excudo.view.rendering.surface.SurfaceImage;
import com.excudo.view.rendering.surface.SurfacePaint;
import javafx.geometry.Rectangle2D;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders picture shapes (p:pic) by loading images from PPTXDocument media parts.
 *
 * Resolution chain:
 *   shape DOM -> p:blipFill/a:blip/@r:embed (rId)
 *   slide rels -> Relationship[@Id=rId]/@Target (e.g., ../media/image1.png)
 *   PPTXDocument.getMediaPart("ppt/media/image1.png") -> byte[]
 *   surface.decodeImage() -> SurfaceImage -> surface.drawImage()
 */
public class PictureRenderer implements ModelShapeRenderer {

    private static final ComponentLogger logger = Logger.getLogger("PictureRenderer");

    // Cache decoded images by OPC part name to avoid redundant decoding.
    // The backend-neutral SurfaceImage lets this cache survive a swap
    // from the Canvas backend to the AWT backend without changing callers.
    private final Map<String, SurfaceImage> imageCache = new HashMap<>();

    @Override
    public boolean canRender(SlideShape.ShapeType type) {
        return type == SlideShape.ShapeType.PICTURE;
    }

    @Override
    public void render(SlideShape shape, RenderingContext ctx, SlideRenderContext slideCtx) {
        ShapeGeometry geom = shape.getGeometry();
        if (geom == null) return;

        RenderSurface surface = ctx.getSurface();
        Rectangle2D bounds = ctx.getZoomedCoordinateMapper().mapToCanvas(
            geom.getX(), geom.getY(), geom.getWidth(), geom.getHeight());

        if (slideCtx == null || slideCtx.getDocument() == null) {
            drawPlaceholder(surface, bounds, "(no document)");
            return;
        }

        // Extract the relationship ID from the blip fill
        String rId = extractBlipRId(shape.getXmlElement());
        if (rId == null) {
            drawPlaceholder(surface, bounds, "(no blip)");
            return;
        }

        // Resolve to OPC part name via slide rels
        String partName = resolveMediaPartName(slideCtx.getDocument(), slideCtx.getSlideNumber(), rId);
        if (partName == null) {
            drawPlaceholder(surface, bounds, "(rel not found: " + rId + ")");
            return;
        }

        // Load and cache the image
        SurfaceImage image = imageCache.computeIfAbsent(partName,
            pn -> loadImage(surface, slideCtx.getDocument(), pn));
        if (image == null) {
            drawPlaceholder(surface, bounds, partName.substring(partName.lastIndexOf('/') + 1));
            return;
        }

        surface.drawImage(image, bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
    }

    /**
     * Extract r:embed from p:blipFill/a:blip.
     */
    private String extractBlipRId(Element shapeElement) {
        if (shapeElement == null) return null;
        Element blipFill = getChildByLocalName(shapeElement, "blipFill");
        if (blipFill == null) return null;
        Element blip = getChildByLocalName(blipFill, "blip");
        if (blip == null) return null;
        String rId = blip.getAttribute("r:embed");
        if (rId == null || rId.isEmpty()) {
            rId = blip.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed");
        }
        return (rId != null && !rId.isEmpty()) ? rId : null;
    }

    /**
     * Resolve a relationship ID to an OPC part name using the slide's .rels file.
     */
    private String resolveMediaPartName(PPTXDocument doc, int slideNumber, String rId) {
        if (slideNumber <= 0) return null;
        String relsPartName = "ppt/slides/_rels/slide" + slideNumber + ".xml.rels";
        org.w3c.dom.Document relsDoc = doc.getXmlPart(relsPartName);
        if (relsDoc == null) return null;

        NodeList rels = relsDoc.getElementsByTagName("Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element rel = (Element) rels.item(i);
            if (rId.equals(rel.getAttribute("Id"))) {
                String target = rel.getAttribute("Target");
                if (target == null || target.isEmpty()) continue;
                if (target.startsWith("../")) {
                    return "ppt/" + target.substring(3);
                } else if (target.startsWith("/")) {
                    return target.substring(1);
                }
                return "ppt/slides/" + target;
            }
        }
        return null;
    }

    /**
     * Load an image from PPTXDocument media parts via the current surface.
     * Returns null for unsupported formats (EMF, WMF).
     */
    private SurfaceImage loadImage(RenderSurface surface, PPTXDocument doc, String partName) {
        MediaElement media = doc.getMediaPart(partName);
        if (media == null) {
            logger.debug("Media part not found: {}", partName);
            return null;
        }

        String mime = media.getMimeType();
        if (mime != null && (mime.contains("emf") || mime.contains("wmf"))) {
            logger.debug("Unsupported image format: {} ({})", partName, mime);
            return null;
        }

        try {
            return surface.decodeImage(media.getData(), mime);
        } catch (Exception e) {
            logger.debug("Failed to decode image {}: {}", partName, e.getMessage());
            return null;
        }
    }

    /**
     * Draw a placeholder rectangle for images that can't be rendered.
     */
    private void drawPlaceholder(RenderSurface surface, Rectangle2D bounds, String label) {
        surface.setFill(SurfacePaint.Solid.rgb(211, 211, 211)); // LIGHTGRAY
        surface.fillRect(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
        surface.setStroke(SurfacePaint.Solid.rgb(128, 128, 128)); // GRAY
        surface.setLineWidth(1);
        surface.strokeRect(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
        // Draw an X through the placeholder
        surface.strokeLine(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
        surface.strokeLine(bounds.getMaxX(), bounds.getMinY(), bounds.getMinX(), bounds.getMaxY());
        // Label
        surface.setFont(SurfaceFont.of("DejaVu Sans", 10));
        surface.setFill(SurfacePaint.Solid.rgb(169, 169, 169)); // DARKGRAY
        surface.fillText(label, bounds.getMinX() + 4, bounds.getMinY() + 14);
    }

    /**
     * Get a child element by local name (ignoring namespace prefix).
     */
    private Element getChildByLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                if (localName.equals(el.getLocalName())) return el;
            }
        }
        return null;
    }
}
