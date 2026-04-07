package com.excudo.core.mermaid.layout;

/**
 * Configuration for diagram layout positioning.
 * All values in EMUs (English Metric Units, 914400 per inch).
 */
public class LayoutConfig {
    private final long boundingX;
    private final long boundingY;
    private final long boundingWidth;
    private final long boundingHeight;
    private long nodeWidth;
    private long nodeHeight;
    private long horizontalGap;
    private long verticalGap;
    private long margin;

    /** Default node size: ~1.5 inches wide, ~0.75 inches tall */
    private static final long DEFAULT_NODE_WIDTH = 1371600;   // 1.5"
    private static final long DEFAULT_NODE_HEIGHT = 685800;   // 0.75"
    private static final long DEFAULT_H_GAP = 457200;         // 0.5"
    private static final long DEFAULT_V_GAP = 457200;         // 0.5"
    private static final long DEFAULT_MARGIN = 228600;        // 0.25"

    public LayoutConfig(long x, long y, long width, long height) {
        this.boundingX = x;
        this.boundingY = y;
        this.boundingWidth = width;
        this.boundingHeight = height;
        this.nodeWidth = DEFAULT_NODE_WIDTH;
        this.nodeHeight = DEFAULT_NODE_HEIGHT;
        this.horizontalGap = DEFAULT_H_GAP;
        this.verticalGap = DEFAULT_V_GAP;
        this.margin = DEFAULT_MARGIN;
    }

    /** Convenience: default content area of a standard slide */
    public static LayoutConfig slideContentArea() {
        return new LayoutConfig(838200, 1825625, 7772400, 4525963);
    }

    public long boundingX() { return boundingX; }
    public long boundingY() { return boundingY; }
    public long boundingWidth() { return boundingWidth; }
    public long boundingHeight() { return boundingHeight; }
    public long nodeWidth() { return nodeWidth; }
    public long nodeHeight() { return nodeHeight; }
    public long horizontalGap() { return horizontalGap; }
    public long verticalGap() { return verticalGap; }
    public long margin() { return margin; }

    public LayoutConfig nodeSize(long width, long height) {
        this.nodeWidth = width;
        this.nodeHeight = height;
        return this;
    }

    public LayoutConfig gaps(long hGap, long vGap) {
        this.horizontalGap = hGap;
        this.verticalGap = vGap;
        return this;
    }

    public LayoutConfig margin(long margin) {
        this.margin = margin;
        return this;
    }
}
