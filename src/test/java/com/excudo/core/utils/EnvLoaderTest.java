package com.excudo.core.utils;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;

/**
 * Unit tests for EnvLoader utility class
 * Tests .env file loading and environment variable access
 */
public class EnvLoaderTest {
    
    private File testEnvFile;
    private File testEnvLocalFile;
    
    @Before
    public void setUp() throws IOException {
        // Create temporary .env files for testing
        testEnvFile = new File(".env.test");
        testEnvLocalFile = new File(".env.local.test");
        
        // Clean up any existing test files
        cleanupTestFiles();
        
        // Reset EnvLoader state using reflection
        resetEnvLoader();
    }
    
    @After
    public void tearDown() {
        cleanupTestFiles();
        resetEnvLoader();
    }
    
    private void cleanupTestFiles() {
        if (testEnvFile != null && testEnvFile.exists()) {
            testEnvFile.delete();
        }
        if (testEnvLocalFile != null && testEnvLocalFile.exists()) {
            testEnvLocalFile.delete();
        }
        
        // Also clean up .env and .env.local if they exist from tests
        File envFile = new File(".env");
        File envLocalFile = new File(".env.local");
        if (envFile.exists()) envFile.delete();
        if (envLocalFile.exists()) envLocalFile.delete();
    }
    
    private void resetEnvLoader() {
        try {
            // Reset the loaded flag and envVars map using reflection
            java.lang.reflect.Field loadedField = EnvLoader.class.getDeclaredField("loaded");
            loadedField.setAccessible(true);
            loadedField.setBoolean(null, false);
            
            java.lang.reflect.Field envVarsField = EnvLoader.class.getDeclaredField("envVars");
            envVarsField.setAccessible(true);
            ((java.util.Map<?, ?>) envVarsField.get(null)).clear();
        } catch (Exception e) {
            // If reflection fails, tests may not be fully isolated
            System.err.println("Warning: Could not reset EnvLoader state");
        }
    }
    
    @Test
    public void testLoadBasicEnvFile() throws IOException {
        // Create a basic .env file
        String envContent = "API_KEY=test123\n" +
                           "DATABASE_URL=localhost:5432\n" +
                           "DEBUG=true\n";
        Files.write(Paths.get(".env"), envContent.getBytes());
        
        EnvLoader.load();
        
        assertEquals("Should load API_KEY", "test123", EnvLoader.get("API_KEY"));
        assertEquals("Should load DATABASE_URL", "localhost:5432", EnvLoader.get("DATABASE_URL"));
        assertEquals("Should load DEBUG", "true", EnvLoader.get("DEBUG"));
    }
    
    @Test
    public void testLoadWithQuotes() throws IOException {
        // Test quoted values
        String envContent = "QUOTED_DOUBLE=\"quoted value\"\n" +
                           "QUOTED_SINGLE='single quoted'\n" +
                           "UNQUOTED=no quotes\n";
        Files.write(Paths.get(".env"), envContent.getBytes());
        
        EnvLoader.load();
        
        assertEquals("Should remove double quotes", "quoted value", EnvLoader.get("QUOTED_DOUBLE"));
        assertEquals("Should remove single quotes", "single quoted", EnvLoader.get("QUOTED_SINGLE"));
        assertEquals("Should preserve unquoted", "no quotes", EnvLoader.get("UNQUOTED"));
    }
    
    @Test
    public void testLoadWithComments() throws IOException {
        // Test comments and empty lines
        String envContent = "# This is a comment\n" +
                           "\n" +
                           "VALID_KEY=valid_value\n" +
                           "# Another comment\n" +
                           "   \n" +  // Whitespace line
                           "ANOTHER_KEY=another_value\n";
        Files.write(Paths.get(".env"), envContent.getBytes());
        
        EnvLoader.load();
        
        assertEquals("Should load valid key", "valid_value", EnvLoader.get("VALID_KEY"));
        assertEquals("Should load another key", "another_value", EnvLoader.get("ANOTHER_KEY"));
        assertNull("Should not load comment as key", EnvLoader.get("#"));
    }
    
    @Test
    public void testLoadWithWhitespace() throws IOException {
        // Test whitespace handling
        String envContent = "  SPACED_KEY  =  spaced value  \n" +
                           "NO_SPACE=nospace\n" +
                           "   LEADING_SPACE=value\n" +
                           "TRAILING_SPACE=value   \n";
        Files.write(Paths.get(".env"), envContent.getBytes());
        
        EnvLoader.load();
        
        assertEquals("Should trim spaces around key and value", "spaced value", EnvLoader.get("SPACED_KEY"));
        assertEquals("Should handle no spaces", "nospace", EnvLoader.get("NO_SPACE"));
        assertEquals("Should handle leading space", "value", EnvLoader.get("LEADING_SPACE"));
        assertEquals("Should trim trailing space", "value", EnvLoader.get("TRAILING_SPACE"));
    }
    
    @Test
    public void testEnvLocalOverridesEnv() throws IOException {
        // Create .env file
        String envContent = "API_KEY=production_key\n" +
                           "SHARED_VAR=shared_value\n";
        Files.write(Paths.get(".env"), envContent.getBytes());
        
        // Create .env.local file that overrides API_KEY
        String envLocalContent = "API_KEY=local_key\n" +
                                "LOCAL_VAR=local_value\n";
        Files.write(Paths.get(".env.local"), envLocalContent.getBytes());
        
        EnvLoader.load();
        
        assertEquals("env.local should override .env", "local_key", EnvLoader.get("API_KEY"));
        assertEquals("Should load from .env", "shared_value", EnvLoader.get("SHARED_VAR"));
        assertEquals("Should load local-only var", "local_value", EnvLoader.get("LOCAL_VAR"));
    }
    
    @Test
    public void testSystemEnvFallback() {
        // Test fallback to system environment variables
        // We can't set system env vars in tests, but we can test existing ones
        String javaHome = System.getenv("JAVA_HOME");
        String path = System.getenv("PATH");
        
        if (javaHome != null) {
            assertEquals("Should fallback to system JAVA_HOME", javaHome, EnvLoader.get("JAVA_HOME"));
        }
        
        if (path != null) {
            assertEquals("Should fallback to system PATH", path, EnvLoader.get("PATH"));
        }
    }
    
    @Test
    public void testHasMethod() throws IOException {
        String envContent = "EXISTING_KEY=value\n";
        Files.write(Paths.get(".env"), envContent.getBytes());
        
        EnvLoader.load();
        
        assertTrue("Should detect existing key", EnvLoader.has("EXISTING_KEY"));
        assertFalse("Should detect non-existing key", EnvLoader.has("NON_EXISTING_KEY"));
        
        // Test system env fallback for has()
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            assertTrue("Should detect system env var", EnvLoader.has("JAVA_HOME"));
        }
    }
    
    @Test
    public void testLoadOnlyOnce() throws IOException {
        // Create initial .env file
        String envContent = "FIRST_LOAD=initial_value\n";
        Files.write(Paths.get(".env"), envContent.getBytes());
        
        EnvLoader.load();
        assertEquals("Should load initial value", "initial_value", EnvLoader.get("FIRST_LOAD"));
        
        // Modify .env file
        String newEnvContent = "FIRST_LOAD=modified_value\n";
        Files.write(Paths.get(".env"), newEnvContent.getBytes());
        
        // Load again - should not reload
        EnvLoader.load();
        assertEquals("Should keep initial value (not reload)", "initial_value", EnvLoader.get("FIRST_LOAD"));
    }
    
    @Test
    public void testInvalidFormatHandling() throws IOException {
        // Test malformed lines
        String envContent = "VALID_KEY=valid_value\n" +
                           "INVALID_LINE_NO_EQUALS\n" +
                           "=NO_KEY_BEFORE_EQUALS\n" +
                           "EMPTY_VALUE=\n" +
                           "ANOTHER_VALID=another_value\n";
        Files.write(Paths.get(".env"), envContent.getBytes());
        
        EnvLoader.load();
        
        assertEquals("Should load valid key", "valid_value", EnvLoader.get("VALID_KEY"));
        assertEquals("Should load empty value", "", EnvLoader.get("EMPTY_VALUE"));
        assertEquals("Should load another valid", "another_value", EnvLoader.get("ANOTHER_VALID"));
        assertNull("Should not load invalid line", EnvLoader.get("INVALID_LINE_NO_EQUALS"));
    }
    
    @Test
    public void testMissingEnvFiles() {
        // Test when no .env files exist
        EnvLoader.load();
        
        // Should still work and fallback to system env
        assertNull("Should return null for non-existent key", EnvLoader.get("NON_EXISTENT_KEY"));
        
        // Should still access system environment
        String path = System.getenv("PATH");
        if (path != null) {
            assertEquals("Should access system PATH", path, EnvLoader.get("PATH"));
        }
    }
    
    @Test
    public void testSpecialCharacters() throws IOException {
        // Test values with special characters
        String envContent = "URL=https://api.example.com/v1\n" +
                           "EMAIL=test@example.com\n" +
                           "SPECIAL_CHARS=!@#$%^&*()\n" +
                           "JSON_LIKE={\"key\":\"value\"}\n";
        Files.write(Paths.get(".env"), envContent.getBytes());
        
        EnvLoader.load();
        
        assertEquals("Should handle URL", "https://api.example.com/v1", EnvLoader.get("URL"));
        assertEquals("Should handle email", "test@example.com", EnvLoader.get("EMAIL"));
        assertEquals("Should handle special chars", "!@#$%^&*()", EnvLoader.get("SPECIAL_CHARS"));
        assertEquals("Should handle JSON-like string", "{\"key\":\"value\"}", EnvLoader.get("JSON_LIKE"));
    }
    
    @Test
    public void testGetMethodAlwaysLoads() {
        // Test that get() calls load() automatically
        assertNotNull("get() should trigger load", EnvLoader.get("PATH")); // PATH usually exists

        // Even for non-existent keys, it should attempt to load
        assertNull("Should return null for non-existent after loading", EnvLoader.get("DEFINITELY_NOT_A_REAL_ENV_VAR"));
    }

    @Test
    public void testLoadFromPath_validFile() throws IOException {
        File tempFile = File.createTempFile("env-test", ".env");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), "TEMP_KEY=temp_value\nTEMP_KEY2=temp_value2\n".getBytes());

        int count = EnvLoader.loadFromPath(tempFile.getAbsolutePath());

        assertEquals("Should return count of new variables", 2, count);
        assertEquals("Should load value", "temp_value", EnvLoader.get("TEMP_KEY"));
    }

    @Test
    public void testLoadFromPath_missingFile() {
        int count = EnvLoader.loadFromPath("/nonexistent/path/.env");
        assertEquals("Should return -1 for missing file", -1, count);
    }

    @Test
    public void testLoadFromPath_overridesCascade() throws IOException {
        // Load first file with KEY=first
        File file1 = File.createTempFile("env-cascade1", ".env");
        file1.deleteOnExit();
        Files.write(file1.toPath(), "CASCADE_KEY=first\n".getBytes());
        EnvLoader.loadFromPath(file1.getAbsolutePath());

        // Load second file with KEY=second (should override)
        File file2 = File.createTempFile("env-cascade2", ".env");
        file2.deleteOnExit();
        Files.write(file2.toPath(), "CASCADE_KEY=second\n".getBytes());
        EnvLoader.loadFromPath(file2.getAbsolutePath());

        assertEquals("Second file should override first", "second", EnvLoader.get("CASCADE_KEY"));
    }

    @Test
    public void testLoadFromPath_commentLinesSkipped() throws IOException {
        File tempFile = File.createTempFile("env-comments", ".env");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), "# comment\nCOMMENT_TEST=value\n# another comment\n".getBytes());

        int count = EnvLoader.loadFromPath(tempFile.getAbsolutePath());

        assertEquals("Should only count non-comment lines", 1, count);
        assertEquals("Should load the valid key", "value", EnvLoader.get("COMMENT_TEST"));
    }

    @Test
    public void testLoadFromPath_quotedValuesStripped() throws IOException {
        File tempFile = File.createTempFile("env-quotes", ".env");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), "DQ_KEY=\"double quoted\"\nSQ_KEY='single quoted'\n".getBytes());

        EnvLoader.loadFromPath(tempFile.getAbsolutePath());

        assertEquals("Double quotes stripped", "double quoted", EnvLoader.get("DQ_KEY"));
        assertEquals("Single quotes stripped", "single quoted", EnvLoader.get("SQ_KEY"));
    }

    @Test
    public void testLoadFromPath_emptyLinesSkipped() throws IOException {
        File tempFile = File.createTempFile("env-empty", ".env");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), "\n\nEMPTY_TEST=value\n\n".getBytes());

        int count = EnvLoader.loadFromPath(tempFile.getAbsolutePath());

        assertEquals("Should only count actual key-value lines", 1, count);
    }

    @Test
    public void testGet_fallsBackToSystemEnv() {
        // Reset to ensure nothing loaded from files
        resetEnvLoader();

        // PATH should exist in system env on any Unix system
        String systemPath = System.getenv("PATH");
        if (systemPath != null) {
            // loadFromPath with a missing file sets loaded=true (via side effect) -- instead,
            // reset again and rely on get() triggering load() which finds no .env files
            resetEnvLoader();

            // get() triggers load(), which finds no .env files, then falls back to System.getenv
            String result = EnvLoader.get("PATH");
            assertEquals("Should fall back to system PATH", systemPath, result);
        }
    }
}