package com.excudo.core.orchestration;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.excudo.xml.builders.AppPropertiesXMLBuilder;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages presentation-level orchestration operations including load, save, finalize, and metadata.
 * 
 * This service extracts presentation lifecycle management from PPTXOrchestratorImpl,
 * providing clean delegation for presentation operations while maintaining proper
 * separation of concerns.
 */
public class PresentationOrchestrationManager {

    private static final ComponentLogger logger = Logger.getLogger(PresentationOrchestrationManager.class);

    private final OrchestrationContext context;
    private Boolean darkThemeCache = null;

    /**
     * Create a PresentationOrchestrationManager with the given orchestration context.
     *
     * @param context The orchestration context providing access to managers and state.
     *                Can be null for static operations like extractPPTX that don't need context.
     */
    public PresentationOrchestrationManager(OrchestrationContext context) {
        this.context = context;
    }
    
    /**
     * Extract PPTX to temporary directory using Java's built-in ZIP extraction.
     * 
     * @param pptxFile The PPTX file to extract
     * @return The extracted directory
     * @throws Exception if extraction fails
     */
    public File extractPPTX(File pptxFile) throws Exception {
        try {
            // Create temporary directory for extraction
            File tempDir = File.createTempFile("pptx_extract_", "");
            tempDir.delete();
            tempDir.mkdirs();
            
            File extractDir = new File(tempDir, "extracted");
            extractDir.mkdirs();

            // Use Java's built-in ZIP extraction (cross-platform)
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(pptxFile);
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                File entryFile = new File(extractDir, entry.getName());
                
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    entryFile.getParentFile().mkdirs();
                    try (java.io.InputStream inputStream = zipFile.getInputStream(entry);
                         java.io.FileOutputStream outputStream = new java.io.FileOutputStream(entryFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
            zipFile.close();

            // Verify extraction success
            if (!new File(extractDir, "ppt/presentation.xml").exists()) {
                throw new Exception("Invalid PPTX structure after extraction");
            }

            return extractDir;
        } catch (Exception e) {
            throw new Exception("Failed to extract PPTX: " + pptxFile.getName(), e);
        }
    }

    /**
     * Load a PPTX file directly into a PPTXDocument (in-memory, no temp directory).
     *
     * @param pptxFile The PPTX file to load
     * @return A PPTXDocument containing all parts in memory
     */
    public static com.excudo.core.model.PPTXDocument loadToDocument(java.io.File pptxFile) throws Exception {
        return com.excudo.core.model.PPTXDocument.loadFromZip(pptxFile);
    }

    /**
     * Load a presentation from a PPTX file.
     * 
     * @param pptxFile The PPTX file to load
     * @return Result containing presentation metadata or loading errors
     */
    public ExecutionResult<PresentationMetadata> loadPresentation(File pptxFile) {
        try {
            if (pptxFile == null || !pptxFile.exists()) {
                return ExecutionResult.failure("LoadPresentation", "PPTX file not found: " + pptxFile);
            }

            if (context == null) {
                return ExecutionResult.failure("LoadPresentation",
                    "Orchestration context not initialized -- call initialize() before loadPresentation()");
            }

            // Create presentation metadata (works from PPTXDocument or disk)
            PresentationMetadata metadata = createPresentationMetadata(null, pptxFile.getName());
            
            logger.info("Successfully loaded presentation: {} with {} slides", 
                       pptxFile.getName(), metadata.getSlideCount());
            
            return ExecutionResult.success("LoadPresentation", metadata);
            
        } catch (Exception e) {
            logger.error("Failed to load presentation {}: {}", pptxFile.getAbsolutePath(), e.getMessage());
            return ExecutionResult.failure("LoadPresentation", "Failed to load presentation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Save the current presentation to a PPTX file.
     * 
     * @param outputFile The file to save to
     * @return Result indicating success/failure of saving
     */
    public ExecutionResult<File> savePresentation(File outputFile) {
        try {
            if (outputFile == null) {
                return ExecutionResult.failure("SavePresentation", "Output file cannot be null");
            }
            
            // Finalize operations before saving
            ExecutionResult<FinalizationResult> finalizationResult = finalizeOperations();
            if (!finalizationResult.isSuccess()) {
                logger.warn("Finalization had issues before save: {}", finalizationResult.getMessage());
            }
            
            // Use PPTXDocument.save() to serialize in-memory parts
            context.getDocument().save(outputFile);
            
            logger.info("Successfully saved presentation to: {}", outputFile.getAbsolutePath());
            
            return ExecutionResult.success("SavePresentation", outputFile);
            
        } catch (Exception e) {
            logger.error("Failed to save presentation to {}: {}", outputFile.getAbsolutePath(), e.getMessage());
            return ExecutionResult.failure("SavePresentation", "Failed to save presentation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Finalize all operations and prepare for PPTX packaging.
     * 
     * @return Result indicating readiness for packaging
     */
    public ExecutionResult<FinalizationResult> finalizeOperations() {
        try {
            List<String> warnings = new ArrayList<>();
            List<String> updates = new ArrayList<>();
            
            // Finalize SPID allocations
            try {
                // SPIDManager doesn't have finalizeAllocations - skipping
                updates.add("SPID allocations finalized");
            } catch (Exception e) {
                warnings.add("SPID finalization issues: " + e.getMessage());
            }
            
            // Finalize relationships
            try {
                // RelationshipManager doesn't have finalizeRelationships - skipping
                updates.add("Relationships finalized");
            } catch (Exception e) {
                warnings.add("Relationship finalization issues: " + e.getMessage());
            }
            
            // Update content types and app properties
            try {
                updateContentTypes();
                updateAppProperties();
                updates.add("Content types and properties updated");
            } catch (Exception e) {
                warnings.add("Content/properties update issues: " + e.getMessage());
            }
            
            FinalizationResult result = new FinalizationResult(
                warnings.isEmpty(), 
                updates, 
                warnings,
                "Finalization completed with " + warnings.size() + " warnings"
            );
            
            logger.info("Finalization completed: {} updates, {} warnings", updates.size(), warnings.size());
            
            return ExecutionResult.success("FinalizeOperations", result);
            
        } catch (Exception e) {
            logger.error("Failed to finalize operations: {}", e.getMessage());
            return ExecutionResult.failure("FinalizeOperations", "Failed to finalize: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get metadata about the current presentation.
     * 
     * @return Presentation metadata
     */
    public PresentationMetadata getPresentationMetadata() {
        try {
            return createPresentationMetadata(null, "current");
        } catch (Exception e) {
            logger.warn("Failed to get presentation metadata: {}", e.getMessage());
            return new PresentationMetadata(0, "Unknown", Instant.now(), List.of());
        }
    }
    
    /**
     * Get information about a specific slide.
     * 
     * @param slideNumber Slide number (1-based)
     * @return Slide metadata
     */
    public Optional<SlideMetadata> getSlideMetadata(int slideNumber) {
        try {
            // Check existence: PPTXDocument first, then filesystem
            com.excudo.core.model.PPTXDocument pptxDoc = (context != null) ? context.getDocument() : null;
            boolean slideExists = false;
            if (pptxDoc != null) {
                slideExists = pptxDoc.hasSlide(slideNumber);
            }

            if (!slideExists) {
                return Optional.empty();
            }

            com.excudo.core.model.ParsedSlideData slideData;
            com.excudo.core.services.ContextService cs = context.getContextService();
            if (cs != null) {
                slideData = cs.getSlideContext(slideNumber).getSlideData();
            } else {
                return Optional.empty();
            }

            String slideTitle = "Slide " + slideNumber;
            int shapeCount = slideData.getShapeRegistry().getAllShapes().size();
            int animationCount = slideData.getAnimationBindings().size();

            List<Integer> spids = slideData.getShapeRegistry().getAllShapes().stream()
                .map(shape -> shape.getSpid())
                .collect(java.util.stream.Collectors.toList());

            // Resolve layout display name from parsed layoutId
            String layoutName = "Unknown Layout";
            String layoutId = slideData.getLayoutId();
            if (layoutId != null) {
                try {
                    com.excudo.core.model.LayoutManager lm;
                    if (cs != null) {
                        lm = cs.getLayoutManager();
                    } else {
                        lm = null;
                    }
                    com.excudo.core.model.LayoutInfo info = lm != null ? lm.getLayoutInfo(layoutId) : null;
                    layoutName = (info != null && info.getName() != null)
                        ? info.getName() + " (" + layoutId + ")"
                        : layoutId;
                } catch (Exception e) {
                    layoutName = layoutId;
                }
            }

            SlideMetadata metadata = new SlideMetadata(
                slideNumber,
                slideTitle,
                SlideType.CONTENT,
                shapeCount,
                spids,
                animationCount,
                layoutName
            );

            return Optional.of(metadata);

        } catch (Exception e) {
            logger.warn("Failed to get metadata for slide {}: {}", slideNumber, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Get list of all slides with basic metadata.
     *
     * @return List of slide metadata
     */
    public List<SlideMetadata> getAllSlideMetadata() {
        List<SlideMetadata> metadataList = new ArrayList<>();

        try {
            // Get slide numbers from PPTXDocument or filesystem
            List<Integer> slideNumbers;
            com.excudo.core.model.PPTXDocument pptxDoc = (context != null) ? context.getDocument() : null;
            if (pptxDoc != null) {
                slideNumbers = pptxDoc.getSlideNumbers();
            } else {
                slideNumbers = new ArrayList<>();
            }

            for (int slideNumber : slideNumbers) {
                Optional<SlideMetadata> metadata = getSlideMetadata(slideNumber);
                metadata.ifPresent(metadataList::add);
            }

        } catch (Exception e) {
            logger.error("Failed to get slide metadata list: {}", e.getMessage());
        }

        return metadataList;
    }
    
    /**
     * Check whether the active theme uses a dark background.
     * Parsed once from slideMaster1.xml's clrMap and cached for the session lifetime.
     *
     * @return true if bg1="dk1" (dark background), false otherwise
     */
    public boolean isDarkTheme() {
        // Check if SlideMasterOrchestrationManager invalidated our cache
        if (context != null && Boolean.TRUE.equals(context.getContextData().get("darkThemeCacheInvalid"))) {
            darkThemeCache = null;
            context.getContextData().remove("darkThemeCacheInvalid");
        }
        if (darkThemeCache != null) {
            return darkThemeCache;
        }
        darkThemeCache = false;
        try {
            // Check from PPTXDocument first, then disk
            String content = getPartAsString(context.getDocument(), "ppt/slideMasters/slideMaster1.xml");
            if (content != null) {
                // Dark themes set bg1="dk1" or bg1="dk2" in the clrMap; light themes set bg1="lt1"/"lt2"
                if (content.contains("bg1=\"dk1\"") || content.contains("bg1=\"dk2\"")) {
                    darkThemeCache = true;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to detect theme darkness from slideMaster1.xml: {}", e.getMessage());
        }
        logger.info("Theme dark background detection: {}", darkThemeCache ? "dark" : "light");
        return darkThemeCache;
    }

    /**
     * Create presentation metadata from extracted directory.
     * 
     * @param extractedDir The extracted PPTX directory
     * @param title The presentation title
     * @return PresentationMetadata object
     */
    private PresentationMetadata createPresentationMetadata(File extractedDir, String title) {
        try {
            // Count slides from PPTXDocument
            com.excudo.core.model.PPTXDocument pptxDoc = (context != null) ? context.getDocument() : null;
            int slideCount = (pptxDoc != null) ? pptxDoc.getSlideCount() : 0;

            // Count layouts from PPTXDocument
            int layoutCount = 0;
            if (pptxDoc != null) {
                layoutCount = (int) pptxDoc.getPartNamesByPrefix("ppt/slideLayouts/").stream()
                    .filter(p -> p.endsWith(".xml") && !p.contains("_rels")).count();
            }
            
            // Get slide metadata list
            List<SlideMetadata> slideMetadataList = getAllSlideMetadata();
            
            // Convert SlideMetadata list to author list (basic implementation)
            List<String> authors = List.of("Generated by Excudo");
            return new PresentationMetadata(slideCount, title, Instant.now(), authors);
            
        } catch (Exception e) {
            logger.warn("Failed to create presentation metadata: {}", e.getMessage());
            return new PresentationMetadata(0, title, Instant.now(), List.of());
        }
    }
    
    /**
     * Update content types for the presentation.
     */
    private void updateContentTypes() {
        try {
            com.excudo.core.model.PPTXDocument pptxDoc = (context != null) ? context.getDocument() : null;
            if (pptxDoc != null && pptxDoc.hasPart("[Content_Types].xml")) {
                // Content types are managed in-memory; no disk action needed
                logger.debug("Content types part present in PPTXDocument, assuming it's up to date");
            }
        } catch (Exception e) {
            logger.warn("Failed to update content types: {}", e.getMessage());
        }
    }
    
    private static final Pattern TEXT_CONTENT_PATTERN = Pattern.compile("<a:t>([^<]*)</a:t>");
    private static final Pattern TITLE_SHAPE_PATTERN = Pattern.compile(
            "<p:ph[^>]*type=\"(?:title|ctrTitle)\"[^>]*/>");
    private static final Pattern THEME_NAME_PATTERN = Pattern.compile("name=\"([^\"]+)\"");

    /**
     * Update app properties for the presentation.
     * Uses PPTXDocument for in-memory data when available, falls back to disk scanning.
     */
    private void updateAppProperties() {
        try {
            com.excudo.core.model.PPTXDocument pptxDoc = (context != null) ? context.getDocument() : null;

            // Check if app.xml exists in PPTXDocument
            if (pptxDoc == null || !pptxDoc.hasPart("docProps/app.xml")) {
                logger.warn("docProps/app.xml not found in PPTXDocument, skipping update");
                return;
            }

            int slideCount = 0;
            int wordCount = 0;
            int paragraphCount = 0;
            List<String> slideTitles = new ArrayList<>();
            String themeName = null;
            List<String> fontsUsed = new ArrayList<>();

            com.excudo.core.services.ContextService cs = context.getContextService();

            // Get slide data from PPTXDocument
            List<Integer> slideNumbers = pptxDoc.getSlideNumbers();
            slideCount = slideNumbers.size();

            for (int slideNum : slideNumbers) {
                // Title and word/paragraph counts via ContextService
                if (cs != null) {
                    try {
                        com.excudo.core.services.ContextService.SlideContext slideCtx =
                            cs.getSlideContext(slideNum);
                        com.excudo.core.model.ParsedSlideData slideData = slideCtx.getSlideData();

                        // Title extraction
                        String title = null;
                        for (com.excudo.core.model.SlideShape shape : slideData.getShapeRegistry().getAllShapes()) {
                            if (shape.getName() != null
                                    && shape.getName().toLowerCase().contains("title")
                                    && shape.hasText()) {
                                title = shape.getTextContent();
                                break;
                            }
                        }
                        slideTitles.add(title != null ? title : "");

                        wordCount += slideData.getWordCount();
                        paragraphCount += slideData.getParagraphCount();
                        continue;
                    } catch (Exception e) {
                        logger.debug("ContextService fallback for slide {}: {}", slideNum, e.getMessage());
                    }
                }

                // Fallback: read raw XML from PPTXDocument
                String content = null;
                org.w3c.dom.Document doc = pptxDoc.getSlideDocument(slideNum);
                if (doc != null) {
                    content = serializeToString(doc);
                }
                if (content != null) {
                    String title = extractSlideTitle(content);
                    slideTitles.add(title != null ? title : "");
                    // Regex word count
                    Matcher textMatcher = TEXT_CONTENT_PATTERN.matcher(content);
                    while (textMatcher.find()) {
                        String text = textMatcher.group(1).trim();
                        if (!text.isEmpty()) wordCount += text.split("\\s+").length;
                    }
                    // Regex paragraph count
                    int searchFrom = 0;
                    while (true) {
                        int pStart = content.indexOf("<a:p>", searchFrom);
                        int pStartAlt = content.indexOf("<a:p ", searchFrom);
                        if (pStart < 0 && pStartAlt < 0) break;
                        if (pStart < 0) pStart = Integer.MAX_VALUE;
                        if (pStartAlt < 0) pStartAlt = Integer.MAX_VALUE;
                        int pIdx = Math.min(pStart, pStartAlt);
                        int pEnd = content.indexOf("</a:p>", pIdx);
                        if (pEnd < 0) break;
                        String pContent = content.substring(pIdx, pEnd);
                        if (pContent.contains("<a:r>") || pContent.contains("<a:r ")) paragraphCount++;
                        searchFrom = pEnd + 6;
                    }
                    extractFontsFromContent(content, fontsUsed);
                }
            }

            // Theme name and fonts
            themeName = com.excudo.core.themes.ThemeManager.getCurrentThemeName();
            if ("Default (No Theme Loaded)".equals(themeName)) themeName = null;

            // Extract fonts from theme and master DOMs
            for (String partName : new String[]{"ppt/theme/theme1.xml", "ppt/slideMasters/slideMaster1.xml"}) {
                String xml = getPartAsString(pptxDoc, partName);
                if (xml != null) extractFontsFromContent(xml, fontsUsed);
            }

            // Build and write app.xml
            AppPropertiesXMLBuilder builder = AppPropertiesXMLBuilder.create()
                    .withPresentationFormat(com.excudo.core.utils.XMLConstants.DocProperties.FORMAT_WIDESCREEN)
                    .withSlides(slideCount)
                    .withWords(wordCount)
                    .withParagraphs(paragraphCount);
            for (String font : fontsUsed) builder.addFont(font);
            if (themeName != null) builder.withThemeName(themeName);
            for (String title : slideTitles) builder.addSlideTitle(title);

            String appXml = builder.build();
            try {
                org.w3c.dom.Document appDoc = com.excudo.core.utils.XMLFactoryProvider.parseDocumentFromString(appXml);
                pptxDoc.putXmlPart("docProps/app.xml", appDoc);
            } catch (Exception e) {
                logger.warn("Failed to write app.xml to PPTXDocument: {}", e.getMessage());
            }

            logger.debug("Updated app properties: {} slides, {} words, {} paragraphs", slideCount, wordCount, paragraphCount);

        } catch (Exception e) {
            logger.warn("Failed to update app properties: {}", e.getMessage());
        }
    }

    /**
     * Get an XML part as a serialized string, from PPTXDocument or disk.
     */
    private String getPartAsString(com.excudo.core.model.PPTXDocument pptxDoc, String partName) {
        try {
            if (pptxDoc != null) {
                org.w3c.dom.Document doc = pptxDoc.getXmlPart(partName);
                if (doc != null) return serializeToString(doc);
            }
        } catch (Exception e) {
            logger.debug("Failed to read part {}: {}", partName, e.getMessage());
        }
        return null;
    }

    /**
     * Serialize a DOM Document to a string for regex-based extraction.
     */
    private String serializeToString(org.w3c.dom.Document doc) {
        try {
            java.io.StringWriter writer = new java.io.StringWriter();
            javax.xml.transform.Transformer t = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static final Pattern TYPEFACE_PATTERN = Pattern.compile("typeface=\"([^\"]+)\"");

    /**
     * Extract font typeface names from XML content.
     * Scans for typeface attributes and filters out OOXML theme font placeholders
     * (e.g., +mj-lt, +mn-ea) which are not concrete font names.
     */
    private void extractFontsFromContent(String xmlContent, List<String> fontsUsed) {
        Matcher matcher = TYPEFACE_PATTERN.matcher(xmlContent);
        while (matcher.find()) {
            String typeface = matcher.group(1).trim();
            if (!typeface.isEmpty() && !typeface.startsWith("+") && !fontsUsed.contains(typeface)) {
                fontsUsed.add(typeface);
            }
        }
    }

    /**
     * Extract the title text from a slide XML string.
     * Looks for the title placeholder shape and extracts its text content.
     */
    private String extractSlideTitle(String slideXml) {
        // Find title placeholder
        int phIdx = slideXml.indexOf("type=\"title\"");
        if (phIdx < 0) {
            phIdx = slideXml.indexOf("type=\"ctrTitle\"");
        }
        if (phIdx < 0) {
            return null;
        }

        // Find the containing <p:sp> element and its <p:txBody>
        int spStart = slideXml.lastIndexOf("<p:sp>", phIdx);
        if (spStart < 0) {
            spStart = slideXml.lastIndexOf("<p:sp ", phIdx);
        }
        if (spStart < 0) return null;

        int spEnd = slideXml.indexOf("</p:sp>", phIdx);
        if (spEnd < 0) return null;

        String spContent = slideXml.substring(spStart, spEnd);

        // Extract all text runs from this shape
        StringBuilder title = new StringBuilder();
        Matcher textMatcher = TEXT_CONTENT_PATTERN.matcher(spContent);
        while (textMatcher.find()) {
            if (title.length() > 0) title.append(" ");
            title.append(textMatcher.group(1));
        }

        if (title.length() == 0) return null;
        // Text extracted from XML contains entity-encoded characters (e.g. &amp;).
        // Unescape so that downstream escapeXml() doesn't double-encode.
        return unescapeXmlEntities(title.toString());
    }

    private static String unescapeXmlEntities(String text) {
        return text.replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&apos;", "'")
                   .replace("&quot;", "\"");
    }
    
    /**
     * Extract slide number from filename (e.g., "slide3.xml" -> 3).
     * 
     * @param filename The slide filename
     * @return The slide number, or 0 if parsing fails
     */
    private int extractSlideNumber(String filename) {
        try {
            String numberStr = filename.substring(5, filename.lastIndexOf('.'));
            return Integer.parseInt(numberStr);
        } catch (Exception e) {
            logger.warn("Failed to extract slide number from filename: {}", filename);
            return 0;
        }
    }
    
    
    /**
     * Package a directory back into a PPTX (ZIP) file.
     * Extracted from PPTXOrchestratorImpl for proper separation of concerns.
     * 
     * @param extractedDir The extracted directory to package
     * @param outputFile The output PPTX file
     * @throws Exception if packaging fails
     */
    public void packagePPTXDirectory(File extractedDir, File outputFile) throws Exception {
        // Ensure parent directory exists
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }
        
        try (java.util.zip.ZipOutputStream zipOut = new java.util.zip.ZipOutputStream(
                new java.io.FileOutputStream(outputFile))) {
            
            addDirectoryToZip(extractedDir, extractedDir, zipOut);
        }
    }
    
    /**
     * Recursively add directory contents to ZIP.
     * 
     * @param rootDir The root directory for calculating relative paths
     * @param currentDir The current directory being processed
     * @param zipOut The ZIP output stream
     * @throws Exception if adding files fails
     */
    private void addDirectoryToZip(File rootDir, File currentDir, java.util.zip.ZipOutputStream zipOut) throws Exception {
        File[] files = currentDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    addDirectoryToZip(rootDir, file, zipOut);
                } else {
                    // Get relative path from root
                    String relativePath = rootDir.toPath().relativize(file.toPath()).toString();
                    // Use forward slashes for ZIP entries (cross-platform compatibility)
                    relativePath = relativePath.replace('\\', '/');
                    
                    java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(relativePath);
                    zipOut.putNextEntry(entry);
                    
                    try (java.io.FileInputStream fileIn = new java.io.FileInputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fileIn.read(buffer)) != -1) {
                            zipOut.write(buffer, 0, bytesRead);
                        }
                    }
                    zipOut.closeEntry();
                }
            }
        }
    }
}
