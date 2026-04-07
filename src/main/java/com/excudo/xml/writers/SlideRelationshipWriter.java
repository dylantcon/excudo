package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.*;
import java.io.File;
import java.util.*;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.OOXMLAttributeOrder;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Handles slide-level relationship file creation, copying, and presentation-level
 * relationship management including rId cascading and notesMaster ordering.
 */
public class SlideRelationshipWriter {

  private static final ComponentLogger logger = Logger.xml();

  private final RelationshipManager manager;
  private final RelationshipPathResolver pathResolver;
  private final DocumentBuilder documentBuilder;
  private final XPath xpath;

  public SlideRelationshipWriter(RelationshipManager manager,
                                  RelationshipPathResolver pathResolver,
                                  DocumentBuilder documentBuilder, XPath xpath) {
    this.manager = manager;
    this.pathResolver = pathResolver;
    this.documentBuilder = documentBuilder;
    this.xpath = xpath;
  }

  /**
   * Creates a standard slide relationship file with layout and theme relationships.
   */
  public RelationshipManager.RelationshipCreationResult createSlideRelationships(int slideNumber,
      String layoutTarget, String themeTarget) throws XMLParsingException {
    if (slideNumber < 1) {
      throw new IllegalArgumentException("slideNumber must be positive");
    }

    try {
      Document relsDoc = documentBuilder.newDocument();
      Element relationships = relsDoc.createElementNS(
          XMLConstants.PACKAGE_RELATIONSHIPS_NS, "Relationships");
      relsDoc.appendChild(relationships);

      List<String> createdRelationshipIds = new ArrayList<>();

      File relsFile = pathResolver.getSlideRelationshipFile(slideNumber);
      String relsFilePath = pathResolver.getRelativePathFromExtractedDir(relsFile);

      String layoutRId = manager.allocateLocalRelationshipId(relsFilePath);
      String finalLayoutTarget = layoutTarget != null ? layoutTarget : XMLConstants.DEFAULT_SLIDE_LAYOUT_TARGET;
      Element layoutRel = manager.createRelationshipElementPublic(relsDoc, layoutRId,
          XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT, finalLayoutTarget);
      relationships.appendChild(layoutRel);
      createdRelationshipIds.add(layoutRId);

      manager.registerRelationshipPublic(layoutRId, XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT, finalLayoutTarget);

      if (themeTarget != null) {
        String themeRId = manager.allocateLocalRelationshipId(relsFilePath);
        Element themeRel = manager.createRelationshipElementPublic(relsDoc, themeRId,
            XMLConstants.RELATIONSHIP_TYPE_THEME, themeTarget);
        relationships.appendChild(themeRel);
        createdRelationshipIds.add(themeRId);

        manager.registerRelationshipPublic(themeRId, XMLConstants.RELATIONSHIP_TYPE_THEME, themeTarget);
      }

      manager.writeRelationshipDocumentPublic(relsDoc, relsFile);

      String cacheKey = pathResolver.getRelativePathFromExtractedDir(relsFile);
      manager.cacheRelationshipDocument(cacheKey, relsDoc);

      return new RelationshipManager.RelationshipCreationResult(relsFile, createdRelationshipIds);

    } catch (Exception e) {
      throw new XMLParsingException("Failed to create slide relationships for slide " + slideNumber, e);
    }
  }

  /**
   * Copies relationships from a source slide to a destination slide with ID remapping.
   */
  public RelationshipManager.RelationshipCopyResult copySlideRelationships(int sourceSlideNumber,
      int destinationSlideNumber, boolean forceNewIds) throws XMLParsingException {
    if (sourceSlideNumber < 1 || destinationSlideNumber < 1) {
      throw new IllegalArgumentException("Slide numbers must be positive");
    }

    try {
      File sourceRelsFile = pathResolver.getSlideRelationshipFile(sourceSlideNumber);
      String sourcePartName = pathResolver.getRelativePathFromExtractedDir(sourceRelsFile);
      boolean sourceExists = manager.getPPTXDocument() != null
          ? manager.getPPTXDocument().hasPart(sourcePartName)
          : sourceRelsFile.exists();
      if (!sourceExists) {
        RelationshipManager.RelationshipCreationResult defaultResult = createSlideRelationships(destinationSlideNumber, null, null);
        return new RelationshipManager.RelationshipCopyResult(defaultResult.getRelationshipFile(),
            Collections.emptyMap(), defaultResult.getCreatedRelationshipIds());
      }

      Document sourceRelsDoc = manager.parseRelationshipDocumentPublic(sourceRelsFile);
      Document destRelsDoc = (Document) sourceRelsDoc.cloneNode(true);

      Map<String, String> idMappings = new HashMap<>();
      List<String> newRelationshipIds = new ArrayList<>();

      File destRelsFile = pathResolver.getSlideRelationshipFile(destinationSlideNumber);
      String destRelsFilePath = pathResolver.getRelativePathFromExtractedDir(destRelsFile);

      NodeList relationshipElements = destRelsDoc.getElementsByTagName("Relationship");
      for (int i = 0; i < relationshipElements.getLength(); i++) {
        Element relationshipEl = (Element) relationshipElements.item(i);
        String oldId = relationshipEl.getAttribute("Id");
        String type = relationshipEl.getAttribute("Type");
        String target = relationshipEl.getAttribute("Target");

        String newId = manager.allocateLocalRelationshipId(destRelsFilePath);
        relationshipEl.setAttribute("Id", newId);
        manager.registerRelationshipPublic(newId, type, target);

        idMappings.put(oldId, newId);
        newRelationshipIds.add(newId);
      }

      manager.writeRelationshipDocumentPublic(destRelsDoc, destRelsFile);

      String cacheKey = pathResolver.getRelativePathFromExtractedDir(destRelsFile);
      manager.cacheRelationshipDocument(cacheKey, destRelsDoc);

      return new RelationshipManager.RelationshipCopyResult(destRelsFile, idMappings, newRelationshipIds);

    } catch (Exception e) {
      throw new XMLParsingException("Failed to copy slide relationships from slide " +
          sourceSlideNumber + " to slide " + destinationSlideNumber, e);
    }
  }

  /**
   * Adds a relationship to the presentation.xml.rels file (in-memory path).
   */
  public void addPresentationRelationship(String relationshipId, String relationshipType, String target) throws XMLParsingException {
    try {
      final String PRES_RELS_PART = "ppt/_rels/presentation.xml.rels";
      com.excudo.core.model.PPTXDocument pptxDoc = manager.getPPTXDocument();

      Document relsDoc;
      if (pptxDoc != null) {
        relsDoc = pptxDoc.getXmlPart(PRES_RELS_PART);
      } else {
        relsDoc = null;
      }
      if (relsDoc == null) {
        relsDoc = documentBuilder.newDocument();
        Element relationships = relsDoc.createElementNS(XMLConstants.PACKAGE_RELATIONSHIPS_NS, "Relationships");
        relsDoc.appendChild(relationships);
      }

      Element relationships = relsDoc.getDocumentElement();

      if (relationshipType.equals(XMLConstants.RELATIONSHIP_TYPE_SLIDE)) {
        cascadeRelationshipIds(relsDoc, relationshipId);
      }

      Element existingRelationship = findRelationshipById(relsDoc, relationshipId);
      if (existingRelationship != null) {
        logger.debug("Found existing relationship " + relationshipId +
                          ", target: " + existingRelationship.getAttribute("Target") +
                          ", type: " + existingRelationship.getAttribute("Type"));

        String existingTarget = existingRelationship.getAttribute("Target");
        String existingType = existingRelationship.getAttribute("Type");
        if (target.equals(existingTarget) && relationshipType.equals(existingType)) {
          logger.debug("Duplicate relationship detected - skipping");
          return;
        }

        String newId = findNextAvailableRelationshipId(relsDoc);
        logger.debug("Reassigning existing relationship from " + relationshipId + " to " + newId);
        existingRelationship.setAttribute("Id", newId);

        manager.reassignRelationshipId(relationshipId, newId);

        if (existingTarget.contains("notesMaster")) {
          updateNotesMasterReferenceInPresentationXml(relationshipId, newId);
        }
      }

      Element newRelationship = manager.createRelationshipElementPublic(relsDoc, relationshipId, relationshipType, target);
      relationships.appendChild(newRelationship);

      if (pptxDoc != null) {
        pptxDoc.putXmlPart(PRES_RELS_PART, relsDoc);
      } else {
        throw new UnsupportedOperationException("PPTXDocument not set -- cannot persist presentation.xml.rels");
      }

      manager.registerRelationshipPublic(relationshipId, relationshipType, target);
      manager.cacheRelationshipDocument(PRES_RELS_PART, relsDoc);

    } catch (Exception e) {
      throw new XMLParsingException("Failed to add presentation relationship " + relationshipId, e);
    }
  }

  /**
   * Cascades relationship IDs when inserting a slide.
   */
  void cascadeRelationshipIds(Document relsDoc, String newSlideRId) throws XMLParsingException {
    try {
      if (!newSlideRId.startsWith("rId")) {
        return;
      }
      int newSlideIdNum = Integer.parseInt(newSlideRId.substring(3));

      // Use TreeMap with descending key order to process cascades from highest
      // rId to lowest. This prevents collision: rId5->rId6 must happen before
      // rId4->rId5, otherwise a renamed element could be found by a later lookup.
      TreeMap<Integer, String[]> rIdUpdates = new TreeMap<>(Comparator.reverseOrder());
      NodeList relationships = relsDoc.getElementsByTagName("Relationship");

      for (int i = 0; i < relationships.getLength(); i++) {
        Element rel = (Element) relationships.item(i);
        String currentId = rel.getAttribute("Id");

        if (currentId.startsWith("rId")) {
          try {
            int currentIdNum = Integer.parseInt(currentId.substring(3));

            if (currentIdNum >= newSlideIdNum) {
              String newId = "rId" + (currentIdNum + 1);
              rIdUpdates.put(currentIdNum, new String[]{currentId, newId});
              logger.debug("Cascading " + currentId + " to " + newId +
                               " (target: " + rel.getAttribute("Target") + ")");
            }
          } catch (NumberFormatException e) {
            // Skip non-numeric rIds
          }
        }
      }

      for (String[] update : rIdUpdates.values()) {
        Element rel = findRelationshipById(relsDoc, update[0]);
        if (rel != null) {
          rel.setAttribute("Id", update[1]);

          String target = rel.getAttribute("Target");
          if (target.contains("notesMaster")) {
            updateNotesMasterReferenceInPresentationXml(update[0], update[1]);
          }

          // When cascading slide relationships, the Target must also shift
          // because renameSubsequentSlides already renamed the physical files
          String type = rel.getAttribute("Type");
          if (type.contains("/slide") && !type.contains("slideMaster") && !type.contains("slideLayout")
              && target.matches("slides/slide\\d+\\.xml")) {
            int oldSlideNum = Integer.parseInt(target.replaceAll("\\D+", ""));
            String newTarget = "slides/slide" + (oldSlideNum + 1) + ".xml";
            rel.setAttribute("Target", newTarget);
            logger.debug("Cascaded target " + target + " -> " + newTarget);
          }
        }
      }

    } catch (Exception e) {
      throw new XMLParsingException("Failed to cascade relationship IDs", e);
    }
  }

  /**
   * Ensures notesMaster relationship comes after all slide relationships.
   */
  void ensureNotesMasterComesAfterSlides(Document relsDoc, String newSlideRId) throws XMLParsingException {
    try {
      Element relationships = relsDoc.getDocumentElement();
      NodeList relationshipElements = relationships.getElementsByTagName("Relationship");

      Element notesMasterRel = null;
      String notesMasterRId = null;
      int highestSlideId = 0;

      for (int i = 0; i < relationshipElements.getLength(); i++) {
        Element rel = (Element) relationshipElements.item(i);
        String id = rel.getAttribute("Id");
        String type = rel.getAttribute("Type");

        if (type.contains("notesMaster")) {
          notesMasterRel = rel;
          notesMasterRId = id;
        } else if (type.contains("slide") && !type.contains("slideMaster") && !type.contains("slideLayout")) {
          if (id.startsWith("rId")) {
            try {
              int idNum = Integer.parseInt(id.substring(3));
              highestSlideId = Math.max(highestSlideId, idNum);
            } catch (NumberFormatException e) {
              // Skip
            }
          }
        }
      }

      if (newSlideRId != null && newSlideRId.startsWith("rId")) {
        try {
          int newSlideIdNum = Integer.parseInt(newSlideRId.substring(3));
          highestSlideId = Math.max(highestSlideId, newSlideIdNum);
        } catch (NumberFormatException e) {
          // Skip
        }
      }

      if (notesMasterRel != null && notesMasterRId != null) {
        try {
          int notesMasterIdNum = Integer.parseInt(notesMasterRId.substring(3));
          if (notesMasterIdNum <= highestSlideId) {
            Set<String> usedRIds = new HashSet<>();
            for (int i = 0; i < relationshipElements.getLength(); i++) {
              Element rel = (Element) relationshipElements.item(i);
              usedRIds.add(rel.getAttribute("Id"));
            }

            int candidateIdNum = highestSlideId + 1;
            String newNotesMasterRId;
            do {
              newNotesMasterRId = "rId" + candidateIdNum;
              candidateIdNum++;
            } while (usedRIds.contains(newNotesMasterRId));

            logger.debug("Reassigning notesMaster from " + notesMasterRId + " to " + newNotesMasterRId);

            notesMasterRel.setAttribute("Id", newNotesMasterRId);

            updateNotesMasterInPresentation(notesMasterRId, newNotesMasterRId);

            manager.reassignRelationshipId(notesMasterRId, newNotesMasterRId);
          }
        } catch (NumberFormatException e) {
          // Skip
        }
      }
    } catch (Exception e) {
      throw new XMLParsingException("Failed to ensure notesMaster ordering", e);
    }
  }

  // ========== PRIVATE HELPERS ==========

  private void updateNotesMasterInPresentation(String oldRId, String newRId) throws XMLParsingException {
    try {
      com.excudo.core.model.PPTXDocument pptxDoc = manager.getPPTXDocument();
      if (pptxDoc == null) {
        return;
      }

      Document doc = pptxDoc.getXmlPart("ppt/presentation.xml");
      if (doc == null) {
        return;
      }

      XPathExpression expr = xpath.compile("//p:notesMasterIdLst/p:notesMasterId[@r:id='" + oldRId + "']");
      Element notesMasterRef = (Element) expr.evaluate(doc, XPathConstants.NODE);

      if (notesMasterRef != null) {
        notesMasterRef.setAttributeNS(XMLConstants.RELATIONSHIPS_NS, "r:id", newRId);
        logger.debug("Updated notesMaster reference in presentation.xml from " + oldRId + " to " + newRId);
      }
    } catch (Exception e) {
      throw new XMLParsingException("Failed to update notesMaster in presentation.xml", e);
    }
  }

  private void updateNotesMasterReferenceInPresentationXml(String oldRId, String newRId) {
    try {
      com.excudo.core.model.PPTXDocument pptxDoc = manager.getPPTXDocument();
      if (pptxDoc == null) {
        return;
      }

      Document doc = pptxDoc.getXmlPart("ppt/presentation.xml");
      if (doc == null) {
        return;
      }

      XPathExpression expr = xpath.compile("//p:notesMasterIdLst/p:notesMasterId[@r:id='" + oldRId + "']");
      Element notesMasterRef = (Element) expr.evaluate(doc, XPathConstants.NODE);

      if (notesMasterRef != null) {
        notesMasterRef.setAttributeNS(XMLConstants.RELATIONSHIPS_NS, "r:id", newRId);
        logger.debug("Updated notesMaster reference in presentation.xml from " + oldRId + " to " + newRId);
      }
    } catch (Exception e) {
      logger.warn("Error updating notesMaster reference: " + e.getMessage());
    }
  }

  private Element findRelationshipById(Document doc, String relationshipId) {
    try {
      String expression = String.format("//rel:Relationship[@Id='%s']", relationshipId);
      XPathExpression expr = xpath.compile(expression);
      return (Element) expr.evaluate(doc, XPathConstants.NODE);
    } catch (Exception e) {
      logger.warn("XPath error finding relationship " + relationshipId + ": " + e.getMessage());
      return null;
    }
  }

  private String findNextAvailableRelationshipId(Document doc) {
    Set<Integer> usedIds = new HashSet<>();
    try {
      XPathExpression expr = xpath.compile("//rel:Relationship/@Id");
      NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      for (int i = 0; i < nodes.getLength(); i++) {
        String idStr = nodes.item(i).getNodeValue();
        if (idStr.startsWith("rId")) {
          try {
            int id = Integer.parseInt(idStr.substring(3));
            usedIds.add(id);
          } catch (NumberFormatException ignored) {}
        }
      }
    } catch (Exception e) {
      logger.warn("XPath error scanning rIds: " + e.getMessage());
    }

    for (int i = 1; i <= 1000; i++) {
      if (!usedIds.contains(i)) {
        return "rId" + i;
      }
    }
    return "rId999";
  }
}
