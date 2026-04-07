package com.excudo.core.orchestration;

/**
 * Types of slides that can be created
 */
public enum SlideType {
    /**
     * Blank slide with minimal content
     */
    BLANK,
    
    /**
     * Title slide with title and subtitle placeholders
     */
    TITLE,
    
    /**
     * Content slide with title and content area
     */
    CONTENT,
    
    /**
     * Two-column content slide
     */
    TWO_COLUMN,
    
    /**
     * Picture with caption slide
     */
    PICTURE_CAPTION,
    
    /**
     * Section header slide
     */
    SECTION_HEADER,
    
    /**
     * Slide created from a template
     */
    TEMPLATE,
    
    /**
     * Copied from another slide
     */
    COPIED
}