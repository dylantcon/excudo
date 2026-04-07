package com.excudo.xml.writers.animations.concrete;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import static org.junit.Assert.*;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.xml.writers.WriterTestFixtures;

/**
 * Unit tests for WipeAnimationFactory OOXML generation.
 * This tests the critical step where WIPE_LEFT animations generate the correct
 * OOXML structure with "wipe(left)" filter, not "fade" filter.
 */
public class WipeAnimationFactoryTest {
    
    private WipeAnimationFactory factory;
    private Document document;
    private ShapeGeometry testGeometry;
    
    @Before
    public void setUp() throws Exception {
        factory = WriterTestFixtures.configureFactory(new WipeAnimationFactory());
        
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        document = dBuilder.newDocument();
        
        // Create test geometry for animation elements
        testGeometry = new ShapeGeometry(0L, 0L, 1000L, 500L);
    }
    
    @Test
    public void testSupportsWipeAnimationTypes() {
        // Test that factory supports all wipe types
        assertTrue("Should support WIPE_LEFT", factory.supportsAnimationType(AnimationType.WIPE_LEFT));
        assertTrue("Should support WIPE_RIGHT", factory.supportsAnimationType(AnimationType.WIPE_RIGHT));
        assertTrue("Should support WIPE_UP", factory.supportsAnimationType(AnimationType.WIPE_UP));
        assertTrue("Should support WIPE_DOWN", factory.supportsAnimationType(AnimationType.WIPE_DOWN));
        
        // Test that factory does NOT support other types
        assertFalse("Should NOT support FADE", factory.supportsAnimationType(AnimationType.FADE));
        assertFalse("Should NOT support ZOOM", factory.supportsAnimationType(AnimationType.ZOOM));
        assertFalse("Should NOT support APPEAR", factory.supportsAnimationType(AnimationType.APPEAR));
        assertFalse("Should NOT support FLY_IN_LEFT", factory.supportsAnimationType(AnimationType.FLY_IN_LEFT));
    }
    
    @Test
    public void testGetSupportedAnimationTypes() {
        AnimationType[] supportedTypes = factory.getSupportedAnimationTypes();
        
        assertNotNull("Supported types should not be null", supportedTypes);
        assertEquals("Should support exactly 4 wipe types", 4, supportedTypes.length);
        
        // Verify all wipe types are present
        boolean hasWipeLeft = false, hasWipeRight = false, hasWipeUp = false, hasWipeDown = false;
        for (AnimationType type : supportedTypes) {
            if (type == AnimationType.WIPE_LEFT) hasWipeLeft = true;
            if (type == AnimationType.WIPE_RIGHT) hasWipeRight = true;
            if (type == AnimationType.WIPE_UP) hasWipeUp = true;
            if (type == AnimationType.WIPE_DOWN) hasWipeDown = true;
        }
        
        assertTrue("Should include WIPE_LEFT", hasWipeLeft);
        assertTrue("Should include WIPE_RIGHT", hasWipeRight);
        assertTrue("Should include WIPE_UP", hasWipeUp);
        assertTrue("Should include WIPE_DOWN", hasWipeDown);
    }
    
    @Test
    public void testWipeLeftEntranceGeneration() throws Exception {
        // Create WIPE_LEFT entrance animation binding
        AnimationBinding binding = AnimationBinding.builder()
            .target(3)
            .type(AnimationType.WIPE_LEFT)
            .entrance()
            .durationMs(500)
            .build();
        
        // Generate animation elements using the factory
        List<Element> elements = factory.createAnimationElements(document, binding, testGeometry);
        
        assertNotNull("Generated elements should not be null", elements);
        assertFalse("Should generate at least one element", elements.isEmpty());
        
        // Find the p:animEffect element in the list
        Element animEffect = findAnimEffectInList(elements);
        assertNotNull("Should contain p:animEffect element", animEffect);
        
        // Verify critical attributes
        String filter = animEffect.getAttribute("filter");
        String transition = animEffect.getAttribute("transition");
        
        assertEquals("WIPE_LEFT should generate 'wipe(left)' filter", "wipe(left)", filter);
        assertEquals("Entrance should generate 'in' transition", "in", transition);
        
        // Most importantly - verify it's NOT generating fade filter
        assertNotEquals("Should NOT generate fade filter for wipe", "fade", filter);
    }
    
    @Test
    public void testWipeLeftExitGeneration() throws Exception {
        // Test WIPE_LEFT exit animation
        AnimationBinding binding = AnimationBinding.builder()
            .target(5)
            .type(AnimationType.WIPE_LEFT)
            .exit()
            .durationMs(750)
            .build();
        
        List<Element> elements = factory.createAnimationElements(document, binding, testGeometry);
        Element animEffect = findAnimEffectInList(elements);
        
        assertNotNull("Exit animation should generate animEffect", animEffect);
        assertEquals("Exit wipe should have 'wipe(left)' filter", "wipe(left)", animEffect.getAttribute("filter"));
        assertEquals("Exit should have 'out' transition", "out", animEffect.getAttribute("transition"));
    }
    
    @Test
    public void testAllWipeDirectionsGenerateCorrectFilters() throws Exception {
        // Test all wipe directions generate correct filters
        AnimationType[] wipeTypes = {
            AnimationType.WIPE_LEFT,
            AnimationType.WIPE_RIGHT,
            AnimationType.WIPE_UP,
            AnimationType.WIPE_DOWN
        };
        
        String[] expectedFilters = {
            "wipe(left)",
            "wipe(right)",
            "wipe(up)",
            "wipe(down)"
        };
        
        for (int i = 0; i < wipeTypes.length; i++) {
            AnimationBinding binding = AnimationBinding.builder()
                .target(1)
                .type(wipeTypes[i])
                .entrance()
                .build();
            
            List<Element> elements = factory.createAnimationElements(document, binding, testGeometry);
            Element animEffect = findAnimEffectInList(elements);
            
            assertNotNull("Should generate animEffect for " + wipeTypes[i], animEffect);
            assertEquals("Should generate correct filter for " + wipeTypes[i], 
                        expectedFilters[i], 
                        animEffect.getAttribute("filter"));
        }
    }
    
    @Test
    public void testWipeFactoryCommonBehaviorGeneration() throws Exception {
        AnimationBinding binding = AnimationBinding.builder()
            .target(7)
            .type(AnimationType.WIPE_LEFT)
            .entrance()
            .durationMs(1000)
            .build();
        
        List<Element> elements = factory.createAnimationElements(document, binding, testGeometry);
        Element animEffect = findAnimEffectInList(elements);
        
        // Verify p:cBhvr (common behavior) is generated
        Element cBhvr = findElement(animEffect, "p:cBhvr");
        assertNotNull("Should generate p:cBhvr element", cBhvr);
        
        // Verify p:cTn (common timing node) with duration
        Element cTn = findElement(cBhvr, "p:cTn");
        assertNotNull("Should generate p:cTn element", cTn);
        
        String duration = cTn.getAttribute("dur");
        assertEquals("Should set correct duration", "1000", duration);
        
        // Verify p:tgtEl (target element) is generated
        Element tgtEl = findElement(cBhvr, "p:tgtEl");
        assertNotNull("Should generate p:tgtEl element", tgtEl);
        
        // Verify p:spTgt (shape target) with correct SPID
        Element spTgt = findElement(tgtEl, "p:spTgt");
        assertNotNull("Should generate p:spTgt element", spTgt);
        assertEquals("Should target correct SPID", "7", spTgt.getAttribute("spid"));
    }
    
    @Test
    public void testWipeFactoryDoesNotGenerateFadeFilter() throws Exception {
        // Critical test: ensure wipe factory never generates fade filter
        AnimationType[] allWipeTypes = {
            AnimationType.WIPE_LEFT, AnimationType.WIPE_RIGHT, 
            AnimationType.WIPE_UP, AnimationType.WIPE_DOWN
        };
        
        for (AnimationType wipeType : allWipeTypes) {
            AnimationBinding binding = AnimationBinding.builder()
                .target(1)
                .type(wipeType)
                .entrance()
                .build();
            
            List<Element> elements = factory.createAnimationElements(document, binding, testGeometry);
            Element animEffect = findAnimEffectInList(elements);
            
            String filter = animEffect.getAttribute("filter");
            assertNotEquals("Wipe type " + wipeType + " should never generate fade filter", 
                           "fade", filter);
            assertTrue("Wipe type " + wipeType + " should generate wipe filter", 
                      filter.startsWith("wipe"));
        }
    }
    
    @Test
    public void testMatchesNativePowerPointStructure() throws Exception {
        // Test that generated structure matches native PowerPoint from timing dump
        // Based on slide_022_Wipe_In_From_Left.xml analysis
        AnimationBinding binding = AnimationBinding.builder()
            .target(3)
            .type(AnimationType.WIPE_LEFT)
            .entrance()
            .durationMs(500)
            .build();
        
        List<Element> elements = factory.createAnimationElements(document, binding, testGeometry);
        Element animEffect = findAnimEffectInList(elements);
        
        // Verify matches native PowerPoint structure:
        // <p:animEffect filter="wipe(left)" transition="in">
        assertEquals("Should match native filter", "wipe(left)", animEffect.getAttribute("filter"));
        assertEquals("Should match native transition", "in", animEffect.getAttribute("transition"));
        
        // Verify duration in cTn matches native (500ms)
        Element cBhvr = findElement(animEffect, "p:cBhvr");
        Element cTn = findElement(cBhvr, "p:cTn");
        assertEquals("Should match native duration", "500", cTn.getAttribute("dur"));
    }
    
    @Test
    public void testFactoryOoxmlPattern() {
        // Test the pattern description for documentation
        String pattern = factory.getOoxmlPattern();
        assertNotNull("OOXML pattern should not be null", pattern);
        assertTrue("Pattern should mention wipe", pattern.toLowerCase().contains("wipe"));
        assertTrue("Pattern should mention direction", pattern.toLowerCase().contains("direction"));
    }
    
    @Test
    public void testTimingContainerGeneration() throws Exception {
        AnimationBinding binding = AnimationBinding.builder()
            .target(1)
            .type(AnimationType.WIPE_LEFT)
            .entrance()
            .build();
        
        Element timing = factory.createTimingContainer(document, binding);
        
        assertNotNull("Timing container should not be null", timing);
        assertEquals("Should create p:par element", "p:par", timing.getTagName());
    }
    
    @Test
    public void testTargetElementGeneration() throws Exception {
        AnimationBinding binding = AnimationBinding.builder()
            .target(42)
            .type(AnimationType.WIPE_LEFT)
            .entrance()
            .build();
        
        Element tgtEl = factory.createTargetElement(document, binding);
        
        assertNotNull("Target element should not be null", tgtEl);
        assertEquals("Should create p:tgtEl element", "p:tgtEl", tgtEl.getTagName());
        
        // Find spTgt with correct SPID
        Element spTgt = findElement(tgtEl, "p:spTgt");
        assertNotNull("Should contain p:spTgt", spTgt);
        assertEquals("Should target correct SPID", "42", spTgt.getAttribute("spid"));
    }
    
    @Test
    public void testBuildListEntryGeneration() throws Exception {
        AnimationBinding binding = AnimationBinding.builder()
            .target(99)
            .type(AnimationType.WIPE_LEFT)
            .entrance()
            .build();
        
        Element bldP = factory.createBuildListEntry(document, binding);
        
        assertNotNull("Build list entry should not be null", bldP);
        assertEquals("Should create p:bldP element", "p:bldP", bldP.getTagName());
        assertEquals("Should reference correct SPID", "99", bldP.getAttribute("spid"));
    }
    
    /**
     * Helper method to find p:animEffect element in a list of elements.
     */
    private Element findAnimEffectInList(List<Element> elements) {
        for (Element element : elements) {
            if ("p:animEffect".equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }
    
    /**
     * Helper method to find any element by tag name in the tree.
     */
    private Element findElement(Element parent, String tagName) {
        if (parent.getTagName().equals(tagName)) {
            return parent;
        }
        
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            if (parent.getChildNodes().item(i) instanceof Element) {
                Element child = (Element) parent.getChildNodes().item(i);
                Element found = findElement(child, tagName);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }
}