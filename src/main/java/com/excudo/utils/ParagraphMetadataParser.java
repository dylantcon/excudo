package com.excudo.utils;

import com.excudo.core.model.ParagraphMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Utility for parsing text content and extracting paragraph metadata
 * Detects bullet points, numbered lists, and plain paragraphs
 */
public class ParagraphMetadataParser {
  
  // Pattern for detecting bullet markers at start of line
  private static final Pattern BULLET_PATTERN = Pattern.compile("^\\s*([•\\-\\*]|\\d+[.)\\s]+)\\s*(.+)$", Pattern.MULTILINE);
  
  // More specific patterns for different bullet types
  private static final Pattern SIMPLE_BULLET_PATTERN = Pattern.compile("^\\s*[•\\-\\*]\\s*(.+)$");
  private static final Pattern NUMBERED_PATTERN = Pattern.compile("^\\s*(\\d+)[.)\\s]+(.+)$");
  
  /**
   * Parse text content and extract paragraph metadata
   */
  public static ParagraphMetadata parseTextContent(String textContent) {
    if (textContent == null || textContent.trim().isEmpty()) {
      return new ParagraphMetadata(
          List.of(),
          List.of(),
          List.of()
      );
    }
    
    String[] lines = textContent.split("\\r?\\n");
    List<String> paragraphContents = new ArrayList<>();
    List<Boolean> isBulletPoint = new ArrayList<>();
    List<String> bulletMarkers = new ArrayList<>();
    
    for (String line : lines) {
      String trimmedLine = line.trim();
      
      // Skip empty lines
      if (trimmedLine.isEmpty()) {
        continue;
      }
      
      // Check for simple bullet points (•, -, *)
      Matcher simpleBulletMatcher = SIMPLE_BULLET_PATTERN.matcher(trimmedLine);
      if (simpleBulletMatcher.matches()) {
        paragraphContents.add(simpleBulletMatcher.group(1).trim());
        isBulletPoint.add(true);
        bulletMarkers.add(extractBulletMarker(trimmedLine));
        continue;
      }
      
      // Check for numbered lists (1., 2), etc.)
      Matcher numberedMatcher = NUMBERED_PATTERN.matcher(trimmedLine);
      if (numberedMatcher.matches()) {
        paragraphContents.add(numberedMatcher.group(2).trim());
        isBulletPoint.add(true);
        bulletMarkers.add(numberedMatcher.group(1));
        continue;
      }
      
      // Plain paragraph (no bullet)
      paragraphContents.add(trimmedLine);
      isBulletPoint.add(false);
      bulletMarkers.add("");
    }
    
    return new ParagraphMetadata(paragraphContents, isBulletPoint, bulletMarkers);
  }
  
  /**
   * Extract the bullet marker character from a bullet line
   */
  private static String extractBulletMarker(String line) {
    String trimmed = line.trim();
    if (trimmed.startsWith("•")) return "•";
    if (trimmed.startsWith("-")) return "-";
    if (trimmed.startsWith("*")) return "*";
    return "•"; // Default fallback
  }
  
  /**
   * Parse HTML content and extract paragraph metadata
   * Handles HTML bullet/numbered lists
   */
  public static ParagraphMetadata parseHtmlContent(String htmlContent) {
    if (htmlContent == null || htmlContent.trim().isEmpty()) {
      return new ParagraphMetadata(List.of(), List.of(), List.of());
    }
    
    // For now, convert HTML to text and parse that way
    // This is a simplified approach - could be enhanced to parse HTML structure directly
    String textContent = htmlContent
        .replaceAll("<[^>]+>", " ") // Remove HTML tags
        .replaceAll("\\s+", " ")   // Normalize whitespace
        .trim();
    
    return parseTextContent(textContent);
  }
  
  /**
   * Create paragraph metadata from OOXML structure (when available)
   * Parses individual <a:p> elements and extracts indentation levels from <a:pPr lvl="..."/>
   */
  public static ParagraphMetadata parseFromOOXML(org.w3c.dom.Element txBodyElement) {
    return parseFromOOXML(txBodyElement, false);
  }
  
  /**
   * Create paragraph metadata from OOXML structure with placeholder context
   * @param txBodyElement The text body element to parse
   * @param isContentPlaceholder Whether this text is in a content placeholder (affects bullet defaults)
   */
  public static ParagraphMetadata parseFromOOXML(org.w3c.dom.Element txBodyElement, boolean isContentPlaceholder) {
    if (txBodyElement == null) {
      return new ParagraphMetadata(List.of(), List.of(), List.of());
    }
    
    try {
      // Get all paragraph elements (<a:p>)
      org.w3c.dom.NodeList paragraphNodes = txBodyElement.getElementsByTagName("a:p");
      
      List<String> paragraphContents = new ArrayList<>();
      List<Boolean> isBulletPoint = new ArrayList<>();
      List<String> bulletMarkers = new ArrayList<>();
      
      for (int i = 0; i < paragraphNodes.getLength(); i++) {
        org.w3c.dom.Element paragraphElement = (org.w3c.dom.Element) paragraphNodes.item(i);
        
        // Extract text content from all <a:t> elements within this paragraph
        StringBuilder paragraphText = new StringBuilder();
        org.w3c.dom.NodeList textNodes = paragraphElement.getElementsByTagName("a:t");
        for (int j = 0; j < textNodes.getLength(); j++) {
          paragraphText.append(textNodes.item(j).getTextContent());
        }
        
        String textContent = paragraphText.toString().trim();
        
        // Skip empty paragraphs (like those with only <a:endParaRPr/>)
        if (textContent.isEmpty()) {
          continue;
        }
        
        // Extract indentation level from <a:pPr lvl="..."/>
        int indentLevel = 0;
        org.w3c.dom.NodeList pPrNodes = paragraphElement.getElementsByTagName("a:pPr");
        if (pPrNodes.getLength() > 0) {
          org.w3c.dom.Element pPrElement = (org.w3c.dom.Element) pPrNodes.item(0);
          String lvlAttr = pPrElement.getAttribute("lvl");
          if (!lvlAttr.isEmpty()) {
            try {
              indentLevel = Integer.parseInt(lvlAttr);
            } catch (NumberFormatException e) {
              indentLevel = 0; // Default to no indentation if parsing fails
            }
          }
        }
        
        // Format content with proper indentation and bullet markers
        String indentation = "  ".repeat(indentLevel); // 2 spaces per level
        String formattedContent;
        
        // Determine if this is a bullet point
        if (indentLevel > 0) {
          // Indented items are always bullets
          formattedContent = indentation + textContent;
          paragraphContents.add(formattedContent);
          isBulletPoint.add(true);
          bulletMarkers.add("-" + indentation); // Dash followed by spaces for alignment
        } else if (isContentPlaceholder) {
          // In content placeholders, level-0 paragraphs are bullets by default
          formattedContent = textContent; // No indentation for level 0
          paragraphContents.add(formattedContent);
          isBulletPoint.add(true);
          bulletMarkers.add("-"); // Simple dash for level-0 bullets
        } else {
          // Non-placeholder context: level-0 is plain text
          paragraphContents.add(textContent);
          isBulletPoint.add(false);
          bulletMarkers.add("");
        }
      }
      
      return new ParagraphMetadata(paragraphContents, isBulletPoint, bulletMarkers);
      
    } catch (Exception e) {
      // If OOXML parsing fails, fall back to text content parsing
      String textContent = txBodyElement.getTextContent();
      return parseTextContent(textContent);
    }
  }
  
}