package com.excudo.core.animations;

import com.excudo.core.model.AnimationType;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Set;

/**
 * Unit tests for AnimationFactoryRegistry to verify correct factory selection.
 * This is critical for ensuring the right factory creates animations.
 */
public class AnimationFactoryRegistryTest {
    
    private AnimationFactoryRegistry registry;
    
    @Before
    public void setUp() {
        registry = new AnimationFactoryRegistry();
        // Registry auto-registers default factories during construction
    }
    
    @Test
    public void testWipeAnimationFactorySelection() {
        // This is the core issue: verify WIPE_LEFT gets WipeAnimationFactory
        AnimationWriter factory = registry.getFactory(AnimationType.WIPE_LEFT);
        
        assertNotNull("Factory should not be null for WIPE_LEFT", factory);
        assertEquals("WIPE_LEFT should get WipeAnimationFactory", 
                    "WipeAnimationFactory", 
                    factory.getClass().getSimpleName());
        
        // Verify it's not getting the wrong factory
        assertNotEquals("WIPE_LEFT should not get FadeAnimationFactory", 
                       "FadeAnimationFactory", 
                       factory.getClass().getSimpleName());
        
        assertNotEquals("WIPE_LEFT should not get ZoomAnimationFactory", 
                       "ZoomAnimationFactory", 
                       factory.getClass().getSimpleName());
    }
    
    @Test
    public void testAllWipeDirectionsGetWipeFactory() {
        // Test all wipe directions get WipeAnimationFactory
        AnimationType[] wipeTypes = {
            AnimationType.WIPE_LEFT,
            AnimationType.WIPE_RIGHT, 
            AnimationType.WIPE_UP,
            AnimationType.WIPE_DOWN
        };
        
        for (AnimationType wipeType : wipeTypes) {
            AnimationWriter factory = registry.getFactory(wipeType);
            assertNotNull("Factory should not be null for " + wipeType, factory);
            assertEquals(wipeType + " should get WipeAnimationFactory", 
                        "WipeAnimationFactory", 
                        factory.getClass().getSimpleName());
        }
    }
    
    @Test
    public void testFadeAnimationFactorySelection() {
        AnimationWriter factory = registry.getFactory(AnimationType.FADE);
        
        assertNotNull("Factory should not be null for FADE", factory);
        assertEquals("FADE should get FadeAnimationFactory", 
                    "FadeAnimationFactory", 
                    factory.getClass().getSimpleName());
    }
    
    @Test
    public void testZoomAnimationFactorySelection() {
        AnimationWriter factory = registry.getFactory(AnimationType.ZOOM);
        
        assertNotNull("Factory should not be null for ZOOM", factory);
        assertEquals("ZOOM should get ZoomAnimationFactory", 
                    "ZoomAnimationFactory", 
                    factory.getClass().getSimpleName());
    }
    
    @Test
    public void testAppearAnimationFactorySelection() {
        AnimationWriter factory = registry.getFactory(AnimationType.APPEAR);
        
        assertNotNull("Factory should not be null for APPEAR", factory);
        assertEquals("APPEAR should get AppearAnimationFactory", 
                    "AppearAnimationFactory", 
                    factory.getClass().getSimpleName());
    }
    
    @Test
    public void testFlyAnimationFactorySelection() {
        AnimationType[] flyTypes = {
            AnimationType.FLY_IN_LEFT,
            AnimationType.FLY_IN_RIGHT,
            AnimationType.FLY_IN_TOP,
            AnimationType.FLY_IN_BOTTOM
        };
        
        for (AnimationType flyType : flyTypes) {
            AnimationWriter factory = registry.getFactory(flyType);
            assertNotNull("Factory should not be null for " + flyType, factory);
            assertEquals(flyType + " should get FlyAnimationFactory", 
                        "FlyAnimationFactory", 
                        factory.getClass().getSimpleName());
        }
    }
    
    @Test
    public void testFactorySupportsCorrectTypes() {
        // Test that factories only claim to support the types they should
        AnimationWriter wipeFactory = registry.getFactory(AnimationType.WIPE_LEFT);
        
        assertTrue("WipeAnimationFactory should support WIPE_LEFT", 
                  wipeFactory.supportsAnimationType(AnimationType.WIPE_LEFT));
        assertTrue("WipeAnimationFactory should support WIPE_RIGHT", 
                  wipeFactory.supportsAnimationType(AnimationType.WIPE_RIGHT));
        
        // Negative tests - should NOT support other types
        assertFalse("WipeAnimationFactory should NOT support FADE", 
                   wipeFactory.supportsAnimationType(AnimationType.FADE));
        assertFalse("WipeAnimationFactory should NOT support ZOOM", 
                   wipeFactory.supportsAnimationType(AnimationType.ZOOM));
        assertFalse("WipeAnimationFactory should NOT support APPEAR", 
                   wipeFactory.supportsAnimationType(AnimationType.APPEAR));
    }
    
    @Test
    public void testNoFactoryOverlap() {
        // Ensure no two factories claim to support the same animation type
        AnimationWriter fadeFactory = registry.getFactory(AnimationType.FADE);
        AnimationWriter wipeFactory = registry.getFactory(AnimationType.WIPE_LEFT);
        AnimationWriter zoomFactory = registry.getFactory(AnimationType.ZOOM);
        
        // These should be different factory instances
        assertNotSame("FADE and WIPE_LEFT should get different factories", 
                     fadeFactory, wipeFactory);
        assertNotSame("FADE and ZOOM should get different factories", 
                     fadeFactory, zoomFactory);
        assertNotSame("WIPE_LEFT and ZOOM should get different factories", 
                     wipeFactory, zoomFactory);
    }
    
    @Test
    public void testCanCreateAnimation() {
        // Test the convenience method
        assertTrue("Registry should be able to create WIPE_LEFT", 
                  registry.canCreateAnimation(AnimationType.WIPE_LEFT));
        assertTrue("Registry should be able to create FADE", 
                  registry.canCreateAnimation(AnimationType.FADE));
        assertTrue("Registry should be able to create ZOOM", 
                  registry.canCreateAnimation(AnimationType.ZOOM));
        assertTrue("Registry should be able to create APPEAR", 
                  registry.canCreateAnimation(AnimationType.APPEAR));
    }
    
    @Test
    public void testGetSupportedAnimationTypes() {
        Set<AnimationType> supportedTypeSet = registry.getSupportedAnimationTypes();
        AnimationType[] supportedTypes = supportedTypeSet.toArray(new AnimationType[0]);
        
        assertNotNull("Supported types should not be null", supportedTypes);
        assertTrue("Should support at least some animation types", supportedTypes.length > 0);
        
        // Check that critical types are supported
        boolean hasWipeLeft = false;
        boolean hasFade = false;
        boolean hasZoom = false;
        
        for (AnimationType type : supportedTypes) {
            if (type == AnimationType.WIPE_LEFT) hasWipeLeft = true;
            if (type == AnimationType.FADE) hasFade = true;
            if (type == AnimationType.ZOOM) hasZoom = true;
        }
        
        assertTrue("Should support WIPE_LEFT", hasWipeLeft);
        assertTrue("Should support FADE", hasFade);
        assertTrue("Should support ZOOM", hasZoom);
    }
    
    @Test
    public void testGetDefaultFactory() {
        AnimationWriter defaultFactory = registry.getDefaultFactory();
        
        assertNotNull("Default factory should not be null", defaultFactory);
        
        // Default should be FADE factory
        AnimationType defaultType = registry.getDefaultAnimationType();
        assertEquals("Default animation type should be FADE", AnimationType.FADE, defaultType);
        
        AnimationWriter fadeFactory = registry.getFactory(AnimationType.FADE);
        assertSame("Default factory should be same as FADE factory", 
                  defaultFactory, fadeFactory);
    }
    
    @Test  
    public void testRegistryInitialization() {
        // Test that registry properly initializes with default factories
        // This verifies that the reflection-based loading works correctly
        
        assertNotNull("Registry should not be null after construction", registry);
        
        // Check that all expected factory types are loaded
        AnimationWriter[] factories = {
            registry.getFactory(AnimationType.APPEAR),
            registry.getFactory(AnimationType.FADE), 
            registry.getFactory(AnimationType.WIPE_LEFT),
            registry.getFactory(AnimationType.ZOOM),
            registry.getFactory(AnimationType.FLY_IN_LEFT)
        };
        
        for (AnimationWriter factory : factories) {
            assertNotNull("Each factory should be loaded successfully", factory);
        }
        
        // Verify they're different instances (no duplicate registration)
        for (int i = 0; i < factories.length; i++) {
            for (int j = i + 1; j < factories.length; j++) {
                if (factories[i] != null && factories[j] != null && 
                    !factories[i].getClass().equals(factories[j].getClass())) {
                    assertNotSame("Different factory types should be different instances", 
                                 factories[i], factories[j]);
                }
            }
        }
    }
}