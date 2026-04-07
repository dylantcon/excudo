package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.*;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.exceptions.*;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Handles slide copy/clone operations including SPID regeneration
 * and relationship copying.
 */
public class SlideCopyManager {

  private static final ComponentLogger logger = Logger.getLogger(SlideCopyManager.class);

  private final DocumentBuilder documentBuilder;
  private final SPIDManager spidManager;
  private final RelationshipManager relationshipManager;

  public SlideCopyManager(DocumentBuilder documentBuilder, SPIDManager spidManager,
                          RelationshipManager relationshipManager) {
    this.documentBuilder = documentBuilder;
    this.spidManager = spidManager;
    this.relationshipManager = relationshipManager;
  }

  /**
   * Modify a copied slide to avoid SPID conflicts and update content
   */
  public Document modifySlideForCopy(Document sourceSlide, String newTitle) throws XMLParsingException {
    Document copiedSlide = (Document) sourceSlide.cloneNode(true);

    SPIDManager.SPIDRegenerationResult spidResult = regenerateSpids(copiedSlide);
    logger.info("SPID regeneration: {} shapes, {} animations updated", spidResult.getShapesProcessed(), spidResult.getAnimationsUpdated());

    if (newTitle != null && !newTitle.trim().isEmpty()) {
      updateSlideTitle(copiedSlide, newTitle);
    }

    return copiedSlide;
  }

  /**
   * Regenerates all SPIDs in a slide document using SPIDManager.
   */
  SPIDManager.SPIDRegenerationResult regenerateSpids(Document slideDocument) throws XMLParsingException {
    try {
      return spidManager.regenerateSpids(slideDocument, 0);
    } catch (Exception e) {
      throw new XMLParsingException("Failed to regenerate SPIDs in copied slide", e);
    }
  }

  /**
   * Updates the title text of a slide by finding the title placeholder shape
   * and replacing its text content.
   */
  void updateSlideTitle(Document slide, String newTitle) throws XMLParsingException {
    try {
      XPath xpath = XMLFactoryProvider.createXPath();

      // Find title placeholder: p:sp whose nvPr contains p:ph with type="title" or "ctrTitle"
      NodeList phElements = (NodeList) xpath.evaluate(
          "//p:sp/p:nvSpPr/p:nvPr/p:ph[@type='title' or @type='ctrTitle']",
          slide, XPathConstants.NODESET);

      if (phElements.getLength() == 0) {
        // No title placeholder found -- not all layouts have one
        logger.info("No title placeholder found, skipping title update");
        return;
      }

      // Navigate from p:ph -> p:nvPr -> p:nvSpPr -> p:sp
      Element ph = (Element) phElements.item(0);
      Element sp = (Element) ph.getParentNode().getParentNode().getParentNode();

      // Find or create txBody
      Element txBody = (Element) xpath.evaluate("p:txBody", sp, XPathConstants.NODE);
      if (txBody == null) {
        logger.info("Title shape has no txBody, skipping title update");
        return;
      }

      // Find existing text run and update, or replace all paragraphs
      NodeList textNodes = (NodeList) xpath.evaluate(".//a:r/a:t", txBody, XPathConstants.NODESET);
      if (textNodes.getLength() > 0) {
        // Update the first text run's content
        textNodes.item(0).setTextContent(newTitle);
        // Remove any additional runs in the first paragraph (clean single-run title)
        Element firstParagraph = (Element) xpath.evaluate("a:p[1]", txBody, XPathConstants.NODE);
        if (firstParagraph != null) {
          NodeList runs = (NodeList) xpath.evaluate("a:r", firstParagraph, XPathConstants.NODESET);
          for (int i = runs.getLength() - 1; i > 0; i--) {
            firstParagraph.removeChild(runs.item(i));
          }
        }
      } else {
        // No existing text runs -- create one
        Element firstParagraph = (Element) xpath.evaluate("a:p[1]", txBody, XPathConstants.NODE);
        if (firstParagraph != null) {
          String drawingNs = "http://schemas.openxmlformats.org/drawingml/2006/main";
          Element run = slide.createElementNS(drawingNs, "a:r");
          Element rPr = slide.createElementNS(drawingNs, "a:rPr");
          rPr.setAttribute("lang", "en-US");
          run.appendChild(rPr);
          Element t = slide.createElementNS(drawingNs, "a:t");
          t.setTextContent(newTitle);
          run.appendChild(t);
          // Insert before endParaRPr if present
          Element endParaRPr = (Element) xpath.evaluate("a:endParaRPr", firstParagraph, XPathConstants.NODE);
          if (endParaRPr != null) {
            firstParagraph.insertBefore(run, endParaRPr);
          } else {
            firstParagraph.appendChild(run);
          }
        }
      }
      logger.info("Title updated to: \"{}\"", newTitle);
    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to update slide title", e);
    }
  }

  /**
   * Copy relationships from source slide to new slide using RelationshipManager
   */
  public void copySlideRelationships(int sourceSlideNumber, int newSlideNumber) throws XMLParsingException {
    try {
      RelationshipManager.RelationshipCopyResult result =
        relationshipManager.copySlideRelationships(sourceSlideNumber, newSlideNumber, false);

      logger.info("Copied relationships file: {}", result.getRelationshipFile().getName());
      logger.info("ID mappings: {} relationships remapped", result.getOldToNewIdMappings().size());
      logger.info("New relationship IDs: {}", result.getNewRelationshipIds());

    } catch (Exception e) {
      throw new XMLParsingException("Failed to copy slide relationships from slide " +
          sourceSlideNumber + " to slide " + newSlideNumber, e);
    }
  }
}
