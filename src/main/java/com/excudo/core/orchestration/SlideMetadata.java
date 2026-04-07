package com.excudo.core.orchestration;

import java.util.List;

/**
 * Metadata about a specific slide
 */
public class SlideMetadata {
    private final int slideNumber;
    private final String title;
    private final SlideType type;
    private final int shapeCount;
    private final List<Integer> spids;
    private final int animationCount;
    private final String layoutName;
    
    public SlideMetadata(int slideNumber, String title, SlideType type, int shapeCount, List<Integer> spids) {
        this(slideNumber, title, type, shapeCount, spids, 0, "Unknown Layout");
    }
    
    public SlideMetadata(int slideNumber, String title, SlideType type, int shapeCount, List<Integer> spids, int animationCount, String layoutName) {
        this.slideNumber = slideNumber;
        this.title = title;
        this.type = type;
        this.shapeCount = shapeCount;
        this.spids = List.copyOf(spids);
        this.animationCount = animationCount;
        this.layoutName = layoutName;
    }
    
    public int getSlideNumber() { return slideNumber; }
    public String getTitle() { return title; }
    public SlideType getType() { return type; }
    public int getShapeCount() { return shapeCount; }
    public List<Integer> getSpids() { return spids; }
    public int getAnimationCount() { return animationCount; }
    public String getLayoutName() { return layoutName; }
    public String getLayout() { return layoutName; } // Alias for getLayoutName
}