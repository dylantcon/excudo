package com.excudo.xml.writers;

import org.junit.jupiter.api.*;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RelationshipPathResolver -- pure virtual-path calculations, zero disk access.
 */
class RelationshipPathResolverTest {

  private RelationshipPathResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new RelationshipPathResolver();
  }

  // ========== getSlideRelationshipFile ==========

  @Test
  @DisplayName("getSlideRelationshipFile returns correct virtual path for slide 1")
  void testGetSlideRelationshipFile_slide1() {
    File result = resolver.getSlideRelationshipFile(1);
    assertEquals("slide1.xml.rels", result.getName());
    assertTrue(result.getPath().replace('\\', '/').contains("ppt/slides/_rels"));
  }

  @Test
  @DisplayName("getSlideRelationshipFile returns correct virtual path for slide 15")
  void testGetSlideRelationshipFile_slide15() {
    File result = resolver.getSlideRelationshipFile(15);
    assertEquals("slide15.xml.rels", result.getName());
  }

  @Test
  @DisplayName("getSlideRelationshipFile virtual path is a PPTXDocument part key")
  void testGetSlideRelationshipFile_partKey() {
    File result = resolver.getSlideRelationshipFile(3);
    String path = result.getPath().replace('\\', '/');
    assertEquals("ppt/slides/_rels/slide3.xml.rels", path);
  }

  // ========== getRelativePathFromExtractedDir ==========

  @Test
  @DisplayName("getRelativePathFromExtractedDir returns forward-slash path")
  void testGetRelativePathFromExtractedDir_forwardSlashes() {
    File file = new File("ppt/slides/slide1.xml");
    String relative = resolver.getRelativePathFromExtractedDir(file);
    assertFalse(relative.startsWith("/"), "Should be a relative virtual key, not absolute");
    assertTrue(relative.contains("slide1.xml"));
  }

  @Test
  @DisplayName("getRelativePathFromExtractedDir handles nested paths")
  void testGetRelativePathFromExtractedDir_nested() {
    File file = new File("ppt/slides/_rels/slide2.xml.rels");
    String relative = resolver.getRelativePathFromExtractedDir(file);
    assertTrue(relative.contains("slide2.xml.rels"));
  }

  // ========== resolveMediaPartName ==========

  @Test
  @DisplayName("resolveMediaPartName handles ../media/ prefix")
  void testResolveMediaPartName_dotDotMediaPrefix() {
    String result = resolver.resolveMediaPartName("../media/image1.png");
    assertEquals("ppt/media/image1.png", result);
  }

  @Test
  @DisplayName("resolveMediaPartName handles media/ prefix")
  void testResolveMediaPartName_mediaPrefix() {
    String result = resolver.resolveMediaPartName("media/image2.jpg");
    assertEquals("ppt/media/image2.jpg", result);
  }

  @Test
  @DisplayName("resolveMediaPartName falls back to ppt/media for bare filename")
  void testResolveMediaPartName_bareFilename() {
    String result = resolver.resolveMediaPartName("image3.png");
    assertEquals("ppt/media/image3.png", result);
  }

  // ========== resolveRelationshipTarget ==========

  @Test
  @DisplayName("resolveRelationshipTarget handles ../ prefix")
  void testResolveRelationshipTarget_dotDotPrefix() {
    File result = resolver.resolveRelationshipTarget("../slideLayouts/slideLayout1.xml");
    assertTrue(result.getPath().replace('\\', '/').contains("ppt/slideLayouts/slideLayout1.xml"));
  }

  @Test
  @DisplayName("resolveRelationshipTarget handles direct path with /")
  void testResolveRelationshipTarget_directPath() {
    File result = resolver.resolveRelationshipTarget("slides/slide1.xml");
    assertTrue(result.getPath().replace('\\', '/').contains("ppt/slides/slide1.xml"));
  }

  @Test
  @DisplayName("resolveRelationshipTarget bare filename maps to ppt/ prefix")
  void testResolveRelationshipTarget_bareFilename() {
    File result = resolver.resolveRelationshipTarget("theme1.xml");
    assertTrue(result.getPath().replace('\\', '/').startsWith("ppt/"));
  }

  // ========== resolveRelationshipPartName ==========

  @Test
  @DisplayName("resolveRelationshipPartName converts ../x to ppt/x")
  void testResolveRelationshipPartName_dotDot() {
    assertEquals("ppt/slideLayouts/slideLayout1.xml",
        resolver.resolveRelationshipPartName("../slideLayouts/slideLayout1.xml"));
  }

  @Test
  @DisplayName("resolveRelationshipPartName prepends ppt/ for paths with /")
  void testResolveRelationshipPartName_withSlash() {
    assertEquals("ppt/slides/slide1.xml",
        resolver.resolveRelationshipPartName("slides/slide1.xml"));
  }
}
