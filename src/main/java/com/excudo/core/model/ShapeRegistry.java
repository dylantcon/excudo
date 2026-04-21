package com.excudo.core.model;

import java.util.*;

/**
 * Registry of all shapes in a slide, indexed by spid for fast lookup
 */
public class ShapeRegistry {
  private final Map<Integer, SlideShape> shapesBySpid = new HashMap<>();
  private final List<SlideShape> allShapes = new ArrayList<>();

  // child SPID -> immediate parent GROUP SPID. Populated by the parser at
  // registerGroupChildren time so structural parentage survives the flat
  // registry. Consumers (tool formatters, animation target resolution)
  // read via getParentSpid / getNestingDepth rather than re-deriving from
  // shape geometry -- containment and parentage are different properties
  // in OOXML (valid children can extend past their group's bbox).
  private final Map<Integer, Integer> parentByChildSpid = new HashMap<>();

  public void addShape(SlideShape shape) {
    shapesBySpid.put(shape.getSpid(), shape);
    allShapes.add(shape);
  }

  /**
   * Record that {@code childSpid} is a direct child of a GROUP shape
   * with SPID {@code parentGroupSpid}. Called by the parser when
   * recursing into a p:grpSp.
   */
  public void registerGroupMembership(int childSpid, int parentGroupSpid) {
    parentByChildSpid.put(childSpid, parentGroupSpid);
  }

  /**
   * Return the immediate parent GROUP's SPID, or -1 if this shape is
   * at the top level of the slide's shape tree.
   */
  public int getParentSpid(int spid) {
    return parentByChildSpid.getOrDefault(spid, -1);
  }

  /**
   * Walk up the parent chain; returns 0 for top-level shapes, 1 for a
   * direct child of a group, 2 for a nested child, etc. Defensive
   * against accidental cycles (bounded at 16 levels, which is far beyond
   * any reasonable slide).
   */
  public int getNestingDepth(int spid) {
    int depth = 0;
    int cursor = getParentSpid(spid);
    while (cursor != -1 && depth < 16) {
      depth++;
      cursor = getParentSpid(cursor);
    }
    return depth;
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
