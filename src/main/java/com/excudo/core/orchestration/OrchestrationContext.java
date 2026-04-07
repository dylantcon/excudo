package com.excudo.core.orchestration;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import com.excudo.core.model.LayoutManager;
import com.excudo.core.services.ContextService;
import com.excudo.xml.writers.SlideCreator;
import com.excudo.xml.writers.RelationshipManager;
import com.excudo.xml.writers.SPIDManager;
import com.excudo.utils.NotesSlideRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Context information for orchestration operations.
 * All state is held in PPTXDocument -- no extracted directory.
 */
public class OrchestrationContext {

    private final PPTXDocument document;
    private final SlideCreator slideCreator;
    private final RelationshipManager relationshipManager;
    private final SPIDManager spidManager;
    private final NotesSlideRegistry notesSlideRegistry;
    private final ContextService contextService;
    private final LayoutManager layoutManager;
    private final Instant initializationTime;
    private final Map<String, Object> contextData;
    private PPTXDocumentParser.ParsedPresentationState parsedState;

    public OrchestrationContext(PPTXDocument document,
                               PPTXDocumentParser.ParsedPresentationState parsedState,
                               SlideCreator slideCreator,
                               RelationshipManager relationshipManager,
                               SPIDManager spidManager,
                               NotesSlideRegistry notesSlideRegistry,
                               LayoutManager layoutManager,
                               Map<String, Object> contextData) {
        if (document == null) {
            throw new IllegalArgumentException("PPTXDocument cannot be null");
        }
        this.document = document;
        this.parsedState = parsedState;
        this.slideCreator = slideCreator;
        this.relationshipManager = relationshipManager;
        this.spidManager = spidManager;
        this.notesSlideRegistry = notesSlideRegistry;
        this.layoutManager = layoutManager;
        this.initializationTime = Instant.now();
        this.contextData = new HashMap<>(contextData);

        ContextService cs = null;
        try {
            cs = new ContextService(document, spidManager, relationshipManager, layoutManager);
        } catch (Exception e) {
            System.err.println("[OrchestrationContext] Failed to create ContextService: " + e.getMessage());
        }
        this.contextService = cs;
    }

    public PPTXDocument getDocument() { return document; }
    public LayoutManager getLayoutManager() { return layoutManager; }
    public SlideCreator getSlideCreator() { return slideCreator; }
    public RelationshipManager getRelationshipManager() { return relationshipManager; }
    public SPIDManager getSpidManager() { return spidManager; }
    public NotesSlideRegistry getNotesSlideRegistry() { return notesSlideRegistry; }
    public ContextService getContextService() { return contextService; }
    public Instant getInitializationTime() { return initializationTime; }
    public Map<String, Object> getContextData() { return contextData; }
    public PPTXDocumentParser.ParsedPresentationState getParsedState() { return parsedState; }
    public void setParsedState(PPTXDocumentParser.ParsedPresentationState state) { this.parsedState = state; }

    /**
     * Get an XML part by OPC part name (e.g., "ppt/slides/slide1.xml").
     */
    public org.w3c.dom.Document getXmlPart(String partName) {
        return document.getXmlPart(partName);
    }

    /**
     * Get a slide DOM by number.
     */
    public org.w3c.dom.Document getSlideDocument(int slideNumber) {
        if (document.hasSlide(slideNumber)) {
            return document.getSlideDocument(slideNumber);
        }
        return null;
    }

    /**
     * Store a modified XML part.
     */
    public void putXmlPart(String partName, org.w3c.dom.Document doc) {
        document.putXmlPart(partName, doc);
    }

    @SuppressWarnings("unchecked")
    public <T> T getContextValue(String key, Class<T> type) {
        Object value = contextData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public boolean isValid() {
        return document != null;
    }

    public java.time.Duration getUptime() {
        return java.time.Duration.between(initializationTime, Instant.now());
    }

    @Override
    public String toString() {
        return String.format("OrchestrationContext[in-memory, uptime=%dms]", getUptime().toMillis());
    }
}
