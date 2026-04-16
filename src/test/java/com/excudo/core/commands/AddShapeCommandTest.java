package com.excudo.core.commands;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.orchestration.SlideMetadata;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests AddShapeCommand for various shape types.
 */
public class AddShapeCommandTest {

    private PPTXOrchestratorImpl orchestrator;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Test Slide", "slideLayout2");
    }

    @Test
    public void addRectangleShape() {
        int beforeCount = getShapeCount(1);
        var result = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1000000, 1000000, 2000000, 1500000), null, "TestRect");
        assertTrue("Add rect should succeed", result.isSuccess());
        assertTrue("Shape count should increase", getShapeCount(1) > beforeCount);
    }

    @Test
    public void addEllipseShape() {
        var result = orchestrator.addShape(1, SlideShape.ShapeType.ELLIPSE,
            new ShapeGeometry(1000000, 1000000, 2000000, 2000000), null, "TestEllipse");
        assertTrue("Add ellipse should succeed", result.isSuccess());
    }

    @Test
    public void addShapeWithText() {
        var result = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1000000, 1000000, 3000000, 1000000), "Hello World", "TextRect");
        assertTrue("Add with text should succeed", result.isSuccess());
    }

    @Test
    public void addMultipleShapes() {
        int before = getShapeCount(1);
        orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(100000, 100000, 200000, 200000), null, "R1");
        orchestrator.addShape(1, SlideShape.ShapeType.ELLIPSE,
            new ShapeGeometry(300000, 100000, 200000, 200000), null, "E1");
        orchestrator.addShape(1, SlideShape.ShapeType.TRIANGLE,
            new ShapeGeometry(500000, 100000, 200000, 200000), null, "T1");

        assertTrue("Should have added 3 shapes", getShapeCount(1) >= before + 3);
    }

    @Test
    public void addShapeReturnsSPID() {
        var result = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 100000, 100000), null, "NewShape");
        assertTrue(result.isSuccess());
        assertTrue("Should return assigned SPID", result.getData().isPresent());
        assertTrue("SPID should be positive", result.getData().get() > 0);
    }

    private int getShapeCount(int slideNumber) {
        List<SlideMetadata> slides = orchestrator.getAllSlideMetadata();
        for (SlideMetadata slide : slides) {
            if (slide.getSlideNumber() == slideNumber) {
                return slide.getShapeCount();
            }
        }
        return -1;
    }
}
