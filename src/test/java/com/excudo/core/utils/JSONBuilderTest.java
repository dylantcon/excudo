package com.excudo.core.utils;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import java.util.*;

/**
 * Comprehensive unit tests for JSONBuilder
 * Tests all public methods and edge cases
 */
public class JSONBuilderTest {
    
    private JSONBuilder builder;
    
    @Before
    public void setUp() {
        builder = new JSONBuilder();
    }
    
    @Test
    public void testEmptyBuilder() {
        String json = builder.toString();
        assertEquals("{}", json);
    }
    
    @Test
    public void testPutString() {
        builder.put("message", "Hello World");
        String json = builder.toString();
        assertTrue("Should contain escaped string", json.contains("\"message\":\"Hello World\""));
    }
    
    @Test
    public void testPutStringWithSpecialCharacters() {
        builder.put("text", "Line 1\nLine 2\tTabbed \"quoted\"");
        String json = builder.toString();
        assertTrue("Should escape newlines", json.contains("\\n"));
        assertTrue("Should escape tabs", json.contains("\\t"));
        assertTrue("Should escape quotes", json.contains("\\\"quoted\\\""));
    }
    
    @Test
    public void testPutInteger() {
        builder.put("count", 42);
        String json = builder.toString();
        assertTrue("Should contain integer value", json.contains("\"count\":42"));
    }
    
    @Test
    public void testPutLong() {
        builder.put("timestamp", 1234567890L);
        String json = builder.toString();
        assertTrue("Should contain long value", json.contains("\"timestamp\":1234567890"));
    }
    
    @Test
    public void testPutBoolean() {
        builder.put("success", true);
        builder.put("failed", false);
        String json = builder.toString();
        assertTrue("Should contain true boolean", json.contains("\"success\":true"));
        assertTrue("Should contain false boolean", json.contains("\"failed\":false"));
    }
    
    @Test
    public void testPutArray() {
        List<String> items = Arrays.asList("item1", "item2", "item3");
        builder.putArray("items", items);
        String json = builder.toString();
        assertTrue("Should contain array", json.contains("\"items\":[\"item1\",\"item2\",\"item3\"]"));
    }
    
    @Test
    public void testPutEmptyArray() {
        builder.putArray("empty", new ArrayList<>());
        String json = builder.toString();
        assertTrue("Should contain empty array", json.contains("\"empty\":[]"));
    }
    
    @Test
    public void testNestedObject() {
        JSONBuilder nested = new JSONBuilder();
        nested.put("nested_key", "nested_value");
        builder.putObject("nested", nested);
        String json = builder.toString();
        assertTrue("Should contain nested object", json.contains("\"nested\":{\"nested_key\":\"nested_value\"}"));
    }
    
    @Test
    public void testCreateNestedObject() {
        JSONBuilder nested = builder.createNestedObject("config");
        nested.put("enabled", true);
        nested.put("timeout", 30);
        
        String json = builder.toString();
        assertTrue("Should contain nested object", json.contains("\"config\":{"));
        assertTrue("Should contain nested boolean", json.contains("\"enabled\":true"));
        assertTrue("Should contain nested integer", json.contains("\"timeout\":30"));
    }
    
    @Test
    public void testPutObjectArray() {
        List<JSONBuilder> objects = new ArrayList<>();
        
        JSONBuilder obj1 = new JSONBuilder();
        obj1.put("id", 1);
        obj1.put("name", "Object 1");
        objects.add(obj1);
        
        JSONBuilder obj2 = new JSONBuilder();
        obj2.put("id", 2);
        obj2.put("name", "Object 2");
        objects.add(obj2);
        
        builder.putObjectArray("objects", objects);
        String json = builder.toString();
        assertTrue("Should contain object array", json.contains("\"objects\":["));
        assertTrue("Should contain first object", json.contains("\"id\":1"));
        assertTrue("Should contain second object", json.contains("\"id\":2"));
    }
    
    @Test
    public void testComplexStructure() {
        // Build a complex JSON structure
        builder.put("success", true);
        builder.put("message", "Operation completed");
        builder.put("timestamp", System.currentTimeMillis());
        
        // Add nested configuration
        JSONBuilder config = builder.createNestedObject("config");
        config.put("retries", 3);
        config.put("timeout_ms", 5000);
        config.putArray("allowed_formats", Arrays.asList("json", "xml", "yaml"));
        
        // Add array of result objects
        List<JSONBuilder> results = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            JSONBuilder result = new JSONBuilder();
            result.put("id", i);
            result.put("status", "completed");
            result.put("processed", true);
            results.add(result);
        }
        builder.putObjectArray("results", results);
        
        String json = builder.toString();
        
        // Verify structure
        assertNotNull("JSON should not be null", json);
        assertTrue("Should be valid JSON structure", json.startsWith("{") && json.endsWith("}"));
        assertTrue("Should contain success field", json.contains("\"success\":true"));
        assertTrue("Should contain config object", json.contains("\"config\":{"));
        assertTrue("Should contain results array", json.contains("\"results\":["));
        assertTrue("Should contain nested arrays", json.contains("\"allowed_formats\":["));
    }
    
    @Test
    public void testFluentInterface() {
        // Test method chaining
        String json = new JSONBuilder()
            .put("status", "active")
            .put("count", 10)
            .put("verified", true)
            .toString();
            
        assertTrue("Should contain all chained values", 
                  json.contains("\"status\":\"active\"") && 
                  json.contains("\"count\":10") && 
                  json.contains("\"verified\":true"));
    }
    
    @Test
    public void testToStringOutput() {
        builder.put("simple", "value");
        JSONBuilder nested = builder.createNestedObject("nested");
        nested.put("key", "value");
        
        String output = builder.toString();
        assertNotNull("String output should not be null", output);
        // Verify it produces valid JSON structure
        assertTrue("Should contain simple value", output.contains("\"simple\":\"value\""));
        assertTrue("Should contain nested structure", output.contains("\"nested\":{"));
        assertTrue("Should be valid JSON structure", output.startsWith("{") && output.endsWith("}"));
    }
    
    @Test
    public void testNullHandling() {
        // Test that null values are handled gracefully
        builder.put("null_string", (String) null);
        String json = builder.toString();
        assertTrue("Should handle null strings", json.contains("\"null_string\":null"));
    }
    
    @Test
    public void testKeyOrdering() {
        // JSONBuilder uses LinkedHashMap, so order should be preserved
        builder.put("first", "1");
        builder.put("second", "2");
        builder.put("third", "3");
        
        String json = builder.toString();
        int firstPos = json.indexOf("\"first\"");
        int secondPos = json.indexOf("\"second\"");
        int thirdPos = json.indexOf("\"third\"");
        
        assertTrue("Keys should maintain insertion order", 
                  firstPos < secondPos && secondPos < thirdPos);
    }
}