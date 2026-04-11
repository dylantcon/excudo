package com.excudo.core.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests LayoutInfo placeholder geometry lookup and accessors.
 */
public class LayoutInfoTest {

    @Test
    public void basicAccessors() {
        LayoutInfo info = new LayoutInfo("slideLayout1", "Title Slide", "ppt/slideLayouts/slideLayout1.xml",
            true, false, true, 0, "Title layout with subtitle");

        assertEquals("slideLayout1", info.getLayoutId());
        assertEquals("Title Slide", info.getName());
        assertEquals("ppt/slideLayouts/slideLayout1.xml", info.getFilePath());
        assertTrue(info.hasTitlePlaceholder());
        assertFalse(info.hasContentPlaceholder());
        assertTrue(info.hasSubtitlePlaceholder());
        assertEquals(0, info.getContentPlaceholderCount());
        assertEquals("Title layout with subtitle", info.getDescription());
    }

    @Test
    public void getPlaceholderGeometryByType() {
        PlaceholderGeometry titleGeom = new PlaceholderGeometry(
            914400, 274638, 10058400, 1325563, "type:title", "title");
        PlaceholderGeometry bodyGeom = new PlaceholderGeometry(
            914400, 1825625, 10058400, 4351338, "type:body", "body");

        LayoutInfo info = new LayoutInfo("layout1", "Two Content", "path",
            true, true, false, 1, "desc",
            Arrays.asList(titleGeom, bodyGeom), "title");

        PlaceholderGeometry found = info.getPlaceholderGeometryByType("title");
        assertNotNull(found);
        assertEquals(914400, found.getX());
        assertEquals(274638, found.getY());

        PlaceholderGeometry body = info.getPlaceholderGeometryByType("body");
        assertNotNull(body);
        assertEquals(1825625, body.getY());
    }

    @Test
    public void getPlaceholderGeometryByTypeReturnsNullForMissing() {
        LayoutInfo info = new LayoutInfo("layout1", "Blank", "path",
            false, false, false, 0, "blank",
            Collections.emptyList(), null);

        assertNull(info.getPlaceholderGeometryByType("title"));
        assertNull(info.getPlaceholderGeometryByType("body"));
    }

    @Test
    public void getPlaceholderGeometryByIndex() {
        PlaceholderGeometry geom = new PlaceholderGeometry(
            100, 200, 300, 400, "1", null);

        LayoutInfo info = new LayoutInfo("layout1", "Content", "path",
            true, true, false, 1, "desc",
            Collections.singletonList(geom), "title");

        PlaceholderGeometry found = info.getPlaceholderGeometryByIndex("1");
        assertNotNull(found);
        assertEquals(100, found.getX());
    }

    @Test
    public void getPlaceholderGeometryByIndexReturnsNullForMissing() {
        LayoutInfo info = new LayoutInfo("layout1", "Blank", "path",
            false, false, false, 0, "blank");

        assertNull(info.getPlaceholderGeometryByIndex("99"));
    }

    @Test
    public void titlePlaceholderTypeDefaults() {
        LayoutInfo withNull = new LayoutInfo("l1", "Test", "path",
            true, false, false, 0, "desc",
            Collections.emptyList(), null);
        assertEquals("title", withNull.getTitlePlaceholderType());

        LayoutInfo withCtrTitle = new LayoutInfo("l2", "Test", "path",
            true, false, false, 0, "desc",
            Collections.emptyList(), "ctrTitle");
        assertEquals("ctrTitle", withCtrTitle.getTitlePlaceholderType());
    }

    @Test
    public void getDisplayNameFormat() {
        LayoutInfo info = new LayoutInfo("slideLayout2", "Title and Content", "path",
            true, true, false, 1, "desc");

        String displayName = info.getDisplayName();
        assertTrue(displayName.contains("Title and Content"));
        assertTrue(displayName.contains("slideLayout2"));
    }

    @Test
    public void getPlaceholderSummary() {
        LayoutInfo withPlaceholders = new LayoutInfo("l1", "Test", "path",
            true, true, true, 2, "desc");
        String summary = withPlaceholders.getPlaceholderSummary();
        assertTrue(summary.contains("title"));
        assertTrue(summary.contains("content"));

        LayoutInfo blank = new LayoutInfo("l2", "Blank", "path",
            false, false, false, 0, "desc");
        assertEquals("none", blank.getPlaceholderSummary());
    }

    @Test
    public void getFirstAvailableUserSpid() {
        // 1=group, 2=title, no content -> first user SPID is 3
        LayoutInfo noContent = new LayoutInfo("l1", "Title Only", "path",
            true, false, false, 0, "desc");
        assertEquals(3, noContent.getFirstAvailableUserSpid());

        // 1=group, 2=title, 3,4=content placeholders -> first user SPID is 5
        LayoutInfo twoContent = new LayoutInfo("l2", "Two Content", "path",
            true, true, false, 2, "desc");
        assertEquals(5, twoContent.getFirstAvailableUserSpid());
    }

    @Test
    public void placeholderGeometriesAreUnmodifiable() {
        PlaceholderGeometry geom = new PlaceholderGeometry(
            0, 0, 100, 100, null, "title");
        LayoutInfo info = new LayoutInfo("l1", "Test", "path",
            true, false, false, 0, "desc",
            Collections.singletonList(geom), "title");

        List<PlaceholderGeometry> geometries = info.getPlaceholderGeometries();
        try {
            geometries.add(new PlaceholderGeometry(0, 0, 100, 100, "1", "body"));
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // correct
        }
    }
}
