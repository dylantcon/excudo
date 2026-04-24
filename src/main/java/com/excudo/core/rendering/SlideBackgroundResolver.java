package com.excudo.core.rendering;

import com.excudo.core.model.MediaElement;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.SlideBackground;
import com.excudo.core.themes.HexColor;
import com.excudo.core.themes.ResolvedFill;
import com.excudo.core.themes.ThemeManager;
import com.excudo.core.utils.XMLConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Map;

/**
 * Resolves a slide's {@link SlideBackground} purely from a
 * {@link PPTXDocument} — no orchestrator dependency. Exists so the
 * render pipeline (which holds the document but not necessarily the
 * orchestrator) can compute the same typed background the orchestrator
 * exposes, without plumbing a new argument through every builder site.
 *
 * <p>Walks slide p:bg -> master p:bg, then follows any bgRef through
 * the theme's fmtScheme. Returns null if no background is defined.
 */
public final class SlideBackgroundResolver {

    private static final String PNS = XMLConstants.PRESENTATION_NS;
    private static final String ANS = XMLConstants.DRAWING_NS;
    private static final String MASTER_PART = "ppt/slideMasters/slideMaster1.xml";

    private SlideBackgroundResolver() {}

    /**
     * Resolve the slide's effective background.
     *
     * @param doc         the presentation
     * @param slideNumber 1-based slide index
     * @param clrMap      master's color map (tx1 -> dk1, bg1 -> lt1, etc.)
     *                    used to resolve bgRef scheme colors
     * @return typed {@link SlideBackground}, or null when none is defined
     */
    public static SlideBackground resolve(PPTXDocument doc, int slideNumber,
                                           Map<String, String> clrMap) {
        if (doc == null) return null;
        Document slideDom = doc.getSlideDocument(slideNumber);
        if (slideDom != null) {
            SlideBackground bg = extract(doc, slideDom, clrMap);
            if (bg != null) return bg;
        }
        Document masterDom = doc.getXmlPart(MASTER_PART);
        if (masterDom != null) {
            SlideBackground bg = extract(doc, masterDom, clrMap);
            if (bg != null) return bg;
        }
        return null;
    }

    private static SlideBackground extract(PPTXDocument doc, Document dom,
                                            Map<String, String> clrMap) {
        // Per PML: p:bg is a child of p:cSld (not the root). The same
        // layout applies across sld / sldLayout / sldMaster.
        Element cSld = firstChildNS(dom.getDocumentElement(), PNS, "cSld");
        if (cSld == null) return null;
        Element bg = firstChildNS(cSld, PNS, "bg");
        if (bg == null) return null;

        // bgPr > solidFill (srgbClr / schemeClr)
        Element bgPr = firstChildNS(bg, PNS, "bgPr");
        if (bgPr != null) {
            String hex = extractBgPrSolidHex(bgPr, clrMap);
            if (hex != null) return new SlideBackground.Solid(hex);
        }

        Element bgRef = firstChildNS(bg, PNS, "bgRef");
        if (bgRef == null) return null;

        int idx = 0;
        try { idx = Integer.parseInt(bgRef.getAttribute("idx")); }
        catch (NumberFormatException ignored) {}
        if (idx <= 0 || !ThemeManager.isThemeLoaded()) return null;

        Element scheme = firstChildNS(bgRef, ANS, "schemeClr");
        if (scheme == null || !scheme.hasAttribute("val")) return null;

        String colorName = scheme.getAttribute("val");
        String resolvedName = clrMap != null ? clrMap.getOrDefault(colorName, colorName) : colorName;
        HexColor phColor = ThemeManager.getThemeColor(resolvedName);

        ResolvedFill fill = ThemeManager.resolveFillStyle(idx, phColor.withHash());
        if (fill instanceof ResolvedFill.BlipFill blip && blip.relId() != null) {
            SlideBackground.BlipImage img = loadBlipImage(doc, blip);
            if (img != null) return img;
        }
        if (fill instanceof ResolvedFill.SolidFill solid) {
            return new SlideBackground.Solid(solid.hex());
        }
        if (fill instanceof ResolvedFill.GradientFill grad && !grad.stops().isEmpty()) {
            return new SlideBackground.Solid(grad.stops().get(0).hex());
        }
        return new SlideBackground.Solid(phColor.withHash());
    }

    private static String extractBgPrSolidHex(Element bgPr, Map<String, String> clrMap) {
        Element solidFill = firstChildNS(bgPr, ANS, "solidFill");
        if (solidFill == null) return null;
        Element srgb = firstChildNS(solidFill, ANS, "srgbClr");
        if (srgb != null && srgb.hasAttribute("val")) {
            return "#" + srgb.getAttribute("val");
        }
        Element scheme = firstChildNS(solidFill, ANS, "schemeClr");
        if (scheme != null && scheme.hasAttribute("val")) {
            String colorName = scheme.getAttribute("val");
            String resolved = clrMap != null ? clrMap.getOrDefault(colorName, colorName) : colorName;
            return ThemeManager.getThemeColor(resolved).withHash();
        }
        return null;
    }

    /** Resolve the blip's relId via the theme's rels part, pull bytes
     *  out of the document's media store, and assemble a BlipImage. */
    private static SlideBackground.BlipImage loadBlipImage(PPTXDocument doc,
                                                            ResolvedFill.BlipFill blip) {
        Document themeRels = doc.getXmlPart("ppt/theme/_rels/theme1.xml.rels");
        if (themeRels == null) return null;
        String target = findRelTarget(themeRels, blip.relId());
        if (target == null) return null;
        String partName = resolveRelativePart("ppt/theme", target);
        MediaElement media = doc.getMediaPart(partName);
        if (media == null || media.getData() == null || media.getData().length == 0) {
            return null;
        }
        String shadow = null;
        String highlight = null;
        if (blip.duotoneHexes().size() >= 2) {
            shadow = blip.duotoneHexes().get(0);
            highlight = blip.duotoneHexes().get(1);
        }
        return new SlideBackground.BlipImage(partName, media.getMimeType(),
            media.getData(), shadow, highlight);
    }

    private static String findRelTarget(Document relsDom, String relId) {
        if (relId == null) return null;
        Element root = relsDom.getDocumentElement();
        if (root == null) return null;
        NodeList rels = root.getElementsByTagName("Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element r = (Element) rels.item(i);
            if (relId.equals(r.getAttribute("Id"))) {
                String target = r.getAttribute("Target");
                return (target == null || target.isEmpty()) ? null : target;
            }
        }
        return null;
    }

    private static String resolveRelativePart(String baseDir, String target) {
        if (target.startsWith("/")) return target.substring(1);
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        for (String s : baseDir.split("/")) if (!s.isEmpty()) stack.addLast(s);
        for (String seg : target.split("/")) {
            if (seg.isEmpty() || ".".equals(seg)) continue;
            if ("..".equals(seg)) { if (!stack.isEmpty()) stack.removeLast(); }
            else stack.addLast(seg);
        }
        return String.join("/", stack);
    }

    /** Find the first direct child Element matching the given namespace + localName. */
    private static Element firstChildNS(Element parent, String ns, String localName) {
        if (parent == null) return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el
                    && ns.equals(el.getNamespaceURI())
                    && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }
}
