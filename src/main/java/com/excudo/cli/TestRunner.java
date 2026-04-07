package com.excudo.cli;

import com.excudo.xml.writers.SPIDManager;
import com.excudo.core.llm.LLMIntegrationService;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

import java.io.File;
import java.util.*;

/**
 * Automated test runner for headless testing.
 * Provides programmatic testing without manual file creation/cleanup.
 * 
 * Usage:
 *   java TestRunner [--verbose] [test-name1] [test-name2] ...
 *   
 * If no test names are provided, runs all tests.
 */
public class TestRunner {
    private static final ComponentLogger logger = Logger.cli();
    private final boolean verbose;
    private final Map<String, TestCase> tests;
    private int passed = 0;
    private int failed = 0;
    
    public TestRunner(boolean verbose) {
        this.verbose = verbose;
        this.tests = initializeTests();
    }
    
    public static void main(String[] args) {
        boolean verbose = false;
        List<String> testNames = new ArrayList<>();
        
        // Parse command line arguments
        for (String arg : args) {
            if ("--verbose".equals(arg) || "-v".equals(arg)) {
                verbose = true;
            } else if ("--help".equals(arg) || "-h".equals(arg)) {
                printUsage();
                return;
            } else if (!arg.startsWith("-")) {
                testNames.add(arg);
            }
        }
        
        TestRunner runner = new TestRunner(verbose);
        
        if (testNames.isEmpty()) {
            runner.runAllTests();
        } else {
            // Run specific tests
            for (String testName : testNames) {
                runner.runTest(testName);
            }
            runner.printSummary();
        }
    }
    
    private static void printUsage() {
        System.out.println("TestRunner - Run integration tests for Excudo");
        System.out.println();
        System.out.println("Usage: java TestRunner [OPTIONS] [TEST_NAMES...]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --verbose, -v    Enable verbose output");
        System.out.println("  --help, -h       Show this help message");
        System.out.println();
        System.out.println("Available tests:");
        TestRunner runner = new TestRunner(false);
        for (String testName : runner.getAvailableTests()) {
            System.out.println("  " + testName);
        }
    }
    
    public Set<String> getAvailableTests() {
        return tests.keySet();
    }
    
    private Map<String, TestCase> initializeTests() {
        Map<String, TestCase> testMap = new LinkedHashMap<>();
        
        // SPID allocation tests
        testMap.put("spid-allocation", this::testSPIDAllocation);
        testMap.put("spid-prediction", this::testSPIDPrediction);
        testMap.put("spid-consistency", this::testSPIDConsistency);
        testMap.put("animation-spid-fix", this::testAnimationSPIDFix);
        
        // Basic functionality tests
        testMap.put("file-operations", this::testFileOperations);
        testMap.put("llm-service", this::testLLMService);
        
        return testMap;
    }
    
    public void runAllTests() {
        log("Running all tests...");
        logger.info("Test Suite: Excudo");
        logger.info("======================================");
        
        for (Map.Entry<String, TestCase> entry : tests.entrySet()) {
            runTest(entry.getKey());
        }
        
        printSummary();
    }
    
    public void runTest(String testName) {
        TestCase test = tests.get(testName);
        if (test == null) {
            logger.error("[FAIL] Unknown test: {}", testName);
            logger.error("Available tests: {}", String.join(", ", tests.keySet()));
            return;
        }
        
        logger.info("  {}... ", testName);
        
        try {
            test.run();
            logger.info("[OK] PASS");
            passed++;
        } catch (Exception e) {
            logger.error("[FAIL] FAIL: {}", e.getMessage());
            if (verbose) {
                logger.error("Test failure stack trace:", e);
            }
            failed++;
        }
    }
    
    private void testSPIDAllocation() throws Exception {
        log("Testing SPID allocation");
        
        SPIDManager spidManager = SPIDManager.getInstance();
        
        // Test basic allocation
        int spid1 = spidManager.allocateSpidForShape("custom", 1, false, false, null);
        int spid2 = spidManager.allocateSpidForShape("custom", 1, false, false, null);
        
        if (spid1 == spid2) {
            throw new AssertionError("SPIDs should be unique, got: " + spid1 + " and " + spid2);
        }
        
        log("SPID allocation test passed: " + spid1 + " != " + spid2);
    }
    
    private void testSPIDPrediction() throws Exception {
        log("Testing SPID prediction accuracy");
        
        SPIDManager spidManager = SPIDManager.getInstance();
        
        // Test prediction vs actual allocation
        int predicted = spidManager.predictSpidForShape("custom", 2, false, false, null);
        int actual = spidManager.allocateSpidForShape("custom", 2, false, false, null);
        
        if (predicted != actual) {
            throw new AssertionError("Prediction mismatch: predicted " + predicted + " but got " + actual);
        }
        
        log("SPID prediction test passed: " + predicted + " == " + actual);
    }
    
    private void testSPIDConsistency() throws Exception {
        log("Testing SPID consistency across slides");
        
        SPIDManager spidManager = SPIDManager.getInstance();
        
        // Allocate SPIDs on different slides
        int slide1Spid = spidManager.allocateSpidForShape("custom", 1, false, false, null);
        int slide2Spid = spidManager.allocateSpidForShape("custom", 2, false, false, null);
        
        // Ensure global uniqueness
        if (slide1Spid == slide2Spid) {
            throw new AssertionError("SPIDs should be globally unique across slides");
        }
        
        log("SPID consistency test passed");
    }
    
    private void testAnimationSPIDFix() throws Exception {
        log("Testing animation SPID fix");
        
        SPIDManager spidManager = SPIDManager.getInstance();
        
        // Simulate the scenario from the original bug
        spidManager.registerSpid(1, 4, "Group Shape");
        spidManager.registerSpid(37, 4, "Title Placeholder");
        
        // Test that prediction matches allocation for multiple shapes
        List<Integer> predicted = new ArrayList<>();
        List<Integer> actual = new ArrayList<>();
        
        for (int i = 0; i < 4; i++) {
            predicted.add(spidManager.predictSpidForShape("custom", 4, false, false, null));
            actual.add(spidManager.allocateSpidForShape("custom", 4, false, false, null));
        }
        
        if (!predicted.equals(actual)) {
            throw new AssertionError("Animation SPID fix failed: predicted " + predicted + " but got " + actual);
        }
        
        log("Animation SPID fix test passed: " + predicted);
    }
    
    private void testFileOperations() throws Exception {
        log("Testing file operations");
        
        File testDir = new File("test-outputs");
        testDir.mkdirs();
        
        File testFile = new File(testDir, "cli-test.txt");
        testFile.createNewFile();
        
        if (!testFile.exists()) {
            throw new AssertionError("Test file was not created");
        }
        
        boolean deleted = testFile.delete();
        if (!deleted || testFile.exists()) {
            throw new AssertionError("Test file cleanup failed");
        }
        
        log("File operations test passed");
    }
    
    private void testLLMService() throws Exception {
        log("Testing LLM service instantiation");
        
        try {
            LLMIntegrationService llm = new LLMIntegrationService();
            // Just test that LLM service can be instantiated
            log("LLM service test passed (service instantiated)");
        } catch (Exception e) {
            // If LLM is not available, skip test
            if (e.getMessage().contains("API key") || e.getMessage().contains("not configured")) {
                log("LLM service test skipped (not configured)");
                return;
            }
            throw e;
        }
    }
    
    private void printSummary() {
        logger.info("");
        logger.info("Test Results:");
        logger.info("=============");
        logger.info("Passed: {}", passed);
        logger.info("Failed: {}", failed);
        logger.info("Total:  {}", (passed + failed));
        
        if (failed > 0) {
            logger.error("\n[FAIL] Some tests failed");
            System.exit(1);
        } else {
            logger.info("\n[OK] All tests passed");
        }
    }
    
    private void log(String message) {
        if (verbose) {
            logger.debug(message);
        }
    }
    
    @FunctionalInterface
    private interface TestCase {
        void run() throws Exception;
    }
}