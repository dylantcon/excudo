package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.OrchestrationContext;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.results.SlideExecutionResult;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * GoF Command implementation for deleting slides.
 * 
 * This command encapsulates all the business logic for deleting
 * a slide from the presentation with optional safety checks.
 */
public class DeleteSlideCommand implements Command {
    
    private final String actionId;
    private final int slideNumber;
    private final boolean safetyCheck;
    private final String reason;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    
    // Undo support - store slide data before deletion
    private Map<String, String> deletedSlideData;
    private Map<String, String> deletedRelationshipData;
    private String deletedRId;
    private String deletedSlideTitle;
    
    /**
     * Create a DeleteSlideCommand.
     * 
     * @param slideNumber the slide number to delete
     * @param safetyCheck whether to perform safety checks
     * @param reason reason for deletion
     * @param orchestrator the PPTX orchestrator for execution
     */
    public DeleteSlideCommand(int slideNumber, boolean safetyCheck, String reason, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        
        this.actionId = "slide-deletion-" + UUID.randomUUID().toString();
        this.slideNumber = slideNumber;
        this.safetyCheck = safetyCheck;
        this.reason = reason != null ? reason : "No reason provided";
        this.orchestrator = orchestrator;
    }
    
    
    /**
     * Execute the slide deletion command.
     * 
     * @throws CommandExecutionException if the operation fails
     */
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Capture slide data before deletion for undo support
            captureSlideDataForUndo();
            
            SlideExecutionResult result = orchestrator.deleteSlide(slideNumber);
            
            if (!result.isSuccess()) {
                throw new CommandExecutionException(
                    getDescription(), 
                    "execute", 
                    result.getMessage()
                );
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "execute", 
                "Failed to delete slide: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Undo the slide deletion command by restoring the captured slide data.
     * 
     * @throws CommandExecutionException if the undo operation fails
     */
    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo", "Command has not been executed");
        }
        
        if (deletedSlideData == null) {
            throw new CommandExecutionException(getDescription(), "undo", "No slide data captured for undo");
        }
        
        try {
            // Use the orchestrator's restoreSlide method to leverage existing business logic
            SlideExecutionResult result = orchestrator.restoreSlide(
                slideNumber, 
                deletedSlideData.get("slideXML"),
                deletedSlideData.get("relationshipXML"), 
                deletedSlideData.get("notesXML")
            );
            
            if (!result.isSuccess()) {
                throw new CommandExecutionException(
                    getDescription(), 
                    "undo", 
                    "Failed to restore slide: " + result.getMessage()
                );
            }
            
            // Reset execution state
            executed = false;
            
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "undo", 
                "Failed to undo slide deletion: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Check if this command can be undone.
     * 
     * @return true (slide deletion now supports undo with data capture)
     */
    @Override
    public boolean canUndo() {
        // Now supports undo by capturing slide data before deletion
        return executed && deletedSlideData != null;
    }
    
    /**
     * Get the description of this command.
     * 
     * @return human-readable description of the deletion operation
     */
    @Override
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append("Delete slide ").append(slideNumber);
        
        if (reason != null && !reason.equals("No reason provided")) {
            desc.append(" (").append(reason).append(")");
        }
        
        if (!safetyCheck) {
            desc.append(" [no safety check]");
        }
        
        return desc.toString();
    }
    
    /**
     * Check if this command has been executed.
     * 
     * @return true if execute() has been called successfully
     */
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    /**
     * Get the operation ID.
     * 
     * @return the unique operation identifier
     */
    public String getOperationId() {
        return actionId;
    }
    
    /**
     * Get the slide number being deleted.
     * 
     * @return the slide number
     */
    public int getSlideNumber() {
        return slideNumber;
    }
    
    /**
     * Check if safety checks are enabled.
     * 
     * @return true if safety checks are enabled
     */
    public boolean hasSafetyCheck() {
        return safetyCheck;
    }
    
    /**
     * Get the reason for deletion.
     * 
     * @return the deletion reason
     */
    public String getReason() {
        return reason;
    }
    
    /**
     * Capture slide data before deletion for undo support.
     * This method reads the slide XML, relationship XML, and notes XML before deletion.
     */
    private void captureSlideDataForUndo() throws Exception {
        deletedSlideData = new HashMap<>();
        
        // Get the context to access the PPTX directory
        java.util.Optional<OrchestrationContext> contextOpt = orchestrator.getContext();
        if (!contextOpt.isPresent()) {
            throw new Exception("Orchestrator context not available for data capture");
        }
        
        OrchestrationContext ctx = contextOpt.get();
        com.excudo.core.model.PPTXDocument pptxDoc = ctx.getDocument();

        // 1. Capture slide XML from PPTXDocument
        if (pptxDoc != null && pptxDoc.hasSlide(slideNumber)) {
            org.w3c.dom.Document slideDoc = pptxDoc.getSlideDocument(slideNumber);
            if (slideDoc != null) {
                deletedSlideData.put("slideXML", serializeDocument(slideDoc));
            }
        }

        // 2. Capture relationship XML from PPTXDocument
        String relsPartName = "ppt/slides/_rels/slide" + slideNumber + ".xml.rels";
        if (pptxDoc != null && pptxDoc.hasPart(relsPartName)) {
            org.w3c.dom.Document relsDoc = pptxDoc.getXmlPart(relsPartName);
            if (relsDoc != null) {
                deletedSlideData.put("relationshipXML", serializeDocument(relsDoc));
            }
        }

        // 3. Capture notes XML from PPTXDocument if it exists
        String notesPartName = "ppt/notesSlides/notesSlide" + slideNumber + ".xml";
        if (pptxDoc != null && pptxDoc.hasPart(notesPartName)) {
            org.w3c.dom.Document notesDoc = pptxDoc.getXmlPart(notesPartName);
            if (notesDoc != null) {
                deletedSlideData.put("notesXML", serializeDocument(notesDoc));
            }
        }
        
    }
    
    /**
     * Read the complete content of a file.
     */
    private String readFileContent(File file) throws Exception {
        StringBuilder content = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    /**
     * Serialize a DOM Document to a String for undo capture.
     */
    private String serializeDocument(org.w3c.dom.Document doc) {
        try {
            java.io.StringWriter writer = new java.io.StringWriter();
            javax.xml.transform.Transformer t =
                javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.transform(new javax.xml.transform.dom.DOMSource(doc),
                        new javax.xml.transform.stream.StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            return "";
        }
    }
}