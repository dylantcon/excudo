package com.excudo.core.introspection;

import com.excudo.core.metrics.LayoutIssue;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideBackground;
import com.excudo.core.model.SlideShape;
import com.excudo.core.rendering.SlideBackgroundResolver;
import com.excudo.core.themes.ThemeManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Flags text whose resolved colour is too close to what is behind it, by WCAG
 * contrast ratio: 4.5:1 for normal text, 3:1 for large text (18pt and up, or
 * 14pt and up in bold). Two tiers: under {@link #NEARLY_INVISIBLE} is
 * LOW_CONTRAST (fix it); between that and the WCAG threshold is
 * CONTRAST_ADVISORY -- it fails AA, but white on a mid-tone accent is common
 * and readable for large labels, and flagging those as errors had agents
 * recolouring every coloured box.
 *
 * <p>"Behind" is the shape's own fill when it has one; otherwise the topmost
 * filled shape underneath it (z-order is document order) at its centre;
 * otherwise the slide background (slide, then layout, then master). A
 * gradient is checked against each of its stops. Picture fills and image
 * backgrounds can't be checked and are reported as such, never guessed.
 */
public final class ContrastChecker {

    private static final String P = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    static final double NORMAL_MIN = 4.5;
    static final double LARGE_MIN = 3.0;
    static final double NEARLY_INVISIBLE = 2.0;

    /** What a shape's fill amounts to: some colours, none (see-through), or uncheckable. */
    private record Fill(List<String> colours, boolean transparent, String uncheckable, String source) {
        static Fill none() { return new Fill(List.of(), true, null, null); }
    }

    private ContrastChecker() {}

    public static List<LayoutIssue> check(PPTXDocument doc, int slideNumber, List<SlideShape> shapes,
                                          Map<String, String> masterClrMap) {
        List<LayoutIssue> issues = new ArrayList<>();
        Document slide = doc.getSlideDocument(slideNumber);
        if (slide == null) return issues;
        ThemeManager theme = ThemeManager.forDocument(doc);
        Map<String, String> clrMap = TextRunInspector.effectiveClrMap(slide, null, masterClrMap);

        Map<Integer, SlideShape> byId = new HashMap<>();
        for (SlideShape s : shapes) byId.put(s.getSpid(), s);

        // Shapes in z-order (document order), bottom first
        List<Element> ordered = new ArrayList<>();
        NodeList all = slide.getElementsByTagNameNS(P, "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            String n = e.getLocalName();
            if ("sp".equals(n) || "pic".equals(n) || "cxnSp".equals(n) || "graphicFrame".equals(n)) ordered.add(e);
        }

        SlideBackground background = SlideBackgroundResolver.resolve(doc, slideNumber, masterClrMap);

        for (int z = 0; z < ordered.size(); z++) {
            Element sp = ordered.get(z);
            if (!"sp".equals(sp.getLocalName())) continue;
            int spid = idOf(sp);
            SlideShape shape = byId.get(spid);
            List<TextRunInspector.RunReport> runs = TextRunInspector.inspect(doc, slideNumber, sp, masterClrMap);
            if (runs.stream().noneMatch(r -> !r.text().isBlank())) continue;

            // What's behind this shape's text
            Fill behind = fillOf(sp, theme, clrMap);
            String behindLabel = behind.source();
            if (behind.transparent()) {
                behind = null;
                ShapeGeometry g = shape != null ? shape.getGeometry() : null;
                for (int under = z - 1; under >= 0 && g != null; under--) {
                    Element other = ordered.get(under);
                    SlideShape o = byId.get(idOf(other));
                    if (o == null || o.getGeometry() == null || !containsCentre(o.getGeometry(), g)) continue;
                    Fill f = "pic".equals(other.getLocalName())
                        ? new Fill(List.of(), false, "a picture", null) : fillOf(other, theme, clrMap);
                    if (f.transparent()) continue;
                    behind = f;
                    behindLabel = "shape " + idOf(other) + (o.getName() != null ? " \"" + o.getName() + "\"" : "")
                        + " underneath";
                    break;
                }
                if (behind == null) {
                    behind = backgroundFill(background);
                    behindLabel = "slide background";
                }
            }
            if (behind.uncheckable() != null) {
                issues.add(new LayoutIssue(LayoutIssue.Type.CONTRAST_ADVISORY, new int[] {spid},
                    label(spid, shape) + ": contrast not checked -- the text sits on " + behind.uncheckable(),
                    "Render the slide (render_slide) and check the text is readable."));
                continue;
            }

            // Worst run against any colour behind it
            double worstShortfall = 0;
            String worst = null;
            String worstBg = null;
            double worstRatio = 0, worstNeed = 0;
            for (TextRunInspector.RunReport r : runs) {
                if (r.text().isBlank()) continue;
                String fg = r.color().value();
                if (fg == null || !fg.matches("#[0-9A-Fa-f]{6}")) continue;
                double need = isLarge(r) ? LARGE_MIN : NORMAL_MIN;
                for (String bg : behind.colours()) {
                    double ratio = ratio(fg, bg);
                    if (ratio < need && need - ratio > worstShortfall) {
                        worstShortfall = need - ratio;
                        worst = "\"" + trimmed(r.text()) + "\" " + fg.toUpperCase(Locale.ROOT)
                            + " [" + r.color().source() + "]";
                        worstBg = bg;
                        worstRatio = ratio;
                        worstNeed = need;
                    }
                }
            }
            if (worst == null) continue;
            String better = ratio("#FFFFFF", worstBg) >= ratio("#000000", worstBg) ? "#FFFFFF" : "#000000";
            boolean severe = worstRatio < NEARLY_INVISIBLE;
            issues.add(new LayoutIssue(severe ? LayoutIssue.Type.LOW_CONTRAST : LayoutIssue.Type.CONTRAST_ADVISORY,
                new int[] {spid},
                String.format(Locale.ROOT, "%s: %s on %s (%s) is %.1f:1; %s %.1f:1",
                    label(spid, shape), worst, worstBg.toUpperCase(Locale.ROOT), behindLabel, worstRatio,
                    severe ? "nearly invisible, needs" : "below the WCAG AA", worstNeed),
                severe
                    ? String.format(Locale.ROOT, "Change the text colour (set-font color) or what's behind it. %s "
                        + "on that background is %.1f:1.", better, ratio(better, worstBg))
                    : String.format(Locale.ROOT, "Readable but below WCAG AA; fine for large labels if intended. "
                        + "For full contrast use %s (%.1f:1).", better, ratio(better, worstBg))));
        }
        return issues;
    }

    // ========== Fills ==========

    private static Fill fillOf(Element shape, ThemeManager theme, Map<String, String> clrMap) {
        Element spPr = child(shape, P, "spPr");
        if (spPr != null) {
            for (Node n = spPr.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element e) || !A.equals(e.getNamespaceURI())) continue;
                switch (e.getLocalName()) {
                    case "noFill": return Fill.none();
                    case "solidFill": {
                        String c = TextRunInspector.colorOf(e, theme, clrMap);
                        return c != null ? new Fill(List.of(c), false, null, "shape fill") : Fill.none();
                    }
                    case "gradFill": {
                        List<String> stops = new ArrayList<>();
                        NodeList gs = e.getElementsByTagNameNS(A, "gs");
                        for (int i = 0; i < gs.getLength(); i++) {
                            String c = TextRunInspector.colorOf((Element) gs.item(i), theme, clrMap);
                            if (c != null) stops.add(c);
                        }
                        return stops.isEmpty() ? new Fill(List.of(), false, "a gradient fill", null)
                            : new Fill(stops, false, null, "shape gradient fill");
                    }
                    case "pattFill": {
                        Element fg = child(e, A, "fgClr");
                        String c = fg != null ? TextRunInspector.colorOf(fg, theme, clrMap) : null;
                        return c != null ? new Fill(List.of(c), false, null, "shape pattern fill")
                            : new Fill(List.of(), false, "a pattern fill", null);
                    }
                    case "blipFill": return new Fill(List.of(), false, "a picture fill", null);
                    default: break;
                }
            }
        }
        if (descendant(child(shape, P, "nvSpPr"), P, "ph") != null) {
            return Fill.none(); // placeholders without a fill of their own are see-through
        }
        Element fillRef = descendant(child(shape, P, "style"), A, "fillRef");
        if (fillRef != null && !"0".equals(fillRef.getAttribute("idx"))) {
            String c = TextRunInspector.colorOf(fillRef, theme, clrMap);
            if (c != null) return new Fill(List.of(c), false, null, "shape style fill");
        }
        return Fill.none();
    }

    private static Fill backgroundFill(SlideBackground bg) {
        if (bg == null) return new Fill(List.of("#FFFFFF"), false, null, "slide background (default white)");
        if (bg instanceof SlideBackground.Solid s) return new Fill(List.of(s.hex()), false, null, "slide background");
        if (bg instanceof SlideBackground.Gradient g) {
            List<String> stops = new ArrayList<>();
            for (SlideBackground.Gradient.GradientStop st : g.stops()) stops.add(st.hex());
            return new Fill(stops, false, null, "slide background gradient");
        }
        return new Fill(List.of(), false, "a picture background", null);
    }

    // ========== Contrast ==========

    /** WCAG 2 contrast ratio between two '#RRGGBB' colours (1..21). */
    static double ratio(String a, String b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        double r = channel(Integer.parseInt(h.substring(0, 2), 16));
        double g = channel(Integer.parseInt(h.substring(2, 4), 16));
        double b = channel(Integer.parseInt(h.substring(4, 6), 16));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static boolean isLarge(TextRunInspector.RunReport r) {
        double pt;
        try { pt = Double.parseDouble(r.size().value()); } catch (RuntimeException e) { return false; }
        return pt >= 18 || (pt >= 14 && "true".equals(r.bold().value()));
    }

    // ========== Helpers ==========

    private static boolean containsCentre(ShapeGeometry outer, ShapeGeometry inner) {
        long cx = inner.getX() + inner.getWidth() / 2, cy = inner.getY() + inner.getHeight() / 2;
        return cx >= outer.getX() && cx <= outer.getX() + outer.getWidth()
            && cy >= outer.getY() && cy <= outer.getY() + outer.getHeight();
    }

    private static String label(int spid, SlideShape shape) {
        return "SPID " + spid + (shape != null && shape.getName() != null ? " \"" + shape.getName() + "\"" : "");
    }

    private static String trimmed(String s) {
        String t = s.strip();
        return t.length() > 30 ? t.substring(0, 27) + "..." : t;
    }

    private static int idOf(Element shape) {
        NodeList c = shape.getElementsByTagNameNS(P, "cNvPr");
        if (c.getLength() == 0) return -1;
        try { return Integer.parseInt(((Element) c.item(0)).getAttribute("id")); } catch (NumberFormatException e) { return -1; }
    }

    private static Element child(Element parent, String ns, String local) {
        if (parent == null) return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && local.equals(e.getLocalName()) && ns.equals(e.getNamespaceURI())) return e;
        }
        return null;
    }

    private static Element descendant(Element root, String ns, String local) {
        if (root == null) return null;
        NodeList list = root.getElementsByTagNameNS(ns, local);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }
}
