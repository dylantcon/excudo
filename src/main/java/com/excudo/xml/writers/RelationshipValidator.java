package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.*;
import java.io.*;
import java.util.*;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;

/**
 * Read-only validation of OOXML relationship consistency and PowerPoint-specific patterns.
 * All methods are pure queries -- zero mutations to any state.
 */
public class RelationshipValidator {

  private final Map<String, RelationshipManager.RelationshipInfo> globalRelationshipRegistry;
  private final DocumentBuilder documentBuilder;
  private final XPath xpath;
  private final RelationshipPathResolver pathResolver;
  private com.excudo.core.model.PPTXDocument pptxDocument;

  public RelationshipValidator(Map<String, RelationshipManager.RelationshipInfo> globalRelationshipRegistry,
                               DocumentBuilder documentBuilder, XPath xpath,
                               RelationshipPathResolver pathResolver) {
    this.globalRelationshipRegistry = globalRelationshipRegistry;
    this.documentBuilder = documentBuilder;
    this.xpath = xpath;
    this.pathResolver = pathResolver;
  }

  public void setPPTXDocument(com.excudo.core.model.PPTXDocument pptxDocument) {
    this.pptxDocument = pptxDocument;
  }

  /**
   * Validates that all relationships in the presentation are consistent and valid.
   */
  public RelationshipManager.ValidationResult validateAllRelationships() throws XMLParsingException {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    for (Map.Entry<String, RelationshipManager.RelationshipInfo> entry : globalRelationshipRegistry.entrySet()) {
      String relationshipId = entry.getKey();
      RelationshipManager.RelationshipInfo info = entry.getValue();

      if (!isExternalTarget(info.getTarget())) {
        String partName = pathResolver.resolveRelationshipPartName(info.getTarget());
        boolean exists = (pptxDocument != null) && pptxDocument.hasPart(partName);
        if (pptxDocument != null && !exists) {
          errors.add("Relationship " + relationshipId + " points to non-existent target: " + info.getTarget());
        }
      }
    }

    Set<String> duplicateCheck = new HashSet<>();
    for (String id : globalRelationshipRegistry.keySet()) {
      if (!duplicateCheck.add(id)) {
        errors.add("Duplicate relationship ID detected: " + id);
      }
    }

    RelationshipManager.ValidationResult patternValidation = validatePresentationRelationshipPattern();
    errors.addAll(patternValidation.getErrors());
    warnings.addAll(patternValidation.getWarnings());

    return new RelationshipManager.ValidationResult(errors, warnings);
  }

  /**
   * Validates the PowerPoint-specific relationship pattern in presentation.xml.rels.
   */
  public RelationshipManager.ValidationResult validatePresentationRelationshipPattern() throws XMLParsingException {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    try {
      Document relsDoc = null;
      Document presDoc = null;

      if (pptxDocument != null) {
        relsDoc = pptxDocument.getXmlPart("ppt/_rels/presentation.xml.rels");
        presDoc = pptxDocument.getXmlPart("ppt/presentation.xml");
      }

      if (relsDoc == null) {
        errors.add("Missing presentation.xml.rels file");
        return new RelationshipManager.ValidationResult(errors, warnings);
      }
      if (presDoc == null) {
        errors.add("Missing presentation.xml file");
        return new RelationshipManager.ValidationResult(errors, warnings);
      }

      Map<String, String> ridToType = new HashMap<>();
      Map<String, String> ridToTarget = new HashMap<>();

      NodeList relationships = relsDoc.getElementsByTagName("Relationship");
      for (int i = 0; i < relationships.getLength(); i++) {
        Element rel = (Element) relationships.item(i);
        String id = rel.getAttribute("Id");
        String type = rel.getAttribute("Type");
        String target = rel.getAttribute("Target");
        ridToType.put(id, type);
        ridToTarget.put(id, target);
      }

      String slideMasterRId = null;
      String notesMasterRId = null;
      List<String> slideRIds = new ArrayList<>();

      NodeList slideMasterIds = presDoc.getElementsByTagName("p:sldMasterId");
      if (slideMasterIds.getLength() > 0) {
        Element slideMasterEl = (Element) slideMasterIds.item(0);
        slideMasterRId = slideMasterEl.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
      }

      NodeList notesMasterIds = presDoc.getElementsByTagName("p:notesMasterId");
      if (notesMasterIds.getLength() > 0) {
        Element notesMasterEl = (Element) notesMasterIds.item(0);
        notesMasterRId = notesMasterEl.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
      }

      NodeList slideIds = presDoc.getElementsByTagName("p:sldId");
      for (int i = 0; i < slideIds.getLength(); i++) {
        Element slideEl = (Element) slideIds.item(i);
        String slideRId = slideEl.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
        slideRIds.add(slideRId);
      }

      if (slideMasterRId != null && !slideMasterRId.equals("rId1")) {
        warnings.add("SlideMaster should typically be rId1, found: " + slideMasterRId);
      }

      List<Integer> slideNumbers = new ArrayList<>();
      for (String slideRId : slideRIds) {
        if (slideRId.startsWith("rId")) {
          try {
            int num = Integer.parseInt(slideRId.substring(3));
            slideNumbers.add(num);
          } catch (NumberFormatException e) {
            warnings.add("Non-numeric slide relationship ID: " + slideRId);
          }
        }
      }

      Collections.sort(slideNumbers);

      if (!slideNumbers.isEmpty()) {
        if (slideNumbers.get(0) != 2) {
          warnings.add("Slides should start from rId2, found starting at: rId" + slideNumbers.get(0));
        }

        for (int i = 1; i < slideNumbers.size(); i++) {
          if (slideNumbers.get(i) != slideNumbers.get(i-1) + 1) {
            errors.add("Non-consecutive slide rIds detected: gap between rId" +
                      slideNumbers.get(i-1) + " and rId" + slideNumbers.get(i));
          }
        }
      }

      if (notesMasterRId != null && !slideNumbers.isEmpty()) {
        try {
          int notesMasterNum = Integer.parseInt(notesMasterRId.substring(3));
          int lastSlideNum = slideNumbers.get(slideNumbers.size() - 1);

          if (notesMasterNum <= lastSlideNum) {
            errors.add("NotesMaster rId (" + notesMasterRId + ") should come after all slide rIds (last: rId" + lastSlideNum + ")");
          } else if (notesMasterNum != lastSlideNum + 1) {
            warnings.add("NotesMaster rId (" + notesMasterRId + ") should ideally be immediately after last slide (rId" + (lastSlideNum + 1) + ")");
          }
        } catch (NumberFormatException e) {
          warnings.add("Non-numeric notesMaster relationship ID: " + notesMasterRId);
        }
      }

      for (String slideRId : slideRIds) {
        String type = ridToType.get(slideRId);
        if (type == null) {
          errors.add("Slide rId " + slideRId + " referenced in presentation.xml but not found in presentation.xml.rels");
        } else if (!type.contains("slide") || type.contains("slideMaster") || type.contains("slideLayout")) {
          errors.add("Slide rId " + slideRId + " has incorrect type in presentation.xml.rels: " + type);
        }
      }

      if (notesMasterRId != null) {
        String type = ridToType.get(notesMasterRId);
        if (type == null) {
          errors.add("NotesMaster rId " + notesMasterRId + " referenced in presentation.xml but not found in presentation.xml.rels");
        } else if (!type.contains("notesMaster")) {
          errors.add("NotesMaster rId " + notesMasterRId + " has incorrect type in presentation.xml.rels: " + type);
        }
      }

    } catch (Exception e) {
      errors.add("Failed to validate presentation relationship pattern: " + e.getMessage());
    }

    return new RelationshipManager.ValidationResult(errors, warnings);
  }

  private boolean isExternalTarget(String target) {
    return target.startsWith("http://") || target.startsWith("https://") || target.startsWith("mailto:");
  }
}
