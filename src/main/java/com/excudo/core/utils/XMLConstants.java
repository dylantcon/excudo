package com.excudo.core.utils;

import javax.xml.namespace.NamespaceContext;
import org.w3c.dom.Element;
import java.util.Iterator;
import java.util.Map;

/**
 * Centralized XML constants and utilities for PowerPoint OOXML processing
 * Eliminates magic strings and provides consistent namespace management
 */
public final class XMLConstants {

  /**
   * Nested, semantically-grouped aliases for the constants below.
   * Prefer {@code XMLConstants.Namespaces.PML} over the flat
   * {@code PRESENTATION_NS} in new code: the grouping makes it clear
   * which symbols belong to which OOXML schema and lets a rename of
   * any one symbol be a single-file edit. The flat constants are kept
   * for backward compatibility with the large existing surface that
   * references them directly.
   */
  public static final class Namespaces {
    private Namespaces() {}
    /** PresentationML -- {@code p:} prefix. */
    public static final String PML = "http://schemas.openxmlformats.org/presentationml/2006/main";
    /** DrawingML -- {@code a:} prefix. */
    public static final String DML = "http://schemas.openxmlformats.org/drawingml/2006/main";
    /** OfficeDocument relationships -- {@code r:} prefix. */
    public static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    /** Package relationships (inside .rels files). */
    public static final String PACKAGE_REL = "http://schemas.openxmlformats.org/package/2006/relationships";
  }

  public static final class RelTypes {
    private RelTypes() {}
    public static final String SLIDE_MASTER =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster";
    public static final String SLIDE_LAYOUT =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout";
    public static final String THEME =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme";
  }

  /** Element local-names (without the p:/a: prefix), grouped by area. */
  public static final class Tags {
    private Tags() {}
    public static final class Timing {
      private Timing() {}
      public static final String C_TN = "cTn";
      public static final String TRANSITION = "transition";
    }
    public static final class Shape {
      private Shape() {}
      public static final String SP = "sp";
      public static final String GRP_SP = "grpSp";
      public static final String PIC = "pic";
      public static final String C_NV_PR = "cNvPr";
      public static final String C_NV_SP_PR = "cNvSpPr";
      public static final String TX_BODY = "txBody";
      public static final String SP_PR = "spPr";
      public static final String STYLE = "style";
    }
    public static final class Drawing {
      private Drawing() {}
      public static final String OFF = "off";
      public static final String EXT = "ext";
      public static final String XFRM = "xfrm";
      public static final String SOLID_FILL = "solidFill";
      public static final String NO_FILL = "noFill";
      public static final String LN = "ln";
      public static final String SCHEME_CLR = "schemeClr";
      public static final String SRGB_CLR = "srgbClr";
    }
  }

  /** Attribute names. */
  public static final class Attrs {
    private Attrs() {}
    public static final class Timing {
      private Timing() {}
      public static final String PRESET_ID = "presetID";
      public static final String PRESET_CLASS = "presetClass";
      public static final String ID = "id";
    }
    public static final class Shape {
      private Shape() {}
      public static final String NAME = "name";
      public static final String TX_BOX = "txBox";
    }
    public static final class Rel {
      private Rel() {}
      public static final String TYPE = "Type";
      public static final String TARGET = "Target";
      public static final String ID = "Id";
    }
  }

  // PowerPoint XML Namespaces
  public static final String PRESENTATION_NS = "http://schemas.openxmlformats.org/presentationml/2006/main";
  public static final String DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";
  public static final String RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
  public static final String PACKAGE_RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/package/2006/relationships";

  // Namespace declaration attribute names (for setAttributeNS calls)
  public static final String XMLNS_ATTRIBUTE = "http://www.w3.org/2000/xmlns/";
  public static final String XMLNS_PREFIX_PRESENTATION = "xmlns:p";
  public static final String XMLNS_PREFIX_DRAWING = "xmlns:a";
  public static final String XMLNS_PREFIX_RELATIONSHIPS = "xmlns:r";
  public static final String XMLNS_PREFIX_PACKAGE_RELATIONSHIPS = "xmlns:rel";

  // Namespace prefixes for programmatic checks
  public static final String PRESENTATION_PREFIX = "p";
  public static final String DRAWING_PREFIX = "a";
  public static final String RELATIONSHIPS_PREFIX = "r";
  public static final String PACKAGE_RELATIONSHIPS_PREFIX = "rel";

  // XPath expressions for shape extraction. Select only direct children
  // of spTree -- SlideXMLParser.registerGroupChildren recurses into
  // groups, so including descendant-or-self ("//" under spTree) would
  // double-walk every grouped shape and register each child twice in the
  // flat ShapeRegistry. p:graphicFrame is selected for its a:tbl payload
  // (A6); non-table graphicFrames (charts, SmartArt, OLE) are dropped by
  // the parser pending their own phases.
  public static final String XPATH_ALL_SHAPES_AND_PICTURES = "//p:spTree/p:sp | //p:spTree/p:pic | //p:spTree/p:grpSp | //p:spTree/p:cxnSp | //p:spTree/p:graphicFrame";
  public static final String XPATH_SHAPE_ID_ATTRIBUTE = ".//p:cNvPr/@id";
  public static final String XPATH_SHAPE_NAME_ATTRIBUTE = ".//p:cNvPr/@name";
  public static final String XPATH_SHAPE_TEXT_CONTENT = ".//a:t/text()";
  
  // XPath expressions for finding specific shapes by SPID
  public static final String XPATH_SHAPE_BY_SPID_TEMPLATE = "//p:sp[p:nvSpPr/p:cNvPr/@id='%d']";
  public static final String XPATH_PICTURE_BY_SPID_TEMPLATE = "//p:pic[p:nvPicPr/p:cNvPr/@id='%d']";
  public static final String XPATH_SHAPE_OR_PICTURE_BY_SPID_TEMPLATE = "//p:sp[p:nvSpPr/p:cNvPr/@id='%d'] | //p:pic[p:nvPicPr/p:cNvPr/@id='%d']";
  
  // XPath expressions for picture relationships
  public static final String XPATH_PICTURE_BLIP_RELATIONSHIP_ID = ".//a:blip/@r:embed";

  // XPath expressions for shape geometry
  public static final String XPATH_SHAPE_X_POSITION = ".//a:xfrm/a:off/@x";
  public static final String XPATH_SHAPE_Y_POSITION = ".//a:xfrm/a:off/@y";
  public static final String XPATH_SHAPE_WIDTH = ".//a:xfrm/a:ext/@cx";
  public static final String XPATH_SHAPE_HEIGHT = ".//a:xfrm/a:ext/@cy";

  // XPath expressions for timing and animation structure
  public static final String XPATH_TIMING_ROOT_ELEMENT = "//p:timing";
  public static final String XPATH_MAIN_ANIMATION_SEQUENCE = ".//p:seq[@concurrent='1']";
  public static final String XPATH_TIMING_CHILD_NODES = "./p:childTnLst/p:par | ./p:childTnLst/p:seq";
  public static final String XPATH_TIMING_CTN_ELEMENT = "./p:cTn";
  public static final String XPATH_TIMING_DELAY_ATTRIBUTE = "./p:stCondLst/p:cond/@delay";
  public static final String XPATH_TIMING_CTN_CHILDREN = "./p:childTnLst/p:par | ./p:childTnLst/p:seq";

  // XPath expressions for animation bindings
  public static final String XPATH_ALL_ANIMATION_EFFECTS = "//p:animEffect | //p:set";
  public static final String XPATH_ANIMATION_TARGET_SHAPE_ID = ".//p:spTgt/@spid";
  public static final String XPATH_ANIMATION_DURATION = "./p:cBhvr/p:cTn/@dur";
  public static final String XPATH_ANIMATION_DELAY = "./p:cBhvr/p:cTn/p:stCondLst/p:cond/@delay";

  /**
   * Animation system XPath expressions and constants - centralized for consistency
   * Eliminates magic strings and provides single source of truth for animation patterns
   */
  public static final class XPath {
    // Common animation detection expressions
    public static final String VISIBILITY_SET = ".//p:set[.//p:attrName[text()='style.visibility']]";
    public static final String VISIBILITY_SET_BROKEN = ".//p:set[contains(@attrNameLst, 'style.visibility')]";
    
    // Animation effect filters
    public static final String FADE_EFFECT = ".//p:animEffect[@filter='fade']";
    public static final String WIPE_EFFECT = ".//p:animEffect[contains(@filter, 'wipe')]";
    public static final String WIPE_EFFECT_WITH_FILTER = ".//p:animEffect[contains(@filter, 'wipe')]";
    
    // Animation types by calculation attribute
    public static final String POSITION_X_ANIM = ".//p:anim[@calc='ppt_x']";
    public static final String POSITION_Y_ANIM = ".//p:anim[@calc='ppt_y']";
    public static final String WIDTH_ANIM = ".//p:anim[@calc='ppt_w']";
    public static final String HEIGHT_ANIM = ".//p:anim[@calc='ppt_h']";
    public static final String COORDINATE_ANIM = ".//p:anim[@calc='ppt_w' or @calc='ppt_h']";
    public static final String POSITION_ANIM = ".//p:anim[@calc='ppt_x' or @calc='ppt_y']";
    
    // Animation value extraction
    public static final String WIDTH_VALUE = ".//p:anim[@calc='ppt_w']//p:strVal";
    public static final String HEIGHT_VALUE = ".//p:anim[@calc='ppt_h']//p:strVal";
    public static final String POSITION_X_VALUE = ".//p:anim[@calc='ppt_x']//p:strVal";
    public static final String POSITION_Y_VALUE = ".//p:anim[@calc='ppt_y']//p:strVal";
    
    // Animation behavior elements
    public static final String ANIM_EFFECT_ELEMENT = ".//p:animEffect";
    public static final String ANIM_ELEMENT = ".//p:anim";
    public static final String SET_ELEMENT = ".//p:set";
    
    // Animation timing and transitions
    public static final String ANIMATION_TRANSITION = ".//p:animEffect/@transition";
    public static final String ANIM_TRANSITION = ".//p:anim/@transition";
    public static final String SET_TRANSITION = ".//p:set/@transition";
    
    // Animation target and behavior
    public static final String TARGET_SHAPE_ID = ".//p:spTgt/@spid";
    public static final String ANIMATION_DURATION = ".//p:cTn/@dur";
    public static final String ANIMATION_DELAY = ".//p:stCondLst/p:cond/@delay";
    public static final String PRESET_ID = ".//p:cTn/@presetID";
    
    private XPath() {
      // Utility class - no instantiation
    }
  }

  /**
   * Animation presetID constants from native PowerPoint OOXML
   */
  public static final class PresetID {
    public static final int APPEAR = 1;
    public static final int FADE = 10;
    public static final int WIPE = 22;
    public static final int ZOOM = 53;
    public static final int FLY_IN = 2;
    
    private PresetID() {
      // Utility class - no instantiation
    }
  }

  /**
   * Animation filter constants for OOXML generation
   */
  public static final class AnimationFilter {
    public static final String FADE = "fade";
    public static final String WIPE_LEFT = "wipe(left)";
    public static final String WIPE_RIGHT = "wipe(right)";
    public static final String WIPE_UP = "wipe(up)";
    public static final String WIPE_DOWN = "wipe(down)";
    public static final String ZOOM_IN = "fade";  // Zoom uses fade filter with coordinate animations
    public static final String ZOOM_OUT = "fade";
    
    private AnimationFilter() {
      // Utility class - no instantiation
    }
  }

  /**
   * Animation timing and transition constants
   */
  public static final class AnimationTiming {
    public static final String TRANSITION_IN = "in";
    public static final String TRANSITION_OUT = "out";
    public static final String FILL_HOLD = "hold";
    public static final String NODE_TYPE_CLICK_EFFECT = "clickEffect";
    public static final String PRESET_CLASS_ENTRANCE = "entr";
    public static final String PRESET_CLASS_EXIT = "exit";
    public static final String DELAY_INDEFINITE = "indefinite";
    public static final String DELAY_ZERO = "0";
    public static final int DEFAULT_DURATION_MS = 500;
    public static final String VISIBILITY_VISIBLE = "visible";
    public static final String VISIBILITY_HIDDEN = "hidden";
    
    private AnimationTiming() {
      // Utility class - no instantiation
    }
  }

  // XPath expressions for presentation structure management
  public static final String XPATH_SLIDE_ID_LIST = "//p:sldIdLst";
  public static final String XPATH_SLIDE_ID_ELEMENTS = "./p:sldId";
  public static final String XPATH_SHAPE_TREE = "//p:spTree";

  // XPath expressions for relationship management
  public static final String XPATH_RELATIONSHIPS_ROOT = "//Relationships";
  public static final String XPATH_RELATIONSHIP_ELEMENTS = "./Relationship";

  // Relationship type constants
  public static final String RELATIONSHIP_TYPE_SLIDE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide";
  public static final String RELATIONSHIP_TYPE_SLIDE_LAYOUT = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout";
  public static final String RELATIONSHIP_TYPE_THEME = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme";
  public static final String RELATIONSHIP_TYPE_SLIDE_MASTER = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster";
  public static final String RELATIONSHIP_TYPE_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";
  public static final String RELATIONSHIP_TYPE_AUDIO = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/audio";

  // Content type constants
  public static final String CONTENT_TYPE_SLIDE = "application/vnd.openxmlformats-officedocument.presentationml.slide+xml";
  public static final String CONTENT_TYPE_SLIDE_LAYOUT = "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml";
  public static final String CONTENT_TYPE_SLIDE_MASTER = "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml";
  public static final String CONTENT_TYPE_THEME = "application/vnd.openxmlformats-officedocument.theme+xml";
  public static final String CONTENT_TYPE_PRESENTATION = "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";
  public static final String CONTENT_TYPE_RELATIONSHIPS = "application/vnd.openxmlformats-package.relationships+xml";
  public static final String CONTENT_TYPE_CORE_PROPERTIES = "application/vnd.openxmlformats-package.core-properties+xml";
  public static final String CONTENT_TYPE_APP_PROPERTIES = "application/vnd.openxmlformats-officedocument.extended-properties+xml";
  public static final String CONTENT_TYPE_NOTES_SLIDE = "application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml";

  // Animation timing constants
  public static final int DEFAULT_ANIMATION_INTERVAL_MS = 330;
  public static final String DEFAULT_ENTRANCE_EFFECT = "fade";
  public static final String DEFAULT_EXIT_EFFECT = "wipe(down)";
  public static final String INDEFINITE_DELAY = "indefinite";

  // PowerPoint units conversion
  public static final double EMU_TO_POINTS = 12700.0;
  public static final double EMU_TO_INCHES = 914400.0;

  // Standard slide relationship targets
  public static final String DEFAULT_SLIDE_LAYOUT_TARGET = "../slideLayouts/slideLayout1.xml";
  public static final String DEFAULT_THEME_TARGET = "../theme/theme1.xml";
  public static final String DEFAULT_SLIDE_MASTER_TARGET = "../slideMasters/slideMaster1.xml";

  // Presentation XML constants
  public static final int DEFAULT_SLIDE_ID_START = 256;
  public static final String RID_PREFIX = "rId";

  // XML template fragments for programmatic generation
  public static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
  
  // Slide ID element template
  public static final String SLIDE_ID_ELEMENT_TEMPLATE = "<p:sldId id=\"%d\" r:id=\"%s\"/>";
  
  // Relationship element template
  public static final String RELATIONSHIP_ELEMENT_TEMPLATE = "<Relationship Id=\"%s\" Type=\"%s\" Target=\"%s\"/>";
  
  // Content type override template
  public static final String CONTENT_TYPE_OVERRIDE_TEMPLATE = "<Override PartName=\"%s\" ContentType=\"%s\"/>";
  
  // Content type default template
  public static final String CONTENT_TYPE_DEFAULT_TEMPLATE = "<Default Extension=\"%s\" ContentType=\"%s\"/>";

  // Common file extensions
  public static final String XML_EXTENSION = "xml";
  public static final String RELS_EXTENSION = "rels";

  /**
   * Microsoft PowerPoint extension URIs from MS-PPTX spec.
   * These are well-known GUIDs used in p:extLst elements across OOXML parts.
   */
  public static final class ExtensionUri {
    // MS-PPTX Section 2.3.1.6: discardImageEditData
    public static final String DISCARD_IMAGE_EDIT_DATA = "{E76CE94A-603C-4142-B9EB-6D1370010A27}";
    // MS-PPTX Section 2.3.1.5: defaultImageDpi
    public static final String DEFAULT_IMAGE_DPI = "{D31A062A-798A-4329-ABDD-BBA856620510}";
    // MS-PPTX Section 2.4.1.1: chartTrackingRefBased
    public static final String CHART_TRACKING_REF_BASED = "{FD5EFAAD-0ECE-453E-9831-46B23BE46B34}";
    // MS-PPTX Section 2.4.1.6: sldGuideLst (slide guide list)
    public static final String SLIDE_GUIDE_LIST = "{EFAFB233-063F-42B5-8137-9DF3F51BA10A}";
    // MS-PPTX Section 2.4.1.3: notesGuideLst (notes guide list)
    public static final String NOTES_GUIDE_LIST = "{2D200454-40CA-4A62-9FC3-DE9A4176ACB9}";

    private ExtensionUri() {}
  }

  /**
   * Microsoft Office versioned namespaces for PowerPoint extensions.
   * Used in p:extLst child elements that reference newer Office features.
   */
  public static final class OfficeNamespace {
    public static final String POWERPOINT_2010 = "http://schemas.microsoft.com/office/powerpoint/2010/main";
    public static final String POWERPOINT_2012 = "http://schemas.microsoft.com/office/powerpoint/2012/main";

    private OfficeNamespace() {}
  }

  /**
   * OPC (Open Packaging Conventions) document property namespaces and constants.
   * Used in docProps/app.xml, docProps/core.xml, and related metadata parts.
   */
  public static final class DocProperties {
    public static final String EXTENDED_PROPERTIES_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties";
    public static final String VT_TYPES_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes";
    public static final String CORE_PROPERTIES_NS =
            "http://schemas.openxmlformats.org/package/2006/metadata/core-properties";
    public static final String DC_NS = "http://purl.org/dc/elements/1.1/";
    public static final String DCTERMS_NS = "http://purl.org/dc/terms/";
    public static final String DCMITYPE_NS = "http://purl.org/dc/dcmitype/";
    public static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    // PowerPoint heading pair labels (MS-OE376 Section 7.2.2.8)
    public static final String HEADING_FONTS_USED = "Fonts Used";
    public static final String HEADING_THEME = "Theme";
    public static final String HEADING_SLIDE_TITLES = "Slide Titles";

    // Standard presentation format values
    public static final String FORMAT_WIDESCREEN = "Widescreen";
    public static final String FORMAT_ON_SCREEN_4_3 = "On-screen Show (4:3)";

    private DocProperties() {}
  }

  private XMLConstants() {
    // Utility class - no instantiation
  }

  /**
   * Ensures a namespace is declared on the given element.
   * Single-source-of-truth for namespace declaration logic.
   * 
   * @param element The element to ensure namespace declaration on
   * @param prefix The namespace prefix (e.g., "r", "p", "a")
   * @param namespaceUri The namespace URI to declare
   */
  public static void ensureNamespaceDeclared(Element element, String prefix, String namespaceUri) {
    if (element.lookupNamespaceURI(prefix) == null) {
      element.setAttributeNS(XMLNS_ATTRIBUTE, "xmlns:" + prefix, namespaceUri);
    }
  }

  /**
   * Ensures all common PowerPoint namespaces are declared on an element.
   * 
   * @param element The element to ensure namespace declarations on
   */
  public static void ensureCommonNamespacesDeclared(Element element) {
    ensureNamespaceDeclared(element, PRESENTATION_PREFIX, PRESENTATION_NS);
    ensureNamespaceDeclared(element, DRAWING_PREFIX, DRAWING_NS);
    ensureNamespaceDeclared(element, RELATIONSHIPS_PREFIX, RELATIONSHIPS_NS);
  }

  /**
   * Centralized namespace context for PowerPoint OOXML XPath queries
   * Eliminates duplication across parser and writer classes
   */
  public static class PowerPointNamespaceContext implements NamespaceContext {
    private static final Map<String, String> NAMESPACES = Map.of(
        "p", PRESENTATION_NS,
        "a", DRAWING_NS,
        "r", RELATIONSHIPS_NS,
        "rel", PACKAGE_RELATIONSHIPS_NS
        );

    @Override
    public String getNamespaceURI(String prefix) {
      return NAMESPACES.getOrDefault(prefix, javax.xml.XMLConstants.NULL_NS_URI);
    }

    @Override
    public String getPrefix(String namespaceURI) {
      return NAMESPACES.entrySet().stream()
        .filter(entry -> entry.getValue().equals(namespaceURI))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
    }

    @Override
    public Iterator<String> getPrefixes(String namespaceURI) {
      return NAMESPACES.entrySet().stream()
        .filter(entry -> entry.getValue().equals(namespaceURI))
        .map(Map.Entry::getKey)
        .iterator();
    }
  }

  /**
   * Factory method for consistent namespace context creation
   */
  public static NamespaceContext createNamespaceContext() {
    return new PowerPointNamespaceContext();
  }
}
