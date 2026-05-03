package com.excudo.core.metrics.math;

import com.excudo.core.model.math.MathBody;
import com.excudo.core.model.math.MathElement;
import com.excudo.core.model.math.MathElement.BarPosition;
import com.excudo.core.model.math.MathElement.FractionType;
import com.excudo.core.model.math.MathElement.GroupCharPosition;
import com.excudo.core.model.math.MathElement.LimitLocation;
import com.excudo.core.model.math.MathElement.MathStyle;
import com.excudo.core.model.math.MathElement.NaryProperties;
import com.excudo.core.model.math.MathElement.ScriptVariant;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts an OOXML math DOM subtree (rooted at {@code <m:oMath>} or
 * {@code <m:oMathPara>}) into the typed {@link MathBody} AST.
 *
 * <p>This replaces the Tier-A flat-text fallback in
 * {@code TextBodyExtractor} with structure-preserving extraction:
 * fractions stay numerator-over-denominator, super/subscripts retain
 * their corner attachment, n-ary operators carry their limits, etc.
 * The layout engine consumes the AST plus
 * {@link MathTable} to compute placement.
 *
 * <p>Element coverage matches {@link MathElement}'s sealed permits
 * list (fractions, radicals, super/sub, n-ary, delimiters, bars,
 * accents, limits, functions, group chars). Unsupported OMML
 * elements ({@code m:m}, {@code m:eqArr}, {@code m:box},
 * {@code m:phant}, ...) collapse to a flat-text {@link MathElement.Run}
 * carrying the concatenated leaf text -- the formula stays readable
 * even before native layout support lands.
 */
public final class OmmlExtractor {

    private OmmlExtractor() {}

    /**
     * Extract a {@link MathBody} from an {@code <m:oMath>} or
     * {@code <m:oMathPara>} element. Returns null when the element
     * isn't a recognised math root.
     */
    public static MathBody extract(Element mathRoot) {
        if (mathRoot == null) return null;
        String local = localName(mathRoot);
        if ("oMathPara".equals(local)) {
            // Display-mode wrapper: descend to the inner m:oMath.
            for (Element kid : childElements(mathRoot)) {
                if ("oMath".equals(localName(kid))) {
                    return new MathBody(true, extractElements(kid));
                }
            }
            return new MathBody(true, List.of());
        }
        if ("oMath".equals(local)) {
            return new MathBody(false, extractElements(mathRoot));
        }
        return null;
    }

    /** Walk a math container's children and produce a list of typed
     *  elements. Skips property elements (m:rPr, m:fPr, etc) -- those
     *  are read by their parent's record builder. */
    private static List<MathElement> extractElements(Element parent) {
        List<MathElement> out = new ArrayList<>();
        for (Element kid : childElements(parent)) {
            MathElement el = extractElement(kid);
            if (el != null) out.add(el);
        }
        return out;
    }

    /** Dispatch on the local name of a math child. */
    private static MathElement extractElement(Element el) {
        return switch (localName(el)) {
            case "r"        -> extractRun(el);
            case "f"        -> extractFraction(el);
            case "rad"      -> extractRadical(el);
            case "sSup"     -> extractSuperscript(el);
            case "sSub"     -> extractSubscript(el);
            case "sSubSup"  -> extractSubSuperscript(el);
            case "sPre"     -> extractPrescript(el);
            case "nary"     -> extractNary(el);
            case "d"        -> extractDelimiter(el);
            case "bar"      -> extractBar(el);
            case "acc"      -> extractAccent(el);
            case "limLow"   -> extractLimitLower(el);
            case "limUpp"   -> extractLimitUpper(el);
            case "func"     -> extractFunction(el);
            case "groupChr" -> extractGroupChar(el);
            // Fall back to flat-text for unsupported elements (m:m,
            // m:eqArr, m:box, m:phant, ...). Walk the subtree
            // collecting m:t leaves so the formula remains readable.
            case "m", "eqArr", "box", "phant", "borderBox" -> flattenAsRun(el);
            default -> null; // m:rPr / m:fPr / m:radPr etc. -- ignore as siblings
        };
    }

    // ============================================================
    // Per-element extractors
    // ============================================================

    private static MathElement.Run extractRun(Element r) {
        StringBuilder text = new StringBuilder();
        for (Element kid : childElements(r)) {
            if ("t".equals(localName(kid))) {
                String t = kid.getTextContent();
                if (t != null) text.append(t);
            }
        }
        return new MathElement.Run(text.toString(), readMathStyle(r));
    }

    private static MathElement.Fraction extractFraction(Element f) {
        Element fPr = firstChild(f, "fPr");
        FractionType type = readFractionType(fPr);
        MathBody num = bodyOf(firstChild(f, "num"));
        MathBody den = bodyOf(firstChild(f, "den"));
        return new MathElement.Fraction(num, den, type);
    }

    private static MathElement.Radical extractRadical(Element rad) {
        // m:radPr/m:degHide tells whether to render the degree slot at
        // all. When degHide is true (or there's no <m:deg> child), this
        // is a plain sqrt.
        Element radPr = firstChild(rad, "radPr");
        boolean hideDegree = radPr != null && hasFlag(radPr, "degHide");
        Element baseE = firstChild(rad, "e");
        Element degE = firstChild(rad, "deg");
        MathBody base = bodyOf(baseE);
        MathBody degree = (hideDegree || degE == null || isEmptyBody(degE))
            ? null
            : bodyOf(degE);
        return new MathElement.Radical(base, degree);
    }

    private static MathElement.Superscript extractSuperscript(Element sSup) {
        return new MathElement.Superscript(
            bodyOf(firstChild(sSup, "e")),
            bodyOf(firstChild(sSup, "sup")));
    }

    private static MathElement.Subscript extractSubscript(Element sSub) {
        return new MathElement.Subscript(
            bodyOf(firstChild(sSub, "e")),
            bodyOf(firstChild(sSub, "sub")));
    }

    private static MathElement.SubSuperscript extractSubSuperscript(Element sSubSup) {
        return new MathElement.SubSuperscript(
            bodyOf(firstChild(sSubSup, "e")),
            bodyOf(firstChild(sSubSup, "sub")),
            bodyOf(firstChild(sSubSup, "sup")));
    }

    private static MathElement.Prescript extractPrescript(Element sPre) {
        return new MathElement.Prescript(
            bodyOf(firstChild(sPre, "sub")),
            bodyOf(firstChild(sPre, "sup")),
            bodyOf(firstChild(sPre, "e")));
    }

    private static MathElement.Nary extractNary(Element nary) {
        Element naryPr = firstChild(nary, "naryPr");
        String chr = readPropertyVal(naryPr, "chr", "∑");
        boolean grow = readBoolFlag(naryPr, "grow", false);
        boolean hideSub = hasFlag(naryPr, "subHide");
        boolean hideSup = hasFlag(naryPr, "supHide");
        LimitLocation limitLoc = "undOvr".equals(readPropertyVal(naryPr, "limLoc", "subSup"))
            ? LimitLocation.UNDER_OVER : LimitLocation.SUB_SUP;
        NaryProperties props = new NaryProperties(limitLoc, grow, hideSub, hideSup);

        MathBody sub = hideSub ? MathBody.empty() : bodyOf(firstChild(nary, "sub"));
        MathBody sup = hideSup ? MathBody.empty() : bodyOf(firstChild(nary, "sup"));
        MathBody base = bodyOf(firstChild(nary, "e"));
        return new MathElement.Nary(chr, sub, sup, base, props);
    }

    private static MathElement.Delimiter extractDelimiter(Element d) {
        Element dPr = firstChild(d, "dPr");
        String beg = readPropertyVal(dPr, "begChr", "(");
        String end = readPropertyVal(dPr, "endChr", ")");
        String sep = readPropertyVal(dPr, "sepChr", "|");
        boolean grow = readBoolFlag(dPr, "grow", true);

        // m:d carries one or more m:e children (multiple when used as
        // a separator-list, e.g. (a, b, c)).
        List<MathBody> bodies = new ArrayList<>();
        for (Element kid : childElements(d)) {
            if ("e".equals(localName(kid))) bodies.add(bodyOf(kid));
        }
        return new MathElement.Delimiter(beg, end, sep, grow, bodies);
    }

    private static MathElement.Bar extractBar(Element bar) {
        Element barPr = firstChild(bar, "barPr");
        BarPosition pos = "bot".equals(readPropertyVal(barPr, "pos", "top"))
            ? BarPosition.BOTTOM : BarPosition.TOP;
        return new MathElement.Bar(bodyOf(firstChild(bar, "e")), pos);
    }

    private static MathElement.Accent extractAccent(Element acc) {
        Element accPr = firstChild(acc, "accPr");
        String chr = readPropertyVal(accPr, "chr", "̂"); // combining circumflex default
        return new MathElement.Accent(bodyOf(firstChild(acc, "e")), chr);
    }

    private static MathElement.LimitLower extractLimitLower(Element limLow) {
        return new MathElement.LimitLower(
            bodyOf(firstChild(limLow, "e")),
            bodyOf(firstChild(limLow, "lim")));
    }

    private static MathElement.LimitUpper extractLimitUpper(Element limUpp) {
        return new MathElement.LimitUpper(
            bodyOf(firstChild(limUpp, "e")),
            bodyOf(firstChild(limUpp, "lim")));
    }

    private static MathElement.Function extractFunction(Element func) {
        return new MathElement.Function(
            bodyOf(firstChild(func, "fName")),
            bodyOf(firstChild(func, "e")));
    }

    private static MathElement.GroupChar extractGroupChar(Element gc) {
        Element gcPr = firstChild(gc, "groupChrPr");
        String chr = readPropertyVal(gcPr, "chr", "⏟");
        GroupCharPosition pos = "top".equals(readPropertyVal(gcPr, "pos", "bot"))
            ? GroupCharPosition.TOP : GroupCharPosition.BOTTOM;
        return new MathElement.GroupChar(bodyOf(firstChild(gc, "e")), chr, pos);
    }

    /** Fallback for OMML elements we haven't modelled yet: walk the
     *  whole subtree picking up every {@code m:t} text leaf and emit
     *  a flat-text {@link MathElement.Run}. Keeps formulas readable
     *  before full layout support lands. */
    private static MathElement.Run flattenAsRun(Element el) {
        StringBuilder sb = new StringBuilder();
        collectMathText(el, sb);
        return new MathElement.Run(sb.toString(), MathStyle.DEFAULT);
    }

    private static void collectMathText(Node node, StringBuilder sb) {
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element kid) {
                if ("t".equals(localName(kid))) {
                    String t = kid.getTextContent();
                    if (t != null) sb.append(t);
                } else {
                    collectMathText(kid, sb);
                }
            }
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** Build a MathBody from a slot-wrapper element (m:e, m:num,
     *  m:den, m:sub, m:sup, m:lim, m:deg, m:fName). Null-safe;
     *  returns {@code MathBody.empty()} when the wrapper is missing
     *  (treats absence as "no content," matching OMML's optional
     *  slot semantics). */
    private static MathBody bodyOf(Element wrapper) {
        if (wrapper == null) return MathBody.empty();
        return new MathBody(false, extractElements(wrapper));
    }

    /** Read math-run styling from an {@code <m:r>}'s {@code <m:rPr>}.
     *  Math runs default to italic; an {@code <m:nor/>} or
     *  {@code <m:sty m:val="p"/>} opts out. */
    private static MathStyle readMathStyle(Element r) {
        Element rPr = firstChild(r, "rPr");
        if (rPr == null) return MathStyle.DEFAULT;

        boolean italic = true;
        boolean bold = false;
        ScriptVariant variant = ScriptVariant.ROMAN;

        // <m:nor/> forces normal (upright) text.
        if (firstChild(rPr, "nor") != null) {
            italic = false;
        }
        // <m:sty m:val="p|b|i|bi"/> chooses style explicitly.
        Element sty = firstChild(rPr, "sty");
        if (sty != null) {
            String val = sty.getAttributeNS(MATH_NS, "val");
            if (val.isEmpty()) val = sty.getAttribute("val");
            switch (val) {
                case "p"  -> { italic = false; bold = false; }
                case "b"  -> { italic = false; bold = true; }
                case "i"  -> { italic = true;  bold = false; }
                case "bi" -> { italic = true;  bold = true; }
                default   -> { /* leave as-is */ }
            }
        }
        // <m:scr m:val="roman|script|fraktur|double-struck|sans-serif|monospace"/>
        Element scr = firstChild(rPr, "scr");
        if (scr != null) {
            String val = scr.getAttributeNS(MATH_NS, "val");
            if (val.isEmpty()) val = scr.getAttribute("val");
            variant = switch (val) {
                case "script"        -> ScriptVariant.SCRIPT;
                case "fraktur"       -> ScriptVariant.FRAKTUR;
                case "double-struck" -> ScriptVariant.DOUBLE_STRUCK;
                case "sans-serif"    -> ScriptVariant.SANS_SERIF;
                case "monospace"     -> ScriptVariant.MONOSPACE;
                default              -> ScriptVariant.ROMAN;
            };
        }
        return new MathStyle(italic, bold, variant);
    }

    private static FractionType readFractionType(Element fPr) {
        if (fPr == null) return FractionType.BAR;
        Element type = firstChild(fPr, "type");
        if (type == null) return FractionType.BAR;
        String val = type.getAttributeNS(MATH_NS, "val");
        if (val.isEmpty()) val = type.getAttribute("val");
        return switch (val) {
            case "skw"   -> FractionType.SKEWED;
            case "lin"   -> FractionType.LINEAR;
            case "noBar" -> FractionType.NO_BAR;
            default      -> FractionType.BAR;
        };
    }

    /** Read a value attribute off a {@code <m:foo m:val="..."/>}
     *  inside the supplied properties container. Falls back to the
     *  supplied default when missing. */
    private static String readPropertyVal(Element propContainer, String childName, String defaultVal) {
        if (propContainer == null) return defaultVal;
        Element p = firstChild(propContainer, childName);
        if (p == null) return defaultVal;
        String val = p.getAttributeNS(MATH_NS, "val");
        if (val.isEmpty()) val = p.getAttribute("val");
        return val.isEmpty() ? defaultVal : val;
    }

    /** Read an OMML boolean property: {@code <m:foo m:val="1"/>} =
     *  true, {@code <m:foo m:val="0"/>} = false, missing element =
     *  default. (OOXML uses 1/0 for booleans on math properties.) */
    private static boolean readBoolFlag(Element propContainer, String childName, boolean defaultVal) {
        if (propContainer == null) return defaultVal;
        Element p = firstChild(propContainer, childName);
        if (p == null) return defaultVal;
        String val = p.getAttributeNS(MATH_NS, "val");
        if (val.isEmpty()) val = p.getAttribute("val");
        if (val.isEmpty()) return true; // bare presence = true per OMML convention
        return "1".equals(val) || "true".equalsIgnoreCase(val);
    }

    /** Bare-presence flag: the element is the flag (e.g.
     *  {@code <m:degHide/>} present means hide the degree). */
    private static boolean hasFlag(Element propContainer, String childName) {
        return propContainer != null && firstChild(propContainer, childName) != null;
    }

    /** True iff a slot wrapper is empty -- used to detect plain
     *  square roots that store an empty {@code <m:deg/>} rather than
     *  omitting it. */
    private static boolean isEmptyBody(Element wrapper) {
        if (wrapper == null) return true;
        for (Element kid : childElements(wrapper)) {
            if ("r".equals(localName(kid))) {
                // any non-whitespace m:t makes the slot non-empty
                for (Element rkid : childElements(kid)) {
                    if ("t".equals(localName(rkid))) {
                        String t = rkid.getTextContent();
                        if (t != null && !t.isBlank()) return false;
                    }
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private static List<Element> childElements(Element parent) {
        List<Element> out = new ArrayList<>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element e) out.add(e);
        }
        return out;
    }

    private static Element firstChild(Element parent, String localName) {
        if (parent == null) return null;
        for (Element kid : childElements(parent)) {
            if (localName.equals(localName(kid))) return kid;
        }
        return null;
    }

    private static String localName(Element el) {
        String local = el.getLocalName();
        if (local != null) return local;
        // Fallback when the document was parsed without namespace
        // awareness (older callers): strip the prefix.
        String tag = el.getTagName();
        int colon = tag.indexOf(':');
        return colon < 0 ? tag : tag.substring(colon + 1);
    }

    /** OMML namespace URI -- the {@code val} attribute on properties
     *  is qualified ({@code m:val}) so we look it up by namespace
     *  rather than relying on the prefix being literally "m:". */
    private static final String MATH_NS =
        "http://schemas.openxmlformats.org/officeDocument/2006/math";
}
