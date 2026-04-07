package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.xpath.*;
import java.util.*;
import com.excudo.core.model.*;
import com.excudo.core.utils.XMLConstants;
import com.excudo.exceptions.*;
import com.excudo.core.animations.AnimationFactoryRegistry;
import com.excudo.core.animations.AnimationWriter;
import com.excudo.xml.writers.animations.GroupIdManager;
import com.excudo.xml.writers.animations.AbstractAnimationFactory;
import com.excudo.xml.writers.animations.TimingNodeIdGenerator;

/**
 * Handles animation injection into PowerPoint slide timing trees.
 * Manages timing infrastructure creation, click triggers, visibility sets,
 * build lists, and animation group coordination.
 */
public class AnimationInjector implements TimingNodeIdGenerator {

  private final Document document;
  private final XPath xpath;
  private final AnimationFactoryRegistry animationFactoryRegistry;
  private final GroupIdManager groupIdManager;
  private int nextTimingNodeId = 1;
  // Used by legacy injectParagraphRangeAnimation path
  private int nextAnimationGroupId = 0;
  private int currentAnimationGroupId = -1;
  private Map<String, Integer> animationsPerClick = new HashMap<>();

  public AnimationInjector(Document document, XPath xpath,
                           AnimationFactoryRegistry animationFactoryRegistry,
                           GroupIdManager groupIdManager) {
    this.document = document;
    this.xpath = xpath;
    this.animationFactoryRegistry = animationFactoryRegistry;
    this.groupIdManager = groupIdManager;

    // Initialize timing node ID counter by checking existing IDs
    try {
      NodeList existingIds = (NodeList) xpath.evaluate("//@id[parent::p:cTn]",
        document, XPathConstants.NODESET);
      int maxId = 0;
      for (int i = 0; i < existingIds.getLength(); i++) {
        try {
          int id = Integer.parseInt(existingIds.item(i).getNodeValue());
          maxId = Math.max(maxId, id);
        } catch (NumberFormatException e) {
          // Ignore non-numeric IDs
        }
      }
      nextTimingNodeId = maxId + 1;
    } catch (XPathExpressionException ex) {
      nextTimingNodeId = 1;
    }
  }

  /**
   * Inject an animation into the slide timing tree using an AnimationBinding.
   * The binding carries all animation parameters end-to-end, including effectParams,
   * so factories receive the complete specification without reconstruction.
   *
   * @param binding  fully-populated AnimationBinding (type, transition, duration, effectParams, etc.)
   * @param geometry target shape geometry for coordinate-based animations
   * @throws XMLParsingException if injection fails
   */
  public void injectAnimation(AnimationBinding binding, ShapeGeometry geometry) throws XMLParsingException {
    try {
      Element timingElement = (Element) xpath.evaluate("//p:timing", document, XPathConstants.NODE);
      if (timingElement == null) {
        timingElement = createTimingInfrastructure();
      }

      // Validate targeting level compatibility with existing animations on the same shape
      if (!binding.isEmphasisAnimation()) {
        validateTargetingCompatibility(timingElement, binding);
      }

      Element clickNode;
      if (binding.isWithPrevious() || binding.isAfterPrevious()) {
        clickNode = findLastClickTriggerNode(timingElement);
        if (clickNode == null) {
          throw new XMLParsingException(
              "Cannot use with-previous/after-previous as first animation on a slide");
        }
      } else {
        // on-click: always create a new top-level click container
        clickNode = createNewClickTrigger(timingElement, 0);
      }

      // Create intermediate container FIRST so its timing node ID
      // precedes the factory's IDs in document order (per oracle)
      Element intermediateContainer = createIntermediateContainer();
      appendToClickTrigger(clickNode, intermediateContainer);

      AnimationType enumType = binding.getAnimationType();

      AnimationWriter factory = animationFactoryRegistry.getFactory(enumType);
      if (factory == null) {
        throw new XMLParsingException("No animation factory available for animation type: " + enumType);
      }

      if (factory instanceof AbstractAnimationFactory) {
        AbstractAnimationFactory abstractFactory = (AbstractAnimationFactory) factory;
        abstractFactory.setGroupIdManager(groupIdManager);
        abstractFactory.setTimingNodeIdGenerator(this);
      }

      Element container = factory.createTimingContainer(document, binding);
      Element childTnLst = (Element) xpath.evaluate("./p:cTn/p:childTnLst", container, XPathConstants.NODE);

      List<Element> animationElements = factory.createAnimationElements(document, binding, geometry);
      for (Element animationElement : animationElements) {
        childTnLst.appendChild(animationElement);
      }

      // Append animation container into the intermediate's childTnLst
      Element intermediateChildTnLst = (Element) xpath.evaluate(
          "./p:cTn/p:childTnLst", intermediateContainer, XPathConstants.NODE);
      intermediateChildTnLst.appendChild(container);

      // Build list entries are created for shape-level animations (entrance, exit, emphasis).
      // Oracle (slides 89, 90, 115-118): emphasis animations DO have grpId + bldP.
      // Omitted only for explicit per-paragraph animations (no grpId; PowerPoint targets
      // paragraphs via pRg without bldP, per native PowerPoint output).
      if (!binding.isParagraphLevelAnimation()) {
        Element buildEntry = factory.createBuildListEntry(document, binding);
        addToBuildList(timingElement, buildEntry);
      }

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to inject animation", e);
    }
  }

  /**
   * Inject animation binding for a specific paragraph range within a shape
   */
  public void injectParagraphRangeAnimation(int targetSpid, String animationType, String transition,
      String filter, String duration, String delay, int clickTrigger, String animationGroup,
      int paragraphStart, int paragraphEnd) throws XMLParsingException {
    try {
      Element timingElement = (Element) xpath.evaluate("//p:timing", document, XPathConstants.NODE);
      if (timingElement == null) {
        timingElement = createTimingInfrastructure();
      }

      Element clickNode = findClickTriggerNode(timingElement, clickTrigger);
      if (clickNode == null) {
        clickNode = createNewClickTrigger(timingElement, clickTrigger);
      }

      Element intermediateContainer = createIntermediateContainer();
      appendToClickTrigger(clickNode, intermediateContainer);

      Element animationEffect = createParagraphRangeAnimationEffect(targetSpid, animationType, transition,
          filter, duration, delay, animationGroup, clickTrigger, paragraphStart, paragraphEnd);

      try {
        Element intermediateChildTnLst = (Element) xpath.evaluate(
            "./p:cTn/p:childTnLst", intermediateContainer, XPathConstants.NODE);
        intermediateChildTnLst.appendChild(animationEffect);
      } catch (XPathExpressionException ex) {
        throw new XMLParsingException("Failed to append paragraph animation to intermediate container", ex);
      }

      int grpId = getAnimationGroupId(animationGroup);
      addToBuildList(timingElement, targetSpid, grpId, animationGroup);

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to inject paragraph range animation", e);
    }
  }

  // ========== ANIMATION REMOVAL ==========

  /**
   * Result of an animation removal operation.
   */
  public static class AnimationRemovalResult {
    private final boolean success;
    private final int removedSpid;
    private final String removedPresetClass;
    private final String removedPresetId;
    private final String message;

    public AnimationRemovalResult(boolean success, int removedSpid,
        String removedPresetClass, String removedPresetId, String message) {
      this.success = success;
      this.removedSpid = removedSpid;
      this.removedPresetClass = removedPresetClass;
      this.removedPresetId = removedPresetId;
      this.message = message;
    }

    public static AnimationRemovalResult failure(String message) {
      return new AnimationRemovalResult(false, -1, null, null, message);
    }

    public boolean isSuccess() { return success; }
    public int getRemovedSpid() { return removedSpid; }
    public String getRemovedPresetClass() { return removedPresetClass; }
    public String getRemovedPresetId() { return removedPresetId; }
    public String getMessage() { return message; }
  }

  /**
   * Remove an animation from the timing tree by its cTn timing node ID.
   *
   * Walks the DOM hierarchy: preset cTn -> animation par -> intermediate par -> click trigger par,
   * removing empty containers bottom-up. Cleans up orphaned bldLst entries.
   *
   * @param timingNodeId the id attribute of the animation's preset-level p:cTn
   * @return result with removed animation metadata
   * @throws XMLParsingException if the timing tree is malformed
   */
  public AnimationRemovalResult removeAnimation(int timingNodeId) throws XMLParsingException {
    try {
      // 1. Find the target cTn by ID
      Element targetCTn = (Element) xpath.evaluate(
          "//p:cTn[@id='" + timingNodeId + "']", document, XPathConstants.NODE);
      if (targetCTn == null) {
        return AnimationRemovalResult.failure("No timing node found with id=" + timingNodeId);
      }

      // Extract metadata before removal
      String presetClass = targetCTn.getAttribute("presetClass");
      String presetId = targetCTn.getAttribute("presetID");
      String grpId = targetCTn.hasAttribute("grpId") ? targetCTn.getAttribute("grpId") : null;

      // Find the SPID targeted by this animation
      Element spTgt = (Element) xpath.evaluate(".//p:spTgt", targetCTn, XPathConstants.NODE);
      int spid = -1;
      if (spTgt != null) {
        try {
          spid = Integer.parseInt(spTgt.getAttribute("spid"));
        } catch (NumberFormatException e) {
          // leave as -1
        }
      }

      // 2. Walk up: cTn is child of animation par (the preset container)
      Element animationPar = (Element) targetCTn.getParentNode();
      if (animationPar == null || !"p:par".equals(animationPar.getNodeName())) {
        return AnimationRemovalResult.failure("Unexpected DOM structure: cTn parent is not p:par");
      }

      // 3. Animation par sits inside intermediate's childTnLst
      Element intermediateChildTnLst = (Element) animationPar.getParentNode();
      if (intermediateChildTnLst == null) {
        return AnimationRemovalResult.failure("Unexpected DOM structure: animation par has no parent");
      }
      Element intermediateCTn = (Element) intermediateChildTnLst.getParentNode();
      Element intermediatePar = (Element) intermediateCTn.getParentNode();

      // 4. Remove the animation par from intermediate's childTnLst
      intermediateChildTnLst.removeChild(animationPar);

      // 5. If intermediate childTnLst is now empty, remove intermediate par from click trigger
      if (!hasChildElements(intermediateChildTnLst)) {
        Element clickChildTnLst = (Element) intermediatePar.getParentNode();
        clickChildTnLst.removeChild(intermediatePar);

        // 6. If click trigger's childTnLst is now empty, remove the click trigger par from mainSeq
        if (!hasChildElements(clickChildTnLst)) {
          Element clickCTn = (Element) clickChildTnLst.getParentNode();
          Element clickPar = (Element) clickCTn.getParentNode();
          Element mainSeqChildTnLst = (Element) clickPar.getParentNode();
          mainSeqChildTnLst.removeChild(clickPar);
        }
      }

      // 7. Clean up bldLst if needed
      if (grpId != null && spid >= 0) {
        cleanupBuildListEntry(spid, grpId);
      }

      return new AnimationRemovalResult(true, spid, presetClass, presetId,
          "Removed animation (presetID=" + presetId + ", presetClass=" + presetClass
          + ") targeting SPID " + spid);

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to remove animation with id=" + timingNodeId, e);
    }
  }

  /**
   * Check if a bldLst entry (spid, grpId) is still referenced by any animation in the timing tree.
   * If not, remove the orphaned bldP entry. If bldLst becomes empty, remove it entirely.
   */
  private void cleanupBuildListEntry(int spid, String grpId) throws XPathExpressionException {
    // Check if any remaining animation references this (spid, grpId) pair
    NodeList remainingRefs = (NodeList) xpath.evaluate(
        "//p:cTn[@grpId='" + grpId + "'][.//p:spTgt[@spid='" + spid + "']]",
        document, XPathConstants.NODESET);

    if (remainingRefs.getLength() == 0) {
      // No remaining references -- remove the bldP entry
      Element bldP = (Element) xpath.evaluate(
          "//p:bldLst/p:bldP[@spid='" + spid + "' and @grpId='" + grpId + "']",
          document, XPathConstants.NODE);
      if (bldP != null) {
        Element bldLst = (Element) bldP.getParentNode();
        bldLst.removeChild(bldP);

        // If bldLst is now empty, remove it
        if (!hasChildElements(bldLst)) {
          bldLst.getParentNode().removeChild(bldLst);
        }
      }
    }
  }

  // ========== ANIMATION PROPERTY UPDATE ==========

  /**
   * Update properties of an existing animation in-place by its cTn timing node ID.
   *
   * Supported property keys:
   *   "duration" -- updates dur attribute on the animation's inner cTn nodes
   *   "delay"    -- updates delay in stCondLst conditions
   *   "presetSubtype" -- updates presetSubtype attribute on the preset cTn
   *
   * @param timingNodeId the id attribute of the animation's preset-level p:cTn
   * @param properties map of property names to new values
   * @throws XMLParsingException if the node is not found or DOM is malformed
   */
  public void updateAnimationProperties(int timingNodeId, Map<String, String> properties)
      throws XMLParsingException {
    try {
      Element targetCTn = (Element) xpath.evaluate(
          "//p:cTn[@id='" + timingNodeId + "']", document, XPathConstants.NODE);
      if (targetCTn == null) {
        throw new XMLParsingException("No timing node found with id=" + timingNodeId);
      }

      for (Map.Entry<String, String> entry : properties.entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();

        switch (key) {
          case "duration":
            updateDuration(targetCTn, value);
            break;
          case "delay":
            updateDelay(targetCTn, value);
            break;
          case "presetSubtype":
            targetCTn.setAttribute("presetSubtype", value);
            break;
          default:
            throw new XMLParsingException("Unsupported animation property: " + key);
        }
      }
    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to update animation properties for id=" + timingNodeId, e);
    }
  }

  /**
   * Update dur attribute on all inner cTn nodes that have a dur attribute
   * (behavior cTn nodes inside animEffect, set, anim, etc.).
   */
  private void updateDuration(Element presetCTn, String newDuration) throws XPathExpressionException {
    NodeList innerCTns = (NodeList) xpath.evaluate(
        ".//p:cTn[@dur]", presetCTn, XPathConstants.NODESET);
    for (int i = 0; i < innerCTns.getLength(); i++) {
      Element inner = (Element) innerCTns.item(i);
      // Skip the preset cTn itself if it has dur -- only update behavior-level dur
      if (inner != presetCTn) {
        inner.setAttribute("dur", newDuration);
      }
    }
  }

  /**
   * Update delay in the stCondLst of the animation's intermediate container.
   * The intermediate container is the parent par of the animation par.
   */
  private void updateDelay(Element presetCTn, String newDelay) throws XPathExpressionException {
    // The delay condition is on the intermediate container's stCondLst,
    // which is the grandparent par's cTn's stCondLst
    Element animPar = (Element) presetCTn.getParentNode();
    Element intermediateChildTnLst = (Element) animPar.getParentNode();
    Element intermediateCTn = (Element) intermediateChildTnLst.getParentNode();

    Element delayCond = (Element) xpath.evaluate(
        "./p:stCondLst/p:cond", intermediateCTn, XPathConstants.NODE);
    if (delayCond != null) {
      delayCond.setAttribute("delay", newDelay);
    }
  }

  /**
   * Check if an element has any child elements (ignoring text/whitespace nodes).
   */
  private boolean hasChildElements(Element element) {
    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
        return true;
      }
    }
    return false;
  }

  // ========== TARGETING LEVEL VALIDATION ==========

  /**
   * Validate that a new animation's targeting level (shape vs paragraph) is compatible
   * with existing animations on the same shape.
   *
   * PowerPoint enforces visibility semantics per targeting level. Mixing shape-level
   * and paragraph-level entrance/exit animations on the same shape produces broken
   * sequences because the two levels manage visibility independently:
   * - Paragraph-level animations reveal/hide individual bullets within a visible shape container
   * - Shape-level animations reveal/hide the entire shape as a unit
   *
   * A shape-level entrance after paragraph-level exits does nothing visible because
   * the shape container never became hidden (only its paragraphs did).
   *
   * @param timingElement the timing root element
   * @param binding the new animation to inject
   * @throws XMLParsingException if the targeting levels conflict
   */
  private void validateTargetingCompatibility(Element timingElement, AnimationBinding binding)
      throws XMLParsingException {
    try {
      int spid = binding.getTargetSpid();

      // Find all existing spTgt elements targeting this shape
      NodeList existingTargets = (NodeList) xpath.evaluate(
          ".//p:spTgt[@spid='" + spid + "']", timingElement, XPathConstants.NODESET);

      if (existingTargets.getLength() == 0) {
        return; // No existing animations on this shape -- no conflict possible
      }

      boolean existingHasParagraph = false;
      boolean existingHasShape = false;

      for (int i = 0; i < existingTargets.getLength(); i++) {
        Element target = (Element) existingTargets.item(i);
        Element txEl = findChildElement(target, "p:txEl");
        if (txEl != null && findChildElement(txEl, "p:pRg") != null) {
          existingHasParagraph = true;
        } else {
          // Check this is from an entrance/exit animation, not just a visibility set or emphasis
          // Walk up to find the preset cTn to check presetClass
          Element presetCTn = findAncestorPresetCTn(target);
          if (presetCTn != null) {
            String presetClass = presetCTn.getAttribute("presetClass");
            if ("entr".equals(presetClass) || "exit".equals(presetClass)) {
              existingHasShape = true;
            }
          }
        }
      }

      boolean newIsParagraph = binding.isParagraphLevelAnimation();
      boolean newIsShape = binding.isShapeLevelAnimation();

      // Conflict: existing paragraph-level + new shape-level entrance/exit
      if (existingHasParagraph && newIsShape && !binding.isEmphasisAnimation()) {
        throw new XMLParsingException(
            "Cannot add shape-level " + binding.getTransition() + " animation to SPID " + spid
            + ": this shape already has paragraph-level animations. "
            + "Mixing targeting levels produces broken visibility sequences. Options: "
            + "(1) Add a paragraph-level animation instead (target specific paragraphs), or "
            + "(2) Remove all existing paragraph-level animations first, then add shape-level animations.");
      }

      // Conflict: existing shape-level entrance/exit + new paragraph-level
      if (existingHasShape && newIsParagraph) {
        throw new XMLParsingException(
            "Cannot add paragraph-level animation to SPID " + spid
            + ": this shape already has shape-level entrance/exit animations. "
            + "Mixing targeting levels produces broken visibility sequences. Options: "
            + "(1) Add a shape-level animation instead, or "
            + "(2) Remove all existing shape-level animations first, then add paragraph-level animations.");
      }

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to validate targeting compatibility", e);
    }
  }

  /**
   * Find the nearest ancestor p:cTn that has a presetClass attribute (the animation preset container).
   */
  private Element findAncestorPresetCTn(Element element) {
    Node current = element.getParentNode();
    while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
      Element el = (Element) current;
      if ("p:cTn".equals(el.getNodeName()) && el.hasAttribute("presetClass")) {
        return el;
      }
      current = current.getParentNode();
    }
    return null;
  }

  /**
   * Find a direct child element by tag name.
   */
  private Element findChildElement(Element parent, String tagName) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
        Element child = (Element) children.item(i);
        if (tagName.equals(child.getNodeName())) {
          return child;
        }
      }
    }
    return null;
  }

  /**
   * Create a new click trigger in the timing sequence (public API)
   */
  public int createNewClickTriggerPublic() throws XMLParsingException {
    try {
      Element timingElement = (Element) xpath.evaluate("//p:timing", document, XPathConstants.NODE);
      if (timingElement == null) {
        timingElement = createTimingInfrastructure();
      }

      Element mainSeq = (Element) xpath.evaluate(".//p:seq[@concurrent='1']//p:cTn", timingElement, XPathConstants.NODE);
      if (mainSeq == null) {
        throw new XMLParsingException("No main sequence found in timing");
      }

      int newClickNumber = getNextClickTriggerNumber(mainSeq);
      Element newClickTrigger = createClickTriggerElement(newClickNumber);

      Element childTnLst = (Element) xpath.evaluate("./p:childTnLst", mainSeq, XPathConstants.NODE);
      if (childTnLst == null) {
        childTnLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:childTnLst");
        mainSeq.appendChild(childTnLst);
      }

      childTnLst.appendChild(newClickTrigger);
      return newClickNumber;

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to create new click trigger", e);
    }
  }

  /**
   * Reset animation group ID allocation for a new slide.
   */
  public void resetAnimationGroupIds() {
    groupIdManager.resetForNewSlide();
  }

  @Override
  public int getNextId() {
    return getNextTimingNodeId();
  }

  // ========== TIMING INFRASTRUCTURE ==========

  private Element createTimingInfrastructure() throws XMLParsingException {
    try {
      Element slideElement = document.getDocumentElement();

      Element timing = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:timing");

      Element tnLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:tnLst");
      timing.appendChild(tnLst);

      // bldLst is created on-demand by addToBuildList() when the first entrance/exit
      // animation needs it. Emphasis-only slides must NOT have an empty <p:bldLst/>.

      Element par = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:par");
      tnLst.appendChild(par);

      Element cTn = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cTn");
      cTn.setAttribute("id", String.valueOf(getNextTimingNodeId()));
      cTn.setAttribute("dur", "indefinite");
      cTn.setAttribute("restart", "never");
      cTn.setAttribute("nodeType", "tmRoot");
      par.appendChild(cTn);

      Element childTnLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:childTnLst");
      cTn.appendChild(childTnLst);

      Element seq = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:seq");
      seq.setAttribute("concurrent", "1");
      seq.setAttribute("nextAc", "seek");
      childTnLst.appendChild(seq);

      Element seqCTn = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cTn");
      seqCTn.setAttribute("id", String.valueOf(getNextTimingNodeId()));
      seqCTn.setAttribute("dur", "indefinite");
      seqCTn.setAttribute("nodeType", "mainSeq");
      seq.appendChild(seqCTn);

      Element seqChildTnLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:childTnLst");
      seqCTn.appendChild(seqChildTnLst);

      Element prevCondLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:prevCondLst");
      seq.appendChild(prevCondLst);

      Element prevCond = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cond");
      prevCond.setAttribute("evt", "onPrev");
      prevCond.setAttribute("delay", "0");
      prevCondLst.appendChild(prevCond);

      Element prevTgt = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:tgtEl");
      prevCond.appendChild(prevTgt);

      Element prevSldTgt = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:sldTgt");
      prevTgt.appendChild(prevSldTgt);

      Element nextCondLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:nextCondLst");
      seq.appendChild(nextCondLst);

      Element nextCond = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cond");
      nextCond.setAttribute("evt", "onNext");
      nextCond.setAttribute("delay", "0");
      nextCondLst.appendChild(nextCond);

      Element nextTgt = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:tgtEl");
      nextCond.appendChild(nextTgt);

      Element nextSldTgt = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:sldTgt");
      nextTgt.appendChild(nextSldTgt);

      slideElement.appendChild(timing);

      return timing;

    } catch (Exception e) {
      throw new XMLParsingException("Failed to create timing infrastructure", e);
    }
  }

  // ========== CLICK TRIGGER MANAGEMENT ==========

  private Element findClickTriggerNode(Element timingElement, int clickNumber) throws XPathExpressionException {
    NodeList clickTriggers = (NodeList) xpath.evaluate(
        ".//p:seq[@concurrent='1']//p:childTnLst/p:par[p:cTn/p:stCondLst/p:cond/@delay='indefinite']",
        timingElement, XPathConstants.NODESET);

    if (clickNumber > 0 && clickNumber <= clickTriggers.getLength()) {
      return (Element) clickTriggers.item(clickNumber - 1);
    }

    return null;
  }

  private Element findLastClickTriggerNode(Element timingElement) throws XMLParsingException {
    try {
      // Match only direct children of mainSeq's childTnLst (click containers),
      // not nested animation containers that also have delay="indefinite"
      NodeList clickTriggers = (NodeList) xpath.evaluate(
          ".//p:seq[@concurrent='1']/p:cTn[@nodeType='mainSeq']/p:childTnLst/p:par[p:cTn/p:stCondLst/p:cond/@delay='indefinite']",
          timingElement, XPathConstants.NODESET);

      if (clickTriggers.getLength() == 0) {
        return null;
      }
      return (Element) clickTriggers.item(clickTriggers.getLength() - 1);
    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to find last click trigger node", e);
    }
  }

  private Element createNewClickTrigger(Element timingElement, int clickNumber) throws XMLParsingException {
    try {
      Element mainSeq = (Element) xpath.evaluate(".//p:seq[@concurrent='1']//p:cTn", timingElement, XPathConstants.NODE);
      if (mainSeq == null) {
        throw new XMLParsingException("No main sequence found");
      }

      Element newClickTrigger = createClickTriggerElement(clickNumber);

      Element childTnLst = (Element) xpath.evaluate("./p:childTnLst", mainSeq, XPathConstants.NODE);
      if (childTnLst == null) {
        childTnLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:childTnLst");
        mainSeq.appendChild(childTnLst);
      }

      childTnLst.appendChild(newClickTrigger);
      return newClickTrigger;
    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to create new click trigger", e);
    }
  }

  private Element createClickTriggerElement(int clickNumber) {
    Element par = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:par");

    Element cTn = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:cTn");
    cTn.setAttribute("id", String.valueOf(getNextTimingNodeId()));
    cTn.setAttribute("fill", "hold");
    par.appendChild(cTn);

    Element stCondLst = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:stCondLst");
    cTn.appendChild(stCondLst);

    Element cond = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:cond");
    cond.setAttribute("delay", "indefinite");
    stCondLst.appendChild(cond);

    Element childTnLst = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:childTnLst");
    cTn.appendChild(childTnLst);

    return par;
  }

  private int getNextClickTriggerNumber(Element mainSeq) throws XMLParsingException {
    try {
      NodeList clickTriggers = (NodeList) xpath.evaluate("./p:childTnLst/p:par", mainSeq, XPathConstants.NODESET);
      return clickTriggers.getLength() + 1;
    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to count existing click triggers", e);
    }
  }

  // ========== ANIMATION INJECTION ==========

  /**
   * Create a new intermediate container (p:par with delay="0").
   * Per oracle, each animation gets its own intermediate container.
   */
  private Element createIntermediateContainer() {
    Element intermediateContainer = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:par");

    Element intermediateCTn = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cTn");
    intermediateCTn.setAttribute("id", String.valueOf(getNextTimingNodeId()));
    intermediateCTn.setAttribute("fill", "hold");
    intermediateContainer.appendChild(intermediateCTn);

    Element intermediateStCondLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:stCondLst");
    intermediateCTn.appendChild(intermediateStCondLst);

    Element intermediateCond = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cond");
    intermediateCond.setAttribute("delay", "0");
    intermediateStCondLst.appendChild(intermediateCond);

    Element intermediateChildTnLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:childTnLst");
    intermediateCTn.appendChild(intermediateChildTnLst);

    return intermediateContainer;
  }

  /**
   * Append a child element to a click trigger's childTnLst.
   */
  private void appendToClickTrigger(Element clickNode, Element child) throws XMLParsingException {
    try {
      Element clickChildTnLst = (Element) xpath.evaluate("./p:cTn/p:childTnLst", clickNode, XPathConstants.NODE);
      if (clickChildTnLst == null) {
        Element cTn = (Element) xpath.evaluate("./p:cTn", clickNode, XPathConstants.NODE);
        clickChildTnLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:childTnLst");
        cTn.appendChild(clickChildTnLst);
      }
      clickChildTnLst.appendChild(child);
    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to append to click trigger", e);
    }
  }

  private Element createParagraphRangeAnimationEffect(int targetSpid, String animationType, String transition,
      String filter, String duration, String delay, String animationGroup, int clickTrigger,
      int paragraphStart, int paragraphEnd) {
    Element par = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:par");

    int parentNodeId = getNextTimingNodeId();
    Element cTn = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:cTn");
    cTn.setAttribute("id", String.valueOf(parentNodeId));
    cTn.setAttribute("presetID", "10");
    cTn.setAttribute("presetClass", "in".equals(transition) ? "entr" : "exit");
    cTn.setAttribute("presetSubtype", "0");
    cTn.setAttribute("fill", "hold");
    cTn.setAttribute("grpId", String.valueOf(getAnimationGroupId(animationGroup)));

    String nodeType = determineNodeType(animationGroup, transition, String.valueOf(clickTrigger));
    cTn.setAttribute("nodeType", nodeType);
    par.appendChild(cTn);

    Element stCondLst = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:stCondLst");
    cTn.appendChild(stCondLst);

    Element cond = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:cond");
    cond.setAttribute("delay", delay != null ? delay : "0");
    stCondLst.appendChild(cond);

    Element childTnLst = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:childTnLst");
    cTn.appendChild(childTnLst);

    String durationMs = "500";

    if ("in".equals(transition)) {
      Element setVisible = createParagraphRangeVisibilitySet(targetSpid, "visible", "0", paragraphStart, paragraphEnd);
      childTnLst.appendChild(setVisible);
    }

    Element animEffect = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:animEffect");
    animEffect.setAttribute("transition", transition);
    if (filter != null) {
      animEffect.setAttribute("filter", filter);
    }
    childTnLst.appendChild(animEffect);

    Element cBhvr = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:cBhvr");
    animEffect.appendChild(cBhvr);

    int behaviorNodeId = getNextTimingNodeId();
    Element behaviorCTn = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:cTn");
    behaviorCTn.setAttribute("id", String.valueOf(behaviorNodeId));
    behaviorCTn.setAttribute("dur", durationMs);
    cBhvr.appendChild(behaviorCTn);

    Element tgtEl = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:tgtEl");
    cBhvr.appendChild(tgtEl);

    Element spTgt = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:spTgt");
    spTgt.setAttribute("spid", String.valueOf(targetSpid));
    tgtEl.appendChild(spTgt);

    Element txEl = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:txEl");
    spTgt.appendChild(txEl);

    Element pRg = document.createElementNS("http://schemas.openxmlformats.org/presentationml/2006/main", "p:pRg");
    pRg.setAttribute("st", String.valueOf(paragraphStart));
    pRg.setAttribute("end", String.valueOf(paragraphEnd));
    txEl.appendChild(pRg);

    if ("out".equals(transition)) {
      int hideDelay = Integer.parseInt(durationMs) - 1;
      Element setHidden = createParagraphRangeVisibilitySet(targetSpid, "hidden", String.valueOf(hideDelay), paragraphStart, paragraphEnd);
      childTnLst.appendChild(setHidden);
    }

    return par;
  }

  // ========== VISIBILITY ==========

  private Element createParagraphRangeVisibilitySet(int targetSpid, String visibility, String delay,
      int paragraphStart, int paragraphEnd) {
    Element set = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:set");

    Element cBhvr = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cBhvr");
    set.appendChild(cBhvr);

    Element cTn = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cTn");
    cTn.setAttribute("id", String.valueOf(getNextTimingNodeId()));
    cTn.setAttribute("dur", "1");
    cTn.setAttribute("fill", "hold");
    cBhvr.appendChild(cTn);

    Element stCondLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:stCondLst");
    cTn.appendChild(stCondLst);

    Element cond = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:cond");
    cond.setAttribute("delay", delay);
    stCondLst.appendChild(cond);

    Element tgtEl = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:tgtEl");
    cBhvr.appendChild(tgtEl);

    Element spTgt = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:spTgt");
    spTgt.setAttribute("spid", String.valueOf(targetSpid));
    tgtEl.appendChild(spTgt);

    Element txEl = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:txEl");
    spTgt.appendChild(txEl);

    Element pRg = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:pRg");
    pRg.setAttribute("st", String.valueOf(paragraphStart));
    pRg.setAttribute("end", String.valueOf(paragraphEnd));
    txEl.appendChild(pRg);

    Element attrNameLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:attrNameLst");
    cBhvr.appendChild(attrNameLst);

    Element attrName = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:attrName");
    attrName.setTextContent("style.visibility");
    attrNameLst.appendChild(attrName);

    Element to = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:to");
    set.appendChild(to);

    Element strVal = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:strVal");
    strVal.setAttribute("val", visibility);
    to.appendChild(strVal);

    return set;
  }

  // ========== BUILD LIST ==========

  private void addToBuildList(Element timingElement, Element buildEntry) throws XMLParsingException {
    try {
      Element bldLst = (Element) xpath.evaluate("./p:bldLst", timingElement, XPathConstants.NODE);
      if (bldLst == null) {
        bldLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:bldLst");
        timingElement.appendChild(bldLst);
      }

      bldLst.appendChild(buildEntry);

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to add build list entry", e);
    }
  }

  private void addToBuildList(Element timingElement, int targetSpid, int grpId, String animationGroup) throws XMLParsingException {
    try {
      Element bldLst = (Element) xpath.evaluate("./p:bldLst", timingElement, XPathConstants.NODE);
      if (bldLst == null) {
        bldLst = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:bldLst");
        timingElement.appendChild(bldLst);
      }

      String buildType = determineBuildType(animationGroup);
      createBuildEntryIfNeeded(bldLst, targetSpid, grpId, buildType);

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to add build list entry", e);
    }
  }

  private String determineBuildType(String animationGroup) {
    return null;
  }

  private void createBuildEntryIfNeeded(Element bldLst, int targetSpid, int groupId, String buildType)
      throws XPathExpressionException {
    NodeList existingBuilds = (NodeList) xpath.evaluate(
      "./p:bldP[@spid='" + targetSpid + "' and @grpId='" + groupId + "']",
      bldLst, XPathConstants.NODESET);

    if (existingBuilds.getLength() == 0) {
      Element bldP = document.createElementNS(XMLConstants.PRESENTATION_NS, "p:bldP");
      bldP.setAttribute("spid", String.valueOf(targetSpid));
      bldP.setAttribute("grpId", String.valueOf(groupId));
      if (buildType != null) {
        bldP.setAttribute("build", buildType);
      }
      bldLst.appendChild(bldP);
    }
  }

  // ========== TIMING NODE IDS ==========

  private int getNextTimingNodeId() {
    return nextTimingNodeId++;
  }

  // ========== ANIMATION GROUP IDS ==========

  private int getAnimationGroupId(String animationGroup) {
    if ("on-click".equals(animationGroup)) {
      currentAnimationGroupId = nextAnimationGroupId++;
    }
    return currentAnimationGroupId;
  }

  private String determineNodeType(String animationGroup, String transition, String clickTrigger) {
    if ("with-previous".equals(animationGroup)) {
      return "withEffect";
    } else if ("after-previous".equals(animationGroup)) {
      return "afterEffect";
    } else {
      int animationCount = animationsPerClick.getOrDefault(clickTrigger, 0);
      animationsPerClick.put(clickTrigger, animationCount + 1);

      if (animationCount == 0) {
        return "clickEffect";
      } else {
        return "withEffect";
      }
    }
  }

}
