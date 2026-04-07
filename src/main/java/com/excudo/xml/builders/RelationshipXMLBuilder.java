package com.excudo.xml.builders;

import com.excudo.core.utils.XMLConstants;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating relationship XML files (.rels)
 * Provides type-safe API for generating OOXML relationship documents
 */
public class RelationshipXMLBuilder {

  private final List<RelationshipEntry> relationships = new ArrayList<>();
  private int autoIdCounter = 1;

  /**
   * Add a relationship entry with explicit rId
   */
  public RelationshipXMLBuilder addRelationship(String id, String type, String target) {
    relationships.add(new RelationshipEntry(id, type, target));
    // Keep autoIdCounter ahead of any manually assigned id
    if (id.startsWith("rId")) {
      try {
        int num = Integer.parseInt(id.substring(3));
        if (num >= autoIdCounter) {
          autoIdCounter = num + 1;
        }
      } catch (NumberFormatException ignored) {}
    }
    return this;
  }

  /**
   * Add a relationship entry with auto-assigned sequential rId
   */
  public RelationshipXMLBuilder addRelationship(String type, String target) {
    return addRelationship("rId" + autoIdCounter, type, target);
  }

  /**
   * Add a slide master relationship
   */
  public RelationshipXMLBuilder addSlideMaster(String id, String target) {
    return addRelationship(id, XMLConstants.RELATIONSHIP_TYPE_SLIDE_MASTER, target);
  }

  public RelationshipXMLBuilder addSlideMaster(String target) {
    return addRelationship(XMLConstants.RELATIONSHIP_TYPE_SLIDE_MASTER, target);
  }

  /**
   * Add a slide layout relationship
   */
  public RelationshipXMLBuilder addSlideLayout(String id, String target) {
    return addRelationship(id, XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT, target);
  }

  public RelationshipXMLBuilder addSlideLayout(String target) {
    return addRelationship(XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT, target);
  }

  /**
   * Add a theme relationship
   */
  public RelationshipXMLBuilder addTheme(String id, String target) {
    return addRelationship(id, XMLConstants.RELATIONSHIP_TYPE_THEME, target);
  }

  public RelationshipXMLBuilder addTheme(String target) {
    return addRelationship(XMLConstants.RELATIONSHIP_TYPE_THEME, target);
  }

  /**
   * Add an image relationship
   */
  public RelationshipXMLBuilder addImage(String id, String target) {
    return addRelationship(id, XMLConstants.RELATIONSHIP_TYPE_IMAGE, target);
  }

  /**
   * Add a standard office document relationship (for main .rels)
   */
  public RelationshipXMLBuilder addOfficeDocument(String id, String target) {
    return addRelationship(id,
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument",
        target);
  }

  /**
   * Build the complete relationship XML content
   */
  public String build() {
    StringBuilder xml = new StringBuilder();
    xml.append(XMLConstants.XML_DECLARATION).append("\n");
    xml.append("<Relationships xmlns=\"").append(XMLConstants.PACKAGE_RELATIONSHIPS_NS).append("\">");
    for (RelationshipEntry rel : relationships) {
      xml.append(String.format(XMLConstants.RELATIONSHIP_ELEMENT_TEMPLATE,
          rel.id, rel.type, rel.target));
    }
    xml.append("</Relationships>");
    return xml.toString();
  }

  /**
   * Create a new empty builder
   */
  public static RelationshipXMLBuilder create() {
    return new RelationshipXMLBuilder();
  }

  /**
   * Create a builder for main .rels file with standard office document relationship
   */
  public static RelationshipXMLBuilder createMainRels() {
    return new RelationshipXMLBuilder()
        .addOfficeDocument("rId1", "ppt/presentation.xml");
  }

  /**
   * Create a builder for presentation.xml.rels with standard relationships
   */
  public static RelationshipXMLBuilder createPresentationRels() {
    return new RelationshipXMLBuilder()
        .addSlideMaster("rId1", "slideMasters/slideMaster1.xml")
        .addTheme("rId5", "theme/theme1.xml");
  }

  /**
   * Create a builder for slide relationships with standard layout and theme
   */
  public static RelationshipXMLBuilder createSlideRels(String layoutRid, String themeRid) {
    return new RelationshipXMLBuilder()
        .addSlideLayout(layoutRid, XMLConstants.DEFAULT_SLIDE_LAYOUT_TARGET)
        .addTheme(themeRid, XMLConstants.DEFAULT_THEME_TARGET);
  }

  // Helper class for type safety
  private static class RelationshipEntry {
    final String id;
    final String type;
    final String target;
    
    RelationshipEntry(String id, String type, String target) {
      this.id = id;
      this.type = type;
      this.target = target;
    }
  }
}