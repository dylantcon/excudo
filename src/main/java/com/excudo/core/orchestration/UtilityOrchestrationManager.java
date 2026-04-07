package com.excudo.core.orchestration;

import com.excudo.core.model.AnimationType;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestration manager for utility operations that don't fit into other managers.
 * Handles read-only inspection, analysis, and debugging operations.
 */
public class UtilityOrchestrationManager {
    
    private final PPTXOrchestratorImpl orchestrator;
    
    public UtilityOrchestrationManager(PPTXOrchestratorImpl orchestrator) {
        this.orchestrator = orchestrator;
    }
    
    /**
     * Get all available animation types with their descriptions.
     */
    public List<AnimationType> getAvailableAnimationTypes() {
        return Arrays.asList(AnimationType.values());
    }
    
    /**
     * Dump timing XML structure from a slide for analysis.
     * Preserves original file-based logging functionality.
     */
    public ExecutionResult<String> dumpTimingXML(int slideNumber) {
        return dumpTimingXML(slideNumber, true);
    }
    
    /**
     * Dump timing XML in bulk with slide range support (matching original functionality).
     * This method replicates the original dumpTiming() behavior exactly.
     * 
     * @param slideRange slide specification: "1", "1-5", "all"
     * @param writeToFile whether to write to timestamped log files
     * @return result with summary of dumped timing structures
     */
    public ExecutionResult<String> dumpTimingsBulk(String slideRange, boolean writeToFile) {
        try {
            var ctxOpt = orchestrator.getContext();
            if (ctxOpt.isEmpty()) {
                return ExecutionResult.failure("DumpTimingsBulk", "No presentation loaded");
            }
            com.excudo.core.model.PPTXDocument pptxDoc = ctxOpt.get().getDocument();
            if (pptxDoc == null) {
                return ExecutionResult.failure("DumpTimingsBulk", "No PPTXDocument available");
            }
            
            // Parse slide range (reusing existing parseSlideRange method)
            List<Integer> slidesToDump = parseSlideRange(slideRange);
            if (slidesToDump.isEmpty()) {
                return ExecutionResult.failure("DumpTimingsBulk", "Invalid slide specification: " + slideRange);
            }
            
            StringBuilder result = new StringBuilder();
            File sessionDir = null;
            
            if (writeToFile) {
                // Create timestamped output directory for timing dumps
                sessionDir = createTimestampedOutputDirectory("timing-dumps");
                result.append("Dumping timing XML structures to: ").append(sessionDir.getAbsolutePath()).append("\n");
                result.append("=====================================\n");
            }
            
            int dumpedSlides = 0;
            int totalTimingSections = 0;
            
            // Process each slide
            for (int slideNum : slidesToDump) {
                if (!pptxDoc.hasSlide(slideNum)) {
                    result.append("Slide ").append(slideNum).append(": Not found\n");
                    continue;
                }
                
                try {
                    // Get timing XML for this slide
                    ExecutionResult<String> timingResult = dumpTimingXML(slideNum, false); // Don't write individual files
                    
                    if (timingResult.isSuccess() && timingResult.getData().isPresent()) {
                        String timingContent = timingResult.getData().get();
                        
                        // Count timing sections (very rough count)
                        int timingSectionCount = countTimingSections(timingContent);
                        
                        if (writeToFile && sessionDir != null) {
                            // Extract title for filename
                            String slideTitle = extractSlideTitleFromContent(timingContent);
                            writeTimingToFile(sessionDir, slideNum, slideTitle, timingContent);
                        }
                        
                        result.append("Slide ").append(slideNum).append(": ");
                        if (timingSectionCount > 0) {
                            result.append(timingSectionCount).append(" timing section(s) extracted");
                            totalTimingSections += timingSectionCount;
                        } else {
                            result.append("No timing sections found");
                        }
                        result.append("\n");
                        dumpedSlides++;
                        
                    } else {
                        result.append("Slide ").append(slideNum).append(": Error - ").append(timingResult.getMessage()).append("\n");
                    }
                    
                } catch (Exception e) {
                    result.append("Error processing slide ").append(slideNum).append(": ").append(e.getMessage()).append("\n");
                }
            }
            
            result.append("\nDump complete: ").append(dumpedSlides).append(" slides, ").append(totalTimingSections).append(" timing sections");
            if (writeToFile && sessionDir != null) {
                result.append("\nOutput directory: ").append(sessionDir.getAbsolutePath());
            }
            
            return ExecutionResult.success("DumpTimingsBulk", result.toString());
            
        } catch (Exception e) {
            return ExecutionResult.failure("DumpTimingsBulk", "Failed to dump timing structures: " + e.getMessage());
        }
    }
    
    /**
     * Dump timing XML structure from a slide for analysis.
     * 
     * @param slideNumber the slide number
     * @param writeToFile whether to write to timestamped log files (preserves original behavior)
     * @return the timing XML content
     */
    public ExecutionResult<String> dumpTimingXML(int slideNumber, boolean writeToFile) {
        try {
            // Read slide from PPTXDocument or disk
            Document doc = null;
            if (orchestrator.getContext().isPresent()) {
                doc = orchestrator.getContext().get().getSlideDocument(slideNumber);
            }
            if (doc == null) {
                return ExecutionResult.failure("DumpTimingXML", "Slide " + slideNumber + " not found");
            }

            // Setup XPath
            XPath xpath = XMLFactoryProvider.createXPath();

            // Find timing nodes
            NodeList timingNodes = (NodeList) xpath.evaluate(
                "//p:timing", doc, XPathConstants.NODESET);
            
            if (timingNodes.getLength() == 0) {
                return ExecutionResult.success("DumpTimingXML", "No timing section found in slide " + slideNumber);
            }
            
            // Build result content with metadata (preserving original format)
            StringBuilder result = new StringBuilder();
            
            // Extract title from slide (SPID 2 usually - matching original logic)
            String slideTitle = extractSlideTitle(doc, xpath);
            
            // Add header with slide info
            result.append("=== SLIDE ").append(slideNumber);
            if (slideTitle != null && !slideTitle.isEmpty()) {
                result.append(" - ").append(slideTitle);
            }
            result.append(" ===\n\n");
            
            if (timingNodes.getLength() == 0) {
                result.append("No timing section found\n");
            } else {
                for (int i = 0; i < timingNodes.getLength(); i++) {
                    Node timingNode = timingNodes.item(i);
                    String timingXml = nodeToString(timingNode);
                    if (timingNodes.getLength() > 1) {
                        result.append("Timing section ").append(i + 1).append(":\n");
                    }
                    result.append(timingXml).append("\n");
                }
            }
            
            // Write to file if requested (preserving original file logging behavior)
            if (writeToFile) {
                try {
                    writeTimingToLogFile(slideNumber, slideTitle, result.toString());
                } catch (Exception e) {
                    // Don't fail the entire operation if file logging fails
                    // Just continue with console output
                }
            }
            
            return ExecutionResult.success("DumpTimingXML", result.toString());
            
        } catch (Exception e) {
            return ExecutionResult.failure("DumpTimingXML", "Failed to dump timing XML: " + e.getMessage());
        }
    }
    
    /**
     * Dump shape XML structure for analysis (single shape).
     */
    public ExecutionResult<String> dumpShapeXML(int slideNumber, int spid) {
        return dumpShapeXML(slideNumber, spid, false);
    }
    
    /**
     * Dump shapes in bulk with slide range support (matching original functionality).
     * This method replicates the original dumpShape() behavior exactly.
     * 
     * @param slideRange slide specification: "1", "1-5", "all"
     * @param writeToFile whether to write to timestamped log files
     * @return result with summary of dumped shapes
     */
    public ExecutionResult<String> dumpShapesBulk(String slideRange, boolean writeToFile) {
        try {
            var ctxOpt = orchestrator.getContext();
            if (ctxOpt.isEmpty()) {
                return ExecutionResult.failure("DumpShapesBulk", "No presentation loaded");
            }
            com.excudo.core.model.PPTXDocument pptxDoc = ctxOpt.get().getDocument();
            if (pptxDoc == null) {
                return ExecutionResult.failure("DumpShapesBulk", "No PPTXDocument available");
            }
            
            // Parse slide range (future: extract to SlideRangeParser service)
            List<Integer> slidesToDump = parseSlideRange(slideRange);
            if (slidesToDump.isEmpty()) {
                return ExecutionResult.failure("DumpShapesBulk", "Invalid slide specification: " + slideRange);
            }
            
            StringBuilder result = new StringBuilder();
            File sessionDir = null;
            
            if (writeToFile) {
                // Create timestamped output directory (future: extract to DiagnosticFileWriter service)
                sessionDir = createTimestampedOutputDirectory("shape-dumps");
                result.append("Dumping shape XML structures to: ").append(sessionDir.getAbsolutePath()).append("\n");
                result.append("=====================================\n");
            }
            
            int dumpedSlides = 0;
            int totalShapes = 0;
            
            // Process each slide (future: extract to ShapeAnalysisService)
            for (int slideNum : slidesToDump) {
                if (!pptxDoc.hasSlide(slideNum)) {
                    result.append("Slide ").append(slideNum).append(": Not found\n");
                    continue;
                }
                
                try {
                    org.w3c.dom.Document slideDoc = pptxDoc.getSlideDocument(slideNum);
                    int slideShapeCount = processSlideShapes(slideDoc, slideNum, sessionDir, writeToFile);
                    result.append("Slide ").append(slideNum).append(": ").append(slideShapeCount).append(" shapes extracted\n");
                    totalShapes += slideShapeCount;
                    dumpedSlides++;
                    
                } catch (Exception e) {
                    result.append("Error processing slide ").append(slideNum).append(": ").append(e.getMessage()).append("\n");
                }
            }
            
            result.append("\nDump complete: ").append(dumpedSlides).append(" slides, ").append(totalShapes).append(" shapes");
            if (writeToFile && sessionDir != null) {
                result.append("\nOutput directory: ").append(sessionDir.getAbsolutePath());
            }
            
            return ExecutionResult.success("DumpShapesBulk", result.toString());
            
        } catch (Exception e) {
            return ExecutionResult.failure("DumpShapesBulk", "Failed to dump shapes: " + e.getMessage());
        }
    }
    
    /**
     * Dump shape XML structure for analysis.
     * 
     * @param slideNumber the slide number
     * @param spid the shape SPID (ignored when writeToFile=true, dumps all shapes)
     * @param writeToFile whether to write to timestamped log files with slide range support
     * @return the shape XML content
     */
    public ExecutionResult<String> dumpShapeXML(int slideNumber, int spid, boolean writeToFile) {
        try {
            Document doc = null;
            if (orchestrator.getContext().isPresent()) {
                doc = orchestrator.getContext().get().getSlideDocument(slideNumber);
            }
            if (doc == null) {
                return ExecutionResult.failure("DumpShapeXML", "Slide " + slideNumber + " not found");
            }

            // Setup XPath
            XPath xpath = XMLFactoryProvider.createXPath();

            // Find shape with specified SPID
            String shapeXPath = String.format("//p:sp[.//p:cNvPr[@id='%d']]", spid);
            Node shapeNode = (Node) xpath.evaluate(shapeXPath, doc, XPathConstants.NODE);
            
            if (shapeNode == null) {
                // Try picture shapes
                shapeXPath = String.format("//p:pic[.//p:cNvPr[@id='%d']]", spid);
                shapeNode = (Node) xpath.evaluate(shapeXPath, doc, XPathConstants.NODE);
            }
            
            if (shapeNode == null) {
                return ExecutionResult.failure("DumpShapeXML", "Shape with SPID " + spid + " not found on slide " + slideNumber);
            }
            
            // Convert to string
            String shapeXml = nodeToString(shapeNode);
            return ExecutionResult.success("DumpShapeXML", shapeXml);
            
        } catch (Exception e) {
            return ExecutionResult.failure("DumpShapeXML", "Failed to dump shape XML: " + e.getMessage());
        }
    }
    
    /**
     * Reset animation context for the current session.
     * This clears any animation grouping state maintained by the console.
     */
    public void resetAnimationContext() {
        // Animation context is maintained at the console/command layer
        // This method serves as a marker that the operation was requested
        // The actual reset happens in AbstractConsoleEngine.resetAnimationContext()
        // which is called by ResetAnimationContextCommand
    }
    
    /**
     * Extract slide title from slide XML (matching original logic).
     */
    private String extractSlideTitle(Document doc, XPath xpath) {
        try {
            // Look for text in shape with SPID 2 (usually the title placeholder)
            Node titleNode = (Node) xpath.evaluate(
                "//p:sp[.//p:cNvPr[@id='2']]//a:t", 
                doc, XPathConstants.NODE);
            
            if (titleNode != null) {
                String title = titleNode.getTextContent();
                if (title != null && !title.trim().isEmpty()) {
                    return title.trim();
                }
            }
            
            // Fallback: look for any title placeholder
            titleNode = (Node) xpath.evaluate(
                "//p:sp[.//p:ph[@type='title']]//a:t", 
                doc, XPathConstants.NODE);
            
            if (titleNode != null) {
                String title = titleNode.getTextContent();
                if (title != null && !title.trim().isEmpty()) {
                    return title.trim();
                }
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Write timing dump to log file (preserving original file logging behavior).
     */
    private void writeTimingToLogFile(int slideNumber, String slideTitle, String content) throws Exception {
        // Create timing-dumps subdirectory in logs (matching original path)
        File logsDir = new File(".excudo/logs/timing-dumps");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        
        // Create timestamp-based subdirectory for this dump session (matching original)
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File sessionDir = new File(logsDir, timestamp);
        sessionDir.mkdirs();
        
        // Create safe filename (matching original logic)
        String safeTitle = slideTitle != null ? 
            slideTitle.replaceAll("[^a-zA-Z0-9.-]", "_") : "no_title";
        if (safeTitle.length() > 50) {
            safeTitle = safeTitle.substring(0, 50);
        }
        String filename = String.format("slide_%03d_%s.xml", slideNumber, safeTitle);
        
        // Write to file
        File outputFile = new File(sessionDir, filename);
        Files.write(outputFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Count timing sections in timing XML content.
     */
    private int countTimingSections(String timingContent) {
        if (timingContent == null) return 0;
        
        // Simple count based on "Timing section" headers in the content
        int count = 0;
        String[] lines = timingContent.split("\n");
        for (String line : lines) {
            if (line.contains("Timing section") || line.contains("<p:timing")) {
                count++;
            }
        }
        // If we found <p:timing tags, that's the actual count. If we found "Timing section" headers, use that.
        return count > 0 ? count : (timingContent.contains("No timing section") ? 0 : 1);
    }
    
    /**
     * Extract slide title from timing content.
     */
    private String extractSlideTitleFromContent(String timingContent) {
        if (timingContent == null) return null;
        
        String[] lines = timingContent.split("\n");
        for (String line : lines) {
            if (line.startsWith("=== SLIDE") && line.contains(" - ")) {
                // Extract title from "=== SLIDE N - Title ==="
                int dashIndex = line.indexOf(" - ");
                int lastEquals = line.lastIndexOf(" ===");
                if (dashIndex != -1 && lastEquals != -1 && lastEquals > dashIndex) {
                    return line.substring(dashIndex + 3, lastEquals).trim();
                }
            }
        }
        return null;
    }
    
    /**
     * Write timing content to individual file in session directory.
     */
    private void writeTimingToFile(File sessionDir, int slideNum, String slideTitle, String content) {
        try {
            String safeTitle = slideTitle != null ? 
                slideTitle.replaceAll("[^a-zA-Z0-9.-]", "_") : "no_title";
            if (safeTitle.length() > 50) {
                safeTitle = safeTitle.substring(0, 50);
            }
            String filename = String.format("slide_%03d_%s.xml", slideNum, safeTitle);
            
            File outputFile = new File(sessionDir, filename);
            Files.write(outputFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Silent failure for individual file writes
        }
    }

    // ========== HELPER METHODS (Future: Extract to Service Classes) ==========
    
    /**
     * Parse slide range specification into list of slide numbers.
     * Future: Extract to SlideRangeParser service
     */
    private List<Integer> parseSlideRange(String slideRange) {
        List<Integer> slides = new ArrayList<>();
        String args = slideRange.trim();
        
        if ("all".equalsIgnoreCase(args)) {
            // Get all slides from presentation metadata
            try {
                var metadata = orchestrator.getPresentationMetadata();
                for (int i = 1; i <= metadata.getSlideCount(); i++) {
                    slides.add(i);
                }
            } catch (Exception e) {
                // Fallback: try slides 1-50
                for (int i = 1; i <= 50; i++) {
                    slides.add(i);
                }
            }
        } else if (args.contains("-")) {
            // Range format: start-end
            String[] parts = args.split("-", 2);
            try {
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());
                for (int i = start; i <= end; i++) {
                    slides.add(i);
                }
            } catch (NumberFormatException e) {
                return Collections.emptyList();
            }
        } else {
            // Single slide number
            try {
                slides.add(Integer.parseInt(args));
            } catch (NumberFormatException e) {
                return Collections.emptyList();
            }
        }
        
        return slides;
    }
    
    /**
     * Create timestamped output directory for diagnostic files.
     * Future: Extract to DiagnosticFileWriter service
     */
    private File createTimestampedOutputDirectory(String subdirName) throws Exception {
        File logsDir = new File(".excudo/logs/" + subdirName);
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File sessionDir = new File(logsDir, timestamp);
        sessionDir.mkdirs();
        
        return sessionDir;
    }
    
    /**
     * Process all shapes on a slide and extract to files if requested.
     * Future: Extract to ShapeAnalysisService
     */
    private int processSlideShapes(Document doc, int slideNum, File sessionDir, boolean writeToFile) throws Exception {

        // Setup XPath
        XPath xpath = XMLFactoryProvider.createXPath();
        
        // Extract title from slide for filename prefix
        String titleText = extractSlideTitle(doc, xpath);
        String safeTitle = (titleText != null ? titleText : "no_title").replaceAll("[^a-zA-Z0-9_\\-]", "_");
        
        // Find all shape elements
        NodeList spNodes = doc.getElementsByTagName("p:sp");
        NodeList picNodes = doc.getElementsByTagName("p:pic");
        NodeList grpSpNodes = doc.getElementsByTagName("p:grpSp");
        
        int slideShapeCount = 0;
        
        if (writeToFile && sessionDir != null) {
            // Extract text shapes (p:sp)
            for (int i = 0; i < spNodes.getLength(); i++) {
                org.w3c.dom.Element shape = (org.w3c.dom.Element) spNodes.item(i);
                if (extractShapeToFile(shape, sessionDir, slideNum, safeTitle, "shape", xpath)) {
                    slideShapeCount++;
                }
            }
            
            // Extract picture shapes (p:pic)
            for (int i = 0; i < picNodes.getLength(); i++) {
                org.w3c.dom.Element shape = (org.w3c.dom.Element) picNodes.item(i);
                if (extractShapeToFile(shape, sessionDir, slideNum, safeTitle, "pic", xpath)) {
                    slideShapeCount++;
                }
            }
            
            // Extract group shapes (p:grpSp)
            for (int i = 0; i < grpSpNodes.getLength(); i++) {
                org.w3c.dom.Element shape = (org.w3c.dom.Element) grpSpNodes.item(i);
                if (extractShapeToFile(shape, sessionDir, slideNum, safeTitle, "group", xpath)) {
                    slideShapeCount++;
                }
            }
        } else {
            // Just count shapes for console output
            slideShapeCount = spNodes.getLength() + picNodes.getLength() + grpSpNodes.getLength();
        }
        
        return slideShapeCount;
    }
    
    /**
     * Extract individual shape to XML file with metadata and analysis.
     * Future: Extract to ShapeAnalysisService
     */
    private boolean extractShapeToFile(org.w3c.dom.Element shape, File outputDir, 
                                      int slideNum, String safeTitle, String shapeType,
                                      XPath xpath) {
        try {
            // Extract shape metadata
            String shapeId = extractShapeAttribute(shape, "id", "unknown");
            String shapeName = extractShapeAttribute(shape, "name", "unnamed");
            boolean isPlaceholder = hasPlaceholderElement(shape);
            
            // Create filename
            String filename = String.format("slide_%03d_%s_%s_%s_%s_%s.xml", 
                slideNum, safeTitle, shapeType, shapeId,
                isPlaceholder ? "placeholder" : "regular",
                shapeName.replaceAll("[^a-zA-Z0-9_\\-]", "_"));
            
            // Create wrapper document with metadata
            Document newDoc = XMLFactoryProvider.createDocument();
            
            // Create root element with metadata
            org.w3c.dom.Element root = newDoc.createElement("shape-dump");
            root.setAttribute("slide", String.valueOf(slideNum));
            root.setAttribute("shape-id", shapeId);
            root.setAttribute("shape-name", shapeName);
            root.setAttribute("shape-type", shapeType);
            root.setAttribute("is-placeholder", String.valueOf(isPlaceholder));
            root.setAttribute("generated-by", "excudo");
            root.setAttribute("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            
            // Add shape content analysis
            addShapeAnalysis(root, shape, xpath);
            
            // Import and append the shape XML
            org.w3c.dom.Node importedShape = newDoc.importNode(shape, true);
            root.appendChild(importedShape);
            newDoc.appendChild(root);
            
            // Write to file with pretty formatting
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            
            DOMSource source = new DOMSource(newDoc);
            StreamResult result = new StreamResult(new File(outputDir, filename));
            
            transformer.transform(source, result);
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Extract shape attribute (id or name) from cNvPr element.
     */
    private String extractShapeAttribute(org.w3c.dom.Element shape, String attrName, String defaultValue) {
        try {
            NodeList cNvPrNodes = shape.getElementsByTagName("p:cNvPr");
            if (cNvPrNodes.getLength() > 0) {
                org.w3c.dom.Element cNvPr = (org.w3c.dom.Element) cNvPrNodes.item(0);
                String value = cNvPr.getAttribute(attrName);
                return value.isEmpty() ? defaultValue : value;
            }
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Check if shape has placeholder element.
     */
    private boolean hasPlaceholderElement(org.w3c.dom.Element shape) {
        try {
            NodeList nvPrNodes = shape.getElementsByTagName("p:nvPr");
            if (nvPrNodes.getLength() > 0) {
                org.w3c.dom.Element nvPr = (org.w3c.dom.Element) nvPrNodes.item(0);
                NodeList phNodes = nvPr.getElementsByTagName("p:ph");
                return phNodes.getLength() > 0;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Add comprehensive shape analysis to dump metadata.
     */
    private void addShapeAnalysis(org.w3c.dom.Element root, org.w3c.dom.Element shape, XPath xpath) {
        try {
            Document doc = root.getOwnerDocument();
            org.w3c.dom.Element analysis = doc.createElement("analysis");
            
            // Text content analysis
            NodeList textNodes = shape.getElementsByTagName("a:t");
            if (textNodes.getLength() > 0) {
                org.w3c.dom.Element textInfo = doc.createElement("text-content");
                textInfo.setAttribute("has-text", "true");
                textInfo.setAttribute("text-count", String.valueOf(textNodes.getLength()));
                
                StringBuilder allText = new StringBuilder();
                for (int i = 0; i < textNodes.getLength(); i++) {
                    String text = textNodes.item(i).getTextContent();
                    if (!text.trim().isEmpty()) {
                        allText.append(text);
                        if (i < textNodes.getLength() - 1) allText.append(" ");
                    }
                }
                textInfo.setTextContent(allText.toString());
                analysis.appendChild(textInfo);
            }
            
            // Font and formatting analysis
            NodeList rPrNodes = shape.getElementsByTagName("a:rPr");
            if (rPrNodes.getLength() > 0) {
                org.w3c.dom.Element fontInfo = doc.createElement("font-formatting");
                
                for (int i = 0; i < rPrNodes.getLength(); i++) {
                    org.w3c.dom.Element rPr = (org.w3c.dom.Element) rPrNodes.item(i);
                    
                    org.w3c.dom.Element fontEntry = doc.createElement("run-properties");
                    if (rPr.hasAttribute("sz")) {
                        fontEntry.setAttribute("font-size", rPr.getAttribute("sz"));
                    }
                    if (rPr.hasAttribute("b")) {
                        fontEntry.setAttribute("bold", rPr.getAttribute("b"));
                    }
                    
                    // Check for color information
                    NodeList solidFillNodes = rPr.getElementsByTagName("a:solidFill");
                    if (solidFillNodes.getLength() > 0) {
                        fontEntry.setAttribute("has-color", "true");
                    }
                    
                    fontInfo.appendChild(fontEntry);
                }
                analysis.appendChild(fontInfo);
            }
            
            // List style analysis
            NodeList lstStyleNodes = shape.getElementsByTagName("a:lstStyle");
            if (lstStyleNodes.getLength() > 0) {
                org.w3c.dom.Element listInfo = doc.createElement("list-style");
                listInfo.setAttribute("has-list-style", "true");
                
                // Count level definitions
                for (int level = 1; level <= 9; level++) {
                    NodeList levelNodes = shape.getElementsByTagName("a:lvl" + level + "pPr");
                    if (levelNodes.getLength() > 0) {
                        listInfo.setAttribute("has-level-" + level, "true");
                    }
                }
                analysis.appendChild(listInfo);
            }
            
            root.appendChild(analysis);
            
        } catch (Exception e) {
            // Analysis is optional - don't fail if it errors
        }
    }
    
    /**
     * Convert XML node to formatted string.
     */
    private String nodeToString(Node node) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        DOMSource source = new DOMSource(node);
        
        transformer.transform(source, result);
        return writer.toString();
    }
}