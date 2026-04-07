package com.excudo;

import com.excudo.core.orchestration.*;
import com.excudo.core.results.SlideExecutionResult;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.results.ExecutionResult;
import com.excudo.xml.writers.SlideXMLWriter;
import com.excudo.xml.parsers.SlideXMLParser;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

public class AnimationTest {
    private PPTXOrchestrator orchestrator;
    
    @Before
    public void setUp() throws Exception {
        orchestrator = new PPTXOrchestratorImpl();
    }
    
    @Test
    public void testAnimationInjection() throws Exception {
        System.out.println("=== Animation Structure Test ===");
            
            // Test file path - use the existing generalist test file
            String testFile = "test-pptx-samples/generalist_test_file.pptx";
            String outputFile = "test-pptx-samples/animations-crud/raw/generalist_animation_test.pptx";
            new File(outputFile).getParentFile().mkdirs();

            // Check if test file exists before proceeding
            File testPptx = new File(testFile);
            if (!testPptx.exists()) {
                System.out.println("Test skipped: Required test file not found: " + testFile);
                System.out.println("This test requires test-pptx-samples/generalist_test_file.pptx to be present.");
                // Skip test gracefully
                return;
            }
            
            // orchestrator already created in setUp()
            
            // Load the PPTX
            System.out.println("Loading PPTX...");
            ExecutionResult loadResult = orchestrator.loadPresentation(new File(testFile));
            assertTrue("Failed to load presentation: " + loadResult.getMessage(), 
                      loadResult.isSuccess());
            
            // Get slide 2 DOM (works for both in-memory and disk paths)
            OrchestrationContext ctx = orchestrator.getContext().get();
            Document doc = ctx.getSlideDocument(2);
            assertNotNull("Slide 2 not found!", doc);

            System.out.println("Found slide 2 DOM");
            
            SlideXMLWriter writer = new SlideXMLWriter(doc);
            
            ShapeGeometry geo = new ShapeGeometry(0, 0, 914400, 914400);

            // Add animations to the existing text box (SPID 15)
            System.out.println("Adding fade in animation (click 1)...");
            AnimationBinding fadeIn = AnimationBinding.builder()
                .target(15).type("fade").entrance()
                .duration("500").delay("0").clickTrigger(1).animationGroup("on-click").build();
            writer.injectAnimation(fadeIn, geo);

            System.out.println("Adding fade out animation (click 2)...");
            AnimationBinding fadeOut = AnimationBinding.builder()
                .target(15).type("fade").exit()
                .duration("500").delay("0").clickTrigger(2).animationGroup("on-click").build();
            writer.injectAnimation(fadeOut, geo);
            
            // Write the modified DOM back through context
            ctx.putXmlPart("ppt/slides/slide2.xml", doc);
            System.out.println("Modified slide XML written.");
            
            // Save the PPTX
            System.out.println("Saving modified PPTX...");
            ExecutionResult<File> saveResult = orchestrator.savePresentation(new File(outputFile));
            assertTrue("Failed to save presentation: " + saveResult.getMessage(),
                      saveResult.isSuccess());
            
            System.out.println("\n[OK] Test complete!");
            System.out.println("Output file: " + outputFile);
            
            // Verify output file was created
            File output = new File(outputFile);
            assertTrue("Output file should exist", output.exists());
            assertTrue("Output file should have content", output.length() > 0);
    }
}