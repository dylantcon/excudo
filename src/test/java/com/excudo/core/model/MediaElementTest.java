package com.excudo.core.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests MediaElement construction, accessors, and extension extraction.
 */
public class MediaElementTest {

    @Test
    public void constructorExtractsExtension() {
        byte[] data = new byte[]{1, 2, 3};
        MediaElement media = new MediaElement("ppt/media/image1.png", "image/png", data);

        assertEquals("ppt/media/image1.png", media.getPartName());
        assertEquals("image/png", media.getMimeType());
        assertEquals("png", media.getExtension());
        assertArrayEquals(data, media.getData());
    }

    @Test
    public void extensionExtractionHandlesVariousFormats() {
        assertEquals("jpeg", new MediaElement("ppt/media/photo.jpeg", "image/jpeg", new byte[0]).getExtension());
        assertEquals("svg", new MediaElement("ppt/media/icon.svg", "image/svg+xml", new byte[0]).getExtension());
        assertEquals("emf", new MediaElement("ppt/media/drawing.emf", "image/x-emf", new byte[0]).getExtension());
    }

    @Test
    public void compressionMethodDefaults() {
        MediaElement media = new MediaElement("part.png", "image/png", new byte[0]);
        assertEquals("Default compression should be DEFLATED (8)", 8, media.getCompressionMethod());
    }

    @Test
    public void compressionMethodCanBeOverridden() {
        MediaElement media = new MediaElement("part.png", "image/png", new byte[0], 0);
        assertEquals("STORED compression", 0, media.getCompressionMethod());
    }

    @Test
    public void fromBytesFactoryMethod() {
        byte[] data = new byte[]{10, 20, 30};
        MediaElement media = MediaElement.fromBytes("ppt/media/test.gif", "image/gif", data);

        assertEquals("ppt/media/test.gif", media.getPartName());
        assertEquals("image/gif", media.getMimeType());
        assertEquals("gif", media.getExtension());
        assertArrayEquals(data, media.getData());
    }

    @Test
    public void partNameWithoutExtension() {
        MediaElement media = new MediaElement("ppt/media/noext", "application/octet-stream", new byte[0]);
        // Extension extraction from a path without an extension
        String ext = media.getExtension();
        // Should handle gracefully (empty or the full name after last /)
        assertNotNull(ext);
    }

    @Test
    public void dataIntegrity() {
        byte[] original = new byte[]{(byte) 0xFF, 0x00, (byte) 0xAB, 0x12};
        MediaElement media = new MediaElement("test.bin", "application/octet-stream", original);
        assertArrayEquals(original, media.getData());
    }
}
