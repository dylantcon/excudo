package com.excudo.core.geometry;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Pins the preset-geometry catalogue: the vendored ECMA-376 definitions
 * parse completely, every preset the parity corpus references resolves,
 * well-known presets carry the exact structure the spec gives them, and
 * unknown names throw instead of falling back to a rectangle.
 */
public class RegistryCompletenessTest {

    /**
     * Every {@code prstGeom/@prst} value used by the six preset-shapes
     * parity decks, extracted from the committed deck.pptx files:
     *
     * <pre>
     *   for deck in parity-corpus/preset-shapes-* /deck.pptx:
     *       grep -o 'prstGeom prst="[^"]*"' ppt/slides/slide*.xml
     * </pre>
     *
     * 177 distinct names (python-pptx MSO_SHAPE vocabulary). If a corpus
     * deck is regenerated with new members this list must be re-derived.
     */
    private static final String[] CORPUS_PRESETS = {
        // preset-shapes-actionbuttons
        "actionButtonBackPrevious", "actionButtonBeginning", "actionButtonBlank",
        "actionButtonDocument", "actionButtonEnd", "actionButtonForwardNext",
        "actionButtonHelp", "actionButtonHome", "actionButtonInformation",
        "actionButtonMovie", "actionButtonReturn", "actionButtonSound",
        // preset-shapes-arrows
        "bentArrow", "bentUpArrow", "circularArrow", "curvedDownArrow",
        "curvedLeftArrow", "curvedRightArrow", "curvedUpArrow", "downArrow",
        "leftArrow", "leftCircularArrow", "leftRightArrow", "leftRightCircularArrow",
        "leftRightUpArrow", "leftUpArrow", "notchedRightArrow", "quadArrow",
        "rightArrow", "stripedRightArrow", "swooshArrow", "upArrow",
        "upDownArrow", "uturnArrow",
        // preset-shapes-basic
        "arc", "bevel", "blockArc", "bracePair", "bracketPair", "can",
        "chartPlus", "chartX", "chevron", "chord", "cloud", "corner",
        "cornerTabs", "cube", "decagon", "diagStripe", "diamond", "dodecagon",
        "donut", "ellipse", "foldedCorner", "frame", "funnel", "gear6",
        "gear9", "halfFrame", "heart", "heptagon", "hexagon", "homePlate",
        "leftBrace", "leftBracket", "lightningBolt", "lineInv", "mathDivide",
        "mathEqual", "mathMinus", "mathMultiply", "mathNotEqual", "mathPlus",
        "moon", "noSmoking", "nonIsoscelesTrapezoid", "octagon", "parallelogram",
        "pentagon", "pie", "pieWedge", "plaque", "plaqueTabs", "plus", "rect",
        "rightBrace", "rightBracket", "round1Rect", "round2DiagRect",
        "round2SameRect", "roundRect", "rtTriangle", "smileyFace", "snip1Rect",
        "snip2DiagRect", "snip2SameRect", "snipRoundRect", "squareTabs", "sun",
        "teardrop", "trapezoid", "triangle",
        // preset-shapes-callouts
        "accentBorderCallout1", "accentBorderCallout2", "accentBorderCallout3",
        "accentCallout1", "accentCallout2", "accentCallout3", "borderCallout1",
        "borderCallout2", "borderCallout3", "callout1", "callout2", "callout3",
        "cloudCallout", "downArrowCallout", "leftArrowCallout",
        "leftRightArrowCallout", "quadArrowCallout", "rightArrowCallout",
        "upArrowCallout", "upDownArrowCallout", "wedgeEllipseCallout",
        "wedgeRectCallout", "wedgeRoundRectCallout",
        // preset-shapes-flowchart
        "flowChartAlternateProcess", "flowChartCollate", "flowChartConnector",
        "flowChartDecision", "flowChartDelay", "flowChartDisplay",
        "flowChartDocument", "flowChartExtract", "flowChartInputOutput",
        "flowChartInternalStorage", "flowChartMagneticDisk", "flowChartMagneticDrum",
        "flowChartMagneticTape", "flowChartManualInput", "flowChartManualOperation",
        "flowChartMerge", "flowChartMultidocument", "flowChartOfflineStorage",
        "flowChartOffpageConnector", "flowChartOnlineStorage", "flowChartOr",
        "flowChartPredefinedProcess", "flowChartPreparation", "flowChartProcess",
        "flowChartPunchedCard", "flowChartPunchedTape", "flowChartSort",
        "flowChartSummingJunction", "flowChartTerminator",
        // preset-shapes-stars-banners
        "chartStar", "doubleWave", "ellipseRibbon", "ellipseRibbon2",
        "horizontalScroll", "irregularSeal1", "irregularSeal2", "leftRightRibbon",
        "ribbon", "ribbon2", "star10", "star12", "star16", "star24", "star32",
        "star4", "star5", "star6", "star7", "star8", "verticalScroll", "wave",
    };

    // ========== catalogue completeness ==========

    @Test
    public void vendoredCatalogueParsesAllPresets() {
        // ECMA-376 ST_ShapeType defines exactly 187 preset geometries and
        // the vendored file carries all of them (docs/THIRD_PARTY.md).
        assertEquals(187, PresetGeometryRegistry.size());
    }

    @Test
    public void everyParityCorpusPresetResolves() {
        for (String name : CORPUS_PRESETS) {
            assertTrue("corpus preset '" + name + "' missing from registry",
                PresetGeometryRegistry.contains(name));
            GeometryDefinition def = PresetGeometryRegistry.get(name);
            assertEquals(name, def.getName());
            assertFalse("preset '" + name + "' has no paths",
                def.getPaths().isEmpty());
        }
    }

    @Test
    public void everyVendoredDefinitionEvaluatesItsGuides() {
        // All 187 definitions' avLst + gdLst formulas must evaluate with
        // defaults, in both landscape and portrait aspect: an unknown op,
        // a forward guide reference, or a malformed formula throws here.
        for (String name : PresetGeometryRegistry.names()) {
            GeometryDefinition def = PresetGeometryRegistry.get(name);
            GuideEvaluator.evaluate(def, Map.of(), 200, 100);
            GuideEvaluator.evaluate(def, Map.of(), 100, 200);
        }
    }

    // ========== spot checks against the spec definitions ==========

    @Test
    public void rectIsFourLinesAndClose() {
        GeometryDefinition rect = PresetGeometryRegistry.get("rect");
        assertTrue(rect.getAdjustDefaults().isEmpty());
        assertTrue(rect.getGuides().isEmpty());
        assertEquals(1, rect.getPaths().size());
        var cmds = rect.getPaths().get(0).getCommands();
        assertEquals(5, cmds.size());
        assertTrue(cmds.get(0) instanceof GeometryPath.MoveTo);
        assertTrue(cmds.get(1) instanceof GeometryPath.LnTo);
        assertTrue(cmds.get(2) instanceof GeometryPath.LnTo);
        assertTrue(cmds.get(3) instanceof GeometryPath.LnTo);
        assertTrue(cmds.get(4) instanceof GeometryPath.Close);
    }

    @Test
    public void roundRectCarriesSpecAdjustDefaultAndCornerArcs() {
        GeometryDefinition rr = PresetGeometryRegistry.get("roundRect");
        assertEquals(1, rr.getAdjustDefaults().size());
        assertEquals("adj", rr.getAdjustDefaults().get(0).name());
        assertEquals("val 16667", rr.getAdjustDefaults().get(0).fmla());
        long arcs = rr.getPaths().get(0).getCommands().stream()
            .filter(c -> c instanceof GeometryPath.ArcTo).count();
        assertEquals(4, arcs);
        assertNotNull("roundRect declares a text rectangle", rr.getTextRect());
        assertEquals("il", rr.getTextRect().left());
    }

    @Test
    public void ellipseIsFourArcs() {
        GeometryDefinition el = PresetGeometryRegistry.get("ellipse");
        assertEquals(1, el.getPaths().size());
        long arcs = el.getPaths().get(0).getCommands().stream()
            .filter(c -> c instanceof GeometryPath.ArcTo).count();
        assertEquals(4, arcs);
    }

    @Test
    public void star5CarriesAllThreeSpecAdjustValues() {
        GeometryDefinition s5 = PresetGeometryRegistry.get("star5");
        assertEquals(3, s5.getAdjustDefaults().size());
        assertEquals("adj", s5.getAdjustDefaults().get(0).name());
        assertEquals("val 19098", s5.getAdjustDefaults().get(0).fmla());
        assertEquals("hf", s5.getAdjustDefaults().get(1).name());
        assertEquals("val 105146", s5.getAdjustDefaults().get(1).fmla());
        assertEquals("vf", s5.getAdjustDefaults().get(2).name());
        assertEquals("val 110557", s5.getAdjustDefaults().get(2).fmla());
    }

    @Test
    public void leftArrowAndTriangleAdjustDefaults() {
        GeometryDefinition la = PresetGeometryRegistry.get("leftArrow");
        assertEquals(2, la.getAdjustDefaults().size());
        assertEquals("val 50000", la.getAdjustDefaults().get(0).fmla());
        assertEquals("val 50000", la.getAdjustDefaults().get(1).fmla());

        GeometryDefinition tri = PresetGeometryRegistry.get("triangle");
        assertEquals(1, tri.getAdjustDefaults().size());
        assertEquals("val 50000", tri.getAdjustDefaults().get(0).fmla());
    }

    @Test
    public void wedgeRectCalloutHasNegativeAdjustDefault() {
        // adj1 = -20833: the tail points down-left of the box by default.
        // Pins signed-literal parsing end to end.
        GeometryDefinition w = PresetGeometryRegistry.get("wedgeRectCallout");
        assertEquals("val -20833", w.getAdjustDefaults().get(0).fmla());
        assertEquals("val 62500", w.getAdjustDefaults().get(1).fmla());
        Map<String, Double> env = GuideEvaluator.evaluate(w, Map.of(), 200, 100);
        assertEquals(-20833.0, env.get("adj1"), 1e-9);
    }

    @Test
    public void flowChartDecisionUsesTwoByTwoLocalSpace() {
        GeometryPath p = PresetGeometryRegistry.get("flowChartDecision").getPaths().get(0);
        assertEquals(2.0, p.getWidth(), 0);
        assertEquals(2.0, p.getHeight(), 0);
    }

    @Test
    public void actionButtonHomeLayersFillModesAndStrokeFlags() {
        // Spec order: face (norm, unstroked), icon back (darkenLess,
        // unstroked), icon front (darken, unstroked), icon outline
        // (none, stroked), border (none, stroked).
        var paths = PresetGeometryRegistry.get("actionButtonHome").getPaths();
        assertEquals(5, paths.size());
        assertEquals(GeometryPath.FillMode.NORM, paths.get(0).getFill());
        assertFalse(paths.get(0).isStroked());
        assertEquals(GeometryPath.FillMode.DARKEN_LESS, paths.get(1).getFill());
        assertFalse(paths.get(1).isStroked());
        assertEquals(GeometryPath.FillMode.DARKEN, paths.get(2).getFill());
        assertFalse(paths.get(2).isStroked());
        assertEquals(GeometryPath.FillMode.NONE, paths.get(3).getFill());
        assertTrue(paths.get(3).isStroked());
        assertEquals(GeometryPath.FillMode.NONE, paths.get(4).getFill());
        assertTrue(paths.get(4).isStroked());
    }

    // ========== the no-fallback contract ==========

    @Test(expected = IllegalArgumentException.class)
    public void unknownPresetThrows() {
        PresetGeometryRegistry.get("notAShape");
    }

    @Test(expected = IllegalArgumentException.class)
    public void presetLookupIsCaseSensitive() {
        PresetGeometryRegistry.get("RoundRect");
    }
}
