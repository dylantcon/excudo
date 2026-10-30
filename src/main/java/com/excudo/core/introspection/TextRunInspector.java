package com.excudo.core.introspection;

import com.excudo.core.color.ColorTransforms;
import com.excudo.core.model.PPTXDocument;
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
 * Per-run text properties of one shape -- colour, font family, size, bold,
 * italic -- each both as written on the run and as resolved through the
 * inheritance chain, with where the resolved value came from.
 *
 * <p>Chain, weakest first (the same one the renderer's LstStyleResolver
 * walks, plus the shape style's fontRef):
 * <ul>
 *   <li>Placeholders: theme default -> master p:txStyles (title / body /
 *       other by placeholder type) -> the layout placeholder's a:lstStyle.</li>
 *   <li>Other shapes: theme default -> presentation p:defaultTextStyle ->
 *       the shape's p:style/a:fontRef (colour and major/minor font).</li>
 *   <li>Then for both: the shape's own a:lstStyle -> the paragraph's
 *       a:pPr/a:defRPr -> the run's a:rPr.</li>
 * </ul>
 * Scheme colours resolve through the colour map (the slide's override, else
 * the master's) and the theme, with modifiers (lumMod, lumOff, tint, ...)
 * applied.
 */
public final class TextRunInspector {

    private static final String P = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";

    /** One resolved property: the value, and the level of the chain it came from. */
    public record Resolved(String value, String source) {
        public static final Resolved NONE = new Resolved(null, null);
    }

    /** A run's properties, explicit (on the run itself) and resolved. */
    public record RunReport(int paragraph, int level, String text,
                            String explicitColor, Resolved color,
                            String explicitFont, Resolved font,
                            Double explicitSizePt, Resolved size,
                            Resolved bold, Resolved italic) {}

    private TextRunInspector() {}

    /**
     * Inspect every run of {@code shape} (a p:sp element on slide
     * {@code slideNumber}). Empty when the shape has no text body.
     */
    public static List<RunReport> inspect(PPTXDocument doc, int slideNumber, Element shape,
                                          Map<String, String> masterClrMap) {
        List<RunReport> out = new ArrayList<>();
        Element txBody = child(shape, P, "txBody");
        if (txBody == null) return out;

        Document slide = doc.getSlideDocument(slideNumber);
        Document layout = related(doc, "ppt/slides/_rels/slide" + slideNumber + ".xml.rels", "ppt/slides/", "/slideLayout");
        String layoutPart = relatedPart(doc, "ppt/slides/_rels/slide" + slideNumber + ".xml.rels", "ppt/slides/", "/slideLayout");
        Document master = layoutPart == null ? null
            : related(doc, relsOf(layoutPart), "ppt/slideLayouts/", "/slideMaster");
        Map<String, String> clrMap = effectiveClrMap(slide, layout, masterClrMap);
        ThemeManager theme = ThemeManager.forDocument(doc);

        Element ph = descendant(child(shape, P, "nvSpPr"), P, "ph");
        String phType = ph != null ? attr(ph, "type") : null;
        String phIdx = ph != null ? attr(ph, "idx") : null;
        boolean isPlaceholder = ph != null;
        boolean isTitle = "title".equals(phType) || "ctrTitle".equals(phType);

        // The style-bearing elements, weakest first, each with a label
        List<Element> chain = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (isPlaceholder) {
            if (master != null) {
                Element txStyles = descendant(master.getDocumentElement(), P, "txStyles");
                String styleName = isTitle ? "titleStyle"
                    : (phType == null || "body".equals(phType) || "obj".equals(phType)
                       || "subTitle".equals(phType) ? "bodyStyle" : "otherStyle");
                add(chain, labels, txStyles != null ? child(txStyles, P, styleName) : null, "master " + styleName);
            }
            if (layout != null) add(chain, labels, layoutPlaceholderLstStyle(layout, phType, phIdx), "layout placeholder");
        } else {
            Document pres = doc.getXmlPart("ppt/presentation.xml");
            add(chain, labels, pres != null ? descendant(pres.getDocumentElement(), P, "defaultTextStyle") : null,
                "presentation defaultTextStyle");
        }
        add(chain, labels, child(txBody, A, "lstStyle"), "shape lstStyle");

        // Non-placeholder shapes take colour and font from their style's fontRef
        Element fontRef = isPlaceholder ? null : descendant(child(shape, P, "style"), A, "fontRef");

        int paragraphIndex = 0;
        for (Element para : children(txBody, A, "p")) {
            Element pPr = child(para, A, "pPr");
            int level = pPr != null && pPr.hasAttribute("lvl") ? parseInt(pPr.getAttribute("lvl"), 0) : 0;
            String lvlTag = "lvl" + (level + 1) + "pPr";

            // defRPr per chain level for this paragraph level
            List<Element> defRPrs = new ArrayList<>();
            List<String> defLabels = new ArrayList<>();
            for (int i = 0; i < chain.size(); i++) {
                Element lvl = child(chain.get(i), A, lvlTag);
                Element def = lvl != null ? child(lvl, A, "defRPr") : null;
                if (def != null) { defRPrs.add(def); defLabels.add(labels.get(i)); }
            }
            Element paraDef = pPr != null ? child(pPr, A, "defRPr") : null;

            for (Element run : children(para, A, "r")) {
                Element rPr = child(run, A, "rPr");
                Element t = child(run, A, "t");
                String text = t != null ? t.getTextContent() : "";

                // Colour: theme default < chain < fontRef (shapes) < paragraph defRPr < run
                Resolved color = new Resolved(hashed(scheme(theme, clrMap, "tx1", null)), "theme text colour (tx1)");
                for (int i = 0; i < defRPrs.size(); i++) {
                    String c = fillColor(defRPrs.get(i), theme, clrMap);
                    if (c != null) color = new Resolved(c, defLabels.get(i));
                }
                if (fontRef != null) {
                    String c = colorOf(fontRef, theme, clrMap);
                    if (c != null) color = new Resolved(c, "shape style fontRef");
                }
                if (paraDef != null) {
                    String c = fillColor(paraDef, theme, clrMap);
                    if (c != null) color = new Resolved(c, "paragraph defRPr");
                }
                String explicitColor = rPr != null ? fillColor(rPr, theme, clrMap) : null;
                if (explicitColor != null) color = new Resolved(explicitColor, "run");

                // Font family: theme minor/major < chain < fontRef idx < paragraph < run
                Resolved font = new Resolved(themeFont(theme, isTitle ? "+mj-lt" : "+mn-lt"),
                    "theme " + (isTitle ? "major" : "minor") + " font");
                for (int i = 0; i < defRPrs.size(); i++) {
                    String f = latin(defRPrs.get(i), theme);
                    if (f != null) font = new Resolved(f, defLabels.get(i));
                }
                if (fontRef != null && fontRef.hasAttribute("idx")) {
                    String f = themeFont(theme, "major".equals(fontRef.getAttribute("idx")) ? "+mj-lt" : "+mn-lt");
                    if (f != null) font = new Resolved(f, "shape style fontRef");
                }
                if (paraDef != null && latin(paraDef, theme) != null) font = new Resolved(latin(paraDef, theme), "paragraph defRPr");
                String explicitFont = rPr != null ? latin(rPr, theme) : null;
                if (explicitFont != null) font = new Resolved(explicitFont, "run");

                // Size (hundredths of a point), bold, italic: chain < paragraph < run
                Resolved size = new Resolved("18", "default (18pt)");
                Resolved bold = new Resolved("false", "default");
                Resolved italic = new Resolved("false", "default");
                for (int i = 0; i < defRPrs.size(); i++) {
                    size = sizeFrom(defRPrs.get(i), defLabels.get(i), size);
                    bold = flag(defRPrs.get(i), "b", defLabels.get(i), bold);
                    italic = flag(defRPrs.get(i), "i", defLabels.get(i), italic);
                }
                if (paraDef != null) {
                    size = sizeFrom(paraDef, "paragraph defRPr", size);
                    bold = flag(paraDef, "b", "paragraph defRPr", bold);
                    italic = flag(paraDef, "i", "paragraph defRPr", italic);
                }
                Double explicitSize = null;
                if (rPr != null) {
                    if (rPr.hasAttribute("sz")) explicitSize = parseInt(rPr.getAttribute("sz"), 1800) / 100.0;
                    size = sizeFrom(rPr, "run", size);
                    bold = flag(rPr, "b", "run", bold);
                    italic = flag(rPr, "i", "run", italic);
                }

                out.add(new RunReport(paragraphIndex, level, text, explicitColor, color,
                    explicitFont, font, explicitSize, size, bold, italic));
            }
            paragraphIndex++;
        }
        return out;
    }

    // ========== Colours ==========

    /** Colour from an element's a:solidFill (or "none" for a:noFill), else null. */
    private static String fillColor(Element props, ThemeManager theme, Map<String, String> clrMap) {
        if (child(props, A, "noFill") != null) return "none (no fill: invisible)";
        Element solid = child(props, A, "solidFill");
        if (solid != null) return colorOf(solid, theme, clrMap);
        if (child(props, A, "gradFill") != null) return "gradient";
        return null;
    }

    /** Resolve the colour child (srgbClr / schemeClr / sysClr / prstClr) of a fill-like element. */
    static String colorOf(Element holder, ThemeManager theme, Map<String, String> clrMap) {
        for (Node n = holder.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element c) || !A.equals(c.getNamespaceURI())) continue;
            String base = switch (c.getLocalName()) {
                case "srgbClr" -> attr(c, "val");
                case "sysClr" -> c.hasAttribute("lastClr") ? attr(c, "lastClr") : null;
                case "schemeClr" -> scheme(theme, clrMap, attr(c, "val"), null);
                case "prstClr" -> "black".equals(attr(c, "val")) ? "000000" : "white".equals(attr(c, "val")) ? "FFFFFF" : null;
                default -> null;
            };
            if (base == null) continue;
            base = base.startsWith("#") ? base.substring(1) : base;
            List<ColorTransforms.Modifier> mods = ColorTransforms.modifiersFromDom(c);
            String hex = base;
            if (!mods.isEmpty()) {
                try { hex = ColorTransforms.apply(base, mods).hex(); } catch (RuntimeException ignored) { }
            }
            hex = hex.startsWith("#") ? hex.substring(1) : hex;
            return "#" + hex.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private static String hashed(String hex) {
        return hex == null ? null : hex.startsWith("#") ? hex : "#" + hex;
    }

    /** Scheme name (tx1, bg1, accent2, dk1...) through the colour map to the theme. */
    private static String scheme(ThemeManager theme, Map<String, String> clrMap, String name, String fallback) {
        if (name == null || !theme.isThemeLoaded()) return fallback;
        String mapped = clrMap.getOrDefault(name, name);
        try {
            return theme.getThemeColor(mapped).withHash().substring(1).toUpperCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static Map<String, String> effectiveClrMap(Document slide, Document layout, Map<String, String> master) {
        Map<String, String> map = new HashMap<>(master != null ? master : Map.of());
        if (map.isEmpty()) {
            // Office default mapping
            map.putAll(Map.of("bg1", "lt1", "tx1", "dk1", "bg2", "lt2", "tx2", "dk2"));
        }
        for (Document d : new Document[] { layout, slide }) {
            if (d == null) continue;
            Element override = descendant(d.getDocumentElement(), A, "overrideClrMapping");
            if (override == null) continue;
            var attrs = override.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) map.put(attrs.item(i).getLocalName() != null
                ? attrs.item(i).getLocalName() : attrs.item(i).getNodeName(), attrs.item(i).getNodeValue());
        }
        return map;
    }

    // ========== Fonts, sizes, flags ==========

    private static String latin(Element props, ThemeManager theme) {
        Element latin = child(props, A, "latin");
        if (latin == null || !latin.hasAttribute("typeface")) return null;
        String face = latin.getAttribute("typeface");
        return face.startsWith("+") ? themeFont(theme, face) : face;
    }

    private static String themeFont(ThemeManager theme, String ref) {
        if (!theme.isThemeLoaded()) return null;
        try { return theme.getThemeFont(ref); } catch (RuntimeException e) { return null; }
    }

    private static Resolved sizeFrom(Element props, String source, Resolved current) {
        if (!props.hasAttribute("sz")) return current;
        double pt = parseInt(props.getAttribute("sz"), 1800) / 100.0;
        return new Resolved(pt == Math.rint(pt) ? String.valueOf((int) pt) : String.valueOf(pt), source);
    }

    private static Resolved flag(Element props, String name, String source, Resolved current) {
        if (!props.hasAttribute(name)) return current;
        String v = props.getAttribute(name);
        return new Resolved(String.valueOf("1".equals(v) || "true".equals(v)), source);
    }

    // ========== Layout lookup ==========

    /** The layout placeholder's lstStyle: matched by type, then by idx (as the renderer does). */
    private static Element layoutPlaceholderLstStyle(Document layout, String phType, String phIdx) {
        Element byIdx = null;
        NodeList phs = layout.getElementsByTagNameNS(P, "ph");
        for (int i = 0; i < phs.getLength(); i++) {
            Element ph = (Element) phs.item(i);
            Element sp = ancestor(ph, P, "sp");
            if (sp == null) continue;
            Element lst = child(child(sp, P, "txBody"), A, "lstStyle");
            String type = attr(ph, "type");
            if (phType != null && phType.equals(type)) return lst;
            if (phType == null && type == null && phIdx != null && phIdx.equals(attr(ph, "idx")) && byIdx == null) byIdx = lst;
        }
        return byIdx;
    }

    private static Document related(PPTXDocument doc, String relsPart, String baseDir, String typeSuffix) {
        String part = relatedPart(doc, relsPart, baseDir, typeSuffix);
        return part != null ? doc.getXmlPart(part) : null;
    }

    private static String relatedPart(PPTXDocument doc, String relsPart, String baseDir, String typeSuffix) {
        Document rels = doc.getXmlPart(relsPart);
        if (rels == null) return null;
        NodeList list = rels.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < list.getLength(); i++) {
            Element r = (Element) list.item(i);
            if (!r.getAttribute("Type").endsWith(typeSuffix)) continue;
            String target = r.getAttribute("Target");
            if (target.startsWith("/")) return target.substring(1);
            String dir = baseDir;
            while (target.startsWith("../")) {
                target = target.substring(3);
                dir = dir.substring(0, dir.lastIndexOf('/', dir.length() - 2) + 1);
            }
            return dir + target;
        }
        return null;
    }

    private static String relsOf(String part) {
        int slash = part.lastIndexOf('/');
        return part.substring(0, slash + 1) + "_rels/" + part.substring(slash + 1) + ".rels";
    }

    // ========== DOM helpers ==========

    private static void add(List<Element> chain, List<String> labels, Element el, String label) {
        if (el != null) { chain.add(el); labels.add(label); }
    }

    private static Element child(Element parent, String ns, String local) {
        if (parent == null) return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && local.equals(e.getLocalName()) && ns.equals(e.getNamespaceURI())) return e;
        }
        return null;
    }

    private static List<Element> children(Element parent, String ns, String local) {
        List<Element> out = new ArrayList<>();
        if (parent == null) return out;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && local.equals(e.getLocalName()) && ns.equals(e.getNamespaceURI())) out.add(e);
        }
        return out;
    }

    private static Element descendant(Element root, String ns, String local) {
        if (root == null) return null;
        NodeList list = root.getElementsByTagNameNS(ns, local);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static Element ancestor(Element el, String ns, String local) {
        for (Node n = el.getParentNode(); n instanceof Element e; n = e.getParentNode()) {
            if (local.equals(e.getLocalName()) && ns.equals(e.getNamespaceURI())) return e;
        }
        return null;
    }

    private static String attr(Element el, String name) {
        return el != null && el.hasAttribute(name) ? el.getAttribute(name) : null;
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return fallback; }
    }
}
