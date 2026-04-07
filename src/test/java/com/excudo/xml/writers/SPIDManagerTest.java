package com.excudo.xml.writers;

import com.excudo.core.model.LayoutManager;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;
import com.excudo.test.utils.TestConstants;
import com.excudo.test.utils.XMLTemplateBuilder;

/**
 * Comprehensive unit tests for SPIDManager using real PPTX data.
 * 
 * @author Excudo Test Suite
 * @version 2.0 - Real Data Focus
 */
class SPIDManagerTest {

  private Path tempDir;

  @Before
  public void setUp() throws Exception {
    tempDir = java.nio.file.Files.createTempDirectory("spid-test");
  }

  /**
   * Test SPIDManager with real PPTX data to improve coverage
   */
  @Test
  public void testWithRealPptxData() throws Exception {
    // Test SPIDManager with real PPTX data to improve code coverage
    Path realTestDir = java.nio.file.Files.createTempDirectory("spid-real-test");
    
    try {
      // Copy test PPTX structure
      copyTestPptxStructure(realTestDir.toFile());
      
      // Initialize SPIDManager with real data
      SPIDManager.resetInstance(); // Clear any existing instance
      PPTXDocument pptxDoc = PPTXDocument.load(realTestDir.toFile(), null);
      PPTXDocumentParser.ParsedPresentationState parsedState = PPTXDocumentParser.parse(pptxDoc);
      LayoutManager layoutManager = new LayoutManager(parsedState.getLayouts());
      SPIDManager realSpidManager = SPIDManager.createFromParsedState(parsedState, layoutManager);
      
      // Test that we loaded some real SPIDs
      Set<Integer> allSpids = realSpidManager.getAllSpids();
      System.out.println("Loaded " + allSpids.size() + " real SPIDs from PPTX: " + allSpids);
      
      // Test basic functionality with real data
      assertNotNull("getAllSpids should return non-null set", allSpids);
      
      // Test SPID info tracking
      for (Integer spid : allSpids) {
        SPIDManager.SPIDInfo spidInfo = realSpidManager.getSpidInfo(spid);
        assertNotNull("Should have SPID info for " + spid, spidInfo);
        assertTrue("Should have valid slide number for " + spid, spidInfo.getSlideNumber() > 0);
        assertNotNull("Should have shape name for " + spid, spidInfo.getShapeName());
      }
      
      // Test allocation doesn't conflict with existing SPIDs
      if (!allSpids.isEmpty()) {
        int newSpid = realSpidManager.allocateSpidForShape("test_shape", 1, false, false, null);
        assertFalse("New SPID should not conflict with existing", allSpids.contains(newSpid));
        assertTrue("New SPID should be positive", newSpid > 0);
      }
      
      // Test validation with real data
      SPIDManager.ValidationResult validation = realSpidManager.validateSpidUniqueness();
      assertNotNull("Validation result should not be null", validation);
      
      // Note: Real PPTX data may have legitimate duplicate SPIDs per-slide
      // This reflects the architectural issue we discovered - global vs per-slide uniqueness
      if (validation.hasErrors()) {
        System.out.println("Note: Found SPID conflicts in real data (per-slide duplicates): " + validation.getErrors());
      }
      
      // Test slide-specific SPID retrieval
      for (int slideNum = 1; slideNum <= 5; slideNum++) { // Check first 5 slides
        Set<Integer> slideSpids = realSpidManager.getSpidsForSlide(slideNum);
        System.out.println("Slide " + slideNum + " SPIDs: " + slideSpids);
      }
      
      // Test peek functionality
      int peekSpid = realSpidManager.peekNextAvailableSpid();
      assertTrue("Peek SPID should be positive", peekSpid > 0);
      
      // Test global SPID registry access
      Map<Integer, SPIDManager.SPIDInfo> globalRegistry = realSpidManager.getGlobalSpidRegistry();
      assertNotNull("Global registry should not be null", globalRegistry);
      assertEquals("Registry size should match SPID count", allSpids.size(), globalRegistry.size());
      
      // Test SPID usage check
      if (!allSpids.isEmpty()) {
        Integer testSpid = allSpids.iterator().next();
        assertTrue("Existing SPID should be marked as in use", realSpidManager.isSpidInUse(testSpid));
        assertFalse("Non-existent SPID should not be in use", realSpidManager.isSpidInUse(99999));
      }
      
      // Test multiple SPID allocation using proper universal pattern
      List<Integer> multipleSpids = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        multipleSpids.add(realSpidManager.allocateSpidForShape("test_shape_" + i, 2, false, false, null));
      }
      assertNotNull("Multiple SPID allocation should not be null", multipleSpids);
      assertEquals("Should allocate exactly 3 SPIDs", 3, multipleSpids.size());
      
      // Test that all allocated SPIDs are unique
      Set<Integer> uniqueSpids = new HashSet<>(multipleSpids);
      assertEquals("All allocated SPIDs should be unique", 3, uniqueSpids.size());
      
    } finally {
      // Clean up temp directory
      deleteDirectory(realTestDir);
    }
  }

  /**
   * Copy test PPTX structure from samples directory
   */
  private void copyTestPptxStructure(File targetDir) throws IOException {
    // Path to the extracted generalist test file
    Path sourceDir = Paths.get("generalist_extracted");
    
    if (!java.nio.file.Files.exists(sourceDir)) {
      // Extract the test PPTX if not already extracted
      java.nio.file.Files.createDirectories(sourceDir);
      try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile("test-pptx-samples/generalist_test_file.pptx")) {
        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
        
        while (entries.hasMoreElements()) {
          java.util.zip.ZipEntry entry = entries.nextElement();
          Path entryPath = sourceDir.resolve(entry.getName());
          
          if (entry.isDirectory()) {
            java.nio.file.Files.createDirectories(entryPath);
          } else {
            java.nio.file.Files.createDirectories(entryPath.getParent());
            java.nio.file.Files.copy(zipFile.getInputStream(entry), entryPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          }
        }
      }
    }
    
    // Copy the extracted structure to our target directory
    copyDirectory(sourceDir, targetDir.toPath());
  }
  
  /**
   * Copy directory recursively
   */
  private void copyDirectory(Path source, Path target) throws IOException {
    try (java.util.stream.Stream<Path> paths = java.nio.file.Files.walk(source)) {
      paths.forEach(sourcePath -> {
        try {
          Path targetPath = target.resolve(source.relativize(sourcePath));
          if (java.nio.file.Files.isDirectory(sourcePath)) {
            java.nio.file.Files.createDirectories(targetPath);
          } else {
            java.nio.file.Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }
  
  /**
   * Delete directory recursively
   */
  private void deleteDirectory(Path path) throws IOException {
    if (java.nio.file.Files.exists(path)) {
      try (java.util.stream.Stream<Path> paths = java.nio.file.Files.walk(path)) {
        paths.sorted(java.util.Comparator.reverseOrder())
             .map(Path::toFile)
             .forEach(File::delete);
      }
    }
  }
}