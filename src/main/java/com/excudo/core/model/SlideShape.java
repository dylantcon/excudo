package com.excudo.core.model;

import org.w3c.dom.Element;

/**
 * Represents a shape or object on a PowerPoint slide
 */
public class SlideShape {
  public enum ShapeType {
    // Meta categories
    PLACEHOLDER, PICTURE, GROUP, CONNECTION, CUSTOM_GEOMETRY,
    
    // Basic Shapes
    RECTANGLE("rect"), ELLIPSE("ellipse"), TRIANGLE("triangle"), DIAMOND("diamond"),
    PARALLELOGRAM("parallelogram"), TRAPEZOID("trapezoid"), PENTAGON("pentagon"), 
    HEXAGON("hexagon"), HEPTAGON("heptagon"), OCTAGON("octagon"), DECAGON("decagon"), 
    DODECAGON("dodecagon"),
    
    // Rectangle Variants
    ROUNDED_RECTANGLE("roundRect"), SNIP_SINGLE_CORNER_RECTANGLE("snip1Rect"),
    SNIP_SAME_SIDE_CORNER_RECTANGLE("snip2SameRect"), SNIP_DIAGONAL_CORNER_RECTANGLE("snip2DiagRect"),
    ROUND_SINGLE_CORNER_RECTANGLE("round1Rect"), ROUND_SAME_SIDE_CORNER_RECTANGLE("round2SameRect"),
    ROUND_DIAGONAL_CORNER_RECTANGLE("round2DiagRect"), SNIP_ROUND_RECTANGLE("snipRoundRect"),
    
    // Arrows - Basic
    RIGHT_ARROW("rightArrow"), LEFT_ARROW("leftArrow"), UP_ARROW("upArrow"), DOWN_ARROW("downArrow"),
    LEFT_RIGHT_ARROW("leftRightArrow"), UP_DOWN_ARROW("upDownArrow"), QUAD_ARROW("quadArrow"),
    LEFT_RIGHT_UP_ARROW("leftRightUpArrow"), BENT_ARROW("bentArrow"), UTURN_ARROW("uturnArrow"),
    LEFT_UP_ARROW("leftUpArrow"), BENT_UP_ARROW("bentUpArrow"), CURVED_RIGHT_ARROW("curvedRightArrow"),
    CURVED_LEFT_ARROW("curvedLeftArrow"), CURVED_UP_ARROW("curvedUpArrow"), CURVED_DOWN_ARROW("curvedDownArrow"),
    STRIPED_RIGHT_ARROW("stripedRightArrow"), NOTCHED_RIGHT_ARROW("notchedRightArrow"),
    CIRCULAR_ARROW("circularArrow"),
    
    // Arrow Callouts
    RIGHT_ARROW_CALLOUT("rightArrowCallout"), LEFT_ARROW_CALLOUT("leftArrowCallout"),
    UP_ARROW_CALLOUT("upArrowCallout"), DOWN_ARROW_CALLOUT("downArrowCallout"),
    LEFT_RIGHT_ARROW_CALLOUT("leftRightArrowCallout"), QUAD_ARROW_CALLOUT("quadArrowCallout"),
    
    // Flowchart Shapes
    FLOWCHART_PROCESS("flowChartProcess"), FLOWCHART_DECISION("flowChartDecision"),
    FLOWCHART_INPUT_OUTPUT("flowChartInputOutput"), FLOWCHART_PREDEFINED_PROCESS("flowChartPredefinedProcess"),
    FLOWCHART_INTERNAL_STORAGE("flowChartInternalStorage"), FLOWCHART_DOCUMENT("flowChartDocument"),
    FLOWCHART_MULTIDOCUMENT("flowChartMultidocument"), FLOWCHART_TERMINATOR("flowChartTerminator"),
    FLOWCHART_PREPARATION("flowChartPreparation"), FLOWCHART_MANUAL_INPUT("flowChartManualInput"),
    FLOWCHART_MANUAL_OPERATION("flowChartManualOperation"), FLOWCHART_CONNECTOR("flowChartConnector"),
    FLOWCHART_PUNCHED_CARD("flowChartPunchedCard"), FLOWCHART_PUNCHED_TAPE("flowChartPunchedTape"),
    FLOWCHART_SUMMING_JUNCTION("flowChartSummingJunction"), FLOWCHART_OR("flowChartOr"),
    FLOWCHART_COLLATE("flowChartCollate"), FLOWCHART_SORT("flowChartSort"),
    FLOWCHART_EXTRACT("flowChartExtract"), FLOWCHART_MERGE("flowChartMerge"),
    FLOWCHART_OFFLINE_STORAGE("flowChartOfflineStorage"), FLOWCHART_ONLINE_STORAGE("flowChartOnlineStorage"),
    FLOWCHART_MAGNETIC_TAPE("flowChartMagneticTape"), FLOWCHART_MAGNETIC_DISK("flowChartMagneticDisk"),
    FLOWCHART_MAGNETIC_DRUM("flowChartMagneticDrum"), FLOWCHART_DISPLAY("flowChartDisplay"),
    FLOWCHART_DELAY("flowChartDelay"), FLOWCHART_ALTERNATE_PROCESS("flowChartAlternateProcess"),
    FLOWCHART_OFFPAGE_CONNECTOR("flowChartOffpageConnector"),
    
    // Stars and Explosions  
    STAR_4_POINTS("star4"), STAR_5_POINTS("star5"), STAR_6_POINTS("star6"), STAR_7_POINTS("star7"),
    STAR_8_POINTS("star8"), STAR_10_POINTS("star10"), STAR_12_POINTS("star12"), STAR_16_POINTS("star16"),
    STAR_24_POINTS("star24"), STAR_32_POINTS("star32"), EXPLOSION_8_POINTS("explosion1"), 
    EXPLOSION_14_POINTS("explosion2"),
    
    // Banners and Ribbons
    RIBBON("ribbon"), RIBBON_2("ribbon2"), ELLIPSE_RIBBON("ellipseRibbon"), 
    ELLIPSE_RIBBON_2("ellipseRibbon2"), VERTICAL_SCROLL("verticalScroll"), 
    HORIZONTAL_SCROLL("horizontalScroll"),
    
    // Callouts
    RECTANGULAR_CALLOUT("borderCallout1"), ROUNDED_RECTANGULAR_CALLOUT("borderCallout2"),
    OVAL_CALLOUT("borderCallout3"), LINE_CALLOUT_1("callout1"), LINE_CALLOUT_2("callout2"),
    LINE_CALLOUT_3("callout3"), ACCENT_CALLOUT_1("accentCallout1"), ACCENT_CALLOUT_2("accentCallout2"),
    ACCENT_CALLOUT_3("accentCallout3"),
    ACCENT_BORDER_CALLOUT_1("accentBorderCallout1"), ACCENT_BORDER_CALLOUT_2("accentBorderCallout2"),
    ACCENT_BORDER_CALLOUT_3("accentBorderCallout3"), WEDGE_RECT_CALLOUT("wedgeRectCallout"),
    WEDGE_ROUND_RECT_CALLOUT("wedgeRoundRectCallout"), WEDGE_ELLIPSE_CALLOUT("wedgeEllipseCallout"),
    CLOUD_CALLOUT("cloudCallout"),
    
    // Action Buttons (Basic set)
    ACTION_BUTTON_BLANK("actionButtonBlank"), ACTION_BUTTON_HOME("actionButtonHome"),
    ACTION_BUTTON_HELP("actionButtonHelp"), ACTION_BUTTON_INFORMATION("actionButtonInformation"),
    ACTION_BUTTON_BACK_OR_PREVIOUS("actionButtonBackPrevious"), ACTION_BUTTON_FORWARD_OR_NEXT("actionButtonForwardNext"),
    ACTION_BUTTON_BEGINNING("actionButtonBeginning"), ACTION_BUTTON_END("actionButtonEnd"),
    ACTION_BUTTON_RETURN("actionButtonReturn"), ACTION_BUTTON_DOCUMENT("actionButtonDocument"),
    ACTION_BUTTON_SOUND("actionButtonSound"), ACTION_BUTTON_MOVIE("actionButtonMovie"),
    
    // Special Shapes
    LINE("line"), ARC("arc"), PIE("pie"), CHORD("chord"), FRAME("frame"), HALF_FRAME("halfFrame"),
    L_SHAPE("corner"), DIAGONAL_STRIPE("diagStripe"), CROSS("plus"), PLAQUE("plaque"),
    DONUT("donut"), BLOCK_ARC("blockArc"), FOLDED_CORNER("foldedCorner"), BEVEL("bevel"),
    
    // Math Symbols
    PLUS("mathPlus"), MINUS("mathMinus"), MULTIPLY("mathMultiply"), DIVIDE("mathDivide"),
    EQUAL("mathEqual"), NOT_EQUAL("mathNotEqual"),
    
    // Symbols
    HEART("heart"), LIGHTNING_BOLT("lightningBolt"), SUN("sun"), MOON("moon"), CLOUD("cloud"),
    SMILEY_FACE("smileyFace"), IRREGULAR_SEAL_1("irregularSeal1"), IRREGULAR_SEAL_2("irregularSeal2"),
    NO_SMOKING("noSmoking"), RIGHT_TRIANGLE("rtTriangle"), 
    TEARDROP("teardrop"), HOME_PLATE("homePlate"), CHEVRON("chevron"), CAN("can"), CUBE("cube"),
    
    // Connectors  
    STRAIGHT_CONNECTOR("straightConnector1"), ELBOW_CONNECTOR("bentConnector3"),
    CURVED_CONNECTOR("curvedConnector3"),
    
    // Brackets and Braces
    LEFT_BRACKET("leftBracket"), RIGHT_BRACKET("rightBracket"), LEFT_BRACE("leftBrace"),
    RIGHT_BRACE("rightBrace"), BRACKET_PAIR("bracketPair"), BRACE_PAIR("bracePair"),
    
    // Wave
    WAVE("wave"), DOUBLE_WAVE("doubleWave");
    
    private final String ooxmlPreset;
    
    ShapeType() {
      this.ooxmlPreset = null;
    }
    
    ShapeType(String ooxmlPreset) {
      this.ooxmlPreset = ooxmlPreset;
    }
    
    public String getOoxmlPreset() {
      return ooxmlPreset;
    }
    
    public boolean hasOoxmlPreset() {
      return ooxmlPreset != null;
    }
    
    /**
     * Get ShapeType from OOXML preset string
     */
    public static ShapeType fromOoxmlPreset(String preset) {
      if (preset == null) return CUSTOM_GEOMETRY;
      
      for (ShapeType type : values()) {
        if (preset.equals(type.ooxmlPreset)) {
          return type;
        }
      }
      return CUSTOM_GEOMETRY;
    }
    
    /**
     * Determine if this shape type supports text content
     */
    public boolean supportsText() {
      // Most shapes support text except connectors and some special shapes
      return this != CONNECTION && 
             !name().contains("CONNECTOR") && 
             this != LINE &&
             this != ARC;
    }
    
    /**
     * Determine if this shape type requires SAT collision detection
     */
    public boolean requiresSATCollision() {
      // Complex shapes that aren't simple rectangles/ellipses
      return this == CUSTOM_GEOMETRY ||
             name().contains("STAR") ||
             name().contains("ARROW") ||
             name().contains("TRIANGLE") ||
             name().contains("LIGHTNING") ||
             name().contains("HEART") ||
             name().contains("WAVE") ||
             name().contains("SEAL");
    }
  }

  private final int spid;
  private final String name;
  private final ShapeType type;
  private final String textContent;
  private final ShapeGeometry geometry;
  private final Element xmlElement;
  private final ParagraphMetadata paragraphMetadata;
  // OOXML cNvSpPr/@txBox="1" marker. Set by the parser when the
  // attribute is present on the shape's non-visual properties; remains
  // false otherwise. Distinct from the structural ShapeType (a text box
  // is structurally still a RECTANGLE preset); this field carries the
  // authorial intent the spec encodes via that attribute.
  private final boolean isTextBox;

  public SlideShape(int spid, String name, ShapeType type, String textContent,
      ShapeGeometry geometry, Element xmlElement) {
    this(spid, name, type, textContent, geometry, xmlElement, null, false);
  }

  public SlideShape(int spid, String name, ShapeType type, String textContent,
      ShapeGeometry geometry, Element xmlElement, ParagraphMetadata paragraphMetadata) {
    this(spid, name, type, textContent, geometry, xmlElement, paragraphMetadata, false);
  }

  public SlideShape(int spid, String name, ShapeType type, String textContent,
      ShapeGeometry geometry, Element xmlElement, ParagraphMetadata paragraphMetadata,
      boolean isTextBox) {
    this.spid = spid;
    this.name = name;
    this.type = type;
    this.textContent = textContent;
    this.geometry = geometry;
    this.xmlElement = xmlElement;
    this.paragraphMetadata = paragraphMetadata;
    this.isTextBox = isTextBox;
  }

  // Getters
  public int getSpid() { return spid; }
  public String getName() { return name; }
  public ShapeType getType() { return type; }
  public String getTextContent() { return textContent; }
  public String getText() { return textContent; }  // Alias for Commands
  /** True iff the parsed cNvSpPr carried the OOXML txBox="1" marker. */
  public boolean isTextBox() { return isTextBox; }
  public ShapeGeometry getGeometry() { return geometry; }
  public Element getXmlElement() { return xmlElement; }
  public ParagraphMetadata getParagraphMetadata() { return paragraphMetadata; }

  public boolean hasText() { return textContent != null && !textContent.trim().isEmpty(); }
  
  public boolean hasParagraphMetadata() { return paragraphMetadata != null; }
  
  public boolean hasBulletPoints() { 
    return hasParagraphMetadata() && !paragraphMetadata.getBulletPointsOnly().isEmpty(); 
  }
  
  public int getBulletPointCount() {
    return hasParagraphMetadata() ? paragraphMetadata.getBulletPointsOnly().size() : 0;
  }
  
  public int getParagraphCount() {
    return hasParagraphMetadata() ? paragraphMetadata.getParagraphCount() : 0;
  }
  
  /**
   * Get bullet point content by bullet index (not paragraph index)
   */
  public String getBulletPointContent(int bulletIndex) {
    if (!hasParagraphMetadata()) {
      throw new IllegalStateException("Shape has no paragraph metadata");
    }
    var bulletPoints = paragraphMetadata.getBulletPointsOnly();
    if (bulletIndex < 0 || bulletIndex >= bulletPoints.size()) {
      throw new IndexOutOfBoundsException("Bullet index out of range: " + bulletIndex);
    }
    return bulletPoints.get(bulletIndex);
  }
  
  /**
   * Get paragraph content by paragraph index (includes both bullets and non-bullets)
   */
  public String getParagraphContent(int paragraphIndex) {
    if (!hasParagraphMetadata()) {
      throw new IllegalStateException("Shape has no paragraph metadata");
    }
    return paragraphMetadata.getParagraphContent(paragraphIndex);
  }

  @Override
  public String toString() {
    if (hasParagraphMetadata()) {
      return String.format("SlideShape{spid=%d, name='%s', type=%s, hasText=%s, paragraphs=%d, bullets=%d}",
          spid, name, type, hasText(), getParagraphCount(), getBulletPointCount());
    } else {
      return String.format("SlideShape{spid=%d, name='%s', type=%s, hasText=%s}",
          spid, name, type, hasText());
    }
  }
}
