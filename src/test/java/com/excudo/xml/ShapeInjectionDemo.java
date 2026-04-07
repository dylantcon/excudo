package com.excudo.xml;

import com.excudo.xml.parsers.SlideXMLParser;
import com.excudo.xml.writers.SlideXMLWriter;
import com.excudo.core.model.*;
import com.excudo.exceptions.XMLParsingException;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.io.File;
import java.nio.file.Paths;

/**
 * Demonstration of shape injection functionality
 * Tests the XML Writer's ability to inject new shapes into existing slides
 */
public class ShapeInjectionDemo {

  public static void main(String[] args) {
    try {
      System.out.println("=== Shape Injection Demo ===");
      System.out.println("Testing XML modification capabilities...");
      System.out.println();

      // Load the original slide - use the actual extracted directory
      File originalSlide = Paths.get("test-pptx-samples", "generalist_extracted", "ppt", "slides", "slide2.xml").toFile();
      if (!originalSlide.exists()) {
        System.err.println("Demo failed: Original slide not found: " + originalSlide.getAbsolutePath());
        System.err.println("This demo requires extracted PPTX files to be present.");
        System.exit(1);
        return;
      }

      // Parse the original slide to understand its structure
      SlideXMLParser parser = new SlideXMLParser();
      ParsedSlideData originalData = parser.parseSlide(originalSlide);

      System.out.println("BEFORE INJECTION:");
      System.out.println("  Shapes: " + originalData.getShapeRegistry().getShapeCount());
      System.out.println("  Text shapes: " + originalData.getShapeRegistry().getTextShapes().size());
      System.out.println();

      // Load the document for modification
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.parse(originalSlide);

      // Create the XML writer
      SlideXMLWriter writer = new SlideXMLWriter(document);

      // Inject a test shape
      System.out.println("INJECTING TEST SHAPE...");
      ShapeGeometry testGeometry = new ShapeGeometry(
          3000000L,  // X position (EMUs)
          1000000L,  // Y position
          2000000L,  // Width
          800000L    // Height
          );

      int newSpid = writer.injectBasicShapeWithSlideContext(
          SlideShape.ShapeType.RECTANGLE,
          testGeometry, 
          "INJECTED BY PRESENTATION CHOREOGRAPHER!", 
          "Test Injection Shape",
          2
          );

      System.out.println("  New shape created with SPID: " + newSpid);
      System.out.println("  Position: " + testGeometry);
      System.out.println();

      // Write the modified slide
      File modifiedSlide = Paths.get("test-pptx-samples", "slide2_modified.xml").toFile();
      writer.writeXML(modifiedSlide);

      System.out.println("INJECTION COMPLETE:");
      System.out.println("  Modified slide saved to: " + modifiedSlide.getName());
      System.out.println();

      // Parse the modified slide to verify changes
      ParsedSlideData modifiedData = parser.parseSlide(modifiedSlide);

      System.out.println("AFTER INJECTION:");
      System.out.println("  Shapes: " + modifiedData.getShapeRegistry().getShapeCount());
      System.out.println("  Text shapes: " + modifiedData.getShapeRegistry().getTextShapes().size());

      // Find our injected shape
      SlideShape injectedShape = modifiedData.getShapeRegistry().getShape(newSpid);
      if (injectedShape != null) {
        System.out.println("  [OK] Injected shape found!");
        System.out.println("    SPID: " + injectedShape.getSpid());
        System.out.println("    Name: " + injectedShape.getName());
        System.out.println("    Text: \"" + injectedShape.getTextContent() + "\"");
        System.out.println("    Position: " + injectedShape.getGeometry());
      } else {
        System.out.println("  [FAIL] Injected shape not found!");
      }

      System.out.println();
      System.out.println("=== VERIFICATION RESULTS ===");

      int shapeIncrease = modifiedData.getShapeRegistry().getShapeCount() - 
        originalData.getShapeRegistry().getShapeCount();
      int textShapeIncrease = modifiedData.getShapeRegistry().getTextShapes().size() - 
        originalData.getShapeRegistry().getTextShapes().size();

      if (shapeIncrease == 1 && textShapeIncrease == 1 && injectedShape != null) {
        System.out.println("[SUCCESS] SHAPE INJECTION SUCCESSFUL!");
        System.out.println("   → XML modification pipeline working correctly");
        System.out.println("   → Ready for animation injection development");
      } else {
        System.out.println("❌ SHAPE INJECTION ISSUES DETECTED");
        System.out.printf("   → Shape count increase: %d (expected: 1)%n", shapeIncrease);
        System.out.printf("   → Text shape increase: %d (expected: 1)%n", textShapeIncrease);
        System.out.printf("   → Injected shape found: %s%n", injectedShape != null);
      }

    } catch (XMLParsingException e) {
      System.err.println("XML Parsing Error: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      System.err.println("Unexpected Error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
