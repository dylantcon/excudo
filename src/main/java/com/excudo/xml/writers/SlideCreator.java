package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.util.*;
import com.excudo.core.model.*;
import com.excudo.exceptions.*;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PresentationXmlHelper;
import com.excudo.xml.parsers.SlideXMLParser;

/**
 * Facade for slide creation supporting blank slides, slide duplication,
 * template-based creation, and complete PPTX relationship management.
 *
 * Delegates to: SlideInsertionPipeline (invariant stages),
 *               SlideDocumentBuilder (DOM creation),
 *               SlideCopyManager (copy operations).
 */
public class SlideCreator {

  private static final ComponentLogger logger = Logger.xml();

  private final DocumentBuilder documentBuilder;
  private final RelationshipManager relationshipManager;
  private final SPIDManager spidManager;
  private final LayoutManager layoutManager;

  private final SlideInsertionPipeline pipeline;
  private final SlideDocumentBuilder docBuilder;
  private final SlideCopyManager copyManager;

  /**
   * Construct for in-memory path.
   * All I/O routes through PPTXDocument set via setPPTXDocument().
   */
  public SlideCreator(RelationshipManager rm, SPIDManager spidMgr, LayoutManager layoutMgr) throws XMLParsingException {
    try {
      this.documentBuilder = XMLFactoryProvider.createDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new XMLParsingException("Failed to initialize SlideCreator", e);
    }
    this.relationshipManager = rm;
    this.spidManager = spidMgr;
    this.spidManager.setRelationshipManager(rm);
    this.layoutManager = layoutMgr;
    this.pipeline = new SlideInsertionPipeline(documentBuilder, rm);
    this.docBuilder = new SlideDocumentBuilder(documentBuilder, spidMgr);
    this.copyManager = new SlideCopyManager(documentBuilder, spidMgr, rm);
  }

  /**
   * Insert a new blank slide at the specified position
   */
  public int insertBlankSlide(int insertPosition, String slideTitle) throws XMLParsingException {
    logger.info("Inserting blank slide at position " + insertPosition);

    boolean layoutHasTitle = false;
    try {
      layoutHasTitle = slideLayoutHasTitle(insertPosition);
    } catch (Exception e) {
      logger.warn("Failed to check layout title support for slide {}: {}", insertPosition, e.getMessage());
    }
    final boolean hasTitle = layoutHasTitle;

    int result = pipeline.executeInsertion(insertPosition, null,
        (slideNum) -> docBuilder.createBlankSlideDocument(slideTitle, slideNum, hasTitle));

    logger.info("Blank slide insertion complete");
    return result;
  }

  /**
   * Insert a new slide with a specific layout
   */
  public int insertSlideWithLayout(int insertPosition, String slideTitle, String layoutId) throws XMLParsingException {
    if (layoutId != null) {
      logger.info("Inserting slide with layout " + layoutId + " at position " + insertPosition);
    } else {
      logger.info("Inserting blank slide at position " + insertPosition);
    }

    // Resolve layout info once, before entering the builder
    final LayoutInfo resolvedLayout;
    if (layoutId != null && !layoutId.trim().isEmpty()) {
      try {
        resolvedLayout = layoutManager.getLayoutInfo(layoutId);
      } catch (Exception e) {
        throw new XMLParsingException("Failed to resolve layout " + layoutId, e);
      }
    } else {
      resolvedLayout = null;
    }

    int result = pipeline.executeInsertion(insertPosition, layoutId,
        (slideNum) -> {
          if (resolvedLayout != null) {
            return docBuilder.createSlideWithLayoutDocument(slideTitle, slideNum, resolvedLayout);
          } else {
            boolean hasTitle = false;
            try {
              hasTitle = slideLayoutHasTitle(slideNum);
            } catch (Exception e) {
              logger.warn("Failed to check layout title for slide {}: {}", slideNum, e.getMessage());
            }
            return docBuilder.createBlankSlideDocument(slideTitle, slideNum, hasTitle);
          }
        });

    logger.info("Slide insertion complete");
    return result;
  }

  /**
   * Insert a new slide by copying an existing slide
   */
  public int insertCopiedSlide(int insertPosition, int sourceSlideNumber, String newSlideTitle) throws XMLParsingException {
    try {
      logger.info("Copying slide " + sourceSlideNumber + " to position " + insertPosition);

      PPTXDocument pptxDoc = pipeline.getPPTXDocument();
      if (pptxDoc == null) {
        throw new XMLParsingException("PPTXDocument not set -- call setPPTXDocument() before copying slides");
      }

      // Stage 1: Validate and adjust position
      List<Integer> existingSlides = pipeline.getExistingSlideNumbers();
      int maxSlideNumber = existingSlides.isEmpty() ? 0 : existingSlides.stream().mapToInt(Integer::intValue).max().orElse(0);
      int actualPosition = insertPosition;

      if (insertPosition > maxSlideNumber + 1) {
        actualPosition = maxSlideNumber + 1;
        logger.warn("Position " + insertPosition + " exceeds slide count (" + maxSlideNumber + "), creating slide " + actualPosition + " instead");
      }

      // Stage 2: Snapshot the source BEFORE shifting -- a source at or after
      // the insert position moves up by one in the rekey below
      if (!pptxDoc.hasSlide(sourceSlideNumber)) {
        throw new XMLParsingException("Source slide " + sourceSlideNumber + " not found in PPTXDocument");
      }
      Document sourceSlide = (Document) pptxDoc.getSlideDocument(sourceSlideNumber).cloneNode(true);
      Document modifiedSlide = copyManager.modifySlideForCopy(sourceSlide, newSlideTitle);
      Document sourceRels = pptxDoc.getXmlPart("ppt/slides/_rels/slide" + sourceSlideNumber + ".xml.rels");
      Document copiedRels = sourceRels != null ? (Document) sourceRels.cloneNode(true) : null;

      // Relationship ids are local to the part, so they're kept as-is -- the
      // slide XML refers to them (r:embed, r:link). Notes and comments belong
      // to the source slide only.
      if (copiedRels != null) {
        removeRelationshipsOfType(copiedRels, "/notesSlide", "/comments");
      }

      int placed = insertSlideParts(actualPosition, modifiedSlide, copiedRels);
      logger.info("Slide copying complete");
      return placed;

    } catch (Exception e) {
      throw new XMLParsingException("Failed to copy slide " + sourceSlideNumber + " to position " + insertPosition, e);
    }
  }

  /**
   * Insert a slide's parts at a position: shift later slides (their parts, the
   * presentation relationships pointing at them, and notes back-references),
   * store the slide and its relationships, and register it in presentation.xml
   * with a relationship id above every existing one. Shared by copy, restore
   * and move.
   *
   * <p>Everything is keyed by slide number, which is the part number here. The
   * older positional rId scheme assumed rId order follows slide order, which
   * stops being true once slides are copied, deleted or moved.
   *
   * @return the position the slide landed at (clamped to the end of the deck)
   */
  public int insertSlideParts(int insertPosition, Document slideDoc, Document slideRels) throws XMLParsingException {
    PPTXDocument pptxDoc = pipeline.getPPTXDocument();
    if (pptxDoc == null) {
      throw new XMLParsingException("PPTXDocument not set -- call setPPTXDocument() first");
    }
    List<Integer> existingSlides = pipeline.getExistingSlideNumbers();
    int maxSlideNumber = existingSlides.isEmpty() ? 0 : existingSlides.stream().mapToInt(Integer::intValue).max().orElse(0);
    int position = Math.max(1, Math.min(insertPosition, maxSlideNumber + 1));

    pptxDoc.rekeySlides(position, +1);
    relationshipManager.shiftSlideTargets(position, +1);
    PresentationXmlHelper.shiftNotesSlideBackReferences(pptxDoc, position, +1);

    pptxDoc.putSlideDocument(position, slideDoc);
    if (slideRels != null) {
      pptxDoc.putXmlPart("ppt/slides/_rels/slide" + position + ".xml.rels", slideRels);
    }

    String rId = relationshipManager.nextUnusedPresentationRId();
    relationshipManager.addPresentationRelationship(rId, XMLConstants.RELATIONSHIP_TYPE_SLIDE,
        "slides/slide" + position + ".xml");
    relationshipManager.resyncReservedRIds();

    PresentationXmlHelper.insertSlideIdWithCascade(pptxDoc.getPresentationXml(), position, rId);
    pptxDoc.markPresentationXmlDirty();
    PresentationXmlHelper.rebuildSlideContentTypes(pptxDoc.getContentTypesDoc(), pptxDoc.getSlideNumbers());
    pptxDoc.markContentTypesDirty();
    logger.info("Inserted slide parts at {} ({})", position, rId);
    return position;
  }

  /** Remove relationships whose Type ends with any of the given suffixes. */
  public static void removeRelationshipsOfType(Document relsDoc, String... typeSuffixes) {
    NodeList rels = relsDoc.getElementsByTagName("Relationship");
    for (int i = rels.getLength() - 1; i >= 0; i--) {
      Element rel = (Element) rels.item(i);
      for (String suffix : typeSuffixes) {
        if (rel.getAttribute("Type").endsWith(suffix)) {
          rel.getParentNode().removeChild(rel);
          break;
        }
      }
    }
  }

  /**
   * Insert a new slide from a predefined template
   */
  public int insertTemplateSlide(int insertPosition, SlideTemplate template, TemplateData templateData) throws XMLParsingException {
    logger.info("Inserting template slide at position " + insertPosition);

    int result = pipeline.executeInsertion(insertPosition, null,
        (slideNum) -> template.createSlideDocument(templateData));

    logger.info("Template slide insertion complete");
    return result;
  }

  /**
   * Add a media relationship to the specified slide
   */
  public String addMediaRelationship(int slideNumber, String mediaType, String mediaTarget) throws XMLParsingException {
    try {
      String mediaRId = relationshipManager.addMediaRelationship(slideNumber, mediaType, mediaTarget);
      logger.info("Added media relationship: " + mediaRId + " -> " + mediaTarget);
      return mediaRId;
    } catch (Exception e) {
      throw new XMLParsingException("Failed to add media relationship to slide " + slideNumber, e);
    }
  }

  /**
   * Set the PPTXDocument on the insertion pipeline so slide creation
   * operates on in-memory DOMs instead of disk.
   */
  private boolean slideLayoutHasTitle(int slideNumber) {
    SlideXMLParser.SlideLayoutParser layouts =
        SlideXMLParser.SlideLayoutParser.forDocument(pipeline.getPPTXDocument());
    return layouts != null && layouts.slideLayoutHasTitlePlaceholder(slideNumber);
  }

  public void setPPTXDocument(PPTXDocument pptxDocument) {
    pipeline.setPPTXDocument(pptxDocument);
  }

  public RelationshipManager getRelationshipManager() {
    return relationshipManager;
  }

  public SPIDManager getSPIDManager() {
    return spidManager;
  }

  public int allocateSpidForShape(String shapeType, int slideNumber, boolean isPlaceholder,
                                 boolean isFromTemplate, String layoutId) {
    return spidManager.allocateSpidForShape(shapeType, slideNumber, isPlaceholder, isFromTemplate, layoutId);
  }

  @Deprecated
  public int allocateSpidForShape(String shapeType, int slideNumber, boolean isPlaceholder) {
    return spidManager.allocateSpidForShape(shapeType, slideNumber, isPlaceholder, false, null);
  }

  /**
   * Validates that all SPIDs and relationships in the presentation are consistent.
   */
  public ValidationSummary validatePresentation() throws XMLParsingException {
    try {
      SPIDManager.ValidationResult spidValidation = spidManager.validateSpidUniqueness();
      RelationshipManager.ValidationResult relationshipValidation = relationshipManager.validateAllRelationships();

      List<String> allErrors = new ArrayList<>();
      List<String> allWarnings = new ArrayList<>();

      allErrors.addAll(spidValidation.getErrors());
      allErrors.addAll(relationshipValidation.getErrors());

      allWarnings.addAll(spidValidation.getWarnings());
      allWarnings.addAll(relationshipValidation.getWarnings());

      return new ValidationSummary(allErrors, allWarnings);

    } catch (Exception e) {
      throw new XMLParsingException("Failed to validate presentation", e);
    }
  }

  // ========== INNER CLASSES ==========

  public static class ValidationSummary {
    private final List<String> errors;
    private final List<String> warnings;

    public ValidationSummary(List<String> errors, List<String> warnings) {
      this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
      this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public boolean hasErrors() { return !errors.isEmpty(); }
    public boolean hasWarnings() { return !warnings.isEmpty(); }
    public boolean isValid() { return errors.isEmpty(); }

    @Override
    public String toString() {
      return String.format("ValidationSummary{errors=%d, warnings=%d}",
          errors.size(), warnings.size());
    }
  }
}

/**
 * Interface for slide templates
 */
interface SlideTemplate {
  Document createSlideDocument(TemplateData data) throws XMLParsingException;
  String getTemplateName();
}

/**
 * Data container for template population
 */
class TemplateData {
  private final Map<String, Object> data = new HashMap<>();

  public void put(String key, Object value) {
    data.put(key, value);
  }

  public Object get(String key) {
    return data.get(key);
  }

  public String getString(String key) {
    Object value = data.get(key);
    return value != null ? value.toString() : null;
  }

  @SuppressWarnings("unchecked")
  public List<String> getStringList(String key) {
    Object value = data.get(key);
    if (value instanceof List) {
      return (List<String>) value;
    }
    return new ArrayList<>();
  }
}
