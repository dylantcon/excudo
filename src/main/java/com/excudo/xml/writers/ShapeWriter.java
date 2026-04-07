package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.xpath.*;
import com.excudo.core.model.*;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.model.BodyProperties;
import com.excudo.core.model.TextBody;
import com.excudo.xml.builders.TextBodyXMLWriter;
import com.excudo.utils.TextFormatUtils;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.exceptions.*;
import com.excudo.xml.builders.ShapeStyleXMLWriter;
import com.excudo.xml.shapes.ShapeFactoryRegistry;
import com.excudo.xml.shapes.ShapeFactory;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import java.util.List;
import java.util.Map;

/**
 * Handles shape CRUD operations on PowerPoint slide DOM structures.
 * Manages shape injection, text updates, geometry updates, removal, and picture shapes.
 */
public class ShapeWriter {

  private final Document document;
  private final XPath xpath;
  private final Element shapeTree;
  private final SPIDManager spidManager;
  private final ShapeFactoryRegistry shapeFactoryRegistry;
  private int nextAvailableSpid;
  private static final ComponentLogger logger = Logger.xml();

  public ShapeWriter(Document document, XPath xpath, Element shapeTree,
                     SPIDManager spidManager, ShapeFactoryRegistry shapeFactoryRegistry) throws XMLParsingException {
    this.document = document;
    this.xpath = xpath;
    this.shapeTree = shapeTree;
    this.spidManager = spidManager;
    this.shapeFactoryRegistry = shapeFactoryRegistry;

    // Calculate next available SPID - use SPIDManager if available
    if (this.spidManager != null) {
      this.nextAvailableSpid = -1; // Will be allocated on demand
    } else {
      try {
        this.nextAvailableSpid = calculateNextSpid();
      } catch (XPathExpressionException e) {
        throw new XMLParsingException("Failed to calculate next SPID", e);
      }
    }
  }

  /**
   * Injects a shape with Microsoft-compatible SPID allocation using ShapeFactory pattern.
   */
  public int injectBasicShapeWithSlideContext(SlideShape.ShapeType shapeType, ShapeGeometry geometry,
      String text, String name, int slideNumber) throws XMLParsingException {
    return injectBasicShapeWithSlideContext(shapeType, geometry, text, name, slideNumber, null);
  }

  /**
   * Injects a shape with styling and Microsoft-compatible SPID allocation.
   *
   * @param style shape styling (fill, line, theme ref). Null = default theme style.
   */
  public int injectBasicShapeWithSlideContext(SlideShape.ShapeType shapeType, ShapeGeometry geometry,
      String text, String name, int slideNumber, ShapeStyle style) throws XMLParsingException {
    try {
      int spid;

      if (spidManager != null) {
        spid = spidManager.allocateSpidForShape("custom", slideNumber, false, false, null);
        // Guard against stale registry: if this SPID already exists in the DOM,
        // compute the next available SPID directly from the document and register it.
        if (spidExistsInDocument(spid)) {
          int conflictingSpid = spid;
          spid = computeNextAvailableSpidFromDocument();
          spidManager.registerSpid(spid, slideNumber, "custom_shape");
          logger.warn("SPID collision detected: {} already in DOM on slide {}, using {} instead",
              conflictingSpid, slideNumber, spid);
        }
      } else {
        spid = nextAvailableSpid++;
      }

      ShapeFactory factory = shapeFactoryRegistry.getFactory(shapeType);
      if (factory == null) {
        factory = shapeFactoryRegistry.getDefaultFactory();
        logger.warn("Shape type " + shapeType + " not supported, falling back to " +
                   shapeFactoryRegistry.getDefaultShapeType());
      }

      Element shapeElement = factory.createShape(document, shapeType, spid, name, geometry, text);

      // Apply fill/line/theme style -- default theme style when null
      boolean hasText = text != null && !text.trim().isEmpty();
      ShapeStyleXMLWriter.applyStyle(document, shapeElement, style, hasText);

      shapeTree.appendChild(shapeElement);

      return spid;
    } catch (Exception e) {
      throw new XMLParsingException("Failed to inject basic shape with Microsoft SPID allocation", e);
    }
  }

  /**
   * Update text content of an existing shape
   */
  public void updateShapeText(int spid, String newText) throws XMLParsingException {
    try {
      Element shape = findShapeBySpid(spid);
      if (shape == null) {
        throw new XMLParsingException("Shape with SPID " + spid + " not found");
      }

      // Strip trailing backslashes the LLM sometimes emits
      String cleanText = newText.replaceAll("\\\\+$", "");

      if (TextFormatUtils.containsBulletMarkers(cleanText)) {
        Element existingTxBody = (Element) xpath.evaluate(".//p:txBody", shape, XPathConstants.NODE);
        if (existingTxBody != null) {
          existingTxBody.getParentNode().removeChild(existingTxBody);
        }
        addTextToShape(shape, cleanText);
      } else if (cleanText.contains("\n")) {
        // Multi-line text: use addTextToShape to create proper paragraphs
        // rather than jamming everything into a single <a:t>
        Element existingTxBody = (Element) xpath.evaluate(".//p:txBody", shape, XPathConstants.NODE);
        if (existingTxBody != null) {
          existingTxBody.getParentNode().removeChild(existingTxBody);
        }
        addTextToShape(shape, cleanText);
      } else {
        Element textElement = (Element) xpath.evaluate(".//a:t", shape, XPathConstants.NODE);
        if (textElement != null) {
          textElement.setTextContent(cleanText);
        } else {
          // No <a:t> element (e.g. empty placeholder with only endParaRPr).
          // Remove existing txBody before appending new one to avoid duplicates.
          Element existingTxBody = (Element) xpath.evaluate(".//p:txBody", shape, XPathConstants.NODE);
          if (existingTxBody != null) {
            existingTxBody.getParentNode().removeChild(existingTxBody);
          }
          addTextToShape(shape, cleanText);
        }
      }

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to update shape text", e);
    }
  }

  /**
   * Replace a shape's entire text body with a richly-formatted TextBody model.
   * Removes the existing p:txBody and appends a new one built from the model.
   */
  public void replaceTextBody(int spid, TextBody textBody) throws XMLParsingException {
    try {
      Element shape = findShapeBySpid(spid);
      if (shape == null) {
        throw new XMLParsingException("Shape with SPID " + spid + " not found");
      }

      // Remove existing txBody
      Element existingTxBody = (Element) xpath.evaluate(".//p:txBody", shape, XPathConstants.NODE);
      if (existingTxBody != null) {
        existingTxBody.getParentNode().removeChild(existingTxBody);
      }

      // Create new txBody from model
      Element newTxBody = TextBodyXMLWriter.write(document, textBody);
      shape.appendChild(newTxBody);
    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to replace text body", e);
    }
  }

  /**
   * Update geometry (position/size) of an existing shape
   */
  public void updateShapeGeometry(int spid, ShapeGeometry newGeometry) throws XMLParsingException {
    try {
      Element shape = findShapeBySpid(spid);
      if (shape == null) {
        throw new XMLParsingException("Shape with SPID " + spid + " not found");
      }

      updateShapeTransform(shape, newGeometry);

    } catch (Exception e) {
      throw new XMLParsingException("Failed to update shape geometry", e);
    }
  }

  /**
   * Remove a shape by SPID from the slide.
   */
  public SlideXMLWriter.ShapeRemovalResult removeShapeBySpid(int spid) throws XMLParsingException {
    try {
      Element shapeElement = findShapeBySpid(spid);
      if (shapeElement == null) {
        throw new XMLParsingException("Shape with SPID " + spid + " not found");
      }

      boolean isPicture = "pic".equals(shapeElement.getLocalName());
      String relationshipId = null;

      if (isPicture) {
        try {
          relationshipId = (String) xpath.evaluate(
              com.excudo.core.utils.XMLConstants.XPATH_PICTURE_BLIP_RELATIONSHIP_ID,
              shapeElement, XPathConstants.STRING);
          if (relationshipId != null && relationshipId.trim().isEmpty()) {
            relationshipId = null;
          }
        } catch (XPathExpressionException e) {
          logger.warn("Could not extract relationship ID from picture {}: {}", spid, e.getMessage());
        }
      }

      shapeElement.getParentNode().removeChild(shapeElement);

      return new SlideXMLWriter.ShapeRemovalResult(true, isPicture, relationshipId, spid);

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to remove shape with SPID " + spid, e);
    }
  }

  /**
   * Restore a previously removed shape element back into the slide's spTree.
   * Used for undo of remove-shape operations.
   */
  public void restoreShape(org.w3c.dom.Element clonedElement) throws XMLParsingException {
    try {
      Element spTree = (Element) xpath.evaluate(
          "//*[local-name()='spTree']", document, XPathConstants.NODE);
      if (spTree == null) {
        throw new XMLParsingException("Cannot restore shape: spTree not found in document");
      }
      // Import the node into this document (it was cloned from a potentially different parse)
      org.w3c.dom.Node imported = document.importNode(clonedElement, true);
      spTree.appendChild(imported);
    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to restore shape element", e);
    }
  }

  /**
   * Add a picture shape to the slide with proper OOXML structure
   */
  public void addPictureShape(int spid, String name, String relationshipId, ShapeGeometry geometry) throws XMLParsingException {
    try {
      Element pic = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:pic");

      Element nvPicPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPicPr");
      pic.appendChild(nvPicPr);

      Element cNvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
      cNvPr.setAttribute("id", String.valueOf(spid));
      cNvPr.setAttribute("name", name);
      nvPicPr.appendChild(cNvPr);

      Element cNvPicPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPicPr");
      nvPicPr.appendChild(cNvPicPr);

      Element picLocks = document.createElementNS(XMLConstants.DRAWING_NS, "a:picLocks");
      picLocks.setAttribute("noChangeAspect", "1");
      cNvPicPr.appendChild(picLocks);

      Element nvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr");
      nvPicPr.appendChild(nvPr);

      Element blipFill = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:blipFill");
      pic.appendChild(blipFill);

      Element blip = document.createElementNS(XMLConstants.DRAWING_NS, "a:blip");
      blip.setAttributeNS(XMLConstants.RELATIONSHIPS_NS, "r:embed", relationshipId);
      blipFill.appendChild(blip);

      Element stretch = document.createElementNS(XMLConstants.DRAWING_NS, "a:stretch");
      blipFill.appendChild(stretch);

      Element fillRect = document.createElementNS(XMLConstants.DRAWING_NS, "a:fillRect");
      stretch.appendChild(fillRect);

      ShapeFactory factory = shapeFactoryRegistry.getFactory(SlideShape.ShapeType.RECTANGLE);
      Element spPr = factory.createShapeProperties(document, SlideShape.ShapeType.RECTANGLE, geometry);
      pic.appendChild(spPr);

      shapeTree.appendChild(pic);

    } catch (Exception e) {
      throw new XMLParsingException("Failed to create picture shape: " + e.getMessage(), e);
    }
  }

  /**
   * Update body properties (a:bodyPr) on an existing shape, optionally marking it as a textbox.
   * Creates a minimal txBody if the shape has none.
   */
  public void updateBodyProperties(int spid, BodyProperties bodyProperties, boolean textBox) throws XMLParsingException {
    try {
      Element shape = findShapeBySpid(spid);
      if (shape == null) {
        throw new XMLParsingException("Shape with SPID " + spid + " not found");
      }

      // Find or create txBody
      Element txBody = (Element) xpath.evaluate(".//p:txBody", shape, XPathConstants.NODE);
      if (txBody == null) {
        txBody = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:txBody");
        Element newBodyPr = document.createElementNS(XMLConstants.DRAWING_NS, "a:bodyPr");
        txBody.appendChild(newBodyPr);
        Element lstStyle = document.createElementNS(XMLConstants.DRAWING_NS, "a:lstStyle");
        txBody.appendChild(lstStyle);
        Element p = document.createElementNS(XMLConstants.DRAWING_NS, "a:p");
        txBody.appendChild(p);
        shape.appendChild(txBody);
      }

      // Find or create bodyPr
      Element bodyPr = (Element) xpath.evaluate("./a:bodyPr", txBody, XPathConstants.NODE);
      if (bodyPr == null) {
        bodyPr = document.createElementNS(XMLConstants.DRAWING_NS, "a:bodyPr");
        txBody.insertBefore(bodyPr, txBody.getFirstChild());
      }

      // Clear existing attributes and children of bodyPr
      while (bodyPr.getAttributes().getLength() > 0) {
        bodyPr.removeAttribute(bodyPr.getAttributes().item(0).getNodeName());
      }
      while (bodyPr.hasChildNodes()) {
        bodyPr.removeChild(bodyPr.getFirstChild());
      }

      // Set new attributes from BodyProperties
      if (bodyProperties.getVerticalAlignment() != null) {
        bodyPr.setAttribute("anchor", bodyProperties.getVerticalAlignment());
      }
      if (bodyProperties.getWrap() != null) {
        bodyPr.setAttribute("wrap", bodyProperties.getWrap());
      }
      if (bodyProperties.getVerticalText() != null) {
        bodyPr.setAttribute("vert", bodyProperties.getVerticalText());
      }
      if (bodyProperties.getLeftInset() != null) {
        bodyPr.setAttribute("lIns", String.valueOf(bodyProperties.getLeftInset()));
      }
      if (bodyProperties.getTopInset() != null) {
        bodyPr.setAttribute("tIns", String.valueOf(bodyProperties.getTopInset()));
      }
      if (bodyProperties.getRightInset() != null) {
        bodyPr.setAttribute("rIns", String.valueOf(bodyProperties.getRightInset()));
      }
      if (bodyProperties.getBottomInset() != null) {
        bodyPr.setAttribute("bIns", String.valueOf(bodyProperties.getBottomInset()));
      }
      if (bodyProperties.getNumColumns() != null) {
        bodyPr.setAttribute("numCol", String.valueOf(bodyProperties.getNumColumns()));
      }
      if (bodyProperties.isRtlCol()) {
        bodyPr.setAttribute("rtlCol", "1");
      }

      // Autofit child elements
      if (bodyProperties.getAutofit() != null) {
        switch (bodyProperties.getAutofit()) {
          case NORMAL:
            Element normAutofit = document.createElementNS(XMLConstants.DRAWING_NS, "a:normAutofit");
            if (bodyProperties.getFontScale() != null) {
              normAutofit.setAttribute("fontScale", String.valueOf(bodyProperties.getFontScale()));
            }
            bodyPr.appendChild(normAutofit);
            break;
          case SHAPE:
            bodyPr.appendChild(document.createElementNS(XMLConstants.DRAWING_NS, "a:spAutoFit"));
            break;
          case NONE:
            bodyPr.appendChild(document.createElementNS(XMLConstants.DRAWING_NS, "a:noAutofit"));
            break;
        }
      }

      // Set textbox flag on cNvSpPr if requested
      if (textBox) {
        Element cNvSpPr = (Element) xpath.evaluate(".//p:cNvSpPr", shape, XPathConstants.NODE);
        if (cNvSpPr != null) {
          cNvSpPr.setAttribute("txBox", "1");
        }
      }

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to update body properties", e);
    }
  }

  /**
   * Reorder a shape's z-order position within the shape tree.
   *
   * @param spid The shape SPID to reorder
   * @param operation Z-order operation: BRING_FRONT, SEND_BACK, BRING_FORWARD, SEND_BACKWARD
   */
  public void reorderShape(int spid, String operation) throws XMLParsingException {
    try {
      Element shape = findShapeBySpid(spid);
      if (shape == null) {
        throw new XMLParsingException("Shape with SPID " + spid + " not found");
      }

      Node parent = shape.getParentNode();
      if (parent == null) {
        throw new XMLParsingException("Shape SPID " + spid + " has no parent node");
      }

      // Get ordered list of shape siblings (skip nvGrpSpPr which is always first)
      List<Node> shapeChildren = new java.util.ArrayList<>();
      NodeList children = parent.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node child = children.item(i);
        if (child.getNodeType() == Node.ELEMENT_NODE) {
          String localName = child.getLocalName();
          if ("sp".equals(localName) || "pic".equals(localName) ||
              "cxnSp".equals(localName) || "grpSp".equals(localName)) {
            shapeChildren.add(child);
          }
        }
      }

      int currentIndex = shapeChildren.indexOf(shape);
      if (currentIndex < 0) {
        throw new XMLParsingException("Shape SPID " + spid + " not found in shape tree children");
      }

      parent.removeChild(shape);
      shapeChildren.remove(currentIndex);

      int targetIndex;
      switch (operation) {
        case "BRING_FRONT":
          targetIndex = shapeChildren.size();
          break;
        case "SEND_BACK":
          targetIndex = 0;
          break;
        case "BRING_FORWARD":
          targetIndex = Math.min(currentIndex + 1, shapeChildren.size());
          break;
        case "SEND_BACKWARD":
          targetIndex = Math.max(currentIndex - 1, 0);
          break;
        default:
          throw new XMLParsingException("Unknown z-order operation: " + operation);
      }

      if (targetIndex >= shapeChildren.size()) {
        parent.appendChild(shape);
      } else {
        parent.insertBefore(shape, shapeChildren.get(targetIndex));
      }

    } catch (XMLParsingException e) {
      throw e;
    } catch (Exception e) {
      throw new XMLParsingException("Failed to reorder shape SPID " + spid + ": " + e.getMessage(), e);
    }
  }

  // ========== CONNECTOR SHAPES ==========

  /**
   * Inject a connector shape (p:cxnSp) into the slide's shape tree.
   * Connectors use a different OOXML element than regular shapes (p:sp).
   */
  public int injectConnectorShape(String connectorType, ShapeGeometry geometry,
      String headEnd, String tailEnd, String lineColor, String lineStyle,
      Integer startSpid, Integer startIdx, Integer endSpid, Integer endIdx,
      String customPath, int slideNumber) throws XMLParsingException {
    try {
      int spid;
      if (spidManager != null) {
        spid = spidManager.allocateSpidForShape("connector", slideNumber, false, false, null);
      } else {
        spid = nextAvailableSpid++;
      }

      Element cxnSp = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cxnSp");

      // nvCxnSpPr
      Element nvCxnSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvCxnSpPr");

      Element cNvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
      cNvPr.setAttribute("id", String.valueOf(spid));
      cNvPr.setAttribute("name", "Connector " + spid);
      nvCxnSpPr.appendChild(cNvPr);

      Element cNvCxnSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvCxnSpPr");
      if (startSpid != null) {
        Element stCxn = document.createElementNS(XMLConstants.DRAWING_NS, "a:stCxn");
        stCxn.setAttribute("id", String.valueOf(startSpid));
        stCxn.setAttribute("idx", String.valueOf(startIdx != null ? startIdx : 0));
        cNvCxnSpPr.appendChild(stCxn);
      }
      if (endSpid != null) {
        Element endCxn = document.createElementNS(XMLConstants.DRAWING_NS, "a:endCxn");
        endCxn.setAttribute("id", String.valueOf(endSpid));
        endCxn.setAttribute("idx", String.valueOf(endIdx != null ? endIdx : 0));
        cNvCxnSpPr.appendChild(endCxn);
      }
      nvCxnSpPr.appendChild(cNvCxnSpPr);

      Element nvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr");
      nvCxnSpPr.appendChild(nvPr);

      cxnSp.appendChild(nvCxnSpPr);

      // spPr
      Element spPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");

      // Transform -- parse flip flags from customPath prefix "flip:H", "flip:V", "flip:HV"
      boolean flipH = false, flipV = false;
      String effectivePath = customPath;
      if (customPath != null && customPath.startsWith("flip:")) {
        String flipSpec = customPath.substring(5);
        int sepIdx = flipSpec.indexOf(' ');
        String flags = sepIdx >= 0 ? flipSpec.substring(0, sepIdx) : flipSpec;
        effectivePath = sepIdx >= 0 ? flipSpec.substring(sepIdx + 1).trim() : null;
        if (effectivePath != null && effectivePath.isEmpty()) effectivePath = null;
        flipH = flags.contains("H");
        flipV = flags.contains("V");
      }

      Element xfrm = document.createElementNS(XMLConstants.DRAWING_NS, "a:xfrm");
      if (flipH) xfrm.setAttribute("flipH", "1");
      if (flipV) xfrm.setAttribute("flipV", "1");
      Element off = document.createElementNS(XMLConstants.DRAWING_NS, "a:off");
      off.setAttribute("x", String.valueOf(geometry.getX()));
      off.setAttribute("y", String.valueOf(geometry.getY()));
      xfrm.appendChild(off);
      Element ext = document.createElementNS(XMLConstants.DRAWING_NS, "a:ext");
      ext.setAttribute("cx", String.valueOf(geometry.getWidth()));
      ext.setAttribute("cy", String.valueOf(geometry.getHeight()));
      xfrm.appendChild(ext);
      spPr.appendChild(xfrm);

      // Geometry
      if (effectivePath != null && !effectivePath.isEmpty()) {
        spPr.appendChild(createCustomGeometry(effectivePath));
      } else {
        Element prstGeom = document.createElementNS(XMLConstants.DRAWING_NS, "a:prstGeom");
        String geomType = mapConnectorType(connectorType);
        prstGeom.setAttribute("prst", geomType);
        Element avLst = document.createElementNS(XMLConstants.DRAWING_NS, "a:avLst");
        prstGeom.appendChild(avLst);
        spPr.appendChild(prstGeom);
      }

      // Line properties
      Element ln = document.createElementNS(XMLConstants.DRAWING_NS, "a:ln");

      if (lineColor != null && !lineColor.isEmpty()) {
        Element solidFill = document.createElementNS(XMLConstants.DRAWING_NS, "a:solidFill");
        if (isSchemeColor(lineColor)) {
          Element schemeClr = document.createElementNS(XMLConstants.DRAWING_NS, "a:schemeClr");
          schemeClr.setAttribute("val", lineColor);
          solidFill.appendChild(schemeClr);
        } else {
          Element srgbClr = document.createElementNS(XMLConstants.DRAWING_NS, "a:srgbClr");
          srgbClr.setAttribute("val", lineColor.startsWith("#") ? lineColor.substring(1) : lineColor);
          solidFill.appendChild(srgbClr);
        }
        ln.appendChild(solidFill);
      }

      if ("dash".equals(lineStyle)) {
        Element prstDash = document.createElementNS(XMLConstants.DRAWING_NS, "a:prstDash");
        prstDash.setAttribute("val", "dash");
        ln.appendChild(prstDash);
      }

      if (headEnd != null && !"none".equalsIgnoreCase(headEnd)) {
        Element headEndEl = document.createElementNS(XMLConstants.DRAWING_NS, "a:headEnd");
        headEndEl.setAttribute("type", headEnd);
        ln.appendChild(headEndEl);
      }

      if (tailEnd != null && !"none".equalsIgnoreCase(tailEnd)) {
        Element tailEndEl = document.createElementNS(XMLConstants.DRAWING_NS, "a:tailEnd");
        tailEndEl.setAttribute("type", tailEnd);
        ln.appendChild(tailEndEl);
      }

      if (ln.hasChildNodes()) {
        spPr.appendChild(ln);
      }

      cxnSp.appendChild(spPr);

      // Style (default connector style referencing theme)
      Element style = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:style");
      addStyleRef(style, "a:lnRef", "1", "accent1");
      addStyleRef(style, "a:fillRef", "0", "accent1");
      addStyleRef(style, "a:effectRef", "0", "accent1");
      Element fontRef = document.createElementNS(XMLConstants.DRAWING_NS, "a:fontRef");
      fontRef.setAttribute("idx", "minor");
      Element fontSchemeClr = document.createElementNS(XMLConstants.DRAWING_NS, "a:schemeClr");
      fontSchemeClr.setAttribute("val", "tx1");
      fontRef.appendChild(fontSchemeClr);
      style.appendChild(fontRef);
      cxnSp.appendChild(style);

      shapeTree.appendChild(cxnSp);

      return spid;
    } catch (Exception e) {
      throw new XMLParsingException("Failed to inject connector shape", e);
    }
  }

  private void addStyleRef(Element parent, String refName, String idx, String schemeClrVal) {
    Element ref = document.createElementNS(XMLConstants.DRAWING_NS, refName);
    ref.setAttribute("idx", idx);
    Element schemeClr = document.createElementNS(XMLConstants.DRAWING_NS, "a:schemeClr");
    schemeClr.setAttribute("val", schemeClrVal);
    ref.appendChild(schemeClr);
    parent.appendChild(ref);
  }

  private String mapConnectorType(String type) {
    if (type == null) return "line";
    switch (type.toLowerCase()) {
      case "line": return "line";
      case "straight": return "straightConnector1";
      case "elbow": return "bentConnector3";
      case "curved": return "curvedConnector3";
      default: return "line";
    }
  }

  private Element createCustomGeometry(String pathNotation) {
    Element custGeom = document.createElementNS(XMLConstants.DRAWING_NS, "a:custGeom");
    custGeom.appendChild(document.createElementNS(XMLConstants.DRAWING_NS, "a:avLst"));
    custGeom.appendChild(document.createElementNS(XMLConstants.DRAWING_NS, "a:gdLst"));
    custGeom.appendChild(document.createElementNS(XMLConstants.DRAWING_NS, "a:ahLst"));
    custGeom.appendChild(document.createElementNS(XMLConstants.DRAWING_NS, "a:cxnLst"));

    Element rect = document.createElementNS(XMLConstants.DRAWING_NS, "a:rect");
    rect.setAttribute("l", "0");
    rect.setAttribute("t", "0");
    rect.setAttribute("r", "0");
    rect.setAttribute("b", "0");
    custGeom.appendChild(rect);

    Element pathLst = document.createElementNS(XMLConstants.DRAWING_NS, "a:pathLst");
    Element path = document.createElementNS(XMLConstants.DRAWING_NS, "a:path");

    String[] tokens = pathNotation.trim().split("\\s+");
    int i = 0;
    while (i < tokens.length) {
      String cmd = tokens[i].toUpperCase();
      switch (cmd) {
        case "M":
          if (i + 2 < tokens.length) {
            Element moveTo = document.createElementNS(XMLConstants.DRAWING_NS, "a:moveTo");
            Element pt = document.createElementNS(XMLConstants.DRAWING_NS, "a:pt");
            pt.setAttribute("x", tokens[i + 1]);
            pt.setAttribute("y", tokens[i + 2]);
            moveTo.appendChild(pt);
            path.appendChild(moveTo);
            i += 3;
          } else i++;
          break;
        case "L":
          if (i + 2 < tokens.length) {
            Element lnTo = document.createElementNS(XMLConstants.DRAWING_NS, "a:lnTo");
            Element pt = document.createElementNS(XMLConstants.DRAWING_NS, "a:pt");
            pt.setAttribute("x", tokens[i + 1]);
            pt.setAttribute("y", tokens[i + 2]);
            lnTo.appendChild(pt);
            path.appendChild(lnTo);
            i += 3;
          } else i++;
          break;
        case "C":
          if (i + 6 < tokens.length) {
            Element cubicBezTo = document.createElementNS(XMLConstants.DRAWING_NS, "a:cubicBezTo");
            for (int j = 0; j < 3; j++) {
              Element pt = document.createElementNS(XMLConstants.DRAWING_NS, "a:pt");
              pt.setAttribute("x", tokens[i + 1 + j * 2]);
              pt.setAttribute("y", tokens[i + 2 + j * 2]);
              cubicBezTo.appendChild(pt);
            }
            path.appendChild(cubicBezTo);
            i += 7;
          } else i++;
          break;
        default:
          i++;
          break;
      }
    }

    pathLst.appendChild(path);
    custGeom.appendChild(pathLst);
    return custGeom;
  }

  /**
   * Inject a hyperlink action (a:hlinkClick) into a shape's p:cNvPr element.
   * Supports navigation actions and optional embedded sound.
   *
   * @param spid The shape SPID to target
   * @param actionType The action type (nextslide, previousslide, etc.)
   * @param soundRId Relationship ID for embedded audio (null if no sound)
   * @param soundName Display name for the sound file (null if no sound)
   */
  public void injectHyperlink(int spid, String actionType, String soundRId, String soundName) throws XMLParsingException {
    try {
      Element shape = findShapeBySpid(spid);
      if (shape == null) {
        throw new XMLParsingException("Shape with SPID " + spid + " not found");
      }

      // Find cNvPr -- it may be under p:nvSpPr, p:nvPicPr, or p:nvCxnSpPr
      Element cNvPr = (Element) xpath.evaluate(
          ".//*[local-name()='cNvPr']", shape, XPathConstants.NODE);
      if (cNvPr == null) {
        throw new XMLParsingException("No cNvPr found for shape SPID " + spid);
      }

      // Remove existing hlinkClick if any
      NodeList existing = cNvPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "hlinkClick");
      while (existing.getLength() > 0) {
        cNvPr.removeChild(existing.item(0));
      }

      // Build action URI
      String actionUri = mapActionType(actionType);

      // Create a:hlinkClick
      Element hlinkClick = document.createElementNS(XMLConstants.DRAWING_NS, "a:hlinkClick");
      hlinkClick.setAttributeNS(XMLConstants.RELATIONSHIPS_NS, "r:id", "");
      if (actionUri != null) {
        hlinkClick.setAttribute("action", actionUri);
      }
      hlinkClick.setAttribute("highlightClick", "1");

      // Add sound if provided
      if (soundRId != null && soundName != null) {
        Element snd = document.createElementNS(XMLConstants.DRAWING_NS, "a:snd");
        snd.setAttributeNS(XMLConstants.RELATIONSHIPS_NS, "r:embed", soundRId);
        snd.setAttribute("name", soundName.contains(".") ? soundName.substring(0, soundName.lastIndexOf('.')) : soundName);
        hlinkClick.appendChild(snd);
      }

      cNvPr.appendChild(hlinkClick);

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to inject hyperlink on SPID " + spid, e);
    }
  }

  /**
   * Map a user-friendly action type name to the OOXML ppaction:// URI.
   */
  private String mapActionType(String actionType) {
    if (actionType == null) return null;
    switch (actionType.toLowerCase()) {
      case "nextslide": return "ppaction://hlinkshowjump?jump=nextslide";
      case "previousslide": return "ppaction://hlinkshowjump?jump=previousslide";
      case "firstslide": return "ppaction://hlinkshowjump?jump=firstslide";
      case "lastslide": return "ppaction://hlinkshowjump?jump=lastslide";
      case "lastslideviewed": return "ppaction://hlinkshowjump?jump=lastslideviewed";
      case "endshow": return "ppaction://hlinkshowjump?jump=endshow";
      case "noaction": return "ppaction://noaction";
      default: return "ppaction://hlinkshowjump?jump=" + actionType.toLowerCase();
    }
  }

  private static boolean isSchemeColor(String val) {
    String lower = val.toLowerCase();
    return lower.startsWith("accent") || lower.startsWith("dk") || lower.startsWith("lt")
        || "hlink".equals(lower) || "folhlink".equals(lower);
  }

  // ========== STYLE AND FONT OPERATIONS ==========

  /**
   * Update font properties on all text runs inside a shape.
   * Creates a:rPr elements when runs do not already carry one.
   *
   * @param spid      Shape SPID to modify
   * @param fontProps Map of properties: "family" (String), "size" (int pts),
   *                  "bold" (Boolean), "italic" (Boolean), "underline" (Boolean),
   *                  "color" (String hex or scheme name)
   */
  public void updateShapeTextProperties(int spid, Map<String, Object> fontProps)
      throws XMLParsingException {
    try {
      Element shape = findShapeBySpid(spid);
      if (shape == null) {
        throw new XMLParsingException("Shape with SPID " + spid + " not found");
      }

      NodeList rPrNodes = (NodeList) xpath.evaluate(".//a:rPr", shape, XPathConstants.NODESET);

      if (rPrNodes.getLength() == 0) {
        NodeList runs = (NodeList) xpath.evaluate(".//a:r", shape, XPathConstants.NODESET);
        for (int i = 0; i < runs.getLength(); i++) {
          Element run = (Element) runs.item(i);
          Element rPr = document.createElementNS(XMLConstants.DRAWING_NS, "a:rPr");
          rPr.setAttribute("lang", "en-US");
          run.insertBefore(rPr, run.getFirstChild());
        }
        rPrNodes = (NodeList) xpath.evaluate(".//a:rPr", shape, XPathConstants.NODESET);
      }

      for (int i = 0; i < rPrNodes.getLength(); i++) {
        Element rPr = (Element) rPrNodes.item(i);
        applyFontPropsToRunPr(rPr, fontProps);
      }

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to update text properties for SPID " + spid, e);
    }
  }

  private void applyFontPropsToRunPr(Element rPr, Map<String, Object> fontProps) {
    if (fontProps.containsKey("size")) {
      int sizeInPts = ((Number) fontProps.get("size")).intValue();
      rPr.setAttribute("sz", String.valueOf(sizeInPts * 100));
    }
    if (fontProps.containsKey("bold")) {
      rPr.setAttribute("b", Boolean.TRUE.equals(fontProps.get("bold")) ? "1" : "0");
    }
    if (fontProps.containsKey("italic")) {
      rPr.setAttribute("i", Boolean.TRUE.equals(fontProps.get("italic")) ? "1" : "0");
    }
    if (fontProps.containsKey("underline")) {
      rPr.setAttribute("u", Boolean.TRUE.equals(fontProps.get("underline")) ? "sng" : "none");
    }
    if (fontProps.containsKey("family")) {
      String family = (String) fontProps.get("family");
      NodeList existingLatin = rPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "latin");
      while (existingLatin.getLength() > 0) {
        rPr.removeChild(existingLatin.item(0));
      }
      Element latin = document.createElementNS(XMLConstants.DRAWING_NS, "a:latin");
      latin.setAttribute("typeface", family);
      rPr.appendChild(latin);
    }
    if (fontProps.containsKey("color")) {
      String color = (String) fontProps.get("color");
      NodeList existingFill = rPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "solidFill");
      while (existingFill.getLength() > 0) {
        rPr.removeChild(existingFill.item(0));
      }
      Element solidFill = document.createElementNS(XMLConstants.DRAWING_NS, "a:solidFill");
      if (isSchemeColor(color)) {
        Element schemeClr = document.createElementNS(XMLConstants.DRAWING_NS, "a:schemeClr");
        schemeClr.setAttribute("val", color);
        solidFill.appendChild(schemeClr);
      } else {
        Element srgbClr = document.createElementNS(XMLConstants.DRAWING_NS, "a:srgbClr");
        srgbClr.setAttribute("val", color.startsWith("#") ? color.substring(1) : color);
        solidFill.appendChild(srgbClr);
      }
      rPr.appendChild(solidFill);
    }
  }

  /**
   * Apply a ShapeStyle (fill, line, theme ref) to an existing shape in-place.
   * Delegates to ShapeStyleXMLWriter.applyStyle() which handles spPr and p:style injection.
   *
   * @param spid  Shape SPID to re-style
   * @param style ShapeStyle to apply
   */
  public void updateShapeStyle(int spid, ShapeStyle style) throws XMLParsingException {
    try {
      Element shape = findShapeBySpid(spid);
      if (shape == null) {
        throw new XMLParsingException("Shape with SPID " + spid + " not found");
      }
      boolean hasText = xpath.evaluate(".//a:t", shape, XPathConstants.NODE) != null;
      ShapeStyleXMLWriter.applyStyle(document, shape, style, hasText);
    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to update style for SPID " + spid, e);
    }
  }

  /**
   * Deep-clone a shape, assign it a new SPID, offset its position, and append it to the spTree.
   * The new SPID is computed from the highest id already present in the document.
   *
   * @param spid    SPID of the shape to clone
   * @param offsetX Horizontal offset in EMUs applied to the clone's a:off/@x
   * @param offsetY Vertical offset in EMUs applied to the clone's a:off/@y
   * @return The newly allocated SPID for the clone
   */
  public int duplicateShape(int spid, long offsetX, long offsetY) throws XMLParsingException {
    try {
      Element shape = findShapeBySpid(spid);
      if (shape == null) {
        throw new XMLParsingException("Shape with SPID " + spid + " not found");
      }

      Element clone = (Element) shape.cloneNode(true);

      int newSpid = computeNextAvailableSpidFromDocument();
      if (spidManager != null) {
        spidManager.registerSpid(newSpid, -1, "duplicated_shape");
      } else {
        nextAvailableSpid = newSpid + 1;
      }

      Element cNvPr = (Element) xpath.evaluate(".//*[local-name()='cNvPr']", clone,
          XPathConstants.NODE);
      if (cNvPr != null) {
        cNvPr.setAttribute("id", String.valueOf(newSpid));
        String existingName = cNvPr.getAttribute("name");
        cNvPr.setAttribute("name", existingName + " Copy");
      }

      Element xfrm = (Element) xpath.evaluate(".//a:xfrm", clone, XPathConstants.NODE);
      if (xfrm != null) {
        Element off = (Element) xpath.evaluate("./a:off", xfrm, XPathConstants.NODE);
        if (off != null) {
          long currentX = 0;
          long currentY = 0;
          String xAttr = off.getAttribute("x");
          String yAttr = off.getAttribute("y");
          if (!xAttr.isEmpty()) {
            try { currentX = Long.parseLong(xAttr); } catch (NumberFormatException ignored) {}
          }
          if (!yAttr.isEmpty()) {
            try { currentY = Long.parseLong(yAttr); } catch (NumberFormatException ignored) {}
          }
          off.setAttribute("x", String.valueOf(currentX + offsetX));
          off.setAttribute("y", String.valueOf(currentY + offsetY));
        }
      }

      shapeTree.appendChild(clone);
      return newSpid;

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to duplicate shape SPID " + spid, e);
    }
  }

  // ========== GROUP / UNGROUP OPERATIONS ==========

  /**
   * Group the specified shapes into a new p:grpSp element.
   * Computes the bounding box from all child shapes, moves them inside the group,
   * and appends the group to the shape tree.
   *
   * @param spids List of SPIDs to include in the group (must be >= 2)
   * @return The SPID allocated for the new group element
   */
  public int groupShapes(List<Integer> spids) throws XMLParsingException {
    try {
      List<Element> shapes = new java.util.ArrayList<>();
      for (int spid : spids) {
        Element shape = findShapeBySpid(spid);
        if (shape == null) {
          throw new XMLParsingException("Shape with SPID " + spid + " not found");
        }
        shapes.add(shape);
      }

      long minX = Long.MAX_VALUE, minY = Long.MAX_VALUE;
      long maxX = Long.MIN_VALUE, maxY = Long.MIN_VALUE;
      for (Element shape : shapes) {
        Element xfrm = (Element) xpath.evaluate(".//a:xfrm", shape, XPathConstants.NODE);
        if (xfrm != null) {
          Element off = (Element) xpath.evaluate("./a:off", xfrm, XPathConstants.NODE);
          Element ext = (Element) xpath.evaluate("./a:ext", xfrm, XPathConstants.NODE);
          if (off != null && ext != null) {
            long x = Long.parseLong(off.getAttribute("x"));
            long y = Long.parseLong(off.getAttribute("y"));
            long cx = Long.parseLong(ext.getAttribute("cx"));
            long cy = Long.parseLong(ext.getAttribute("cy"));
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + cx);
            maxY = Math.max(maxY, y + cy);
          }
        }
      }
      if (minX == Long.MAX_VALUE) { minX = 0; minY = 0; maxX = 0; maxY = 0; }

      int groupSpid = computeNextAvailableSpidFromDocument() + shapes.size();
      if (spidManager != null) {
        spidManager.registerSpid(groupSpid, -1, "group_shape");
      }

      Element grpSp = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:grpSp");

      Element nvGrpSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvGrpSpPr");
      Element cNvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
      cNvPr.setAttribute("id", String.valueOf(groupSpid));
      cNvPr.setAttribute("name", "Group " + groupSpid);
      nvGrpSpPr.appendChild(cNvPr);
      Element cNvGrpSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvGrpSpPr");
      nvGrpSpPr.appendChild(cNvGrpSpPr);
      Element nvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr");
      nvGrpSpPr.appendChild(nvPr);
      grpSp.appendChild(nvGrpSpPr);

      Element grpSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:grpSpPr");
      Element xfrmEl = document.createElementNS(XMLConstants.DRAWING_NS, "a:xfrm");
      Element grpOff = document.createElementNS(XMLConstants.DRAWING_NS, "a:off");
      grpOff.setAttribute("x", String.valueOf(minX));
      grpOff.setAttribute("y", String.valueOf(minY));
      xfrmEl.appendChild(grpOff);
      Element grpExt = document.createElementNS(XMLConstants.DRAWING_NS, "a:ext");
      grpExt.setAttribute("cx", String.valueOf(maxX - minX));
      grpExt.setAttribute("cy", String.valueOf(maxY - minY));
      xfrmEl.appendChild(grpExt);
      Element chOff = document.createElementNS(XMLConstants.DRAWING_NS, "a:chOff");
      chOff.setAttribute("x", String.valueOf(minX));
      chOff.setAttribute("y", String.valueOf(minY));
      xfrmEl.appendChild(chOff);
      Element chExt = document.createElementNS(XMLConstants.DRAWING_NS, "a:chExt");
      chExt.setAttribute("cx", String.valueOf(maxX - minX));
      chExt.setAttribute("cy", String.valueOf(maxY - minY));
      xfrmEl.appendChild(chExt);
      grpSpPr.appendChild(xfrmEl);
      grpSp.appendChild(grpSpPr);

      for (Element shape : shapes) {
        shape.getParentNode().removeChild(shape);
        grpSp.appendChild(shape);
      }

      shapeTree.appendChild(grpSp);
      logger.info("Grouped {} shapes into group SPID {}", shapes.size(), groupSpid);
      return groupSpid;

    } catch (XMLParsingException e) {
      throw e;
    } catch (Exception e) {
      throw new XMLParsingException("Failed to group shapes", e);
    }
  }

  /**
   * Dissolve a p:grpSp group shape, promoting all child shapes back into the spTree.
   *
   * @param groupSpid SPID of the p:grpSp element to dissolve
   * @return List of child SPIDs that were promoted out of the group
   */
  public List<Integer> ungroupShape(int groupSpid) throws XMLParsingException {
    try {
      Element grpSp = findShapeBySpid(groupSpid);
      if (grpSp == null) {
        throw new XMLParsingException("Group shape with SPID " + groupSpid + " not found");
      }
      if (!"grpSp".equals(grpSp.getLocalName())) {
        throw new XMLParsingException("Shape SPID " + groupSpid + " is not a group shape (found: " + grpSp.getLocalName() + ")");
      }

      List<Integer> childSpids = new java.util.ArrayList<>();
      List<Element> children = new java.util.ArrayList<>();
      NodeList childNodes = grpSp.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
        Node child = childNodes.item(i);
        if (child.getNodeType() == Node.ELEMENT_NODE) {
          String localName = child.getLocalName();
          if ("sp".equals(localName) || "pic".equals(localName) ||
              "cxnSp".equals(localName) || "grpSp".equals(localName)) {
            children.add((Element) child);
            Element childCNvPr = (Element) xpath.evaluate(
                ".//*[local-name()='cNvPr']", child, XPathConstants.NODE);
            if (childCNvPr != null) {
              try {
                childSpids.add(Integer.parseInt(childCNvPr.getAttribute("id")));
              } catch (NumberFormatException ignored) {}
            }
          }
        }
      }

      Node parent = grpSp.getParentNode();
      for (Element child : children) {
        grpSp.removeChild(child);
        parent.insertBefore(child, grpSp);
      }
      parent.removeChild(grpSp);

      logger.info("Ungrouped SPID {} into {} child shapes", groupSpid, childSpids.size());
      return childSpids;

    } catch (XMLParsingException e) {
      throw e;
    } catch (Exception e) {
      throw new XMLParsingException("Failed to ungroup shape SPID " + groupSpid, e);
    }
  }

  // ========== COPY STYLE OPERATIONS ==========

  /**
   * Extract the shape properties element (p:spPr) of the given shape as a deep clone.
   * Returns null if the shape has no spPr.
   *
   * @param spid SPID of the source shape
   * @return Cloned spPr element, or null if not found
   */
  public Element extractShapeStyle(int spid) throws XMLParsingException {
    try {
      Element shape = findShapeBySpid(spid);
      if (shape == null) {
        throw new XMLParsingException("Shape with SPID " + spid + " not found");
      }
      Element spPr = (Element) xpath.evaluate("./p:spPr", shape, XPathConstants.NODE);
      if (spPr == null) {
        spPr = (Element) xpath.evaluate("./a:spPr", shape, XPathConstants.NODE);
      }
      return spPr != null ? (Element) spPr.cloneNode(true) : null;
    } catch (XMLParsingException e) {
      throw e;
    } catch (Exception e) {
      throw new XMLParsingException("Failed to extract style from SPID " + spid, e);
    }
  }

  /**
   * Apply a previously extracted spPr style element to the target shape.
   * The target shape's existing geometry (a:xfrm) is preserved.
   *
   * @param targetSpid   SPID of the shape to restyle
   * @param styleElement Cloned spPr element from the source shape
   */
  public void applyShapeStyle(int targetSpid, Element styleElement) throws XMLParsingException {
    if (styleElement == null) return;
    try {
      Element shape = findShapeBySpid(targetSpid);
      if (shape == null) {
        throw new XMLParsingException("Shape with SPID " + targetSpid + " not found");
      }

      Element existingSpPr = (Element) xpath.evaluate("./p:spPr", shape, XPathConstants.NODE);
      if (existingSpPr == null) {
        existingSpPr = (Element) xpath.evaluate("./a:spPr", shape, XPathConstants.NODE);
      }

      Element existingXfrm = null;
      if (existingSpPr != null) {
        existingXfrm = (Element) xpath.evaluate("./a:xfrm", existingSpPr, XPathConstants.NODE);
        if (existingXfrm != null) {
          existingXfrm = (Element) existingXfrm.cloneNode(true);
        }
      }

      Element imported = (Element) document.importNode(styleElement, true);

      if (existingSpPr != null) {
        shape.replaceChild(imported, existingSpPr);
      } else {
        shape.appendChild(imported);
      }

      if (existingXfrm != null) {
        Element newXfrm = (Element) xpath.evaluate("./a:xfrm", imported, XPathConstants.NODE);
        Element importedXfrm = (Element) document.importNode(existingXfrm, true);
        if (newXfrm != null) {
          imported.replaceChild(importedXfrm, newXfrm);
        } else {
          imported.insertBefore(importedXfrm, imported.getFirstChild());
        }
      }

    } catch (XMLParsingException e) {
      throw e;
    } catch (Exception e) {
      throw new XMLParsingException("Failed to apply style to SPID " + targetSpid, e);
    }
  }

  /**
   * Copy the visual style (spPr) from one shape to a list of target shapes.
   * The position and size of each target are preserved.
   *
   * @param sourceSpid  Source shape SPID
   * @param targetSpids List of target shape SPIDs
   */
  public void copyShapeStyle(int sourceSpid, List<Integer> targetSpids) throws XMLParsingException {
    Element styleElement = extractShapeStyle(sourceSpid);
    for (int targetSpid : targetSpids) {
      applyShapeStyle(targetSpid, styleElement != null ? (Element) styleElement.cloneNode(true) : null);
    }
    logger.info("Copied style from SPID {} to {} shapes", sourceSpid, targetSpids.size());
  }

  // ========== PRIVATE HELPERS ==========

  public Element findShapeBySpid(int spid) throws XPathExpressionException {
    // Search all shape types: p:sp, p:pic, p:cxnSp, p:grpSp
    String xpathExpression = String.format(
        com.excudo.core.utils.XMLConstants.XPATH_SHAPE_OR_PICTURE_BY_SPID_TEMPLATE,
        spid, spid);
    Element result = (Element) xpath.evaluate(xpathExpression, document, XPathConstants.NODE);
    if (result != null) return result;
    // Fallback: connector shapes
    String cxnXpath = String.format("//p:cxnSp[p:nvCxnSpPr/p:cNvPr/@id='%d']", spid);
    result = (Element) xpath.evaluate(cxnXpath, document, XPathConstants.NODE);
    if (result != null) return result;
    // Fallback: group shapes
    String grpXpath = String.format("//p:grpSp[p:nvGrpSpPr/p:cNvPr/@id='%d']", spid);
    return (Element) xpath.evaluate(grpXpath, document, XPathConstants.NODE);
  }

  /**
   * Returns true if any element in the document already has this SPID in its p:cNvPr/@id.
   * Used to guard against stale SPIDManager registry after sequential slide creation.
   */
  private boolean spidExistsInDocument(int spid) {
    try {
      String expr = String.format("//p:cNvPr[@id='%d']", spid);
      org.w3c.dom.Node found = (org.w3c.dom.Node) xpath.evaluate(expr, document, XPathConstants.NODE);
      return found != null;
    } catch (XPathExpressionException e) {
      return false; // Fail open: assume no collision
    }
  }

  /**
   * Computes the next available SPID by scanning the current document for the
   * highest existing id value and returning max+1. Used as a fallback when the
   * SPIDManager registry is stale due to sequential slide creation patterns.
   */
  private int computeNextAvailableSpidFromDocument() {
    try {
      NodeList allIds = (NodeList) xpath.evaluate("//p:cNvPr/@id", document, XPathConstants.NODESET);
      int max = 0;
      for (int i = 0; i < allIds.getLength(); i++) {
        try {
          int id = Integer.parseInt(allIds.item(i).getNodeValue());
          if (id > max) max = id;
        } catch (NumberFormatException ignored) {}
      }
      return max + 1;
    } catch (XPathExpressionException e) {
      return 100; // Safe large fallback
    }
  }

  private int calculateNextSpid() throws XPathExpressionException {
    NodeList spids = (NodeList) xpath.evaluate("//p:cNvPr/@id", document, XPathConstants.NODESET);
    int maxSpid = 0;

    for (int i = 0; i < spids.getLength(); i++) {
      String spidStr = spids.item(i).getTextContent();
      try {
        int spidVal = Integer.parseInt(spidStr);
        maxSpid = Math.max(maxSpid, spidVal);
      } catch (NumberFormatException e) {
        // Skip non-numeric SPIDs
      }
    }

    return maxSpid + 1;
  }

  private void updateShapeTransform(Element shape, ShapeGeometry geometry) throws XPathExpressionException {
    Element xfrm = (Element) xpath.evaluate(".//a:xfrm", shape, XPathConstants.NODE);
    if (xfrm == null) return;

    Element off = (Element) xpath.evaluate("./a:off", xfrm, XPathConstants.NODE);
    if (off != null) {
      off.setAttribute("x", String.valueOf(geometry.getX()));
      off.setAttribute("y", String.valueOf(geometry.getY()));
    }

    Element ext = (Element) xpath.evaluate("./a:ext", xfrm, XPathConstants.NODE);
    if (ext != null) {
      ext.setAttribute("cx", String.valueOf(geometry.getWidth()));
      ext.setAttribute("cy", String.valueOf(geometry.getHeight()));
    }

    // For group shapes, keep child coordinate system (a:chOff, a:chExt) in sync.
    // When moving a group, chOff should match the new position so children render
    // at the same relative offsets. When resizing, chExt should match the new size.
    if ("grpSp".equals(shape.getLocalName())) {
      Element chOff = (Element) xpath.evaluate("./a:chOff", xfrm, XPathConstants.NODE);
      if (chOff != null) {
        chOff.setAttribute("x", String.valueOf(geometry.getX()));
        chOff.setAttribute("y", String.valueOf(geometry.getY()));
      }
      Element chExt = (Element) xpath.evaluate("./a:chExt", xfrm, XPathConstants.NODE);
      if (chExt != null) {
        chExt.setAttribute("cx", String.valueOf(geometry.getWidth()));
        chExt.setAttribute("cy", String.valueOf(geometry.getHeight()));
      }
    }
  }

  private void addTextToShape(Element shape, String text) {
    boolean isPlaceholder = isPlaceholderShape(shape);
    Element txBody = TextBodyXMLWriter.write(document, TextBody.fromPlainText(text, isPlaceholder));
    shape.appendChild(txBody);
  }

  private boolean isPlaceholderShape(Element shape) {
    try {
      return xpath.evaluate(".//p:ph", shape, XPathConstants.NODE) != null;
    } catch (Exception e) {
      return false;
    }
  }

}
