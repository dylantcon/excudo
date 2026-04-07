package com.excudo.core.themes;

/**
 * Defines a placeholder shape within a slide layout.
 * Maps to p:sp with p:ph (placeholder) properties and a:xfrm geometry.
 */
public final class PlaceholderDefinition {

    private final String type;
    private final Integer idx;
    private final long x;
    private final long y;
    private final long cx;
    private final long cy;
    private final TextLevelStyle[] lstStyleOverrides;

    public PlaceholderDefinition(String type, Integer idx, long x, long y, long cx, long cy) {
        this(type, idx, x, y, cx, cy, null);
    }

    public PlaceholderDefinition(String type, Integer idx, long x, long y, long cx, long cy,
                                  TextLevelStyle[] lstStyleOverrides) {
        this.type = type;
        this.idx = idx;
        this.x = x;
        this.y = y;
        this.cx = cx;
        this.cy = cy;
        this.lstStyleOverrides = lstStyleOverrides;
    }

    public String getType() { return type; }
    public Integer getIdx() { return idx; }
    public long getX() { return x; }
    public long getY() { return y; }
    public long getCx() { return cx; }
    public long getCy() { return cy; }
    public TextLevelStyle[] getLstStyleOverrides() { return lstStyleOverrides; }

    public boolean hasLstStyleOverrides() {
        return lstStyleOverrides != null && lstStyleOverrides.length > 0;
    }

    public boolean isWithinSlideBounds() {
        long slideWidth = 12192000L;
        long slideHeight = 6858000L;
        return x >= 0 && y >= 0 && (x + cx) <= slideWidth && (y + cy) <= slideHeight;
    }
}
