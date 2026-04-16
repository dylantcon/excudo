package com.excudo.core.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests ParagraphMetadata text/bullet structure accessors.
 */
public class ParagraphMetadataTest {

    @Test
    public void basicAccessors() {
        List<String> contents = Arrays.asList("Title text", "First bullet", "Second bullet");
        List<Boolean> isBullet = Arrays.asList(false, true, true);
        List<String> markers = Arrays.asList("", "•", "•");

        ParagraphMetadata pm = new ParagraphMetadata(contents, isBullet, markers);

        assertEquals(3, pm.getParagraphCount());
        assertEquals("Title text", pm.getParagraphContent(0));
        assertEquals("First bullet", pm.getParagraphContent(1));
        assertFalse(pm.isParagraphBullet(0));
        assertTrue(pm.isParagraphBullet(1));
        assertEquals("•", pm.getBulletMarker(1));
    }

    @Test
    public void getBulletPointsOnlyFiltersBullets() {
        List<String> contents = Arrays.asList("Title", "Bullet A", "Non-bullet", "Bullet B");
        List<Boolean> isBullet = Arrays.asList(false, true, false, true);
        List<String> markers = Arrays.asList("", "•", "", "–");

        ParagraphMetadata pm = new ParagraphMetadata(contents, isBullet, markers);

        List<String> bulletsOnly = pm.getBulletPointsOnly();
        assertEquals(2, bulletsOnly.size());
        assertEquals("Bullet A", bulletsOnly.get(0));
        assertEquals("Bullet B", bulletsOnly.get(1));
    }

    @Test
    public void getBulletPointIndicesReturnsCorrectPositions() {
        List<String> contents = Arrays.asList("Title", "Bullet A", "Non-bullet", "Bullet B");
        List<Boolean> isBullet = Arrays.asList(false, true, false, true);
        List<String> markers = Arrays.asList("", "•", "", "–");

        ParagraphMetadata pm = new ParagraphMetadata(contents, isBullet, markers);

        List<Integer> indices = pm.getBulletPointIndices();
        assertEquals(2, indices.size());
        assertEquals(Integer.valueOf(1), indices.get(0));
        assertEquals(Integer.valueOf(3), indices.get(1));
    }

    @Test
    public void noBulletPoints() {
        List<String> contents = Arrays.asList("Title", "Subtitle");
        List<Boolean> isBullet = Arrays.asList(false, false);
        List<String> markers = Arrays.asList("", "");

        ParagraphMetadata pm = new ParagraphMetadata(contents, isBullet, markers);

        assertTrue(pm.getBulletPointsOnly().isEmpty());
        assertTrue(pm.getBulletPointIndices().isEmpty());
    }

    @Test
    public void singleParagraph() {
        ParagraphMetadata pm = new ParagraphMetadata(
            Collections.singletonList("Only text"),
            Collections.singletonList(false),
            Collections.singletonList(""));

        assertEquals(1, pm.getParagraphCount());
        assertEquals("Only text", pm.getParagraphContent(0));
        assertFalse(pm.isParagraphBullet(0));
    }

    @Test
    public void toStringContainsInfo() {
        ParagraphMetadata pm = new ParagraphMetadata(
            Arrays.asList("A", "B"),
            Arrays.asList(false, true),
            Arrays.asList("", "•"));

        String str = pm.toString();
        assertNotNull(str);
        assertTrue(str.length() > 0);
    }

    @Test
    public void bulletMarkersPreserved() {
        List<String> markers = Arrays.asList("•", "–", "▸", "○");
        ParagraphMetadata pm = new ParagraphMetadata(
            Arrays.asList("A", "B", "C", "D"),
            Arrays.asList(true, true, true, true),
            markers);

        assertEquals("•", pm.getBulletMarker(0));
        assertEquals("–", pm.getBulletMarker(1));
        assertEquals("▸", pm.getBulletMarker(2));
        assertEquals("○", pm.getBulletMarker(3));
    }
}
