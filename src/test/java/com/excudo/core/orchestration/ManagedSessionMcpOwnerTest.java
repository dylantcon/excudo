package com.excudo.core.orchestration;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link SessionManager.ManagedSession}'s MCP owner API:
 * {@code setMcpOwner / clearMcpOwner / isMcpExclusive / isOwnedBy}.
 *
 * These pin the identity-comparison semantics that let the engine that
 * started an MCP server keep mutating its session while every other
 * console attached to the same file is blocked.
 *
 * Uses the orchestrator-wrapping constructor so no file I/O is needed.
 */
public class ManagedSessionMcpOwnerTest {

    private SessionManager.ManagedSession session;

    @Before
    public void setUp() throws Exception {
        session = new SessionManager.ManagedSession("test-session", new PPTXOrchestratorImpl());
    }

    @Test
    public void freshSessionIsNotExclusive() {
        assertFalse(session.isMcpExclusive());
    }

    @Test
    public void freshSessionIsOwnedByNobody() {
        Object someOwner = new Object();
        assertFalse(session.isOwnedBy(someOwner));
        assertFalse("null is not a valid owner", session.isOwnedBy(null));
    }

    @Test
    public void setMcpOwnerMarksSessionExclusive() {
        Object owner = new Object();
        session.setMcpOwner(owner);

        assertTrue(session.isMcpExclusive());
        assertTrue(session.isOwnedBy(owner));
    }

    @Test
    public void clearMcpOwnerRemovesExclusivity() {
        Object owner = new Object();
        session.setMcpOwner(owner);
        session.clearMcpOwner();

        assertFalse(session.isMcpExclusive());
        assertFalse(session.isOwnedBy(owner));
    }

    @Test
    public void ownershipComparisonIsIdentityNotEquality() {
        // Two Strings that are equal() but not == must not be confused.
        String owner1 = new String("owner"); // explicit new to avoid interning
        String owner2 = new String("owner");
        assertNotSame("sanity: owner1 and owner2 are different instances", owner1, owner2);
        assertEquals("sanity: owner1 and owner2 equal()", owner1, owner2);

        session.setMcpOwner(owner1);
        assertTrue(session.isOwnedBy(owner1));
        assertFalse("equal-but-not-same object should NOT be treated as owner",
            session.isOwnedBy(owner2));
    }

    @Test
    public void settingNewOwnerReplacesPrevious() {
        Object first = new Object();
        Object second = new Object();

        session.setMcpOwner(first);
        session.setMcpOwner(second);

        assertFalse("first owner was replaced", session.isOwnedBy(first));
        assertTrue("second owner is now active", session.isOwnedBy(second));
        assertTrue(session.isMcpExclusive());
    }

    @Test
    public void clearWhenUnownedIsSafe() {
        session.clearMcpOwner(); // no setMcpOwner called first
        assertFalse(session.isMcpExclusive());
    }
}
