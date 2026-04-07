package com.excudo.core.model;

/**
 * In-memory representation of a media part in an OPC package (image, video, audio).
 * Holds raw bytes along with metadata needed for Content_Types registration and
 * ZIP serialization.
 */
public class MediaElement {

    private final String partName;   // e.g., "/ppt/media/image1.png"
    private final String extension;  // e.g., "png"
    private final String mimeType;   // e.g., "image/png"
    private final byte[] data;
    private final int compressionMethod; // ZipEntry.DEFLATED or STORED

    public MediaElement(String partName, String mimeType, byte[] data) {
        this(partName, mimeType, data, java.util.zip.ZipEntry.DEFLATED);
    }

    public MediaElement(String partName, String mimeType, byte[] data, int compressionMethod) {
        this.partName = partName;
        this.mimeType = mimeType;
        this.data = data;
        this.compressionMethod = compressionMethod;

        // Derive extension from part name
        int dotIdx = partName.lastIndexOf('.');
        this.extension = dotIdx >= 0 ? partName.substring(dotIdx + 1) : "";
    }

    public static MediaElement fromBytes(String partName, String mimeType, byte[] data) {
        return new MediaElement(partName, mimeType, data);
    }

    public String getPartName() { return partName; }
    public String getExtension() { return extension; }
    public String getMimeType() { return mimeType; }
    public byte[] getData() { return data; }
    public int getCompressionMethod() { return compressionMethod; }
}
