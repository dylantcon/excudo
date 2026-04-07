package com.excudo.core.model;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for AnimationBinding.Builder type conversion functionality.
 * This tests the critical pipeline step where string animation types from user input
 * get converted to AnimationType enums via the Builder pattern.
 */
public class AnimationBindingBuilderTest {
    
    private AnimationBinding.Builder builder;
    
    @Before
    public void setUp() {
        builder = AnimationBinding.builder();
    }
    
    @Test
    public void testTypeStringConversionForWipe() {
        // This is the critical test - "wipe" string should become WIPE_LEFT
        AnimationBinding binding = builder
            .target(1)
            .type("wipe")  // String -> AnimationType conversion
            .build();
        
        assertEquals("String 'wipe' should convert to WIPE_LEFT", 
                    AnimationType.WIPE_LEFT, 
                    binding.getAnimationType());
        
        // Verify it's not converted to FADE
        assertNotEquals("'wipe' should not convert to FADE", 
                       AnimationType.FADE, 
                       binding.getAnimationType());
    }
    
    @Test
    public void testTypeStringConversionForWipeDirections() {
        // Test all wipe directions work correctly through Builder
        AnimationType[] expectedTypes = {
            AnimationType.WIPE_LEFT,
            AnimationType.WIPE_RIGHT,
            AnimationType.WIPE_UP,
            AnimationType.WIPE_DOWN
        };
        
        String[] inputStrings = {
            "wipe-left",
            "wipe-right", 
            "wipe-up",
            "wipe-down"
        };
        
        for (int i = 0; i < inputStrings.length; i++) {
            AnimationBinding binding = AnimationBinding.builder()
                .target(1)
                .type(inputStrings[i])
                .build();
            
            assertEquals("String '" + inputStrings[i] + "' should convert to " + expectedTypes[i], 
                        expectedTypes[i], 
                        binding.getAnimationType());
        }
    }
    
    @Test
    public void testTypeEnumDirectAssignment() {
        // Test that direct enum assignment works correctly
        AnimationBinding binding = builder
            .target(1)
            .type(AnimationType.WIPE_LEFT)  // Direct enum assignment
            .build();
        
        assertEquals("Direct enum assignment should work", 
                    AnimationType.WIPE_LEFT, 
                    binding.getAnimationType());
    }
    
    @Test
    public void testTypeConversionConsistency() {
        // String and enum should produce identical bindings
        AnimationBinding stringBinding = AnimationBinding.builder()
            .target(1)
            .type("wipe")
            .build();
            
        AnimationBinding enumBinding = AnimationBinding.builder()
            .target(1) 
            .type(AnimationType.WIPE_LEFT)
            .build();
        
        assertEquals("String and enum conversion should be consistent",
                    stringBinding.getAnimationType(),
                    enumBinding.getAnimationType());
    }
    
    @Test
    public void testFadeStringConversion() {
        AnimationBinding binding = builder
            .target(1)
            .type("fade")
            .build();
        
        assertEquals("String 'fade' should convert to FADE", 
                    AnimationType.FADE, 
                    binding.getAnimationType());
    }
    
    @Test
    public void testZoomStringConversion() {
        AnimationBinding binding = builder
            .target(1)
            .type("zoom")
            .build();
        
        assertEquals("String 'zoom' should convert to ZOOM", 
                    AnimationType.ZOOM, 
                    binding.getAnimationType());
    }
    
    @Test
    public void testCaseInsensitiveStringConversion() {
        // Test case insensitive parsing works through Builder
        String[] variants = {"wipe", "WIPE", "Wipe", "wIpE"};
        
        for (String variant : variants) {
            AnimationBinding binding = AnimationBinding.builder()
                .target(1)
                .type(variant)
                .build();
            
            assertEquals("Case insensitive '" + variant + "' should convert to WIPE_LEFT", 
                        AnimationType.WIPE_LEFT, 
                        binding.getAnimationType());
        }
    }
    
    @Test
    public void testUnknownStringDefaultsToFade() {
        // Test that unknown strings default to FADE
        AnimationBinding binding = builder
            .target(1)
            .type("unknown-animation-type")
            .build();
        
        assertEquals("Unknown string should default to FADE", 
                    AnimationType.FADE, 
                    binding.getAnimationType());
    }
    
    @Test
    public void testNullStringDefaultsToFade() {
        // Test that null string defaults to FADE
        AnimationBinding binding = builder
            .target(1)
            .type((String) null)
            .build();
        
        assertEquals("Null string should default to FADE", 
                    AnimationType.FADE, 
                    binding.getAnimationType());
    }
    
    @Test
    public void testDefaultAnimationTypeIsFade() {
        // Test that builder defaults to FADE when no type is set
        AnimationBinding binding = builder
            .target(1)
            .build();
        
        assertEquals("Default animation type should be FADE", 
                    AnimationType.FADE, 
                    binding.getAnimationType());
    }
    
    @Test
    public void testBuilderChaining() {
        // Test that type() method returns builder for chaining
        AnimationBinding binding = builder
            .target(1)
            .type("wipe")
            .entrance()
            .durationMs(750)
            .clickTrigger(2)
            .build();
        
        assertEquals("Chained type should work correctly", 
                    AnimationType.WIPE_LEFT, 
                    binding.getAnimationType());
        assertEquals("Chained properties should be set", "in", binding.getTransition());
        assertEquals("Chained properties should be set", "750", binding.getDuration());
        assertEquals("Chained properties should be set", 2, binding.getClickTrigger());
    }
    
    @Test
    public void testBuilderTypeOverride() {
        // Test that last type() call wins
        AnimationBinding binding = builder
            .target(1)
            .type("fade")
            .type("wipe")  // This should override fade
            .build();
        
        assertEquals("Last type() call should win", 
                    AnimationType.WIPE_LEFT, 
                    binding.getAnimationType());
    }
    
    @Test
    public void testStringToEnumToBuilderRoundTrip() {
        // Test that string -> enum -> builder -> binding preserves type
        String originalString = "wipe";
        AnimationType parsedEnum = AnimationType.parseType(originalString);
        
        AnimationBinding binding = AnimationBinding.builder()
            .target(1)
            .type(parsedEnum)
            .build();
        
        assertEquals("Round trip should preserve animation type",
                    AnimationType.WIPE_LEFT,
                    binding.getAnimationType());
        assertEquals("Round trip should preserve user-friendly name",
                    "wipe-left",
                    binding.getAnimationType().getUserFriendlyName());
    }
    
    @Test
    public void testPowerPointFilterFromBuilderType() {
        // Test that PowerPoint filter is correct after string conversion
        AnimationBinding wipeBinding = builder
            .target(1)
            .type("wipe")
            .build();
        
        // This should use the AnimationType's filter, not any hardcoded value
        String filter = wipeBinding.getFilter();
        
        assertNotNull("Filter should not be null", filter);
        assertTrue("Wipe filter should contain direction", 
                  filter.contains("left") || filter.equals("wipe") || filter.equals("wipe(left)") || filter.equals("wipe(fromLeft)"));
        
        // Most importantly - it should NOT be a fade filter
        assertFalse("Wipe should not have fade filter", filter.equals("fade"));
    }
}