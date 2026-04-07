package com.excudo.integration;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import static org.junit.Assert.*;
import java.util.Set;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.animations.AnimationFactoryRegistry;
import com.excudo.core.animations.AnimationWriter;
import com.excudo.xml.writers.WriterTestFixtures;
import com.excudo.xml.writers.animations.AbstractAnimationFactory;

/**
 * Integration test for complete wipe animation creation pipeline.
 * This test traces the entire path from user input "wipe" to final OOXML generation
 * to identify exactly where WIPE_LEFT might be incorrectly handled as FADE.
 * 
 * Pipeline tested:
 * 1. String "wipe" -> AnimationType.parseType() -> WIPE_LEFT enum
 * 2. WIPE_LEFT -> AnimationBinding.Builder.type() -> AnimationBinding
 * 3. WIPE_LEFT -> AnimationFactoryRegistry.getFactory() -> WipeAnimationFactory  
 * 4. WipeAnimationFactory.createAnimation() -> OOXML with filter="wipe(left)"
 */
public class WipeAnimationCreationIntegrationTest {
    
    private Document document;
    private AnimationFactoryRegistry registry;
    
    @Before
    public void setUp() throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        document = dBuilder.newDocument();
        
        registry = new AnimationFactoryRegistry();
    }
    
    @Test
    public void testCompleteWipeAnimationCreationPipeline() throws Exception {
        // STEP 1: String parsing (simulating console input)
        String userInput = "wipe";
        AnimationType parsedType = AnimationType.parseType(userInput);
        
        assertEquals("Step 1: String 'wipe' should parse to WIPE_LEFT", 
                    AnimationType.WIPE_LEFT, parsedType);
        System.out.println("✓ Step 1: '" + userInput + "' -> " + parsedType);
        
        // STEP 2: AnimationBinding creation via Builder
        AnimationBinding binding = AnimationBinding.builder()
            .target(3)
            .type(userInput)  // Using string, not enum
            .entrance()
            .durationMs(500)
            .build();
        
        assertEquals("Step 2: Builder should create binding with WIPE_LEFT", 
                    AnimationType.WIPE_LEFT, binding.getAnimationType());
        System.out.println("✓ Step 2: Builder created binding with " + binding.getAnimationType());
        
        // STEP 3: Factory selection from registry
        AnimationWriter factory = registry.getFactory(binding.getAnimationType());
        if (factory instanceof AbstractAnimationFactory) {
            WriterTestFixtures.configureFactory((AbstractAnimationFactory) factory);
        }

        assertNotNull("Step 3: Registry should return factory for WIPE_LEFT", factory);
        assertEquals("Step 3: Should get WipeAnimationFactory", 
                    "WipeAnimationFactory", factory.getClass().getSimpleName());
        assertNotEquals("Step 3: Should NOT get FadeAnimationFactory", 
                       "FadeAnimationFactory", factory.getClass().getSimpleName());
        System.out.println("✓ Step 3: Registry returned " + factory.getClass().getSimpleName());
        
        // STEP 4: OOXML generation  
        ShapeGeometry geometry = new ShapeGeometry(0L, 0L, 1000L, 500L);
        List<Element> elements = factory.createAnimationElements(document, binding, geometry);
        
        assertNotNull("Step 4: Factory should generate elements", elements);
        assertFalse("Step 4: Should generate at least one element", elements.isEmpty());
        System.out.println("✓ Step 4: Factory generated OOXML structure");
        
        // STEP 5: Verify final OOXML has correct filter
        Element animEffect = findAnimEffectInList(elements);
        assertNotNull("Step 5: Should find p:animEffect in generated OOXML", animEffect);
        
        String filter = animEffect.getAttribute("filter");
        assertEquals("Step 5: Final OOXML should have 'wipe(left)' filter", 
                    "wipe(left)", filter);
        assertNotEquals("Step 5: Final OOXML should NOT have 'fade' filter", 
                       "fade", filter);
        System.out.println("✓ Step 5: Generated filter='" + filter + "'");
        
        String transition = animEffect.getAttribute("transition");
        assertEquals("Step 5: Should have 'in' transition for entrance", "in", transition);
        System.out.println("✓ Step 5: Generated transition='" + transition + "'");
    }
    
    @Test
    public void testPipelineWithDirectEnumInput() throws Exception {
        // Test the pipeline when using direct enum instead of string
        AnimationType directEnum = AnimationType.WIPE_LEFT;
        
        // Create binding with direct enum
        AnimationBinding binding = AnimationBinding.builder()
            .target(3)
            .type(directEnum)  // Direct enum, not string
            .entrance()
            .durationMs(500)
            .build();
        
        assertEquals("Direct enum should work in Builder", 
                    AnimationType.WIPE_LEFT, binding.getAnimationType());
        
        // Factory selection
        AnimationWriter factory = registry.getFactory(binding.getAnimationType());
        if (factory instanceof AbstractAnimationFactory) {
            WriterTestFixtures.configureFactory((AbstractAnimationFactory) factory);
        }
        assertEquals("Direct enum should get WipeAnimationFactory",
                    "WipeAnimationFactory", factory.getClass().getSimpleName());
        
        // OOXML generation
        ShapeGeometry geometry = new ShapeGeometry(0L, 0L, 1000L, 500L);
        List<Element> elements = factory.createAnimationElements(document, binding, geometry);
        Element animEffect = findAnimEffectInList(elements);
        
        assertEquals("Direct enum should generate wipe(left) filter", 
                    "wipe(left)", animEffect.getAttribute("filter"));
    }
    
    @Test
    public void testPipelineConsistencyStringVsEnum() throws Exception {
        // Test that string input and direct enum produce identical results
        String stringInput = "wipe";
        AnimationType enumInput = AnimationType.WIPE_LEFT;
        
        // Create bindings both ways
        AnimationBinding stringBinding = AnimationBinding.builder()
            .target(3).type(stringInput).entrance().durationMs(500).build();
            
        AnimationBinding enumBinding = AnimationBinding.builder()
            .target(3).type(enumInput).entrance().durationMs(500).build();
        
        // Both should have same animation type
        assertEquals("String and enum should produce same AnimationType",
                    stringBinding.getAnimationType(),
                    enumBinding.getAnimationType());
        
        // Both should get same factory
        AnimationWriter stringFactory = registry.getFactory(stringBinding.getAnimationType());
        if (stringFactory instanceof AbstractAnimationFactory) {
            WriterTestFixtures.configureFactory((AbstractAnimationFactory) stringFactory);
        }
        AnimationWriter enumFactory = registry.getFactory(enumBinding.getAnimationType());
        if (enumFactory instanceof AbstractAnimationFactory) {
            WriterTestFixtures.configureFactory((AbstractAnimationFactory) enumFactory);
        }

        assertEquals("String and enum should get same factory type",
                    stringFactory.getClass(),
                    enumFactory.getClass());
        
        // Both should generate same OOXML filter
        ShapeGeometry geometry = new ShapeGeometry(0L, 0L, 1000L, 500L);
        List<Element> stringElements = stringFactory.createAnimationElements(document, stringBinding, geometry);
        List<Element> enumElements = enumFactory.createAnimationElements(document, enumBinding, geometry);
        
        String stringFilter = findAnimEffectInList(stringElements).getAttribute("filter");
        String enumFilter = findAnimEffectInList(enumElements).getAttribute("filter");
        
        assertEquals("String and enum should generate same filter",
                    stringFilter, enumFilter);
        assertEquals("Both should generate wipe(left) filter", 
                    "wipe(left)", stringFilter);
    }
    
    @Test
    public void testAllWipeDirectionsPipeline() throws Exception {
        // Test complete pipeline for all wipe directions
        String[] userInputs = {"wipe-left", "wipe-right", "wipe-up", "wipe-down"};
        AnimationType[] expectedTypes = {
            AnimationType.WIPE_LEFT, AnimationType.WIPE_RIGHT,
            AnimationType.WIPE_UP, AnimationType.WIPE_DOWN
        };
        String[] expectedFilters = {"wipe(left)", "wipe(right)", "wipe(up)", "wipe(down)"};
        
        for (int i = 0; i < userInputs.length; i++) {
            // Full pipeline test for each direction
            String userInput = userInputs[i];
            AnimationType expectedType = expectedTypes[i];
            String expectedFilter = expectedFilters[i];
            
            // Parse string
            AnimationType parsedType = AnimationType.parseType(userInput);
            assertEquals("Parse " + userInput, expectedType, parsedType);
            
            // Create binding
            AnimationBinding binding = AnimationBinding.builder()
                .target(1).type(userInput).entrance().build();
            assertEquals("Binding for " + userInput, expectedType, binding.getAnimationType());
            
            // Get factory
            AnimationWriter factory = registry.getFactory(binding.getAnimationType());
            if (factory instanceof AbstractAnimationFactory) {
                WriterTestFixtures.configureFactory((AbstractAnimationFactory) factory);
            }
            assertEquals("Factory for " + userInput, "WipeAnimationFactory",
                        factory.getClass().getSimpleName());
            
            // Generate OOXML
            ShapeGeometry geometry = new ShapeGeometry(0L, 0L, 1000L, 500L);
            List<Element> elements = factory.createAnimationElements(document, binding, geometry);
            Element animEffect = findAnimEffectInList(elements);
            String actualFilter = animEffect.getAttribute("filter");
            
            assertEquals("Filter for " + userInput, expectedFilter, actualFilter);
            assertNotEquals("Should not be fade for " + userInput, "fade", actualFilter);
        }
    }
    
    @Test
    public void testPipelineFailureDetection() throws Exception {
        // This test is designed to FAIL if the pipeline is broken
        // It specifically checks for the reported issue: wipe -> fade
        
        String userInput = "wipe";
        
        // If the pipeline is broken, this might return FADE instead of WIPE_LEFT
        AnimationType parsedType = AnimationType.parseType(userInput);
        
        if (parsedType == AnimationType.FADE) {
            fail("PIPELINE BROKEN: String 'wipe' parsed as FADE instead of WIPE_LEFT. " +
                 "Issue is in AnimationType.parseType() method.");
        }
        
        // Create binding - if Builder has issue, might default to FADE
        AnimationBinding binding = AnimationBinding.builder()
            .target(1).type(userInput).entrance().build();
        
        if (binding.getAnimationType() == AnimationType.FADE) {
            fail("PIPELINE BROKEN: Builder converted 'wipe' to FADE. " +
                 "Issue is in AnimationBinding.Builder.type(String) method.");
        }
        
        // Get factory - if registry has issue, might return FadeAnimationFactory
        AnimationWriter factory = registry.getFactory(binding.getAnimationType());
        if (factory instanceof AbstractAnimationFactory) {
            WriterTestFixtures.configureFactory((AbstractAnimationFactory) factory);
        }

        if ("FadeAnimationFactory".equals(factory.getClass().getSimpleName())) {
            fail("PIPELINE BROKEN: Registry returned FadeAnimationFactory for WIPE_LEFT. " +
                 "Issue is in AnimationFactoryRegistry.getFactory() method.");
        }
        
        // Generate OOXML - if factory has issue, might generate fade filter
        ShapeGeometry geometry = new ShapeGeometry(0L, 0L, 1000L, 500L);
        List<Element> elements = factory.createAnimationElements(document, binding, geometry);
        Element animEffect = findAnimEffectInList(elements);
        String filter = animEffect.getAttribute("filter");
        
        if ("fade".equals(filter)) {
            fail("PIPELINE BROKEN: WipeAnimationFactory generated 'fade' filter. " +
                 "Issue is in WipeAnimationFactory.createAnimationEffect() method.");
        }
        
        // If we get here, pipeline is working correctly
        assertEquals("Pipeline working correctly", "wipe(left)", filter);
    }
    
    @Test
    public void testRegistryFactorySupport() {
        // Test that registry correctly identifies factory support
        assertTrue("Registry should support WIPE_LEFT", 
                  registry.canCreateAnimation(AnimationType.WIPE_LEFT));
        assertTrue("Registry should support FADE", 
                  registry.canCreateAnimation(AnimationType.FADE));
        assertTrue("Registry should support ZOOM", 
                  registry.canCreateAnimation(AnimationType.ZOOM));
        
        // Test supported types array
        Set<AnimationType> supportedTypeSet = registry.getSupportedAnimationTypes();
        AnimationType[] supportedTypes = supportedTypeSet.toArray(new AnimationType[0]);
        boolean hasWipeLeft = false;
        for (AnimationType type : supportedTypes) {
            if (type == AnimationType.WIPE_LEFT) {
                hasWipeLeft = true;
                break;
            }
        }
        assertTrue("Registry supported types should include WIPE_LEFT", hasWipeLeft);
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
}