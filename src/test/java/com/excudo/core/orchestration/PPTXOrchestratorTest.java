package com.excudo.core.orchestration;

import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for PPTXOrchestrator interface.
 * Tests the interface definition and default methods.
 */
public class PPTXOrchestratorTest {

    @Test
    public void testDefaultFinalizePresentation() {
        boolean[] called = {false};

        PPTXOrchestrator orchestrator = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<FinalizationResult> finalizeOperations() {
                called[0] = true;
                return null;
            }
        };

        orchestrator.finalizePresentation();

        assertTrue("Default finalizePresentation should call finalizeOperations", called[0]);
    }

    @Test
    public void testInterfaceInstantiation() {
        PPTXOrchestrator orchestrator = new StubPPTXOrchestrator();
        assertNotNull("Interface implementation should be instantiable", orchestrator);
    }
}
