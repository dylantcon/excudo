package com.excudo.xml.writers;

import com.excudo.core.model.PPTXDocument;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RelationshipValidator -- read-only validation of OOXML relationship consistency.
 * All tests operate through PPTXDocument (in-memory mode only).
 */
class RelationshipValidatorTest {

  private DocumentBuilder documentBuilder;
  private XPath xpath;

  @BeforeEach
  void setUp() throws Exception {
    documentBuilder = WriterTestFixtures.createDocumentBuilder();
    xpath = WriterTestFixtures.createConfiguredXPath();
  }

  private PPTXDocument buildPptxDoc(String relsXml, String presXml) throws Exception {
    PPTXDocument doc = PPTXDocument.createEmpty();
    if (relsXml != null) {
      doc.putXmlPart("ppt/_rels/presentation.xml.rels",
          documentBuilder.parse(new org.xml.sax.InputSource(new java.io.StringReader(relsXml))));
    }
    if (presXml != null) {
      doc.putXmlPart("ppt/presentation.xml",
          documentBuilder.parse(new org.xml.sax.InputSource(new java.io.StringReader(presXml))));
    }
    return doc;
  }

  // ========== validateAllRelationships ==========

  @Test
  @DisplayName("No errors for external URL relationships")
  void testValidateAll_skipsExternalUrls() throws Exception {
    String relsXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
        """;
    String presXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <p:sldIdLst/>
        </p:presentation>
        """;
    PPTXDocument pptxDoc = buildPptxDoc(relsXml, presXml);

    Map<String, RelationshipManager.RelationshipInfo> registry = new HashMap<>();
    registry.put("rId10", new RelationshipManager.RelationshipInfo(
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink",
        "https://example.com"));
    registry.put("rId11", new RelationshipManager.RelationshipInfo(
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink",
        "mailto:test@example.com"));

    RelationshipPathResolver pathResolver = new RelationshipPathResolver();
    RelationshipValidator validator = new RelationshipValidator(registry, documentBuilder, xpath, pathResolver);
    validator.setPPTXDocument(pptxDoc);

    RelationshipManager.ValidationResult result = validator.validateAllRelationships();

    boolean foundExternalError = result.getErrors().stream()
        .anyMatch(e -> e.contains("example.com") || e.contains("mailto:"));
    assertFalse(foundExternalError, "Should not flag external URLs as missing files");
  }

  // ========== validatePresentationRelationshipPattern ==========

  @Test
  @DisplayName("Detects missing presentation.xml.rels file")
  void testPatternValidation_missingRelsFile() throws Exception {
    String presXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"/>
        """;
    PPTXDocument pptxDoc = buildPptxDoc(null, presXml);

    Map<String, RelationshipManager.RelationshipInfo> registry = new HashMap<>();
    RelationshipPathResolver pathResolver = new RelationshipPathResolver();
    RelationshipValidator validator = new RelationshipValidator(registry, documentBuilder, xpath, pathResolver);
    validator.setPPTXDocument(pptxDoc);

    RelationshipManager.ValidationResult result = validator.validatePresentationRelationshipPattern();

    boolean hasMissingError = result.getErrors().stream()
        .anyMatch(e -> e.contains("Missing presentation.xml.rels"));
    assertTrue(hasMissingError, "Should detect missing presentation.xml.rels");
  }

  @Test
  @DisplayName("Warns when slideMaster is not rId1")
  void testPatternValidation_slideMasterNotRId1() throws Exception {
    String presXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <p:sldMasterIdLst>
            <p:sldMasterId id="2147483648" r:id="rId5"/>
          </p:sldMasterIdLst>
          <p:sldIdLst/>
        </p:presentation>
        """;
    String relsXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
        </Relationships>
        """;
    PPTXDocument pptxDoc = buildPptxDoc(relsXml, presXml);

    Map<String, RelationshipManager.RelationshipInfo> registry = new HashMap<>();
    RelationshipPathResolver pathResolver = new RelationshipPathResolver();
    RelationshipValidator validator = new RelationshipValidator(registry, documentBuilder, xpath, pathResolver);
    validator.setPPTXDocument(pptxDoc);

    RelationshipManager.ValidationResult result = validator.validatePresentationRelationshipPattern();

    boolean hasWarning = result.getWarnings().stream()
        .anyMatch(w -> w.contains("rId1") && w.contains("rId5"));
    assertTrue(hasWarning, "Should warn that slideMaster is not rId1");
  }

  @Test
  @DisplayName("Detects non-consecutive slide rIds")
  void testPatternValidation_nonConsecutiveSlideRIds() throws Exception {
    String presXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <p:sldMasterIdLst>
            <p:sldMasterId id="2147483648" r:id="rId1"/>
          </p:sldMasterIdLst>
          <p:sldIdLst>
            <p:sldId id="256" r:id="rId2"/>
            <p:sldId id="257" r:id="rId4"/>
          </p:sldIdLst>
        </p:presentation>
        """;
    String relsXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
          <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
        </Relationships>
        """;
    PPTXDocument pptxDoc = buildPptxDoc(relsXml, presXml);

    Map<String, RelationshipManager.RelationshipInfo> registry = new HashMap<>();
    RelationshipPathResolver pathResolver = new RelationshipPathResolver();
    RelationshipValidator validator = new RelationshipValidator(registry, documentBuilder, xpath, pathResolver);
    validator.setPPTXDocument(pptxDoc);

    RelationshipManager.ValidationResult result = validator.validatePresentationRelationshipPattern();

    boolean hasGapError = result.getErrors().stream()
        .anyMatch(e -> e.contains("Non-consecutive"));
    assertTrue(hasGapError, "Should detect non-consecutive slide rIds");
  }

  @Test
  @DisplayName("Detects notesMaster before slides")
  void testPatternValidation_notesMasterBeforeSlides() throws Exception {
    String presXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <p:sldMasterIdLst>
            <p:sldMasterId id="2147483648" r:id="rId1"/>
          </p:sldMasterIdLst>
          <p:sldIdLst>
            <p:sldId id="256" r:id="rId3"/>
          </p:sldIdLst>
          <p:notesMasterIdLst>
            <p:notesMasterId r:id="rId2"/>
          </p:notesMasterIdLst>
        </p:presentation>
        """;
    String relsXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesMaster" Target="notesMasters/notesMaster1.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
        </Relationships>
        """;
    PPTXDocument pptxDoc = buildPptxDoc(relsXml, presXml);

    Map<String, RelationshipManager.RelationshipInfo> registry = new HashMap<>();
    RelationshipPathResolver pathResolver = new RelationshipPathResolver();
    RelationshipValidator validator = new RelationshipValidator(registry, documentBuilder, xpath, pathResolver);
    validator.setPPTXDocument(pptxDoc);

    RelationshipManager.ValidationResult result = validator.validatePresentationRelationshipPattern();

    boolean hasOrderingError = result.getErrors().stream()
        .anyMatch(e -> e.contains("NotesMaster") && e.contains("should come after"));
    assertTrue(hasOrderingError, "Should detect notesMaster rId before slide rIds");
  }

  @Test
  @DisplayName("Valid consecutive pattern produces no errors")
  void testPatternValidation_validConsecutivePattern() throws Exception {
    String presXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <p:sldMasterIdLst>
            <p:sldMasterId id="2147483648" r:id="rId1"/>
          </p:sldMasterIdLst>
          <p:sldIdLst>
            <p:sldId id="256" r:id="rId2"/>
            <p:sldId id="257" r:id="rId3"/>
          </p:sldIdLst>
        </p:presentation>
        """;
    String relsXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
        </Relationships>
        """;
    PPTXDocument pptxDoc = buildPptxDoc(relsXml, presXml);

    Map<String, RelationshipManager.RelationshipInfo> registry = new HashMap<>();
    RelationshipPathResolver pathResolver = new RelationshipPathResolver();
    RelationshipValidator validator = new RelationshipValidator(registry, documentBuilder, xpath, pathResolver);
    validator.setPPTXDocument(pptxDoc);

    RelationshipManager.ValidationResult result = validator.validatePresentationRelationshipPattern();

    boolean hasConsecutiveError = result.getErrors().stream()
        .anyMatch(e -> e.contains("Non-consecutive"));
    assertFalse(hasConsecutiveError, "Valid consecutive pattern should not report gaps");
  }
}
