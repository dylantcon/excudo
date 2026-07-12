package com.excudo.view.rendering.text;

import com.excudo.core.metrics.TextStyleSource;
import com.excudo.core.model.TextColor;
import com.excudo.core.themes.TextLevelStyle;
import com.excudo.view.rendering.SlideRenderContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the effective {@link TextStyleSource} for one shape's text body
 * by walking the OOXML list-style inheritance chain (weakest first):
 *
 * <ul>
 *   <li><b>Placeholders</b>: slide master {@code p:txStyles} (title /
 *       body / other, selected by placeholder type) &rarr; slide layout
 *       placeholder {@code a:lstStyle} (matched by ph type, then idx)
 *       &rarr; the shape's own {@code p:txBody/a:lstStyle}.</li>
 *   <li><b>Plain text boxes</b>: presentation
 *       {@code p:defaultTextStyle} &rarr; the shape's own
 *       {@code a:lstStyle}. Master txStyles do NOT apply to
 *       non-placeholder shapes — leaking them gave text boxes phantom
 *       margins and bullets, while ignoring defaultTextStyle lost the
 *       per-level indents PowerPoint gives multi-level lists in plain
 *       text boxes.</li>
 * </ul>
 *
 * Run rPr and paragraph pPr overrides stay in the model and are applied
 * on top by {@code TextMeasurer} / {@code TextPainter}. Theme-symbolic
 * typefaces ({@code +mn-lt} / {@code +mj-lt}) resolve against the theme
 * fonts supplied at construction.
 */
public final class LstStyleResolver implements TextStyleSource {

    private final Element[] chain;   // weakest -> strongest, nullable entries
    private final SlideRenderContext slideCtx; // theme fallback for placeholders, may be null
    private final boolean isPlaceholder;
    private final boolean isTitle;
    private final String majorFont;
    private final String minorFont;
    private final Map<Integer, LevelStyle> cache = new HashMap<>();

    private LstStyleResolver(Element[] chain, SlideRenderContext slideCtx,
                             boolean isPlaceholder, boolean isTitle,
                             String majorFont, String minorFont) {
        this.chain = chain;
        this.slideCtx = slideCtx;
        this.isPlaceholder = isPlaceholder;
        this.isTitle = isTitle;
        this.majorFont = majorFont;
        this.minorFont = minorFont;
    }

    /**
     * Resolve the style chain for a shape rendered under {@code slideCtx}.
     *
     * @param slideCtx     render context (layout / master / presentation DOMs)
     * @param phType       placeholder type, or null/empty for plain shapes
     * @param phIdx        placeholder idx, or null/empty
     * @param shapeElement the shape's p:sp element (for its own txBody lstStyle); may be null
     */
    public static TextStyleSource forShape(SlideRenderContext slideCtx, String phType,
                                           String phIdx, Element shapeElement) {
        String type = phType != null && !phType.isEmpty() ? phType : null;
        String idx = phIdx != null && !phIdx.isEmpty() ? phIdx : null;
        boolean isPlaceholder = type != null || idx != null;

        Element base = null;
        Element layoutLst = null;
        if (slideCtx != null) {
            if (isPlaceholder) {
                Document master = slideCtx.getMasterDom();
                if (master != null) {
                    base = masterStyleElement(master, type);
                }
                Document layout = slideCtx.getLayoutDom();
                if (layout != null) {
                    layoutLst = placeholderLstStyle(layout, type, idx);
                }
            } else {
                Document pres = slideCtx.getPresentationDom();
                if (pres != null) {
                    base = firstDescendant(pres.getDocumentElement(), "defaultTextStyle");
                }
            }
        }
        Element shapeLst = shapeElement != null ? shapeTxBodyLstStyle(shapeElement) : null;

        return new LstStyleResolver(new Element[]{ base, layoutLst, shapeLst }, slideCtx,
            isPlaceholder, type != null && (type.equals("title") || type.equals("ctrTitle")),
            slideCtx != null ? slideCtx.getMajorFont() : null,
            slideCtx != null ? slideCtx.getMinorFont() : null);
    }

    /**
     * Low-level factory for tests and non-render callers: supply the raw
     * style-bearing elements (weakest first). Each may be null; each is
     * either a {@code p:titleStyle}/{@code p:bodyStyle}/{@code p:otherStyle}/
     * {@code p:defaultTextStyle}/{@code a:lstStyle} element containing
     * {@code a:lvlNpPr} children.
     */
    public static LstStyleResolver ofChain(String majorFont, String minorFont,
                                           Element... weakestFirst) {
        return new LstStyleResolver(weakestFirst, null, false, false, majorFont, minorFont);
    }

    @Override
    public LevelStyle levelStyle(int level) {
        return cache.computeIfAbsent(level, this::resolveLevel);
    }

    private LevelStyle resolveLevel(int level) {
        LevelStyle merged = themeFallback(level);
        for (Element source : chain) {
            if (source == null) continue;
            Element defPPr = firstDescendantLocal(source, "defPPr");
            if (defPPr != null) {
                merged = merged.overlaidBy(parseLevelElement(defPPr));
            }
            Element lvl = firstDescendantLocal(source, "lvl" + (level + 1) + "pPr");
            if (lvl != null) {
                merged = merged.overlaidBy(parseLevelElement(lvl));
            }
        }
        return merged;
    }

    /**
     * When a placeholder has no master DOM to parse (bundled-theme
     * rendering), fall back to the theme's parsed {@link TextLevelStyle}.
     */
    private LevelStyle themeFallback(int level) {
        if (!isPlaceholder || slideCtx == null) return LevelStyle.EMPTY;
        Element masterEl = chain.length > 0 ? chain[0] : null;
        if (masterEl != null) return LevelStyle.EMPTY; // master DOM available: parsed directly
        TextLevelStyle ts = isTitle ? slideCtx.getTitleStyle(level) : slideCtx.getBodyStyle(level);
        if (ts == null) return LevelStyle.EMPTY;
        TextColor color = ts.getColorRef() == null ? null
            : ts.isSchemeColor() ? TextColor.scheme(ts.getColorRef())
            : TextColor.hex(ts.getHexColor());
        return new LevelStyle(ts.getFontSize(), ts.isBold() ? Boolean.TRUE : null, null,
            isTitle ? majorFont : minorFont, color,
            ts.getMarginLeft(), null, ts.getIndent(), ts.getAlignment(),
            ts.getLineSpacing(), ts.getSpaceBefore() > 0 ? ts.getSpaceBefore() : null, null,
            null, ts.hasBullet() ? ts.getBulletChar() : null,
            ts.hasBullet() ? ts.getBulletFont() : null, null, null, null);
    }

    // ========== DOM PARSING ==========

    /**
     * Parse one {@code a:lvlNpPr} (or {@code a:defPPr}) element into a
     * partial {@link LevelStyle}. Only what the element states is
     * non-null, so overlays merge correctly.
     */
    private LevelStyle parseLevelElement(Element lvl) {
        Integer marL = intAttr(lvl, "marL");
        Integer marR = intAttr(lvl, "marR");
        Integer indent = intAttr(lvl, "indent");
        String algn = strAttr(lvl, "algn");

        Integer lnSpcPct = null;
        Element lnSpc = firstChildLocal(lvl, "lnSpc");
        if (lnSpc != null) {
            Element spcPct = firstChildLocal(lnSpc, "spcPct");
            if (spcPct != null) lnSpcPct = intAttr(spcPct, "val");
        }
        Integer spcBef = spcPtsVal(lvl, "spcBef");
        Integer spcAft = spcPtsVal(lvl, "spcAft");

        Boolean buNone = firstChildLocal(lvl, "buNone") != null ? Boolean.TRUE : null;
        String buChar = null;
        Element buCharEl = firstChildLocal(lvl, "buChar");
        if (buCharEl != null) buChar = strAttr(buCharEl, "char");
        String autonum = null;
        Element buAutoNum = firstChildLocal(lvl, "buAutoNum");
        if (buAutoNum != null) autonum = strAttr(buAutoNum, "type");
        String buFont = null;
        Element buFontEl = firstChildLocal(lvl, "buFont");
        if (buFontEl != null) buFont = resolveTypeface(strAttr(buFontEl, "typeface"));
        Integer buSzPct = null;
        Element buSzPctEl = firstChildLocal(lvl, "buSzPct");
        if (buSzPctEl != null) buSzPct = intAttr(buSzPctEl, "val");
        TextColor buClr = null;
        Element buClrEl = firstChildLocal(lvl, "buClr");
        if (buClrEl != null) buClr = parseColorChoice(buClrEl);

        Integer sz = null;
        Boolean bold = null;
        Boolean italic = null;
        String family = null;
        TextColor color = null;
        Element defRPr = firstChildLocal(lvl, "defRPr");
        if (defRPr != null) {
            sz = intAttr(defRPr, "sz");
            String b = strAttr(defRPr, "b");
            if (b != null) bold = "1".equals(b) || "true".equalsIgnoreCase(b);
            String i = strAttr(defRPr, "i");
            if (i != null) italic = "1".equals(i) || "true".equalsIgnoreCase(i);
            Element latin = firstChildLocal(defRPr, "latin");
            if (latin != null) family = resolveTypeface(strAttr(latin, "typeface"));
            Element solidFill = firstChildLocal(defRPr, "solidFill");
            if (solidFill != null) color = parseColorChoice(solidFill);
        }

        return new LevelStyle(sz, bold, italic, family, color, marL, marR, indent, algn,
            lnSpcPct, spcBef, spcAft, buNone, buChar, buFont, autonum, buSzPct, buClr);
    }

    /** Map theme-symbolic typefaces to concrete theme font families. */
    private String resolveTypeface(String typeface) {
        if (typeface == null) return null;
        if (typeface.startsWith("+mj")) return majorFont != null ? majorFont : null;
        if (typeface.startsWith("+mn")) return minorFont != null ? minorFont : null;
        return typeface;
    }

    private static TextColor parseColorChoice(Element parent) {
        Element srgb = firstChildLocal(parent, "srgbClr");
        if (srgb != null) {
            String val = strAttr(srgb, "val");
            if (val != null) return TextColor.hex(val);
        }
        Element scheme = firstChildLocal(parent, "schemeClr");
        if (scheme != null) {
            String val = strAttr(scheme, "val");
            if (val != null) return TextColor.scheme(val);
        }
        return null;
    }

    private static Integer spcPtsVal(Element lvl, String tag) {
        Element el = firstChildLocal(lvl, tag);
        if (el == null) return null;
        Element spcPts = firstChildLocal(el, "spcPts");
        return spcPts != null ? intAttr(spcPts, "val") : null;
    }

    // ========== SOURCE ELEMENT LOOKUP ==========

    /** Master txStyles element for a placeholder type. */
    private static Element masterStyleElement(Document masterDom, String phType) {
        Element txStyles = firstDescendant(masterDom.getDocumentElement(), "txStyles");
        if (txStyles == null) return null;
        String key;
        if ("title".equals(phType) || "ctrTitle".equals(phType)) {
            key = "titleStyle";
        } else if ("dt".equals(phType) || "ftr".equals(phType) || "sldNum".equals(phType)) {
            key = "otherStyle";
        } else {
            key = "bodyStyle";
        }
        return firstChildLocal(txStyles, key);
    }

    /**
     * Find the layout placeholder matching phType/phIdx (type match beats
     * idx match, mirroring bodyPr anchor inheritance) and return its
     * txBody lstStyle.
     */
    private static Element placeholderLstStyle(Document layoutDom, String phType, String phIdx) {
        NodeList shapes = layoutDom.getElementsByTagName("p:sp");
        Element idxMatch = null;
        for (int i = 0; i < shapes.getLength(); i++) {
            Element sp = (Element) shapes.item(i);
            Element ph = firstDescendant(sp, "ph");
            if (ph == null) continue;
            String shapePhType = ph.getAttribute("type");
            String shapePhIdx = ph.getAttribute("idx");
            boolean typeHit = phType != null && phType.equals(shapePhType);
            boolean idxHit = phIdx != null && phIdx.equals(shapePhIdx);
            if (!typeHit && !idxHit) continue;
            Element lst = shapeTxBodyLstStyle(sp);
            if (lst == null) continue;
            if (typeHit) return lst;
            if (idxMatch == null) idxMatch = lst;
        }
        return idxMatch;
    }

    private static Element shapeTxBodyLstStyle(Element shapeElement) {
        Element txBody = firstDescendant(shapeElement, "txBody");
        if (txBody == null) return null;
        return firstChildLocal(txBody, "lstStyle");
    }

    // ========== NAMESPACE-TOLERANT DOM HELPERS ==========

    private static Integer intAttr(Element el, String name) {
        String v = el.getAttribute(name);
        if (v.isEmpty()) return null;
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { return null; }
    }

    private static String strAttr(Element el, String name) {
        String v = el.getAttribute(name);
        return v.isEmpty() ? null : v;
    }

    private static Element firstChildLocal(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && localName.equals(localName(el))) {
                return el;
            }
        }
        return null;
    }

    private static Element firstDescendantLocal(Element parent, String localName) {
        return firstChildLocal(parent, localName);
    }

    private static Element firstDescendant(Element parent, String localName) {
        NodeList all = parent.getElementsByTagName("*");
        if (localName.equals(localName(parent))) return parent;
        for (int i = 0; i < all.getLength(); i++) {
            Element el = (Element) all.item(i);
            if (localName.equals(localName(el))) return el;
        }
        return null;
    }

    private static String localName(Element el) {
        String local = el.getLocalName();
        if (local != null) return local;
        String tag = el.getTagName();
        int colon = tag.indexOf(':');
        return colon < 0 ? tag : tag.substring(colon + 1);
    }
}
