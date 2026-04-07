package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.OOXMLAttributeOrder;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Facade providing centralized relationship management for OOXML PowerPoint presentations.
 *
 * Invariant core: global registry, ID counters, scan, allocation.
 * Delegates to: RelationshipValidator, RelationshipPathResolver,
 *               MediaRelationshipManager, SlideRelationshipWriter.
 */
public class RelationshipManager {

  private static final ComponentLogger logger = Logger.xml();

  // ========== INVARIANT CORE STATE ==========

  private final DocumentBuilder documentBuilder;
  private final XPath xpath;
  private final Map<String, RelationshipInfo> globalRelationshipRegistry;
  private final AtomicInteger nextRelationshipIdCounter;
  private final ReservedRIdManager reservedRIdManager;
  private final Map<String, AtomicInteger> perFileRelationshipCounters;
  private final Map<String, Document> relationshipDocumentCache;

  // ========== IN-MEMORY DOCUMENT (optional) ==========

  private PPTXDocument pptxDocument;

  // ========== EXTRACTED COLLABORATORS ==========

  private final RelationshipPathResolver pathResolver;
  private final RelationshipValidator validator;
  private final MediaRelationshipManager mediaRelMgr;
  private final SlideRelationshipWriter slideRelWriter;

  // ========== CONSTRUCTOR ==========

  /**
   * Construct from PPTXDocument and pre-parsed state (no disk access).
   * Populates registries from ParsedPresentationState instead of scanning .rels files.
   */
  public RelationshipManager(PPTXDocument pptxDocument,
                             PPTXDocumentParser.ParsedPresentationState parsedState) throws XMLParsingException {
    this.pptxDocument = pptxDocument;
    this.globalRelationshipRegistry = new ConcurrentHashMap<>();
    this.relationshipDocumentCache = new ConcurrentHashMap<>();
    this.perFileRelationshipCounters = new ConcurrentHashMap<>();
    this.nextRelationshipIdCounter = new AtomicInteger(parsedState.getGlobalMaxRId() + 1);

    try {
      this.documentBuilder = XMLFactoryProvider.createDocumentBuilder();
      this.xpath = XMLFactoryProvider.createXPath();
    } catch (ParserConfigurationException e) {
      throw new XMLParsingException("Failed to initialize RelationshipManager", e);
    }

    // Populate registry from parsed state (no disk scan)
    for (Map.Entry<String, PPTXDocumentParser.RelationshipEntry> entry :
         parsedState.getGlobalRelationships().entrySet()) {
      globalRelationshipRegistry.put(entry.getKey(),
          new RelationshipInfo(entry.getValue().getType(), entry.getValue().getTarget()));
    }
    for (Map.Entry<String, Integer> entry : parsedState.getPerFileMaxRId().entrySet()) {
      perFileRelationshipCounters.put(entry.getKey(), new AtomicInteger(entry.getValue() + 1));
    }

    int slideCount = parsedState.getSlideCount();
    this.reservedRIdManager = new ReservedRIdManager(slideCount, pptxDocument);
    this.pathResolver = new RelationshipPathResolver();
    this.validator = new RelationshipValidator(globalRelationshipRegistry,
        documentBuilder, xpath, pathResolver);
    this.validator.setPPTXDocument(pptxDocument);
    this.mediaRelMgr = new MediaRelationshipManager(this, pathResolver, documentBuilder);
    this.slideRelWriter = new SlideRelationshipWriter(this, pathResolver, documentBuilder, xpath);
  }

  /**
   * Set the PPTXDocument for in-memory relationship operations.
   * When set, rels files are read/written through the document's xmlParts
   * instead of the filesystem.
   */
  public void setPPTXDocument(PPTXDocument pptxDocument) {
    this.pptxDocument = pptxDocument;
    // Re-scan relationships from PPTXDocument if registry is empty
    // (constructor may have scanned empty stub dirs)
    if (pptxDocument != null && globalRelationshipRegistry.isEmpty()) {
      try {
        scanExistingRelationships();
        updateRelationshipIdCounter();
        // Reinitialize reserved rId manager from the in-memory rels Document
        int currentSlideCount = getCurrentSlideCountFromRegistry();
        org.w3c.dom.Document relsDoc = pptxDocument.getXmlPart("ppt/_rels/presentation.xml.rels");
        reservedRIdManager.reinitialize(currentSlideCount, relsDoc);
      } catch (XMLParsingException e) {
        logger.warn("Failed to re-scan relationships from PPTXDocument: " + e.getMessage());
      }
    }
  }

  public PPTXDocument getPPTXDocument() {
    return pptxDocument;
  }

  // ========== PIPELINE METHODS ==========

  public void queueBatchSlideOperations(SlideAction[] operations) {
    reservedRIdManager.queueBatchOperation(operations);
  }

  public void queueSingleSlideAction(SlideAction operation) {
    reservedRIdManager.queueSingleOperation(operation);
  }

  public void executeSlideOperationPipeline() {
    reservedRIdManager.executeOperationPipeline();
  }

  public String getRIdForSlidePosition(int position) {
    return reservedRIdManager.getRIdForSlidePosition(position);
  }

  public boolean hasPendingSlideOperations() {
    return reservedRIdManager.hasPendingOperations();
  }

  public void queueSlideDeleteOperation(int slidePosition) {
    String operationId = "delete-" + slidePosition + "-" + System.currentTimeMillis();
    SlideAction deleteOp = SlideAction.createDeleteOperation(operationId, slidePosition);
    reservedRIdManager.queueSingleOperation(deleteOp);
  }

  public String removePresentationSlideRelationship(int slidePosition) throws XMLParsingException {
    queueSlideDeleteOperation(slidePosition);
    executeSlideOperationPipeline();

    String removedRId = getRIdForSlidePosition(slidePosition);
    removePresentationRelationshipFromFile(removedRId);

    return removedRId;
  }

  // ========== DELEGATE TO SlideRelationshipWriter ==========

  public RelationshipCreationResult createSlideRelationships(int slideNumber,
      String layoutTarget, String themeTarget) throws XMLParsingException {
    return slideRelWriter.createSlideRelationships(slideNumber, layoutTarget, themeTarget);
  }

  public RelationshipCopyResult copySlideRelationships(int sourceSlideNumber,
      int destinationSlideNumber, boolean forceNewIds) throws XMLParsingException {
    return slideRelWriter.copySlideRelationships(sourceSlideNumber, destinationSlideNumber, forceNewIds);
  }

  public void addPresentationRelationship(String relationshipId, String relationshipType, String target) throws XMLParsingException {
    slideRelWriter.addPresentationRelationship(relationshipId, relationshipType, target);
  }

  // ========== DELEGATE TO MediaRelationshipManager ==========

  public String addMediaRelationship(int slideNumber, String mediaType, String mediaTarget) throws XMLParsingException {
    return mediaRelMgr.addMediaRelationship(slideNumber, mediaType, mediaTarget);
  }

  public boolean removeMediaRelationship(int slideNumber, String relationshipId) throws XMLParsingException {
    return mediaRelMgr.removeMediaRelationship(slideNumber, relationshipId);
  }

  public Map<String, String> getSlideRelationships(int slideNumber) {
    return mediaRelMgr.getSlideRelationships(slideNumber);
  }

  public Map<String, String> getAllMediaRelationships() {
    return mediaRelMgr.getAllMediaRelationships();
  }

  // ========== DELEGATE TO RelationshipValidator ==========

  public ValidationResult validateAllRelationships() throws XMLParsingException {
    return validator.validateAllRelationships();
  }

  public ValidationResult validatePresentationRelationshipPattern() throws XMLParsingException {
    return validator.validatePresentationRelationshipPattern();
  }

  // ========== INVARIANT CORE: ID ALLOCATION ==========

  public String allocateRelationshipId() {
    String candidateId;
    do {
      int idNumber = nextRelationshipIdCounter.getAndIncrement();
      candidateId = XMLConstants.RID_PREFIX + idNumber;
    } while (globalRelationshipRegistry.containsKey(candidateId));

    logger.debug("RelationshipManager allocated " + candidateId);
    return candidateId;
  }

  @Deprecated
  public String allocateSlideRelationshipId() {
    int nextPosition = reservedRIdManager.getCurrentSlideCount() + 1;
    SlideAction tempOp = new SlideAction("temp-" + System.currentTimeMillis(),
                                             nextPosition, "BLANK");
    reservedRIdManager.queueSingleOperation(tempOp);
    reservedRIdManager.executeOperationPipeline();

    String allocatedRId = tempOp.getPreCalculatedRId();
    logger.debug("RelationshipManager allocated slide " + allocatedRId + " (via pipeline)");
    return allocatedRId;
  }

  public String allocateSlideRelationshipIdForPosition(int slidePosition) {
    return reservedRIdManager.getRIdForSlidePosition(slidePosition);
  }

  public String allocateLocalRelationshipId(String relsFilePath) {
    AtomicInteger counter = perFileRelationshipCounters.get(relsFilePath);
    if (counter == null) {
      // Part wasn't seen during init scan -- try PPTXDocument or filesystem
      if (pptxDocument != null) {
        Document doc = pptxDocument.getXmlPart(relsFilePath);
        if (doc != null) {
          scanRelationshipDocument(doc, relsFilePath);
          counter = perFileRelationshipCounters.get(relsFilePath);
        }
      }
      counter = perFileRelationshipCounters.computeIfAbsent(
          relsFilePath, k -> new AtomicInteger(1));
    }

    int idNumber = counter.getAndIncrement();
    String candidateId = XMLConstants.RID_PREFIX + idNumber;

    logger.debug("Allocated local " + candidateId + " for " + relsFilePath);
    return candidateId;
  }

  // ========== INVARIANT CORE: REGISTRY ==========

  public Set<String> getAllRelationshipIds() {
    return Collections.unmodifiableSet(globalRelationshipRegistry.keySet());
  }

  public RelationshipInfo getRelationshipInfo(String relationshipId) {
    return globalRelationshipRegistry.get(relationshipId);
  }

  public boolean removeRelationship(int slideNumber, String relationshipId) throws XMLParsingException {
    if (slideNumber < 1) {
      throw new IllegalArgumentException("slideNumber must be positive");
    }
    if (relationshipId == null || relationshipId.trim().isEmpty()) {
      throw new IllegalArgumentException("relationshipId cannot be null or empty");
    }

    try {
      File relsFile = pathResolver.getSlideRelationshipFile(slideNumber);
      String partName = "ppt/slides/_rels/slide" + slideNumber + ".xml.rels";

      Document relsDoc = null;
      if (pptxDocument != null) {
        relsDoc = pptxDocument.getXmlPart(partName);
      }
      if (relsDoc == null) {
        return false;
      }

      Element relationships = relsDoc.getDocumentElement();

      NodeList relationshipElements = relationships.getElementsByTagName("Relationship");
      for (int i = 0; i < relationshipElements.getLength(); i++) {
        Element relationshipEl = (Element) relationshipElements.item(i);
        if (relationshipId.equals(relationshipEl.getAttribute("Id"))) {
          relationships.removeChild(relationshipEl);

          unregisterRelationship(relationshipId);

          writeRelationshipDocumentPublic(relsDoc, relsFile);

          String cacheKey = pathResolver.getRelativePathFromExtractedDir(relsFile);
          relationshipDocumentCache.put(cacheKey, relsDoc);

          return true;
        }
      }

      return false;

    } catch (Exception e) {
      throw new XMLParsingException("Failed to remove relationship " + relationshipId +
          " from slide " + slideNumber, e);
    }
  }

  public void updateRelationshipsForSlideMove(int oldSlideNumber, int newSlideNumber) throws XMLParsingException {
    if (oldSlideNumber < 1 || newSlideNumber < 1) {
      throw new IllegalArgumentException("Slide numbers must be positive");
    }
    // TODO: Implement comprehensive slide move relationship updates
  }

  public List<String> getAvailableThemes() {
    List<String> themes = new ArrayList<>();
    if (pptxDocument != null) {
      for (String part : pptxDocument.getPartNamesByPrefix("ppt/theme/")) {
        if (part.endsWith(".xml")) {
          themes.add(part.substring("ppt/".length()));
        }
      }
    }
    return themes;
  }

  public List<String> getAvailableLayouts() {
    List<String> layouts = new ArrayList<>();
    if (pptxDocument != null) {
      for (String part : pptxDocument.getPartNamesByPrefix("ppt/slideLayouts/")) {
        if (part.endsWith(".xml") && !part.contains("_rels")) {
          layouts.add(part.substring("ppt/".length()));
        }
      }
    }
    return layouts;
  }

  // ========== PACKAGE-PRIVATE BRIDGE METHODS (for extracted collaborators) ==========

  /** Provides read-only view of the global registry for collaborators. */
  Map<String, RelationshipInfo> getGlobalRegistryView() {
    return Collections.unmodifiableMap(globalRelationshipRegistry);
  }

  /** Registers a relationship in the global registry. */
  void registerRelationshipPublic(String id, String type, String target) {
    globalRelationshipRegistry.put(id, new RelationshipInfo(type, target));
  }

  /** Unregisters a relationship from the global registry. */
  private void unregisterRelationship(String id) {
    globalRelationshipRegistry.remove(id);
  }

  /** Reassigns a relationship from one ID to another in the global registry. */
  void reassignRelationshipId(String oldId, String newId) {
    RelationshipInfo info = globalRelationshipRegistry.remove(oldId);
    if (info != null) {
      globalRelationshipRegistry.put(newId, info);
    }
  }

  /** Creates a relationship XML element. */
  Element createRelationshipElementPublic(Document doc, String id, String type, String target) {
    Element relationship = doc.createElementNS(XMLConstants.PACKAGE_RELATIONSHIPS_NS, "Relationship");
    relationship.setAttribute("Id", id);
    relationship.setAttribute("Type", type);
    relationship.setAttribute("Target", target);
    return relationship;
  }

  /** Parses a relationship document, using PPTXDocument or cache if available. */
  Document parseRelationshipDocumentPublic(File relsFile) throws XMLParsingException {
    String cacheKey = pathResolver.getRelativePathFromExtractedDir(relsFile);

    if (pptxDocument != null) {
      Document doc = pptxDocument.getXmlPart(cacheKey);
      if (doc != null) return doc;
    }

    Document cached = relationshipDocumentCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    throw new XMLParsingException("Relationship document not found in PPTXDocument: " + cacheKey);
  }

  /** Writes a relationship document to PPTXDocument. */
  void writeRelationshipDocumentPublic(Document doc, File outputFile) throws XMLParsingException {
    String partName = pathResolver.getRelativePathFromExtractedDir(outputFile);

    if (pptxDocument != null) {
      pptxDocument.putXmlPart(partName, doc);
      return;
    }

    throw new UnsupportedOperationException("Disk-backed relationship writes require a PPTXDocument. Part: " + partName);
  }

  /** Caches a relationship document. */
  void cacheRelationshipDocument(String cacheKey, Document doc) {
    relationshipDocumentCache.put(cacheKey, doc);
  }

  /** Loads an existing slide relationship document or creates a new one. */
  Document loadOrCreateSlideRelationshipDocument(int slideNumber) throws XMLParsingException {
    if (pptxDocument != null) {
      String partName = "ppt/slides/_rels/slide" + slideNumber + ".xml.rels";
      Document doc = pptxDocument.getXmlPart(partName);
      if (doc != null) return doc;
    }

    Document doc = documentBuilder.newDocument();
    Element relationships = doc.createElementNS(XMLConstants.PACKAGE_RELATIONSHIPS_NS, "Relationships");
    doc.appendChild(relationships);
    return doc;
  }

  // ========== PRIVATE: SCAN & INIT ==========

  private int getCurrentSlideCountFromRegistry() {
    int slideCount = 0;
    for (Map.Entry<String, RelationshipInfo> entry : globalRelationshipRegistry.entrySet()) {
      String type = entry.getValue().getType();
      String target = entry.getValue().getTarget();

      if (type != null && type.contains("slide") &&
          !type.contains("slideMaster") && !type.contains("slideLayout") &&
          target != null && target.startsWith("slides/")) {
        slideCount++;
      }
    }
    return slideCount;
  }

  private void scanExistingRelationships() throws XMLParsingException {
    try {
      if (pptxDocument != null) {
        // Scan from in-memory parts
        for (String partName : pptxDocument.getPartNames()) {
          if (partName.endsWith(".rels")) {
            Document doc = pptxDocument.getXmlPart(partName);
            if (doc != null) {
              scanRelationshipDocument(doc, partName);
            }
          }
        }
      }

      updateRelationshipIdCounter();

    } catch (Exception e) {
      throw new XMLParsingException("Failed to scan existing relationships", e);
    }
  }

  /**
   * Scan a relationship Document (in-memory) and register all relationships.
   * Returns the max rId number found.
   */
  private int scanRelationshipDocument(Document relsDoc, String partName) {
    NodeList relationshipElements = relsDoc.getElementsByTagName("Relationship");
    int maxIdForFile = 0;

    for (int i = 0; i < relationshipElements.getLength(); i++) {
      Element relationshipEl = (Element) relationshipElements.item(i);
      String id = relationshipEl.getAttribute("Id");
      String type = relationshipEl.getAttribute("Type");
      String target = relationshipEl.getAttribute("Target");

      registerRelationshipPublic(id, type, target);

      if (id.startsWith(XMLConstants.RID_PREFIX)) {
        try {
          int idNumber = Integer.parseInt(id.substring(XMLConstants.RID_PREFIX.length()));
          maxIdForFile = Math.max(maxIdForFile, idNumber);
        } catch (NumberFormatException e) {
          // Skip non-numeric relationship IDs
        }
      }
    }

    if (maxIdForFile > 0) {
      perFileRelationshipCounters.put(partName, new AtomicInteger(maxIdForFile + 1));
    }
    return maxIdForFile;
  }

  private void updateRelationshipIdCounter() {
    int maxId = 0;
    for (String relationshipId : globalRelationshipRegistry.keySet()) {
      if (relationshipId.startsWith(XMLConstants.RID_PREFIX)) {
        try {
          int idNumber = Integer.parseInt(relationshipId.substring(XMLConstants.RID_PREFIX.length()));
          maxId = Math.max(maxId, idNumber);
        } catch (NumberFormatException e) {
          // Skip
        }
      }
    }
    nextRelationshipIdCounter.set(maxId + 1);
  }

  private void removePresentationRelationshipFromFile(String relationshipId) throws XMLParsingException {
    try {
      String partName = "ppt/_rels/presentation.xml.rels";
      Document relsDoc = null;

      if (pptxDocument != null) {
        relsDoc = pptxDocument.getXmlPart(partName);
      }

      if (relsDoc == null) {
        return;
      }

      Element relationships = relsDoc.getDocumentElement();

      NodeList relationshipElements = relationships.getElementsByTagName("Relationship");
      for (int i = 0; i < relationshipElements.getLength(); i++) {
        Element relationshipEl = (Element) relationshipElements.item(i);
        if (relationshipId.equals(relationshipEl.getAttribute("Id"))) {
          relationships.removeChild(relationshipEl);
          logger.debug("Removed relationship " + relationshipId +
                           " (target: " + relationshipEl.getAttribute("Target") + ") from presentation.xml.rels");
          break;
        }
      }

      File presentationRelsFile = new File(partName);
      writeRelationshipDocumentPublic(relsDoc, presentationRelsFile);

      String cacheKey = pathResolver.getRelativePathFromExtractedDir(presentationRelsFile);
      relationshipDocumentCache.put(cacheKey, relsDoc);

    } catch (Exception e) {
      throw new XMLParsingException("Failed to remove presentation relationship " + relationshipId, e);
    }
  }

  // ========== INNER CLASSES ==========

  public static class RelationshipInfo {
    private final String type;
    private final String target;

    public RelationshipInfo(String type, String target) {
      this.type = type;
      this.target = target;
    }

    public String getType() { return type; }
    public String getTarget() { return target; }

    @Override
    public String toString() {
      return String.format("RelationshipInfo{type='%s', target='%s'}", type, target);
    }
  }

  public static class RelationshipCreationResult {
    private final File relationshipFile;
    private final List<String> createdRelationshipIds;

    public RelationshipCreationResult(File relationshipFile, List<String> createdRelationshipIds) {
      this.relationshipFile = relationshipFile;
      this.createdRelationshipIds = Collections.unmodifiableList(new ArrayList<>(createdRelationshipIds));
    }

    public File getRelationshipFile() { return relationshipFile; }
    public List<String> getCreatedRelationshipIds() { return createdRelationshipIds; }
  }

  public static class RelationshipCopyResult {
    private final File relationshipFile;
    private final Map<String, String> oldToNewIdMappings;
    private final List<String> newRelationshipIds;

    public RelationshipCopyResult(File relationshipFile, Map<String, String> oldToNewIdMappings,
        List<String> newRelationshipIds) {
      this.relationshipFile = relationshipFile;
      this.oldToNewIdMappings = Collections.unmodifiableMap(new HashMap<>(oldToNewIdMappings));
      this.newRelationshipIds = Collections.unmodifiableList(new ArrayList<>(newRelationshipIds));
    }

    public File getRelationshipFile() { return relationshipFile; }
    public Map<String, String> getOldToNewIdMappings() { return oldToNewIdMappings; }
    public List<String> getNewRelationshipIds() { return newRelationshipIds; }
  }

  public static class ValidationResult {
    private final List<String> errors;
    private final List<String> warnings;

    public ValidationResult(List<String> errors, List<String> warnings) {
      this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
      this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public boolean hasErrors() { return !errors.isEmpty(); }
    public boolean hasWarnings() { return !warnings.isEmpty(); }
    public boolean isValid() { return errors.isEmpty(); }
  }
}
