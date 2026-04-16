package com.excudo.core.orchestration;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests SlideMetadata construction and accessors.
 */
public class SlideMetadataTest {

    @Test
    public void basicConstruction() {
        SlideMetadata meta = new SlideMetadata(1, "Introduction",
            SlideType.TITLE, 3, Arrays.asList(2, 3, 4));

        assertEquals(1, meta.getSlideNumber());
        assertEquals("Introduction", meta.getTitle());
        assertEquals(SlideType.TITLE, meta.getType());
        assertEquals(3, meta.getShapeCount());
        assertEquals(3, meta.getSpids().size());
    }

    @Test
    public void extendedConstruction() {
        SlideMetadata meta = new SlideMetadata(2, "Content Slide",
            SlideType.CONTENT, 5, Arrays.asList(2, 3, 4, 5, 6),
            3, "slideLayout2");

        assertEquals(2, meta.getSlideNumber());
        assertEquals("Content Slide", meta.getTitle());
        assertEquals(5, meta.getShapeCount());
        assertEquals(3, meta.getAnimationCount());
        assertEquals("slideLayout2", meta.getLayoutName());
    }

    @Test
    public void spidsPreservedInOrder() {
        SlideMetadata meta = new SlideMetadata(1, "Test",
            SlideType.BLANK, 4, Arrays.asList(10, 5, 20, 1));

        assertEquals(Integer.valueOf(10), meta.getSpids().get(0));
        assertEquals(Integer.valueOf(5), meta.getSpids().get(1));
        assertEquals(Integer.valueOf(20), meta.getSpids().get(2));
        assertEquals(Integer.valueOf(1), meta.getSpids().get(3));
    }

    @Test
    public void slideTypeValues() {
        // Verify all expected slide types exist
        assertNotNull(SlideType.TITLE);
        assertNotNull(SlideType.CONTENT);
        assertNotNull(SlideType.BLANK);
    }

    @Test
    public void emptySlide() {
        SlideMetadata meta = new SlideMetadata(1, "",
            SlideType.BLANK, 0, Collections.emptyList());

        assertEquals(0, meta.getShapeCount());
        assertTrue(meta.getSpids().isEmpty());
        assertEquals("", meta.getTitle());
    }

    @Test
    public void defaultAnimationCount() {
        SlideMetadata meta = new SlideMetadata(1, "Test",
            SlideType.CONTENT, 2, Arrays.asList(2, 3));

        assertEquals(0, meta.getAnimationCount());
        // Default layout name is "Unknown Layout", not null
        assertNotNull(meta.getLayoutName());
    }
}
