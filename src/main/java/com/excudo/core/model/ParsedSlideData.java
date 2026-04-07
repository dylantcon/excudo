package com.excudo.core.model;

import java.util.List;

/**
 * Container for all parsed data from a slide XML
 */
public class ParsedSlideData {
  private final ShapeRegistry shapeRegistry;
  private final TimingTree timingTree;
  private final List<AnimationBinding> animationBindings;
  private final String layoutId;

  public ParsedSlideData(ShapeRegistry shapeRegistry, TimingTree timingTree,
      List<AnimationBinding> animationBindings) {
    this(shapeRegistry, timingTree, animationBindings, null);
  }

  public ParsedSlideData(ShapeRegistry shapeRegistry, TimingTree timingTree,
      List<AnimationBinding> animationBindings, String layoutId) {
    this.shapeRegistry = shapeRegistry;
    this.timingTree = timingTree;
    this.animationBindings = animationBindings;
    this.layoutId = layoutId;
  }

  public ShapeRegistry getShapeRegistry() { return shapeRegistry; }
  public TimingTree getTimingTree() { return timingTree; }
  public List<AnimationBinding> getAnimationBindings() { return animationBindings; }
  public String getLayoutId() { return layoutId; }

  /**
   * Count total words across all text-bearing shapes on this slide.
   */
  public int getWordCount() {
    int count = 0;
    for (SlideShape shape : shapeRegistry.getAllShapes()) {
      if (shape.hasText() && shape.getTextContent() != null) {
        String text = shape.getTextContent().trim();
        if (!text.isEmpty()) {
          count += text.split("\\s+").length;
        }
      }
    }
    return count;
  }

  /**
   * Count total paragraphs (with text) across all shapes on this slide.
   */
  public int getParagraphCount() {
    int count = 0;
    for (SlideShape shape : shapeRegistry.getAllShapes()) {
      int pc = shape.getParagraphCount();
      if (pc > 0) {
        count += pc;
      }
    }
    return count;
  }

  @Override
  public String toString() {
    return String.format("ParsedSlideData{shapes=%d, timingNodes=%d, animations=%d}",
        shapeRegistry.getShapeCount(),
        timingTree.getNodeCount(),
        animationBindings.size());
  }
}
