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
 * Unit tests for RotationAnimationFactory.
 * Validates SPIN (single p:animRot) and TEETER (5 sequenced p:animRot) patterns
 * against oracle OOXML output.
 */
public class RotationAnimationFactoryTest {

    private static final String P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main";

    // ========== SPIN TESTS ==========

    @Test
    public void testSpinProducesSingleAnimRot() throws Exception {
        RotationAnimationFactory factory = WriterTestFixtures.configureFactory(new RotationAnimationFactory());
        List<Element> elements = createEmphasisElements(factory, AnimationType.SPIN);

        assertEquals("SPIN emphasis should produce 1 element", 1, elements.size());
        assertEquals("p:animRot", elements.get(0).getTagName());
    }

    @Test
    public void testSpinRotationValue() throws Exception {
        RotationAnimationFactory factory = WriterTestFixtures.configureFactory(new RotationAnimationFactory());
        List<Element> elements = createEmphasisElements(factory, AnimationType.SPIN);

        Element animRot = elements.get(0);
        assertEquals("21600000", animRot.getAttribute("by"));
    }

    @Test
    public void testSpinEntranceAddsVisibilitySet() throws Exception {
        RotationAnimationFactory factory = WriterTestFixtures.configureFactory(new RotationAnimationFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.SPIN);

        assertEquals("SPIN entrance should produce 2 elements", 2, elements.size());
        assertEquals("p:set", elements.get(0).getTagName());
        assertEquals("p:animRot", elements.get(1).getTagName());
    }

    // ========== TEETER TESTS ==========

    @Test
    public void testTeeterProducesFiveAnimRot() throws Exception {
        RotationAnimationFactory factory = WriterTestFixtures.configureFactory(new RotationAnimationFactory());
        List<Element> elements = createEmphasisElements(factory, AnimationType.TEETER);

        assertEquals("TEETER should produce 5 p:animRot elements", 5, elements.size());
        for (Element el : elements) {
            assertEquals("p:animRot", el.getTagName());
        }
    }

    @Test
    public void testTeeterRotationValues() throws Exception {
        RotationAnimationFactory factory = WriterTestFixtures.configureFactory(new RotationAnimationFactory());
        List<Element> elements = createEmphasisElements(factory, AnimationType.TEETER);

        String[] expectedBy = { "120000", "-240000", "240000", "-240000", "120000" };
        for (int i = 0; i < 5; i++) {
            assertEquals("TEETER keyframe " + i + " rotation",
                         expectedBy[i], elements.get(i).getAttribute("by"));
        }
    }

    @Test
    public void testTeeterKeyframeDurations() throws Exception {
        RotationAnimationFactory factory = WriterTestFixtures.configureFactory(new RotationAnimationFactory());
        List<Element> elements = createEmphasisElements(factory, AnimationType.TEETER);

        // Expected durations from oracle: 100, 200, 200, 200, 200
        String[] expectedDur = { "100", "200", "200", "200", "200" };
        for (int i = 0; i < 5; i++) {
            Element cBhvr = getChildByTag(elements.get(i), "p:cBhvr");
            Element cTn = getChildByTag(cBhvr, "p:cTn");
            assertEquals("TEETER keyframe " + i + " duration",
                         expectedDur[i], cTn.getAttribute("dur"));
        }
    }

    @Test
    public void testTeeterDoesNotProduceVisibilitySets() throws Exception {
        RotationAnimationFactory factory = WriterTestFixtures.configureFactory(new RotationAnimationFactory());
        List<Element> elements = createEmphasisElements(factory, AnimationType.TEETER);

        for (Element el : elements) {
            assertNotEquals("TEETER should not produce visibility sets",
                            "p:set", el.getTagName());
        }
    }

    @Test
    public void testTeeterOmitsAdditiveAttribute() throws Exception {
        // Oracle pattern: TEETER p:cBhvr does NOT have additive="base".
        // PowerPoint rejected timing when additive="base" was present on emphasis TEETER.
        RotationAnimationFactory factory = WriterTestFixtures.configureFactory(new RotationAnimationFactory());
        List<Element> elements = createEmphasisElements(factory, AnimationType.TEETER);

        for (int i = 0; i < 5; i++) {
            Element cBhvr = getChildByTag(elements.get(i), "p:cBhvr");
            assertFalse("TEETER keyframe " + i + " cBhvr must NOT have additive attribute (oracle pattern)",
                         cBhvr.hasAttribute("additive"));
        }
    }

    @Test
    public void testTeeterKeyframeDelays() throws Exception {
        // Oracle pattern: delays are {0, 200, 400, 600, 800} (cumulative absolute delays).
        // Previous incorrect values were {0, 100, 300, 500, 700}.
        RotationAnimationFactory factory = WriterTestFixtures.configureFactory(new RotationAnimationFactory());
        List<Element> elements = createEmphasisElements(factory, AnimationType.TEETER);

        String[] expectedDelays = { "0", "200", "400", "600", "800" };
        for (int i = 0; i < 5; i++) {
            Element cBhvr = getChildByTag(elements.get(i), "p:cBhvr");
            Element cTn = getChildByTag(cBhvr, "p:cTn");

            // Extract delay from stCondLst if present, or "0" if absent
            Element stCondLst = getChildByTag(cTn, "p:stCondLst");
            String actualDelay;
            if (stCondLst != null) {
                Element cond = getChildByTag(stCondLst, "p:cond");
                actualDelay = cond.getAttribute("delay");
            } else {
                actualDelay = "0";
            }

            assertEquals("TEETER keyframe " + i + " delay",
                         expectedDelays[i], actualDelay);
        }
    }

    @Test
    public void testTeeterFirstKeyframeHasStCondLst() throws Exception {
        // Oracle pattern: even the first keyframe (delay=0) includes stCondLst.
        // createCommonBehavior skips stCondLst for delay="0", but TEETER needs it always.
        RotationAnimationFactory factory = WriterTestFixtures.configureFactory(new RotationAnimationFactory());
        List<Element> elements = createEmphasisElements(factory, AnimationType.TEETER);

        Element firstCBhvr = getChildByTag(elements.get(0), "p:cBhvr");
        Element firstCTn = getChildByTag(firstCBhvr, "p:cTn");
        Element stCondLst = getChildByTag(firstCTn, "p:stCondLst");

        assertNotNull("TEETER first keyframe must have stCondLst even for delay=0 (oracle pattern)",
                      stCondLst);
        Element cond = getChildByTag(stCondLst, "p:cond");
        assertEquals("0", cond.getAttribute("delay"));
    }

    // ========== REGISTRY INTEGRATION ==========

    @Test
    public void testRegistryResolvesSpinAndTeeter() {
        AnimationFactoryRegistry registry = new AnimationFactoryRegistry();
        assertNotNull(registry.getFactory(AnimationType.SPIN));
        assertNotNull(registry.getFactory(AnimationType.TEETER));
        assertEquals("RotationAnimationFactory",
                     registry.getFactory(AnimationType.TEETER).getClass().getSimpleName());
    }

    @Test
    public void testSupportsAnimationTypeRejectsUnrelated() {
        RotationAnimationFactory factory = WriterTestFixtures.configureFactory(new RotationAnimationFactory());
        assertFalse(factory.supportsAnimationType(AnimationType.FADE));
        assertFalse(factory.supportsAnimationType(AnimationType.GROW_TURN));
        assertFalse(factory.supportsAnimationType(AnimationType.GROW_SHRINK));
    }

    // ========== HELPERS ==========

    private List<Element> createEmphasisElements(RotationAnimationFactory factory, AnimationType type) throws Exception {
        Document doc = createTestDocument();
        AnimationBinding binding = AnimationBinding.builder()
            .type(type).target(3).emphasis().duration("1000").delay("0").animationGroup("on-click").build();
        return factory.createAnimationElements(doc, binding, null);
    }

    private List<Element> createEntranceElements(RotationAnimationFactory factory, AnimationType type) throws Exception {
        Document doc = createTestDocument();
        AnimationBinding binding = AnimationBinding.builder()
            .type(type).target(3).entrance().duration("500").delay("0").animationGroup("on-click").build();
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
