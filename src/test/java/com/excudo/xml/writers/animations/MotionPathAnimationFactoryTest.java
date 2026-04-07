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
 * Unit tests for MotionPathAnimationFactory.
 * Validates MOTION_LINEAR, MOTION_ARC, and MOTION_CUSTOM patterns
 * against oracle OOXML output (slides 129, 133, 154).
 */
public class MotionPathAnimationFactoryTest {

    // ========== MOTION_LINEAR TESTS ==========

    @Test
    public void testLinearProducesAnimMotion() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.MOTION_LINEAR);

        // p:set (visibility) + p:animMotion = 2 elements
        assertEquals(2, elements.size());
        assertEquals("p:set", elements.get(0).getTagName());
        assertEquals("p:animMotion", elements.get(1).getTagName());
    }

    @Test
    public void testLinearPathContainsLine() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.MOTION_LINEAR);

        Element animMotion = elements.get(1);
        String path = animMotion.getAttribute("path");
        assertTrue("Linear path should start with M", path.startsWith("M"));
        assertTrue("Linear path should contain L (line)", path.contains("L"));
    }

    @Test
    public void testLinearHasOracleAttributes() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.MOTION_LINEAR);

        Element animMotion = elements.get(1);
        assertEquals("layout", animMotion.getAttribute("origin"));
        assertEquals("relative", animMotion.getAttribute("pathEditMode"));
        assertEquals("0", animMotion.getAttribute("rAng"));
    }

    @Test
    public void testLinearUsesOracleDefaultPath() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.MOTION_LINEAR);

        Element animMotion = elements.get(1);
        String path = animMotion.getAttribute("path");
        // Oracle slide 129: Line Down path
        assertEquals("M -1.25E-6 3.33333E-6 L -1.25E-6 0.25 ", path);
    }

    // ========== MOTION_ARC TESTS ==========

    @Test
    public void testArcProducesAnimMotion() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.MOTION_ARC);

        assertEquals(2, elements.size());
        assertEquals("p:animMotion", elements.get(1).getTagName());
    }

    @Test
    public void testArcPathContainsCurve() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.MOTION_ARC);

        Element animMotion = elements.get(1);
        String path = animMotion.getAttribute("path");
        assertTrue("Arc path should contain C (curve)", path.contains("C"));
    }

    @Test
    public void testArcRCtrHasOracleValues() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.MOTION_ARC);

        Element animMotion = elements.get(1);
        Element rCtr = getChildByTag(animMotion, "p:rCtr");
        assertNotNull("Arc should have p:rCtr", rCtr);
        assertEquals("12500", rCtr.getAttribute("x"));
        assertEquals("2685", rCtr.getAttribute("y"));
    }

    // ========== MOTION_CUSTOM TESTS ==========

    @Test
    public void testCustomUsesBindingMotionPath() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        String customPath = "M 0 0 L 0.5 -0.5 L 1.0 0";

        Document doc = createTestDocument();
        AnimationBinding binding = AnimationBinding.builder()
            .type(AnimationType.MOTION_CUSTOM).target(3).entrance()
            .duration("500").delay("0").animationGroup("on-click")
            .motionPath(customPath)
            .build();

        List<Element> elements = factory.createAnimationElements(doc, binding, null);
        Element animMotion = elements.get(1);

        assertEquals("Custom motion should use binding's path",
                     customPath, animMotion.getAttribute("path"));
    }

    @Test
    public void testCustomFallsBackWhenNoPathProvided() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());

        Document doc = createTestDocument();
        AnimationBinding binding = AnimationBinding.builder()
            .type(AnimationType.MOTION_CUSTOM).target(3).entrance()
            .duration("500").delay("0").animationGroup("on-click")
            .build();

        List<Element> elements = factory.createAnimationElements(doc, binding, null);
        Element animMotion = elements.get(1);

        String path = animMotion.getAttribute("path");
        assertNotNull("Should have fallback path", path);
        assertFalse("Fallback path should not be empty", path.isEmpty());
    }

    @Test
    public void testCustomPathHasNoRCtr() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());

        Document doc = createTestDocument();
        AnimationBinding binding = AnimationBinding.builder()
            .type(AnimationType.MOTION_CUSTOM).target(3).entrance()
            .duration("500").delay("0").animationGroup("on-click")
            .motionPath("M 0 0 L 0.3 -0.2 L 0.5 0.1")
            .build();

        List<Element> elements = factory.createAnimationElements(doc, binding, null);
        Element animMotion = elements.get(1);
        Element rCtr = getChildByTag(animMotion, "p:rCtr");
        assertNull("MOTION_CUSTOM should have no p:rCtr (oracle slide 154)", rCtr);
    }

    @Test
    public void testCustomPathHasNoRAng() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());

        Document doc = createTestDocument();
        AnimationBinding binding = AnimationBinding.builder()
            .type(AnimationType.MOTION_CUSTOM).target(3).entrance()
            .duration("500").delay("0").animationGroup("on-click")
            .motionPath("M 0 0 L 0.3 -0.2")
            .build();

        List<Element> elements = factory.createAnimationElements(doc, binding, null);
        Element animMotion = elements.get(1);
        assertEquals("MOTION_CUSTOM should have no rAng attribute (oracle slide 154)",
                     "", animMotion.getAttribute("rAng"));
    }

    // ========== STRUCTURAL TESTS ==========

    @Test
    public void testLinearRCtrHasOracleValues() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.MOTION_LINEAR);

        Element animMotion = elements.get(1);
        Element rCtr = getChildByTag(animMotion, "p:rCtr");
        assertNotNull("MOTION_LINEAR should have p:rCtr child element", rCtr);
        assertEquals("0", rCtr.getAttribute("x"));
        assertEquals("12500", rCtr.getAttribute("y"));
    }

    @Test
    public void testPtsTypesAllAFormat() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.MOTION_LINEAR);

        Element animMotion = elements.get(1);
        String ptsTypes = animMotion.getAttribute("ptsTypes");
        assertNotNull("Should have ptsTypes attribute", ptsTypes);
        assertFalse("ptsTypes should not be empty", ptsTypes.isEmpty());
        // Oracle: "AA" for linear (M + L = 2 commands)
        assertEquals("AA", ptsTypes);
        // No "1" or "E" characters in oracle ptsTypes format
        assertFalse("ptsTypes should not contain '1'", ptsTypes.contains("1"));
        assertFalse("ptsTypes should not contain 'E'", ptsTypes.contains("E"));
    }

    @Test
    public void testArcPtsTypesMatchesOracle() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.MOTION_ARC);

        Element animMotion = elements.get(1);
        String ptsTypes = animMotion.getAttribute("ptsTypes");
        // Oracle slide 133 (Arc Down): M..L..C..C..L.. = "AAAAA"
        assertEquals("AAAAA", ptsTypes);
    }

    @Test
    public void testNoAdditiveBaseOnMotionPath() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.MOTION_LINEAR);

        Element animMotion = elements.get(1);
        Element cBhvr = getChildByTag(animMotion, "p:cBhvr");
        assertNotNull("Should have cBhvr", cBhvr);
        // Oracle doesn't have additive="base" on motion paths
        assertEquals("Motion path cBhvr should not have additive=base",
                     "", cBhvr.getAttribute("additive"));
    }

    @Test
    public void testOuterCtnHasPathPresetClass() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());

        Document doc = createTestDocument();
        AnimationBinding binding = AnimationBinding.builder()
            .type(AnimationType.MOTION_LINEAR).target(3).entrance()
            .duration("500").delay("0").animationGroup("on-click")
            .build();

        Element par = factory.createTimingContainer(doc, binding);
        Element cTn = getChildByTag(par, "p:cTn");
        assertNotNull("Should have cTn", cTn);
        assertEquals("path", cTn.getAttribute("presetClass"));
    }

    @Test
    public void testOuterCtnHasAccelDecel() throws Exception {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());

        Document doc = createTestDocument();
        AnimationBinding binding = AnimationBinding.builder()
            .type(AnimationType.MOTION_LINEAR).target(3).entrance()
            .duration("500").delay("0").animationGroup("on-click")
            .build();

        Element par = factory.createTimingContainer(doc, binding);
        Element cTn = getChildByTag(par, "p:cTn");
        assertNotNull("Should have cTn", cTn);
        assertEquals("50000", cTn.getAttribute("accel"));
        assertEquals("50000", cTn.getAttribute("decel"));
    }

    // ========== REGISTRY INTEGRATION ==========

    @Test
    public void testRegistryResolvesAllThreeMotionTypes() {
        AnimationFactoryRegistry registry = new AnimationFactoryRegistry();
        for (AnimationType type : new AnimationType[]{
                AnimationType.MOTION_LINEAR, AnimationType.MOTION_ARC, AnimationType.MOTION_CUSTOM}) {
            assertNotNull("Registry should resolve " + type, registry.getFactory(type));
            assertEquals("MotionPathAnimationFactory",
                         registry.getFactory(type).getClass().getSimpleName());
        }
    }

    @Test
    public void testSupportsAnimationTypeRejectsUnrelated() {
        MotionPathAnimationFactory factory = WriterTestFixtures.configureFactory(new MotionPathAnimationFactory());
        assertFalse(factory.supportsAnimationType(AnimationType.FADE));
        assertFalse(factory.supportsAnimationType(AnimationType.SPIN));
        assertFalse(factory.supportsAnimationType(AnimationType.GROW_TURN));
    }

    // ========== HELPERS ==========

    private List<Element> createEntranceElements(MotionPathAnimationFactory factory, AnimationType type) throws Exception {
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
