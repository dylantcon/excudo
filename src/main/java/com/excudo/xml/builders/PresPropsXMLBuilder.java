package com.excudo.xml.builders;

import com.excudo.core.utils.XMLConstants;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating ppt/presProps.xml files.
 * Handles PowerPoint presentation properties including Microsoft extension elements
 * documented in MS-PPTX sections 2.3.1.5, 2.3.1.6, and 2.4.1.1.
 */
public class PresPropsXMLBuilder {

    private final List<ExtensionEntry> extensions = new ArrayList<>();

    public PresPropsXMLBuilder addExtension(String uri, String namespace, String prefix, String elementName, String value) {
        extensions.add(new ExtensionEntry(uri, namespace, prefix, elementName, value));
        return this;
    }

    /**
     * Add discardImageEditData extension (MS-PPTX 2.3.1.6).
     * Controls whether image editing data is discarded on save.
     */
    public PresPropsXMLBuilder withDiscardImageEditData(int val) {
        return addExtension(XMLConstants.ExtensionUri.DISCARD_IMAGE_EDIT_DATA,
                XMLConstants.OfficeNamespace.POWERPOINT_2010, "p14", "discardImageEditData", String.valueOf(val));
    }

    /**
     * Add defaultImageDpi extension (MS-PPTX 2.3.1.5).
     * Sets the default DPI for images in the presentation.
     */
    public PresPropsXMLBuilder withDefaultImageDpi(int dpi) {
        return addExtension(XMLConstants.ExtensionUri.DEFAULT_IMAGE_DPI,
                XMLConstants.OfficeNamespace.POWERPOINT_2010, "p14", "defaultImageDpi", String.valueOf(dpi));
    }

    /**
     * Add chartTrackingRefBased extension (MS-PPTX 2.4.1.1).
     * Controls chart reference-based tracking.
     */
    public PresPropsXMLBuilder withChartTrackingRefBased(int val) {
        return addExtension(XMLConstants.ExtensionUri.CHART_TRACKING_REF_BASED,
                XMLConstants.OfficeNamespace.POWERPOINT_2012, "p15", "chartTrackingRefBased", String.valueOf(val));
    }

    /**
     * Add all standard PowerPoint extensions with default values.
     * Matches what PowerPoint writes to new presentations.
     */
    public PresPropsXMLBuilder withStandardExtensions() {
        return withDiscardImageEditData(0)
                .withDefaultImageDpi(220)
                .withChartTrackingRefBased(0);
    }

    public String build() {
        StringBuilder xml = new StringBuilder();
        xml.append(XMLConstants.XML_DECLARATION).append("\n");
        xml.append("<p:presentationPr")
           .append(" xmlns:a=\"").append(XMLConstants.DRAWING_NS).append("\"")
           .append(" xmlns:r=\"").append(XMLConstants.RELATIONSHIPS_NS).append("\"")
           .append(" xmlns:p=\"").append(XMLConstants.PRESENTATION_NS).append("\"");

        if (extensions.isEmpty()) {
            xml.append("/>");
        } else {
            xml.append(">");
            xml.append("<p:extLst>");
            for (ExtensionEntry ext : extensions) {
                xml.append("<p:ext uri=\"").append(ext.uri).append("\">");
                xml.append("<").append(ext.prefix).append(":").append(ext.elementName);
                xml.append(" xmlns:").append(ext.prefix).append("=\"").append(ext.namespace).append("\"");
                xml.append(" val=\"").append(ext.value).append("\"/>");
                xml.append("</p:ext>");
            }
            xml.append("</p:extLst>");
            xml.append("</p:presentationPr>");
        }

        return xml.toString();
    }

    public static PresPropsXMLBuilder create() {
        return new PresPropsXMLBuilder();
    }

    /**
     * Create builder with standard PowerPoint-compatible defaults.
     */
    public static PresPropsXMLBuilder createWithDefaults() {
        return new PresPropsXMLBuilder().withStandardExtensions();
    }

    private static class ExtensionEntry {
        final String uri;
        final String namespace;
        final String prefix;
        final String elementName;
        final String value;

        ExtensionEntry(String uri, String namespace, String prefix, String elementName, String value) {
            this.uri = uri;
            this.namespace = namespace;
            this.prefix = prefix;
            this.elementName = elementName;
            this.value = value;
        }
    }
}
