package com.excudo.core.model;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/**
 * Information about a PowerPoint slide layout.
 * Contains metadata about layout capabilities, placeholder types, SPID allocation patterns,
 * and geometry information for proper placeholder inheritance.
 */
public class LayoutInfo {
    private final String layoutId;
    private final String name;
    private final String filePath;
    private final boolean hasTitlePlaceholder;
    private final boolean hasContentPlaceholder;
    private final boolean hasSubtitlePlaceholder;
    private final int contentPlaceholderCount;
    private final String description;
    private final List<PlaceholderGeometry> placeholderGeometries;
    private final String titlePlaceholderType;

    public LayoutInfo(String layoutId, String name, String filePath,
                     boolean hasTitlePlaceholder, boolean hasContentPlaceholder,
                     boolean hasSubtitlePlaceholder, String description) {
        this(layoutId, name, filePath, hasTitlePlaceholder, hasContentPlaceholder,
             hasSubtitlePlaceholder, hasContentPlaceholder ? 1 : 0, description, Collections.emptyList(), null);
    }

    public LayoutInfo(String layoutId, String name, String filePath,
                     boolean hasTitlePlaceholder, boolean hasContentPlaceholder,
                     boolean hasSubtitlePlaceholder, int contentPlaceholderCount, String description) {
        this(layoutId, name, filePath, hasTitlePlaceholder, hasContentPlaceholder,
             hasSubtitlePlaceholder, contentPlaceholderCount, description, Collections.emptyList(), null);
    }

    public LayoutInfo(String layoutId, String name, String filePath,
                     boolean hasTitlePlaceholder, boolean hasContentPlaceholder,
                     boolean hasSubtitlePlaceholder, int contentPlaceholderCount,
                     String description, List<PlaceholderGeometry> placeholderGeometries) {
        this(layoutId, name, filePath, hasTitlePlaceholder, hasContentPlaceholder,
             hasSubtitlePlaceholder, contentPlaceholderCount, description, placeholderGeometries, null);
    }

    public LayoutInfo(String layoutId, String name, String filePath,
                     boolean hasTitlePlaceholder, boolean hasContentPlaceholder,
                     boolean hasSubtitlePlaceholder, int contentPlaceholderCount,
                     String description, List<PlaceholderGeometry> placeholderGeometries,
                     String titlePlaceholderType) {
        this.layoutId = layoutId;
        this.name = name;
        this.filePath = filePath;
        this.hasTitlePlaceholder = hasTitlePlaceholder;
        this.hasContentPlaceholder = hasContentPlaceholder;
        this.hasSubtitlePlaceholder = hasSubtitlePlaceholder;
        this.contentPlaceholderCount = contentPlaceholderCount;
        this.description = description;
        this.placeholderGeometries = Collections.unmodifiableList(new ArrayList<>(placeholderGeometries));
        this.titlePlaceholderType = titlePlaceholderType != null ? titlePlaceholderType : "title";
    }

    // Getters
    public String getLayoutId() { return layoutId; }
    public String getName() { return name; }
    public String getFilePath() { return filePath; }
    public boolean hasTitlePlaceholder() { return hasTitlePlaceholder; }
    public boolean hasContentPlaceholder() { return hasContentPlaceholder; }
    public boolean hasSubtitlePlaceholder() { return hasSubtitlePlaceholder; }
    public String getTitlePlaceholderType() { return titlePlaceholderType; }
    public int getContentPlaceholderCount() { return contentPlaceholderCount; }
    public String getDescription() { return description; }
    
    /**
     * Get all placeholder geometries parsed from the layout XML.
     * @return Unmodifiable list of placeholder geometries
     */
    public List<PlaceholderGeometry> getPlaceholderGeometries() { 
        return placeholderGeometries; 
    }
    
    /**
     * Get placeholder geometry by index (e.g., "1", "14").
     * @param index The placeholder index to search for
     * @return PlaceholderGeometry if found, null otherwise
     */
    public PlaceholderGeometry getPlaceholderGeometryByIndex(String index) {
        return placeholderGeometries.stream()
                .filter(geometry -> geometry.getPlaceholderIndex().equals(index))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get placeholder geometry by type (e.g., "ctrTitle", "subTitle", "title").
     * Type-based geometries are stored with key "type:X" in the placeholder index.
     * @param type The placeholder type to search for
     * @return PlaceholderGeometry if found, null otherwise
     */
    public PlaceholderGeometry getPlaceholderGeometryByType(String type) {
        return placeholderGeometries.stream()
                .filter(geometry -> geometry.getPlaceholderIndex().equals("type:" + type))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get a user-friendly display name for the LLM context
     */
    public String getDisplayName() {
        return name + " (" + layoutId + ")";
    }

    /**
     * Get placeholder summary for LLM context
     */
    public String getPlaceholderSummary() {
        StringBuilder summary = new StringBuilder();
        if (hasTitlePlaceholder) summary.append("title, ");
        if (hasContentPlaceholder) {
            if (contentPlaceholderCount > 1) {
                summary.append(contentPlaceholderCount).append(" content placeholders, ");
            } else {
                summary.append("content, ");
            }
        }
        if (hasSubtitlePlaceholder) summary.append("subtitle, ");
        
        String result = summary.toString();
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }
        return result.isEmpty() ? "none" : result;
    }
    
    /**
     * Calculate the first available SPID for user content based on layout placeholders.
     * Universal pattern: SPID 1 (group), SPID 2 (title if present), SPID 3+ (content placeholders), then user content
     */
    public int getFirstAvailableUserSpid() {
        int spid = 3; // Start after group (1) and title (2)
        
        // Skip past title placeholder if present (title always gets SPID 2)
        // Note: title is handled separately, we start counting from content placeholders
        
        // Skip past layout content placeholders
        spid += contentPlaceholderCount;
        
        return spid;
    }

    @Override
    public String toString() {
        return String.format("LayoutInfo{id='%s', name='%s', placeholders=[%s]}", 
                           layoutId, name, getPlaceholderSummary());
    }
}