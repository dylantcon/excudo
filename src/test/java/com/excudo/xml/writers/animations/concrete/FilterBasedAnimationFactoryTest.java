package com.excudo.xml.writers.animations.concrete;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.excudo.core.animations.AnimationFactoryRegistry;
import com.excudo.core.animations.AnimationWriter;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.xml.writers.WriterTestFixtures;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for FilterBasedAnimationFactory and its integration with AnimationFactoryRegistry.
 * Validates that all 12 filter-based animation types produce correct OOXML structure.
 */
public class FilterBasedAnimationFactoryTest {

    private static final AnimationType[] FILTER_TYPES = {
        AnimationType.BLINDS_HORIZONTAL,
        AnimationType.BLINDS_VERTICAL,
        AnimationType.SPLIT_HORIZONTAL,
        AnimationType.SPLIT_VERTICAL,
        AnimationType.CHECKERBOARD,
        AnimationType.BOX_IN,
        AnimationType.DIAMOND_IN,
        AnimationType.WHEEL_4,
        AnimationType.WHEEL_8,
        AnimationType.RANDOM_BARS_HORIZONTAL,
        AnimationType.RANDOM_BARS_VERTICAL,
        AnimationType.DISSOLVE
    };

    @Test
    public void testRegistryResolvesAllFilterTypes() {
        AnimationFactoryRegistry registry = new AnimationFactoryRegistry();
        for (AnimationType type : FILTER_TYPES) {
            AnimationWriter factory = registry.getFactory(type);
            assertNotNull("Registry should resolve factory for " + type.name(), factory);
            assertEquals("FilterBasedAnimationFactory", factory.getClass().getSimpleName());
        }
    }

    @Test
    public void testRegistryStillResolvesFadeAndWipe() {
        AnimationFactoryRegistry registry = new AnimationFactoryRegistry();
        assertNotNull(registry.getFactory(AnimationType.FADE));
        assertEquals("FadeAnimationFactory", registry.getFactory(AnimationType.FADE).getClass().getSimpleName());
        assertNotNull(registry.getFactory(AnimationType.WIPE_LEFT));
        assertEquals("WipeAnimationFactory", registry.getFactory(AnimationType.WIPE_LEFT).getClass().getSimpleName());
    }

    @Test
    public void testSupportedAnimationTypeCount() {
        AnimationFactoryRegistry registry = new AnimationFactoryRegistry();
        // 5 original + 12 filter-based + RotationFactory(SPIN,TEETER=2) + ScaleFactory(GROW_SHRINK=1)
        // + ColorFactory(DARKEN,DESATURATE,LIGHTEN,COLOR_PULSE=4) + EmphasisFactory(TRANSPARENCY,PULSE=2)
        // + CompositeFactory(GROW_TURN,SWIVEL=2) + MotionPathFactory(LINEAR,ARC,CUSTOM=3) = 40
        // Note: FLICKER and SPIRAL removed (phantom types not in PowerPoint oracle)
        int expectedMinimum = 40;
        assertTrue("Registry should support at least " + expectedMinimum + " types, got " + registry.getSupportedAnimationTypes().size(),
                   registry.getSupportedAnimationTypes().size() >= expectedMinimum);
    }

    @Test
    public void testCreateAnimationElementsProducesValidStructure() throws Exception {
        FilterBasedAnimationFactory factory = WriterTestFixtures.configureFactory(new FilterBasedAnimationFactory());
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        for (AnimationType type : FILTER_TYPES) {
            AnimationBinding binding = AnimationBinding.builder()
                .type(type)
                .target(2)
                .entrance()
                .duration("500")
                .delay("0")
                .animationGroup("on-click")
                .build();

            List<Element> elements = factory.createAnimationElements(doc, binding, null);

            // Entrance animations: p:set (visibility) + p:animEffect
            assertEquals("Expected 2 elements for entrance " + type.name(), 2, elements.size());

            Element visSet = elements.get(0);
            assertEquals("First element should be p:set for " + type.name(), "p:set", visSet.getTagName());

            Element animEffect = elements.get(1);
            assertEquals("Second element should be p:animEffect for " + type.name(), "p:animEffect", animEffect.getTagName());

            String filter = animEffect.getAttribute("filter");
            assertEquals("Filter should match PowerPoint filter for " + type.name(),
                         type.getPowerPointFilter(), filter);

            assertEquals("Transition should be 'in' for entrance " + type.name(),
                         "in", animEffect.getAttribute("transition"));
        }
    }

    @Test
    public void testExitAnimationsOmitVisibilitySetBeforeEffect() throws Exception {
        FilterBasedAnimationFactory factory = WriterTestFixtures.configureFactory(new FilterBasedAnimationFactory());
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        AnimationBinding exitBinding = AnimationBinding.builder()
            .type(AnimationType.BLINDS_HORIZONTAL)
            .target(2)
            .exit()
            .duration("500")
            .delay("0")
            .animationGroup("on-click")
            .build();

        List<Element> elements = factory.createAnimationElements(doc, exitBinding, null);

        // Exit: p:animEffect + p:set (visibility hidden)
        assertEquals("Expected 2 elements for exit animation", 2, elements.size());

        Element animEffect = elements.get(0);
        assertEquals("First element should be p:animEffect for exit", "p:animEffect", animEffect.getTagName());
        assertEquals("out", animEffect.getAttribute("transition"));

        Element visSet = elements.get(1);
        assertEquals("Second element should be p:set for exit", "p:set", visSet.getTagName());
    }

    @Test
    public void testFilterStringsMatchAnimationType() {
        for (AnimationType type : FILTER_TYPES) {
            String filter = type.getPowerPointFilter();
            assertNotNull("Filter should not be null for " + type.name(), filter);
            assertFalse("Filter should not be empty for " + type.name(), filter.isEmpty());
        }
    }

    @Test
    public void testSupportsAnimationTypeRejectsUnrelated() {
        FilterBasedAnimationFactory factory = WriterTestFixtures.configureFactory(new FilterBasedAnimationFactory());
        assertFalse(factory.supportsAnimationType(AnimationType.FADE));
        assertFalse(factory.supportsAnimationType(AnimationType.APPEAR));
        assertFalse(factory.supportsAnimationType(AnimationType.FLY_IN_LEFT));
        assertFalse(factory.supportsAnimationType(AnimationType.ZOOM));
        assertFalse(factory.supportsAnimationType(AnimationType.SPIN));
    }
}
