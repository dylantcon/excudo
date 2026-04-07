package com.excudo.xml.builders;

import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.exceptions.XMLParsingException;
import org.w3c.dom.*;
import javax.xml.xpath.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating [Content_Types].xml files
 * Provides type-safe API for generating OOXML content type definitions
 */
public class ContentTypesXMLBuilder {
  
  private final List<DefaultEntry> defaults = new ArrayList<>();
  private final List<OverrideEntry> overrides = new ArrayList<>();

  /**
   * Add a default content type for an extension
   */
  public ContentTypesXMLBuilder addDefault(String extension, String contentType) {
    defaults.add(new DefaultEntry(extension, contentType));
    return this;
  }

  /**
   * Add an override content type for a specific part
   */
  public ContentTypesXMLBuilder addOverride(String partName, String contentType) {
    overrides.add(new OverrideEntry(partName, contentType));
    return this;
  }

  /**
   * Add standard default content types for PPTX
   */
  public ContentTypesXMLBuilder withStandardDefaults() {
    return addDefault(XMLConstants.RELS_EXTENSION, XMLConstants.CONTENT_TYPE_RELATIONSHIPS)
           .addDefault(XMLConstants.XML_EXTENSION, "application/xml");
  }

  /**
   * Add default content types for common image formats (png, jpeg, gif).
   * Skips any extension already registered to avoid duplicates.
   */
  public ContentTypesXMLBuilder addImageDefaults() {
    if (defaults.stream().noneMatch(d -> d.extension.equals("png"))) {
      addDefault("png", "image/png");
    }
    if (defaults.stream().noneMatch(d -> d.extension.equals("jpeg"))) {
      addDefault("jpeg", "image/jpeg");
    }
    if (defaults.stream().noneMatch(d -> d.extension.equals("jpg"))) {
      addDefault("jpg", "image/jpeg");
    }
    if (defaults.stream().noneMatch(d -> d.extension.equals("gif"))) {
      addDefault("gif", "image/gif");
    }
    return this;
  }

  /**
   * Ensure image content types are registered in an existing [Content_Types].xml file.
   * Parses the file, adds missing image defaults, and writes it back.
   */
  public static void ensureImageContentTypes(File contentTypesFile) throws XMLParsingException {
    ContentTypesXMLBuilder builder = parseExisting(contentTypesFile);
    builder.addImageDefaults();
    try {
      java.nio.file.Files.writeString(contentTypesFile.toPath(), builder.build());
    } catch (java.io.IOException e) {
      throw new XMLParsingException("Failed to write updated Content_Types.xml", e);
    }
  }

  /**
   * Add presentation content type override
   */
  public ContentTypesXMLBuilder addPresentation(String partName) {
    return addOverride(partName, XMLConstants.CONTENT_TYPE_PRESENTATION);
  }

  /**
   * Add slide content type override
   */
  public ContentTypesXMLBuilder addSlide(String partName) {
    return addOverride(partName, XMLConstants.CONTENT_TYPE_SLIDE);
  }

  /**
   * Add slide master content type override
   */
  public ContentTypesXMLBuilder addSlideMaster(String partName) {
    return addOverride(partName, XMLConstants.CONTENT_TYPE_SLIDE_MASTER);
  }

  /**
   * Add slide layout content type override
   */
  public ContentTypesXMLBuilder addSlideLayout(String partName) {
    return addOverride(partName, XMLConstants.CONTENT_TYPE_SLIDE_LAYOUT);
  }

  /**
   * Add theme content type override
   */
  public ContentTypesXMLBuilder addTheme(String partName) {
    return addOverride(partName, XMLConstants.CONTENT_TYPE_THEME);
  }

  /**
   * Add notes slide content type override
   */
  public ContentTypesXMLBuilder addNotesSlide(String partName) {
    return addOverride(partName, XMLConstants.CONTENT_TYPE_NOTES_SLIDE);
  }

  /**
   * Remove slide content type override
   */
  public ContentTypesXMLBuilder removeSlide(String partName) {
    overrides.removeIf(entry -> entry.partName.equals(partName) &&
                                entry.contentType.equals(XMLConstants.CONTENT_TYPE_SLIDE));
    return this;
  }

  /**
   * Remove all slide content type overrides
   */
  public ContentTypesXMLBuilder removeAllSlides() {
    overrides.removeIf(entry -> entry.contentType.equals(XMLConstants.CONTENT_TYPE_SLIDE));
    return this;
  }

  /**
   * Remove notes slide content type override
   */
  public ContentTypesXMLBuilder removeNotesSlide(String partName) {
    overrides.removeIf(entry -> entry.partName.equals(partName) && 
                                entry.contentType.equals(XMLConstants.CONTENT_TYPE_NOTES_SLIDE));
    return this;
  }

  /**
   * Build the complete [Content_Types].xml content
   */
  public String build() {
    StringBuilder xml = new StringBuilder();
    xml.append(XMLConstants.XML_DECLARATION).append("\n");
    xml.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
    for (DefaultEntry entry : defaults) {
      xml.append(String.format(XMLConstants.CONTENT_TYPE_DEFAULT_TEMPLATE,
          entry.extension, entry.contentType));
    }
    for (OverrideEntry entry : overrides) {
      xml.append(String.format(XMLConstants.CONTENT_TYPE_OVERRIDE_TEMPLATE,
          entry.partName, entry.contentType));
    }
    xml.append("</Types>");
    return xml.toString();
  }

  /**
   * Create a new empty builder
   */
  public static ContentTypesXMLBuilder create() {
    return new ContentTypesXMLBuilder();
  }

  /**
   * Create a builder with standard PPTX content types
   */
  public static ContentTypesXMLBuilder createStandard() {
    return new ContentTypesXMLBuilder()
        .withStandardDefaults()
        .addPresentation("/ppt/presentation.xml")
        .addSlideMaster("/ppt/slideMasters/slideMaster1.xml")
        .addSlideLayout("/ppt/slideLayouts/slideLayout1.xml")
        .addTheme("/ppt/theme/theme1.xml");
  }

  /**
   * Create a builder for minimal PPTX testing
   */
  public static ContentTypesXMLBuilder createForTesting() {
    return createStandard();  // Same as standard for now
  }

  /**
   * Parse existing [Content_Types].xml and populate builder
   * Enables reading current content types and adding new ones
   */
  public static ContentTypesXMLBuilder parseExisting(File contentTypesFile) throws XMLParsingException {
    try {
      Document doc = XMLFactoryProvider.parseDocument(contentTypesFile);
      XPath xpath = XMLFactoryProvider.createXPathWithoutNamespace();
      
      ContentTypesXMLBuilder builder = new ContentTypesXMLBuilder();
      
      // Parse Default entries (namespace-aware using local-name())
      NodeList defaultNodes = (NodeList) xpath.evaluate("//*[local-name()='Default']", doc, XPathConstants.NODESET);
      for (int i = 0; i < defaultNodes.getLength(); i++) {
        Element defaultElement = (Element) defaultNodes.item(i);
        String extension = defaultElement.getAttribute("Extension");
        String contentType = defaultElement.getAttribute("ContentType");
        builder.addDefault(extension, contentType);
      }
      
      // Parse Override entries (namespace-aware using local-name())
      NodeList overrideNodes = (NodeList) xpath.evaluate("//*[local-name()='Override']", doc, XPathConstants.NODESET);
      for (int i = 0; i < overrideNodes.getLength(); i++) {
        Element overrideElement = (Element) overrideNodes.item(i);
        String partName = overrideElement.getAttribute("PartName");
        String contentType = overrideElement.getAttribute("ContentType");
        builder.addOverride(partName, contentType);
      }
      
      return builder;
      
    } catch (Exception e) {
      throw new XMLParsingException("Failed to parse existing Content_Types.xml", e);
    }
  }

  // Helper classes for type safety
  private static class DefaultEntry {
    final String extension;
    final String contentType;
    
    DefaultEntry(String extension, String contentType) {
      this.extension = extension;
      this.contentType = contentType;
    }
  }
  
  private static class OverrideEntry {
    final String partName;
    final String contentType;
    
    OverrideEntry(String partName, String contentType) {
      this.partName = partName;
      this.contentType = contentType;
    }
  }
}