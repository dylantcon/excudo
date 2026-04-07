package com.excudo.xml.writers.themes;

import com.excudo.core.utils.XMLConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Generates standard Office fmtScheme DOM elements.
 * The fmtScheme (fill styles, line styles, effect styles, background fills) is largely
 * boilerplate that references schemeClr val="phClr" (placeholder color). This is the same
 * across PowerPoint's built-in themes with only minor variation.
 */
public final class FmtSchemeTemplates {

    private static final String A = XMLConstants.DRAWING_NS;

    private FmtSchemeTemplates() {}

    public static Element createFmtScheme(Document doc, String schemeName) {
        Element fmtScheme = doc.createElementNS(A, "a:fmtScheme");
        fmtScheme.setAttribute("name", schemeName);

        fmtScheme.appendChild(createFillStyleLst(doc));
        fmtScheme.appendChild(createLnStyleLst(doc));
        fmtScheme.appendChild(createEffectStyleLst(doc));
        fmtScheme.appendChild(createBgFillStyleLst(doc));

        return fmtScheme;
    }

    private static Element createFillStyleLst(Document doc) {
        Element lst = doc.createElementNS(A, "a:fillStyleLst");

        // Style 1: solid fill
        Element solid = doc.createElementNS(A, "a:solidFill");
        solid.appendChild(schemeClr(doc, "phClr"));
        lst.appendChild(solid);

        // Style 2: gradient fill (subtle)
        lst.appendChild(createSubtleGradient(doc));

        // Style 3: gradient fill (intense)
        lst.appendChild(createIntenseGradient(doc));

        return lst;
    }

    private static Element createSubtleGradient(Document doc) {
        Element grad = doc.createElementNS(A, "a:gradFill");
        grad.setAttribute("rotWithShape", "1");

        Element gsLst = doc.createElementNS(A, "a:gsLst");
        gsLst.appendChild(gradStop(doc, "0", "phClr", "tint", "50000", "satMod", "300000"));
        gsLst.appendChild(gradStop(doc, "35000", "phClr", "tint", "37000", "satMod", "300000"));
        gsLst.appendChild(gradStop(doc, "100000", "phClr", "tint", "15000", "satMod", "350000"));
        grad.appendChild(gsLst);

        Element lin = doc.createElementNS(A, "a:lin");
        lin.setAttribute("ang", "16200000");
        lin.setAttribute("scaled", "1");
        grad.appendChild(lin);

        return grad;
    }

    private static Element createIntenseGradient(Document doc) {
        Element grad = doc.createElementNS(A, "a:gradFill");
        grad.setAttribute("rotWithShape", "1");

        Element gsLst = doc.createElementNS(A, "a:gsLst");
        gsLst.appendChild(gradStopWithShade(doc, "0", "phClr", "100000", "100000", "130000"));
        gsLst.appendChild(gradStopWithShade(doc, "100000", "phClr", "50000", "100000", "350000"));
        grad.appendChild(gsLst);

        Element lin = doc.createElementNS(A, "a:lin");
        lin.setAttribute("ang", "16200000");
        lin.setAttribute("scaled", "0");
        grad.appendChild(lin);

        return grad;
    }

    private static Element createLnStyleLst(Document doc) {
        Element lst = doc.createElementNS(A, "a:lnStyleLst");

        // Thin line
        lst.appendChild(createLine(doc, "9525", "95000", "105000"));
        // Medium line
        lst.appendChild(createLine(doc, "25400", null, null));
        // Thick line
        lst.appendChild(createLine(doc, "38100", null, null));

        return lst;
    }

    private static Element createLine(Document doc, String width, String shade, String satMod) {
        Element ln = doc.createElementNS(A, "a:ln");
        ln.setAttribute("w", width);
        ln.setAttribute("cap", "flat");
        ln.setAttribute("cmpd", "sng");
        ln.setAttribute("algn", "ctr");

        Element fill = doc.createElementNS(A, "a:solidFill");
        Element clr = schemeClr(doc, "phClr");
        if (shade != null) {
            Element shadeMod = doc.createElementNS(A, "a:shade");
            shadeMod.setAttribute("val", shade);
            clr.appendChild(shadeMod);
            Element sat = doc.createElementNS(A, "a:satMod");
            sat.setAttribute("val", satMod);
            clr.appendChild(sat);
        }
        fill.appendChild(clr);
        ln.appendChild(fill);

        Element dash = doc.createElementNS(A, "a:prstDash");
        dash.setAttribute("val", "solid");
        ln.appendChild(dash);

        return ln;
    }

    private static Element createEffectStyleLst(Document doc) {
        Element lst = doc.createElementNS(A, "a:effectStyleLst");

        // Style 1: subtle shadow
        lst.appendChild(createEffectStyle(doc, "40000", "20000", "38000", false));
        // Style 2: medium shadow
        lst.appendChild(createEffectStyle(doc, "40000", "23000", "35000", false));
        // Style 3: strong shadow + 3D
        lst.appendChild(createEffectStyle(doc, "40000", "23000", "35000", true));

        return lst;
    }

    private static Element createEffectStyle(Document doc, String blur, String dist, String alpha, boolean with3d) {
        Element style = doc.createElementNS(A, "a:effectStyle");

        Element effectLst = doc.createElementNS(A, "a:effectLst");
        Element shadow = doc.createElementNS(A, "a:outerShdw");
        shadow.setAttribute("blurRad", blur);
        shadow.setAttribute("dist", dist);
        shadow.setAttribute("dir", "5400000");
        shadow.setAttribute("rotWithShape", "0");
        Element clr = doc.createElementNS(A, "a:srgbClr");
        clr.setAttribute("val", "000000");
        Element alphaEl = doc.createElementNS(A, "a:alpha");
        alphaEl.setAttribute("val", alpha);
        clr.appendChild(alphaEl);
        shadow.appendChild(clr);
        effectLst.appendChild(shadow);
        style.appendChild(effectLst);

        if (with3d) {
            Element scene3d = doc.createElementNS(A, "a:scene3d");
            Element camera = doc.createElementNS(A, "a:camera");
            camera.setAttribute("prst", "orthographicFront");
            Element camRot = doc.createElementNS(A, "a:rot");
            camRot.setAttribute("lat", "0");
            camRot.setAttribute("lon", "0");
            camRot.setAttribute("rev", "0");
            camera.appendChild(camRot);
            scene3d.appendChild(camera);
            Element lightRig = doc.createElementNS(A, "a:lightRig");
            lightRig.setAttribute("rig", "threePt");
            lightRig.setAttribute("dir", "t");
            Element lightRot = doc.createElementNS(A, "a:rot");
            lightRot.setAttribute("lat", "0");
            lightRot.setAttribute("lon", "0");
            lightRot.setAttribute("rev", "1200000");
            lightRig.appendChild(lightRot);
            scene3d.appendChild(lightRig);
            style.appendChild(scene3d);

            Element sp3d = doc.createElementNS(A, "a:sp3d");
            Element bevel = doc.createElementNS(A, "a:bevelT");
            bevel.setAttribute("w", "63500");
            bevel.setAttribute("h", "25400");
            sp3d.appendChild(bevel);
            style.appendChild(sp3d);
        }

        return style;
    }

    private static Element createBgFillStyleLst(Document doc) {
        Element lst = doc.createElementNS(A, "a:bgFillStyleLst");

        // BG 1: solid
        Element solid = doc.createElementNS(A, "a:solidFill");
        solid.appendChild(schemeClr(doc, "phClr"));
        lst.appendChild(solid);

        // BG 2: radial gradient
        Element grad1 = doc.createElementNS(A, "a:gradFill");
        grad1.setAttribute("rotWithShape", "1");
        Element gsLst1 = doc.createElementNS(A, "a:gsLst");
        gsLst1.appendChild(gradStop(doc, "0", "phClr", "tint", "40000", "satMod", "350000"));
        gsLst1.appendChild(gradStopWithShade(doc, "40000", "phClr", "45000", "99000", "350000"));
        gsLst1.appendChild(gradStopShadeOnly(doc, "100000", "phClr", "20000", "255000"));
        grad1.appendChild(gsLst1);
        Element path1 = doc.createElementNS(A, "a:path");
        path1.setAttribute("path", "circle");
        Element rect1 = doc.createElementNS(A, "a:fillToRect");
        rect1.setAttribute("l", "50000");
        rect1.setAttribute("t", "-80000");
        rect1.setAttribute("r", "50000");
        rect1.setAttribute("b", "180000");
        path1.appendChild(rect1);
        grad1.appendChild(path1);
        lst.appendChild(grad1);

        // BG 3: centered radial gradient
        Element grad2 = doc.createElementNS(A, "a:gradFill");
        grad2.setAttribute("rotWithShape", "1");
        Element gsLst2 = doc.createElementNS(A, "a:gsLst");
        gsLst2.appendChild(gradStop(doc, "0", "phClr", "tint", "80000", "satMod", "300000"));
        gsLst2.appendChild(gradStopShadeOnly(doc, "100000", "phClr", "30000", "200000"));
        grad2.appendChild(gsLst2);
        Element path2 = doc.createElementNS(A, "a:path");
        path2.setAttribute("path", "circle");
        Element rect2 = doc.createElementNS(A, "a:fillToRect");
        rect2.setAttribute("l", "50000");
        rect2.setAttribute("t", "50000");
        rect2.setAttribute("r", "50000");
        rect2.setAttribute("b", "50000");
        path2.appendChild(rect2);
        grad2.appendChild(path2);
        lst.appendChild(grad2);

        return lst;
    }

    // --- Helper methods ---

    private static Element schemeClr(Document doc, String val) {
        Element el = doc.createElementNS(A, "a:schemeClr");
        el.setAttribute("val", val);
        return el;
    }

    private static Element gradStop(Document doc, String pos, String clrVal,
                                     String mod1Name, String mod1Val,
                                     String mod2Name, String mod2Val) {
        Element gs = doc.createElementNS(A, "a:gs");
        gs.setAttribute("pos", pos);
        Element clr = schemeClr(doc, clrVal);
        Element m1 = doc.createElementNS(A, "a:" + mod1Name);
        m1.setAttribute("val", mod1Val);
        clr.appendChild(m1);
        Element m2 = doc.createElementNS(A, "a:" + mod2Name);
        m2.setAttribute("val", mod2Val);
        clr.appendChild(m2);
        gs.appendChild(clr);
        return gs;
    }

    private static Element gradStopWithShade(Document doc, String pos, String clrVal,
                                              String tintVal, String shadeVal, String satModVal) {
        Element gs = doc.createElementNS(A, "a:gs");
        gs.setAttribute("pos", pos);
        Element clr = schemeClr(doc, clrVal);
        Element tint = doc.createElementNS(A, "a:tint");
        tint.setAttribute("val", tintVal);
        clr.appendChild(tint);
        Element shade = doc.createElementNS(A, "a:shade");
        shade.setAttribute("val", shadeVal);
        clr.appendChild(shade);
        Element satMod = doc.createElementNS(A, "a:satMod");
        satMod.setAttribute("val", satModVal);
        clr.appendChild(satMod);
        gs.appendChild(clr);
        return gs;
    }

    private static Element gradStopShadeOnly(Document doc, String pos, String clrVal,
                                              String shadeVal, String satModVal) {
        Element gs = doc.createElementNS(A, "a:gs");
        gs.setAttribute("pos", pos);
        Element clr = schemeClr(doc, clrVal);
        Element shade = doc.createElementNS(A, "a:shade");
        shade.setAttribute("val", shadeVal);
        clr.appendChild(shade);
        Element satMod = doc.createElementNS(A, "a:satMod");
        satMod.setAttribute("val", satModVal);
        clr.appendChild(satMod);
        gs.appendChild(clr);
        return gs;
    }
}
