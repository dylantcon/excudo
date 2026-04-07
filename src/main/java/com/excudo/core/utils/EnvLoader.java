package com.excudo.core.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple .env file loader for loading environment variables from .env files
 */
public class EnvLoader {
    private static final ComponentLogger logger = Logger.getLogger(EnvLoader.class);
    private static final Map<String, String> envVars = new HashMap<>();
    private static boolean loaded = false;
    
    /**
     * Load environment variables from .env file in current directory
     */
    public static void load() {
        if (loaded) return;

        // Load from ~/.env first as a baseline (survives gitignore/clean)
        File homeEnv = new File(System.getProperty("user.home"), ".env");
        if (homeEnv.exists()) {
            loadFromFile(homeEnv);
        }

        // Project-local .env overrides home values
        File envFile = new File(".env");
        if (envFile.exists()) {
            loadFromFile(envFile);
        }

        // .env.local overrides both
        File envLocalFile = new File(".env.local");
        if (envLocalFile.exists()) {
            loadFromFile(envLocalFile);
        }

        loaded = true;
    }
    
    /**
     * Load environment variables from a specific file path.
     * Variables from this file override any previously loaded values.
     * @param path the path to the .env file
     * @return the number of variables loaded, or -1 if the file was not found
     */
    public static int loadFromPath(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return -1;
        }
        int before = envVars.size();
        loadFromFile(file);
        loaded = true;
        return envVars.size() - before;
    }

    /**
     * Load environment variables from a specific file
     */
    private static void loadFromFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip empty lines and comments
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse KEY=VALUE format
                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();
                    
                    // Remove quotes if present
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    envVars.put(key, value);
                }
            }
        } catch (IOException e) {
            logger.warn("Could not load .env file: {}", e.getMessage());
        }
    }
    
    /**
     * Get an environment variable value, checking .env file first, then system env
     */
    public static String get(String key) {
        load(); // Ensure loaded
        
        // Check loaded .env vars first
        if (envVars.containsKey(key)) {
            return envVars.get(key);
        }
        
        // Fall back to system environment
        return System.getenv(key);
    }
    
    /**
     * Check if an environment variable is set
     */
    public static boolean has(String key) {
        return get(key) != null;
    }
}