package com.excudo.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for TextFormatUtils markdown detection and cleaning.
 */
public class TextFormatUtilsTest {

    @Test
    public void testContainsBulletMarkers() {
        assertTrue(TextFormatUtils.containsBulletMarkers("- First item\n- Second item"));
        assertTrue(TextFormatUtils.containsBulletMarkers("- First item\n  - Second item"));
        assertTrue(TextFormatUtils.containsBulletMarkers("1. First item\n2. Second item"));
        assertFalse(TextFormatUtils.containsBulletMarkers("Regular text without bullets"));
        assertFalse(TextFormatUtils.containsBulletMarkers(null));
    }

    @Test
    public void testCleanBulletMarkers() {
        assertEquals("• First item\n• Second item",
                TextFormatUtils.cleanBulletMarkers("- First item\n- Second item"));

        assertEquals("• First item\n  • Second item",
                TextFormatUtils.cleanBulletMarkers("- First item\n  - Second item"));

        assertEquals("1. First item\n2. Second item",
                TextFormatUtils.cleanBulletMarkers("1. First item\n2. Second item"));

        assertEquals("Title\n• Bullet one\n• Bullet two\nRegular text",
                TextFormatUtils.cleanBulletMarkers("Title\n- Bullet one\n- Bullet two\nRegular text"));

        assertNull(TextFormatUtils.cleanBulletMarkers(null));
    }
}
