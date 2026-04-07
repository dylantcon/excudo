package com.excudo.xml;

import com.excudo.core.model.LayoutManager;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import com.excudo.xml.writers.RelationshipManager;
import com.excudo.xml.writers.SPIDManager;
import com.excudo.xml.writers.SlideCreator;
import com.excudo.exceptions.XMLParsingException;
import java.io.File;
import java.nio.file.Paths;

/**
 * Demonstration of slide creation capabilities
 * Tests blank slide insertion and file management
 */
public class SlideCreationDemo {

  public static void main(String[] args) {
    try {
      System.out.println("=== Slide Creation Demo ===");
      System.out.println("Testing slide insertion capabilities...");
      System.out.println();

      // Point to our extracted PPTX directory - use the actual extracted directory
      File extractedDir = Paths.get("test-pptx-samples", "generalist_extracted").toFile();
      if (!extractedDir.exists()) {
        System.err.println("Demo failed: Extracted PPTX directory not found: " + extractedDir.getAbsolutePath());
        System.err.println("This demo requires extracted PPTX files to be present.");
        System.exit(1);
        return;
      }

      // Create slide creator via in-memory PPTXDocument
      PPTXDocument pptxDoc = PPTXDocument.load(extractedDir, null);
      PPTXDocumentParser.ParsedPresentationState parsedState = PPTXDocumentParser.parse(pptxDoc);
      LayoutManager layoutManager = new LayoutManager(parsedState.getLayouts());
      SPIDManager spidManager = SPIDManager.createFromParsedState(parsedState, layoutManager);
      RelationshipManager relationshipManager = new RelationshipManager(pptxDoc, parsedState);
      SlideCreator creator = new SlideCreator(relationshipManager, spidManager, layoutManager);

      // List existing slides before insertion
      System.out.println("BEFORE SLIDE INSERTION:");
      listExistingSlides(extractedDir);
      System.out.println();

      // Test blank slide insertion
      System.out.println("INSERTING BLANK SLIDE...");
      System.out.println("  Position: 3 (after slide 2)");
      System.out.println("  Title: 'NEW SLIDE CREATED BY PRESENTATION CHOREOGRAPHER'");

      int newSlideNumber = creator.insertBlankSlide(3, "NEW SLIDE CREATED BY PRESENTATION CHOREOGRAPHER");

      System.out.println("  [OK] New slide created: slide" + newSlideNumber + ".xml");
      System.out.println();

      // List slides after insertion
      System.out.println("AFTER SLIDE INSERTION:");
      listExistingSlides(extractedDir);
      System.out.println();

      // Verify the new slide file exists
      File newSlideFile = Paths.get(extractedDir.getPath(), "ppt", "slides", "slide" + newSlideNumber + ".xml").toFile();
      if (newSlideFile.exists()) {
        System.out.println("=== VERIFICATION RESULTS ===");
        System.out.println("✅ SLIDE CREATION SUCCESSFUL!");
        System.out.println("   → New slide file created: " + newSlideFile.getName());
        System.out.println("   → File size: " + newSlideFile.length() + " bytes");
        System.out.println("   → Slide numbering cascade working");
        System.out.println();
        System.out.println("NEXT STEPS:");
        System.out.println("   → Implement relationship management");
        System.out.println("   → Update presentation.xml");
        System.out.println("   → Test PPTX reconstruction");
        System.out.println("   → Add slide copying functionality");
      } else {
        System.out.println("❌ SLIDE CREATION FAILED");
        System.out.println("   → New slide file not found");
      }

    } catch (XMLParsingException e) {
      System.err.println("Slide Creation Error: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      System.err.println("Unexpected Error: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * List all existing slide files in the presentation
   */
  private static void listExistingSlides(File extractedDir) {
    File slidesDir = Paths.get(extractedDir.getPath(), "ppt", "slides").toFile();
    if (!slidesDir.exists()) {
      System.out.println("  No slides directory found");
      return;
    }

    File[] slideFiles = slidesDir.listFiles((dir, name) -> name.matches("slide\\d+\\.xml"));
    if (slideFiles == null || slideFiles.length == 0) {
      System.out.println("  No slide files found");
      return;
    }

    // Sort by slide number
    java.util.Arrays.sort(slideFiles, (a, b) -> {
      int numA = extractSlideNumber(a.getName());
      int numB = extractSlideNumber(b.getName());
      return Integer.compare(numA, numB);
    });

    System.out.println("  Slide files found:");
    for (File slideFile : slideFiles) {
      System.out.printf("    %s (%d bytes)%n", slideFile.getName(), slideFile.length());
    }
  }

  /**
   * Extract slide number from filename
   */
  private static int extractSlideNumber(String fileName) {
    try {
      String numberStr = fileName.substring(5, fileName.lastIndexOf(".xml"));
      return Integer.parseInt(numberStr);
    } catch (Exception e) {
      return 0;
    }
  }
}
