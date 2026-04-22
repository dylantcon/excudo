package com.excudo.core.geometry;

import com.excudo.core.commands.mutating.slide.ResizeShapeCommand;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResizeShapeCommandTest {

    private StubPPTXOrchestrator createOrchestratorWithShape() {
        return new StubPPTXOrchestrator() {
            private ShapeGeometry currentGeometry = new ShapeGeometry(100, 200, 500, 300);

            @Override
            public ExecutionResult<ShapeRegistry> getShapeRegistry(int slideNumber) {
                ShapeRegistry registry = new ShapeRegistry();
                registry.addShape(new SlideShape(5, "TestShape", SlideShape.ShapeType.RECTANGLE,
                    "text", currentGeometry, null));
                return ExecutionResult.success("GetShapeRegistry", registry);
            }

            @Override
            public ExecutionResult<Void> updateShapeGeometry(int slideNumber, int spid, ShapeGeometry newGeometry) {
                currentGeometry = newGeometry;
                return ExecutionResult.success("UpdateShapeGeometry", null);
            }
        };
    }

    @Test
    public void testExecuteResizesShape() {
        ResizeShapeCommand cmd = new ResizeShapeCommand(1, 5, 800, 600, createOrchestratorWithShape());
        cmd.execute();
        assertTrue(cmd.isExecuted());
        assertTrue(cmd.canUndo());
    }

    @Test
    public void testUndoRestoresOriginal() {
        ResizeShapeCommand cmd = new ResizeShapeCommand(1, 5, 800, 600, createOrchestratorWithShape());
        cmd.execute();
        cmd.undo();
        assertFalse(cmd.isExecuted());
    }

    @Test
    public void testDescription() {
        ResizeShapeCommand cmd = new ResizeShapeCommand(1, 5, 800, 600, createOrchestratorWithShape());
        assertTrue(cmd.getDescription().contains("Resize"));
        assertTrue(cmd.getDescription().contains("5"));
    }

    @Test(expected = CommandExecutionException.class)
    public void testDoubleExecuteThrows() {
        ResizeShapeCommand cmd = new ResizeShapeCommand(1, 5, 800, 600, createOrchestratorWithShape());
        cmd.execute();
        cmd.execute();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullOrchestrator() {
        new ResizeShapeCommand(1, 5, 800, 600, null);
    }
}
