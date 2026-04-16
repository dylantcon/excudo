package com.excudo.core.orchestration;

import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests PresentationMetadata construction and accessors.
 */
public class PresentationMetadataTest {

    @Test
    public void basicConstruction() {
        Instant now = Instant.now();
        PresentationMetadata meta = new PresentationMetadata(
            5, "Q4 Report", now, Arrays.asList("Alice", "Bob"));

        assertEquals(5, meta.getSlideCount());
        assertEquals("Q4 Report", meta.getTitle());
        assertEquals(now, meta.getLastModified());
        assertEquals(2, meta.getAuthors().size());
    }

    @Test
    public void getAuthorReturnsPrimaryAuthor() {
        PresentationMetadata meta = new PresentationMetadata(
            1, "Test", Instant.now(), Arrays.asList("Primary Author", "Secondary"));

        String author = meta.getAuthor();
        assertNotNull(author);
    }

    @Test
    public void emptyAuthorsList() {
        PresentationMetadata meta = new PresentationMetadata(
            1, "No Author", Instant.now(), Collections.emptyList());

        assertNotNull(meta.getAuthors());
        assertTrue(meta.getAuthors().isEmpty());
    }

    @Test
    public void nullTitle() {
        PresentationMetadata meta = new PresentationMetadata(
            3, null, Instant.now(), Collections.emptyList());

        assertNull(meta.getTitle());
        assertEquals(3, meta.getSlideCount());
    }

    @Test
    public void zeroSlides() {
        PresentationMetadata meta = new PresentationMetadata(
            0, "Empty", Instant.now(), Collections.emptyList());

        assertEquals(0, meta.getSlideCount());
    }

    @Test
    public void layoutCountReturnsValue() {
        PresentationMetadata meta = new PresentationMetadata(
            1, "Test", Instant.now(), Collections.emptyList());

        assertTrue(meta.getLayoutCount() >= 1);
    }
}
