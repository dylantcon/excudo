package com.excudo.xml.writers;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import org.w3c.dom.*;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Queue-based pipeline for deterministic PowerPoint rId allocation.
 * 
 * This manager implements the elegant mathematical approach to rId management:
 * - Operations are queued in batches representing sequential neighboring slides
 * - Each batch is pre-calculated for rIds before execution
 * - Array insertion with cascade increment ensures perfect PowerPoint structure
 * - Zero rId conflicts through mathematical certainty
 * 
 * PowerPoint rId Structure:
 * rId1-rId3: customXml items (optional, 0-3 entries)
 * rId4: slideMasters/slideMaster1.xml
 * rId5-rId(4+N): slides/slide1.xml to slides/slideN.xml (sequential)
 * rId(5+N): notesMasters/notesMaster1.xml
 * rId(6+N): presProps.xml
 * rId(7+N): viewProps.xml
 * rId(8+N): theme/theme1.xml
 * rId(9+N): tableStyles.xml
 * rId(10+N): revisionInfo.xml
 * rId(11+N): authors.xml (always last)
 */
public class ReservedRIdManager {

    private static final ComponentLogger logger = Logger.xml();

    /**
     * The core reserved rId array - mathematical foundation of the system
     * Index 0 = rId1, Index 1 = rId2, etc.
     * Slides occupy indices [4] through [4+slideCount-1]
     * Suffix relationships start at [4+slideCount]
     */
    private String[] reservedRIdArray;
    
    /**
     * Pipeline queue containing batches of sequential slide operations
     * Each SlideAction[] represents neighboring slides to be created together
     */
    private final Queue<SlideAction[]> operationQueue;
    
    /**
     * Current slide count in the presentation
     */
    private int currentSlideCount;
    
    /**
     * Number of customXml items present (0-3)
     */
    private final int customXmlCount;
    
    /**
     * Number of slideMasters present (1-N)
     */
    private final int slideMasterCount;
    
    /**
     * Fixed suffix relationships that come after all slides
     */
    private static final String[] SUFFIX_RELATIONSHIPS = {
        "notesMasters/notesMaster1.xml",
        "presProps.xml", 
        "viewProps.xml",
        "theme/theme1.xml",
        "tableStyles.xml",
        "revisionInfo.xml",
        "authors.xml"  // Always last
    };
    
    /**
     * Detects the actual number of customXml items from PPTXDocument (in-memory).
     */
    private int detectCustomXmlCount(PPTXDocument pptxDocument) {
        int count = 0;
        for (int i = 1; i <= 3; i++) {
            if (pptxDocument.hasPart("customXml/item" + i + ".xml")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Detects the actual number of slideMasters from PPTXDocument (in-memory).
     */
    private int detectSlideMasterCount(PPTXDocument pptxDocument) {
        int count = 0;
        for (int i = 1; i <= 10; i++) {
            if (pptxDocument.hasPart("ppt/slideMasters/slideMaster" + i + ".xml")) {
                count++;
            } else {
                break;
            }
        }
        return Math.max(1, count);
    }

    /**
     * Fallback method when scanning fails - builds the theoretical structure
     */
    private String[] buildFallbackReservedRIdArray() {
        List<String> relationships = new ArrayList<>();
        
        // Add customXml relationships (0-3 entries)
        for (int i = 1; i <= customXmlCount; i++) {
            relationships.add("../customXml/item" + i + ".xml");
        }
        
        // Add slideMaster relationships (1-N entries)
        for (int i = 1; i <= slideMasterCount; i++) {
            relationships.add("slideMasters/slideMaster" + i + ".xml");
        }
        
        // Add slide relationships (slides start after customXml + slideMasters)
        for (int i = 1; i <= currentSlideCount; i++) {
            relationships.add("slides/slide" + i + ".xml");
        }
        
        // Add suffix relationships (notesMaster, presProps, etc.)
        relationships.addAll(Arrays.asList(SUFFIX_RELATIONSHIPS));
        
        return relationships.toArray(new String[0]);
    }
    
    /**
     * Calculates the array index where slides start based on dynamic prefix count.
     * This replaces the hardcoded "4" with actual customXml + slideMaster count.
     */
    private int getSlideStartIndex() {
        return customXmlCount + slideMasterCount;
    }
    
    /**
     * Construct from PPTXDocument's in-memory parts (no disk access).
     */
    public ReservedRIdManager(int initialSlideCount, PPTXDocument pptxDocument) {
        this.currentSlideCount = initialSlideCount;
        this.customXmlCount = detectCustomXmlCount(pptxDocument);
        this.slideMasterCount = detectSlideMasterCount(pptxDocument);
        this.operationQueue = new LinkedBlockingQueue<>();

        Document relsDoc = pptxDocument.getXmlPart("ppt/_rels/presentation.xml.rels");
        if (relsDoc != null) {
            this.reservedRIdArray = buildArrayFromDocument(relsDoc);
        } else {
            this.reservedRIdArray = buildFallbackReservedRIdArray();
        }
    }
    
    /**
     * Reinitialize from an already-parsed presentation.xml.rels Document.
     * Used when the rels data comes from PPTXDocument (in-memory).
     */
    public void reinitialize(int newSlideCount, Document relsDoc) {
        this.currentSlideCount = newSlideCount;
        if (relsDoc != null) {
            this.reservedRIdArray = buildArrayFromDocument(relsDoc);
        } else {
            this.reservedRIdArray = buildFallbackReservedRIdArray();
        }
    }

    private String[] buildArrayFromDocument(Document doc) {
        try {
            NodeList relationships = doc.getElementsByTagName("Relationship");
            Map<Integer, String> rIdToTarget = new java.util.TreeMap<>();
            int maxRId = 0;

            for (int i = 0; i < relationships.getLength(); i++) {
                Element rel = (Element) relationships.item(i);
                String id = rel.getAttribute("Id");
                String target = rel.getAttribute("Target");

                if (id.startsWith("rId")) {
                    int rIdNum = Integer.parseInt(id.substring(3));
                    rIdToTarget.put(rIdNum, target);
                    maxRId = Math.max(maxRId, rIdNum);
                }
            }

            String[] array = new String[maxRId];
            for (Map.Entry<Integer, String> entry : rIdToTarget.entrySet()) {
                array[entry.getKey() - 1] = entry.getValue();
            }
            return array;
        } catch (Exception e) {
            logger.error("Failed to build rId array from Document: " + e.getMessage());
            return buildFallbackReservedRIdArray();
        }
    }

    /**
     * Add a batch of sequential slide operations to the pipeline queue
     *
     * @param operations Array of slide operations representing neighboring slides
     */
    public void queueBatchOperation(SlideAction[] operations) {
        if (operations == null || operations.length == 0) {
            throw new IllegalArgumentException("Operations batch cannot be null or empty");
        }
        
        // Validate that operations are sequential (positions are consecutive)
        for (int i = 1; i < operations.length; i++) {
            if (operations[i].getPosition() != operations[i-1].getPosition() + 1) {
                throw new IllegalArgumentException("Batch operations must be sequential/neighboring");
            }
        }
        
        operationQueue.offer(operations);
    }
    
    /**
     * Add a single slide operation (convenience method)
     */
    public void queueSingleOperation(SlideAction operation) {
        queueBatchOperation(new SlideAction[]{operation});
    }
    
    /**
     * Execute the entire pipeline - processes all queued batches sequentially
     * This is where the mathematical magic happens!
     */
    public void executeOperationPipeline() {
        while (!operationQueue.isEmpty()) {
            SlideAction[] currentBatch = operationQueue.poll();
            
            logger.debug("Processing batch: " + Arrays.toString(currentBatch));
            
            // Validate batch consistency (all operations must be same type)
            validateBatchConsistency(currentBatch);
            
            // Phase 1: Pre-calculate rIds for this batch
            preCalculateRIds(currentBatch);
            
            // Phase 2: Apply batch operations to reserved array with cascade effect
            if (currentBatch[0].isCreateOperation()) {
                insertBatchIntoArray(currentBatch);
                currentSlideCount += currentBatch.length;
            } else if (currentBatch[0].isDeleteOperation()) {
                deleteBatchFromArray(currentBatch);
                currentSlideCount -= currentBatch.length;
            }
            
            logger.debug("Batch processed. New slide count: " + currentSlideCount);
            logger.debug("Updated rId array: " + Arrays.toString(reservedRIdArray));
        }
    }
    
    /**
     * Pre-calculate rIds for all operations in the batch
     * This ensures zero conflicts during execution
     */
    private void preCalculateRIds(SlideAction[] batch) {
        int basePosition = batch[0].getPosition();
        
        if (batch[0].isCreateOperation()) {
            // For CREATE operations: calculate rIds for new positions
            for (int i = 0; i < batch.length; i++) {
                // Calculate array index: dynamic prefix + position - 1 + offset within batch
                int arrayIndex = getSlideStartIndex() + basePosition - 1 + i;
                String rId = "rId" + (arrayIndex + 1); // rIds are 1-based
                
                batch[i].setPreCalculatedRId(rId);
                logger.debug("Pre-calculated CREATE: " + batch[i]);
            }
        } else if (batch[0].isDeleteOperation()) {
            // For DELETE operations: identify rIds that will be removed
            for (int i = 0; i < batch.length; i++) {
                SlideAction operation = batch[i];
                int deletePosition = operation.getPosition();
                int arrayIndex = getSlideStartIndex() + deletePosition - 1;
                String rId = "rId" + (arrayIndex + 1); // rIds are 1-based
                
                operation.setPreCalculatedRId(rId);
                logger.debug("Pre-calculated DELETE: " + operation);
            }
        }
    }
    
    /**
     * Mathematical array insertion with cascade increment
     * This is the core of your elegant algorithm!
     */
    private void insertBatchIntoArray(SlideAction[] batch) {
        int insertIndex = getSlideStartIndex() + batch[0].getPosition() - 1; // Array index where batch starts
        int batchSize = batch.length;
        
        // Create new array with expanded size
        String[] newArray = new String[reservedRIdArray.length + batchSize];
        
        // Copy prefix (before insertion point)
        System.arraycopy(reservedRIdArray, 0, newArray, 0, insertIndex);
        
        // Insert new slide relationships
        for (int i = 0; i < batchSize; i++) {
            newArray[insertIndex + i] = "slides/slide" + (batch[0].getPosition() + i) + ".xml";
        }
        
        // Copy suffix (after insertion point) with cascade shift
        System.arraycopy(reservedRIdArray, insertIndex, newArray, 
                        insertIndex + batchSize, reservedRIdArray.length - insertIndex);
        
        // Update the array
        reservedRIdArray = newArray;
        
        // Now we need to update slide numbers in the suffix slides that got shifted
        updateShiftedSlideNumbers(insertIndex + batchSize, batchSize);
    }
    
    /**
     * Update slide numbers for slides that were shifted by the insertion
     */
    private void updateShiftedSlideNumbers(int startIndex, int shiftAmount) {
        for (int i = startIndex; i < reservedRIdArray.length; i++) {
            String relationship = reservedRIdArray[i];
            
            // Only update slide relationships, not suffix relationships
            if (relationship != null && relationship.startsWith("slides/slide") && relationship.endsWith(".xml")) {
                // Extract slide number and increment it
                String slideNumStr = relationship.substring("slides/slide".length(), relationship.length() - ".xml".length());
                try {
                    int slideNum = Integer.parseInt(slideNumStr);
                    reservedRIdArray[i] = "slides/slide" + (slideNum + shiftAmount) + ".xml";
                } catch (NumberFormatException e) {
                    // Skip non-numeric slide names
                }
            }
        }
    }
    
    /**
     * Validate that all operations in a batch have the same type (CREATE or DELETE).
     * Mixed batches are not allowed for mathematical consistency.
     */
    private void validateBatchConsistency(SlideAction[] batch) {
        if (batch == null || batch.length == 0) {
            throw new IllegalArgumentException("Batch cannot be null or empty");
        }
        
        SlideAction.ActionType expectedType = batch[0].getActionType();
        for (int i = 1; i < batch.length; i++) {
            if (batch[i].getActionType() != expectedType) {
                throw new IllegalArgumentException("Mixed action types in batch not allowed. " +
                    "Found " + expectedType + " and " + batch[i].getActionType() + " in same batch.");
            }
        }
    }
    
    /**
     * Mathematical array deletion with reverse cascade effect.
     * Removes slides from the reserved array and shifts subsequent elements down.
     */
    private void deleteBatchFromArray(SlideAction[] batch) {
        // Sort by position (descending) to delete from highest position first
        // This prevents position shift conflicts during batch deletion
        Arrays.sort(batch, (a, b) -> Integer.compare(b.getPosition(), a.getPosition()));
        
        for (SlideAction operation : batch) {
            deleteSingleSlideFromArray(operation);
        }
    }
    
    /**
     * Delete a single slide from the reserved array with mathematical precision.
     */
    private void deleteSingleSlideFromArray(SlideAction operation) {
        int deletePosition = operation.getPosition();
        int deleteIndex = getSlideStartIndex() + deletePosition - 1; // Array index to delete
        
        if (deleteIndex < 0 || deleteIndex >= reservedRIdArray.length) {
            throw new IllegalArgumentException("Invalid delete position: " + deletePosition + 
                " (array index: " + deleteIndex + ", array length: " + reservedRIdArray.length + ")");
        }
        
        logger.debug("Reverse cascading - removing " + reservedRIdArray[deleteIndex] +
                          " at array index " + deleteIndex);
        
        // Create new array with reduced size
        String[] newArray = new String[reservedRIdArray.length - 1];
        
        // Copy elements before deletion point
        System.arraycopy(reservedRIdArray, 0, newArray, 0, deleteIndex);
        
        // Copy elements after deletion point (shifted down by 1)
        System.arraycopy(reservedRIdArray, deleteIndex + 1, newArray, deleteIndex, 
                        reservedRIdArray.length - deleteIndex - 1);
        
        // Update the array reference
        reservedRIdArray = newArray;
        
        // Update slide numbers in targets that were shifted (reverse of updateShiftedSlideNumbers)
        updateShiftedSlideNumbersAfterDeletion(deleteIndex, 1);
        
        // Store the pre-calculated rId for this deletion (the rId that was removed)
        String deletedRId = "rId" + (deleteIndex + 1);
        operation.setPreCalculatedRId(deletedRId);
        
        logger.debug("Deleted slide at position " + deletePosition +
                          ", removed rId: " + deletedRId);
    }
    
    /**
     * Update slide numbers in relationship targets after deletion.
     * This decrements slide numbers for all slides that were shifted down.
     */
    private void updateShiftedSlideNumbersAfterDeletion(int deletionIndex, int shiftAmount) {
        for (int i = deletionIndex; i < reservedRIdArray.length; i++) {
            String relationship = reservedRIdArray[i];
            if (relationship != null && relationship.startsWith("slides/slide") && relationship.endsWith(".xml")) {
                // Extract slide number and decrement it
                String slideNumStr = relationship.substring("slides/slide".length(), relationship.length() - ".xml".length());
                try {
                    int slideNum = Integer.parseInt(slideNumStr);
                    reservedRIdArray[i] = "slides/slide" + (slideNum - shiftAmount) + ".xml";
                    logger.debug("Reverse cascade - updated " + relationship +
                                     " to " + reservedRIdArray[i]);
                } catch (NumberFormatException e) {
                    // Skip non-numeric slide names
                }
            }
        }
    }
    
    /**
     * Get the current reserved rId array (for debugging/inspection)
     */
    public String[] getReservedRIdArray() {
        return reservedRIdArray.clone();
    }
    
    /**
     * Get the rId for a specific slide position.
     * Slides should have consecutive rIds after slideMaster.
     */
    public String getRIdForSlidePosition(int position) {
        if (position < 1) {
            throw new IllegalArgumentException("Invalid slide position: " + position);
        }
        
        // Slides get consecutive rIds starting after slideMaster and customXml
        int arrayIndex = getSlideStartIndex() + position - 1;
        return "rId" + (arrayIndex + 1);
    }
    
    /**
     * Get current slide count
     */
    public int getCurrentSlideCount() {
        return currentSlideCount;
    }
    
    /**
     * Check if pipeline has pending operations
     */
    public boolean hasPendingOperations() {
        return !operationQueue.isEmpty();
    }
    
    /**
     * Get the number of pending batch operations
     */
    public int getPendingBatchCount() {
        return operationQueue.size();
    }
}