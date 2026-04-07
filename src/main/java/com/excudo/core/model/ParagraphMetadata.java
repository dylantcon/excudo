package com.excudo.core.model;

import java.util.List;

/**
 * Metadata for individual paragraphs within a text shape
 * Supports bullet point indexing and content tracking
 */
public class ParagraphMetadata {
  private final List<String> paragraphContents;
  private final List<Boolean> isBulletPoint;
  private final List<String> bulletMarkers;

  public ParagraphMetadata(List<String> paragraphContents, List<Boolean> isBulletPoint, List<String> bulletMarkers) {
    if (paragraphContents.size() != isBulletPoint.size() || paragraphContents.size() != bulletMarkers.size()) {
      throw new IllegalArgumentException("All lists must have the same size");
    }
    this.paragraphContents = paragraphContents;
    this.isBulletPoint = isBulletPoint;
    this.bulletMarkers = bulletMarkers;
  }

  public List<String> getParagraphContents() { return paragraphContents; }
  public List<Boolean> getIsBulletPoint() { return isBulletPoint; }
  public List<String> getBulletMarkers() { return bulletMarkers; }

  public int getParagraphCount() { return paragraphContents.size(); }

  public String getParagraphContent(int index) {
    if (index < 0 || index >= paragraphContents.size()) {
      throw new IndexOutOfBoundsException("Paragraph index out of range: " + index);
    }
    return paragraphContents.get(index);
  }

  public boolean isParagraphBullet(int index) {
    if (index < 0 || index >= isBulletPoint.size()) {
      throw new IndexOutOfBoundsException("Paragraph index out of range: " + index);
    }
    return isBulletPoint.get(index);
  }

  public String getBulletMarker(int index) {
    if (index < 0 || index >= bulletMarkers.size()) {
      throw new IndexOutOfBoundsException("Paragraph index out of range: " + index);
    }
    return bulletMarkers.get(index);
  }

  /**
   * Get bullet points only (filtering out non-bullet paragraphs)
   */
  public List<String> getBulletPointsOnly() {
    return java.util.stream.IntStream.range(0, paragraphContents.size())
        .filter(this::isParagraphBullet)
        .mapToObj(this::getParagraphContent)
        .collect(java.util.stream.Collectors.toList());
  }

  /**
   * Get bullet point indices (zero-based paragraph indices that are bullets)
   */
  public List<Integer> getBulletPointIndices() {
    return java.util.stream.IntStream.range(0, paragraphContents.size())
        .filter(this::isParagraphBullet)
        .boxed()
        .collect(java.util.stream.Collectors.toList());
  }

  @Override
  public String toString() {
    return String.format("ParagraphMetadata{paragraphs=%d, bullets=%d}",
        getParagraphCount(),
        (int) isBulletPoint.stream().mapToInt(b -> b ? 1 : 0).sum());
  }
}