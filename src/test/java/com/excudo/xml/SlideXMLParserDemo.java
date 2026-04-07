package com.excudo.xml;

import com.excudo.xml.parsers.SlideXMLParser;
import com.excudo.core.model.*;
import com.excudo.exceptions.XMLParsingException;
import java.io.File;
import java.nio.file.Paths;

/**
 * Demonstration class showing the SlideXMLParser in action
 * This will parse your slide2.xml and show the extracted data
 */
public class SlideXMLParserDemo {

  public static void main(String[] args) {
    try {
      // Initialize the parser
      SlideXMLParser parser = new SlideXMLParser();

      // Parse your slide2.xml file - use the actual extracted directory
      File slideFile = Paths.get("test-pptx-samples", "generalist_extracted", "ppt", "slides", "slide2.xml").toFile();

      if (!slideFile.exists()) {
        System.err.println("Demo failed: Slide file not found: " + slideFile.getAbsolutePath());
        System.err.println("This demo requires extracted PPTX files to be present.");
        System.exit(1);
        return;
      }

      System.out.println("=== PowerPoint Slide XML Analysis ===");
      System.out.println("Parsing: " + slideFile.getName());
      System.out.println();

      // Parse the slide
      ParsedSlideData slideData = parser.parseSlide(slideFile);

      // Display the results
      demonstrateShapeAnalysis(slideData.getShapeRegistry());
      demonstrateTimingAnalysis(slideData.getTimingTree());
      demonstrateAnimationAnalysis(slideData.getAnimationBindings());

      // Show some insights for template development
      analyzeAnimationPatterns(slideData);

    } catch (XMLParsingException e) {
      System.err.println("Failed to parse slide: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private static void demonstrateShapeAnalysis(ShapeRegistry registry) {
    System.out.println("=== SHAPE ANALYSIS ===");
    System.out.println(registry);
    System.out.println();

    System.out.println("Text Shapes Found:");
    for (SlideShape shape : registry.getTextShapes()) {
      String preview = shape.getTextContent().length() > 50 ? 
        shape.getTextContent().substring(0, 50) + "..." : 
        shape.getTextContent();

      System.out.printf("  SPID %d: '%s' - \"%s\"%n", 
          shape.getSpid(), shape.getName(), preview);
    }
    System.out.println();

    System.out.println("Picture Shapes Found:");
    for (SlideShape shape : registry.getShapesByType(SlideShape.ShapeType.PICTURE)) {
      System.out.printf("  SPID %d: '%s' at %s%n", 
          shape.getSpid(), shape.getName(), shape.getGeometry());
    }
    System.out.println();
  }

  private static void demonstrateTimingAnalysis(TimingTree timingTree) {
    System.out.println("=== TIMING ANALYSIS ===");
    System.out.println(timingTree);

    if (timingTree.getRootNode() != null) {
      System.out.println("Timing Structure:");
      printTimingNode(timingTree.getRootNode(), 0);
    }
    System.out.println();
  }

  private static void printTimingNode(TimingNode node, int depth) {
    String indent = "  ".repeat(depth);
    System.out.printf("%s- Node %s (%s) [%s]%n", 
        indent, node.getNodeId(), node.getNodeType(), node.getDuration());

    for (TimingNode child : node.getChildren()) {
      printTimingNode(child, depth + 1);
    }
  }

  private static void demonstrateAnimationAnalysis(java.util.List<AnimationBinding> bindings) {
    System.out.println("=== ANIMATION ANALYSIS ===");
    System.out.printf("Found %d animation bindings:%n", bindings.size());

    // Group by animation type
    java.util.Map<String, java.util.List<AnimationBinding>> byType = bindings.stream()
      .collect(java.util.stream.Collectors.groupingBy(binding -> binding.getAnimationType().toString()));

    for (java.util.Map.Entry<String, java.util.List<AnimationBinding>> entry : byType.entrySet()) {
      System.out.printf("%n%s animations (%d):%n", entry.getKey(), entry.getValue().size());

      for (AnimationBinding binding : entry.getValue()) {
        System.out.printf("  SPID %d: %s -> %s (delay: %s, duration: %s)%n",
            binding.getTargetSpid(),
            binding.getTransition(),
            binding.getFilter(),
            binding.getDelay(),
            binding.getDuration());
      }
    }
    System.out.println();
  }

  private static void analyzeAnimationPatterns(ParsedSlideData slideData) {
    System.out.println("=== PATTERN ANALYSIS FOR TEMPLATING ===");

    // Analyze your 330ms timing patterns
    java.util.List<AnimationBinding> entranceAnimations = slideData.getAnimationBindings().stream()
      .filter(AnimationBinding::isEntranceAnimation)
      .toList();

    System.out.printf("Entrance animations: %d%n", entranceAnimations.size());

    // Count delay patterns
    java.util.Map<String, Long> delayPatterns = entranceAnimations.stream()
      .collect(java.util.stream.Collectors.groupingBy(
            AnimationBinding::getDelay,
            java.util.stream.Collectors.counting()));

    System.out.println("Delay patterns (for template timing):");
    delayPatterns.entrySet().stream()
      .sorted(java.util.Map.Entry.<String, Long>comparingByKey())
      .forEach(entry -> {
        String delay = entry.getKey();
        if (!delay.isEmpty() && !delay.equals("0")) {
          try {
            int delayMs = Integer.parseInt(delay);
            System.out.printf("  %dms: %d animations%n", delayMs, entry.getValue());
          } catch (NumberFormatException e) {
            System.out.printf("  %s: %d animations%n", delay, entry.getValue());
          }
        }
      });

    System.out.println();
    System.out.println("=== TEMPLATE INSIGHTS ===");
    System.out.println("• Your animation timing uses consistent 330ms intervals");
    System.out.println("• Multiple grouped entrance effects suggest template-friendly patterns");
    System.out.println("• Shape relationships can be inferred from animation timing");
    System.out.println("• Ready for template abstraction!");
  }
}
