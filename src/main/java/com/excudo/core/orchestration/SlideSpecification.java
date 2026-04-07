package com.excudo.core.orchestration;

import java.util.Map;

/**
 * Specification for creating a new slide.
 * Used in batch operations to define slide properties.
 */
public class SlideSpecification {
    
    private final int position;
    private final String title;
    private final SlideType type;
    private final String templateName;
    private final Map<String, Object> properties;
    
    public SlideSpecification(int position, String title, SlideType type, 
                            String templateName, Map<String, Object> properties) {
        this.position = position;
        this.title = title;
        this.type = type;
        this.templateName = templateName;
        this.properties = properties != null ? Map.copyOf(properties) : Map.of();
    }
    
    public int getPosition() { return position; }
    public String getTitle() { return title; }
    public SlideType getType() { return type; }
    public String getTemplateName() { return templateName; }
    public Map<String, Object> getProperties() { return properties; }
    
    /**
     * Get a property value by key
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Builder for creating slide specifications
     */
    public static class Builder {
        private int position;
        private String title;
        private SlideType type = SlideType.BLANK;
        private String templateName;
        private Map<String, Object> properties = Map.of();
        
        public Builder position(int position) {
            this.position = position;
            return this;
        }
        
        public Builder title(String title) {
            this.title = title;
            return this;
        }
        
        public Builder type(SlideType type) {
            this.type = type;
            return this;
        }
        
        public Builder template(String templateName) {
            this.templateName = templateName;
            return this;
        }
        
        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }
        
        public SlideSpecification build() {
            return new SlideSpecification(position, title, type, templateName, properties);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public String toString() {
        return String.format("SlideSpec[pos=%d, title='%s', type=%s]", 
                           position, title, type);
    }
}