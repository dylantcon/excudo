package com.excudo.core.model;

import org.w3c.dom.*;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * DOM-based wrapper for [Content_Types].xml.
 * Provides type-safe operations on the content types document without
 * string-based XML building.
 */
public class ContentTypesModel {

    private static final ComponentLogger logger = Logger.getLogger(ContentTypesModel.class);
    private static final String CT_NS = "http://schemas.openxmlformats.org/package/2006/content-types";

    private final Document doc;

    public ContentTypesModel(Document doc) {
        this.doc = doc;
    }

    public Document getDocument() {
        return doc;
    }

    public void addDefault(String extension, String mimeType) {
        if (hasDefault(extension)) return;

        Element root = doc.getDocumentElement();
        Element defaultEl = doc.createElementNS(CT_NS, "Default");
        defaultEl.setAttribute("Extension", extension);
        defaultEl.setAttribute("ContentType", mimeType);

        // Insert defaults before overrides for clean ordering
        NodeList overrides = root.getElementsByTagNameNS(CT_NS, "Override");
        if (overrides.getLength() > 0) {
            root.insertBefore(defaultEl, overrides.item(0));
        } else {
            root.appendChild(defaultEl);
        }
    }

    public void addOverride(String partName, String mimeType) {
        if (hasOverride(partName)) return;

        Element root = doc.getDocumentElement();
        Element override = doc.createElementNS(CT_NS, "Override");
        override.setAttribute("PartName", partName);
        override.setAttribute("ContentType", mimeType);
        root.appendChild(override);
    }

    public void removeOverride(String partName) {
        Element el = findOverride(partName);
        if (el != null) {
            el.getParentNode().removeChild(el);
        }
    }

    /**
     * Ensure the media's extension type is registered as a Default entry.
     */
    public void ensureMediaType(MediaElement media) {
        String ext = media.getExtension();
        if (ext.isEmpty() || hasDefault(ext)) return;
        addDefault(ext, media.getMimeType());
        logger.debug("Auto-registered content type: {} -> {}", ext, media.getMimeType());
    }

    public boolean hasDefault(String extension) {
        Element root = doc.getDocumentElement();
        NodeList defaults = root.getElementsByTagNameNS(CT_NS, "Default");
        for (int i = 0; i < defaults.getLength(); i++) {
            Element el = (Element) defaults.item(i);
            if (extension.equalsIgnoreCase(el.getAttribute("Extension"))) {
                return true;
            }
        }
        // Also check without namespace
        defaults = root.getElementsByTagName("Default");
        for (int i = 0; i < defaults.getLength(); i++) {
            Element el = (Element) defaults.item(i);
            if (extension.equalsIgnoreCase(el.getAttribute("Extension"))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasOverride(String partName) {
        return findOverride(partName) != null;
    }

    private Element findOverride(String partName) {
        Element root = doc.getDocumentElement();
        NodeList overrides = root.getElementsByTagNameNS(CT_NS, "Override");
        for (int i = 0; i < overrides.getLength(); i++) {
            Element el = (Element) overrides.item(i);
            if (partName.equals(el.getAttribute("PartName"))) {
                return el;
            }
        }
        // Also check without namespace
        overrides = root.getElementsByTagName("Override");
        for (int i = 0; i < overrides.getLength(); i++) {
            Element el = (Element) overrides.item(i);
            if (partName.equals(el.getAttribute("PartName"))) {
                return el;
            }
        }
        return null;
    }
}
