package com.excudo.exceptions;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for XMLParsingException
 * Tests all constructors and exception behavior
 */
public class XMLParsingExceptionTest {
    
    @Test
    public void testMessageConstructor() {
        String testMessage = "Test XML parsing error";
        XMLParsingException exception = new XMLParsingException(testMessage);
        
        assertEquals("Message should be preserved", testMessage, exception.getMessage());
        assertNull("Cause should be null", exception.getCause());
    }
    
    @Test
    public void testMessageAndCauseConstructor() {
        String testMessage = "Test XML parsing error with cause";
        RuntimeException cause = new RuntimeException("Root cause");
        XMLParsingException exception = new XMLParsingException(testMessage, cause);
        
        assertEquals("Message should be preserved", testMessage, exception.getMessage());
        assertEquals("Cause should be preserved", cause, exception.getCause());
    }
    
    @Test
    public void testCauseOnlyConstructor() {
        RuntimeException cause = new RuntimeException("Root cause message");
        XMLParsingException exception = new XMLParsingException(cause);
        
        assertEquals("Cause should be preserved", cause, exception.getCause());
        // Message should be the cause's toString() or similar
        assertNotNull("Message should not be null", exception.getMessage());
    }
    
    @Test
    public void testInheritanceFromException() {
        XMLParsingException exception = new XMLParsingException("Test message");
        
        assertTrue("Should be instance of Exception", exception instanceof Exception);
        assertTrue("Should be instance of Throwable", exception instanceof Throwable);
    }
    
    @Test
    public void testStackTracePreservation() {
        XMLParsingException exception = new XMLParsingException("Test message");
        
        // Fill in stack trace
        exception.fillInStackTrace();
        StackTraceElement[] stackTrace = exception.getStackTrace();
        
        assertNotNull("Stack trace should not be null", stackTrace);
        assertTrue("Stack trace should contain elements", stackTrace.length > 0);
        
        // Verify our test method is in the stack trace
        boolean foundTestMethod = false;
        for (StackTraceElement element : stackTrace) {
            if (element.getMethodName().equals("testStackTracePreservation")) {
                foundTestMethod = true;
                break;
            }
        }
        assertTrue("Should find test method in stack trace", foundTestMethod);
    }
    
    @Test
    public void testChainedExceptionHandling() {
        // Create a chain of exceptions
        Exception rootCause = new IllegalArgumentException("Invalid XML format");
        RuntimeException intermediateCause = new RuntimeException("Processing failed", rootCause);
        XMLParsingException exception = new XMLParsingException("Failed to parse slide XML", intermediateCause);
        
        // Verify exception chain
        assertEquals("Direct cause should be intermediate", intermediateCause, exception.getCause());
        assertEquals("Root cause should be accessible", rootCause, exception.getCause().getCause());
        
        // Test walking the exception chain
        Throwable current = exception;
        int chainLength = 0;
        while (current != null) {
            chainLength++;
            current = current.getCause();
        }
        assertEquals("Exception chain should have 3 levels", 3, chainLength);
    }
    
    @Test
    public void testExceptionSerialization() {
        // Test that exception can be serialized (important for distributed systems)
        XMLParsingException exception = new XMLParsingException("Serialization test");
        
        // Verify it implements Serializable (inherited from Exception)
        assertTrue("Exception should be serializable", 
                  exception instanceof java.io.Serializable);
    }
    
    @Test
    public void testNullMessageHandling() {
        XMLParsingException exception = new XMLParsingException((String) null);
        
        // Should not throw NPE and handle null gracefully
        assertNull("Null message should be preserved", exception.getMessage());
    }
    
    @Test
    public void testNullCauseHandling() {
        XMLParsingException exception = new XMLParsingException("Message", null);
        
        assertEquals("Message should be preserved", "Message", exception.getMessage());
        assertNull("Null cause should be preserved", exception.getCause());
    }
    
    @Test
    public void testTypicalUsageScenarios() {
        // Test common usage patterns in the codebase
        
        // Scenario 1: Simple parsing error
        XMLParsingException parseError = new XMLParsingException("Invalid slide XML structure");
        assertEquals("Parse error message should be correct", 
                    "Invalid slide XML structure", parseError.getMessage());
        
        // Scenario 2: Wrapping a lower-level exception
        java.io.IOException ioException = new java.io.IOException("File not found");
        XMLParsingException wrapperException = new XMLParsingException("Failed to read presentation file", ioException);
        assertEquals("Wrapper message should be correct", 
                    "Failed to read presentation file", wrapperException.getMessage());
        assertEquals("Wrapped exception should be preserved", ioException, wrapperException.getCause());
        
        // Scenario 3: Re-throwing with context
        try {
            throw new org.xml.sax.SAXException("Malformed XML");
        } catch (org.xml.sax.SAXException e) {
            XMLParsingException contextException = new XMLParsingException("Error parsing slide content on slide 3", e);
            assertTrue("Context message should contain details", 
                      contextException.getMessage().contains("slide 3"));
            assertTrue("Original exception should be preserved", 
                      contextException.getCause() instanceof org.xml.sax.SAXException);
        }
    }
    
    @Test
    public void testExceptionEquality() {
        XMLParsingException exception1 = new XMLParsingException("Same message");
        XMLParsingException exception2 = new XMLParsingException("Same message");
        
        // Exceptions are typically not equal even with same message (object identity)
        assertNotEquals("Different exception instances should not be equal", exception1, exception2);
        
        // But same instance should be equal to itself
        assertEquals("Same instance should be equal", exception1, exception1);
    }
}