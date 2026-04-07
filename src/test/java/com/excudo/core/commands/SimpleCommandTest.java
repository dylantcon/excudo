package com.excudo.core.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to isolate the CompositeCommand issue
 */
public class SimpleCommandTest {
    
    @TempDir
    File tempDir;
    
    @Test
    void testCompositeCommandExecution() {
        // Create two simple stub commands that actually succeed
        Command cmd1 = new Command() {
            private boolean executed = false;
            
            @Override
            public void execute() {
                System.out.println("Executing command 1");
                executed = true;
            }
            
            @Override
            public void undo() {
                System.out.println("Undoing command 1");
                executed = false;
            }
            
            @Override
            public boolean canUndo() {
                return executed;
            }
            
            @Override
            public String getDescription() {
                return "Test Command 1";
            }
            
            @Override
            public boolean isExecuted() {
                return executed;
            }
        };
        
        Command cmd2 = new Command() {
            private boolean executed = false;
            
            @Override
            public void execute() {
                System.out.println("Executing command 2");
                executed = true;
            }
            
            @Override
            public void undo() {
                System.out.println("Undoing command 2");
                executed = false;
            }
            
            @Override
            public boolean canUndo() {
                return executed;
            }
            
            @Override
            public String getDescription() {
                return "Test Command 2";
            }
            
            @Override
            public boolean isExecuted() {
                return executed;
            }
        };
        
        // Create composite command
        CompositeCommand composite = new CompositeCommand(List.of(cmd1, cmd2), "Test Composite");
        
        // Verify initial state
        assertFalse(composite.isExecuted());
        assertFalse(composite.canUndo());
        
        // Execute composite
        composite.execute();
        
        // Verify execution
        assertTrue(composite.isExecuted(), "Composite should be executed");
        assertTrue(cmd1.isExecuted(), "Command 1 should be executed");
        assertTrue(cmd2.isExecuted(), "Command 2 should be executed");
        assertTrue(composite.canUndo(), "Should be able to undo");
        
        // Test undo
        composite.undo();
        assertFalse(composite.isExecuted(), "Composite should not be executed after undo");
        assertFalse(cmd1.isExecuted(), "Command 1 should not be executed after undo");
        assertFalse(cmd2.isExecuted(), "Command 2 should not be executed after undo");
    }
}