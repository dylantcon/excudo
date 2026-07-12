package com.excudo.view.rendering.text;

import com.excudo.core.metrics.TextStyleSource;
import org.junit.Test;
import org.w3c.dom.Element;

import static com.excudo.view.rendering.text.TextPaintTestSupport.A_NS;
import static com.excudo.view.rendering.text.TextPaintTestSupport.P_NS;
import static com.excudo.view.rendering.text.TextPaintTestSupport.parse;
import static org.junit.Assert.*;

/**
 * Pins OOXML lstStyle precedence: shape txBody lstStyle beats slide
 * layout placeholder lstStyle beats master txStyles, merged per
 * property and per indent level. Before A3 only theme/master styles
 * were consulted — a layout-level color override was silently ignored
 * (this test's exact scenario).
 */
public class LstStyleInheritanceTest {

    private static Element aEl(String xml) {
        return parse(xml.replaceFirst(">",
            " xmlns:a=\"" + A_NS + "\" xmlns:p=\"" + P_NS + "\">"));
    }

    private static final String MASTER_BODY_STYLE =
        "<p:bodyStyle>"
        + "<a:lvl1pPr marL=\"342900\" indent=\"-342900\" algn=\"l\">"
        + "<a:buChar char=\"•\"/>"
        + "<a:defRPr sz=\"1800\"><a:solidFill><a:srgbClr val=\"FF0000\"/></a:solidFill>"
        + "<a:latin typeface=\"+mn-lt\"/></a:defRPr>"
        + "</a:lvl1pPr>"
        + "</p:bodyStyle>";

    private static final String LAYOUT_LST_STYLE =
        "<a:lstStyle>"
        + "<a:lvl1pPr>"
        + "<a:defRPr><a:solidFill><a:srgbClr val=\"00FF00\"/></a:solidFill></a:defRPr>"
        + "</a:lvl1pPr>"
        + "</a:lstStyle>";

    private static final String SHAPE_LST_STYLE =
        "<a:lstStyle>"
        + "<a:lvl1pPr>"
        + "<a:defRPr><a:solidFill><a:srgbClr val=\"0000FF\"/></a:solidFill></a:defRPr>"
        + "</a:lvl1pPr>"
        + "</a:lstStyle>";

    @Test
    public void layoutLevelColorBeatsMasterLevel() {
        LstStyleResolver resolver = LstStyleResolver.ofChain("Georgia", "Calibri",
            aEl(MASTER_BODY_STYLE), aEl(LAYOUT_LST_STYLE));

        TextStyleSource.LevelStyle ls = resolver.levelStyle(0);
        assertNotNull(ls.color());
        assertEquals("layout lstStyle color must override master txStyles",
            "00FF00", ls.color().getHexVal());
        // Properties the layout does NOT restate still come from the master.
        assertEquals("master font size survives a color-only layout override",
            Integer.valueOf(1800), ls.fontSizeCentiPt());
        assertEquals(Integer.valueOf(342900), ls.marginLeftEmu());
        assertEquals(Integer.valueOf(-342900), ls.indentEmu());
        assertEquals("master bullet survives", "•", ls.bulletChar());
        assertEquals("+mn-lt resolves to the theme minor font", "Calibri", ls.fontFamily());
    }

    @Test
    public void shapeLstStyleBeatsLayoutAndMaster() {
        LstStyleResolver resolver = LstStyleResolver.ofChain("Georgia", "Calibri",
            aEl(MASTER_BODY_STYLE), aEl(LAYOUT_LST_STYLE), aEl(SHAPE_LST_STYLE));

        assertEquals("shape's own lstStyle wins the chain",
            "0000FF", resolver.levelStyle(0).color().getHexVal());
    }

    @Test
    public void defaultTextStyleSuppliesPerLevelMargins() {
        // The presentation part's defaultTextStyle is the style root for
        // plain text boxes; its per-level marL is what indents multi-level
        // lists (text-bullets corpus deck, "Indent level N" cell).
        Element defaultTextStyle = aEl(
            "<p:defaultTextStyle>"
            + "<a:lvl1pPr marL=\"0\"><a:defRPr sz=\"1800\"/></a:lvl1pPr>"
            + "<a:lvl2pPr marL=\"457200\"><a:defRPr sz=\"1800\"/></a:lvl2pPr>"
            + "<a:lvl3pPr marL=\"914400\"><a:defRPr sz=\"1800\"/></a:lvl3pPr>"
            + "</p:defaultTextStyle>");
        LstStyleResolver resolver = LstStyleResolver.ofChain(null, null, defaultTextStyle);

        assertEquals(Integer.valueOf(0), resolver.levelStyle(0).marginLeftEmu());
        assertEquals(Integer.valueOf(457200), resolver.levelStyle(1).marginLeftEmu());
        assertEquals(Integer.valueOf(914400), resolver.levelStyle(2).marginLeftEmu());
    }

    @Test
    public void bulletKindOverridesAsAGroup() {
        // A level that says buNone must suppress an inherited buChar —
        // field-by-field merging would resurrect the master's bullet.
        Element noBullet = aEl(
            "<a:lstStyle><a:lvl1pPr><a:buNone/></a:lvl1pPr></a:lstStyle>");
        LstStyleResolver resolver = LstStyleResolver.ofChain(null, "Calibri",
            aEl(MASTER_BODY_STYLE), noBullet);

        TextStyleSource.LevelStyle ls = resolver.levelStyle(0);
        assertEquals(Boolean.TRUE, ls.bulletNone());
        assertNull("buNone must suppress the inherited buChar", ls.bulletChar());
    }
}
