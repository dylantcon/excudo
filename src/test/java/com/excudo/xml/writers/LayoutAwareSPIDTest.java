package com.excudo.xml.writers;

import org.junit.Test;
import static org.junit.Assert.*;
import com.excudo.core.model.LayoutInfo;

/**
 * Test that SPIDManager correctly accounts for layout placeholders
 */
public class LayoutAwareSPIDTest {
    
    @Test
    public void testLayoutInfoFirstAvailableSpid() {
        // Test single content placeholder layout (Title + Content)
        LayoutInfo singleContent = new LayoutInfo("slideLayout1", "Title and Content", 
            "slideLayouts/slideLayout1.xml", true, true, false, 1, "Title and Content");
        
        // Expected: SPID 1=group, 2=title, 3=content placeholder, 4=first user content
        assertEquals("Single content layout should have first user SPID at 4", 4, singleContent.getFirstAvailableUserSpid());
        
        // Test two content placeholder layout (Title + Two Content)
        LayoutInfo dualContent = new LayoutInfo("slideLayout2", "Title and Two Content", 
            "slideLayouts/slideLayout2.xml", true, true, false, 2, "Title and Two Content");
        
        // Expected: SPID 1=group, 2=title, 3=content1, 4=content2, 5=first user content  
        assertEquals("Dual content layout should have first user SPID at 5", 5, dualContent.getFirstAvailableUserSpid());
        
        // Test content-only layout (no title)
        LayoutInfo contentOnly = new LayoutInfo("slideLayout3", "Content Only", 
            "slideLayouts/slideLayout3.xml", false, true, false, 1, "Content Only");
        
        // Expected: SPID 1=group, 3=content placeholder, 4=first user content (no title=2)
        assertEquals("Content-only layout should have first user SPID at 4", 4, contentOnly.getFirstAvailableUserSpid());
        
        // Test blank layout (no placeholders)
        LayoutInfo blank = new LayoutInfo("slideLayout4", "Blank", 
            "slideLayouts/slideLayout4.xml", false, false, false, 0, "Blank");
        
        // Expected: SPID 1=group, 3=first user content (no title or content placeholders)
        assertEquals("Blank layout should have first user SPID at 3", 3, blank.getFirstAvailableUserSpid());
    }
    
    @Test
    public void testPlaceholderSummary() {
        // Test dual content placeholder display
        LayoutInfo dualContent = new LayoutInfo("test", "Test", "test.xml", 
            true, true, false, 2, "Test");
        
        String summary = dualContent.getPlaceholderSummary();
        assertTrue("Should show count for multiple placeholders", summary.contains("2 content placeholders"));
        
        // Test single content placeholder display
        LayoutInfo singleContent = new LayoutInfo("test", "Test", "test.xml", 
            true, true, false, 1, "Test");
        
        summary = singleContent.getPlaceholderSummary();
        assertTrue("Should show simple 'content' for single placeholder", summary.contains("content"));
        assertFalse("Should not show count for single placeholder", summary.contains("1 content"));
    }
}