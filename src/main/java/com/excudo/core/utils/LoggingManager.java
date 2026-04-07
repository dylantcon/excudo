package com.excudo.core.utils;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;

/**
 * Centralized logging manager for Excudo
 * Provides hierarchical, file-based logging with component separation
 */
public class LoggingManager {
    
    private static volatile LoggingManager instance;
    private static final Object lock = new Object();
    
    // Configuration
    private LogLevel minLogLevel = LogLevel.INFO;
    private boolean enableConsoleOutput = true;
    private boolean enableFileOutput = true;
    private Path logDirectory;
    private final DateTimeFormatter timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // Component loggers
    private final ConcurrentMap<String, ComponentLogger> componentLoggers = new ConcurrentHashMap<>();
    
    // File writers
    private PrintWriter mainLogWriter;
    private final Map<String, PrintWriter> componentWriters = new ConcurrentHashMap<>();
    
    private LoggingManager() {
        initializeLogging();
    }
    
    /**
     * Get singleton instance of LoggingManager
     */
    public static LoggingManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new LoggingManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize logging system
     */
    private void initializeLogging() {
        try {
            // Create logs directory structure
            logDirectory = Paths.get("logs");
            Files.createDirectories(logDirectory);
            Files.createDirectories(logDirectory.resolve("debug"));
            Files.createDirectories(logDirectory.resolve("tests"));
            
            // Initialize main log file
            if (enableFileOutput) {
                Path mainLogFile = logDirectory.resolve("main.log");
                mainLogWriter = new PrintWriter(new BufferedWriter(new FileWriter(mainLogFile.toFile(), true)));
            }
            
            // Set minimum log level from environment
            String envLogLevel = System.getProperty("pc.log.level", System.getenv("PC_LOG_LEVEL"));
            if (envLogLevel != null) {
                minLogLevel = LogLevel.fromString(envLogLevel);
            }
            
            // Console output setting
            String envConsole = System.getProperty("pc.log.console", System.getenv("PC_LOG_CONSOLE"));
            if ("false".equalsIgnoreCase(envConsole)) {
                enableConsoleOutput = false;
            }
            
            // Log startup
            log("SYSTEM", LogLevel.INFO, "LoggingManager initialized - level: {}, console: {}, file: {}", 
                minLogLevel.getName(), enableConsoleOutput, enableFileOutput);
                
        } catch (IOException e) {
            System.err.println("LOGGING INIT ERROR: Failed to initialize logging system: " + e.getMessage());
            enableFileOutput = false;
        }
    }
    
    /**
     * Get or create a component logger
     */
    public ComponentLogger getLogger(String component) {
        return componentLoggers.computeIfAbsent(component, this::createComponentLogger);
    }
    
    /**
     * Create a new component logger
     */
    private ComponentLogger createComponentLogger(String component) {
        try {
            PrintWriter componentWriter = null;
            if (enableFileOutput) {
                Path componentLogFile = logDirectory.resolve("debug").resolve(component.toLowerCase() + ".log");
                componentWriter = new PrintWriter(new BufferedWriter(new FileWriter(componentLogFile.toFile(), true)));
                componentWriters.put(component, componentWriter);
            }
            return new ComponentLogger(component, this, componentWriter);
        } catch (IOException e) {
            System.err.println("LOGGING ERROR: Failed to create logger for component " + component + ": " + e.getMessage());
            return new ComponentLogger(component, this, null);
        }
    }
    
    /**
     * Internal logging method
     */
    synchronized void log(String component, LogLevel level, String message, Object... args) {
        if (!level.shouldLog(minLogLevel)) {
            return;
        }
        
        // Format message
        String formattedMessage = args.length > 0 ? formatMessage(message, args) : message;
        String timestamp = LocalDateTime.now().format(timestampFormat);
        String logEntry = String.format("[%s] %s [%s] %s", timestamp, level.getName(), component, formattedMessage);
        
        // Console output (always stderr -- stdout may be a data channel e.g. MCP JSON-RPC)
        if (enableConsoleOutput) {
            System.err.println(logEntry);
        }
        
        // Main log file
        if (enableFileOutput && mainLogWriter != null) {
            mainLogWriter.println(logEntry);
            mainLogWriter.flush();
        }
        
        // Component-specific log file
        if (enableFileOutput) {
            PrintWriter componentWriter = componentWriters.get(component);
            if (componentWriter != null) {
                componentWriter.println(logEntry);
                componentWriter.flush();
            }
        }
    }
    
    /**
     * Format message with arguments (simple {} placeholder replacement)
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
     * Set minimum log level
     */
    public void setMinLogLevel(LogLevel level) {
        this.minLogLevel = level;
        log("SYSTEM", LogLevel.INFO, "Log level changed to {}", level.getName());
    }
    
    /**
     * Enable/disable console output
     */
    public void setConsoleOutput(boolean enabled) {
        this.enableConsoleOutput = enabled;
        log("SYSTEM", LogLevel.INFO, "Console output {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Flush all log writers
     */
    public void flush() {
        if (mainLogWriter != null) {
            mainLogWriter.flush();
        }
        componentWriters.values().forEach(PrintWriter::flush);
    }
    
    /**
     * Shutdown logging system
     */
    public void shutdown() {
        log("SYSTEM", LogLevel.INFO, "LoggingManager shutting down");
        
        if (mainLogWriter != null) {
            mainLogWriter.close();
        }
        
        componentWriters.values().forEach(PrintWriter::close);
        componentWriters.clear();
    }
    
    /**
     * Get current log directory
     */
    public Path getLogDirectory() {
        return logDirectory;
    }
    
    /**
     * Get current minimum log level
     */
    public LogLevel getMinLogLevel() {
        return minLogLevel;
    }
}