package com.excudo.xml.writers;

import com.excudo.core.animations.AnimationFactoryRegistry;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;
import com.excudo.xml.writers.animations.SequentialGroupIdManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AnimationInjector -- animation injection into PowerPoint timing trees.
 */
class AnimationInjectorTest {

  @TempDir
  Path tempDir;

  private AnimationInjector injector;
  private Document slideDocument;
  private DocumentBuilder documentBuilder;
  private XPath xpath;
  private AnimationFactoryRegistry animationRegistry;
  private SequentialGroupIdManager groupIdManager;

  private static final ShapeGeometry DEFAULT_GEO = new ShapeGeometry(0, 0, 914400, 914400);

  @BeforeEach
  void setUp() throws Exception {
    documentBuilder = WriterTestFixtures.createDocumentBuilder();
    xpath = WriterTestFixtures.createConfiguredXPath();
    animationRegistry = new AnimationFactoryRegistry();
    groupIdManager = new SequentialGroupIdManager();

    slideDocument = WriterTestFixtures.createMinimalSlideDocument(documentBuilder);
    injector = new AnimationInjector(slideDocument, xpath, animationRegistry, groupIdManager);
  }

  // ========== Helper ==========

  /**
   * Build an AnimationBinding from primitives for test convenience.
   */
  private static AnimationBinding buildBinding(int spid, String type, String transition,
      String duration, String delay, int clickTrigger, String animationGroup) {
    AnimationBinding.Builder b = AnimationBinding.builder()
        .target(spid)
        .type(type)
        .duration(duration != null ? duration : "500")
        .delay(delay != null ? delay : "0");

    if ("with-previous".equals(animationGroup)) {
      b.withPrevious();
    } else if ("after-previous".equals(animationGroup)) {
      b.afterPrevious();
    } else {
      b.clickTrigger(clickTrigger);
      b.animationGroup(animationGroup);
    }

    if ("in".equals(transition)) {
      b.entrance();
    } else if ("out".equals(transition)) {
      b.exit();
    } else {
      b.emphasis();
    }
    return b.build();
  }

  // ========== Constructor ==========

  @Test
  @DisplayName("Constructor defaults to ID 1 for empty document")
  void testConstructor_defaultsId1ForEmpty() {
    // getNextId should return >= 1 since no existing timing IDs
    int firstId = injector.getNextId();
    assertTrue(firstId >= 1, "First ID should be >= 1 for document with no animations");
  }

  @Test
  @DisplayName("Constructor reads existing timing IDs from document")
  void testConstructor_readsExistingIds() throws Exception {
    // Create doc with existing timing node
    Document docWithTiming = WriterTestFixtures.createMinimalSlideDocument(documentBuilder);
    Element root = docWithTiming.getDocumentElement();

    Element timing = docWithTiming.createElementNS(XMLConstants.PRESENTATION_NS, "p:timing");
    root.appendChild(timing);

    Element tnLst = docWithTiming.createElementNS(XMLConstants.PRESENTATION_NS, "p:tnLst");
    timing.appendChild(tnLst);

    Element par = docWithTiming.createElementNS(XMLConstants.PRESENTATION_NS, "p:par");
    tnLst.appendChild(par);

    Element cTn = docWithTiming.createElementNS(XMLConstants.PRESENTATION_NS, "p:cTn");
    cTn.setAttribute("id", "5");
    cTn.setAttribute("dur", "indefinite");
    par.appendChild(cTn);

    AnimationInjector injectorWithExisting = new AnimationInjector(
        docWithTiming, xpath, animationRegistry, groupIdManager);

    int nextId = injectorWithExisting.getNextId();
    assertTrue(nextId > 5, "Next ID should be > 5 since existing max is 5");
  }

  // ========== injectAnimation ==========

  @Test
  @DisplayName("injectAnimation creates timing infrastructure if missing")
  void testInjectAnimation_createsTimingInfra() throws Exception {
    // Initially no timing element
    Node timing = (Node) xpath.evaluate("//p:timing", slideDocument, XPathConstants.NODE);
    assertNull(timing, "Should have no timing before injection");

    injector.injectAnimation(buildBinding(42, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);

    timing = (Node) xpath.evaluate("//p:timing", slideDocument, XPathConstants.NODE);
    assertNotNull(timing, "Should create timing element after injection");

    // Semantic validation
    WriterTestFixtures.assertValidTimingHierarchy(slideDocument);
    WriterTestFixtures.assertUniqueTimingNodeIds(slideDocument);
  }

  @Test
  @DisplayName("injectAnimation adds animation to existing timing")
  void testInjectAnimation_addsToExisting() throws Exception {
    injector.injectAnimation(buildBinding(10, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(20, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);

    // Both should be in the timing tree
    NodeList spTgts = (NodeList) xpath.evaluate("//p:spTgt", slideDocument, XPathConstants.NODESET);
    assertTrue(spTgts.getLength() >= 2,
        "Should have at least 2 shape targets after 2 injections");

    // Semantic validation
    WriterTestFixtures.assertUniqueTimingNodeIds(slideDocument);
    WriterTestFixtures.assertBuildListComplete(slideDocument);
    WriterTestFixtures.assertBuildListAttributesValid(slideDocument);
  }

  @Test
  @DisplayName("injectAnimation creates new click trigger for unmatched click")
  void testInjectAnimation_newClickTrigger() throws Exception {
    injector.injectAnimation(buildBinding(10, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(20, "fade", "in", "500", "0", 2, "on-click"), DEFAULT_GEO);

    // Should have at least 2 click trigger par elements
    NodeList clickPars = (NodeList) xpath.evaluate(
        "//p:seq[@concurrent='1']//p:childTnLst/p:par", slideDocument, XPathConstants.NODESET);
    assertTrue(clickPars.getLength() >= 2,
        "Should create separate click triggers for different click numbers");

    // Semantic validation
    WriterTestFixtures.assertClickTriggersValid(slideDocument);
    WriterTestFixtures.assertUniqueTimingNodeIds(slideDocument);
  }

  @Test
  @DisplayName("injectAnimation sets entrance preset class for in transition")
  void testInjectAnimation_entrancePresetClass() throws Exception {
    injector.injectAnimation(buildBinding(42, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);

    NodeList presetNodes = (NodeList) xpath.evaluate(
        "//p:cTn[@presetClass='entr']", slideDocument, XPathConstants.NODESET);
    assertTrue(presetNodes.getLength() > 0, "Should have entrance preset class for 'in' transition");

    WriterTestFixtures.assertEntranceVisibility(slideDocument);
  }

  @Test
  @DisplayName("injectAnimation sets exit preset class for out transition")
  void testInjectAnimation_exitPresetClass() throws Exception {
    injector.injectAnimation(buildBinding(42, "fade", "out", "500", "0", 1, "on-click"), DEFAULT_GEO);

    NodeList presetNodes = (NodeList) xpath.evaluate(
        "//p:cTn[@presetClass='exit']", slideDocument, XPathConstants.NODESET);
    assertTrue(presetNodes.getLength() > 0, "Should have exit preset class for 'out' transition");

    WriterTestFixtures.assertExitVisibility(slideDocument);
  }

  @Test
  @DisplayName("injectAnimation uses default duration and delay when null")
  void testInjectAnimation_defaultDurationDelay() throws Exception {
    injector.injectAnimation(buildBinding(42, "fade", "in", null, null, 1, "on-click"), DEFAULT_GEO);

    // Should not throw -- null duration/delay uses defaults
    Node timing = (Node) xpath.evaluate("//p:timing", slideDocument, XPathConstants.NODE);
    assertNotNull(timing, "Animation should be injected with default values");
  }

  // ========== injectParagraphRangeAnimation ==========

  @Test
  @DisplayName("Paragraph range animation targets correct paragraph range")
  void testParagraphRange_correctTargeting() throws Exception {
    injector.injectParagraphRangeAnimation(42, "fade", "in", "fade",
        "500", "0", 1, "on-click", 0, 2);

    String pRgSt = xpath.evaluate("//p:pRg/@st", slideDocument);
    String pRgEnd = xpath.evaluate("//p:pRg/@end", slideDocument);

    assertEquals("0", pRgSt, "Paragraph range start should be 0");
    assertEquals("2", pRgEnd, "Paragraph range end should be 2");
  }

  @Test
  @DisplayName("Paragraph range entrance animation sets visibility")
  void testParagraphRange_entranceVisibility() throws Exception {
    injector.injectParagraphRangeAnimation(42, "fade", "in", "fade",
        "500", "0", 1, "on-click", 0, 0);

    NodeList visibleSets = (NodeList) xpath.evaluate(
        "//p:set//p:strVal[@val='visible']", slideDocument, XPathConstants.NODESET);
    assertTrue(visibleSets.getLength() > 0,
        "Entrance animation should include visibility set to visible");

    WriterTestFixtures.assertEntranceVisibility(slideDocument);
    WriterTestFixtures.assertBuildListComplete(slideDocument);
  }

  @Test
  @DisplayName("Paragraph range exit animation sets hidden")
  void testParagraphRange_exitHidden() throws Exception {
    injector.injectParagraphRangeAnimation(42, "fade", "out", "fade",
        "500", "0", 1, "on-click", 0, 0);

    NodeList hiddenSets = (NodeList) xpath.evaluate(
        "//p:set//p:strVal[@val='hidden']", slideDocument, XPathConstants.NODESET);
    assertTrue(hiddenSets.getLength() > 0,
        "Exit animation should include visibility set to hidden");

    WriterTestFixtures.assertExitVisibility(slideDocument);
    WriterTestFixtures.assertBuildListComplete(slideDocument);
  }

  // ========== createNewClickTriggerPublic ==========

  @Test
  @DisplayName("createNewClickTriggerPublic creates timing infrastructure if missing")
  void testCreateClickTrigger_createsInfra() throws Exception {
    int clickNum = injector.createNewClickTriggerPublic();
    assertTrue(clickNum > 0, "Should return positive click number");

    Node timing = (Node) xpath.evaluate("//p:timing", slideDocument, XPathConstants.NODE);
    assertNotNull(timing, "Should create timing element");
  }

  @Test
  @DisplayName("createNewClickTriggerPublic increments click count")
  void testCreateClickTrigger_incrementsCount() throws Exception {
    int click1 = injector.createNewClickTriggerPublic();
    int click2 = injector.createNewClickTriggerPublic();

    assertTrue(click2 > click1, "Second click trigger should have higher number");
  }

  // ========== getNextId ==========

  @Test
  @DisplayName("getNextId returns monotonically increasing IDs")
  void testGetNextId_monotonicallyIncreasing() throws Exception {
    // Inject some animations to consume IDs
    injector.injectAnimation(buildBinding(10, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);

    int id1 = injector.getNextId();
    int id2 = injector.getNextId();
    assertTrue(id2 >= id1, "IDs should be monotonically non-decreasing");
  }

  // ========== Animation groups ==========

  @Test
  @DisplayName("on-click group produces clickEffect nodeType")
  void testAnimationGroup_onClick() throws Exception {
    injector.injectAnimation(buildBinding(42, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);

    NodeList clickEffects = (NodeList) xpath.evaluate(
        "//p:cTn[@nodeType='clickEffect']", slideDocument, XPathConstants.NODESET);
    assertTrue(clickEffects.getLength() > 0, "First on-click animation should be clickEffect");

    WriterTestFixtures.assertAnimationGroupTypes(slideDocument);
    WriterTestFixtures.assertNoLeadingWithOrAfter(slideDocument);
  }

  @Test
  @DisplayName("with-previous group produces withEffect nodeType")
  void testAnimationGroup_withPrevious() throws Exception {
    // Must inject an on-click animation first to allocate initial group ID
    injector.injectAnimation(buildBinding(41, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);

    injector.injectAnimation(buildBinding(42, "fade", "in", "500", "0", 1, "with-previous"), DEFAULT_GEO);

    NodeList withEffects = (NodeList) xpath.evaluate(
        "//p:cTn[@nodeType='withEffect']", slideDocument, XPathConstants.NODESET);
    assertTrue(withEffects.getLength() > 0, "with-previous animation should be withEffect");

    WriterTestFixtures.assertAnimationGroupTypes(slideDocument);
    WriterTestFixtures.assertNoLeadingWithOrAfter(slideDocument);
  }

  @Test
  @DisplayName("after-previous group produces afterEffect nodeType")
  void testAnimationGroup_afterPrevious() throws Exception {
    // Must inject an on-click animation first to allocate initial group ID
    injector.injectAnimation(buildBinding(41, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);

    injector.injectAnimation(buildBinding(42, "fade", "in", "500", "0", 1, "after-previous"), DEFAULT_GEO);

    NodeList afterEffects = (NodeList) xpath.evaluate(
        "//p:cTn[@nodeType='afterEffect']", slideDocument, XPathConstants.NODESET);
    assertTrue(afterEffects.getLength() > 0, "after-previous animation should be afterEffect");

    WriterTestFixtures.assertAnimationGroupTypes(slideDocument);
  }

  @Test
  @DisplayName("with-previous animation gets its own intermediate container")
  void testAnimationGroup_withPreviousSeparateContainer() throws Exception {
    // Inject on-click + with-previous into same click trigger
    injector.injectAnimation(buildBinding(41, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(42, "fade", "in", "500", "0", 1, "with-previous"), DEFAULT_GEO);

    // Each animation should have its own intermediate p:par with delay="0"
    // under the single click trigger
    NodeList clickTriggers = (NodeList) xpath.evaluate(
        "//p:seq[@concurrent='1']/p:cTn[@nodeType='mainSeq']/p:childTnLst/p:par",
        slideDocument, XPathConstants.NODESET);
    assertEquals(1, clickTriggers.getLength(),
        "Should have exactly 1 click trigger (with-previous joins existing)");

    // Count intermediate containers (p:par with delay="0") under the click trigger
    NodeList intermediates = (NodeList) xpath.evaluate(
        "./p:cTn/p:childTnLst/p:par[p:cTn/p:stCondLst/p:cond[@delay='0']]",
        clickTriggers.item(0), XPathConstants.NODESET);
    assertEquals(2, intermediates.getLength(),
        "Each animation needs its own intermediate container");
  }

  // ========== Paragraph-level animation constraints (MS-OI29500 cTn(h)) ==========

  /**
   * Build a paragraph-level AnimationBinding targeting a specific paragraph index.
   */
  private static AnimationBinding buildParagraphBinding(int spid, String type, String transition,
      int paragraphIndex, String animationGroup) {
    AnimationBinding.Builder b = AnimationBinding.builder()
        .target(spid)
        .type(type)
        .duration("500")
        .delay("0")
        .paragraphRange(paragraphIndex, paragraphIndex);

    if ("with-previous".equals(animationGroup)) {
      b.withPrevious();
    } else if ("after-previous".equals(animationGroup)) {
      b.afterPrevious();
    } else {
      b.animationGroup("on-click");
    }

    if ("in".equals(transition)) {
      b.entrance();
    } else if ("out".equals(transition)) {
      b.exit();
    } else {
      b.emphasis();
    }
    return b.build();
  }

  @Test
  @DisplayName("Paragraph-level animation omits grpId from cTn (no bldLst linkage)")
  void testParagraphLevel_omitsGrpId() throws Exception {
    AnimationBinding binding = buildParagraphBinding(42, "fade", "in", 0, "on-click");
    injector.injectAnimation(binding, DEFAULT_GEO);

    // Find the animation's cTn (the one with presetID)
    NodeList presetNodes = (NodeList) xpath.evaluate(
        "//p:cTn[@presetID]", slideDocument, XPathConstants.NODESET);
    assertTrue(presetNodes.getLength() > 0, "Should have injected animation cTn");

    Element animCTn = (Element) presetNodes.item(0);
    assertFalse(animCTn.hasAttribute("grpId"),
        "Paragraph-level animation cTn must NOT have grpId (MS-OI29500 cTn(h): grpId requires bldLst match)");
  }

  @Test
  @DisplayName("Paragraph-level animation creates no bldP entry")
  void testParagraphLevel_noBldP() throws Exception {
    AnimationBinding binding = buildParagraphBinding(42, "fade", "in", 0, "on-click");
    injector.injectAnimation(binding, DEFAULT_GEO);

    // bldLst should either not exist or be empty
    NodeList bldPNodes = (NodeList) xpath.evaluate(
        "//p:bldP", slideDocument, XPathConstants.NODESET);
    assertEquals(0, bldPNodes.getLength(),
        "Explicit per-paragraph animations must NOT create bldP entries (per native PowerPoint output)");
  }

  @Test
  @DisplayName("Paragraph-level animation correctly targets pRg in timing tree")
  void testParagraphLevel_correctParagraphTargeting() throws Exception {
    AnimationBinding binding = buildParagraphBinding(42, "fade", "in", 3, "on-click");
    injector.injectAnimation(binding, DEFAULT_GEO);

    // Should have pRg with correct indices
    String st = xpath.evaluate("//p:pRg/@st", slideDocument);
    String end = xpath.evaluate("//p:pRg/@end", slideDocument);
    assertEquals("3", st, "Should target paragraph 3");
    assertEquals("3", end, "Should target paragraph 3");
  }

  @Test
  @DisplayName("Multiple paragraph-level animations: none create bldP, all omit grpId")
  void testParagraphLevel_multipleAnimations() throws Exception {
    // Replicate the pattern from native PowerPoint slide 5:
    // Click 1: fade in par 0
    // Click 2: fade in par 1
    // Click 3: fade in par 3 + with-previous par 4 + with-previous par 5
    injector.injectAnimation(buildParagraphBinding(3, "fade", "in", 0, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildParagraphBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildParagraphBinding(3, "fade", "in", 3, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildParagraphBinding(3, "fade", "in", 4, "with-previous"), DEFAULT_GEO);
    injector.injectAnimation(buildParagraphBinding(3, "fade", "in", 5, "with-previous"), DEFAULT_GEO);

    // No bldP entries should exist
    NodeList bldPNodes = (NodeList) xpath.evaluate(
        "//p:bldP", slideDocument, XPathConstants.NODESET);
    assertEquals(0, bldPNodes.getLength(),
        "No bldP entries for explicit per-paragraph animations");

    // No grpId on any animation cTn
    NodeList presetNodes = (NodeList) xpath.evaluate(
        "//p:cTn[@presetID]", slideDocument, XPathConstants.NODESET);
    for (int i = 0; i < presetNodes.getLength(); i++) {
      Element cTn = (Element) presetNodes.item(i);
      assertFalse(cTn.hasAttribute("grpId"),
          "Animation cTn " + cTn.getAttribute("id") + " must not have grpId");
    }

    // Should have correct pRg targeting
    NodeList pRgNodes = (NodeList) xpath.evaluate(
        "//p:pRg", slideDocument, XPathConstants.NODESET);
    assertTrue(pRgNodes.getLength() >= 5,
        "Should have pRg entries for each paragraph animation");

    // Timing IDs should still be unique
    WriterTestFixtures.assertUniqueTimingNodeIds(slideDocument);
    WriterTestFixtures.assertValidTimingHierarchy(slideDocument);
  }

  @Test
  @DisplayName("Shape-level animation still creates bldP and grpId (not affected by paragraph fix)")
  void testShapeLevel_stillCreatesBldPAndGrpId() throws Exception {
    // Shape-level animation (no paragraph range)
    injector.injectAnimation(buildBinding(42, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);

    // Should have bldP
    NodeList bldPNodes = (NodeList) xpath.evaluate(
        "//p:bldP", slideDocument, XPathConstants.NODESET);
    assertEquals(1, bldPNodes.getLength(), "Shape-level animation must create bldP");

    // Should have grpId on cTn
    NodeList presetNodes = (NodeList) xpath.evaluate(
        "//p:cTn[@presetID]", slideDocument, XPathConstants.NODESET);
    Element animCTn = (Element) presetNodes.item(0);
    assertTrue(animCTn.hasAttribute("grpId"), "Shape-level animation must have grpId");
  }

  // ========== Emphasis animation constraints ==========

  @Test
  @DisplayName("Emphasis animation has grpId and bldP per oracle")
  void testEmphasis_hasGrpIdAndBldP() throws Exception {
    injector.injectAnimation(buildBinding(42, "fade", "emphasis", "500", "0", 1, "on-click"), DEFAULT_GEO);

    // Oracle (slides 89, 90, 115-118): emphasis cTn HAS grpId
    NodeList presetNodes = (NodeList) xpath.evaluate(
        "//p:cTn[@presetID]", slideDocument, XPathConstants.NODESET);
    assertTrue(presetNodes.getLength() > 0, "Should have injected emphasis animation");
    Element animCTn = (Element) presetNodes.item(0);
    assertTrue(animCTn.hasAttribute("grpId"),
        "Emphasis cTn must have grpId per oracle");
    assertEquals("emph", animCTn.getAttribute("presetClass"),
        "Should have emphasis preset class");

    // Oracle: emphasis animations have bldP entries
    NodeList bldPNodes = (NodeList) xpath.evaluate(
        "//p:bldP", slideDocument, XPathConstants.NODESET);
    assertEquals(1, bldPNodes.getLength(),
        "Emphasis animations must have bldP entries per oracle");
  }

  @Test
  @DisplayName("Mixed entrance + emphasis: both get bldP and grpId per oracle")
  void testMixedEntranceEmphasis_bothGetBldP() throws Exception {
    // Entrance on shape 10, emphasis on shape 20
    injector.injectAnimation(buildBinding(10, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(20, "fade", "emphasis", "500", "0", 1, "with-previous"), DEFAULT_GEO);

    // Oracle: both entrance and emphasis create bldP entries
    NodeList bldPNodes = (NodeList) xpath.evaluate(
        "//p:bldP", slideDocument, XPathConstants.NODESET);
    assertEquals(2, bldPNodes.getLength(), "Both entrance and emphasis should have bldP per oracle");

    // Both cTns should have grpId
    Element entrCTn = (Element) xpath.evaluate(
        "//p:cTn[@presetClass='entr']", slideDocument, XPathConstants.NODE);
    assertTrue(entrCTn.hasAttribute("grpId"), "Entrance cTn must have grpId");

    Element emphCTn = (Element) xpath.evaluate(
        "//p:cTn[@presetClass='emph']", slideDocument, XPathConstants.NODE);
    assertTrue(emphCTn.hasAttribute("grpId"), "Emphasis cTn must have grpId per oracle");
  }

  // ========== animBg propagation ==========

  @Test
  @DisplayName("animBg effectParam propagates to bldP attribute")
  void testAnimBg_propagatesToBldP() throws Exception {
    // Build binding with animBg effectParam (as AnimationOrchestrationManager would set)
    AnimationBinding binding = AnimationBinding.builder()
        .target(42)
        .type(AnimationType.FADE)
        .entrance()
        .duration("500")
        .delay("0")
        .animationGroup("on-click")
        .effectParam("animBg", "1")
        .build();

    injector.injectAnimation(binding, DEFAULT_GEO);

    Element bldP = (Element) xpath.evaluate("//p:bldP", slideDocument, XPathConstants.NODE);
    assertNotNull(bldP, "Should create bldP for entrance animation");
    assertEquals("1", bldP.getAttribute("animBg"),
        "animBg effectParam must propagate to bldP attribute (required for filled shapes)");
  }

  @Test
  @DisplayName("Binding without animBg produces bldP without animBg attribute")
  void testNoAnimBg_noBldPAttribute() throws Exception {
    injector.injectAnimation(buildBinding(42, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);

    Element bldP = (Element) xpath.evaluate("//p:bldP", slideDocument, XPathConstants.NODE);
    assertNotNull(bldP, "Should create bldP");
    assertEquals("", bldP.getAttribute("animBg"),
        "Binding without animBg should not produce animBg on bldP");
  }

  // ========== Entrance + exit on same shape ==========

  @Test
  @DisplayName("Entrance then exit on same shape produces separate grpIds and bldP entries")
  void testEntranceExitSameShape() throws Exception {
    injector.injectAnimation(buildBinding(42, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(42, "fade", "out", "500", "0", 2, "on-click"), DEFAULT_GEO);

    // Should have 2 animations
    NodeList presetNodes = (NodeList) xpath.evaluate(
        "//p:cTn[@presetID]", slideDocument, XPathConstants.NODESET);
    assertEquals(2, presetNodes.getLength(), "Should have entrance and exit animations");

    // Verify preset classes
    Element entrCTn = (Element) xpath.evaluate(
        "//p:cTn[@presetClass='entr']", slideDocument, XPathConstants.NODE);
    Element exitCTn = (Element) xpath.evaluate(
        "//p:cTn[@presetClass='exit']", slideDocument, XPathConstants.NODE);
    assertNotNull(entrCTn, "Should have entrance animation");
    assertNotNull(exitCTn, "Should have exit animation");

    // Different grpIds
    String entrGrpId = entrCTn.getAttribute("grpId");
    String exitGrpId = exitCTn.getAttribute("grpId");
    assertNotEquals(entrGrpId, exitGrpId,
        "Entrance and exit on same shape must have different grpIds");

    // Should have 2 bldP entries (one per grpId)
    NodeList bldPNodes = (NodeList) xpath.evaluate(
        "//p:bldP", slideDocument, XPathConstants.NODESET);
    assertEquals(2, bldPNodes.getLength(),
        "Entrance + exit on same shape needs 2 bldP entries (different grpIds)");

    // Full bidirectional linkage check
    WriterTestFixtures.assertGrpIdBldLstLinkage(slideDocument);
  }

  // ========== Mixed animation types (mirrors PowerPoint-confirmed test) ==========

  @Test
  @DisplayName("Mixed types: fade + fly-in + wipe + zoom (PowerPoint-confirmed scenario)")
  void testMixedTypes_pptConfirmedScenario() throws Exception {
    // Replicate exactly what passed PowerPoint Layer 3 validation:
    // Click 1: Fade on shape 3 (placeholder)
    injector.injectAnimation(buildBinding(3, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);
    // Click 2: Fly-in-bottom on shape 4 (rectangle, needs animBg)
    AnimationBinding flyInBinding = AnimationBinding.builder()
        .target(4).type(AnimationType.FLY_IN_BOTTOM).entrance()
        .duration("500").delay("0").animationGroup("on-click")
        .effectParam("animBg", "1").build();
    injector.injectAnimation(flyInBinding, DEFAULT_GEO);
    // After-previous: Wipe-left on shape 5 (rounded rect, needs animBg)
    AnimationBinding wipeBinding = AnimationBinding.builder()
        .target(5).type(AnimationType.WIPE_LEFT).entrance()
        .duration("500").delay("0").afterPrevious()
        .effectParam("animBg", "1").build();
    injector.injectAnimation(wipeBinding, DEFAULT_GEO);
    // Click 3: Zoom on shape 6 (picture, no animBg)
    injector.injectAnimation(buildBinding(6, "zoom", "in", "500", "0", 3, "on-click"), DEFAULT_GEO);

    // Full validation battery
    WriterTestFixtures.assertValidTimingHierarchy(slideDocument);
    WriterTestFixtures.assertUniqueTimingNodeIds(slideDocument);
    WriterTestFixtures.assertClickTriggersValid(slideDocument);
    WriterTestFixtures.assertBuildListComplete(slideDocument);
    WriterTestFixtures.assertBuildListAttributesValid(slideDocument);
    WriterTestFixtures.assertGrpIdBldLstLinkage(slideDocument);
    WriterTestFixtures.assertAnimationGroupTypes(slideDocument);
    WriterTestFixtures.assertNoLeadingWithOrAfter(slideDocument);

    // Verify animBg propagation
    Element bldP4 = (Element) xpath.evaluate(
        "//p:bldP[@spid='4']", slideDocument, XPathConstants.NODE);
    assertEquals("1", bldP4.getAttribute("animBg"),
        "Rectangle bldP must have animBg=1");

    Element bldP5 = (Element) xpath.evaluate(
        "//p:bldP[@spid='5']", slideDocument, XPathConstants.NODE);
    assertEquals("1", bldP5.getAttribute("animBg"),
        "Rounded rect bldP must have animBg=1");

    Element bldP6 = (Element) xpath.evaluate(
        "//p:bldP[@spid='6']", slideDocument, XPathConstants.NODE);
    assertEquals("", bldP6.getAttribute("animBg"),
        "Picture bldP must NOT have animBg");
  }

  // ========== Motion path animations ==========

  @Test
  @DisplayName("Motion custom injection produces p:animMotion with presetClass=path and accel/decel")
  void testInjectMotionCustom() throws Exception {
    AnimationBinding binding = AnimationBinding.builder()
        .target(42)
        .type(AnimationType.MOTION_CUSTOM)
        .entrance()
        .duration("500")
        .delay("0")
        .animationGroup("on-click")
        .motionPath("M 0 0 L 0.3 -0.2 L 0.5 0.1")
        .build();

    injector.injectAnimation(binding, DEFAULT_GEO);

    // Check presetClass="path"
    NodeList pathNodes = (NodeList) xpath.evaluate(
        "//p:cTn[@presetClass='path']", slideDocument, XPathConstants.NODESET);
    assertTrue(pathNodes.getLength() > 0, "Motion custom should have presetClass='path'");

    // Check accel and decel
    Element pathCTn = (Element) pathNodes.item(0);
    assertEquals("50000", pathCTn.getAttribute("accel"), "Should have accel=50000");
    assertEquals("50000", pathCTn.getAttribute("decel"), "Should have decel=50000");

    // Check p:animMotion is present
    NodeList animMotions = (NodeList) xpath.evaluate(
        "//p:animMotion", slideDocument, XPathConstants.NODESET);
    assertTrue(animMotions.getLength() > 0, "Should have p:animMotion element");

    // Check path attribute on animMotion
    Element animMotion = (Element) animMotions.item(0);
    assertEquals("M 0 0 L 0.3 -0.2 L 0.5 0.1", animMotion.getAttribute("path"));

    // Semantic validation
    WriterTestFixtures.assertValidTimingHierarchy(slideDocument);
    WriterTestFixtures.assertUniqueTimingNodeIds(slideDocument);
  }

  @Test
  @DisplayName("Motion linear injection produces default path with rCtr and ptsTypes=AA")
  void testInjectMotionLinear() throws Exception {
    AnimationBinding binding = AnimationBinding.builder()
        .target(42)
        .type(AnimationType.MOTION_LINEAR)
        .entrance()
        .duration("500")
        .delay("0")
        .animationGroup("on-click")
        .build();

    injector.injectAnimation(binding, DEFAULT_GEO);

    // Check p:animMotion
    NodeList animMotions = (NodeList) xpath.evaluate(
        "//p:animMotion", slideDocument, XPathConstants.NODESET);
    assertTrue(animMotions.getLength() > 0, "Should have p:animMotion element");

    Element animMotion = (Element) animMotions.item(0);
    // Check ptsTypes
    assertEquals("AA", animMotion.getAttribute("ptsTypes"), "Linear should have ptsTypes=AA");
    // Check rAng present for preset paths
    assertEquals("0", animMotion.getAttribute("rAng"), "Linear should have rAng=0");

    // Check rCtr
    NodeList rCtrs = animMotion.getElementsByTagNameNS(
        "http://schemas.openxmlformats.org/presentationml/2006/main", "rCtr");
    assertTrue(rCtrs.getLength() > 0, "Linear should have p:rCtr element");
    Element rCtr = (Element) rCtrs.item(0);
    assertEquals("0", rCtr.getAttribute("x"));
    assertEquals("12500", rCtr.getAttribute("y"));

    WriterTestFixtures.assertValidTimingHierarchy(slideDocument);
    WriterTestFixtures.assertUniqueTimingNodeIds(slideDocument);
  }

  // ========== Comprehensive semantic validation ==========

  @Test
  @DisplayName("Full semantic validation after multi-animation injection")
  void testFullSemanticValidation_multiAnimation() throws Exception {
    // Inject multiple animations across different click triggers and groups
    injector.injectAnimation(buildBinding(10, "fade", "in", "500", "0", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(20, "fade", "out", "500", "0", 1, "with-previous"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(30, "fade", "in", "500", "0", 2, "on-click"), DEFAULT_GEO);

    // Run all semantic assertions
    WriterTestFixtures.assertValidTimingHierarchy(slideDocument);
    WriterTestFixtures.assertUniqueTimingNodeIds(slideDocument);
    WriterTestFixtures.assertClickTriggersValid(slideDocument);
    WriterTestFixtures.assertBuildListComplete(slideDocument);
    WriterTestFixtures.assertBuildListAttributesValid(slideDocument);
    WriterTestFixtures.assertGrpIdBldLstLinkage(slideDocument);
    WriterTestFixtures.assertAnimationGroupTypes(slideDocument);
    WriterTestFixtures.assertNoLeadingWithOrAfter(slideDocument);
  }
}
