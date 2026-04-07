package com.excudo.core.orchestration;

import java.time.Instant;
import java.util.List;

/**
 * Metadata about the presentation
 */
public class PresentationMetadata {
    private final int slideCount;
    private final String title;
    private final Instant lastModified;
    private final List<String> authors;
    
    public PresentationMetadata(int slideCount, String title, Instant lastModified, List<String> authors) {
        this.slideCount = slideCount;
        this.title = title;
        this.lastModified = lastModified;
        this.authors = List.copyOf(authors);
    }
    
    public int getSlideCount() { return slideCount; }
    public String getTitle() { return title; }
    public Instant getLastModified() { return lastModified; }
    public List<String> getAuthors() { return authors; }
    public int getLayoutCount() { return 1; } // Basic implementation - could be made dynamic
    
    /**
     * Get the primary author (first author in the list)
     * @return Primary author or null if no authors
     */
    public String getAuthor() {
        return authors.isEmpty() ? null : authors.get(0);
    }
}