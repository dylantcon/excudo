package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.xpath.*;
import java.io.*;
import com.excudo.core.model.*;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.core.utils.OOXMLAttributeOrder;
import com.excudo.exceptions.*;
import com.excudo.xml.shapes.ShapeFactoryRegistry;
import com.excudo.core.animations.AnimationFactoryRegistry;
import com.excudo.xml.writers.animations.GroupIdManager;
import com.excudo.xml.writers.animations.SequentialGroupIdManager;

/**
 * Facade for injecting shapes, animations, and content into PowerPoint slides.
 * Delegates shape operations to ShapeWriter and animation operations to AnimationInjector.
 */
public class SlideXMLWriter implements com.excudo.xml.writers.animations.TimingNodeIdGenerator {

  private final Document document;
  private final ShapeWriter shapeWriter;
  private final AnimationInjector animationInjector;

  public SlideXMLWriter(Document document) throws XMLParsingException {
    this(document, null);
  }

  public SlideXMLWriter(Document document, SPIDManager spidManager) throws XMLParsingException {
    this(document, spidManager, scanGroupIds(document));
  }

  private static GroupIdManager scanGroupIds(Document document) {
    try {
      XPath xp = XMLFactoryProvider.createXPath();
      NodeList grpIds = (NodeList) xp.evaluate("//p:cTn/@grpId", document, XPathConstants.NODESET);
      int maxGrpId = -1;
      for (int i = 0; i < grpIds.getLength(); i++) {
        try {
          int id = Integer.parseInt(grpIds.item(i).getNodeValue());
          maxGrpId = Math.max(maxGrpId, id);
        } catch (NumberFormatException e) {
          // skip non-numeric
        }
      }
      return new SequentialGroupIdManager(maxGrpId + 1);
    } catch (XPathExpressionException e) {
      return new SequentialGroupIdManager(0);
    }
  }

  public SlideXMLWriter(Document document, SPIDManager spidManager,
                       GroupIdManager groupIdManager) throws XMLParsingException {
    this.document = document;

    try {
      XPath xpath = XMLFactoryProvider.createXPath();

      Element shapeTree = (Element) xpath.evaluate("//p:spTree", document, XPathConstants.NODE);
      if (shapeTree == null) {
        throw new XMLParsingException("No shape tree found in slide document");
      }

      ShapeFactoryRegistry shapeFactoryRegistry = new ShapeFactoryRegistry();
      AnimationFactoryRegistry animationFactoryRegistry = new AnimationFactoryRegistry();

      this.shapeWriter = new ShapeWriter(document, xpath, shapeTree, spidManager, shapeFactoryRegistry);
      this.animationInjector = new AnimationInjector(document, xpath, animationFactoryRegistry, groupIdManager);

    } catch (XPathExpressionException e) {
      throw new XMLParsingException("Failed to initialize XML writer", e);
    }
  }

  // ========== SHAPE OPERATIONS (delegate to ShapeWriter) ==========

  public int injectBasicShapeWithSlideContext(SlideShape.ShapeType shapeType, ShapeGeometry geometry,
      String text, String name, int slideNumber) throws XMLParsingException {
    return shapeWriter.injectBasicShapeWithSlideContext(shapeType, geometry, text, name, slideNumber);
  }

  public int injectBasicShapeWithSlideContext(SlideShape.ShapeType shapeType, ShapeGeometry geometry,
      String text, String name, int slideNumber, ShapeStyle style) throws XMLParsingException {
    return shapeWriter.injectBasicShapeWithSlideContext(shapeType, geometry, text, name, slideNumber, style);
  }

  public void updateShapeText(int spid, String newText) throws XMLParsingException {
    shapeWriter.updateShapeText(spid, newText);
  }

  public void updateShapeGeometry(int spid, ShapeGeometry newGeometry) throws XMLParsingException {
    shapeWriter.updateShapeGeometry(spid, newGeometry);
  }

  public void updateShapeName(int spid, String newName) throws XMLParsingException {
    shapeWriter.updateShapeName(spid, newName);
  }

  public void updateShapeTextBoxFlag(int spid, boolean flag) throws XMLParsingException {
    shapeWriter.updateShapeTextBoxFlag(spid, flag);
  }

  public void updateRunFormat(int spid, int paragraphIdx, int runIdx,
                              com.excudo.core.model.TextRun newRun) throws XMLParsingException {
    shapeWriter.updateRunFormat(spid, paragraphIdx, runIdx, newRun);
  }

  public void addToGroup(int groupSpid, int childSpid) throws XMLParsingException {
    shapeWriter.addToGroup(groupSpid, childSpid);
  }

  public void detachFromGroup(int childSpid) throws XMLParsingException {
    shapeWriter.detachFromGroup(childSpid);
  }

  public void reorderShape(int spid, String operation) throws XMLParsingException {
    shapeWriter.reorderShape(spid, operation);
  }

  public ShapeRemovalResult removeShapeBySpid(int spid) throws XMLParsingException {
    return shapeWriter.removeShapeBySpid(spid);
  }

  public org.w3c.dom.Element findShapeBySpid(int spid) throws XMLParsingException {
    try {
      return shapeWriter.findShapeBySpid(spid);
    } catch (javax.xml.xpath.XPathExpressionException e) {
      throw new XMLParsingException("Failed to find shape with SPID " + spid, e);
    }
  }

  public void restoreShape(org.w3c.dom.Element element) throws XMLParsingException {
    shapeWriter.restoreShape(element);
  }

  public void addPictureShape(int spid, String name, String relationshipId, ShapeGeometry geometry) throws XMLParsingException {
    shapeWriter.addPictureShape(spid, name, relationshipId, geometry);
  }

  public void updateBodyProperties(int spid, com.excudo.core.model.BodyProperties bodyProperties, boolean textBox) throws XMLParsingException {
    shapeWriter.updateBodyProperties(spid, bodyProperties, textBox);
  }

  public void injectHyperlink(int spid, String actionType, String soundRId, String soundName) throws XMLParsingException {
    shapeWriter.injectHyperlink(spid, actionType, soundRId, soundName);
  }

  public void replaceTextBody(int spid, com.excudo.core.model.TextBody textBody) throws XMLParsingException {
    shapeWriter.replaceTextBody(spid, textBody);
  }

  public void updateShapeTextProperties(int spid, java.util.Map<String, Object> fontProps)
      throws XMLParsingException {
    shapeWriter.updateShapeTextProperties(spid, fontProps);
  }

  public void updateShapeStyle(int spid, com.excudo.core.model.ShapeStyle style)
      throws XMLParsingException {
    shapeWriter.updateShapeStyle(spid, style);
  }

  public int duplicateShape(int spid, long offsetX, long offsetY) throws XMLParsingException {
    return shapeWriter.duplicateShape(spid, offsetX, offsetY);
  }

  public int injectConnectorShape(String connectorType, ShapeGeometry geometry,
      String headEnd, String tailEnd, String lineColor, String lineStyle,
      Integer startSpid, Integer startIdx, Integer endSpid, Integer endIdx,
      String customPath, int slideNumber) throws XMLParsingException {
    return shapeWriter.injectConnectorShape(connectorType, geometry, headEnd, tailEnd, lineColor, lineStyle,
        startSpid, startIdx, endSpid, endIdx, customPath, slideNumber);
  }

  public int groupShapes(java.util.List<Integer> spids) throws XMLParsingException {
    return shapeWriter.groupShapes(spids);
  }

  public java.util.List<Integer> ungroupShape(int groupSpid) throws XMLParsingException {
    return shapeWriter.ungroupShape(groupSpid);
  }

  public void copyShapeStyle(int sourceSpid, java.util.List<Integer> targetSpids) throws XMLParsingException {
    shapeWriter.copyShapeStyle(sourceSpid, targetSpids);
  }

  // ========== ANIMATION OPERATIONS (delegate to AnimationInjector) ==========

  public void injectAnimation(AnimationBinding binding, ShapeGeometry geometry) throws XMLParsingException {
    animationInjector.injectAnimation(binding, geometry);
  }

  public void injectParagraphRangeAnimation(int targetSpid, String animationType, String transition,
      String filter, String duration, String delay, int clickTrigger, String animationGroup,
      int paragraphStart, int paragraphEnd) throws XMLParsingException {
    animationInjector.injectParagraphRangeAnimation(targetSpid, animationType, transition, filter, duration, delay, clickTrigger, animationGroup, paragraphStart, paragraphEnd);
  }

  public AnimationInjector.AnimationRemovalResult removeAnimation(int timingNodeId) throws XMLParsingException {
    return animationInjector.removeAnimation(timingNodeId);
  }

  public void updateAnimationProperties(int timingNodeId, java.util.Map<String, String> properties)
      throws XMLParsingException {
    animationInjector.updateAnimationProperties(timingNodeId, properties);
  }

  public int createNewClickTrigger() throws XMLParsingException {
    return animationInjector.createNewClickTriggerPublic();
  }

  public void resetAnimationGroupIds() {
    animationInjector.resetAnimationGroupIds();
  }

  // ========== SHARED OPERATIONS ==========

  /**
   * Write the modified document to a file
   */
  public void writeXML(File outputFile) throws XMLParsingException {
    try {
      OOXMLAttributeOrder.serialize(document, outputFile);
    } catch (java.io.IOException e) {
      throw new XMLParsingException("Failed to write XML to file", e);
    }
  }

  @Override
  public int getNextId() {
    return animationInjector.getNextId();
  }

  // ========== INNER CLASSES ==========

  /**
   * Result of shape removal operation containing information about what was removed.
   */
  public static class ShapeRemovalResult {
    private final boolean success;
    private final boolean isPicture;
    private final String relationshipId;
    private final int spid;

    public ShapeRemovalResult(boolean success, boolean isPicture, String relationshipId, int spid) {
      this.success = success;
      this.isPicture = isPicture;
      this.relationshipId = relationshipId;
      this.spid = spid;
    }

    public boolean isSuccess() { return success; }
    public boolean isPicture() { return isPicture; }
    public String getRelationshipId() { return relationshipId; }
    public int getSpid() { return spid; }

    public boolean hasMediaRelationship() {
      return isPicture && relationshipId != null && !relationshipId.trim().isEmpty();
    }
  }
}
