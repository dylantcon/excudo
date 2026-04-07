package com.excudo.core.llm;

import org.junit.Test;
import static org.junit.Assert.*;
import com.excudo.core.llm.RequestParser;
import com.excudo.core.commands.RequestSchema;
import com.excudo.core.results.ExecutionResult;

/**
 * Test that JSON text formatting is preserved during parsing
 */
public class TextFormattingPreservationTest {
    
    @Test
    public void testMarkdownBulletPreservation() {
        String jsonWithBullets = """
        {
          "schemaVersion": "1.0",
          "operations": [
            {
              "type": "shape-addition",
              "parameters": {
                "slideNumber": 1,
                "shapeData": {
                  "name": "Test Shape",
                  "text": "Java Programming Language\\n- Platform independent\\n  - Write once, run anywhere\\n  - JVM handles platform differences\\n- Object-oriented programming",
                  "geometry": {
                    "x": 838200,
                    "y": 1825625,
                    "width": 3800000,
                    "height": 4000000
                  }
                }
              },
              "description": "Test markdown bullet preservation"
            }
          ],
          "metadata": {
            "confidence": 0.95,
            "reasoning": "Test",
            "contextUsed": true,
            "ooxmlNotes": "Test"
          }
        }
        """;
        
        RequestParser parser = new RequestParser();
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest(jsonWithBullets);
        
        assertTrue("Parsing should succeed", result.isSuccess());
        RequestSchema.LLMRequest request = result.getData().get();
        assertNotNull("Request should not be null", request);
        
        assertEquals("Should have 1 operation", 1, request.getActions().size());
        
        RequestSchema.ActionRequest operation = request.getActions().get(0);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> shapeData = (java.util.Map<String, Object>) operation.getParameters().get("shapeData");
        String text = (String) shapeData.get("text");
        
        assertNotNull("Text should not be null", text);
        
        // Check that newlines are preserved
        assertTrue("Text should contain actual newlines", text.contains("\n"));
        
        // Check that markdown bullets are preserved
        assertTrue("Text should contain markdown bullets", text.contains("- Platform independent"));
        assertTrue("Text should contain indented bullets", text.contains("  - Write once"));
        
        // Check that backslash-n is converted to actual newlines
        assertFalse("Text should not contain literal \\n", text.contains("\\n"));
        
        System.out.println("Preserved text content:");
        System.out.println(text);
    }
    
    @Test
    public void testMultilineTextPreservation() {
        String jsonWithMultiline = """
        {
          "schemaVersion": "1.0",
          "operations": [
            {
              "type": "shape-addition",
              "parameters": {
                "slideNumber": 1,
                "shapeData": {
                  "name": "Code Example",
                  "text": "public class HelloWorld {\\n    public static void main(String[] args) {\\n        System.out.println(\\"Hello, Java!\\");\\n    }\\n}",
                  "geometry": {
                    "x": 5000000,
                    "y": 2000000,
                    "width": 3500000,
                    "height": 2000000
                  }
                }
              },
              "description": "Test multiline code preservation"
            }
          ],
          "metadata": {
            "confidence": 0.95,
            "reasoning": "Test",
            "contextUsed": true,
            "ooxmlNotes": "Test"
          }
        }
        """;
        
        RequestParser parser = new RequestParser();
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest(jsonWithMultiline);
        
        assertTrue("Parsing should succeed", result.isSuccess());
        RequestSchema.LLMRequest request = result.getData().get();
        
        RequestSchema.ActionRequest operation = request.getActions().get(0);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> shapeData = (java.util.Map<String, Object>) operation.getParameters().get("shapeData");
        String text = (String) shapeData.get("text");
        
        // Check that code formatting is preserved
        assertTrue("Code should contain actual newlines", text.contains("\n"));
        assertTrue("Code should contain indentation", text.contains("    public static"));
        assertFalse("Code should not contain literal \\n", text.contains("\\n"));
        
        System.out.println("Preserved code content:");
        System.out.println(text);
    }
}