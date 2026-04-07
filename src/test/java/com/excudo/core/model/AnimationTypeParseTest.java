package com.excudo.core.model;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for AnimationType.parseType method to verify correct string-to-enum parsing.
 * This is a critical component in the animation creation pipeline that maps user input
 * to specific animation types.
 */
public class AnimationTypeParseTest {
    
    @Test
    public void testParseWipeAnimations() {
        // Test basic "wipe" maps to WIPE_LEFT (default direction)
        assertEquals("Basic 'wipe' should map to WIPE_LEFT", 
                    AnimationType.WIPE_LEFT, 
                    AnimationType.parseType("wipe"));
        
        // Test case insensitive
        assertEquals("Case insensitive 'WIPE' should map to WIPE_LEFT", 
                    AnimationType.WIPE_LEFT, 
                    AnimationType.parseType("WIPE"));
                    
        assertEquals("Mixed case 'Wipe' should map to WIPE_LEFT", 
                    AnimationType.WIPE_LEFT, 
                    AnimationType.parseType("Wipe"));
    }
    
    @Test
    public void testParseWipeDirectionalVariants() {
        // Test user-friendly name matching for directional wipes
        assertEquals("'wipe-left' should map to WIPE_LEFT", 
                    AnimationType.WIPE_LEFT, 
                    AnimationType.parseType("wipe-left"));
                    
        assertEquals("'wipe-right' should map to WIPE_RIGHT", 
                    AnimationType.WIPE_RIGHT, 
                    AnimationType.parseType("wipe-right"));
                    
        assertEquals("'wipe-up' should map to WIPE_UP", 
                    AnimationType.WIPE_UP, 
                    AnimationType.parseType("wipe-up"));
                    
        assertEquals("'wipe-down' should map to WIPE_DOWN", 
                    AnimationType.WIPE_DOWN, 
                    AnimationType.parseType("wipe-down"));
    }
    
    @Test
    public void testParseFadeAnimation() {
        assertEquals("'fade' should map to FADE", 
                    AnimationType.FADE, 
                    AnimationType.parseType("fade"));
                    
        assertEquals("Case insensitive 'FADE' should map to FADE", 
                    AnimationType.FADE, 
                    AnimationType.parseType("FADE"));
    }
    
    @Test
    public void testParseZoomAnimation() {
        assertEquals("'zoom' should map to ZOOM", 
                    AnimationType.ZOOM, 
                    AnimationType.parseType("zoom"));
                    
        assertEquals("Case insensitive 'ZOOM' should map to ZOOM", 
                    AnimationType.ZOOM, 
                    AnimationType.parseType("ZOOM"));
    }
    
    @Test
    public void testParseAppearAnimation() {
        assertEquals("'appear' should map to APPEAR", 
                    AnimationType.APPEAR, 
                    AnimationType.parseType("appear"));
    }
    
    @Test
    public void testParseFlyAnimations() {
        // Test basic fly maps to default direction
        assertEquals("'fly' should map to FLY_IN_LEFT (default)", 
                    AnimationType.FLY_IN_LEFT, 
                    AnimationType.parseType("fly"));
                    
        assertEquals("'fly-in' should map to FLY_IN_LEFT (default)", 
                    AnimationType.FLY_IN_LEFT, 
                    AnimationType.parseType("fly-in"));
        
        // Test specific directions
        assertEquals("'fly-in-left' should map to FLY_IN_LEFT", 
                    AnimationType.FLY_IN_LEFT, 
                    AnimationType.parseType("fly-in-left"));
                    
        assertEquals("'fly-in-right' should map to FLY_IN_RIGHT", 
                    AnimationType.FLY_IN_RIGHT, 
                    AnimationType.parseType("fly-in-right"));
                    
        assertEquals("'fly-in-top' should map to FLY_IN_TOP", 
                    AnimationType.FLY_IN_TOP, 
                    AnimationType.parseType("fly-in-top"));
    }
    
    @Test
    public void testParseNullAndEmpty() {
        // Test null input returns default (FADE)
        assertEquals("null input should return FADE default", 
                    AnimationType.FADE, 
                    AnimationType.parseType(null));
        
        // Test empty string returns default (FADE)
        assertEquals("empty string should return FADE default", 
                    AnimationType.FADE, 
                    AnimationType.parseType(""));
        
        // Test whitespace-only string returns default (FADE)
        assertEquals("whitespace-only should return FADE default", 
                    AnimationType.FADE, 
                    AnimationType.parseType("   "));
    }
    
    @Test
    public void testParseUnknownType() {
        // Test unknown animation type returns default (FADE)
        assertEquals("unknown type should return FADE default", 
                    AnimationType.FADE, 
                    AnimationType.parseType("unknown-animation-type"));
                    
        assertEquals("gibberish should return FADE default", 
                    AnimationType.FADE, 
                    AnimationType.parseType("xyz123"));
    }
    
    @Test
    public void testParseWithWhitespace() {
        // Test trimming works correctly
        assertEquals("'wipe' with leading whitespace should work", 
                    AnimationType.WIPE_LEFT, 
                    AnimationType.parseType("  wipe"));
        
        assertEquals("'wipe' with trailing whitespace should work", 
                    AnimationType.WIPE_LEFT, 
                    AnimationType.parseType("wipe  "));
        
        assertEquals("'wipe' with surrounding whitespace should work", 
                    AnimationType.WIPE_LEFT, 
                    AnimationType.parseType("  wipe  "));
    }
    
    @Test
    public void testUserFriendlyNameMatching() {
        // Test that user-friendly names from enum definitions work
        // This tests the exact match logic in parseType
        for (AnimationType type : AnimationType.values()) {
            String userFriendlyName = type.getUserFriendlyName();
            assertEquals("User-friendly name '" + userFriendlyName + "' should map back to " + type, 
                        type, 
                        AnimationType.parseType(userFriendlyName));
        }
    }
    
    @Test
    public void testCriticalTypesForTrace() {
        // Test the specific types involved in our wipe animation trace
        AnimationType wipeResult = AnimationType.parseType("wipe");
        AnimationType wipeLeftResult = AnimationType.parseType("wipe-left");
        
        assertSame("'wipe' and 'wipe-left' should both map to same WIPE_LEFT instance", 
                  wipeResult, wipeLeftResult);
        
        assertEquals("Both should be WIPE_LEFT", AnimationType.WIPE_LEFT, wipeResult);
        assertEquals("Both should be WIPE_LEFT", AnimationType.WIPE_LEFT, wipeLeftResult);
        
        // Verify these are not FADE
        assertNotEquals("WIPE_LEFT should not be FADE", AnimationType.FADE, wipeResult);
        
        // Verify these are not ZOOM  
        assertNotEquals("WIPE_LEFT should not be ZOOM", AnimationType.ZOOM, wipeResult);
    }
}