package com.excudo.core.themes;

import com.excudo.core.utils.XMLFactoryProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a:fmtScheme from theme XML and resolves fill/line/effect styles
 * by index with phClr (placeholder color) substitution.
 *
 * OOXML index mapping:
 *   fillStyleLst / lnStyleLst / effectStyleLst: idx 1-3 (1-based)
 *   bgFillStyleLst: idx 1001-1003 (1000 + position)
 *   idx 0 = no style from theme matrix
 */
public class FmtSchemeResolver {

    private final List<Element> fillStyles = new ArrayList<>(3);
    private final List<Element> lnStyles = new ArrayList<>(3);
    private final List<Element> effectStyles = new ArrayList<>(3);
    private final List<Element> bgFillStyles = new ArrayList<>(3);
    private boolean parsed = false;

    // ========== PARSING ==========

    /**
     * Parse fmtScheme from theme document. Stores raw Element references
     * for each style entry (Document must stay alive for the resolver's lifetime).
     */
    public boolean parseFmtScheme(Document themeDocument) {
        try {
            XPath xpath = XMLFactoryProvider.createXPath();
            Element fmtScheme = (Element) xpath.evaluate("//a:fmtScheme",
                themeDocument, XPathConstants.NODE);
            if (fmtScheme == null) return false;

            collectChildren(fmtScheme, "a:fillStyleLst", fillStyles);
            collectChildren(fmtScheme, "a:lnStyleLst", lnStyles);
            collectChildren(fmtScheme, "a:effectStyleLst", effectStyles);
            collectChildren(fmtScheme, "a:bgFillStyleLst", bgFillStyles);

            parsed = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void collectChildren(Element parent, String listTagName, List<Element> target) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && listTagName.equals(el.getTagName())) {
                // Found the list element; collect its children
                for (Node child = el.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child instanceof Element childEl) {
                        target.add(childEl);
                    }
                }
                return;
            }
        }
    }

    public boolean isParsed() { return parsed; }

    // ========== FILL RESOLUTION ==========

    /**
     * Resolve a fill style by index with phClr substitution.
     * @param idx fillRef/@idx (1-3) or bgRef/@idx (1001-1003). 0 = no fill.
     * @param phColorHex resolved scheme color hex (e.g., "#569CD6") to substitute for phClr
     */
    public ResolvedFill resolveFillByIndex(int idx, String phColorHex) {
        if (idx == 0) return new ResolvedFill.NoFill();

        List<Element> list;
        int listIdx;

        if (idx >= 1001) {
            list = bgFillStyles;
            listIdx = idx - 1001;
        } else {
            list = fillStyles;
            listIdx = idx - 1;
        }

        if (listIdx < 0 || listIdx >= list.size()) {
            throw new IllegalArgumentException("Fill style index " + idx
                + " out of range (list has " + list.size() + " entries)");
        }

        return resolveFillElement(list.get(listIdx), phColorHex);
    }

    // ========== LINE RESOLUTION ==========

    /**
     * Resolve a line style by index with phClr substitution.
     * @param idx lnRef/@idx (1-3). 0 = no line style.
     * @param phColorHex resolved scheme color hex to substitute for phClr
     */
    public ResolvedLineStyle resolveLineByIndex(int idx, String phColorHex) {
        if (idx == 0) return ResolvedLineStyle.NONE;

        int listIdx = idx - 1;
        if (listIdx < 0 || listIdx >= lnStyles.size()) {
            throw new IllegalArgumentException("Line style index " + idx
                + " out of range (list has " + lnStyles.size() + " entries)");
        }

        Element ln = lnStyles.get(listIdx);

        // Width in EMUs
        double widthEmu = 12700; // default 1pt
        if (ln.hasAttribute("w")) {
            try { widthEmu = Double.parseDouble(ln.getAttribute("w")); }
            catch (NumberFormatException ignored) {}
        }

        // Dash style
        String dashStyle = "solid";
        Element prstDash = directChild(ln, "a:prstDash");
        if (prstDash != null && prstDash.hasAttribute("val")) {
            dashStyle = prstDash.getAttribute("val");
        }

        // Line color from solidFill
        Element solidFill = directChild(ln, "a:solidFill");
        if (solidFill != null) {
            ColorModifierUtils.ColorWithAlpha resolved = resolveColorFromFill(solidFill, phColorHex);
            return new ResolvedLineStyle(resolved.hex(), resolved.alpha(), widthEmu, dashStyle);
        }

        // No fill specified — use phClr directly
        return new ResolvedLineStyle(
            phColorHex.startsWith("#") ? phColorHex : "#" + phColorHex,
            1.0, widthEmu, dashStyle);
    }

    public record ResolvedLineStyle(String colorHex, double alpha, double widthEmu, String dashStyle) {
        public static final ResolvedLineStyle NONE = new ResolvedLineStyle("#000000", 1.0, 0, "solid");
    }

    // ========== INTERNAL: FILL ELEMENT RESOLUTION ==========

    private ResolvedFill resolveFillElement(Element fillElement, String phColorHex) {
        String tag = fillElement.getTagName();

        return switch (tag) {
            case "a:solidFill" -> resolveSolidFill(fillElement, phColorHex);
            case "a:gradFill" -> resolveGradientFill(fillElement, phColorHex);
            case "a:noFill" -> new ResolvedFill.NoFill();
            default -> throw new IllegalStateException(
                "Unknown fill element in fmtScheme: " + tag);
        };
    }

    private ResolvedFill.SolidFill resolveSolidFill(Element solidFillEl, String phColorHex) {
        ColorModifierUtils.ColorWithAlpha resolved = resolveColorFromFill(solidFillEl, phColorHex);
        return new ResolvedFill.SolidFill(resolved.hex(), resolved.alpha());
    }

    private ResolvedFill.GradientFill resolveGradientFill(Element gradFillEl, String phColorHex) {
        List<ResolvedFill.GradientStop> stops = new ArrayList<>();

        // Parse gradient stops from a:gsLst
        Element gsLst = directChild(gradFillEl, "a:gsLst");
        if (gsLst != null) {
            for (Node n = gsLst.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element gs && "a:gs".equals(gs.getTagName())) {
                    double pos = 0;
                    if (gs.hasAttribute("pos")) {
                        try { pos = Integer.parseInt(gs.getAttribute("pos")) / 100000.0; }
                        catch (NumberFormatException ignored) {}
                    }

                    ColorModifierUtils.ColorWithAlpha color = resolveColorFromFill(gs, phColorHex);
                    stops.add(new ResolvedFill.GradientStop(pos, color.hex(), color.alpha()));
                }
            }
        }

        // Determine gradient type and angle
        ResolvedFill.GradientType type = ResolvedFill.GradientType.LINEAR;
        double angleDegrees = 0;

        Element lin = directChild(gradFillEl, "a:lin");
        if (lin != null && lin.hasAttribute("ang")) {
            try { angleDegrees = Integer.parseInt(lin.getAttribute("ang")) / 60000.0; }
            catch (NumberFormatException ignored) {}
        }

        Element path = directChild(gradFillEl, "a:path");
        if (path != null) {
            type = ResolvedFill.GradientType.PATH;
        }

        return new ResolvedFill.GradientFill(stops, angleDegrees, type);
    }

    // ========== INTERNAL: COLOR RESOLUTION ==========

    /**
     * Resolve color from a fill-level element (solidFill or gradient stop).
     * Finds the color child (srgbClr or schemeClr), substitutes phClr,
     * and applies modifier children in document order.
     */
    private ColorModifierUtils.ColorWithAlpha resolveColorFromFill(Element fillEl, String phColorHex) {
        // Check for srgbClr (explicit hex)
        Element srgb = directChild(fillEl, "a:srgbClr");
        if (srgb != null && srgb.hasAttribute("val")) {
            String hex = "#" + srgb.getAttribute("val");
            List<ColorModifierUtils.ColorModifier> mods = extractModifiers(srgb);
            return mods.isEmpty()
                ? new ColorModifierUtils.ColorWithAlpha(hex)
                : ColorModifierUtils.applyModifiers(hex, mods);
        }

        // Check for schemeClr
        Element scheme = directChild(fillEl, "a:schemeClr");
        if (scheme != null && scheme.hasAttribute("val")) {
            String val = scheme.getAttribute("val");

            // phClr = substitute with caller's color
            String baseHex;
            if ("phClr".equals(val)) {
                baseHex = phColorHex.startsWith("#") ? phColorHex : "#" + phColorHex;
            } else {
                // Non-phClr scheme color — resolve through ThemeManager
                baseHex = "#" + ThemeManager.getThemeColor(val);
            }

            List<ColorModifierUtils.ColorModifier> mods = extractModifiers(scheme);
            return mods.isEmpty()
                ? new ColorModifierUtils.ColorWithAlpha(baseHex)
                : ColorModifierUtils.applyModifiers(baseHex, mods);
        }

        // No recognized color child
        return new ColorModifierUtils.ColorWithAlpha(
            phColorHex.startsWith("#") ? phColorHex : "#" + phColorHex);
    }

    /**
     * Extract color modifiers from a color element's children in document order.
     * Scans for a:tint, a:shade, a:satMod, a:alpha, a:lumMod, a:lumOff, etc.
     */
    private List<ColorModifierUtils.ColorModifier> extractModifiers(Element colorElement) {
        List<ColorModifierUtils.ColorModifier> mods = new ArrayList<>();

        for (Node n = colorElement.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element el)) continue;

            String tag = el.getTagName();
            if (!tag.startsWith("a:")) continue;
            String modName = tag.substring(2); // strip "a:" prefix

            if (el.hasAttribute("val")) {
                try {
                    int val = Integer.parseInt(el.getAttribute("val"));
                    mods.add(new ColorModifierUtils.ColorModifier(modName, val));
                } catch (NumberFormatException ignored) {}
            }
        }

        return mods;
    }

    // ========== UTILITY ==========

    private static Element directChild(Element parent, String tagName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && tagName.equals(el.getTagName())) {
                return el;
            }
        }
        return null;
    }
}
