package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import java.io.*;
import java.util.*;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Manages media relationships (images, video, audio) for slides.
 * Handles adding, removing, and querying media relationships.
 */
public class MediaRelationshipManager {

  private static final ComponentLogger logger = Logger.xml();

  private final RelationshipManager manager;
  private final RelationshipPathResolver pathResolver;
  private final DocumentBuilder documentBuilder;

  public MediaRelationshipManager(RelationshipManager manager,
                                  RelationshipPathResolver pathResolver,
                                  DocumentBuilder documentBuilder) {
    this.manager = manager;
    this.pathResolver = pathResolver;
    this.documentBuilder = documentBuilder;
  }

  /**
   * Adds a media relationship to the specified slide.
   */
  public String addMediaRelationship(int slideNumber, String mediaType, String mediaTarget) throws XMLParsingException {
    if (slideNumber < 1) {
      throw new IllegalArgumentException("slideNumber must be positive");
    }
    if (mediaType == null || mediaType.trim().isEmpty()) {
      throw new IllegalArgumentException("mediaType cannot be null or empty");
    }
    if (mediaTarget == null || mediaTarget.trim().isEmpty()) {
      throw new IllegalArgumentException("mediaTarget cannot be null or empty");
    }

    try {
      File relsFile = pathResolver.getSlideRelationshipFile(slideNumber);
      Document relsDoc = manager.loadOrCreateSlideRelationshipDocument(slideNumber);

      Element relationships = relsDoc.getDocumentElement();

      String relsFilePath = pathResolver.getRelativePathFromExtractedDir(relsFile);
      String mediaRId = manager.allocateLocalRelationshipId(relsFilePath);

      Element mediaRel = manager.createRelationshipElementPublic(relsDoc, mediaRId, mediaType, mediaTarget);
      relationships.appendChild(mediaRel);

      manager.registerRelationshipPublic(mediaRId, mediaType, mediaTarget);

      manager.writeRelationshipDocumentPublic(relsDoc, relsFile);

      String cacheKey = pathResolver.getRelativePathFromExtractedDir(relsFile);
      manager.cacheRelationshipDocument(cacheKey, relsDoc);

      return mediaRId;

    } catch (Exception e) {
      throw new XMLParsingException("Failed to add media relationship to slide " + slideNumber, e);
    }
  }

  /**
   * Removes a media relationship from the specified slide.
   */
  public boolean removeMediaRelationship(int slideNumber, String relationshipId) throws XMLParsingException {
    if (slideNumber < 1) {
      throw new IllegalArgumentException("slideNumber must be positive");
    }
    if (relationshipId == null || relationshipId.trim().isEmpty()) {
      throw new IllegalArgumentException("relationshipId cannot be null or empty");
    }

    try {
      RelationshipManager.RelationshipInfo relationshipInfo = manager.getRelationshipInfo(relationshipId);
      if (relationshipInfo == null) {
        return false;
      }

      if (!isMediaRelationship(relationshipInfo.getType())) {
        throw new IllegalArgumentException("Relationship " + relationshipId +
            " is not a media relationship (type: " + relationshipInfo.getType() + ")");
      }

      String mediaTarget = relationshipInfo.getTarget();
      logger.debug("Removing media relationship " + relationshipId +
                        " (type: " + relationshipInfo.getType() + ", target: " + mediaTarget + ")");

      boolean relationshipRemoved = manager.removeRelationship(slideNumber, relationshipId);
      if (!relationshipRemoved) {
        return false;
      }

      logger.debug("Successfully removed media relationship " + relationshipId);
      return true;

    } catch (Exception e) {
      throw new XMLParsingException("Failed to remove media relationship " + relationshipId +
          " from slide " + slideNumber, e);
    }
  }

  /**
   * Gets all relationships for a specific slide.
   */
  public Map<String, String> getSlideRelationships(int slideNumber) {
    Map<String, String> relationships = new HashMap<>();

    try {
      Document relsDoc = manager.loadOrCreateSlideRelationshipDocument(slideNumber);
      if (relsDoc != null) {
        NodeList relationshipElements = relsDoc.getElementsByTagName("Relationship");

        for (int i = 0; i < relationshipElements.getLength(); i++) {
          Element rel = (Element) relationshipElements.item(i);
          String id = rel.getAttribute("Id");
          String target = rel.getAttribute("Target");
          relationships.put(id, target);
        }
      }
    } catch (Exception e) {
      // Return empty map on error
    }

    return relationships;
  }

  /**
   * Gets all media relationships across the entire presentation.
   */
  public Map<String, String> getAllMediaRelationships() {
    Map<String, String> mediaRelationships = new HashMap<>();

    for (Map.Entry<String, RelationshipManager.RelationshipInfo> entry : manager.getGlobalRegistryView().entrySet()) {
      RelationshipManager.RelationshipInfo info = entry.getValue();
      if (isMediaRelationship(info.getType())) {
        mediaRelationships.put(info.getTarget(), info.getType());
      }
    }

    return mediaRelationships;
  }

  /**
   * Determines if a relationship type is for media.
   */
  boolean isMediaRelationship(String relationshipType) {
    return relationshipType.equals(XMLConstants.RELATIONSHIP_TYPE_IMAGE) ||
      relationshipType.contains("video") ||
      relationshipType.contains("audio");
  }
}
