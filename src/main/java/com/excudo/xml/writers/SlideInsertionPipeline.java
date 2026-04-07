package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import com.excudo.exceptions.*;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PresentationXmlHelper;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Orchestrates the invariant 6-stage slide insertion pipeline:
 * 1. Get existing slide numbers
 * 2. Rename subsequent slides
 * 3. Create slide DOM (variant - provided by SlideDocumentFactory)
 * 4. Write slide file
 * 5. Create coordinated relationships
 * 6. Update presentation.xml and content types
 */
public class SlideInsertionPipeline {

  private static final ComponentLogger logger = Logger.xml();

  /**
   * Strategy interface for the variant stage (DOM creation).
   */
  @FunctionalInterface
  public interface SlideDocumentFactory {
    Document create(int slideNumber) throws XMLParsingException;
  }

  private final DocumentBuilder documentBuilder;
  private final RelationshipManager relationshipManager;
  private PPTXDocument pptxDocument;

  public SlideInsertionPipeline(DocumentBuilder documentBuilder,
                                 RelationshipManager relationshipManager) {
    this.documentBuilder = documentBuilder;
    this.relationshipManager = relationshipManager;
  }

  /**
   * Set the PPTXDocument for in-memory operations.
   * When set, slide DOMs go into PPTXDocument instead of disk,
   * and presentation.xml / content types are manipulated as live DOMs.
   */
  public void setPPTXDocument(PPTXDocument pptxDocument) {
    this.pptxDocument = pptxDocument;
  }

  /**
   * Get the current PPTXDocument (may be null).
   */
  public PPTXDocument getPPTXDocument() {
    return pptxDocument;
  }

  /**
   * Execute the full insertion pipeline with a custom document factory.
   *
   * @param insertPosition Requested position (1-based)
   * @param layoutId Layout ID for relationship creation, or null
   * @param documentFactory Strategy for creating the slide DOM
   * @return The actual slide number created
   */
  public int executeInsertion(int insertPosition, String layoutId,
                               SlideDocumentFactory documentFactory) throws XMLParsingException {
    try {
      // Stage 1: Validate and adjust position
      List<Integer> existingSlides = getExistingSlideNumbers();
      int maxSlideNumber = existingSlides.isEmpty() ? 0 : existingSlides.stream().mapToInt(Integer::intValue).max().orElse(0);
      int actualPosition = insertPosition;

      if (insertPosition > maxSlideNumber + 1) {
        actualPosition = maxSlideNumber + 1;
        logger.warn("Position " + insertPosition + " exceeds slide count (" + maxSlideNumber + "), creating slide " + actualPosition + " instead");
      }

      // Stage 2: Shift subsequent slides (in-memory rekey or file rename)
      if (pptxDocument != null) {
        pptxDocument.rekeySlides(actualPosition, +1);
      }
      renameSubsequentSlides(actualPosition);

      // Stage 3: Create slide DOM (variant)
      Document newSlide = documentFactory.create(actualPosition);

      // Stage 4: Store slide DOM
      if (pptxDocument == null) {
        throw new UnsupportedOperationException("SlideInsertionPipeline requires a PPTXDocument -- call setPPTXDocument() first");
      }
      pptxDocument.putSlideDocument(actualPosition, newSlide);
      logger.info("Stored slide {} in PPTXDocument", actualPosition);

      // Stage 5: Create coordinated relationships (still file-based -- rels are small)
      String coordinatedRId = createCoordinatedSlideRelationships(actualPosition, layoutId);

      // Stage 6a: Update presentation.xml
      if (pptxDocument != null) {
        int newSlideId = PresentationXmlHelper.insertSlideIdWithCascade(
            pptxDocument.getPresentationXml(), actualPosition, coordinatedRId);
        pptxDocument.markPresentationXmlDirty();
        logger.info("Updated presentation.xml DOM with slide ID: " + newSlideId);
      } else {
        updatePresentationXml(actualPosition, coordinatedRId);
      }

      // Stage 6b: Update content types
      if (pptxDocument != null) {
        // Rebuild all slide content types from the PPTXDocument's slide map
        PresentationXmlHelper.rebuildSlideContentTypes(
            pptxDocument.getContentTypesDoc(), pptxDocument.getSlideNumbers());
        pptxDocument.markContentTypesDirty();
      } else {
        updateContentTypes(actualPosition);
        for (int renamed : existingSlides) {
          if (renamed >= actualPosition) {
            updateContentTypes(renamed + 1);
          }
        }
      }

      return actualPosition;

    } catch (Exception e) {
      throw new XMLParsingException("Failed to insert slide at position " + insertPosition, e);
    }
  }

  // ========== STAGE 1: SCAN ==========

  public List<Integer> getExistingSlideNumbers() {
    if (pptxDocument != null) {
      return pptxDocument.getSlideNumbers();
    }
    return new ArrayList<>();
  }

  // ========== STAGE 2: RENAME ==========

  public void renameSubsequentSlides(int insertPosition) throws XMLParsingException {
    // Slides are managed in-memory via rekeySlides() when PPTXDocument is set.
    // This method is a no-op in the in-memory path.
  }

  // ========== STAGE 5: RELATIONSHIPS ==========

  public String createCoordinatedSlideRelationships(int slideNumber, String layoutId) throws XMLParsingException {
    try {
      String layoutTarget = null;
      if (layoutId != null && !layoutId.trim().isEmpty()) {
        layoutTarget = "../slideLayouts/" + layoutId + ".xml";
      }

      RelationshipManager.RelationshipCreationResult slideRelsResult =
        relationshipManager.createSlideRelationships(slideNumber, layoutTarget, null);

      logger.info("Created slide relationships file: " + slideRelsResult.getRelationshipFile().getName());
      logger.debug("Slide relationships: " + slideRelsResult.getCreatedRelationshipIds());

      String coordinatedRId = relationshipManager.allocateSlideRelationshipIdForPosition(slideNumber);

      relationshipManager.addPresentationRelationship(coordinatedRId,
          XMLConstants.RELATIONSHIP_TYPE_SLIDE,
          "slides/slide" + slideNumber + ".xml");

      logger.info("Coordinated rID allocated: " + coordinatedRId);
      logger.debug("Will be used in BOTH presentation.xml and presentation.xml.rels");

      return coordinatedRId;

    } catch (Exception e) {
      throw new XMLParsingException("Failed to create coordinated slide relationships for slide " + slideNumber, e);
    }
  }

  // ========== STAGE 6A: PRESENTATION.XML ==========

  public void updatePresentationXml(int newSlideNumber, String existingRId) throws XMLParsingException {
    throw new UnsupportedOperationException("Disk-backed updatePresentationXml is removed. Route through PPTXDocument.");
  }

  // ========== STAGE 6B: CONTENT TYPES ==========

  public void updateContentTypes(int slideNumber) throws XMLParsingException {
    throw new UnsupportedOperationException("Disk-backed updateContentTypes is removed. Route through PPTXDocument.");
  }

  // ========== STRING PARSING HELPERS ==========

  int calculateNextSlideId(String presentationContent) {
    int maxId = XMLConstants.DEFAULT_SLIDE_ID_START - 1;

    Pattern slideIdPattern = Pattern.compile("<p:sldId\\s+id=\"(\\d+)\"");
    Matcher matcher = slideIdPattern.matcher(presentationContent);

    while (matcher.find()) {
      int id = Integer.parseInt(matcher.group(1));
      maxId = Math.max(maxId, id);
    }

    return maxId + 1;
  }

  String insertSlideIdElement(String presentationContent, String newSldIdElement, int newSlideNumber) {
    // Handle both <p:sldIdLst>...</p:sldIdLst> and self-closing <p:sldIdLst/>
    // DOM serialization may collapse empty sldIdLst to self-closing form
    Pattern sldIdLstPattern = Pattern.compile("(<p:sldIdLst>)(.*?)(</p:sldIdLst>)", Pattern.DOTALL);
    Matcher matcher = sldIdLstPattern.matcher(presentationContent);

    if (!matcher.find()) {
      // Check for self-closing form (empty list after all slides deleted)
      Pattern selfClosingPattern = Pattern.compile("<p:sldIdLst/>");
      Matcher selfClosingMatcher = selfClosingPattern.matcher(presentationContent);
      if (selfClosingMatcher.find()) {
        String replacement = "<p:sldIdLst>" + newSldIdElement + "</p:sldIdLst>";
        return presentationContent.replace(selfClosingMatcher.group(0), replacement);
      }
      throw new RuntimeException("Could not find sldIdLst in presentation.xml");
    }

    String beforeList = matcher.group(1);
    String listContent = matcher.group(2);
    String afterList = matcher.group(3);

    List<SlideIdInfo> existingSlides = parseExistingSlideIds(listContent);

    StringBuilder newListContent = new StringBuilder();
    boolean inserted = false;

    // Extract the rId number from the new slide element to know the cascade boundary
    int newRIdNum = -1;
    java.util.regex.Matcher rIdMatcher = java.util.regex.Pattern.compile("r:id=\"rId(\\d+)\"").matcher(newSldIdElement);
    if (rIdMatcher.find()) {
      newRIdNum = Integer.parseInt(rIdMatcher.group(1));
    }

    for (int i = 0; i < existingSlides.size(); i++) {
      SlideIdInfo slideInfo = existingSlides.get(i);

      if (!inserted && slideInfo.slideNumber >= newSlideNumber) {
        newListContent.append(newSldIdElement);
        inserted = true;
      }

      // Cascade existing sldId r:id references to match the cascaded rels
      if (newRIdNum > 0) {
        int existingRIdNum = Integer.parseInt(slideInfo.rId.substring(3));
        if (existingRIdNum >= newRIdNum) {
          String updatedElement = slideInfo.originalElement.replace(
              "r:id=\"" + slideInfo.rId + "\"",
              "r:id=\"rId" + (existingRIdNum + 1) + "\"");
          newListContent.append(updatedElement);
          continue;
        }
      }

      newListContent.append(slideInfo.originalElement);
    }

    if (!inserted) {
      newListContent.append(newSldIdElement);
    }

    return presentationContent.replace(matcher.group(0),
        beforeList + newListContent.toString() + afterList);
  }

  List<SlideIdInfo> parseExistingSlideIds(String listContent) {
    List<SlideIdInfo> slides = new ArrayList<>();

    Pattern slidePattern = Pattern.compile("<p:sldId\\s+id=\"(\\d+)\"\\s+r:id=\"([^\"]+)\"/>");
    Matcher matcher = slidePattern.matcher(listContent);

    while (matcher.find()) {
      String fullElement = matcher.group(0);
      int slideId = Integer.parseInt(matcher.group(1));
      String rId = matcher.group(2);
      int slideNumber = extractSlideNumberFromRId(rId);

      slides.add(new SlideIdInfo(slideNumber, slideId, rId, fullElement));
    }

    return slides;
  }

  private int extractSlideNumberFromRId(String rId) {
    try {
      int ridNum = Integer.parseInt(rId.substring(XMLConstants.RID_PREFIX.length()));
      return ridNum - 1;
    } catch (Exception e) {
      return 1;
    }
  }

  // ========== INNER CLASS ==========

  static class SlideIdInfo {
    final int slideNumber;
    final int slideId;
    final String rId;
    final String originalElement;

    SlideIdInfo(int slideNumber, int slideId, String rId, String originalElement) {
      this.slideNumber = slideNumber;
      this.slideId = slideId;
      this.rId = rId;
      this.originalElement = originalElement;
    }
  }
}
