package com.excudo.core.synthesis;

import com.excudo.core.introspection.LayoutBaseline;
import com.excudo.core.introspection.SlideIntrospector;
import com.excudo.core.introspection.TransitionDescriptor;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.orchestration.PPTXOrchestrator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Constructs a {@link SlideState} for a live slide: pulls the typed
 * shape snapshots from the shape registry, animations from parsed
 * slide data, transition + layout baseline from the introspection
 * surface.
 *
 * <p>Two variants:
 * <ul>
 *   <li>{@link #current(int)} -- the slide's actual state now.</li>
 *   <li>{@link #baseline(int)} -- the state the slide would be in if
 *       nothing had been added or modified since the layout applied.
 *       In v1 this is an empty-shapes + no-animations + no-transition
 *       state anchored to the slide's {@link LayoutBaseline}. Refine
 *       later to project per-placeholder defaults from the layout
 *       (title "Click to add title" ghost text, etc.).</li>
 * </ul>
 *
 * <p>Best-effort: returns null when the slide can't be resolved (e.g.,
 * no orchestrator context, slide number out of range).
 */
public final class SlideStateBuilder {

    private final PPTXOrchestrator orchestrator;
    private final SlideIntrospector introspector;

    public SlideStateBuilder(PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("orchestrator must not be null");
        }
        this.orchestrator = orchestrator;
        this.introspector = new SlideIntrospector(orchestrator);
    }

    /** The current state of a slide, read from parsed DOM. */
    public SlideState current(int slideNumber) {
        var ctxOpt = orchestrator.getContext();
        if (ctxOpt.isEmpty()) return null;
        PPTXDocument doc = ctxOpt.get().getDocument();
        if (doc == null) return null;

        ParsedSlideData parsed = doc.getParsedSlideData(slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        if (parsed == null) return null;

        LayoutBaseline baseline = introspector.getLayoutBaseline(slideNumber);
        if (baseline == null) return null;

        Map<Integer, ShapeSnapshot> shapes = buildShapeSnapshots(parsed.getShapeRegistry(),
            slideNumber);
        List<AnimationBinding> anims = parsed.getAnimationBindings() != null
            ? parsed.getAnimationBindings() : Collections.emptyList();
        TransitionDescriptor transition = introspector.getTransition(slideNumber);

        return new SlideState(slideNumber, shapes, anims, transition, baseline);
    }

    /**
     * The baseline state of a slide: the shapes the layout projects
     * via {@link PlaceholderProjector}, no animations, no transition
     * override. When the parser reads a freshly-created slide whose
     * placeholders are untouched, the diff against this baseline will
     * be empty -- authored content shows up as genuine user edits, not
     * as "every placeholder was re-added by the user."
     */
    public SlideState baseline(int slideNumber) {
        LayoutBaseline baseline = introspector.getLayoutBaseline(slideNumber);
        if (baseline == null) return null;
        Map<Integer, ShapeSnapshot> placeholders = PlaceholderProjector.project(baseline);
        return new SlideState(slideNumber, placeholders, Collections.emptyList(),
            null, baseline);
    }

    // ========== Helpers ==========

    private Map<Integer, ShapeSnapshot> buildShapeSnapshots(ShapeRegistry registry, int slideNumber) {
        Map<Integer, ShapeSnapshot> out = new LinkedHashMap<>();
        if (registry == null) return out;
        for (SlideShape s : registry.getAllShapes()) {
            if (s == null) continue;
            int parentSpid = registry.getParentSpid(s.getSpid());
            Integer parent = parentSpid > 0 ? parentSpid : null;
            TextBody body = extractTextBody(s);
            com.excudo.core.model.ConnectorAttachment conn =
                s.getType() == SlideShape.ShapeType.CONNECTION
                    ? extractConnectorAttachment(s)
                    : null;
            out.put(s.getSpid(), new ShapeSnapshot(
                s.getSpid(), s.getName(), s.getType(),
                s.getGeometry(),
                introspector.getShapeStyle(slideNumber, s.getSpid()),
                body,
                s.isTextBox(),
                parent,
                conn));
        }
        return out;
    }

    /**
     * Read the connector-specific structural attributes the writer
     * emits in {@code <p:cxnSp>}: preset family (from {@code prstGeom@prst}),
     * start/end SPID + idx bindings (from {@code <a:stCxn>}/{@code <a:endCxn>}),
     * arrowhead types (from {@code <a:ln><a:headEnd>}/{@code <a:tailEnd>}),
     * and any freeform path geometry (from {@code <a:custGeom><a:pathLst>}).
     */
    private static com.excudo.core.model.ConnectorAttachment extractConnectorAttachment(SlideShape s) {
        if (s == null || s.getXmlElement() == null) return null;
        org.w3c.dom.Element root = s.getXmlElement();

        String connectorType = mapPresetToType(findPresetGeom(root));

        org.w3c.dom.Element nvCxnSpPr = firstDescendantElement(root, "nvCxnSpPr");
        Integer startSpid = null, startIdx = null, endSpid = null, endIdx = null;
        if (nvCxnSpPr != null) {
            org.w3c.dom.Element cNvCxn = firstDescendantElement(nvCxnSpPr, "cNvCxnSpPr");
            if (cNvCxn != null) {
                org.w3c.dom.Element st = firstDescendantElement(cNvCxn, "stCxn");
                if (st != null) {
                    startSpid = parseIntOrNull(st.getAttribute("id"));
                    startIdx  = parseIntOrNull(st.getAttribute("idx"));
                }
                org.w3c.dom.Element en = firstDescendantElement(cNvCxn, "endCxn");
                if (en != null) {
                    endSpid = parseIntOrNull(en.getAttribute("id"));
                    endIdx  = parseIntOrNull(en.getAttribute("idx"));
                }
            }
        }

        org.w3c.dom.Element ln = firstDescendantElement(firstDescendantElement(root, "spPr"), "ln");
        String headEnd = ln != null ? lineEndType(ln, "headEnd") : null;
        String tailEnd = ln != null ? lineEndType(ln, "tailEnd") : null;

        // Custom path: presence of <a:custGeom>/<a:pathLst>; we don't
        // round-trip the path text yet (the writer's AddConnectorCommand
        // surface accepts an M/L/C-style string), so leave null until
        // the freeform case lands a real consumer.
        String customPath = null;

        return new com.excudo.core.model.ConnectorAttachment(
            connectorType, startSpid, startIdx, endSpid, endIdx,
            headEnd, tailEnd, customPath);
    }

    private static String findPresetGeom(org.w3c.dom.Element shape) {
        org.w3c.dom.Element spPr = firstDescendantElement(shape, "spPr");
        if (spPr == null) return null;
        org.w3c.dom.Element prst = firstDescendantElement(spPr, "prstGeom");
        if (prst == null) return null;
        String attr = prst.getAttribute("prst");
        return attr.isEmpty() ? null : attr;
    }

    /** Preset names emitted by ShapeWriter -> the AddConnectorCommand
     *  "type" vocabulary. Default to {@code "line"} for unknown presets
     *  so the connector survives even if a future writer adds new ones. */
    private static String mapPresetToType(String preset) {
        if (preset == null) return "line";
        return switch (preset) {
            case "straightConnector1" -> "straight";
            case "bentConnector3"     -> "elbow";
            case "curvedConnector3"   -> "curved";
            case "line"               -> "line";
            default                   -> "line";
        };
    }

    private static String lineEndType(org.w3c.dom.Element ln, String localName) {
        org.w3c.dom.Element end = firstDescendantElement(ln, localName);
        if (end == null) return null;
        String t = end.getAttribute("type");
        return t.isEmpty() ? null : t;
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static org.w3c.dom.Element firstDescendantElement(org.w3c.dom.Element parent, String localName) {
        if (parent == null) return null;
        org.w3c.dom.NodeList kids = parent.getElementsByTagNameNS("*", localName);
        return kids.getLength() > 0 ? (org.w3c.dom.Element) kids.item(0) : null;
    }

    private static TextBody extractTextBody(SlideShape s) {
        if (s == null || s.getXmlElement() == null) return null;
        var txBody = firstChildElement(s.getXmlElement(), "txBody");
        if (txBody == null) return null;
        return com.excudo.core.metrics.TextBodyExtractor.extract(txBody);
    }

    private static org.w3c.dom.Element firstChildElement(org.w3c.dom.Element parent, String localName) {
        org.w3c.dom.NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && localName.equals(n.getLocalName())) {
                return (org.w3c.dom.Element) n;
            }
        }
        return null;
    }
}
