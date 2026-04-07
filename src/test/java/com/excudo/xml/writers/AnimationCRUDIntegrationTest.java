package com.excudo.xml.writers;

import com.excudo.core.animations.AnimationFactoryRegistry;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;
import com.excudo.xml.writers.animations.SequentialGroupIdManager;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Animation CRUD operations: create, read, update, delete.
 * Tests removeAnimation(), updateAnimationProperties(), and the factory-based
 * paragraph-level animation path.
 */
class AnimationCRUDIntegrationTest {

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

    slideDocument = WriterTestFixtures.createSlideWithShapes(documentBuilder, 3, 4, 5, 6);
    injector = new AnimationInjector(slideDocument, xpath, animationRegistry, groupIdManager);
  }

  // ========== Helpers ==========

  private static AnimationBinding buildBinding(int spid, String type, String transition,
      int clickTrigger, String animationGroup) {
    AnimationBinding.Builder b = AnimationBinding.builder()
        .target(spid).type(type).duration("500").delay("0");
    if ("with-previous".equals(animationGroup)) {
      b.withPrevious();
    } else if ("after-previous".equals(animationGroup)) {
      b.afterPrevious();
    } else {
      b.clickTrigger(clickTrigger);
      b.animationGroup(animationGroup);
    }
    if ("in".equals(transition)) b.entrance();
    else if ("out".equals(transition)) b.exit();
    else b.emphasis();
    return b.build();
  }

  private static AnimationBinding buildParagraphBinding(int spid, String type, String transition,
      int paragraphIndex, String animationGroup) {
    AnimationBinding.Builder b = AnimationBinding.builder()
        .target(spid).type(type).duration("500").delay("0")
        .paragraphRange(paragraphIndex, paragraphIndex);
    if ("with-previous".equals(animationGroup)) b.withPrevious();
    else if ("after-previous".equals(animationGroup)) b.afterPrevious();
    else b.animationGroup("on-click");
    if ("in".equals(transition)) b.entrance();
    else if ("out".equals(transition)) b.exit();
    else b.emphasis();
    return b.build();
  }

  /**
   * Find the cTn id of the animation that has a presetID attribute,
   * targeting a specific spid. Returns the first match.
   */
  private int findPresetCTnId(int spid) throws Exception {
    NodeList nodes = (NodeList) xpath.evaluate(
        "//p:cTn[@presetID][.//p:spTgt[@spid='" + spid + "']]",
        slideDocument, XPathConstants.NODESET);
    if (nodes.getLength() == 0) {
      fail("No animation found for SPID " + spid);
    }
    return Integer.parseInt(((Element) nodes.item(0)).getAttribute("id"));
  }

  /**
   * Find all preset cTn ids for animations targeting a specific spid.
   */
  private int[] findAllPresetCTnIds(int spid) throws Exception {
    NodeList nodes = (NodeList) xpath.evaluate(
        "//p:cTn[@presetID][.//p:spTgt[@spid='" + spid + "']]",
        slideDocument, XPathConstants.NODESET);
    int[] ids = new int[nodes.getLength()];
    for (int i = 0; i < nodes.getLength(); i++) {
      ids[i] = Integer.parseInt(((Element) nodes.item(i)).getAttribute("id"));
    }
    return ids;
  }

  private int countAnimations() throws Exception {
    return ((NodeList) xpath.evaluate(
        "//p:cTn[@presetID]", slideDocument, XPathConstants.NODESET)).getLength();
  }

  private int countClickTriggers() throws Exception {
    return ((NodeList) xpath.evaluate(
        "//p:seq[@concurrent='1']/p:cTn[@nodeType='mainSeq']/p:childTnLst/p:par",
        slideDocument, XPathConstants.NODESET)).getLength();
  }

  private int countBldPEntries() throws Exception {
    return ((NodeList) xpath.evaluate(
        "//p:bldLst/p:bldP", slideDocument, XPathConstants.NODESET)).getLength();
  }

  // ========== Test 1: Delete single animation ==========

  @Test
  @DisplayName("Delete single animation preserves remaining animations and timing integrity")
  void testDeleteSingleAnimation() throws Exception {
    // Inject 2 animations on different shapes, different click triggers
    injector.injectAnimation(buildBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(4, "fade", "in", 2, "on-click"), DEFAULT_GEO);
    assertEquals(2, countAnimations());
    assertEquals(2, countBldPEntries());

    // Remove animation on shape 3
    int nodeId = findPresetCTnId(3);
    AnimationInjector.AnimationRemovalResult result = injector.removeAnimation(nodeId);

    assertTrue(result.isSuccess(), "Removal should succeed");
    assertEquals(3, result.getRemovedSpid());
    assertEquals("entr", result.getRemovedPresetClass());

    // Verify: 1 animation remains, 1 bldP remains, timing still valid
    assertEquals(1, countAnimations(), "Should have 1 animation remaining");
    assertEquals(1, countBldPEntries(), "Should have 1 bldP remaining");

    WriterTestFixtures.assertValidTimingHierarchy(slideDocument);
    WriterTestFixtures.assertUniqueTimingNodeIds(slideDocument);
    WriterTestFixtures.assertGrpIdBldLstLinkage(slideDocument);
    WriterTestFixtures.assertBuildListComplete(slideDocument);
  }

  // ========== Test 2: Delete last animation in click group ==========

  @Test
  @DisplayName("Delete last animation in click group removes the click container")
  void testDeleteLastAnimationInClickGroup() throws Exception {
    // Inject 2 animations in separate click groups
    injector.injectAnimation(buildBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(4, "fade", "in", 2, "on-click"), DEFAULT_GEO);
    assertEquals(2, countClickTriggers());

    // Remove the first click group's animation
    int nodeId = findPresetCTnId(3);
    AnimationInjector.AnimationRemovalResult result = injector.removeAnimation(nodeId);

    assertTrue(result.isSuccess());
    assertEquals(1, countClickTriggers(), "Click container should be removed when empty");
    assertEquals(1, countAnimations());

    WriterTestFixtures.assertValidTimingHierarchy(slideDocument);
    WriterTestFixtures.assertClickTriggersValid(slideDocument);
  }

  // ========== Test 3: Delete all animations ==========

  @Test
  @DisplayName("Delete all animations leaves clean timing tree")
  void testDeleteAllAnimations() throws Exception {
    injector.injectAnimation(buildBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(4, "fade", "in", 2, "on-click"), DEFAULT_GEO);
    assertEquals(2, countAnimations());

    // Remove both animations
    int nodeId1 = findPresetCTnId(3);
    injector.removeAnimation(nodeId1);

    int nodeId2 = findPresetCTnId(4);
    injector.removeAnimation(nodeId2);

    assertEquals(0, countAnimations(), "All animations should be removed");
    assertEquals(0, countClickTriggers(), "All click triggers should be removed");

    // bldLst should be gone
    Node bldLst = (Node) xpath.evaluate("//p:bldLst", slideDocument, XPathConstants.NODE);
    assertNull(bldLst, "bldLst should be removed when empty");
  }

  // ========== Test 4: Update duration ==========

  @Test
  @DisplayName("Update duration changes dur on inner behavior cTn nodes")
  void testUpdateDuration() throws Exception {
    injector.injectAnimation(buildBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);

    int nodeId = findPresetCTnId(3);

    injector.updateAnimationProperties(nodeId, Map.of("duration", "2000"));

    // Find inner cTn nodes with dur (behavior-level nodes inside the preset cTn)
    Element presetCTn = (Element) xpath.evaluate(
        "//p:cTn[@id='" + nodeId + "']", slideDocument, XPathConstants.NODE);
    NodeList innerDurs = (NodeList) xpath.evaluate(
        ".//p:cTn[@dur]", presetCTn, XPathConstants.NODESET);

    boolean foundUpdated = false;
    for (int i = 0; i < innerDurs.getLength(); i++) {
      Element inner = (Element) innerDurs.item(i);
      if (inner != presetCTn && "2000".equals(inner.getAttribute("dur"))) {
        foundUpdated = true;
      }
    }
    assertTrue(foundUpdated, "At least one inner cTn dur should be updated to 2000");

    WriterTestFixtures.assertUniqueTimingNodeIds(slideDocument);
  }

  // ========== Test 5: Update delay ==========

  @Test
  @DisplayName("Update delay changes condition on intermediate container")
  void testUpdateDelay() throws Exception {
    injector.injectAnimation(buildBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);

    int nodeId = findPresetCTnId(3);

    injector.updateAnimationProperties(nodeId, Map.of("delay", "500"));

    // The delay is on the intermediate container's stCondLst/cond
    // The intermediate is the grandparent par of the animation par
    Element presetCTn = (Element) xpath.evaluate(
        "//p:cTn[@id='" + nodeId + "']", slideDocument, XPathConstants.NODE);
    Element animPar = (Element) presetCTn.getParentNode();
    Element intermediateChildTnLst = (Element) animPar.getParentNode();
    Element intermediateCTn = (Element) intermediateChildTnLst.getParentNode();

    String delay = xpath.evaluate("./p:stCondLst/p:cond/@delay", intermediateCTn);
    assertEquals("500", delay, "Intermediate container delay should be updated");
  }

  // ========== Test 6: Add + Delete roundtrip ==========

  @Test
  @DisplayName("Add then delete roundtrip produces structurally equivalent tree")
  void testAddDeleteRoundtrip() throws Exception {
    // Capture initial state (before any animation)
    int initialAnimCount = countAnimations();
    assertEquals(0, initialAnimCount);

    // Inject, then remove
    injector.injectAnimation(buildBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);
    assertEquals(1, countAnimations());

    int nodeId = findPresetCTnId(3);
    AnimationInjector.AnimationRemovalResult result = injector.removeAnimation(nodeId);

    assertTrue(result.isSuccess());
    assertEquals(0, countAnimations(), "After roundtrip, animation count should be back to 0");
    assertEquals(0, countClickTriggers(), "After roundtrip, click triggers should be 0");

    Node bldLst = (Node) xpath.evaluate("//p:bldLst", slideDocument, XPathConstants.NODE);
    assertNull(bldLst, "After roundtrip, bldLst should be removed");
  }

  // ========== Test 7: Paragraph-level create via factory path ==========

  @Test
  @DisplayName("Paragraph-level animation via factory path produces valid output")
  void testParagraphLevelCreateViaFactory() throws Exception {
    AnimationBinding binding = buildParagraphBinding(3, "fade", "in", 0, "on-click");
    injector.injectAnimation(binding, DEFAULT_GEO);

    // Should have animation with pRg targeting
    String st = xpath.evaluate("//p:pRg/@st", slideDocument);
    String end = xpath.evaluate("//p:pRg/@end", slideDocument);
    assertEquals("0", st, "Paragraph range start");
    assertEquals("0", end, "Paragraph range end");

    // Must NOT have grpId on preset cTn
    NodeList presetNodes = (NodeList) xpath.evaluate(
        "//p:cTn[@presetID]", slideDocument, XPathConstants.NODESET);
    assertTrue(presetNodes.getLength() > 0);
    Element animCTn = (Element) presetNodes.item(0);
    assertFalse(animCTn.hasAttribute("grpId"),
        "Paragraph-level animation must NOT have grpId");

    // Must NOT have bldP
    assertEquals(0, countBldPEntries(),
        "Paragraph-level animations must NOT create bldP entries");

    // Correct presetID (should match FADE, not hardcoded 10)
    String presetID = animCTn.getAttribute("presetID");
    assertEquals(String.valueOf(AnimationType.FADE.getPresetId()), presetID,
        "Factory-based path should use correct presetID from AnimationType");

    WriterTestFixtures.assertValidTimingHierarchy(slideDocument);
    WriterTestFixtures.assertUniqueTimingNodeIds(slideDocument);
  }

  // ========== Test 8: Mixed CRUD sequence ==========

  @Test
  @DisplayName("Mixed CRUD sequence: add, update, delete in sequence maintains integrity")
  void testMixedCRUDSequence() throws Exception {
    // Add 3 animations
    injector.injectAnimation(buildBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(4, "wipe", "in", 2, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(5, "fade", "out", 3, "on-click"), DEFAULT_GEO);
    assertEquals(3, countAnimations());
    assertEquals(3, countBldPEntries());

    // Update duration of shape 4's animation
    int nodeId4 = findPresetCTnId(4);
    injector.updateAnimationProperties(nodeId4, Map.of("duration", "1500"));

    // Delete shape 3's animation
    int nodeId3 = findPresetCTnId(3);
    injector.removeAnimation(nodeId3);
    assertEquals(2, countAnimations(), "Should have 2 animations after delete");
    assertEquals(2, countBldPEntries(), "Should have 2 bldP entries after delete");

    // Verify shape 4's update persisted
    Element cTn4 = (Element) xpath.evaluate(
        "//p:cTn[@id='" + nodeId4 + "']", slideDocument, XPathConstants.NODE);
    assertNotNull(cTn4, "Shape 4 animation should still exist");

    // Delete shape 5's animation
    int nodeId5 = findPresetCTnId(5);
    injector.removeAnimation(nodeId5);
    assertEquals(1, countAnimations(), "Should have 1 animation remaining");
    assertEquals(1, countBldPEntries(), "Should have 1 bldP remaining");

    // Full validation battery on final state
    WriterTestFixtures.assertValidTimingHierarchy(slideDocument);
    WriterTestFixtures.assertUniqueTimingNodeIds(slideDocument);
    WriterTestFixtures.assertClickTriggersValid(slideDocument);
    WriterTestFixtures.assertBuildListComplete(slideDocument);
    WriterTestFixtures.assertBuildListAttributesValid(slideDocument);
    WriterTestFixtures.assertGrpIdBldLstLinkage(slideDocument);
    WriterTestFixtures.assertAnimationGroupTypes(slideDocument);
  }

  // ========== Edge cases ==========

  @Test
  @DisplayName("Remove non-existent timing node returns failure")
  void testRemoveNonExistent() throws Exception {
    AnimationInjector.AnimationRemovalResult result = injector.removeAnimation(99999);
    assertFalse(result.isSuccess());
    assertTrue(result.getMessage().contains("No timing node found"));
  }

  @Test
  @DisplayName("Update non-existent timing node throws XMLParsingException")
  void testUpdateNonExistent() {
    assertThrows(XMLParsingException.class, () ->
        injector.updateAnimationProperties(99999, Map.of("duration", "1000")));
  }

  @Test
  @DisplayName("Update with unsupported property throws XMLParsingException")
  void testUpdateUnsupportedProperty() throws Exception {
    injector.injectAnimation(buildBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);
    int nodeId = findPresetCTnId(3);

    assertThrows(XMLParsingException.class, () ->
        injector.updateAnimationProperties(nodeId, Map.of("bogus", "value")));
  }

  @Test
  @DisplayName("Update presetSubtype modifies attribute correctly")
  void testUpdatePresetSubtype() throws Exception {
    injector.injectAnimation(buildBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);
    int nodeId = findPresetCTnId(3);

    injector.updateAnimationProperties(nodeId, Map.of("presetSubtype", "4"));

    Element cTn = (Element) xpath.evaluate(
        "//p:cTn[@id='" + nodeId + "']", slideDocument, XPathConstants.NODE);
    assertEquals("4", cTn.getAttribute("presetSubtype"));
  }

  @Test
  @DisplayName("Delete emphasis animation removes its bldP, preserves entrance bldP")
  void testDeleteEmphasisAnimation() throws Exception {
    // Oracle: both entrance and emphasis create bldP entries
    injector.injectAnimation(buildBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(4, "fade", "emphasis", 1, "with-previous"), DEFAULT_GEO);

    assertEquals(2, countAnimations());
    assertEquals(2, countBldPEntries(), "Both entrance and emphasis should have bldP per oracle");

    // Delete emphasis -- its bldP should be cleaned up, entrance bldP remains
    int emphNodeId = findPresetCTnId(4);
    AnimationInjector.AnimationRemovalResult result = injector.removeAnimation(emphNodeId);

    assertTrue(result.isSuccess());
    assertEquals("emph", result.getRemovedPresetClass());
    assertEquals(1, countAnimations());
    assertEquals(1, countBldPEntries(), "Entrance bldP should be preserved");

    WriterTestFixtures.assertGrpIdBldLstLinkage(slideDocument);
  }

  // ========== Targeting level conflict validation ==========

  @Test
  @DisplayName("Shape-level animation after paragraph-level on same SPID is rejected")
  void testShapeLevelBlockedByExistingParagraphLevel() throws Exception {
    // Add paragraph-level animation first
    AnimationBinding paraBinding = buildParagraphBinding(3, "fade", "in", 0, "on-click");
    injector.injectAnimation(paraBinding, DEFAULT_GEO);

    // Attempt shape-level entrance on same SPID -- should fail
    AnimationBinding shapeBinding = buildBinding(3, "fade", "in", 2, "on-click");
    XMLParsingException ex = assertThrows(XMLParsingException.class, () ->
        injector.injectAnimation(shapeBinding, DEFAULT_GEO));

    assertTrue(ex.getMessage().contains("paragraph-level animations"),
        "Error should mention paragraph-level conflict");
    assertTrue(ex.getMessage().contains("SPID 3"),
        "Error should mention the conflicting SPID");
  }

  @Test
  @DisplayName("Paragraph-level animation after shape-level on same SPID is rejected")
  void testParagraphLevelBlockedByExistingShapeLevel() throws Exception {
    // Add shape-level animation first
    injector.injectAnimation(buildBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);

    // Attempt paragraph-level on same SPID -- should fail
    AnimationBinding paraBinding = buildParagraphBinding(3, "fade", "in", 0, "on-click");
    XMLParsingException ex = assertThrows(XMLParsingException.class, () ->
        injector.injectAnimation(paraBinding, DEFAULT_GEO));

    assertTrue(ex.getMessage().contains("shape-level"),
        "Error should mention shape-level conflict");
  }

  @Test
  @DisplayName("Shape-level animation on different SPID from paragraph-level is allowed")
  void testDifferentSpidNoConflict() throws Exception {
    // Paragraph-level on SPID 3
    AnimationBinding paraBinding = buildParagraphBinding(3, "fade", "in", 0, "on-click");
    injector.injectAnimation(paraBinding, DEFAULT_GEO);

    // Shape-level on SPID 4 -- different shape, should succeed
    injector.injectAnimation(buildBinding(4, "fade", "in", 2, "on-click"), DEFAULT_GEO);
    assertEquals(2, countAnimations());
  }

  @Test
  @DisplayName("Emphasis animation bypasses targeting level validation")
  void testEmphasisBypassesTargetingValidation() throws Exception {
    // Paragraph-level entrance on SPID 3
    AnimationBinding paraBinding = buildParagraphBinding(3, "fade", "in", 0, "on-click");
    injector.injectAnimation(paraBinding, DEFAULT_GEO);

    // Shape-level emphasis on same SPID -- should be allowed (emphasis is non-visibility)
    AnimationBinding emphBinding = buildBinding(3, "pulse", "emphasis", 1, "with-previous");
    assertDoesNotThrow(() -> injector.injectAnimation(emphBinding, DEFAULT_GEO));
    assertEquals(2, countAnimations());
  }

  @Test
  @DisplayName("Multiple paragraph-level animations on same SPID are allowed")
  void testMultipleParagraphLevelsSameSpid() throws Exception {
    // Multiple paragraphs on same shape -- should all work
    injector.injectAnimation(buildParagraphBinding(3, "fade", "in", 0, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildParagraphBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildParagraphBinding(3, "fade", "out", 0, "on-click"), DEFAULT_GEO);
    assertEquals(3, countAnimations());
  }

  @Test
  @DisplayName("Delete with-previous animation preserves click trigger with remaining animations")
  void testDeleteWithPreviousPreservesClickTrigger() throws Exception {
    // Add on-click + with-previous in same click group
    injector.injectAnimation(buildBinding(3, "fade", "in", 1, "on-click"), DEFAULT_GEO);
    injector.injectAnimation(buildBinding(4, "fade", "in", 1, "with-previous"), DEFAULT_GEO);

    assertEquals(1, countClickTriggers(), "Both should be in 1 click trigger");
    assertEquals(2, countAnimations());

    // Delete the with-previous animation
    int nodeId = findPresetCTnId(4);
    injector.removeAnimation(nodeId);

    assertEquals(1, countClickTriggers(), "Click trigger should be preserved");
    assertEquals(1, countAnimations(), "Original on-click animation should remain");

    WriterTestFixtures.assertValidTimingHierarchy(slideDocument);
    WriterTestFixtures.assertNoLeadingWithOrAfter(slideDocument);
  }
}
