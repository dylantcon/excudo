package com.excudo.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test markdown bullet detection specifically
 */
public class MarkdownDetectionTest {
    
    @Test
    public void testMarkdownDetectionWithBackslashes() {
        // This is what we might be getting from poorly parsed JSON
        String textWithBackslashes = "Java Programming Language\\- Platform independent\\  - Write once, run anywhere\\- Object-oriented programming";
        
        System.out.println("Testing text with backslashes: " + textWithBackslashes);
        boolean detected = TextFormatUtils.containsBulletMarkers(textWithBackslashes);
        System.out.println("Bullet markers detected: " + detected);
        
        assertFalse("Should not detect bullets in text with backslashes", detected);
    }
    
    @Test 
    public void testMarkdownDetectionWithActualNewlines() {
        // This is what we should be getting after proper JSON parsing
        String textWithNewlines = "Java Programming Language\n- Platform independent\n  - Write once, run anywhere\n- Object-oriented programming";
        
        System.out.println("Testing text with newlines: " + textWithNewlines.replace("\n", "\\n"));
        boolean detected = TextFormatUtils.containsBulletMarkers(textWithNewlines);
        System.out.println("Bullet markers detected: " + detected);
        
        assertTrue("Should detect bullets in text with actual newlines", detected);
    }
    
    @Test
    public void testSimpleMarkdownBullet() {
        String text = "- Simple bullet";
        assertTrue("Should detect simple bullet", TextFormatUtils.containsBulletMarkers(text));
    }
}