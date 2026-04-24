package com.excudo.core.themes;

import com.excudo.core.utils.XMLFactoryProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * DOM-to-typed-model parser for {@code a:fmtScheme}. The only class in
 * the theme resolution pipeline that touches {@code org.w3c.dom}. Its
 * output, {@link FmtSchemeModel}, is what {@link FmtSchemeResolver}
 * consumes — the resolver is therefore pure computation over typed
 * data with zero DOM coupling.
 *
 * <p>Design rule enforced here: structural relationships that the
 * parser knows at parse time belong on the model, not on the resolver.
 * The resolver derives nothing structural — it only substitutes colors
 * and indexes into the lists.
 */
public final class FmtSchemeParser {

    private FmtSchemeParser() {}

    /**
     * Parse the {@code a:fmtScheme} element from a theme document into
     * a typed model. Returns empty when the document has no
     * {@code a:fmtScheme} element.
     */
    public static Optional<FmtSchemeModel> parse(Document themeDocument) {
        try {
            XPath xpath = XMLFactoryProvider.createXPath();
            Element fmtScheme = (Element) xpath.evaluate(
                "//a:fmtScheme", themeDocument, XPathConstants.NODE);
            if (fmtScheme == null) return Optional.empty();

            return Optional.of(new FmtSchemeModel(
                parseFillList(fmtScheme, "a:fillStyleLst"),
                parseFillList(fmtScheme, "a:bgFillStyleLst"),
                parseLineList(fmtScheme, "a:lnStyleLst"),
                parseEffectList(fmtScheme)
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ====================================================================
    // Fill list parsing
    // ====================================================================

    private static List<FmtSchemeModel.FillDef> parseFillList(Element fmtScheme, String listTag) {
        Element listEl = directChild(fmtScheme, listTag);
        if (listEl == null) return Collections.emptyList();

        List<FmtSchemeModel.FillDef> fills = new ArrayList<>(3);
        for (Node n = listEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el) {
                fills.add(parseFillElement(el));
            }
        }
        return fills;
    }

    private static FmtSchemeModel.FillDef parseFillElement(Element fillEl) {
        return switch (fillEl.getTagName()) {
            case "a:solidFill" -> new FmtSchemeModel.SolidFillDef(parseColorChild(fillEl));
            case "a:gradFill"  -> parseGradient(fillEl);
            case "a:noFill"    -> new FmtSchemeModel.NoFillDef();
            case "a:blipFill"  -> parseBlipFill(fillEl);
            case "a:pattFill"  -> parsePatternFill(fillEl);
            case "a:grpFill"   -> new FmtSchemeModel.GroupFillDef();
            default -> throw new IllegalStateException(
                "Unknown fill element in fmtScheme: " + fillEl.getTagName());
        };
    }

    private static FmtSchemeModel.GradientFillDef parseGradient(Element gradEl) {
        List<FmtSchemeModel.GradientStopDef> stops = new ArrayList<>();
        Element gsLst = directChild(gradEl, "a:gsLst");
        if (gsLst != null) {
            for (Node n = gsLst.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element gs && "a:gs".equals(gs.getTagName())) {
                    double pos = 0;
                    if (gs.hasAttribute("pos")) {
                        try { pos = Integer.parseInt(gs.getAttribute("pos")) / 100000.0; }
                        catch (NumberFormatException ignored) {}
                    }
                    stops.add(new FmtSchemeModel.GradientStopDef(pos, parseColorChild(gs)));
                }
            }
        }

        FmtSchemeModel.GradientKind kind = FmtSchemeModel.GradientKind.LINEAR;
        double angleDegrees = 0;

        Element lin = directChild(gradEl, "a:lin");
        if (lin != null && lin.hasAttribute("ang")) {
            try { angleDegrees = Integer.parseInt(lin.getAttribute("ang")) / 60000.0; }
            catch (NumberFormatException ignored) {}
        }

        if (directChild(gradEl, "a:path") != null) {
            kind = FmtSchemeModel.GradientKind.PATH;
        }

        return new FmtSchemeModel.GradientFillDef(stops, angleDegrees, kind);
    }

    private static FmtSchemeModel.BlipFillDef parseBlipFill(Element blipFillEl) {
        Element blip = directChild(blipFillEl, "a:blip");
        String embedRelId = null;
        String linkRelId = null;
        List<FmtSchemeModel.ColorSpec> duotone = List.of();
        if (blip != null) {
            if (blip.hasAttribute("r:embed")) embedRelId = blip.getAttribute("r:embed");
            if (blip.hasAttribute("r:link"))  linkRelId  = blip.getAttribute("r:link");

            // a:duotone carries two color children (shadow + highlight)
            // that remap the source image's luminance range.
            Element duotoneEl = directChild(blip, "a:duotone");
            if (duotoneEl != null) {
                List<FmtSchemeModel.ColorSpec> colors = new ArrayList<>(2);
                for (Node n = duotoneEl.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (!(n instanceof Element el)) continue;
                    String tag = el.getTagName();
                    if ("a:srgbClr".equals(tag) || "a:schemeClr".equals(tag)) {
                        // parseColorChild expects a PARENT whose child is
                        // srgbClr/schemeClr. Here the children ARE those,
                        // so wrap by calling a dedicated leaf parser.
                        colors.add(parseLeafColor(el));
                    }
                }
                if (!colors.isEmpty()) duotone = colors;
            }
        }
        return new FmtSchemeModel.BlipFillDef(embedRelId, linkRelId, duotone);
    }

    /** Parse a color element itself (not its child). Used for a:duotone
     *  whose children are direct color elements rather than wrappers. */
    private static FmtSchemeModel.ColorSpec parseLeafColor(Element colorEl) {
        String tag = colorEl.getTagName();
        List<ColorModifierUtils.ColorModifier> mods = extractModifiers(colorEl);
        if ("a:srgbClr".equals(tag) && colorEl.hasAttribute("val")) {
            return new FmtSchemeModel.SrgbColor("#" + colorEl.getAttribute("val"), mods);
        }
        if ("a:schemeClr".equals(tag) && colorEl.hasAttribute("val")) {
            String val = colorEl.getAttribute("val");
            return "phClr".equals(val)
                ? new FmtSchemeModel.PlaceholderColor(mods)
                : new FmtSchemeModel.SchemeColor(val, mods);
        }
        return new FmtSchemeModel.PlaceholderColor(List.of());
    }

    private static FmtSchemeModel.PatternFillDef parsePatternFill(Element pattFillEl) {
        String preset = pattFillEl.getAttribute("prst");
        FmtSchemeModel.ColorSpec fg = null;
        FmtSchemeModel.ColorSpec bg = null;
        Element fgEl = directChild(pattFillEl, "a:fgClr");
        Element bgEl = directChild(pattFillEl, "a:bgClr");
        if (fgEl != null) fg = parseColorChild(fgEl);
        if (bgEl != null) bg = parseColorChild(bgEl);
        return new FmtSchemeModel.PatternFillDef(preset, fg, bg);
    }

    // ====================================================================
    // Line list parsing
    // ====================================================================

    private static List<FmtSchemeModel.LineDef> parseLineList(Element fmtScheme, String listTag) {
        Element listEl = directChild(fmtScheme, listTag);
        if (listEl == null) return Collections.emptyList();

        List<FmtSchemeModel.LineDef> lines = new ArrayList<>(3);
        for (Node n = listEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element ln && "a:ln".equals(ln.getTagName())) {
                lines.add(parseLine(ln));
            }
        }
        return lines;
    }

    private static FmtSchemeModel.LineDef parseLine(Element lnEl) {
        double widthEmu = 12700; // 1pt
        if (lnEl.hasAttribute("w")) {
            try { widthEmu = Double.parseDouble(lnEl.getAttribute("w")); }
            catch (NumberFormatException ignored) {}
        }

        String dashStyle = "solid";
        Element prstDash = directChild(lnEl, "a:prstDash");
        if (prstDash != null && prstDash.hasAttribute("val")) {
            dashStyle = prstDash.getAttribute("val");
        }

        // Line fill is typically a:solidFill; accept any of the fill
        // variants so unusual themes don't crash. Default to NoFill when
        // a:ln has no fill child at all (the resolver then uses phClr).
        FmtSchemeModel.FillDef fill = new FmtSchemeModel.NoFillDef();
        for (Node n = lnEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && el.getTagName().startsWith("a:")
                    && el.getTagName().endsWith("Fill")) {
                fill = parseFillElement(el);
                break;
            }
        }

        return new FmtSchemeModel.LineDef(widthEmu, dashStyle, fill);
    }

    // ====================================================================
    // Effect list parsing (v1 stub — preserves list length for indexing)
    // ====================================================================

    private static List<FmtSchemeModel.EffectDef> parseEffectList(Element fmtScheme) {
        Element listEl = directChild(fmtScheme, "a:effectStyleLst");
        if (listEl == null) return Collections.emptyList();

        List<FmtSchemeModel.EffectDef> effects = new ArrayList<>(3);
        for (Node n = listEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && "a:effectStyle".equals(el.getTagName())) {
                // v1 collapses every effectStyle to an opaque marker.
                // When the resolver starts consuming effects, extend
                // EffectDef + this parse to carry the real data.
                effects.add(new FmtSchemeModel.EffectDef());
            }
        }
        return effects;
    }

    // ====================================================================
    // Color parsing
    // ====================================================================

    /** Parse the child color element inside a fill / gradient-stop /
     *  pattern-color element. Returns a typed ColorSpec with modifiers
     *  in document order. */
    private static FmtSchemeModel.ColorSpec parseColorChild(Element parent) {
        Element srgb = directChild(parent, "a:srgbClr");
        if (srgb != null && srgb.hasAttribute("val")) {
            return new FmtSchemeModel.SrgbColor(
                "#" + srgb.getAttribute("val"),
                extractModifiers(srgb));
        }

        Element scheme = directChild(parent, "a:schemeClr");
        if (scheme != null && scheme.hasAttribute("val")) {
            String val = scheme.getAttribute("val");
            List<ColorModifierUtils.ColorModifier> mods = extractModifiers(scheme);
            return "phClr".equals(val)
                ? new FmtSchemeModel.PlaceholderColor(mods)
                : new FmtSchemeModel.SchemeColor(val, mods);
        }

        // No recognized color child — treat as a pure phClr reference
        // with no modifiers. This preserves the old "fallback to phClr"
        // behavior the resolver used to emit when no color child existed.
        return new FmtSchemeModel.PlaceholderColor(Collections.emptyList());
    }

    /** Extract color modifiers from a color element's children in
     *  document order. Scans a:tint, a:shade, a:satMod, a:alpha,
     *  a:lumMod, a:lumOff, etc. — any {@code a:*} child with a
     *  {@code val} attribute. */
    private static List<ColorModifierUtils.ColorModifier> extractModifiers(Element colorEl) {
        List<ColorModifierUtils.ColorModifier> mods = new ArrayList<>();
        for (Node n = colorEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element el)) continue;
            String tag = el.getTagName();
            if (!tag.startsWith("a:")) continue;
            if (!el.hasAttribute("val")) continue;
            try {
                int val = Integer.parseInt(el.getAttribute("val"));
                mods.add(new ColorModifierUtils.ColorModifier(tag.substring(2), val));
            } catch (NumberFormatException ignored) {}
        }
        return mods;
    }

    // ====================================================================
    // Utility
    // ====================================================================

    private static Element directChild(Element parent, String tagName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && tagName.equals(el.getTagName())) {
                return el;
            }
        }
        return null;
    }
}
