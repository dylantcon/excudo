package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import java.io.*;
import com.excudo.core.model.*;
import com.excudo.exceptions.*;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.model.TextBody;
import com.excudo.xml.builders.TextBodyXMLWriter;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Creates slide DOM documents with proper OOXML structure.
 * Handles blank slides, layout-specific slides, and shape tree construction.
 */
public class SlideDocumentBuilder {

  private static final ComponentLogger logger = Logger.getLogger(SlideDocumentBuilder.class);

  private final DocumentBuilder documentBuilder;
  private final SPIDManager spidManager;

  public SlideDocumentBuilder(DocumentBuilder documentBuilder, SPIDManager spidManager) {
    this.documentBuilder = documentBuilder;
    this.spidManager = spidManager;
  }

  /**
   * Create a blank slide document with minimal OOXML structure.
   *
   * @param slideTitle The title text
   * @param slideNumber The slide number for SPID allocation
   * @param layoutHasTitle Whether the assigned layout has a title placeholder (resolved by caller)
   */
  public Document createBlankSlideDocument(String slideTitle, int slideNumber, boolean layoutHasTitle) throws XMLParsingException {
    try {
      Document document = documentBuilder.newDocument();

      Element slide = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:sld");
      slide.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:a", XMLConstants.DRAWING_NS);
      slide.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:r", XMLConstants.RELATIONSHIPS_NS);
      slide.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:p", XMLConstants.PRESENTATION_NS);
      document.appendChild(slide);

      Element cSld = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cSld");
      slide.appendChild(cSld);

      Element spTree = createBlankShapeTree(document, slideTitle, slideNumber, layoutHasTitle);
      cSld.appendChild(spTree);

      Element clrMapOvr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:clrMapOvr");
      slide.appendChild(clrMapOvr);

      Element masterClrMapping = document.createElementNS(XMLConstants.DRAWING_NS, "a:masterClrMapping");
      clrMapOvr.appendChild(masterClrMapping);

      return document;

    } catch (Exception e) {
      throw new XMLParsingException("Failed to create blank slide document", e);
    }
  }

  /**
   * Create a slide document with layout-specific placeholder structure.
   *
   * @param slideTitle The title text
   * @param slideNumber The slide number for SPID allocation
   * @param layoutInfo Pre-resolved layout info (from LayoutManager, resolved by caller)
   */
  public Document createSlideWithLayoutDocument(String slideTitle, int slideNumber, LayoutInfo layoutInfo) throws XMLParsingException {
    try {
      Document document = documentBuilder.newDocument();

      Element slide = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:sld");
      slide.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:a", XMLConstants.DRAWING_NS);
      slide.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:r", XMLConstants.RELATIONSHIPS_NS);
      slide.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:p", XMLConstants.PRESENTATION_NS);
      document.appendChild(slide);

      Element cSld = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cSld");
      slide.appendChild(cSld);

      Element spTree = createLayoutShapeTree(document, slideTitle, slideNumber, layoutInfo);
      cSld.appendChild(spTree);

      Element clrMapOvr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:clrMapOvr");
      slide.appendChild(clrMapOvr);

      Element masterClrMapping = document.createElementNS(XMLConstants.DRAWING_NS, "a:masterClrMapping");
      clrMapOvr.appendChild(masterClrMapping);

      return document;

    } catch (Exception e) {
      throw new XMLParsingException("Failed to create slide document with layout " +
          (layoutInfo != null ? layoutInfo.getLayoutId() : "null"), e);
    }
  }

  // ========== SHAPE TREE CONSTRUCTION ==========

  Element createLayoutShapeTree(Document document, String slideTitle, int slideNumber, LayoutInfo layoutInfo) {
    Element spTree = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:spTree");

    addGroupShapeProperties(document, spTree, slideNumber);

    if (layoutInfo != null) {
      if (layoutInfo.hasTitlePlaceholder()) {
        String titleType = layoutInfo.getTitlePlaceholderType();
        Element titleShape = createTitleShape(document, slideTitle, slideNumber, titleType, layoutInfo);
        spTree.appendChild(titleShape);
      }

      if (layoutInfo.hasContentPlaceholder()) {
        int contentCount = Math.max(1, layoutInfo.getContentPlaceholderCount());
        for (int i = 0; i < contentCount; i++) {
          Element contentShape = createContentShape(document, "", slideNumber, layoutInfo, i);
          spTree.appendChild(contentShape);
        }
      }

      if (layoutInfo.hasSubtitlePlaceholder()) {
        Element subtitleShape = createSubtitleShape(document, "", slideNumber, layoutInfo);
        spTree.appendChild(subtitleShape);
      }

      logger.info("Created slide with layout {} ({})", layoutInfo.getLayoutId(), layoutInfo.getName());
    } else {
      logger.warn("LayoutInfo is null, falling back to title-only structure");
      Element titleShape = createTitleShape(document, slideTitle, slideNumber, "title");
      spTree.appendChild(titleShape);
    }

    return spTree;
  }

  Element createBlankShapeTree(Document document, String slideTitle, int slideNumber, boolean layoutHasTitle) {
    Element spTree = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:spTree");

    addGroupShapeProperties(document, spTree, slideNumber);

    if (layoutHasTitle) {
      Element titleShape = createTitleShape(document, slideTitle, slideNumber, "title");
      spTree.appendChild(titleShape);
    }

    return spTree;
  }

  // ========== SHAPE CREATION ==========

  Element createTitleShape(Document document, String slideTitle, int slideNumber, String placeholderType) {
    return createTitleShape(document, slideTitle, slideNumber, placeholderType, null);
  }

  Element createTitleShape(Document document, String slideTitle, int slideNumber, String placeholderType, LayoutInfo layoutInfo) {
    Element sp = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:sp");

    Element nvSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvSpPr");
    sp.appendChild(nvSpPr);

    Element cNvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
    int spid = spidManager.allocateSpidForShape("title", slideNumber, true, true, "slideLayout1");
    cNvPr.setAttribute("id", String.valueOf(spid));
    cNvPr.setAttribute("name", "Title Placeholder");
    nvSpPr.appendChild(cNvPr);

    Element cNvSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvSpPr");
    Element spLocks = document.createElementNS(XMLConstants.DRAWING_NS, "a:spLocks");
    spLocks.setAttribute("noGrp", "1");
    cNvSpPr.appendChild(spLocks);
    nvSpPr.appendChild(cNvSpPr);

    Element nvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr");
    Element ph = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:ph");
    ph.setAttribute("type", placeholderType);
    nvPr.appendChild(ph);
    nvSpPr.appendChild(nvPr);

    // Placeholders use empty spPr -- geometry inherited from layout/master chain
    Element spPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
    addGeometryFromLayout(document, spPr, layoutInfo, placeholderType);
    sp.appendChild(spPr);

    Element txBody = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:txBody");
    sp.appendChild(txBody);

    // Empty bodyPr -- properties inherited from layout/master
    Element bodyPr = document.createElementNS(XMLConstants.DRAWING_NS, "a:bodyPr");
    txBody.appendChild(bodyPr);

    Element lstStyle = document.createElementNS(XMLConstants.DRAWING_NS, "a:lstStyle");
    txBody.appendChild(lstStyle);

    String titleText = slideTitle != null ? slideTitle : "Click to add title";
    TextBodyXMLWriter.writeParagraphs(document, txBody, TextBody.fromPlainText(titleText, true));

    return sp;
  }

  Element createContentShape(Document document, String content, int slideNumber,
                                   LayoutInfo layoutInfo, int placeholderIndex) {
    Element sp = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:sp");

    // Resolve placeholder type and geometry from pre-loaded LayoutInfo
    String resolvedPhType = "body";
    PlaceholderGeometry matchedGeometry = null;

    if (layoutInfo != null && !layoutInfo.getPlaceholderGeometries().isEmpty()) {
      matchedGeometry = layoutInfo.getPlaceholderGeometryByIndex(
          String.valueOf(placeholderIndex + 1));

      if (matchedGeometry == null) {
        matchedGeometry = layoutInfo.getPlaceholderGeometryByType("body");
      }
      if (matchedGeometry == null) {
        matchedGeometry = layoutInfo.getPlaceholderGeometryByType("obj");
      }

      if (matchedGeometry != null && matchedGeometry.getPlaceholderType() != null
          && !matchedGeometry.getPlaceholderType().isEmpty()) {
        resolvedPhType = matchedGeometry.getPlaceholderType();
      }
    }

    Element nvSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvSpPr");
    sp.appendChild(nvSpPr);

    Element cNvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
    int spid = 3 + placeholderIndex;
    cNvPr.setAttribute("id", String.valueOf(spid));
    cNvPr.setAttribute("name", "Content Placeholder " + (placeholderIndex + 1));
    nvSpPr.appendChild(cNvPr);

    spidManager.registerSpid(spid, slideNumber, "Content Placeholder " + (placeholderIndex + 1));

    Element cNvSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvSpPr");
    Element spLocks = document.createElementNS(XMLConstants.DRAWING_NS, "a:spLocks");
    spLocks.setAttribute("noGrp", "1");
    cNvSpPr.appendChild(spLocks);
    nvSpPr.appendChild(cNvSpPr);

    Element nvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr");
    Element ph = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:ph");
    ph.setAttribute("type", resolvedPhType);
    ph.setAttribute("idx", String.valueOf(placeholderIndex + 1));
    nvPr.appendChild(ph);
    nvSpPr.appendChild(nvPr);

    Element spPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
    addGeometryFromLayout(document, spPr, matchedGeometry);
    sp.appendChild(spPr);

    Element txBody = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:txBody");
    sp.appendChild(txBody);

    Element bodyPr = document.createElementNS(XMLConstants.DRAWING_NS, "a:bodyPr");
    txBody.appendChild(bodyPr);

    Element lstStyle = document.createElementNS(XMLConstants.DRAWING_NS, "a:lstStyle");
    txBody.appendChild(lstStyle);

    String contentText = content != null ? content : "Click to add content";
    TextBodyXMLWriter.writeParagraphs(document, txBody, TextBody.fromPlainText(contentText, true));

    return sp;
  }

  Element createSubtitleShape(Document document, String subtitle, int slideNumber) {
    return createSubtitleShape(document, subtitle, slideNumber, null);
  }

  Element createSubtitleShape(Document document, String subtitle, int slideNumber, LayoutInfo layoutInfo) {
    Element sp = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:sp");

    Element nvSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvSpPr");
    sp.appendChild(nvSpPr);

    Element cNvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
    int spid = spidManager.allocateSpidForShape("subtitle", slideNumber, true, true, null);
    cNvPr.setAttribute("id", String.valueOf(spid));
    cNvPr.setAttribute("name", "Subtitle Shape");
    nvSpPr.appendChild(cNvPr);

    spidManager.registerSpid(spid, slideNumber, "Subtitle Shape");

    Element cNvSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvSpPr");
    Element spLocks = document.createElementNS(XMLConstants.DRAWING_NS, "a:spLocks");
    spLocks.setAttribute("noGrp", "1");
    cNvSpPr.appendChild(spLocks);
    nvSpPr.appendChild(cNvSpPr);

    Element nvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr");
    Element ph = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:ph");
    ph.setAttribute("type", "subTitle");
    nvPr.appendChild(ph);
    nvSpPr.appendChild(nvPr);

    Element spPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
    addGeometryFromLayout(document, spPr, layoutInfo, "subTitle");
    sp.appendChild(spPr);

    Element txBody = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:txBody");
    sp.appendChild(txBody);

    Element bodyPr = document.createElementNS(XMLConstants.DRAWING_NS, "a:bodyPr");
    txBody.appendChild(bodyPr);

    Element lstStyle = document.createElementNS(XMLConstants.DRAWING_NS, "a:lstStyle");
    txBody.appendChild(lstStyle);

    String subtitleText = subtitle != null ? subtitle : "Click to add subtitle";
    TextBodyXMLWriter.writeParagraphs(document, txBody, TextBody.fromPlainText(subtitleText, true));

    return sp;
  }

  // ========== HELPERS ==========

  private void addGeometryFromLayout(Document document, Element spPr, LayoutInfo layoutInfo, String placeholderType) {
    if (layoutInfo == null || placeholderType == null) {
      return;
    }
    addGeometryFromLayout(document, spPr, layoutInfo.getPlaceholderGeometryByType(placeholderType));
  }

  private void addGeometryFromLayout(Document document, Element spPr, PlaceholderGeometry geometry) {
    if (geometry == null) {
      return;
    }
    Element xfrm = document.createElementNS(XMLConstants.DRAWING_NS, "a:xfrm");
    Element off = document.createElementNS(XMLConstants.DRAWING_NS, "a:off");
    off.setAttribute("x", String.valueOf(geometry.getX()));
    off.setAttribute("y", String.valueOf(geometry.getY()));
    xfrm.appendChild(off);
    Element ext = document.createElementNS(XMLConstants.DRAWING_NS, "a:ext");
    ext.setAttribute("cx", String.valueOf(geometry.getWidth()));
    ext.setAttribute("cy", String.valueOf(geometry.getHeight()));
    xfrm.appendChild(ext);
    spPr.appendChild(xfrm);
  }

  private void addGroupShapeProperties(Document document, Element spTree, int slideNumber) {
    Element nvGrpSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvGrpSpPr");
    spTree.appendChild(nvGrpSpPr);

    Element cNvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
    int groupSpid = spidManager.allocateSpidForShape("group", slideNumber, false, true, null);
    cNvPr.setAttribute("id", String.valueOf(groupSpid));
    cNvPr.setAttribute("name", "");
    nvGrpSpPr.appendChild(cNvPr);

    spidManager.registerSpid(groupSpid, slideNumber, "Group Shape");

    Element cNvGrpSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvGrpSpPr");
    nvGrpSpPr.appendChild(cNvGrpSpPr);

    Element nvPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr");
    nvGrpSpPr.appendChild(nvPr);

    Element grpSpPr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:grpSpPr");
    spTree.appendChild(grpSpPr);

    Element xfrm = document.createElementNS(XMLConstants.DRAWING_NS, "a:xfrm");
    grpSpPr.appendChild(xfrm);

    addTransformElement(document, xfrm, "a:off", "0", "0");
    addTransformElement(document, xfrm, "a:ext", "0", "0");
    addTransformElement(document, xfrm, "a:chOff", "0", "0");
    addTransformElement(document, xfrm, "a:chExt", "0", "0");
  }

  void addTransformElement(Document document, Element parent, String elementName, String x, String y) {
    Element element = document.createElementNS(XMLConstants.DRAWING_NS, elementName);
    if (elementName.contains("ext") || elementName.contains("Ext")) {
      element.setAttribute("cx", x);
      element.setAttribute("cy", y);
    } else {
      element.setAttribute("x", x);
      element.setAttribute("y", y);
    }
    parent.appendChild(element);
  }
}
