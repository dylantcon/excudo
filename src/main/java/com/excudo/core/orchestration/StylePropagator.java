package com.excudo.core.orchestration;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Set;
import java.util.TreeSet;

/**
 * Propagates theme / master / layout edits to the slides that inherit from
 * them. Registered on the document, so it runs for every such edit no matter
 * which command or agent made it: it marks the orchestrator's derived state
 * stale (master styles and colors, dark-theme flag, layout list, placeholder
 * geometry, per-slide context) and tells listeners which slides were
 * restyled.
 *
 * <p>Runs once per changed part, so everything here must be cheap and
 * idempotent: caches are invalidated, not rebuilt.
 */
final class StylePropagator implements PPTXDocument.StylePartListener {

    private static final ComponentLogger logger = Logger.getLogger(StylePropagator.class);
    private static final String LAYOUT_PREFIX = "ppt/slideLayouts/slideLayout";
    private static final String LAYOUT_RELS_PREFIX = "ppt/slideLayouts/_rels/slideLayout";

    private final PPTXDocument document;
    private final OrchestrationContext context;
    private final SlideMasterOrchestrationManager masterManager;
    private final PresentationOrchestrationManager presentationManager;

    StylePropagator(PPTXDocument document, OrchestrationContext context,
                    SlideMasterOrchestrationManager masterManager,
                    PresentationOrchestrationManager presentationManager) {
        this.document = document;
        this.context = context;
        this.masterManager = masterManager;
        this.presentationManager = presentationManager;
    }

    @Override
    public void onStylePartChanged(String partName) {
        masterManager.invalidateMasterCache();
        presentationManager.invalidateDarkThemeCache();
        if (context.getLayoutManager() != null) context.getLayoutManager().markStale();
        if (context.getContextService() != null) context.getContextService().invalidateAllSlides();

        Set<Integer> affected = affectedSlides(partName);
        logger.debug("Style part {} changed; restyling slides {}", partName, affected);
        SessionManager.getInstance().fireSlidesRestyled(affected);
    }

    /**
     * Slides that inherit from the changed part: a layout affects the slides
     * that use it; the theme or a master affects every slide.
     */
    Set<Integer> affectedSlides(String partName) {
        int layout = layoutNumber(partName);
        if (layout < 0) return new TreeSet<>(document.getSlideNumbers());

        Set<Integer> slides = new TreeSet<>();
        String target = "slideLayout" + layout + ".xml";
        for (int slide : document.getSlideNumbers()) {
            Document rels = document.getXmlPart("ppt/slides/_rels/slide" + slide + ".xml.rels");
            if (rels == null) continue;
            NodeList nodes = rels.getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element rel = (Element) nodes.item(i);
                if (rel.getAttribute("Type").endsWith("/slideLayout")
                        && rel.getAttribute("Target").endsWith("/" + target)) {
                    slides.add(slide);
                    break;
                }
            }
        }
        return slides;
    }

    /** N for slideLayoutN.xml or its rels part; -1 for anything else. */
    static int layoutNumber(String partName) {
        String rest;
        if (partName.startsWith(LAYOUT_RELS_PREFIX)) {
            rest = partName.substring(LAYOUT_RELS_PREFIX.length());
            if (!rest.endsWith(".xml.rels")) return -1;
            rest = rest.substring(0, rest.length() - ".xml.rels".length());
        } else if (partName.startsWith(LAYOUT_PREFIX)) {
            rest = partName.substring(LAYOUT_PREFIX.length());
            if (!rest.endsWith(".xml")) return -1;
            rest = rest.substring(0, rest.length() - ".xml".length());
        } else {
            return -1;
        }
        try {
            return Integer.parseInt(rest);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
