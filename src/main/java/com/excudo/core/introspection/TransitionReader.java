package com.excudo.core.introspection;

import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import com.excudo.core.model.TransitionType;
import com.excudo.core.orchestration.PPTXOrchestrator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads the effective transition for a slide by walking the OOXML
 * slide &rarr; layout &rarr; master inheritance chain (per
 * feedback_ooxml_inheritance memory: always walk the full chain, never
 * skip levels). Returns a typed {@link TransitionDescriptor} or
 * {@code null} if the entire chain is transition-free.
 *
 * <p>The reader reverse-maps OOXML element+attribute combinations
 * back to {@link TransitionType} by iterating every enum value and
 * matching on {@code (xmlElementName, attributeName, attributeValue)}.
 * That is intentionally slower than a hand-built lookup map, but it
 * guarantees that every {@code TransitionType} the writer can emit
 * is a type the reader can recognize -- one-place-to-update symmetry.
 */
public final class TransitionReader {

    private static final String PML_NS = com.excudo.core.utils.XMLConstants.Namespaces.PML;
    private static final String REL_NS = com.excudo.core.utils.XMLConstants.Namespaces.PACKAGE_REL;
    private static final String MASTER_REL_TYPE = com.excudo.core.utils.XMLConstants.RelTypes.SLIDE_MASTER;

    private final PPTXOrchestrator orchestrator;

    public TransitionReader(PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("orchestrator must not be null");
        }
        this.orchestrator = orchestrator;
    }

    /**
     * Resolve the effective transition for a slide. Returns null when
     * neither the slide nor its layout nor its master declares one.
     */
    public TransitionDescriptor read(int slideNumber) {
        var ctxOpt = orchestrator.getContext();
        if (ctxOpt.isEmpty()) return null;
        PPTXDocument doc = ctxOpt.get().getDocument();
        if (doc == null) return null;

        // Slide-level override has highest precedence.
        Document slideDom = doc.getSlideDocument(slideNumber);
        TransitionDescriptor slideLevel = extract(slideDom, TransitionDescriptor.Source.SLIDE);
        if (slideLevel != null) return slideLevel;

        // Fall back to layout. Layout file path is stored on LayoutInfo
        // without the "ppt/" prefix that part-keys carry, so we prepend.
        PPTXDocumentParser.ParsedPresentationState state = doc.getParsedState();
        if (state == null) return null;
        String layoutId = state.getSlideToLayoutId().get(slideNumber);
        if (layoutId == null) return null;
        LayoutInfo layoutInfo = state.getLayouts().get(layoutId);
        if (layoutInfo == null) return null;
        String layoutPartName = "ppt/" + layoutInfo.getFilePath();
        Document layoutDom = doc.getXmlPart(layoutPartName);
        TransitionDescriptor layoutLevel = extract(layoutDom, TransitionDescriptor.Source.LAYOUT);
        if (layoutLevel != null) return layoutLevel;

        // Fall back to master by following the layout's .rels file.
        Document masterDom = resolveMasterDom(doc, layoutPartName);
        return extract(masterDom, TransitionDescriptor.Source.MASTER);
    }

    /**
     * Extract a transition from the given DOM if present. Handles the
     * PowerPoint convention where {@code <p:transition/>} with no child
     * element encodes the {@link TransitionType#NONE} case explicitly.
     */
    private TransitionDescriptor extract(Document dom, TransitionDescriptor.Source source) {
        if (dom == null) return null;
        Element root = dom.getDocumentElement();
        if (root == null) return null;
        Element transitionEl = firstChildElement(root, "transition");
        if (transitionEl == null) return null;

        String speed = transitionEl.hasAttribute("spd") ? transitionEl.getAttribute("spd") : null;
        Integer dur = parseIntOrNull(transitionEl.getAttribute("dur"));
        Integer advTm = parseIntOrNull(transitionEl.getAttribute("advTm"));

        // Find the first child element of <p:transition> -- that's the
        // effect kind (p:fade, p:push, etc.). If there is no child
        // element, treat as NONE (explicit "no transition" marker).
        Element kindEl = firstElementChild(transitionEl);
        TransitionType type;
        if (kindEl == null) {
            type = TransitionType.NONE;
        } else {
            type = matchType(kindEl);
            if (type == null) {
                // Unknown effect type -- represent as NONE rather than
                // fabricating a default; the synthesizer treats NONE as
                // "nothing to emit" and a warning elsewhere flags the
                // unrecognized shape.
                type = TransitionType.NONE;
            }
        }

        return new TransitionDescriptor(type, speed, dur, advTm, source);
    }

    /** Reverse-lookup from OOXML element+attribute to {@link TransitionType}.
     *  Returns null when no enum value matches the observed shape. */
    private TransitionType matchType(Element kindEl) {
        String elName = "p:" + kindEl.getLocalName();
        TransitionType fallback = null;
        for (TransitionType t : TransitionType.values()) {
            if (!elName.equals(t.getXmlElementName())) continue;
            String attrName = t.getAttributeName();
            if (attrName == null) {
                // No attribute on this enum value; match if the actual
                // element also has no attributes, or if nothing more
                // specific matches.
                if (kindEl.getAttributes().getLength() == 0) return t;
                if (fallback == null) fallback = t;
                continue;
            }
            String expected = t.getAttributeValue();
            String actual = kindEl.hasAttribute(attrName) ? kindEl.getAttribute(attrName) : null;
            if (expected != null && expected.equals(actual)) return t;
        }
        return fallback;
    }

    /** Walk the layout's .rels file, find the slideMaster relationship,
     *  resolve to a part key, and return the master DOM. */
    private Document resolveMasterDom(PPTXDocument doc, String layoutPartName) {
        int lastSlash = layoutPartName.lastIndexOf('/');
        if (lastSlash < 0) return null;
        String dir = layoutPartName.substring(0, lastSlash);           // ppt/slideLayouts
        String filename = layoutPartName.substring(lastSlash + 1);     // slideLayout2.xml
        String relsPartName = dir + "/_rels/" + filename + ".rels";
        Document relsDom = doc.getXmlPart(relsPartName);
        if (relsDom == null) return null;
        Element relsRoot = relsDom.getDocumentElement();
        if (relsRoot == null) return null;
        NodeList rels = relsRoot.getElementsByTagNameNS(REL_NS, "Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element r = (Element) rels.item(i);
            if (!MASTER_REL_TYPE.equals(r.getAttribute("Type"))) continue;
            String target = r.getAttribute("Target");
            if (target == null || target.isEmpty()) continue;
            String masterPart = resolveRelativePart(dir, target);
            Document masterDom = doc.getXmlPart(masterPart);
            if (masterDom != null) return masterDom;
        }
        return null;
    }

    /** Resolve a relationship Target (possibly relative like
     *  "../slideMasters/slideMaster1.xml") against the base directory
     *  (e.g., "ppt/slideLayouts") into the absolute part key the
     *  document uses (e.g., "ppt/slideMasters/slideMaster1.xml"). */
    private String resolveRelativePart(String baseDir, String target) {
        if (target.startsWith("/")) return target.substring(1);
        String[] baseSeg = baseDir.split("/");
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        for (String s : baseSeg) if (!s.isEmpty()) stack.addLast(s);
        for (String seg : target.split("/")) {
            if (seg.isEmpty() || ".".equals(seg)) continue;
            if ("..".equals(seg)) { if (!stack.isEmpty()) stack.removeLast(); }
            else stack.addLast(seg);
        }
        return String.join("/", stack);
    }

    private static Element firstChildElement(Element parent, String localName) {
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            if (localName.equals(((Element) n).getLocalName())
                    && PML_NS.equals(n.getNamespaceURI())) {
                return (Element) n;
            }
        }
        return null;
    }

    private static Element firstElementChild(Element parent) {
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) return (Element) n;
        }
        return null;
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
