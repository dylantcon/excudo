package com.excudo.core.model;

import java.util.*;

/**
 * Registry of all shapes in a slide, indexed by spid for fast lookup
 */
public class ShapeRegistry {
  private final Map<Integer, SlideShape> shapesBySpid = new HashMap<>();
  private final List<SlideShape> allShapes = new ArrayList<>();

  public void addShape(SlideShape shape) {
    shapesBySpid.put(shape.getSpid(), shape);
    allShapes.add(shape);
  }

  public SlideShape getShape(int spid) {
    return shapesBySpid.get(spid);
  }
  
  public java.util.Optional<SlideShape> getShapeBySpid(String spid) {
    try {
      int spidInt = Integer.parseInt(spid);
      SlideShape shape = getShape(spidInt);
      return java.util.Optional.ofNullable(shape);
    } catch (NumberFormatException e) {
      return java.util.Optional.empty();
    }
  }

  public List<SlideShape> getAllShapes() {
    return new ArrayList<>(allShapes);
  }

  public List<SlideShape> getShapesByType(SlideShape.ShapeType type) {
    return allShapes.stream()
      .filter(shape -> shape.getType() == type)
      .toList();
  }

  public List<SlideShape> getTextShapes() {
    return allShapes.stream()
      .filter(SlideShape::hasText)
      .toList();
  }

  public int getShapeCount() { return allShapes.size(); }

  public Set<Integer> getAllSpids() { return new HashSet<>(shapesBySpid.keySet()); }

  @Override
  public String toString() {
    return String.format("ShapeRegistry{%d shapes, %d with text}",
        getShapeCount(), getTextShapes().size());
  }
}
