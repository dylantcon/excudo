package com.excudo.xml.builders;

import com.excudo.core.utils.XMLConstants;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating presentation.xml structures
 * Provides type-safe, readable API for generating OOXML presentation documents
 */
public class PresentationXMLBuilder {
  
  private final List<SlideIdEntry> slideIds = new ArrayList<>();
  private final List<SlideMasterEntry> slideMasters = new ArrayList<>();
  private String slideWidth = "12192000";  // Default PowerPoint width
  private String slideHeight = "6858000";  // Default PowerPoint height
  private String notesWidth = "6858000";
  private String notesHeight = "9144000";
  private String language = "en-US";

  /**
   * Add a slide master to the presentation
   */
  public PresentationXMLBuilder addSlideMaster(String id, String relationshipId) {
    slideMasters.add(new SlideMasterEntry(id, relationshipId));
    return this;
  }

  /**
   * Add a slide to the presentation
   */
  public PresentationXMLBuilder addSlide(int slideId, String relationshipId) {
    slideIds.add(new SlideIdEntry(slideId, relationshipId));
    return this;
  }

  /**
   * Set slide dimensions in EMU (English Metric Units)
   */
  public PresentationXMLBuilder withSlideDimensions(String width, String height) {
    this.slideWidth = width;
    this.slideHeight = height;
    return this;
  }

  /**
   * Set notes dimensions in EMU
   */
  public PresentationXMLBuilder withNotesDimensions(String width, String height) {
    this.notesWidth = width;
    this.notesHeight = height;
    return this;
  }

  /**
   * Set default language
   */
  public PresentationXMLBuilder withLanguage(String language) {
    this.language = language;
    return this;
  }

  /**
   * Build the complete presentation.xml content
   */
  public String build() {
    StringBuilder xml = new StringBuilder();
    xml.append(XMLConstants.XML_DECLARATION).append("\n");
    xml.append("<p:presentation xmlns:a=\"").append(XMLConstants.DRAWING_NS).append("\"")
       .append(" xmlns:r=\"").append(XMLConstants.RELATIONSHIPS_NS).append("\"")
       .append(" xmlns:p=\"").append(XMLConstants.PRESENTATION_NS).append("\">");
    if (!slideMasters.isEmpty()) {
      xml.append("<p:sldMasterIdLst>");
      for (SlideMasterEntry master : slideMasters) {
        xml.append("<p:sldMasterId id=\"").append(master.id)
           .append("\" r:id=\"").append(master.relationshipId).append("\"/>");
      }
      xml.append("</p:sldMasterIdLst>");
    }
    xml.append("<p:sldIdLst>");
    for (SlideIdEntry slide : slideIds) {
      xml.append(String.format(XMLConstants.SLIDE_ID_ELEMENT_TEMPLATE, slide.slideId, slide.relationshipId));
    }
    xml.append("</p:sldIdLst>");
    xml.append("<p:sldSz cx=\"").append(slideWidth)
       .append("\" cy=\"").append(slideHeight).append("\"/>");
    xml.append("<p:notesSz cx=\"").append(notesWidth)
       .append("\" cy=\"").append(notesHeight).append("\"/>");
    xml.append("<p:defaultTextStyle>");
    xml.append("<a:defPPr>");
    xml.append("<a:defRPr lang=\"").append(language).append("\"/>");
    xml.append("</a:defPPr>");
    xml.append("</p:defaultTextStyle>");
    xml.append("<p:extLst>");
    xml.append("<p:ext uri=\"").append(XMLConstants.ExtensionUri.SLIDE_GUIDE_LIST).append("\">");
    xml.append("<p15:sldGuideLst xmlns:p15=\"").append(XMLConstants.OfficeNamespace.POWERPOINT_2012).append("\"/>");
    xml.append("</p:ext>");
    xml.append("</p:extLst>");
    xml.append("</p:presentation>");
    return xml.toString();
  }

  /**
   * Create a new empty builder
   */
  public static PresentationXMLBuilder create() {
    return new PresentationXMLBuilder();
  }

  /**
   * Create a builder with standard PowerPoint defaults
   */
  public static PresentationXMLBuilder createWithDefaults() {
    return new PresentationXMLBuilder()
        .addSlideMaster("2147483660", "rId1");
  }

  // Helper classes for type safety
  private static class SlideIdEntry {
    final int slideId;
    final String relationshipId;
    
    SlideIdEntry(int slideId, String relationshipId) {
      this.slideId = slideId;
      this.relationshipId = relationshipId;
    }
  }
  
  private static class SlideMasterEntry {
    final String id;
    final String relationshipId;
    
    SlideMasterEntry(String id, String relationshipId) {
      this.id = id;
      this.relationshipId = relationshipId;
    }
  }
}