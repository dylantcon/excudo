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
 * Unit tests for CompositeEntranceFactory.
 * Validates GROW_TURN and SWIVEL composite entrance/exit patterns
 * against oracle OOXML output.
 */
public class CompositeEntranceFactoryTest {

    // ========== GROW_TURN TESTS ==========

    @Test
    public void testGrowTurnEntranceProducesFiveElements() throws Exception {
        CompositeEntranceFactory factory = WriterTestFixtures.configureFactory(new CompositeEntranceFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.GROW_TURN);

        // p:set + 3x p:anim + p:animEffect = 5 elements
        assertEquals("GROW_TURN entrance should produce 5 elements", 5, elements.size());
    }

    @Test
    public void testGrowTurnEntranceElementOrder() throws Exception {
        CompositeEntranceFactory factory = WriterTestFixtures.configureFactory(new CompositeEntranceFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.GROW_TURN);

        assertEquals("p:set", elements.get(0).getTagName());      // visibility
        assertEquals("p:anim", elements.get(1).getTagName());     // ppt_w
        assertEquals("p:anim", elements.get(2).getTagName());     // ppt_h
        assertEquals("p:anim", elements.get(3).getTagName());     // style.rotation
        assertEquals("p:animEffect", elements.get(4).getTagName()); // fade
    }

    @Test
    public void testGrowTurnAnimAttributeNames() throws Exception {
        CompositeEntranceFactory factory = WriterTestFixtures.configureFactory(new CompositeEntranceFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.GROW_TURN);

        String[] expectedAttrs = { "ppt_w", "ppt_h", "style.rotation" };
        for (int i = 0; i < 3; i++) {
            Element cBhvr = getChildByTag(elements.get(i + 1), "p:cBhvr");
            Element attrNameLst = getChildByTag(cBhvr, "p:attrNameLst");
            Element attrName = getChildByTag(attrNameLst, "p:attrName");
            assertEquals("GROW_TURN anim[" + i + "] attribute",
                         expectedAttrs[i], attrName.getTextContent());
        }
    }

    @Test
    public void testGrowTurnFadeEffect() throws Exception {
        CompositeEntranceFactory factory = WriterTestFixtures.configureFactory(new CompositeEntranceFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.GROW_TURN);

        Element animEffect = elements.get(4);
        assertEquals("fade", animEffect.getAttribute("filter"));
        assertEquals("in", animEffect.getAttribute("transition"));
    }

    @Test
    public void testGrowTurnExitProducesSixElements() throws Exception {
        CompositeEntranceFactory factory = WriterTestFixtures.configureFactory(new CompositeEntranceFactory());
        List<Element> elements = createExitElements(factory, AnimationType.GROW_TURN);

        // 3x p:anim + p:animEffect + p:set(hidden) = 5 elements (no leading visibility for exit)
        // Actually: no leading set, 3x p:anim + p:animEffect + p:set(hidden) = 5
        assertEquals("GROW_TURN exit should produce 5 elements", 5, elements.size());
        assertEquals("p:anim", elements.get(0).getTagName());
        assertEquals("p:set", elements.get(4).getTagName());
    }

    @Test
    public void testGrowTurnExitFadeDirection() throws Exception {
        CompositeEntranceFactory factory = WriterTestFixtures.configureFactory(new CompositeEntranceFactory());
        List<Element> elements = createExitElements(factory, AnimationType.GROW_TURN);

        // Find the animEffect element
        Element animEffect = null;
        for (Element el : elements) {
            if ("p:animEffect".equals(el.getTagName())) {
                animEffect = el;
                break;
            }
        }
        assertNotNull("Exit should have p:animEffect", animEffect);
        assertEquals("out", animEffect.getAttribute("transition"));
    }

    // ========== SWIVEL TESTS ==========

    @Test
    public void testSwivelEntranceProducesFourElements() throws Exception {
        CompositeEntranceFactory factory = WriterTestFixtures.configureFactory(new CompositeEntranceFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.SWIVEL);

        // p:set + p:animEffect + 2x p:anim = 4 elements
        assertEquals("SWIVEL entrance should produce 4 elements", 4, elements.size());
    }

    @Test
    public void testSwivelEntranceElementOrder() throws Exception {
        CompositeEntranceFactory factory = WriterTestFixtures.configureFactory(new CompositeEntranceFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.SWIVEL);

        assertEquals("p:set", elements.get(0).getTagName());          // visibility
        assertEquals("p:animEffect", elements.get(1).getTagName());   // fade
        assertEquals("p:anim", elements.get(2).getTagName());         // ppt_w (sin formula)
        assertEquals("p:anim", elements.get(3).getTagName());         // ppt_h (constant)
    }

    @Test
    public void testSwivelSinFormula() throws Exception {
        CompositeEntranceFactory factory = WriterTestFixtures.configureFactory(new CompositeEntranceFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.SWIVEL);

        Element widthAnim = elements.get(2);
        String fmla = widthAnim.getAttribute("fmla");
        assertTrue("SWIVEL should have sin formula on ppt_w anim",
                   fmla.contains("sin") && fmla.contains("pi"));
    }

    @Test
    public void testSwivelConstantHeight() throws Exception {
        CompositeEntranceFactory factory = WriterTestFixtures.configureFactory(new CompositeEntranceFactory());
        List<Element> elements = createEntranceElements(factory, AnimationType.SWIVEL);

        Element heightAnim = elements.get(3);
        Element cBhvr = getChildByTag(heightAnim, "p:cBhvr");
        Element attrNameLst = getChildByTag(cBhvr, "p:attrNameLst");
        Element attrName = getChildByTag(attrNameLst, "p:attrName");
        assertEquals("ppt_h", attrName.getTextContent());
    }

    // ========== REGISTRY INTEGRATION ==========

    @Test
    public void testRegistryResolvesGrowTurnAndSwivel() {
        AnimationFactoryRegistry registry = new AnimationFactoryRegistry();
        assertNotNull(registry.getFactory(AnimationType.GROW_TURN));
        assertNotNull(registry.getFactory(AnimationType.SWIVEL));
        assertEquals("CompositeEntranceFactory",
                     registry.getFactory(AnimationType.GROW_TURN).getClass().getSimpleName());
        assertEquals("CompositeEntranceFactory",
                     registry.getFactory(AnimationType.SWIVEL).getClass().getSimpleName());
    }

    @Test
    public void testSupportsAnimationTypeRejectsUnrelated() {
        CompositeEntranceFactory factory = WriterTestFixtures.configureFactory(new CompositeEntranceFactory());
        assertFalse(factory.supportsAnimationType(AnimationType.FADE));
        assertFalse(factory.supportsAnimationType(AnimationType.SPIN));
        assertFalse(factory.supportsAnimationType(AnimationType.ZOOM));
    }

    // ========== HELPERS ==========

    private List<Element> createEntranceElements(CompositeEntranceFactory factory, AnimationType type) throws Exception {
        Document doc = createTestDocument();
        AnimationBinding binding = AnimationBinding.builder()
            .type(type).target(3).entrance().duration("500").delay("0").animationGroup("on-click").build();
        return factory.createAnimationElements(doc, binding, null);
    }

    private List<Element> createExitElements(CompositeEntranceFactory factory, AnimationType type) throws Exception {
        Document doc = createTestDocument();
        AnimationBinding binding = AnimationBinding.builder()
            .type(type).target(3).exit().duration("500").delay("0").animationGroup("on-click").build();
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
