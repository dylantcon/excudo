package com.excudo.core.utils;

import java.io.PrintWriter;

/**
 * Component-specific logger that provides convenient logging methods
 * for different subsystems of Excudo
 */
public class ComponentLogger {
    
    private final String componentName;
    private final LoggingManager loggingManager;
    private final PrintWriter componentWriter;
    
    ComponentLogger(String componentName, LoggingManager loggingManager, PrintWriter componentWriter) {
        this.componentName = componentName;
        this.loggingManager = loggingManager;
        this.componentWriter = componentWriter;
    }
    
    /**
     * Log debug message
     */
    public void debug(String message, Object... args) {
        loggingManager.log(componentName, LogLevel.DEBUG, message, args);
    }
    
    /**
     * Log info message
     */
    public void info(String message, Object... args) {
        loggingManager.log(componentName, LogLevel.INFO, message, args);
    }
    
    /**
     * Log warning message
     */
    public void warn(String message, Object... args) {
        loggingManager.log(componentName, LogLevel.WARN, message, args);
    }
    
    /**
     * Log error message
     */
    public void error(String message, Object... args) {
        loggingManager.log(componentName, LogLevel.ERROR, message, args);
    }
    
    /**
     * Log error with exception
     */
    public void error(String message, Throwable throwable, Object... args) {
        String formattedMessage = args.length > 0 ? formatMessage(message, args) : message;
        loggingManager.log(componentName, LogLevel.ERROR, formattedMessage + " - Exception: " + throwable.getMessage());
        
        // If we have detailed logging enabled, print stack trace to component log
        if (loggingManager.getMinLogLevel() == LogLevel.DEBUG && componentWriter != null) {
            throwable.printStackTrace(componentWriter);
            componentWriter.flush();
        }
    }
    
    /**
     * Check if debug logging is enabled
     */
    public boolean isDebugEnabled() {
        return LogLevel.DEBUG.shouldLog(loggingManager.getMinLogLevel());
    }
    
    /**
     * Check if info logging is enabled
     */
    public boolean isInfoEnabled() {
        return LogLevel.INFO.shouldLog(loggingManager.getMinLogLevel());
    }
    
    /**
     * Get the component name
     */
    public String getComponentName() {
        return componentName;
    }
    
    /**
     * Format message with simple {} placeholder replacement
     */
    private String formatMessage(String message, Object... args) {
        String result = message;
        for (Object arg : args) {
            int index = result.indexOf("{}");
            if (index != -1) {
                result = result.substring(0, index) + String.valueOf(arg) + result.substring(index + 2);
            }
        }
        return result;
    }
    
    /**
     * Create a sub-logger for more specific operations within this component
     */
    public ComponentLogger subLogger(String subComponent) {
        return loggingManager.getLogger(componentName + "." + subComponent);
    }
}