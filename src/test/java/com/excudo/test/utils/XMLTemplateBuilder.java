package com.excudo.test.utils;

import com.excudo.core.utils.XMLConstants;
import com.excudo.xml.builders.PresentationXMLBuilder;
import com.excudo.xml.builders.RelationshipXMLBuilder;
import com.excudo.xml.builders.ContentTypesXMLBuilder;

/**
 * Builder utility for generating consistent test XML structures
 * Eliminates magic strings and provides type-safe XML generation
 * Now uses the new fluent XML builders for consistency
 */
public final class XMLTemplateBuilder {

  /**
   * Build a complete presentation.xml structure for testing
   */
  public static String buildPresentationXml() {
    return PresentationXMLBuilder.createWithDefaults()
        .withSlideDimensions(TestConstants.SLIDE_WIDTH_EMU, TestConstants.SLIDE_HEIGHT_EMU)
        .withNotesDimensions(TestConstants.NOTES_WIDTH_EMU, TestConstants.NOTES_HEIGHT_EMU)
        .withLanguage(TestConstants.DEFAULT_LANGUAGE)
        .build();
  }

  /**
   * Build presentation.xml.rels relationship file
   */
  public static String buildPresentationRelationships() {
    return RelationshipXMLBuilder.create()
        .addSlideMaster(TestConstants.TEST_SLIDE_MASTER_RID, TestConstants.TEST_SLIDE_MASTER_TARGET)
        .addTheme(TestConstants.TEST_THEME_RID, TestConstants.TEST_THEME_TARGET)
        .build();
  }

  /**
   * Build main .rels relationship file
   */
  public static String buildMainRelationships() {
    return RelationshipXMLBuilder.createMainRels().build();
  }

  /**
   * Build [Content_Types].xml file
   */
  public static String buildContentTypes() {
    return ContentTypesXMLBuilder.createForTesting().build();
  }

  /**
   * Build a slide XML with duplicate SPID for validation testing
   */
  public static String buildSlideWithDuplicateSpid(int duplicateSpid) {
    return XMLConstants.XML_DECLARATION + "\n" +
        "<p:sld xmlns:p=\"" + XMLConstants.PRESENTATION_NS + "\">\n" +
        "<p:cSld>\n" +
        "<p:spTree>\n" +
        "<p:sp><p:nvSpPr><p:cNvPr id=\"" + duplicateSpid + "\" name=\"" + 
        TestConstants.TEST_DUPLICATE_SHAPE_NAME + "\"/></p:nvSpPr></p:sp>\n" +
        "</p:spTree>\n" +
        "</p:cSld>\n" +
        "</p:sld>";
  }

  private XMLTemplateBuilder() {
    // Utility class - no instantiation
  }
}