package com.excudo.demo;

import com.excudo.xml.writers.SlideNotesWriter;
import com.excudo.core.llm.LLMIntegrationService;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import java.nio.file.*;

/**
 * Demonstrates slide notes functionality with icon attribution
 */
public class SlideNotesDemo {
    
    public static void main(String[] args) {
        try {
            // Test presentation path - use an available test file
            Path testPptxPath = Paths.get("test-pptx-samples", "generalist_test_file.pptx");
            
            // Check if source file exists before proceeding
            if (!Files.exists(testPptxPath)) {
                System.out.println("Demo skipped: Source PPTX file not found: " + testPptxPath.toAbsolutePath());
                System.out.println("This demo requires test PPTX files to be present.");
                return; // Exit gracefully without System.exit(1)
            }
            
            // Create a copy for testing
            Path testFile = Paths.get("test-output", "notes_demo.pptx");
            Files.createDirectories(testFile.getParent());
            Files.copy(testPptxPath, testFile, StandardCopyOption.REPLACE_EXISTING);
            
            System.out.println("=== Slide Notes Attribution Demo ===\n");
            
            // Initialize notes writer
            SlideNotesWriter notesWriter = new SlideNotesWriter(testFile.toString());
            
            // Test 1: Read existing notes
            System.out.println("1. Reading existing notes from slide 1:");
            String existingNotes = notesWriter.getSlideNotes(1);
            System.out.println("   Current notes: " + existingNotes);
            
            // Test 2: Add icon attribution to slide 1
            System.out.println("\n2. Adding icon attribution to slide 1:");
            notesWriter.addIconAttribution(
                1,
                "https://example.com/icons/email.svg",
                "Icon: Email | Source: Feather Icons | License: MIT License"
            );
            System.out.println("   [OK] Attribution added");
            
            // Test 3: Add multiple attributions
            System.out.println("\n3. Adding multiple icon attributions to slide 2:");
            notesWriter.addIconAttribution(
                2,
                "https://example.com/icons/phone.svg",
                "Icon: Phone | Source: Iconify | License: Apache 2.0"
            );
            notesWriter.addIconAttribution(
                2,
                "https://example.com/icons/location.svg",
                "Icon: Location Pin | Source: OpenMoji | License: CC BY-SA 4.0"
            );
            System.out.println("   [OK] Multiple attributions added");
            
            // Test 4: Verify attributions were added
            System.out.println("\n4. Verifying attributions:");
            String slide1Notes = notesWriter.getSlideNotes(1);
            System.out.println("   Slide 1 notes now contain:");
            System.out.println("   " + slide1Notes.replace("\n", "\n   "));
            
            String slide2Notes = notesWriter.getSlideNotes(2);
            System.out.println("\n   Slide 2 notes now contain:");
            System.out.println("   " + slide2Notes.replace("\n", "\n   "));
            
            // Test 5: Demonstrate LLM integration
            System.out.println("\n5. LLM Integration with Icon Attribution:");
            System.out.println("   When the LLM creates enhanced content with icons,");
            System.out.println("   attribution is automatically added to slide notes.");
            System.out.println("   This ensures proper credit for open-source icons.");
            
            System.out.println("\n=== Demo Complete ===");
            System.out.println("Output saved to: " + testFile);
            System.out.println("\nTo verify in PowerPoint:");
            System.out.println("1. Open " + testFile);
            System.out.println("2. Go to View > Notes Page");
            System.out.println("3. Check the notes section below each slide");
            
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}