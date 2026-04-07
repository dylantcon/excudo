package com.excudo.view.animation;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Unit tests for AnimationPlaybackListener interface.
 * Tests the default implementations and interface contract.
 */
public class AnimationPlaybackListenerTest {
    
    @Test
    public void testDefaultErrorHandling() {
        // Create a concrete implementation to test the default method
        AnimationPlaybackListener listener = new TestAnimationPlaybackListener();
        
        // Capture System.err output to verify default error handling
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));
        
        try {
            // Test the default onPlaybackError implementation
            Exception testError = new RuntimeException("Playback test error");
            String context = "playback test context";
            
            listener.onPlaybackError(testError, context);
            
            // Verify the default implementation printed the error
            String errorOutput = errContent.toString();
            assertTrue("Should contain context", errorOutput.contains("playback test context"));
            assertTrue("Should contain error message", errorOutput.contains("Playback test error"));
            assertTrue("Should contain 'Animation playback error'", errorOutput.contains("Animation playback error"));
            
        } finally {
            // Restore original System.err
            System.setErr(originalErr);
        }
    }
    
    @Test
    public void testDefaultProgressHandling() {
        // Test that the default onPlaybackProgress method can be called without errors
        AnimationPlaybackListener listener = new TestAnimationPlaybackListener();
        
        try {
            // Test various progress scenarios
            listener.onPlaybackProgress(0, 5, 0.0);    // Start
            listener.onPlaybackProgress(2, 5, 0.4);    // Middle
            listener.onPlaybackProgress(5, 5, 1.0);    // Complete
            listener.onPlaybackProgress(-1, 0, 0.0);   // Edge case
            
            // Default implementation should not throw exceptions
            assertTrue("Default progress handling should work", true);
            
        } catch (Exception e) {
            fail("Default onPlaybackProgress should not throw exceptions: " + e.getMessage());
        }
    }
    
    @Test
    public void testInterfaceMethods() {
        // Test that all interface methods can be called without errors
        TestAnimationPlaybackListener listener = new TestAnimationPlaybackListener();
        
        try {
            // Test all required interface methods
            listener.onSequencesBuilt(3);
            listener.onPlaybackStarted();
            listener.onPlaybackPaused();
            listener.onPlaybackResumed();
            listener.onPlaybackStopped();
            listener.onSequencePlayed(1);
            listener.onAnimationCompleted(789, true);
            listener.onAnimationCompleted(101112, false);
            
            // Verify calls were tracked
            assertEquals("Should track onSequencesBuilt calls", 1, listener.sequencesBuiltCallCount);
            assertEquals("Should track onPlaybackStarted calls", 1, listener.playbackStartedCallCount);
            assertEquals("Should track onPlaybackPaused calls", 1, listener.playbackPausedCallCount);
            assertEquals("Should track onPlaybackResumed calls", 1, listener.playbackResumedCallCount);
            assertEquals("Should track onPlaybackStopped calls", 1, listener.playbackStoppedCallCount);
            assertEquals("Should track onSequencePlayed calls", 1, listener.sequencePlayedCallCount);
            assertEquals("Should track onAnimationCompleted calls", 2, listener.animationCompletedCallCount);
            
        } catch (Exception e) {
            fail("Interface methods should not throw exceptions: " + e.getMessage());
        }
    }
    
    @Test
    public void testParameterEdgeCases() {
        // Test edge cases for parameters
        TestAnimationPlaybackListener listener = new TestAnimationPlaybackListener();
        
        // Test boundary values
        listener.onSequencesBuilt(0);              // No sequences
        listener.onSequencesBuilt(Integer.MAX_VALUE); // Large number
        
        listener.onSequencePlayed(-1);             // Invalid trigger
        listener.onSequencePlayed(0);              // First trigger
        
        listener.onAnimationCompleted(0, true);    // Shape ID 0, entrance
        listener.onAnimationCompleted(-1, false);  // Negative shape ID, exit
        
        // Test progress with edge values
        listener.onPlaybackProgress(0, 0, 0.0);    // No triggers
        listener.onPlaybackProgress(1, 1, 1.0);    // Single trigger complete
        listener.onPlaybackProgress(5, 3, 2.0);    // Invalid progress > 1.0
        listener.onPlaybackProgress(0, 10, -0.5);  // Negative progress
        
        // All calls should complete without exceptions
        assertTrue("Edge case parameters should be handled gracefully", true);
    }
    
    @Test
    public void testDefaultMethodBehavior() {
        // Verify that default methods have the expected signatures and behavior
        AnimationPlaybackListener listener = new TestAnimationPlaybackListener();
        
        // Test that default methods are actually default (can be called on interface)
        AnimationPlaybackListener directInterface = new AnimationPlaybackListener() {
            @Override
            public void onSequencesBuilt(int sequenceCount) {}
            
            @Override
            public void onPlaybackStarted() {}
            
            @Override
            public void onPlaybackPaused() {}
            
            @Override
            public void onPlaybackResumed() {}
            
            @Override
            public void onPlaybackStopped() {}
            
            @Override
            public void onSequencePlayed(int triggerIndex) {}
            
            @Override
            public void onAnimationCompleted(int spid, boolean isEntrance) {}
        };
        
        // These default methods should be callable without implementation
        directInterface.onPlaybackError(new Exception("test"), "test");
        directInterface.onPlaybackProgress(1, 2, 0.5);
        
        assertTrue("Default methods should be available on interface", true);
    }
    
    /**
     * Concrete test implementation of AnimationPlaybackListener for testing
     */
    private static class TestAnimationPlaybackListener implements AnimationPlaybackListener {
        int sequencesBuiltCallCount = 0;
        int playbackStartedCallCount = 0;
        int playbackPausedCallCount = 0;
        int playbackResumedCallCount = 0;
        int playbackStoppedCallCount = 0;
        int sequencePlayedCallCount = 0;
        int animationCompletedCallCount = 0;
        
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
    }
}