package com.excudo.core.themes;

import java.util.Collections;
import java.util.List;

/**
 * Defines a slide layout structure within a theme bundle.
 * Maps to a ppt/slideLayouts/slideLayoutN.xml file.
 */
public final class LayoutDefinition {

    public enum LayoutType {
        TITLE_SLIDE("Title Slide"),
        TITLE_CONTENT("Title, Content"),
        SECTION_HEADER("Section Header"),
        TWO_CONTENT("Two Content"),
        COMPARISON("Comparison"),
        TITLE_ONLY("Title Only"),
        BLANK("Blank"),
        CONTENT_CAPTION("Content with Caption"),
        PICTURE_CAPTION("Picture with Caption"),
        TITLE_VERTICAL("Title and Vertical Text");

        private final String displayName;

        LayoutType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    private final String layoutId;
    private final String matchingName;
    private final LayoutType layoutType;
    private final List<PlaceholderDefinition> placeholders;
    private final boolean preserve;

    public LayoutDefinition(String layoutId, String matchingName, LayoutType layoutType,
                            List<PlaceholderDefinition> placeholders, boolean preserve) {
        this.layoutId = layoutId;
        this.matchingName = matchingName;
        this.layoutType = layoutType;
        this.placeholders = placeholders != null ? Collections.unmodifiableList(placeholders) : Collections.emptyList();
        this.preserve = preserve;
    }

    public String getLayoutId() { return layoutId; }
    public String getMatchingName() { return matchingName; }
    public LayoutType getLayoutType() { return layoutType; }
    public List<PlaceholderDefinition> getPlaceholders() { return placeholders; }
    public boolean isPreserve() { return preserve; }

    public int getPlaceholderCount() { return placeholders.size(); }

    public boolean hasPlaceholderOfType(String type) {
        return placeholders.stream().anyMatch(p -> type.equals(p.getType()));
    }
}
