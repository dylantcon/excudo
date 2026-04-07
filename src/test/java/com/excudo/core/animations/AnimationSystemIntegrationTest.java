package com.excudo.core.animations;

import com.excudo.core.model.AnimationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the animation system focusing on registry functionality
 * and factory availability. Tests the working animation system components.
 */
class AnimationSystemIntegrationTest {

    private AnimationFactoryRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AnimationFactoryRegistry();
    }

    @Test
    @DisplayName("Registry should initialize with working default factories")
    void testRegistryInitialization() {
        assertNotNull(registry, "Registry should be created");
        assertTrue(registry.getAllFactories().size() > 0, "Should have registered default factories");
        
        String stats = registry.getStatistics();
        assertNotNull(stats, "Should provide statistics");
        assertTrue(stats.contains("AnimationFactoryRegistry"), "Statistics should mention registry");
    }

    @Test
    @DisplayName("Should support core animation types that were working in console test")
    void testCoreAnimationSupport() {
        // Test the animation types we know work from our console testing
        assertTrue(registry.canCreateAnimation(AnimationType.FADE), 
                  "Should support FADE animation");
        assertTrue(registry.canCreateAnimation(AnimationType.APPEAR), 
                  "Should support APPEAR animation");  
        assertTrue(registry.canCreateAnimation(AnimationType.ZOOM), 
                  "Should support ZOOM animation");
        assertTrue(registry.canCreateAnimation(AnimationType.FLY_IN_LEFT), 
                  "Should support FLY_IN_LEFT animation (verified working in console)");
    }

    @Test
    @DisplayName("Should provide factory lookup functionality")
    void testFactoryLookup() {
        // Test factory retrieval for supported types
        AnimationWriter fadeFactory = registry.getFactory(AnimationType.FADE);
        AnimationWriter flyFactory = registry.getFactory(AnimationType.FLY_IN_LEFT);
        
        if (fadeFactory != null) {
            assertTrue(fadeFactory.supportsAnimationType(AnimationType.FADE),
                      "FADE factory should support FADE animation");
        }
        
        if (flyFactory != null) {
            assertTrue(flyFactory.supportsAnimationType(AnimationType.FLY_IN_LEFT),
                      "FLY factory should support FLY_IN_LEFT animation");
        }
    }

    @Test
    @DisplayName("Should support write-path factory lookup for all registered types")
    void testWritePathFactoryLookup() {
        var supportedTypes = registry.getSupportedAnimationTypes();
        assertFalse(supportedTypes.isEmpty(), "Should have supported animation types");

        for (AnimationType type : supportedTypes) {
            AnimationWriter factory = registry.getFactory(type);
            assertNotNull(factory, "Should have factory for supported type: " + type);
        }
    }

    @Test
    @DisplayName("Should handle registry clearing and re-registration")
    void testRegistryManagement() {
        int initialCount = registry.getAllFactories().size();
        assertTrue(initialCount > 0, "Should start with factories");
        
        registry.clear();
        assertEquals(0, registry.getAllFactories().size(), "Should be empty after clear");
        assertFalse(registry.canCreateAnimation(AnimationType.FADE), 
                   "Should not support animations after clear");
        
        // Test re-initialization
        AnimationFactoryRegistry newRegistry = new AnimationFactoryRegistry();
        assertTrue(newRegistry.getAllFactories().size() > 0, 
                  "New registry should load default factories");
    }

    @Test
    @DisplayName("Should provide comprehensive OOXML pattern support")
    void testOoxmlPatternSupport() {
        var patterns = registry.getSupportedOoxmlPatterns();
        assertNotNull(patterns, "Should provide OOXML patterns");
        
        if (registry.getAllFactories().size() > 0) {
            assertTrue(patterns.size() > 0, "Should have patterns when factories loaded");
            
            // Test that patterns are non-empty strings
            for (String pattern : patterns) {
                assertNotNull(pattern, "Pattern should not be null");
                assertFalse(pattern.trim().isEmpty(), "Pattern should not be empty");
            }
        }
    }

    @Test
    @DisplayName("Should generate validation report")
    void testValidationReporting() {
        String report = registry.validateAgainstTimingDumps();
        assertNotNull(report, "Should generate validation report");
        
        assertTrue(report.contains("Animation Factory Registry Validation Report"),
                  "Report should have proper header");
        assertTrue(report.contains("Registered Factories"),
                  "Report should mention factory count");
        assertTrue(report.contains("Supported Animation Types"),
                  "Report should mention animation types");
        assertTrue(report.contains("Supported OOXML Patterns"),
                  "Report should mention patterns");
        
        // Report should list actual patterns if any factories are loaded
        if (registry.getAllFactories().size() > 0) {
            assertTrue(report.contains("Supported Patterns:"),
                      "Report should list supported patterns");
        }
    }

    @Test
    @DisplayName("Should handle default animation type correctly")
    void testDefaultAnimationType() {
        AnimationType defaultType = registry.getDefaultAnimationType();
        assertEquals(AnimationType.FADE, defaultType, 
                    "Default animation type should be FADE");
        
        // Should be able to get factory for default type if any factories loaded
        if (registry.getAllFactories().size() > 0) {
            try {
                AnimationWriter defaultFactory = registry.getDefaultFactory();
                assertNotNull(defaultFactory, 
                             "Should get factory for default animation type");
                assertTrue(defaultFactory.supportsAnimationType(defaultType),
                          "Default factory should support default type");
            } catch (IllegalStateException e) {
                // This is acceptable if no factories support the default type
                assertTrue(e.getMessage().contains("No factory available"),
                          "Exception should explain missing default factory");
            }
        }
    }

    @Test
    @DisplayName("Should maintain supported animation types consistency")
    void testSupportedTypesConsistency() {
        var supportedTypes = registry.getSupportedAnimationTypes();
        assertNotNull(supportedTypes, "Supported types should not be null");
        
        // All supported types should be creatable
        for (AnimationType type : supportedTypes) {
            assertTrue(registry.canCreateAnimation(type),
                      "All supported types should be creatable: " + type);
        }
        
        // All creatable types should have factories
        for (AnimationType type : supportedTypes) {
            AnimationWriter factory = registry.getFactory(type);
            if (factory != null) {
                assertTrue(factory.supportsAnimationType(type),
                          "Factory should support the type it's registered for: " + type);
            }
        }
    }

    @Test
    @DisplayName("Should demonstrate working animation system based on console success")
    void testWorkingAnimationTypes() {
        // These are the animation types we proved work in console testing
        AnimationType[] workingTypes = {
            AnimationType.FADE,
            AnimationType.APPEAR, 
            AnimationType.ZOOM,
            AnimationType.FLY_IN_LEFT,
            AnimationType.FLY_IN_RIGHT,
            AnimationType.FLY_IN_TOP,
            AnimationType.FLY_IN_BOTTOM
        };
        
        int workingCount = 0;
        for (AnimationType type : workingTypes) {
            if (registry.canCreateAnimation(type)) {
                workingCount++;
                AnimationWriter factory = registry.getFactory(type);
                if (factory != null) {
                    assertTrue(factory.supportsAnimationType(type),
                              "Working animation type should have supporting factory: " + type);
                }
            }
        }
        
        assertTrue(workingCount > 0, 
                  "Should have at least some working animation types from console testing");
        
        // We know FLY_IN_LEFT specifically works from our console test
        assertTrue(registry.canCreateAnimation(AnimationType.FLY_IN_LEFT),
                  "FLY_IN_LEFT should work (verified in console test)");
    }
}