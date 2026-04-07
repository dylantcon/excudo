package com.excudo.utils;

import com.excudo.core.model.PPTXDocument;
import org.w3c.dom.*;
import java.util.*;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Registry for tracking slide-to-notes mappings in PPTX presentations.
 * 
 * PowerPoint's insane notes slide numbering system requires careful tracking:
 * - Notes slides are numbered sequentially (1, 2, 3...) not by slide number
 * - Each notes slide can belong to any slide number
 * - Adding notes to an earlier slide requires renumbering ALL existing notes
 * 
 * This registry helps manage these mappings efficiently and provides rollback capabilities.
 */
public class NotesSlideRegistry {

    private static final ComponentLogger logger = Logger.xml();

    /**
     * Mapping from sequential notes slide number to target slide number
     * Key: Sequential notes number (1, 2, 3...)  
     * Value: Target slide number (any valid slide number)
     */
    private final Map<Integer, Integer> notesToSlideMap = new TreeMap<>();
    
    /**
     * Reverse mapping for quick lookups
     * Key: Target slide number
     * Value: Sequential notes number
     */
    private final Map<Integer, Integer> slideToNotesMap = new HashMap<>();

    public NotesSlideRegistry() {
    }

    /**
     * Build the registry from PPTXDocument's in-memory parts.
     * Scans notes slide relationship parts to determine slide-to-notes mappings.
     */
    public void buildRegistry(PPTXDocument pptxDocument) {
        notesToSlideMap.clear();
        slideToNotesMap.clear();

        Set<String> notesParts = pptxDocument.getPartNamesByPrefix("ppt/notesSlides/");
        for (String partName : notesParts) {
            if (!partName.endsWith(".xml") || partName.contains("_rels")) continue;

            try {
                String filename = partName.substring(partName.lastIndexOf('/') + 1);
                if (!filename.startsWith("notesSlide")) continue;

                String numStr = filename.substring(10, filename.length() - 4);
                int seqNum = Integer.parseInt(numStr);

                // Find the target slide from rels
                String relsPartName = "ppt/notesSlides/_rels/notesSlide" + numStr + ".xml.rels";
                Document relsDoc = pptxDocument.getXmlPart(relsPartName);
                if (relsDoc == null) continue;

                NodeList rels = relsDoc.getElementsByTagName("Relationship");
                for (int i = 0; i < rels.getLength(); i++) {
                    Element rel = (Element) rels.item(i);
                    String type = rel.getAttribute("Type");
                    String target = rel.getAttribute("Target");
                    if (type.contains("/slide") && !type.contains("slideLayout") && !type.contains("slideMaster")) {
                        String slideFilename = target.substring(target.lastIndexOf('/') + 1);
                        String slideNumStr = slideFilename.replace("slide", "").replace(".xml", "");
                        int slideNum = Integer.parseInt(slideNumStr);
                        addMapping(seqNum, slideNum);
                        break;
                    }
                }
            } catch (NumberFormatException e) {
                logger.warn("Ignoring malformed notes part: {}", partName);
            }
        }

        logger.debug("NotesSlideRegistry: Built from PPTXDocument with {} mappings", notesToSlideMap.size());
    }
    
    
    /**
     * Populate registry from pre-parsed notes-to-slide mapping.
     * Used by the fully in-memory initialization path.
     */
    public void loadFromParsedState(Map<Integer, Integer> notesToSlideMapping) {
        notesToSlideMap.clear();
        slideToNotesMap.clear();
        notesToSlideMapping.forEach(this::addMapping);
        logger.debug("NotesSlideRegistry: Loaded from parsed state with {} mappings", notesToSlideMap.size());
    }

    /**
     * Add a mapping to the registry
     */
    public void addMapping(int sequentialNumber, int targetSlideNumber) {
        notesToSlideMap.put(sequentialNumber, targetSlideNumber);
        slideToNotesMap.put(targetSlideNumber, sequentialNumber);
    }
    
    /**
     * Remove a mapping from the registry
     */
    public void removeMapping(int sequentialNumber) {
        Integer targetSlideNumber = notesToSlideMap.remove(sequentialNumber);
        if (targetSlideNumber != null) {
            slideToNotesMap.remove(targetSlideNumber);
        }
    }
    
    /**
     * Get the sequential notes number for a given slide number
     * @param slideNumber The slide number to look up
     * @return The sequential notes number, or -1 if no notes exist for this slide
     */
    public int getNotesNumberForSlide(int slideNumber) {
        return slideToNotesMap.getOrDefault(slideNumber, -1);
    }
    
    /**
     * Get the target slide number for a sequential notes number
     * @param sequentialNumber The sequential notes number
     * @return The target slide number, or -1 if not found
     */
    public int getSlideNumberForNotes(int sequentialNumber) {
        return notesToSlideMap.getOrDefault(sequentialNumber, -1);
    }
    
    /**
     * Get all sequential notes numbers that need to be shifted when inserting
     * a new notes slide at a specific position
     * 
     * @param insertAtPosition The position where we want to insert (1-based)
     * @return List of sequential numbers that need to be renamed, sorted in descending order
     */
    public List<Integer> getNotesToShift(int insertAtPosition) {
        List<Integer> toShift = new ArrayList<>();
        
        for (Integer sequentialNumber : notesToSlideMap.keySet()) {
            if (sequentialNumber >= insertAtPosition) {
                toShift.add(sequentialNumber);
            }
        }
        
        // Sort in descending order to avoid conflicts during renaming
        toShift.sort(Collections.reverseOrder());
        return toShift;
    }
    
    /**
     * Get the next available sequential notes number
     */
    public int getNextSequentialNumber() {
        if (notesToSlideMap.isEmpty()) {
            return 1;
        }
        return Collections.max(notesToSlideMap.keySet()) + 1;
    }
    
    /**
     * Check if a slide already has notes
     */
    public boolean hasNotes(int slideNumber) {
        return slideToNotesMap.containsKey(slideNumber);
    }
    
    /**
     * Get all slides that have notes, sorted by slide number
     */
    public List<Integer> getSlidesWithNotes() {
        List<Integer> slides = new ArrayList<>(slideToNotesMap.keySet());
        Collections.sort(slides);
        return slides;
    }
    
    /**
     * Get the insertion position for a new notes slide for a given slide number.
     * This maintains the invariant that slides with lower numbers should have
     * lower sequential notes numbers when possible.
     * 
     * @param slideNumber The slide number that will have the new notes
     * @return The sequential position where the new notes should be inserted
     */
    public int getInsertionPosition(int slideNumber) {
        // Find the appropriate insertion position to maintain ordering
        int position = 1;
        
        for (Map.Entry<Integer, Integer> entry : notesToSlideMap.entrySet()) {
            int existingSlideNumber = entry.getValue();
            if (existingSlideNumber < slideNumber) {
                position = entry.getKey() + 1;
            } else {
                break; // Found the insertion point
            }
        }
        
        return position;
    }
    
    /**
     * Create a snapshot of current mappings for rollback purposes
     */
    public NotesSlideSnapshot createSnapshot() {
        return new NotesSlideSnapshot(notesToSlideMap, slideToNotesMap);
    }
    
    /**
     * Restore registry from a snapshot
     */
    public void restoreFromSnapshot(NotesSlideSnapshot snapshot) {
        notesToSlideMap.clear();
        slideToNotesMap.clear();
        notesToSlideMap.putAll(snapshot.getNotesToSlideMap());
        slideToNotesMap.putAll(snapshot.getSlideToNotesMap());
    }
    
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("NotesSlideRegistry[\n");
        for (Map.Entry<Integer, Integer> entry : notesToSlideMap.entrySet()) {
            sb.append("  notesSlide").append(entry.getKey()).append(".xml -> slide")
              .append(entry.getValue()).append(".xml\n");
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Snapshot class for rollback functionality
     */
    public static class NotesSlideSnapshot {
        private final Map<Integer, Integer> notesToSlideMap;
        private final Map<Integer, Integer> slideToNotesMap;
        
        public NotesSlideSnapshot(Map<Integer, Integer> notesToSlideMap, 
                                Map<Integer, Integer> slideToNotesMap) {
            this.notesToSlideMap = new TreeMap<>(notesToSlideMap);
            this.slideToNotesMap = new HashMap<>(slideToNotesMap);
        }
        
        public Map<Integer, Integer> getNotesToSlideMap() {
            return notesToSlideMap;
        }
        
        public Map<Integer, Integer> getSlideToNotesMap() {
            return slideToNotesMap;
        }
    }
}