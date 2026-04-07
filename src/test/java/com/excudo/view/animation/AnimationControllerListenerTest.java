package com.excudo.view.animation;

import org.junit.Test;
import static org.junit.Assert.*;

import com.excudo.core.model.ParsedSlideData;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Unit tests for AnimationControllerListener interface.
 * Tests the default implementation and interface contract.
 */
public class AnimationControllerListenerTest {
    
    @Test
    public void testDefaultErrorHandling() {
        // Create a concrete implementation to test the default method
        AnimationControllerListener listener = new TestAnimationControllerListener();
        
        // Capture System.err output to verify default error handling
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));
        
        try {
            // Test the default onControllerError implementation
            Exception testError = new RuntimeException("Test error message");
            String context = "test context";
            
            listener.onControllerError(testError, context);
            
            // Verify the default implementation printed the error
            String errorOutput = errContent.toString();
            assertTrue("Should contain context", errorOutput.contains("test context"));
            assertTrue("Should contain error message", errorOutput.contains("Test error message"));
            assertTrue("Should contain 'Animation controller error'", errorOutput.contains("Animation controller error"));
            
        } finally {
            // Restore original System.err
            System.setErr(originalErr);
        }
    }
    
    @Test
    public void testInterfaceMethods() {
        // Test that all interface methods can be called without errors
        AnimationControllerListener listener = new TestAnimationControllerListener();
        
        // Test with null slide data (no mocking needed)
        ParsedSlideData nullSlideData = null;
        
        try {
            // Test all interface methods
            listener.onAnimationsLoaded(nullSlideData);
            listener.onSequencesBuilt(5);
            listener.onPlaybackStarted();
            listener.onPlaybackPaused();
            listener.onPlaybackResumed();
            listener.onPlaybackStopped();
            listener.onSequencePlayed(2);
            listener.onAnimationCompleted(123, true);
            listener.onAnimationCompleted(456, false);
            listener.onAutoAdvanceChanged(true);
            listener.onAutoAdvanceChanged(false);
            
            // All methods should complete without throwing exceptions
            assertTrue("All interface methods should be callable", true);
            
        } catch (Exception e) {
            fail("Interface methods should not throw exceptions: " + e.getMessage());
        }
    }
    
    @Test
    public void testParameterHandling() {
        // Test that the interface methods handle various parameter values correctly
        TestAnimationControllerListener listener = new TestAnimationControllerListener();
        
        // Test edge cases for numeric parameters
        listener.onSequencesBuilt(0);  // No sequences
        listener.onSequencesBuilt(Integer.MAX_VALUE);  // Large number
        
        listener.onSequencePlayed(-1);  // Invalid trigger index
        listener.onSequencePlayed(0);   // First trigger
        
        listener.onAnimationCompleted(0, true);    // Shape ID 0
        listener.onAnimationCompleted(-1, false);  // Negative shape ID
        
        // Test null parameters where applicable
        listener.onAnimationsLoaded(null);  // Should handle null gracefully
        
        // Verify listener tracked the calls
        assertEquals("Should track onSequencesBuilt calls", 2, listener.sequencesBuiltCallCount);
        assertEquals("Should track onSequencePlayed calls", 2, listener.sequencePlayedCallCount);
        assertEquals("Should track onAnimationCompleted calls", 2, listener.animationCompletedCallCount);
        assertEquals("Should track onAnimationsLoaded calls", 1, listener.animationsLoadedCallCount);
    }
    
    /**
     * Concrete test implementation of AnimationControllerListener for testing
     */
    private static class TestAnimationControllerListener implements AnimationControllerListener {
        int animationsLoadedCallCount = 0;
        int sequencesBuiltCallCount = 0;
        int playbackStartedCallCount = 0;
        int playbackPausedCallCount = 0;
        int playbackResumedCallCount = 0;
        int playbackStoppedCallCount = 0;
        int sequencePlayedCallCount = 0;
        int animationCompletedCallCount = 0;
        int autoAdvanceChangedCallCount = 0;
        
        @Override
        public void onAnimationsLoaded(ParsedSlideData slideData) {
            animationsLoadedCallCount++;
        }
        
        @Override
        public void onSequencesBuilt(int sequenceCount) {
            sequencesBuiltCallCount++;
        }
        
        @Override
        public void onPlaybackStarted() {
            playbackStartedCallCount++;
        }
        
        @Override
        public void onPlaybackPaused() {
            playbackPausedCallCount++;
        }
        
        @Override
        public void onPlaybackResumed() {
            playbackResumedCallCount++;
        }
        
        @Override
        public void onPlaybackStopped() {
            playbackStoppedCallCount++;
        }
        
        @Override
        public void onSequencePlayed(int triggerIndex) {
            sequencePlayedCallCount++;
        }
        
        @Override
        public void onAnimationCompleted(int spid, boolean isEntrance) {
            animationCompletedCallCount++;
        }
        
        @Override
        public void onAutoAdvanceChanged(boolean enabled) {
            autoAdvanceChangedCallCount++;
        }
    }
}