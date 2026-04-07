package com.excudo.core.utils;

/**
 * Enumeration of log levels in order of severity
 */
public enum LogLevel {
    DEBUG(0, "DEBUG"),
    INFO(1, "INFO"),
    WARN(2, "WARN"),
    ERROR(3, "ERROR");
    
    private final int level;
    private final String name;
    
    LogLevel(int level, String name) {
        this.level = level;
        this.name = name;
    }
    
    public int getLevel() {
        return level;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * Check if this level should be logged given the minimum log level
     */
    public boolean shouldLog(LogLevel minLevel) {
        return this.level >= minLevel.level;
    }
    
    /**
     * Parse log level from string (case insensitive)
     */
    public static LogLevel fromString(String levelStr) {
        if (levelStr == null) return INFO;
        
        try {
            return valueOf(levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO; // Default to INFO if invalid
        }
    }
}