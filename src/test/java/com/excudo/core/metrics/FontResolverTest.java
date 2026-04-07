package com.excudo.core.metrics;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.file.Path;

/**
 * Tests FontResolver font family name resolution via fc-match.
 */
public class FontResolverTest {

    @Test
    public void testDejaVuSansResolves() {
        FontResolver.clearCache();
        Path path = FontResolver.resolve("DejaVu Sans", false, false);
        assertNotNull("DejaVu Sans should resolve to a font file", path);
        assertTrue("Path should end in .ttf", path.toString().endsWith(".ttf"));
        assertTrue("Should resolve to DejaVuSans.ttf",
            path.toString().contains("DejaVuSans"));
    }

    @Test
    public void testCalibriResolvesToFallback() {
        Path path = FontResolver.resolve("Calibri", false, false);
        assertNotNull("Calibri should resolve to a fallback font", path);
        assertTrue("Fallback should be a .ttf file",
            path.toString().endsWith(".ttf") || path.toString().endsWith(".otf"));
    }

    @Test
    public void testCachingWorks() {
        FontResolver.clearCache();
        Path first = FontResolver.resolve("DejaVu Sans", false, false);
        Path second = FontResolver.resolve("DejaVu Sans", false, false);
        assertSame("Cached paths should be identical objects", first, second);
    }

    @Test
    public void testBoldVariant() {
        Path regular = FontResolver.resolve("DejaVu Sans", false, false);
        Path bold = FontResolver.resolve("DejaVu Sans", true, false);
        assertNotNull("Bold variant should resolve", bold);
        // Bold may resolve to a different file or the same -- just verify it resolves
    }
}
