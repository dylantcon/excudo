package com.excudo.xml.writers.animations;

import org.junit.Test;
import static org.junit.Assert.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.animations.AnimationFactoryRegistry;
import com.excudo.xml.writers.WriterTestFixtures;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;

/**
 * Unit tests for ColorAnimationFactory.
 * Validates HSL emphasis (DARKEN, DESATURATE, LIGHTEN) and RGB target (COLOR_PULSE)
 * against oracle OOXML patterns.
 */
public class ColorAnimationFactoryTest {

    private static final String P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main";

    // ========== HSL EMPHASIS PATTERN TESTS ==========

    @Test
    public void testDarkenProducesThreeAnimClrAndOneSet() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.DARKEN);

        assertEquals("DARKEN should produce 4 elements (3 animClr + 1 set)", 4, elements.size());
        assertEquals("p:animClr", elements.get(0).getTagName());
        assertEquals("p:animClr", elements.get(1).getTagName());
        assertEquals("p:animClr", elements.get(2).getTagName());
        assertEquals("p:set", elements.get(3).getTagName());
    }

    @Test
    public void testDarkenHslDeltas() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.DARKEN);

        // All three animClr should have same HSL deltas
        for (int i = 0; i < 3; i++) {
            Element animClr = elements.get(i);
            Element by = getChildByTag(animClr, "p:by");
            assertNotNull("animClr[" + i + "] should have p:by child", by);
            Element hsl = getChildByTag(by, "p:hsl");
            assertNotNull("p:by should have p:hsl child", hsl);
            assertEquals("h", "0", hsl.getAttribute("h"));
            assertEquals("s", "-12549", hsl.getAttribute("s"));
            assertEquals("l", "-25098", hsl.getAttribute("l"));
        }
    }

    @Test
    public void testDesaturateHslDeltas() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.DESATURATE);

        Element hsl = getChildByTag(getChildByTag(elements.get(0), "p:by"), "p:hsl");
        assertEquals("0", hsl.getAttribute("h"));
        assertEquals("-70588", hsl.getAttribute("s"));
        assertEquals("0", hsl.getAttribute("l"));
    }

    @Test
    public void testLightenHslDeltas() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.LIGHTEN);

        Element hsl = getChildByTag(getChildByTag(elements.get(0), "p:by"), "p:hsl");
        assertEquals("0", hsl.getAttribute("h"));
        assertEquals("12549", hsl.getAttribute("s"));
        assertEquals("25098", hsl.getAttribute("l"));
    }

    @Test
    public void testHslEmphasisFirstAnimClrHasOverrideChildStyle() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.DARKEN);

        Element firstCBhvr = getChildByTag(elements.get(0), "p:cBhvr");
        assertEquals("First animClr cBhvr should have override=childStyle",
                     "childStyle", firstCBhvr.getAttribute("override"));

        Element secondCBhvr = getChildByTag(elements.get(1), "p:cBhvr");
        assertEquals("Second animClr cBhvr should NOT have override",
                     "", secondCBhvr.getAttribute("override"));
    }

    @Test
    public void testHslEmphasisTargetAttributes() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.LIGHTEN);

        String[] expectedAttrs = {"style.color", "fillcolor", "stroke.color"};
        for (int i = 0; i < 3; i++) {
            Element cBhvr = getChildByTag(elements.get(i), "p:cBhvr");
            Element attrNameLst = getChildByTag(cBhvr, "p:attrNameLst");
            Element attrName = getChildByTag(attrNameLst, "p:attrName");
            assertEquals("animClr[" + i + "] should target " + expectedAttrs[i],
                         expectedAttrs[i], attrName.getTextContent());
        }
    }

    @Test
    public void testHslEmphasisColorSpaceAndDirection() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.DESATURATE);

        for (int i = 0; i < 3; i++) {
            Element animClr = elements.get(i);
            assertEquals("hsl", animClr.getAttribute("clrSpc"));
            assertEquals("cw", animClr.getAttribute("dir"));
        }
    }

    @Test
    public void testFillTypeSetElement() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.DARKEN);

        Element set = elements.get(3);
        assertEquals("p:set", set.getTagName());
        Element setCBhvr = getChildByTag(set, "p:cBhvr");
        Element setAttrNameLst = getChildByTag(setCBhvr, "p:attrNameLst");
        Element setAttrName = getChildByTag(setAttrNameLst, "p:attrName");
        assertEquals("fill.type", setAttrName.getTextContent());

        Element to = getChildByTag(set, "p:to");
        Element strVal = getChildByTag(to, "p:strVal");
        assertEquals("solid", strVal.getAttribute("val"));
    }

    // ========== COLOR_PULSE TESTS ==========

    @Test
    public void testColorPulseProducesTwoAnimClrAndTwoSets() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.COLOR_PULSE);

        assertEquals("COLOR_PULSE should produce 4 elements (2 animClr + 2 set)", 4, elements.size());
        assertEquals("p:animClr", elements.get(0).getTagName());
        assertEquals("p:animClr", elements.get(1).getTagName());
        assertEquals("p:set", elements.get(2).getTagName());
        assertEquals("p:set", elements.get(3).getTagName());
    }

    @Test
    public void testColorPulseUsesRgbColorSpace() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.COLOR_PULSE);

        for (int i = 0; i < 2; i++) {
            Element animClr = elements.get(i);
            assertEquals("rgb", animClr.getAttribute("clrSpc"));
            assertEquals("cw", animClr.getAttribute("dir"));
        }
    }

    @Test
    public void testColorPulseHasAutoRevAndFillRemove() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.COLOR_PULSE);

        // Check autoRev on inner cTn of first animClr
        Element cBhvr = getChildByTag(elements.get(0), "p:cBhvr");
        Element innerCTn = getChildByTag(cBhvr, "p:cTn");
        assertEquals("1", innerCTn.getAttribute("autoRev"));
        assertEquals("remove", innerCTn.getAttribute("fill"));
    }

    @Test
    public void testColorPulseTargetColor() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.COLOR_PULSE);

        Element to = getChildByTag(elements.get(0), "p:to");
        assertNotNull("COLOR_PULSE animClr should have p:to", to);
        // Should contain a:srgbClr
        Element srgbClr = (Element) to.getFirstChild();
        assertEquals("a:srgbClr", srgbClr.getTagName());
        assertEquals("181818", srgbClr.getAttribute("val"));
    }

    @Test
    public void testColorPulseFillOnSet() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        List<Element> elements = createElements(factory, AnimationType.COLOR_PULSE);

        // Fourth element should be p:set for fill.on=true
        Element fillOnSet = elements.get(3);
        Element setCBhvr = getChildByTag(fillOnSet, "p:cBhvr");
        Element attrNameLst = getChildByTag(setCBhvr, "p:attrNameLst");
        Element attrName = getChildByTag(attrNameLst, "p:attrName");
        assertEquals("fill.on", attrName.getTextContent());
    }

    // ========== EMPHASIS TYPE TESTS ==========

    @Test
    public void testEmphasisTypesDoNotGenerateVisibilitySets() throws Exception {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());

        for (AnimationType type : new AnimationType[]{AnimationType.DARKEN, AnimationType.DESATURATE,
                                                       AnimationType.LIGHTEN, AnimationType.COLOR_PULSE}) {
            List<Element> elements = createElements(factory, type);
            for (Element el : elements) {
                if ("p:set".equals(el.getTagName())) {
                    // Verify it's NOT a visibility set
                    Element cBhvr = getChildByTag(el, "p:cBhvr");
                    Element attrNameLst = getChildByTag(cBhvr, "p:attrNameLst");
                    Element attrName = getChildByTag(attrNameLst, "p:attrName");
                    assertNotEquals("Emphasis " + type + " should not have visibility sets",
                                   "style.visibility", attrName.getTextContent());
                }
            }
        }
    }

    // ========== REGISTRY INTEGRATION ==========

    @Test
    public void testRegistryResolvesAllColorTypes() {
        AnimationFactoryRegistry registry = new AnimationFactoryRegistry();
        for (AnimationType type : new AnimationType[]{AnimationType.DARKEN, AnimationType.DESATURATE,
                                                       AnimationType.LIGHTEN, AnimationType.COLOR_PULSE}) {
            assertNotNull("Registry should resolve factory for " + type, registry.getFactory(type));
            assertEquals("ColorAnimationFactory", registry.getFactory(type).getClass().getSimpleName());
        }
    }

    @Test
    public void testSupportsAnimationTypeRejectsUnrelated() {
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        assertFalse(factory.supportsAnimationType(AnimationType.FADE));
        assertFalse(factory.supportsAnimationType(AnimationType.SPIN));
        assertFalse(factory.supportsAnimationType(AnimationType.PULSE));
        assertFalse(factory.supportsAnimationType(AnimationType.TRANSPARENCY));
    }

    // ========== HELPERS ==========

    private List<Element> createElements(ColorAnimationFactory factory, AnimationType type) throws Exception {
        Document doc = createTestDocument();
        AnimationBinding binding = AnimationBinding.builder()
            .type(type)
            .target(3)
            .emphasis()
            .duration("500")
            .delay("0")
            .animationGroup("on-click")
            .build();
        return factory.createAnimationElements(doc, binding, null);
    }

    private Document createTestDocument() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().newDocument();
    }

    private Element getChildByTag(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && tagName.equals(el.getTagName())) {
                return el;
            }
        }
        return null;
    }
}
