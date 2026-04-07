package com.excudo.core.orchestration;

import com.excudo.core.model.PPTXDocument;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for OrchestrationContext.
 * Verifies field storage, typed context value retrieval, validity checks, and uptime tracking.
 */
public class OrchestrationContextTest {

    /** Create a minimal PPTXDocument for testing. */
    private PPTXDocument createMinimalDoc() {
        try {
            return PresentationScaffolder.scaffoldDocument("minimal");
        } catch (Exception e) {
            throw new RuntimeException("Failed to scaffold test document", e);
        }
    }

    private OrchestrationContext createContext(Map<String, Object> contextData) {
        PPTXDocument doc = createMinimalDoc();
        return new OrchestrationContext(doc, null, null, null, null, null, null, contextData);
    }

    // ========== CONSTRUCTOR AND GETTERS ==========

    @Test
    public void constructorStoresAllFieldsCorrectly() {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("sessionId", "abc-123");

        OrchestrationContext ctx = createContext(contextData);

        assertNotNull(ctx.getDocument());
        assertNotNull(ctx.getInitializationTime());
        assertNotNull(ctx.getContextData());
        assertEquals("abc-123", ctx.getContextData().get("sessionId"));
    }

    @Test
    public void nullDependenciesAreStoredWithoutThrowingOnConstruction() {
        OrchestrationContext ctx = createContext(new HashMap<>());

        assertNull(ctx.getSlideCreator());
        assertNull(ctx.getRelationshipManager());
        assertNull(ctx.getSpidManager());
        assertNull(ctx.getNotesSlideRegistry());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNullDocument() {
        new OrchestrationContext(null, null, null, null, null, null, null, new HashMap<>());
    }

    // ========== getContextValue ==========

    @Test
    public void getContextValueReturnsTypedValueForMatchingType() {
        Map<String, Object> data = new HashMap<>();
        data.put("greeting", "hello");

        OrchestrationContext ctx = createContext(data);
        assertEquals("hello", ctx.getContextValue("greeting", String.class));
    }

    @Test
    public void getContextValueReturnsNullForWrongType() {
        Map<String, Object> data = new HashMap<>();
        data.put("greeting", "hello");

        OrchestrationContext ctx = createContext(data);
        assertNull(ctx.getContextValue("greeting", Integer.class));
    }

    @Test
    public void getContextValueReturnsNullForMissingKey() {
        OrchestrationContext ctx = createContext(new HashMap<>());
        assertNull(ctx.getContextValue("nonexistent", String.class));
    }

    @Test
    public void getContextValueHandlesIntegerValueCorrectly() {
        Map<String, Object> data = new HashMap<>();
        data.put("slideCount", 42);

        OrchestrationContext ctx = createContext(data);
        assertEquals(Integer.valueOf(42), ctx.getContextValue("slideCount", Integer.class));
    }

    // ========== isValid ==========

    @Test
    public void isValidReturnsTrueWithDocument() {
        OrchestrationContext ctx = createContext(new HashMap<>());
        assertTrue(ctx.isValid());
    }

    // ========== getUptime ==========

    @Test
    public void getUptimeReturnsNonNegativeDuration() throws InterruptedException {
        OrchestrationContext ctx = createContext(new HashMap<>());
        Thread.sleep(5);

        Duration uptime = ctx.getUptime();
        assertNotNull(uptime);
        assertFalse("Uptime must be non-negative", uptime.isNegative());
        assertTrue("Uptime should be positive after a short sleep", uptime.toMillis() > 0);
    }
}
