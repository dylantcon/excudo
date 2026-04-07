package com.excudo.test.utils;

/**
 * Centralized constants for test files to eliminate magic strings
 * Provides consistent values for test XML generation and validation
 */
public final class TestConstants {

  // Test slide master configuration
  public static final String TEST_SLIDE_MASTER_ID = "2147483660";
  public static final String TEST_SLIDE_MASTER_RID = "rId1";
  public static final String TEST_THEME_RID = "rId5";
  
  // Standard presentation dimensions (PowerPoint default)
  public static final String SLIDE_WIDTH_EMU = "12192000";  // 12.19 inches
  public static final String SLIDE_HEIGHT_EMU = "6858000";  // 6.86 inches
  public static final String NOTES_WIDTH_EMU = "6858000";
  public static final String NOTES_HEIGHT_EMU = "9144000";
  
  // Test slide and shape identifiers
  public static final int TEST_SLIDE_ID_START = 256;
  public static final String TEST_SLIDE_TITLE_1 = "Test Slide 1";
  public static final String TEST_SLIDE_TITLE_2 = "Test Slide 2";
  
  // Relationship targets for test files
  public static final String TEST_SLIDE_MASTER_TARGET = "slideMasters/slideMaster1.xml";
  public static final String TEST_SLIDE_LAYOUT_TARGET = "slideLayouts/slideLayout1.xml";
  public static final String TEST_THEME_TARGET = "theme/theme1.xml";
  
  // Content type definitions for test PPTX files
  public static final String CONTENT_TYPE_RELS = "application/vnd.openxmlformats-package.relationships+xml";
  public static final String CONTENT_TYPE_XML = "application/xml";
  public static final String CONTENT_TYPE_PRESENTATION = "application/vnd.openxmlformats-presentationml.presentation.main+xml";
  public static final String CONTENT_TYPE_SLIDE_MASTER = "application/vnd.openxmlformats-presentationml.slideMaster+xml";
  public static final String CONTENT_TYPE_SLIDE_LAYOUT = "application/vnd.openxmlformats-presentationml.slideLayout+xml";
  public static final String CONTENT_TYPE_THEME = "application/vnd.openxmlformats-officedocument.theme+xml";
  
  // Test validation messages
  public static final String SHOULD_DETECT_SPIDS_MESSAGE = "Should detect SPIDs from generated slides";
  public static final String SHOULD_HAVE_SPID_INFO_MESSAGE = "Should have info for SPID";
  public static final String SHOULD_HAVE_VALID_SLIDE_NUMBER_MESSAGE = "Should have valid slide number for SPID";
  public static final String SHOULD_NOT_HAVE_SPID_ERRORS_MESSAGE = "Should not have SPID errors in generated slides";
  public static final String SHOULD_DETECT_MANUAL_CONFLICTS_MESSAGE = "Should detect manually created SPID conflicts";
  public static final String SHOULD_REPORT_ERRORS_MESSAGE = "Should report errors for manual duplicates";
  
  // Test SPID values for duplicate testing
  public static final int TEST_DUPLICATE_SPID = 999;
  public static final String TEST_DUPLICATE_SHAPE_NAME = "Duplicate SPID";
  
  // Language and locale settings
  public static final String DEFAULT_LANGUAGE = "en-US";
  
  private TestConstants() {
    // Utility class - no instantiation
  }
}