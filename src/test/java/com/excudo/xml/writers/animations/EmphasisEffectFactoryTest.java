package com.excudo.xml.writers.animations;

import org.junit.Test;
import static org.junit.Assert.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.animations.AnimationFactoryRegistry;
import com.excudo.xml.writers.WriterTestFixtures;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;

/**
 * Unit tests for EmphasisEffectFactory.
 * Validates TRANSPARENCY (p:set + p:animEffect) and PULSE (p:animEffect + p:animScale)
 * against oracle OOXML patterns.
 */
public class EmphasisEffectFactoryTest {

    // ========== TRANSPARENCY TESTS ==========

    @Test
    public void testTransparencyProducesSetAndAnimEffect() throws Exception {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        List<Element> elements = createElements(factory, AnimationType.TRANSPARENCY);

        assertEquals("TRANSPARENCY should produce 2 elements", 2, elements.size());
        assertEquals("First element should be p:set", "p:set", elements.get(0).getTagName());
        assertEquals("Second element should be p:animEffect", "p:animEffect", elements.get(1).getTagName());
    }

    @Test
    public void testTransparencySetTargetsStyleOpacity() throws Exception {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        List<Element> elements = createElements(factory, AnimationType.TRANSPARENCY);

        Element set = elements.get(0);
        Element cBhvr = getChildByTag(set, "p:cBhvr");
        Element attrNameLst = getChildByTag(cBhvr, "p:attrNameLst");
        Element attrName = getChildByTag(attrNameLst, "p:attrName");
        assertEquals("style.opacity", attrName.getTextContent());
    }

    @Test
    public void testTransparencySetValue() throws Exception {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        List<Element> elements = createElements(factory, AnimationType.TRANSPARENCY);

        Element set = elements.get(0);
        Element to = getChildByTag(set, "p:to");
        Element strVal = getChildByTag(to, "p:strVal");
        assertEquals("0.75", strVal.getAttribute("val"));
    }

    @Test
    public void testTransparencyAnimEffectFilter() throws Exception {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        List<Element> elements = createElements(factory, AnimationType.TRANSPARENCY);

        Element animEffect = elements.get(1);
        assertEquals("image", animEffect.getAttribute("filter"));
        assertEquals("opacity: 0.75", animEffect.getAttribute("prLst"));
    }

    @Test
    public void testTransparencyAnimEffectHasRctx() throws Exception {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        List<Element> elements = createElements(factory, AnimationType.TRANSPARENCY);

        Element animEffect = elements.get(1);
        Element cBhvr = getChildByTag(animEffect, "p:cBhvr");
        assertEquals("IE", cBhvr.getAttribute("rctx"));
    }

    @Test
    public void testTransparencyUsesIndefiniteDuration() throws Exception {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        List<Element> elements = createElements(factory, AnimationType.TRANSPARENCY);

        // Both elements should use indefinite duration on inner cTn
        for (Element el : elements) {
            Element cBhvr = getChildByTag(el, "p:cBhvr");
            Element innerCTn = getChildByTag(cBhvr, "p:cTn");
            assertEquals("indefinite", innerCTn.getAttribute("dur"));
        }
    }

    // ========== PULSE TESTS ==========

    @Test
    public void testPulseProducesAnimEffectAndAnimScale() throws Exception {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        List<Element> elements = createElements(factory, AnimationType.PULSE);

        assertEquals("PULSE should produce 2 elements", 2, elements.size());
        assertEquals("First element should be p:animEffect", "p:animEffect", elements.get(0).getTagName());
        assertEquals("Second element should be p:animScale", "p:animScale", elements.get(1).getTagName());
    }

    @Test
    public void testPulseAnimEffectAttributes() throws Exception {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        List<Element> elements = createElements(factory, AnimationType.PULSE);

        Element animEffect = elements.get(0);
        assertEquals("out", animEffect.getAttribute("transition"));
        assertEquals("fade", animEffect.getAttribute("filter"));
    }

    @Test
    public void testPulseAnimEffectTmFilter() throws Exception {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        List<Element> elements = createElements(factory, AnimationType.PULSE);

        Element animEffect = elements.get(0);
        Element cBhvr = getChildByTag(animEffect, "p:cBhvr");
        Element innerCTn = getChildByTag(cBhvr, "p:cTn");
        assertEquals("0, 0; .2, .5; .8, .5; 1, 0", innerCTn.getAttribute("tmFilter"));
    }

    @Test
    public void testPulseAnimScaleAutoRev() throws Exception {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        List<Element> elements = createElements(factory, AnimationType.PULSE);

        Element animScale = elements.get(1);
        Element cBhvr = getChildByTag(animScale, "p:cBhvr");
        Element innerCTn = getChildByTag(cBhvr, "p:cTn");
        assertEquals("1", innerCTn.getAttribute("autoRev"));
    }

    @Test
    public void testPulseAnimScaleByValues() throws Exception {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        List<Element> elements = createElements(factory, AnimationType.PULSE);

        Element animScale = elements.get(1);
        Element by = getChildByTag(animScale, "p:by");
        assertNotNull("p:animScale should have p:by child", by);
        assertEquals("105000", by.getAttribute("x"));
        assertEquals("105000", by.getAttribute("y"));
    }

    @Test
    public void testPulseDoesNotGenerateVisibilitySets() throws Exception {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        List<Element> elements = createElements(factory, AnimationType.PULSE);

        for (Element el : elements) {
            // Neither element should be a visibility set
            if ("p:set".equals(el.getTagName())) {
                Element cBhvr = getChildByTag(el, "p:cBhvr");
                Element attrNameLst = getChildByTag(cBhvr, "p:attrNameLst");
                Element attrName = getChildByTag(attrNameLst, "p:attrName");
                assertNotEquals("style.visibility", attrName.getTextContent());
            }
        }
    }

    // ========== REGISTRY INTEGRATION ==========

    @Test
    public void testRegistryResolvesTransparency() {
        AnimationFactoryRegistry registry = new AnimationFactoryRegistry();
        assertNotNull(registry.getFactory(AnimationType.TRANSPARENCY));
        assertEquals("EmphasisEffectFactory",
                     registry.getFactory(AnimationType.TRANSPARENCY).getClass().getSimpleName());
    }

    @Test
    public void testRegistryResolvesPulse() {
        AnimationFactoryRegistry registry = new AnimationFactoryRegistry();
        assertNotNull(registry.getFactory(AnimationType.PULSE));
        assertEquals("EmphasisEffectFactory",
                     registry.getFactory(AnimationType.PULSE).getClass().getSimpleName());
    }

    @Test
    public void testSupportsAnimationTypeRejectsUnrelated() {
        EmphasisEffectFactory factory = WriterTestFixtures.configureFactory(new EmphasisEffectFactory());
        assertFalse(factory.supportsAnimationType(AnimationType.FADE));
        assertFalse(factory.supportsAnimationType(AnimationType.DARKEN));
        assertFalse(factory.supportsAnimationType(AnimationType.SPIN));
        assertFalse(factory.supportsAnimationType(AnimationType.COLOR_PULSE));
    }

    // ========== HELPERS ==========

    private List<Element> createElements(EmphasisEffectFactory factory, AnimationType type) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
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
