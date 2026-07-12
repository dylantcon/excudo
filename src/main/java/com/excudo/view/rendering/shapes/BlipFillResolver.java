package com.excudo.view.rendering.shapes;

import com.excudo.core.model.MediaElement;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.rendering.surface.RenderSurface;
import com.excudo.core.rendering.surface.SurfaceImage;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared blip (embedded image) resolution + decode cache. Used by both
 * {@link PictureRenderer} (p:pic shapes) and the picture-fill path in
 * {@code ShapeStyleExtractor} (a:blipFill inside p:spPr), so there is
 * exactly one rId &rarr; part-name &rarr; decoded-image pipeline.
 *
 * <p>The cache is keyed by document identity (weakly, so closing a deck
 * releases its images), then by backend class + part name — a JavaFX
 * {@code Image} decoded for the Canvas backend must never be handed to
 * the AWT backend and vice versa.
 */
public final class BlipFillResolver {

    private static final ComponentLogger logger = Logger.getLogger("BlipFillResolver");

    private static final Map<PPTXDocument, Map<String, SurfaceImage>> CACHE =
        java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private BlipFillResolver() {}

    /**
     * Resolve a slide-relative relationship id to a decoded image.
     * Returns null when the relationship, media part, or decode fails
     * (e.g. WMF/EMF).
     */
    public static SurfaceImage resolve(RenderSurface surface, PPTXDocument doc,
                                       int slideNumber, String rId) {
        String partName = resolveMediaPartName(doc, slideNumber, rId);
        if (partName == null) return null;
        return loadCached(surface, doc, partName);
    }

    /**
     * Resolve a relationship ID to an OPC part name using the slide's
     * .rels file (e.g. rId2 -> ppt/media/image1.png).
     */
    public static String resolveMediaPartName(PPTXDocument doc, int slideNumber, String rId) {
        if (doc == null || rId == null || slideNumber <= 0) return null;
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
     * Load + decode a media part through the surface, memoised per
     * (document, backend, part). Returns null for unsupported formats.
     */
    public static SurfaceImage loadCached(RenderSurface surface, PPTXDocument doc, String partName) {
        if (surface == null || doc == null || partName == null) return null;
        Map<String, SurfaceImage> perDoc =
            CACHE.computeIfAbsent(doc, d -> new ConcurrentHashMap<>());
        String key = surface.getClass().getName() + "|" + partName;
        // computeIfAbsent can't cache nulls; use containsKey to avoid
        // re-decoding known failures.
        if (perDoc.containsKey(key)) return perDoc.get(key);
        SurfaceImage decoded = decode(surface, doc, partName);
        if (decoded != null) perDoc.put(key, decoded);
        return decoded;
    }

    /**
     * Native pixel dimensions of a media part, read from the image
     * header only (no decode). Needed by tiled picture fills: backends
     * may decode a subsampled raster when the target surface is small,
     * but OOXML tile geometry is defined against the image's native
     * size. Returns null when no reader understands the format.
     */
    public static int[] nativeSize(PPTXDocument doc, String partName) {
        if (doc == null || partName == null) return null;
        MediaElement media = doc.getMediaPart(partName);
        if (media == null || media.getData() == null) return null;
        try (javax.imageio.stream.ImageInputStream iis =
                 javax.imageio.ImageIO.createImageInputStream(
                     new java.io.ByteArrayInputStream(media.getData()))) {
            if (iis == null) return null;
            java.util.Iterator<javax.imageio.ImageReader> readers =
                javax.imageio.ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static SurfaceImage decode(RenderSurface surface, PPTXDocument doc, String partName) {
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
}
