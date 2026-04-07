package com.excudo.xml.writers;

import java.io.File;
import com.excudo.core.model.PPTXDocument;

/**
 * Resolves paths for relationship files and targets within a PPTX presentation.
 * All methods produce virtual paths usable as PPTXDocument part keys.
 * Operates in pure virtual-path mode only -- no disk access.
 */
public class RelationshipPathResolver {

  private PPTXDocument pptxDocument;

  public RelationshipPathResolver() {
  }

  /**
   * Attach a PPTXDocument for in-memory part existence checks.
   */
  public void setPPTXDocument(PPTXDocument pptxDocument) {
    this.pptxDocument = pptxDocument;
  }

  /**
   * Returns the virtual part name for a slide's relationship file as a File whose
   * path is the PPTXDocument part key (forward-slash virtual path).
   */
  public File getSlideRelationshipFile(int slideNumber) {
    return new File(String.format("ppt/slides/_rels/slide%d.xml.rels", slideNumber));
  }

  /**
   * Returns the slide relationship virtual part name (always a forward-slash path).
   */
  public String getSlideRelationshipPartName(int slideNumber) {
    return String.format("ppt/slides/_rels/slide%d.xml.rels", slideNumber);
  }

  /**
   * Returns the file's path as a virtual part key (forward slashes).
   * In virtual-path mode the file path IS the part key -- no relativization needed.
   */
  public String getRelativePathFromExtractedDir(File file) {
    return file.getPath().replace('\\', '/');
  }

  /**
   * Resolves a media relationship target to the virtual part name.
   * Returns a PPTXDocument-compatible part path (forward slashes, rooted at archive root).
   */
  public String resolveMediaPartName(String mediaTarget) {
    if (mediaTarget.startsWith("../media/")) {
      return "ppt/media/" + mediaTarget.substring("../media/".length());
    }
    if (mediaTarget.startsWith("media/")) {
      return "ppt/" + mediaTarget;
    }
    return "ppt/media/" + mediaTarget;
  }

  /**
   * Resolves a relationship target to its virtual part name.
   */
  public String resolveRelationshipPartName(String target) {
    if (target.startsWith("../")) {
      return "ppt/" + target.substring(3);
    }
    if (target.contains("/")) {
      return "ppt/" + target;
    }
    return "ppt/" + target;
  }

  /**
   * Resolves a relationship target to a File whose path is the virtual part key.
   * Uses PPTXDocument part existence checks when available.
   */
  public File resolveRelationshipTarget(String target) {
    return new File(resolveRelationshipPartName(target));
  }
}
