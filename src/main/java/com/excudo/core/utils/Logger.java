package com.excudo.core.utils;

/**
 * Static utility class for easy access to component loggers
 * Provides factory methods for common logging patterns
 */
public final class Logger {
    
    // Common component names
    public static final String RENDERING = "Rendering";
    public static final String LLM = "LLM";
    public static final String XML = "XML";
    public static final String UI = "UI";
    public static final String SPID = "SPID";
    public static final String THEME = "Theme";
    public static final String ANIMATION = "Animation";
    public static final String CONSOLE = "Console";
    public static final String CLI = "CLI";
    public static final String TEST = "Test";
    public static final String SYSTEM = "System";
    
    private Logger() {
        // Utility class - no instantiation
    }
    
    /**
     * Get logger for the calling class
     */
    public static ComponentLogger getLogger(Class<?> clazz) {
        return LoggingManager.getInstance().getLogger(getComponentFromClass(clazz));
    }
    
    /**
     * Get logger for specific component
     */
    public static ComponentLogger getLogger(String component) {
        return LoggingManager.getInstance().getLogger(component);
    }
    
    /**
     * Get rendering logger
     */
    public static ComponentLogger rendering() {
        return LoggingManager.getInstance().getLogger(RENDERING);
    }
    
    /**
     * Get LLM logger
     */
    public static ComponentLogger llm() {
        return LoggingManager.getInstance().getLogger(LLM);
    }
    
    /**
     * Get XML logger
     */
    public static ComponentLogger xml() {
        return LoggingManager.getInstance().getLogger(XML);
    }
    
    /**
     * Get UI logger
     */
    public static ComponentLogger ui() {
        return LoggingManager.getInstance().getLogger(UI);
    }
    
    /**
     * Get SPID logger
     */
    public static ComponentLogger spid() {
        return LoggingManager.getInstance().getLogger(SPID);
    }
    
    /**
     * Get theme logger
     */
    public static ComponentLogger theme() {
        return LoggingManager.getInstance().getLogger(THEME);
    }
    
    /**
     * Get animation logger
     */
    public static ComponentLogger animation() {
        return LoggingManager.getInstance().getLogger(ANIMATION);
    }
    
    /**
     * Get console logger
     */
    public static ComponentLogger console() {
        return LoggingManager.getInstance().getLogger(CONSOLE);
    }
    
    /**
     * Get CLI logger
     */
    public static ComponentLogger cli() {
        return LoggingManager.getInstance().getLogger(CLI);
    }
    
    /**
     * Get test logger
     */
    public static ComponentLogger test() {
        return LoggingManager.getInstance().getLogger(TEST);
    }
    
    /**
     * Get system logger
     */
    public static ComponentLogger system() {
        return LoggingManager.getInstance().getLogger(SYSTEM);
    }
    
    /**
     * Determine component name from class package structure
     */
    private static String getComponentFromClass(Class<?> clazz) {
        String packageName = clazz.getPackage().getName();
        
        // Map package names to component names
        if (packageName.contains(".rendering")) {
            return RENDERING;
        } else if (packageName.contains(".llm")) {
            return LLM;
        } else if (packageName.contains(".xml")) {
            return XML;
        } else if (packageName.contains(".view") || packageName.contains(".components")) {
            return UI;
        } else if (packageName.contains(".animation")) {
            return ANIMATION;
        } else if (packageName.contains(".themes")) {
            return THEME;
        } else if (packageName.contains(".console")) {
            return CONSOLE;
        } else if (packageName.contains(".cli")) {
            return CLI;
        } else if (packageName.contains(".test")) {
            return TEST;
        } else {
            // Use simple class name for other packages
            return clazz.getSimpleName();
        }
    }
    
    /**
     * Set global log level
     */
    public static void setLevel(LogLevel level) {
        LoggingManager.getInstance().setMinLogLevel(level);
    }
    
    /**
     * Set global log level from string
     */
    public static void setLevel(String levelStr) {
        LoggingManager.getInstance().setMinLogLevel(LogLevel.fromString(levelStr));
    }
    
    /**
     * Enable/disable console output
     */
    public static void setConsoleOutput(boolean enabled) {
        LoggingManager.getInstance().setConsoleOutput(enabled);
    }
    
    /**
     * Flush all loggers
     */
    public static void flush() {
        LoggingManager.getInstance().flush();
    }
    
    /**
     * Shutdown logging system
     */
    public static void shutdown() {
        LoggingManager.getInstance().shutdown();
    }
}