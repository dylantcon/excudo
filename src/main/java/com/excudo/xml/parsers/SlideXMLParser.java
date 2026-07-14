package com.excudo.xml.parsers;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import java.io.*;
import java.util.*;
import java.util.HashMap;
import com.excudo.core.model.*;
import com.excudo.exceptions.*;
import com.excudo.utils.ParagraphMetadataParser;
import com.excudo.core.model.ParagraphMetadata;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.animations.AnimationFactoryRegistry;

/**
 * Core XML parser for PowerPoint slide files (.xml extracted from .pptx)
 * Handles DOM parsing and extraction of shapes, timing, and animation data
 */
public class SlideXMLParser {

  private static final ComponentLogger logger = Logger.getLogger(SlideXMLParser.class);

  private final DocumentBuilder documentBuilder;
  private final XPath xpath;
  private final AnimationFactoryRegistry animationRegistry;

  public SlideXMLParser() throws XMLParsingException {
    try {
      // Use centralized factory provider for consistent configuration
      this.documentBuilder = XMLFactoryProvider.createDocumentBuilder();
      this.xpath = XMLFactoryProvider.createXPath();

      // Initialize animation factory registry for animation parsing
      this.animationRegistry = new AnimationFactoryRegistry();

    } catch (ParserConfigurationException e) {
      throw new XMLParsingException("Failed to initialize XML parser", e);
    }
  }

  /**
   * Parse a slide XML file and return the Document for further manipulation
   */
  public Document parseSlideDocument(File xmlFile) throws XMLParsingException {
    try {
      return documentBuilder.parse(xmlFile);
    } catch (Exception e) {
      throw new XMLParsingException("Failed to parse slide XML file: " + xmlFile.getName(), e);
    }
  }

  /**
   * Parse a slide XML file and extract all critical data
   */
  public ParsedSlideData parseSlide(File xmlFile) throws XMLParsingException {
    try {
      Document document = documentBuilder.parse(xmlFile);
      
      // Extract slide number from filename (e.g., slide1.xml -> 1)
      String filename = xmlFile.getName();
      int slideNumber = extractSlideNumberFromFilename(filename);
      
      return parseSlide(document, slideNumber);
    } catch (Exception e) {
      throw new XMLParsingException("Failed to parse slide XML file: " + xmlFile.getName(), e);
    }
  }
  
  /**
   * Parse a slide XML document and extract all critical data
   */
  public ParsedSlideData parseSlide(Document document) throws XMLParsingException {
    return parseSlide(document, -1); // Use -1 to indicate unknown slide number
  }
  
  /**
   * Parse a slide XML document with slide number context for layout resolution
   */
  public ParsedSlideData parseSlide(Document document, int slideNumber) throws XMLParsingException {
    try {
      // Unwrap mc:AlternateContent before any DOM walk. PowerPoint authors
      // shapes that use Office 2010+ extensions (like a14:m for math)
      // inside a <mc:Choice Requires="a14"> branch with a <mc:Fallback>
      // sibling for older readers. Without unwrapping, our XPath misses
      // the Choice shapes entirely, so on slide-with-math files (e.g.
      // /tmp/Uncertainty Quantification.pptx slides 8/10/12) the body
      // doesn't render at all.
      MarkupCompatibilityUnwrapper.unwrap(document);

      // Extract the three core data structures with slide context
      ShapeRegistry shapeRegistry = extractShapes(document, slideNumber);
      TimingTree timingTree = extractTimingTree(document);
      List<AnimationBinding> animationBindings = extractAnimationBindings(document, timingTree);

      // Resolve layout ID from slide rels (e.g., "slideLayout2")
      String layoutId = (slideNumber > 0) ? SlideLayoutParser.resolveLayoutId(slideNumber) : null;

      return new ParsedSlideData(shapeRegistry, timingTree, animationBindings, layoutId);

    } catch (Exception e) {
      throw new XMLParsingException("Failed to parse slide document", e);
    }
  }

  /**
   * Extract all shapes from the slide with their spid mappings
   */
  private ShapeRegistry extractShapes(Document document) throws XPathExpressionException {
    return extractShapes(document, -1); // Use -1 to indicate unknown slide number
  }
  
  /**
   * Extract all shapes from the slide with slide number context for layout resolution
   */
  private ShapeRegistry extractShapes(Document document, int slideNumber) throws XPathExpressionException {
    ShapeRegistry registry = new ShapeRegistry();

    // Find all shapes in the shape tree
    NodeList shapeNodes = (NodeList) xpath.evaluate(
        com.excudo.core.utils.XMLConstants.XPATH_ALL_SHAPES_AND_PICTURES, 
        document, 
        XPathConstants.NODESET
        );

    for (int i = 0; i < shapeNodes.getLength(); i++) {
      Element shapeElement = (Element) shapeNodes.item(i);
      SlideShape shape = parseShapeElement(shapeElement, slideNumber);
      if (shape != null) {
        registry.addShape(shape);
        // Recursively register children of group shapes so they are visible
        // to bulk operations, LLM context retrieval, and SPID-based lookups
        if (shape.getType() == SlideShape.ShapeType.GROUP) {
          registerGroupChildren(shapeElement, shape.getSpid(), slideNumber, registry);
        }
      }
    }

    return registry;
  }

  /**
   * Recursively register child shapes inside a group shape.
   * Transforms child coordinates from the group's child coordinate system
   * (a:chOff/a:chExt) to absolute slide coordinates so the flat ShapeRegistry
   * contains renderable shapes with correct positions.
   */
  private void registerGroupChildren(Element groupElement, int parentGroupSpid, int slideNumber, ShapeRegistry registry)
      throws XPathExpressionException {
    // Extract group transform: position on slide + child coordinate system
    String grpX = (String) xpath.evaluate("p:grpSpPr/a:xfrm/a:off/@x", groupElement, XPathConstants.STRING);
    String grpY = (String) xpath.evaluate("p:grpSpPr/a:xfrm/a:off/@y", groupElement, XPathConstants.STRING);
    String grpCx = (String) xpath.evaluate("p:grpSpPr/a:xfrm/a:ext/@cx", groupElement, XPathConstants.STRING);
    String grpCy = (String) xpath.evaluate("p:grpSpPr/a:xfrm/a:ext/@cy", groupElement, XPathConstants.STRING);
    String chOffX = (String) xpath.evaluate("p:grpSpPr/a:xfrm/a:chOff/@x", groupElement, XPathConstants.STRING);
    String chOffY = (String) xpath.evaluate("p:grpSpPr/a:xfrm/a:chOff/@y", groupElement, XPathConstants.STRING);
    String chExtCx = (String) xpath.evaluate("p:grpSpPr/a:xfrm/a:chExt/@cx", groupElement, XPathConstants.STRING);
    String chExtCy = (String) xpath.evaluate("p:grpSpPr/a:xfrm/a:chExt/@cy", groupElement, XPathConstants.STRING);

    long gx = parseLongSafe(grpX);
    long gy = parseLongSafe(grpY);
    long gcx = parseLongSafe(grpCx);
    long gcy = parseLongSafe(grpCy);
    long cox = parseLongSafe(chOffX);
    long coy = parseLongSafe(chOffY);
    long cecx = parseLongSafe(chExtCx);
    long cecy = parseLongSafe(chExtCy);

    org.w3c.dom.NodeList children = groupElement.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (!(children.item(i) instanceof Element child)) continue;
      String tag = child.getTagName();
      if ("p:sp".equals(tag) || "p:pic".equals(tag) || "p:cxnSp".equals(tag)
          || "p:grpSp".equals(tag) || "p:graphicFrame".equals(tag)) {
        SlideShape childShape = parseShapeElement(child, slideNumber);
        if (childShape != null) {
          // Transform child coordinates from group-space to slide-space
          if (childShape.getGeometry() != null && cecx > 0 && cecy > 0) {
            ShapeGeometry cg = childShape.getGeometry();
            long absX = gx + (cg.getX() - cox) * gcx / cecx;
            long absY = gy + (cg.getY() - coy) * gcy / cecy;
            long absW = cg.getWidth() * gcx / cecx;
            long absH = cg.getHeight() * gcy / cecy;
            // withBounds keeps rotation, flips, and the geometry payload
            // (preset name / adjust values / custGeom) while re-basing
            // into slide coordinates. The table payload rides along on
            // the shape itself.
            childShape = new SlideShape(childShape.getSpid(), childShape.getName(),
                childShape.getType(), childShape.getTextContent(),
                cg.withBounds(absX, absY, absW, absH),
                childShape.getXmlElement(), childShape.getParagraphMetadata(),
                childShape.isTextBox(), childShape.getTableModel());
          }
          registry.addShape(childShape);
          // Record structural parentage at parse time -- consumers that
          // need to know "is SPID X inside a group" read this directly
          // rather than re-deriving from geometry downstream.
          registry.registerGroupMembership(childShape.getSpid(), parentGroupSpid);
          if (childShape.getType() == SlideShape.ShapeType.GROUP) {
            registerGroupChildren(child, childShape.getSpid(), slideNumber, registry);
          }
        }
      }
    }
  }

  private static long parseLongSafe(String s) {
    if (s == null || s.isEmpty()) return 0;
    try { return Long.parseLong(s); }
    catch (NumberFormatException e) { return 0; }
  }

  /**
   * Parse an individual shape element
   */
  private SlideShape parseShapeElement(Element shapeElement) throws XPathExpressionException {
    return parseShapeElement(shapeElement, -1); // Use -1 to indicate unknown slide number
  }
  
  /**
   * Parse an individual shape element with slide number context
   */
  private SlideShape parseShapeElement(Element shapeElement, int slideNumber) throws XPathExpressionException {
    // Extract spid from cNvPr element
    String spidStr = (String) xpath.evaluate(com.excudo.core.utils.XMLConstants.XPATH_SHAPE_ID_ATTRIBUTE, shapeElement, XPathConstants.STRING);
    if (spidStr.isEmpty()) return null;

    int spid = Integer.parseInt(spidStr);

    // Extract shape name
    String name = (String) xpath.evaluate(com.excudo.core.utils.XMLConstants.XPATH_SHAPE_NAME_ATTRIBUTE, shapeElement, XPathConstants.STRING);

    // Determine shape type
    String tagName = shapeElement.getTagName();
    if (tagName.equals("p:graphicFrame")) {
      return parseGraphicFrameElement(shapeElement, spid, name);
    }
    SlideShape.ShapeType type;
    if (tagName.equals("p:pic")) {
      type = SlideShape.ShapeType.PICTURE;
    } else if (tagName.equals("p:cxnSp")) {
      type = SlideShape.ShapeType.CONNECTION;
    } else if (tagName.equals("p:grpSp")) {
      type = SlideShape.ShapeType.GROUP;
    } else {
      // For p:sp elements, try to determine from preset geometry
      type = determineShapeTypeFromGeometry(shapeElement);
    }

    // Extract text content if present
    String textContent = (String) xpath.evaluate(com.excudo.core.utils.XMLConstants.XPATH_SHAPE_TEXT_CONTENT, shapeElement, XPathConstants.STRING);

    // Extract position and size with slide number context
    ShapeGeometry geometry = extractShapeGeometry(shapeElement, slideNumber);
    
    // Extract paragraph metadata if shape has text
    ParagraphMetadata paragraphMetadata = null;
    if (textContent != null && !textContent.trim().isEmpty()) {
      try {
        // Try to get the text body element for direct OOXML parsing
        Element txBodyElement = (Element) xpath.evaluate(".//p:txBody", shapeElement, XPathConstants.NODE);
        if (txBodyElement != null) {
          // Check if this is a content placeholder (has placeholder element but not a title)
          boolean isContentPlaceholder = false;
          if (type == SlideShape.ShapeType.PLACEHOLDER) {
            // Check if it has a placeholder element with idx attribute (content placeholders)
            Element ph = (Element) xpath.evaluate(".//p:ph[@idx]", shapeElement, XPathConstants.NODE);
            isContentPlaceholder = (ph != null);
          }
          paragraphMetadata = ParagraphMetadataParser.parseFromOOXML(txBodyElement, isContentPlaceholder);
        } else {
          // Fallback to text content parsing
          paragraphMetadata = ParagraphMetadataParser.parseTextContent(textContent);
        }
      } catch (Exception e) {
        // If parsing fails, fall back to text content parsing
        paragraphMetadata = ParagraphMetadataParser.parseTextContent(textContent);
      }
    }

    // Read OOXML's cNvSpPr/@txBox marker so consumers can distinguish a
    // shape authored as a Text Box (Insert -> Text Box in PowerPoint)
    // from a styled rectangle that happens to contain text. Both have
    // structural ShapeType=RECTANGLE, but the txBox flag carries the
    // authorial-intent distinction the spec encodes.
    boolean isTextBox = false;
    try {
      String txBoxAttr = (String) xpath.evaluate(
          ".//p:cNvSpPr/@txBox", shapeElement, XPathConstants.STRING);
      isTextBox = "1".equals(txBoxAttr) || "true".equalsIgnoreCase(txBoxAttr);
    } catch (XPathExpressionException ignored) {
      // Treat absent attribute as not-a-text-box (default).
    }

    return new SlideShape(spid, name, type, textContent, geometry, shapeElement, paragraphMetadata, isTextBox);
  }
  
  /**
   * Parse a {@code p:graphicFrame}. Only the DrawingML table payload
   * ({@code a:graphicData} with the table URI) becomes a shape — a
   * TABLE-typed {@link SlideShape} carrying the eagerly parsed
   * {@link TableModel}. Non-table graphicFrames (charts, SmartArt, OLE
   * objects) return null and stay invisible to the registry pending
   * their own parsing phases; that policy is pinned by
   * GraphicFrameSynthesisFirewallTest.
   *
   * <p>Malformed table XML throws ({@link TableModel#parse} validates
   * grid/rows/spans) — never a silently empty table.
   */
  private SlideShape parseGraphicFrameElement(Element frameElement, int spid, String name)
      throws XPathExpressionException {
    Element graphicData = (Element) xpath.evaluate(
        "a:graphic/a:graphicData", frameElement, XPathConstants.NODE);
    if (graphicData == null || !TABLE_GRAPHIC_URI.equals(graphicData.getAttribute("uri"))) {
      return null;
    }

    Element tbl = (Element) xpath.evaluate("a:tbl", graphicData, XPathConstants.NODE);
    if (tbl == null) {
      throw new IllegalArgumentException("p:graphicFrame SPID " + spid
          + " declares the table graphicData URI but has no a:tbl child");
    }
    TableModel table = TableModel.parse(tbl);

    // Frame geometry lives in p:xfrm (presentation namespace, unlike the
    // a:xfrm of p:spPr), with the same a:off/a:ext children.
    // CT_GraphicalObjectFrame requires it; a table with no position is
    // malformed.
    Element xfrm = (Element) xpath.evaluate("p:xfrm", frameElement, XPathConstants.NODE);
    if (xfrm == null) {
      throw new IllegalArgumentException("p:graphicFrame SPID " + spid + " has no p:xfrm");
    }
    long x = requiredEmu(xfrm, "a:off/@x", spid);
    long y = requiredEmu(xfrm, "a:off/@y", spid);
    long cx = requiredEmu(xfrm, "a:ext/@cx", spid);
    long cy = requiredEmu(xfrm, "a:ext/@cy", spid);
    String rotStr = xfrm.getAttribute("rot");
    int rot = rotStr.isEmpty() ? 0 : Integer.parseInt(rotStr);
    boolean flipH = parseXmlBoolean(xfrm.getAttribute("flipH"));
    boolean flipV = parseXmlBoolean(xfrm.getAttribute("flipV"));
    ShapeGeometry geometry = new ShapeGeometry(x, y, cx, cy, rot, flipH, flipV,
        null, java.util.Map.of(), null);

    // textContent stays null: a table is not a text shape — cell text
    // lives in the TableModel and flows through the cell text pipeline.
    return new SlideShape(spid, name, SlideShape.ShapeType.TABLE, null,
        geometry, frameElement, null, false, table);
  }

  private static final String TABLE_GRAPHIC_URI =
      "http://schemas.openxmlformats.org/drawingml/2006/table";

  private long requiredEmu(Element xfrm, String path, int spid) throws XPathExpressionException {
    String v = (String) xpath.evaluate(path, xfrm, XPathConstants.STRING);
    if (v == null || v.isEmpty()) {
      throw new IllegalArgumentException("p:graphicFrame SPID " + spid
          + " p:xfrm is missing " + path);
    }
    return Long.parseLong(v);
  }

  /**
   * Extract slide number from filename (e.g., slide1.xml -> 1)
   */
  private int extractSlideNumberFromFilename(String filename) {
    try {
      if (filename.startsWith("slide") && filename.endsWith(".xml")) {
        String numberPart = filename.substring(5, filename.length() - 4);
        return Integer.parseInt(numberPart);
      }
    } catch (NumberFormatException e) {
      // Ignore parse errors
    }
    return -1; // Return -1 for unknown slide numbers
  }

  /**
   * Extract shape position and size information
   */
  private ShapeGeometry extractShapeGeometry(Element shapeElement) throws XPathExpressionException {
    return extractShapeGeometry(shapeElement, -1); // Use -1 to indicate unknown slide number
  }
  
  /**
   * Extract shape position and size information with slide number context
   */
  private ShapeGeometry extractShapeGeometry(Element shapeElement, int slideNumber) throws XPathExpressionException {
    String xStr = (String) xpath.evaluate(com.excudo.core.utils.XMLConstants.XPATH_SHAPE_X_POSITION, shapeElement, XPathConstants.STRING);
    String yStr = (String) xpath.evaluate(com.excudo.core.utils.XMLConstants.XPATH_SHAPE_Y_POSITION, shapeElement, XPathConstants.STRING);
    String cxStr = (String) xpath.evaluate(com.excudo.core.utils.XMLConstants.XPATH_SHAPE_WIDTH, shapeElement, XPathConstants.STRING);
    String cyStr = (String) xpath.evaluate(com.excudo.core.utils.XMLConstants.XPATH_SHAPE_HEIGHT, shapeElement, XPathConstants.STRING);

    // PowerPoint uses EMUs (English Metric Units)
    long x = xStr.isEmpty() ? 0 : Long.parseLong(xStr);
    long y = yStr.isEmpty() ? 0 : Long.parseLong(yStr);
    long cx = cxStr.isEmpty() ? 0 : Long.parseLong(cxStr);
    long cy = cyStr.isEmpty() ? 0 : Long.parseLong(cyStr);

    // Check if this is a placeholder shape with missing geometry
    if ((x == 0 && y == 0 && cx == 0 && cy == 0) || (xStr.isEmpty() && yStr.isEmpty() && cxStr.isEmpty() && cyStr.isEmpty())) {
      ShapeGeometry placeholderGeometry = resolveplaceholderGeometry(shapeElement, slideNumber);
      if (placeholderGeometry != null) {
        return placeholderGeometry;
      }
    }

    // Extract rotation from a:xfrm/@rot (60,000ths of a degree)
    String rotStr = (String) xpath.evaluate(".//a:xfrm/@rot", shapeElement, XPathConstants.STRING);
    int rot = (rotStr != null && !rotStr.isEmpty()) ? Integer.parseInt(rotStr) : 0;

    // Extract mirror flags from a:xfrm/@flipH and @flipV. Critical for
    // connectors: a straight connector stores its endpoints as a bounding
    // box plus flip flags that pick which diagonal the line runs along.
    // Dropping them made every flipped connector render on the wrong diagonal.
    boolean flipH = parseXmlBoolean(
        (String) xpath.evaluate(".//a:xfrm/@flipH", shapeElement, XPathConstants.STRING));
    boolean flipV = parseXmlBoolean(
        (String) xpath.evaluate(".//a:xfrm/@flipV", shapeElement, XPathConstants.STRING));

    // Geometry payload: the raw preset name + avLst overrides, or the
    // full parsed custGeom. Scoped to this shape's own p:spPr (not
    // ".//") so a group element never picks up a child's geometry.
    String presetName = null;
    Map<String, Integer> adjustValues = Map.of();
    com.excudo.core.geometry.GeometryDefinition customGeometry = null;
    Element prstGeom = (Element) xpath.evaluate(
        "p:spPr/a:prstGeom", shapeElement, XPathConstants.NODE);
    if (prstGeom != null) {
      String prst = prstGeom.getAttribute("prst");
      if (prst != null && !prst.isEmpty()) {
        presetName = prst;
        adjustValues = extractAdjustValues(prstGeom);
      }
    } else {
      Element custGeom = (Element) xpath.evaluate(
          "p:spPr/a:custGeom", shapeElement, XPathConstants.NODE);
      if (custGeom != null) {
        // Malformed custom geometry throws (no rectangle fallback) --
        // see CustomGeometryParser.
        customGeometry = com.excudo.core.geometry.CustomGeometryParser.parse(custGeom);
      }
    }

    return new ShapeGeometry(x, y, cx, cy, rot, flipH, flipV,
        presetName, adjustValues, customGeometry);
  }

  /**
   * Parse a:prstGeom/a:avLst adjust-value overrides. Each gd carries a
   * "val N" formula per the prstGeom schema; anything else is malformed
   * and throws rather than being silently dropped.
   */
  private Map<String, Integer> extractAdjustValues(Element prstGeom)
      throws XPathExpressionException {
    NodeList gds = (NodeList) xpath.evaluate(
        "a:avLst/a:gd", prstGeom, XPathConstants.NODESET);
    if (gds.getLength() == 0) return Map.of();
    Map<String, Integer> values = new HashMap<>();
    for (int i = 0; i < gds.getLength(); i++) {
      Element gd = (Element) gds.item(i);
      String name = gd.getAttribute("name");
      String fmla = gd.getAttribute("fmla").trim();
      if (name.isEmpty() || !fmla.startsWith("val ")) {
        throw new IllegalArgumentException(
            "prstGeom avLst gd must be name + 'val N', got name='" + name
            + "' fmla='" + fmla + "'");
      }
      try {
        values.put(name, Integer.parseInt(fmla.substring(4).trim()));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "prstGeom avLst gd '" + name + "' has non-integer value: " + fmla, e);
      }
    }
    return values;
  }

  /** Parse an OOXML xsd:boolean attribute ("1"/"0"/"true"/"false"). Empty/null -> false. */
  private static boolean parseXmlBoolean(String value) {
    if (value == null || value.isEmpty()) return false;
    return value.equals("1") || value.equalsIgnoreCase("true");
  }

  /**
   * Extract the complete timing tree structure
   */
  private TimingTree extractTimingTree(Document document) throws XPathExpressionException {
    Element timingElement = (Element) xpath.evaluate(com.excudo.core.utils.XMLConstants.XPATH_TIMING_ROOT_ELEMENT, document, XPathConstants.NODE);
    if (timingElement == null) {
      return new TimingTree(); // Empty timing tree
    }

    return parseTimingElement(timingElement);
  }

  /**
   * Parse the timing element into a structured tree
   */
  private TimingTree parseTimingElement(Element timingElement) throws XPathExpressionException {
    TimingTree tree = new TimingTree();

    // Find the main sequence
    Element mainSeq = (Element) xpath.evaluate(com.excudo.core.utils.XMLConstants.XPATH_MAIN_ANIMATION_SEQUENCE, timingElement, XPathConstants.NODE);
    if (mainSeq != null) {
      TimingNode rootNode = parseTimingNode(mainSeq);
      tree.setRootNode(rootNode);
    }

    return tree;
  }

  /**
   * Recursively parse timing nodes
   */
  private TimingNode parseTimingNode(Element element) throws XPathExpressionException {
    // The timing attributes are on the child <p:cTn> element, not the parent
    Element cTnElement = (Element) xpath.evaluate(com.excudo.core.utils.XMLConstants.XPATH_TIMING_CTN_ELEMENT, element, XPathConstants.NODE);
    if (cTnElement == null) {
      // If no cTn child, this element might BE the cTn element
      cTnElement = element;
    }

    String nodeId = cTnElement.getAttribute("id");
    String nodeType = cTnElement.getAttribute("nodeType");
    String duration = cTnElement.getAttribute("dur");

    TimingNode node = new TimingNode(nodeId, nodeType, duration);

    // Extract delay information for click triggers
    String delay = (String) xpath.evaluate(com.excudo.core.utils.XMLConstants.XPATH_TIMING_DELAY_ATTRIBUTE, 
        cTnElement, XPathConstants.STRING);
    if (!delay.isEmpty()) {
      node.setDelay(delay);
    }

    // Parse child timing nodes - handle both par and seq elements
    NodeList children = (NodeList) xpath.evaluate(com.excudo.core.utils.XMLConstants.XPATH_TIMING_CTN_CHILDREN, 
        cTnElement, XPathConstants.NODESET);

    for (int i = 0; i < children.getLength(); i++) {
      Element childElement = (Element) children.item(i);
      TimingNode childNode = parseTimingNode(childElement);
      node.addChild(childNode);
    }

    return node;
  }

  /**
   * Extract animation bindings from slide document.
   * Parses presetID, presetClass, and presetSubtype from p:cTn attributes --
   * PowerPoint's canonical animation identifiers.
   */
  private List<AnimationBinding> extractAnimationBindings(Document document, TimingTree timingTree)
      throws XPathExpressionException {

      List<AnimationBinding> bindings = new ArrayList<>();

      // Find ALL p:par elements with animation content (expanded to include p:animScale, p:set, etc.)
      // Updated XPath to capture animation containers with nodeType attributes (actual animation effects)
      NodeList parElements = (NodeList) xpath.evaluate(
          "//p:par[p:cTn[@nodeType='clickEffect' or @nodeType='withEffect' or @nodeType='afterEffect'] and .//p:spTgt/@spid]",
          document,
          XPathConstants.NODESET
      );

      for (int i = 0; i < parElements.getLength(); i++) {
        Element parElement = (Element) parElements.item(i);

        try {
          AnimationBinding binding = parseAnimationBinding(parElement);
          if (binding != null) {
            bindings.add(binding);
          }
        } catch (Exception e) {
          // Log parsing failure but continue with other animations
          logger.warn("Failed to parse animation from p:par element: {}", e.getMessage());
        }
      }

      return bindings;
  }

  /**
   * Parse an AnimationBinding from a p:par element using presetID, presetClass, and presetSubtype
   * attributes from the p:cTn element -- PowerPoint's canonical animation identifiers.
   */
  private AnimationBinding parseAnimationBinding(Element parElement) throws XPathExpressionException {
    // First check presetID to identify animation type (PowerPoint's primary identifier)
    Element cTnElement = (Element) xpath.evaluate("./p:cTn", parElement, XPathConstants.NODE);
    String presetID = cTnElement != null ? cTnElement.getAttribute("presetID") : "";
    String presetClass = cTnElement != null ? cTnElement.getAttribute("presetClass") : "entr";
    String presetSubtype = cTnElement != null ? cTnElement.getAttribute("presetSubtype") : "";
    
    // Extract target shape ID - look in multiple locations for spid
    String spidStr = "";
    Element targetElement = null;
    
    // Try different element patterns to find the spid
    Element animEffectElement = (Element) xpath.evaluate(".//p:animEffect[.//p:spTgt/@spid]", parElement, XPathConstants.NODE);
    Element setElement = (Element) xpath.evaluate(".//p:set[.//p:spTgt/@spid]", parElement, XPathConstants.NODE);
    Element animElement = (Element) xpath.evaluate(".//p:anim[.//p:spTgt/@spid]", parElement, XPathConstants.NODE);
    Element animScaleElement = (Element) xpath.evaluate(".//p:animScale[.//p:spTgt/@spid]", parElement, XPathConstants.NODE);
    
    // Prioritize elements: set > anim > animEffect > animScale
    if (setElement != null) {
      targetElement = setElement;
      spidStr = (String) xpath.evaluate(".//p:spTgt/@spid", setElement, XPathConstants.STRING);
    } else if (animElement != null) {
      targetElement = animElement;
      spidStr = (String) xpath.evaluate(".//p:spTgt/@spid", animElement, XPathConstants.STRING);
    } else if (animEffectElement != null) {
      targetElement = animEffectElement;
      spidStr = (String) xpath.evaluate(".//p:spTgt/@spid", animEffectElement, XPathConstants.STRING);
    } else if (animScaleElement != null) {
      targetElement = animScaleElement;
      spidStr = (String) xpath.evaluate(".//p:spTgt/@spid", animScaleElement, XPathConstants.STRING);
    }
    
    if (spidStr.isEmpty() || targetElement == null) {
      return null; // No animation elements with valid spid found
    }
    
    // Determine animation type based on presetID, presetClass, and presetSubtype
    AnimationType animType = determineAnimationTypeFromPreset(presetID, presetClass, presetSubtype);
    String transition = "exit".equals(presetClass) ? "out" : "in";
    
    if (spidStr.isEmpty()) return null;
    int targetSpid = Integer.parseInt(spidStr);

    // Extract timing information. The animation's running time lives on
    // the cBhvr/cTn/@dur of the actual effect element (animEffect, anim,
    // animScale) -- not on the sibling p:set's cBhvr/cTn (which is the
    // 1ms visibility flip the oracle prepends to entrance animations).
    // Skip the p:set path or duration reads back as "1".
    String duration = (String) xpath.evaluate(
        ".//*[self::p:animEffect or self::p:anim or self::p:animScale or self::p:animMotion or self::p:animClr or self::p:animRot]"
        + "/p:cBhvr/p:cTn/@dur",
        parElement, XPathConstants.STRING);
    if (duration == null || duration.isEmpty()) {
        // Fall back to any cBhvr/cTn/@dur if the effect wrapper isn't one
        // of the recognised forms (e.g. composite custom effects).
        duration = (String) xpath.evaluate(
            ".//p:cBhvr/p:cTn/@dur", parElement, XPathConstants.STRING);
    }
    // Delay comes from the par-level click-trigger condition.
    String delay = (String) xpath.evaluate(
        "./p:stCondLst/p:cond/@delay", cTnElement, XPathConstants.STRING);

    // Extract click trigger by finding the parent click trigger node
    int clickTrigger = extractClickTriggerFromContext(parElement);
    
    // Extract animation group from cTn nodeType attribute (matches injection)
    String nodeType = cTnElement != null ? cTnElement.getAttribute("nodeType") : "";
    String animationGroup = convertNodeTypeToAnimationGroup(nodeType);
    
    // Use builder pattern for complete AnimationBinding construction
    AnimationBinding.Builder builder = AnimationBinding.builder()
        .target(targetSpid)
        .type(animType)
        .duration(duration != null && !duration.isEmpty() ? duration : "500")
        .delay(delay != null && !delay.isEmpty() ? delay : "0")
        .clickTrigger(clickTrigger)
        .animationGroup(animationGroup);

    // Extract timing node ID from cTn for use by remove/update commands
    if (cTnElement != null) {
        String ctnIdStr = cTnElement.getAttribute("id");
        if (ctnIdStr != null && !ctnIdStr.isEmpty()) {
            try {
                builder.timingNodeId(Integer.parseInt(ctnIdStr));
            } catch (NumberFormatException ignored) {}
        }
    }

    // Set the correct transition type based on presetClass attribute
    if ("exit".equals(presetClass)) {
        builder.exit();
    } else if ("emph".equals(presetClass) || "emphasis".equals(presetClass)) {
        builder.emphasis();
    } else {
        builder.entrance(); // Default for "entr" or unspecified
    }

    return builder.build();
  }

  /**
   * Extract click trigger number by walking up the XML hierarchy to find the parent click trigger node.
   * PowerPoint stores animations within par elements that have delay="indefinite" for click triggers.
   */
  private int extractClickTriggerFromContext(Element effectElement) {
    try {
      // Walk up the DOM tree to find the par element with delay="indefinite"
      Element current = effectElement;
      while (current != null) {
        if ("par".equals(current.getLocalName()) || "p:par".equals(current.getNodeName())) {
          // Check if this par has delay="indefinite" indicating a click trigger
          String delay = (String) xpath.evaluate(".//p:cTn/p:stCondLst/p:cond/@delay", current, XPathConstants.STRING);
          if ("indefinite".equals(delay)) {
            // Found a click trigger node, now determine which click number it is
            return determineClickNumber(current);
          }
        }
        current = (Element) current.getParentNode();
      }
      
      // Default to click 1 if no click trigger found
      return 1;
    } catch (Exception e) {
      logger.warn("Failed to extract click trigger: {}", e.getMessage());
      return 1; // Safe default
    }
  }

  /**
   * Determine the click number by counting preceding click trigger siblings.
   */
  private int determineClickNumber(Element clickTriggerPar) {
    try {
      // Find all par elements with delay="indefinite" in the same parent
      Element parent = (Element) clickTriggerPar.getParentNode();
      NodeList clickTriggers = (NodeList) xpath.evaluate(
          ".//p:par[p:cTn/p:stCondLst/p:cond/@delay='indefinite']", 
          parent, XPathConstants.NODESET);
      
      // Find the position of this trigger in the list
      for (int i = 0; i < clickTriggers.getLength(); i++) {
        if (clickTriggers.item(i).isSameNode(clickTriggerPar)) {
          return i + 1; // 1-based indexing
        }
      }
      
      return 1; // Default to click 1
    } catch (Exception e) {
      return 1; // Safe default
    }
  }

  /**
   * Convert nodeType attribute to animation group string (matches injection logic)
   */
  private String convertNodeTypeToAnimationGroup(String nodeType) {
    switch (nodeType) {
      case "clickEffect":
        return "on-click";
      case "withEffect": 
        return "with-previous";
      case "afterEffect":
        return "after-previous";
      default:
        return "on-click"; // Default
    }
  }

  /**
   * Determine the shape type from the geometry preset or element structure
   */
  private SlideShape.ShapeType determineShapeTypeFromGeometry(Element shapeElement) {
    try {
      // Check for placeholder type
      Element nvPr = (Element) xpath.evaluate(".//p:nvPr", shapeElement, XPathConstants.NODE);
      if (nvPr != null) {
        Element ph = (Element) xpath.evaluate(".//p:ph", nvPr, XPathConstants.NODE);
        if (ph != null) {
          return SlideShape.ShapeType.PLACEHOLDER;
        }
      }
      
      // Check for custom geometry
      Element custGeom = (Element) xpath.evaluate(".//a:custGeom", shapeElement, XPathConstants.NODE);
      if (custGeom != null) {
        return SlideShape.ShapeType.CUSTOM_GEOMETRY;
      }
      
      // Check for preset geometry
      Element prstGeom = (Element) xpath.evaluate(".//a:prstGeom", shapeElement, XPathConstants.NODE);
      if (prstGeom != null) {
        String preset = prstGeom.getAttribute("prst");
        if (preset != null && !preset.isEmpty()) {
          return SlideShape.ShapeType.fromOoxmlPreset(preset);
        }
      }
      
      // Default to rectangle for shapes without specific geometry
      return SlideShape.ShapeType.RECTANGLE;
      
    } catch (Exception e) {
      // If we can't determine the type, default to rectangle
      return SlideShape.ShapeType.RECTANGLE;
    }
  }

  /**
   * Resolve placeholder geometry from slide layout when shape has missing geometry
   */
  private ShapeGeometry resolveplaceholderGeometry(Element shapeElement) {
    return resolveplaceholderGeometry(shapeElement, -1); // Use -1 to indicate unknown slide number
  }
  
  /**
   * Resolve placeholder geometry from slide layout when shape has missing geometry with slide context
   */
  private ShapeGeometry resolveplaceholderGeometry(Element shapeElement, int slideNumber) {
    try {
      // Check if this is a placeholder shape
      Element nvPr = (Element) xpath.evaluate(".//p:nvPr", shapeElement, XPathConstants.NODE);
      if (nvPr == null) return null;
      
      Element ph = (Element) xpath.evaluate(".//p:ph", nvPr, XPathConstants.NODE);
      if (ph == null) return null;
      
      // Get placeholder type (title, ctrTitle, body, etc.)
      String placeholderType = ph.getAttribute("type");
      if (placeholderType.isEmpty()) {
        placeholderType = "body"; // Default placeholder type
      }
      
      // Get placeholder index if specified
      String placeholderIdx = ph.getAttribute("idx");
      
      // Try to get geometry from actual slide layout XML
      if (slideNumber > 0) {
        ShapeGeometry layoutGeometry = SlideLayoutParser.getPlaceholderGeometry(placeholderType, placeholderIdx, slideNumber);
        if (layoutGeometry != null) {
          return layoutGeometry;
        }
      }
      
      // Fallback to hardcoded positions if layout parsing fails
      switch (placeholderType) {
        case "title":
        case "ctrTitle":
          return new ShapeGeometry(997233, 300033, 10822233, 1143200);
        case "body":
        case "obj":
          return new ShapeGeometry(838200, 1825625, 7772400, 4525963);
        case "ftr":
          return new ShapeGeometry(838200, 6400000, 7772400, 500000);
        case "sldNum":
          return new ShapeGeometry(8000000, 6400000, 1000000, 500000);
        case "dt":
          return new ShapeGeometry(838200, 6400000, 2000000, 500000);
        default:
          return new ShapeGeometry(1828800, 1714500, 2286000, 1371600);
      }
      
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Nested parser for slide layout XML files
   * Handles parsing slideLayout*.xml files to extract placeholder geometry
   */
  public static class SlideLayoutParser {
    private static final Map<String, ShapeGeometry> layoutCache = new HashMap<>();
    private static String currentLayoutPath = null;
    private static final Map<String, String> masterLayoutIdToRIdMap = new HashMap<>();
    private static com.excudo.core.model.PPTXDocument pptxDocumentRef = null;

    /**
     * Initialize from PPTXDocument's in-memory parts.
     */
    public static void initialize(com.excudo.core.model.PPTXDocument pptxDocument) {
      pptxDocumentRef = pptxDocument;
      // A new document invalidates the previous deck's cached layout geometry
      // and parse guards. layoutCache is keyed by layout PATH (e.g.
      // "ppt/slideLayouts/slideLayout2.xml"), which collides across decks, so
      // without clearing it a second deck silently reads the first deck's
      // placeholder geometry. currentLayoutPath gates re-parsing within a
      // document and must reset too. This static state also coupled test
      // ordering -- clearing on initialize() makes each load self-contained.
      layoutCache.clear();
      currentLayoutPath = null;
      // In-memory mode: parse master from PPTXDocument
      try {
        org.w3c.dom.Document masterDoc = pptxDocument.getXmlPart("ppt/slideMasters/slideMaster1.xml");
        if (masterDoc != null) {
          masterLayoutIdToRIdMap.clear();
          javax.xml.xpath.XPath xpath = com.excudo.core.utils.XMLFactoryProvider.createXPath();
          org.w3c.dom.NodeList layoutIds = (org.w3c.dom.NodeList) xpath.evaluate(
              "//p:sldLayoutIdLst/p:sldLayoutId", masterDoc, javax.xml.xpath.XPathConstants.NODESET);
          for (int i = 0; i < layoutIds.getLength(); i++) {
            org.w3c.dom.Element el = (org.w3c.dom.Element) layoutIds.item(i);
            String id = el.getAttribute("id");
            String rId = el.getAttribute("r:id");
            if (!id.isEmpty() && !rId.isEmpty()) {
              masterLayoutIdToRIdMap.put(id, rId);
            }
          }
        }
      } catch (Exception e) {
        logger.warn("Failed to initialize SlideLayoutParser from PPTXDocument: {}", e.getMessage());
      }
    }
    
    /**
     * Get placeholder geometry from slide layout XML for a specific slide
     */
    public static ShapeGeometry getPlaceholderGeometry(String placeholderType, String placeholderIdx, int slideNumber) {
      try {
        // Resolve actual layout file for this slide
        String layoutPath = resolveSlideLayoutPath(slideNumber);
        if (layoutPath == null) {
          return null;
        }
        
        // Check cache first
        String cacheKey = layoutPath + ":" + placeholderType + ":" + (placeholderIdx.isEmpty() ? "default" : placeholderIdx);
        if (layoutCache.containsKey(cacheKey)) {
          return layoutCache.get(cacheKey);
        }
        
        // Parse layout file if not cached or if layout changed
        if (!layoutPath.equals(currentLayoutPath)) {
          parseLayoutFile(layoutPath);
          currentLayoutPath = layoutPath;
        }
        
        // Return cached result
        return layoutCache.get(cacheKey);
        
      } catch (Exception e) {
        logger.warn("Failed to parse slide layout: {}", e.getMessage());
        return null;
      }
    }
    
    /**
     * Get placeholder geometry (legacy method for backwards compatibility)
     */
    public static ShapeGeometry getPlaceholderGeometry(String placeholderType, String placeholderIdx) {
      // This method now returns null to indicate it needs the slide number
      // TODO: Update callers to use the new method with slide number
      return null;
    }
    
    /**
     * CRITICAL: Check if a specific layout has title placeholder enabled.
     * This determines whether SPID 2 should be used or skipped.
     * 
     * @param layoutPath Path to the layout XML file
     * @return true if layout has title placeholder checkbox enabled, false otherwise
     */
    public static boolean layoutHasTitlePlaceholder(String layoutPath) {
      try {
        // Try PPTXDocument first, fall back to file
        Document document = null;
        if (pptxDocumentRef != null) {
          // layoutPath may be absolute file path or virtual part name
          String partName = layoutPath;
          if (partName.contains("slideLayouts/")) {
            partName = "ppt/slideLayouts/" + partName.substring(partName.lastIndexOf("slideLayouts/") + "slideLayouts/".length());
          }
          document = pptxDocumentRef.getXmlPart(partName);
        }
        if (document == null) {
          logger.warn("Layout document not available for path: {}", layoutPath);
          return false;
        }
        XPath xpath = XMLFactoryProvider.createXPath();

        // Look for title placeholder in layout
        NodeList titlePlaceholders = (NodeList) xpath.evaluate(
            "//p:sp[.//p:ph[@type='title' or @type='ctrTitle']]", 
            document, XPathConstants.NODESET);
        
        boolean hasTitle = titlePlaceholders.getLength() > 0;

        return hasTitle;
        
      } catch (Exception e) {
        logger.warn("Failed to check title placeholder in layout {}: {}", layoutPath, e.getMessage());
        return false; // Default to no title on error
      }
    }
    
    /**
     * Check if a slide's layout has title placeholder by slide number.
     * This uses the existing layout resolution logic.
     * 
     * @param slideNumber The slide number to check
     * @return true if the slide's layout has title placeholder enabled
     */
    public static boolean slideLayoutHasTitlePlaceholder(int slideNumber) {
      try {
        String layoutPath = resolveSlideLayoutPath(slideNumber);
        return layoutPath != null && layoutHasTitlePlaceholder(layoutPath);
      } catch (Exception e) {
        logger.warn("Failed to check slide {} layout title placeholder: {}", slideNumber, e.getMessage());
        return false; // Default to no title on error
      }
    }
    
    /**
     * Parse a slide layout XML file and cache placeholder geometries
     */
    private static void parseLayoutFile(String layoutPath) {
      try {
        Document document = null;
        if (pptxDocumentRef != null) {
          String partName = layoutPath;
          if (partName.contains("slideLayouts/")) {
            partName = "ppt/slideLayouts/" + partName.substring(partName.lastIndexOf("slideLayouts/") + "slideLayouts/".length());
          }
          document = pptxDocumentRef.getXmlPart(partName);
        }
        if (document == null) {
          logger.warn("Layout document not available for path: {}", layoutPath);
          return;
        }
        XPath xpath = XMLFactoryProvider.createXPath();

        // Find all placeholder shapes in the layout
        NodeList placeholders = (NodeList) xpath.evaluate("//p:sp[.//p:ph]", document, XPathConstants.NODESET);
        
        for (int i = 0; i < placeholders.getLength(); i++) {
          Element placeholder = (Element) placeholders.item(i);
          
          // Extract placeholder type and index
          Element ph = (Element) xpath.evaluate(".//p:ph", placeholder, XPathConstants.NODE);
          String type = ph.getAttribute("type");
          String idx = ph.getAttribute("idx");
          
          if (type.isEmpty()) {
            type = "body"; // Default type
          }
          
          // Extract geometry
          String xStr = (String) xpath.evaluate(".//a:xfrm/a:off/@x", placeholder, XPathConstants.STRING);
          String yStr = (String) xpath.evaluate(".//a:xfrm/a:off/@y", placeholder, XPathConstants.STRING);
          String cxStr = (String) xpath.evaluate(".//a:xfrm/a:ext/@cx", placeholder, XPathConstants.STRING);
          String cyStr = (String) xpath.evaluate(".//a:xfrm/a:ext/@cy", placeholder, XPathConstants.STRING);
          
          if (!xStr.isEmpty() && !yStr.isEmpty() && !cxStr.isEmpty() && !cyStr.isEmpty()) {
            long x = Long.parseLong(xStr);
            long y = Long.parseLong(yStr);
            long cx = Long.parseLong(cxStr);
            long cy = Long.parseLong(cyStr);
            
            ShapeGeometry geometry = new ShapeGeometry(x, y, cx, cy);

            // Cache with type and index
            String cacheKey = layoutPath + ":" + type + ":" + (idx.isEmpty() ? "default" : idx);
            layoutCache.put(cacheKey, geometry);
          }
        }
        
      } catch (Exception e) {
        logger.warn("Failed to parse layout file {}: {}", layoutPath, e.getMessage());
      }
    }
    
    
    /**
     * Resolve the layout ID (e.g., "slideLayout2") for a specific slide
     * by reading its .rels file.
     *
     * @param slideNumber The 1-based slide number
     * @return The layout ID stem, or null if unresolvable
     */
    public static String resolveLayoutId(int slideNumber) {
      String layoutPath = resolveSlideLayoutPath(slideNumber);
      if (layoutPath == null) return null;
      String filename = layoutPath.contains("/")
          ? layoutPath.substring(layoutPath.lastIndexOf('/') + 1)
          : layoutPath;
      return filename.endsWith(".xml") ? filename.substring(0, filename.length() - 4) : filename;
    }

    /**
     * Resolve the actual layout file path for a specific slide
     */
    private static String resolveSlideLayoutPath(int slideNumber) {
      try {
        if (pptxDocumentRef == null) {
          return null;
        }

        String relsPartName = "ppt/slides/_rels/slide" + slideNumber + ".xml.rels";
        Document document = pptxDocumentRef.getXmlPart(relsPartName);

        if (document == null) {
          logger.warn("Slide .rels not available for slide {}", slideNumber);
          return null;
        }

        XPath xpath = XMLFactoryProvider.createXPathWithoutNamespace();
        NodeList relationships = (NodeList) xpath.evaluate(
            "//*[local-name()='Relationship'][@Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout']",
            document, XPathConstants.NODESET);

        if (relationships.getLength() > 0) {
          Element layoutRel = (Element) relationships.item(0);
          String target = layoutRel.getAttribute("Target");

          if (!target.isEmpty()) {
            // Return a normalized virtual path for in-memory resolution
            // Target is typically "../slideLayouts/slideLayout1.xml"
            if (target.startsWith("../")) {
              return "ppt/" + target.substring(3);
            }
            return target;
          }
        }

        return null;

      } catch (Exception e) {
        logger.warn("Failed to resolve slide layout path for slide {}: {}", slideNumber, e.getMessage());
        return null;
      }
    }
    
  }

  // PowerPointNamespaceContext moved to XMLConstants.PowerPointNamespaceContext
  // Use XMLFactoryProvider.createXPath() to get XPath with namespace context configured

  /**
   * Parse notes slide XML file to extract text content.
   * This method respects SRP by keeping parsing logic in the parser, not the writer.
   * 
   * @param notesFile The notes slide XML file to parse
   * @return The text content of the notes slide (empty string if no content)
   * @throws XMLParsingException if parsing fails
   */
  public String parseNotesSlide(File notesFile) throws XMLParsingException {
    if (!notesFile.exists()) {
      return "";
    }
    
    try {
      Document doc = documentBuilder.parse(notesFile);
      
      // Find all text elements in notes body
      NodeList textNodes = (NodeList) xpath.evaluate(
          "//p:sp[p:nvSpPr/p:nvPr/p:ph[@type='body']]//a:t",
          doc,
          XPathConstants.NODESET
      );
      
      StringBuilder notesText = new StringBuilder();
      for (int i = 0; i < textNodes.getLength(); i++) {
        notesText.append(textNodes.item(i).getTextContent());
      }
      
      return notesText.toString();
      
    } catch (Exception e) {
      throw new XMLParsingException("Failed to parse notes slide: " + notesFile.getName(), e);
    }
  }

  /**
   * Determine animation type from PowerPoint presetID.
   * Maps PowerPoint's preset IDs to our AnimationType enum values.
   * Defaults to entrance context when presetClass is unknown.
   */
  static AnimationType determineAnimationTypeFromPreset(String presetID) {
    return determineAnimationTypeFromPreset(presetID, "entr", "");
  }

  static AnimationType determineAnimationTypeFromPreset(String presetID, String presetClass) {
    return determineAnimationTypeFromPreset(presetID, presetClass, "");
  }

  /**
   * Determine animation type from PowerPoint presetID with presetClass and presetSubtype context.
   * PresetID is scoped to presetClass -- the same ID can mean different things
   * in entrance vs emphasis contexts. PresetSubtype disambiguates directional
   * variants (e.g. fly-in from bottom vs left).
   *
   * @param presetID the presetID attribute from the cTn element
   * @param presetClass the presetClass attribute ("entr", "exit", "emph", "path")
   * @param presetSubtype the presetSubtype attribute for directional resolution
   * @return the matching AnimationType, or FADE as safe default
   */
  static AnimationType determineAnimationTypeFromPreset(String presetID, String presetClass, String presetSubtype) {
    if (presetID == null || presetID.trim().isEmpty()) {
      return AnimationType.FADE; // Safe default
    }

    int subtype = 0;
    if (presetSubtype != null && !presetSubtype.trim().isEmpty()) {
      try {
        subtype = Integer.parseInt(presetSubtype.trim());
      } catch (NumberFormatException e) {
        // Leave as 0
      }
    }

    try {
      int id = Integer.parseInt(presetID.trim());
      String cls = (presetClass != null) ? presetClass.trim().toLowerCase() : "entr";

      // Emphasis animations have their own ID space
      if ("emph".equals(cls)) {
        switch (id) {
          case 6:  return AnimationType.GROW_SHRINK;
          case 8:  return AnimationType.SPIN;
          case 9:  return AnimationType.TRANSPARENCY;
          case 24: return AnimationType.DARKEN;
          case 25: return AnimationType.DESATURATE;
          case 26: return AnimationType.PULSE;
          case 27: return AnimationType.COLOR_PULSE;
          case 30: return AnimationType.LIGHTEN;
          case 32: return AnimationType.TEETER;
          default: return AnimationType.FADE;
        }
      }

      // Entrance and exit animations share the same ID space
      switch (id) {
        case 1:  return AnimationType.APPEAR;
        case 2:  return resolveFlyDirection(subtype);
        case 3:  return AnimationType.BLINDS_HORIZONTAL;
        case 4:  return AnimationType.BOX_IN;
        case 8:  return AnimationType.DIAMOND_IN;
        case 9:  return AnimationType.CHECKERBOARD;
        case 10: return AnimationType.FADE;
        case 12: return AnimationType.DISSOLVE;
        case 14: return AnimationType.RANDOM_BARS_HORIZONTAL;
        case 16: return AnimationType.SPLIT_HORIZONTAL;
        case 21: return AnimationType.WHEEL_4;
        case 22: return AnimationType.WIPE_LEFT; // Direction resolved by factory parser
        case 31: return AnimationType.GROW_TURN;
        case 45: return AnimationType.SWIVEL;
        case 53: return AnimationType.ZOOM;
        default:
          return AnimationType.FADE; // Unknown preset -- safe default
      }
    } catch (NumberFormatException e) {
      return AnimationType.FADE; // Safe default
    }
  }

  /**
   * Resolve fly-in direction from presetSubtype.
   * PowerPoint subtype mapping (from ECMA-376 oracle):
   *   1=top, 2=right, 3=top-right, 4=bottom,
   *   6=bottom-right, 8=left, 9=top-left, 12=bottom-left
   *
   * Only 4 cardinal directions have dedicated AnimationType values;
   * diagonal subtypes fall back to the nearest cardinal direction.
   */
  private static AnimationType resolveFlyDirection(int subtype) {
    switch (subtype) {
      case 1:             // top
      case 3:             // top-right (nearest: top)
      case 9:             // top-left  (nearest: top)
        return AnimationType.FLY_IN_TOP;
      case 2:             // right
      case 6:             // bottom-right (nearest: right)
        return AnimationType.FLY_IN_RIGHT;
      case 4:             // bottom
      case 0:             // PowerPoint default for fly = bottom
      case 12:            // bottom-left (nearest: bottom)
        return AnimationType.FLY_IN_BOTTOM;
      case 8:             // left
        return AnimationType.FLY_IN_LEFT;
      default:
        return AnimationType.FLY_IN_BOTTOM; // Safest default -- matches PowerPoint default
    }
  }
}
