package com.excudo.core.smartcontent;

import com.excudo.core.model.*;
import com.excudo.core.geometry.IntelligentLayoutEngine;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.excudo.xml.writers.SlideXMLWriter;
import com.excudo.xml.writers.SPIDManager;
import com.excudo.xml.writers.RelationshipManager;
import com.excudo.xml.writers.SlideNotesWriter;
import com.excudo.utils.NotesSlideRegistry;
import com.excudo.xml.shapes.ShapeFactoryRegistry;
import com.excudo.xml.parsers.SlideXMLParser;
import com.excudo.core.utils.XMLConstants;
import com.excudo.exceptions.XMLParsingException;
import org.w3c.dom.Document;
import java.io.File;
import java.util.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Font;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.FontMetrics;
import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;

/**
 * SmartContent system for intelligent icon injection and slide enhancement.
 * Provides automatic content analysis, icon suggestion, smart positioning, and attribution.
 * 
 * Key Features:
 * - Icon repository integration (Flaticon API + local uploads)
 * - Collision-aware positioning using IntelligentLayoutEngine
 * - Automatic attribution in slide Notes sections
 * - Content analysis for relevant image suggestions
 */
public class SmartContentEnhancer {
    
    private static final ComponentLogger logger = Logger.llm();
    
    // ========== CORE COMPONENTS ==========

    private final String iconCacheDir;
    private final String presentationPath;
    private final IntelligentLayoutEngine layoutEngine;
    private final IconRepository iconRepository;
    private final ShapeFactoryRegistry shapeFactoryRegistry;
    private final NotesSlideRegistry notesSlideRegistry;
    private final RelationshipManager sharedRelationshipManager;
    private com.excudo.core.model.PPTXDocument pptxDocument;
    
    // ========== CONFIGURATION ==========
    
    private static final long DEFAULT_ICON_SIZE = 914400L; // 1 inch in EMUs
    
    public SmartContentEnhancer(String iconCacheDir, String presentationPath,
                               NotesSlideRegistry notesSlideRegistry, RelationshipManager relationshipManager) {
        this.iconCacheDir = iconCacheDir;
        this.presentationPath = presentationPath;
        this.layoutEngine = new IntelligentLayoutEngine();
        this.iconRepository = IconRepository.fromEnvironment(iconCacheDir);
        this.shapeFactoryRegistry = new ShapeFactoryRegistry();
        this.notesSlideRegistry = notesSlideRegistry;
        this.sharedRelationshipManager = relationshipManager;

        initializeCacheDirectory();

        logger.info("SmartContentEnhancer initialized with cache: " + iconCacheDir +
            " (Freepik API: " + (iconRepository.hasFreepikApiKey() ? "enabled" : "disabled") + ")");
    }

    public SmartContentEnhancer(String iconCacheDir, String presentationPath, NotesSlideRegistry notesSlideRegistry) {
        this(iconCacheDir, presentationPath, notesSlideRegistry, null);
    }

    // Backward compatibility constructor
    public SmartContentEnhancer(String iconCacheDir, String presentationPath) {
        this(iconCacheDir, presentationPath, null, null);
    }

    /**
     * Set the PPTXDocument for in-memory media injection.
     * When set, icons are stored as MediaElements instead of copied to disk.
     */
    public void setPPTXDocument(com.excudo.core.model.PPTXDocument pptxDocument) {
        this.pptxDocument = pptxDocument;
    }

    /**
     * Expose the icon repository for direct search/upload operations (e.g. console icon commands).
     */
    public IconRepository getIconRepository() {
        return iconRepository;
    }
    
    /**
     * Inject an icon into a slide based on content analysis and positioning
     */
    public ExecutionResult<SlideShape> injectIcon(String query, ParsedSlideData slideData, int slideNumber) {
        return injectIcon(query, slideData, slideNumber, IntelligentLayoutEngine.PlacementPreference.AUTO);
    }

    /**
     * Inject an icon into a slide with explicit placement preference.
     */
    public ExecutionResult<SlideShape> injectIcon(String query, ParsedSlideData slideData, int slideNumber,
                                                   IntelligentLayoutEngine.PlacementPreference placement) {
        try {
            logger.info("Injecting icon for query: " + query + " on slide " + slideNumber
                + " (placement: " + placement + ")");

            // Initialize layout engine with current slide content
            layoutEngine.initializeWithSlideData(slideData);

            // Find appropriate icon using the repository
            ExecutionResult<IconRepository.IconSearchResult> searchResult = iconRepository.searchIcons(query, 1);
            if (!searchResult.isSuccess() || searchResult.getData().get().getIcons().isEmpty()) {
                return ExecutionResult.failure("icon-injection", "No suitable icon found for query: " + query);
            }

            IconRepository.IconAsset iconAsset = searchResult.getData().get().getIcons().get(0);

            // Download the icon if needed
            ExecutionResult<IconRepository.IconAsset> downloadResult = iconRepository.downloadIcon(iconAsset);
            if (!downloadResult.isSuccess()) {
                return ExecutionResult.failure("icon-injection", "Failed to download icon: " + downloadResult.getErrorMessage());
            }

            // Determine optimal position for the icon with dynamic sizing
            ShapeGeometry optimalPosition = findOptimalPositionWithSizing(
                DEFAULT_ICON_SIZE, DEFAULT_ICON_SIZE,
                placement
            );
            
            if (optimalPosition == null) {
                return ExecutionResult.failure("icon-injection", "No suitable position found for icon even with size adjustment");
            }
            
            // CRITICAL: Perform complete OOXML image injection
            ExecutionResult<SlideShape> injectionResult = performCompleteImageInjection(
                iconAsset, optimalPosition, slideData, slideNumber);
            
            if (!injectionResult.isSuccess()) {
                return injectionResult;
            }
            
            // Add attribution to slide notes if not already present
            addIconAttribution(iconAsset, slideNumber);
            
            logger.info("Successfully injected icon with OOXML integration: " + iconAsset.getName());
            return injectionResult;
            
        } catch (Exception e) {
            logger.error("Failed to inject icon: " + e.getMessage());
            return ExecutionResult.failure("icon-injection", "Icon injection failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Suggest multiple relevant icons based on slide content analysis
     */
    public List<IconSuggestion> suggestIcons(int slideNumber, ParsedSlideData slideData) {
        List<IconSuggestion> suggestions = new ArrayList<>();
        
        try {
            // Analyze slide content for icon-worthy concepts
            List<String> concepts = analyzeContentForConcepts(slideData);
            
            for (String concept : concepts) {
                ExecutionResult<IconRepository.IconSearchResult> searchResult = iconRepository.searchIcons(concept, 3);
                if (searchResult.isSuccess()) {
                    for (IconRepository.IconAsset icon : searchResult.getData().get().getIcons()) {
                        suggestions.add(new IconSuggestion(concept, icon, icon.getRelevanceScore()));
                    }
                }
            }
            
            // Sort by relevance score and return top suggestions
            suggestions.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
            int maxSuggestions = Math.min(10, suggestions.size());
            return suggestions.subList(0, maxSuggestions);
            
        } catch (Exception e) {
            logger.error("Failed to generate icon suggestions: " + e.getMessage());
            return suggestions; // Return partial results
        }
    }
    
    /**
     * Enhance a slide with automatically selected and positioned icons and decorative shapes
     */
    public ExecutionResult<List<SlideShape>> enhanceSlideContent(ParsedSlideData slideData, int slideNumber) {
        List<SlideShape> addedShapes = new ArrayList<>();
        
        try {
            List<IconSuggestion> suggestions = suggestIcons(slideNumber, slideData);
            
            // Apply top 2-3 suggestions based on available space
            int maxIcons = Math.min(3, suggestions.size());
            for (int i = 0; i < maxIcons; i++) {
                IconSuggestion suggestion = suggestions.get(i);
                
                ExecutionResult<SlideShape> result = injectIcon(
                    suggestion.getConcept(), slideData, slideNumber);
                
                if (result.isSuccess() && result.getData().isPresent()) {
                    addedShapes.add(result.getData().get());
                    // Re-initialize layout engine after each addition
                    layoutEngine.initializeWithSlideData(slideData);
                }
            }
            
            // Add intelligent decorative shapes based on content analysis
            List<SlideShape> decorativeShapes = addIntelligentDecorativeShapes(slideData, slideNumber);
            addedShapes.addAll(decorativeShapes);
            
            logger.info("Enhanced slide " + slideNumber + " with " + addedShapes.size() + " shapes (" +
                       (addedShapes.size() - decorativeShapes.size()) + " icons, " + 
                       decorativeShapes.size() + " decorative shapes)");
            return ExecutionResult.success("slide-enhancement", addedShapes);
            
        } catch (Exception e) {
            logger.error("Failed to enhance slide content: " + e.getMessage());
            return ExecutionResult.failure("slide-enhancement", "Slide enhancement failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Add intelligent decorative shapes based on content analysis and available space.
     * Uses ShapeFactoryRegistry to create varied shapes instead of hardcoded rectangles.
     */
    private List<SlideShape> addIntelligentDecorativeShapes(ParsedSlideData slideData, int slideNumber) {
        List<SlideShape> decorativeShapes = new ArrayList<>();
        
        try {
            // Initialize layout engine with current slide content
            layoutEngine.initializeWithSlideData(slideData);
            
            // Analyze slide content to determine appropriate decorative shapes
            List<DecorativeShapeRecommendation> recommendations = analyzeContentForDecorativeShapes(slideData);
            
            // Apply recommendations based on available space (max 2-3 decorative shapes per slide)
            int maxShapes = Math.min(3, recommendations.size());
            for (int i = 0; i < maxShapes; i++) {
                DecorativeShapeRecommendation recommendation = recommendations.get(i);
                
                // Find optimal position for the decorative shape
                ShapeGeometry position = layoutEngine.findOptimalPosition(
                    recommendation.getWidth(), recommendation.getHeight(),
                    recommendation.getPlacementPreference()
                );
                
                if (position != null) {
                    ExecutionResult<SlideShape> result = createDecorativeShape(
                        recommendation.getShapeType(), position, slideData, slideNumber, recommendation.getRationale());
                    
                    if (result.isSuccess() && result.getData().isPresent()) {
                        decorativeShapes.add(result.getData().get());
                        // Update layout engine with new shape
                        layoutEngine.initializeWithSlideData(slideData);
                    }
                }
            }
            
            logger.info("Added " + decorativeShapes.size() + " intelligent decorative shapes to slide " + slideNumber);
            return decorativeShapes;
            
        } catch (Exception e) {
            logger.error("Failed to add intelligent decorative shapes: " + e.getMessage());
            return decorativeShapes; // Return partial results
        }
    }
    
    /**
     * Analyze slide content to recommend appropriate decorative shapes.
     * Uses content analysis to select shape types that enhance the presentation's visual appeal.
     */
    private List<DecorativeShapeRecommendation> analyzeContentForDecorativeShapes(ParsedSlideData slideData) {
        List<DecorativeShapeRecommendation> recommendations = new ArrayList<>();
        
        String combinedText = extractCombinedTextContent(slideData);
        
        // Analyze content themes and recommend appropriate shapes
        if (containsProcessOrFlow(combinedText)) {
            // Use arrows and connectors for process flows
            recommendations.add(new DecorativeShapeRecommendation(
                SlideShape.ShapeType.STAR_5_POINTS, 
                DEFAULT_ICON_SIZE, DEFAULT_ICON_SIZE,
                IntelligentLayoutEngine.PlacementPreference.TOP_RIGHT,
                "Process flow detected - adding visual emphasis"
            ));
        }
        
        if (containsAchievementOrSuccess(combinedText)) {
            // Use stars for achievements and success
            recommendations.add(new DecorativeShapeRecommendation(
                SlideShape.ShapeType.STAR_8_POINTS,
                DEFAULT_ICON_SIZE, DEFAULT_ICON_SIZE,
                IntelligentLayoutEngine.PlacementPreference.TOP_LEFT,
                "Achievement content - adding success visual"
            ));
        }
        
        if (containsDataOrAnalytics(combinedText)) {
            // Use geometric shapes for data presentations
            recommendations.add(new DecorativeShapeRecommendation(
                SlideShape.ShapeType.DIAMOND,
                DEFAULT_ICON_SIZE * 3/4, DEFAULT_ICON_SIZE * 3/4,
                IntelligentLayoutEngine.PlacementPreference.BOTTOM_RIGHT,
                "Data content - adding analytical visual element"
            ));
        }
        
        if (containsCreativeOrInnovation(combinedText)) {
            // Use interesting shapes for creative content
            recommendations.add(new DecorativeShapeRecommendation(
                SlideShape.ShapeType.EXPLOSION_8_POINTS,
                DEFAULT_ICON_SIZE * 5/4, DEFAULT_ICON_SIZE * 5/4,
                IntelligentLayoutEngine.PlacementPreference.AUTO,
                "Creative content - adding innovation visual"
            ));
        }
        
        // If no specific content detected, add subtle decorative elements
        if (recommendations.isEmpty()) {
            recommendations.add(new DecorativeShapeRecommendation(
                SlideShape.ShapeType.ELLIPSE,
                DEFAULT_ICON_SIZE / 2, DEFAULT_ICON_SIZE / 2,
                IntelligentLayoutEngine.PlacementPreference.BOTTOM_LEFT,
                "General content - adding subtle decoration"
            ));
        }
        
        return recommendations;
    }
    
    /**
     * Create a decorative shape using the ShapeFactoryRegistry system.
     * Uses SlideXMLWriter with appropriate ShapeType parameter instead of hardcoded creation.
     */
    private ExecutionResult<SlideShape> createDecorativeShape(
            SlideShape.ShapeType shapeType, ShapeGeometry position, ParsedSlideData slideData, 
            int slideNumber, String rationale) {
        
        try {
            // Use SlideXMLParser for DOM manipulation (as per requirements)
            SlideXMLParser parser = new SlideXMLParser();
            File slideFile = getSlideFile(slideNumber);
            
            if (slideFile == null || !slideFile.exists()) {
                return ExecutionResult.failure("decorative-shape", "Slide file not found for slide " + slideNumber);
            }
            
            // Parse slide document using existing infrastructure
            Document document = parser.parseSlideDocument(slideFile);
            
            // Create shape using SlideXMLWriter with ShapeFactoryRegistry integration
            SlideXMLWriter writer = new SlideXMLWriter(document);
            
            // Generate unique name for decorative shape
            String shapeName = "Decorative_" + shapeType.name().toLowerCase() + "_" + System.currentTimeMillis();
            
            // Inject shape using ShapeFactory system (no hardcoded rect!)
            int spid = writer.injectBasicShapeWithSlideContext(
                shapeType, position, null, shapeName, slideNumber);
            
            // Create SlideShape object for registry
            SlideShape decorativeShape = new SlideShape(
                spid,
                shapeName,
                shapeType,
                null, // No text content for decorative shapes
                position,
                null  // XML element will be managed by SlideXMLWriter
            );
            
            // Register the shape in slide data
            slideData.getShapeRegistry().addShape(decorativeShape);
            
            logger.info("Created decorative shape: " + shapeType + " at slide " + slideNumber + 
                       " (rationale: " + rationale + ")");
            
            return ExecutionResult.success("decorative-shape", decorativeShape);
            
        } catch (Exception e) {
            logger.error("Failed to create decorative shape " + shapeType + ": " + e.getMessage());
            return ExecutionResult.failure("decorative-shape", 
                "Failed to create decorative shape: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get slide file for parsing. Uses existing SlideXMLParser instead of duplicating DOM logic.
     */
    private File getSlideFile(int slideNumber) {
        try {
            // Determine presentation directory structure
            File presentationDir;
            if (presentationPath.endsWith(".pptx")) {
                presentationDir = new File(presentationPath).getParentFile();
            } else {
                presentationDir = new File(presentationPath);
            }
            
            // Construct slide file path
            File slideFile = new File(presentationDir, "ppt/slides/slide" + slideNumber + ".xml");
            return slideFile.exists() ? slideFile : null;
            
        } catch (Exception e) {
            logger.error("Failed to locate slide file for slide " + slideNumber + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract combined text content from all text shapes in the slide.
     */
    private String extractCombinedTextContent(ParsedSlideData slideData) {
        StringBuilder combined = new StringBuilder();
        
        for (SlideShape shape : slideData.getShapeRegistry().getTextShapes()) {
            String text = shape.getTextContent();
            if (text != null && !text.trim().isEmpty()) {
                combined.append(text).append(" ");
            }
        }
        
        return combined.toString().toLowerCase();
    }
    
    // Content analysis helper methods for intelligent shape selection
    private boolean containsProcessOrFlow(String text) {
        return text.contains("process") || text.contains("flow") || text.contains("step") || 
               text.contains("workflow") || text.contains("pipeline") || text.contains("sequence");
    }
    
    private boolean containsAchievementOrSuccess(String text) {
        return text.contains("success") || text.contains("achievement") || text.contains("award") ||
               text.contains("goal") || text.contains("milestone") || text.contains("accomplished");
    }
    
    private boolean containsDataOrAnalytics(String text) {
        return text.contains("data") || text.contains("analytics") || text.contains("metric") ||
               text.contains("analysis") || text.contains("statistic") || text.contains("report");
    }
    
    private boolean containsCreativeOrInnovation(String text) {
        return text.contains("creative") || text.contains("innovation") || text.contains("design") ||
               text.contains("brainstorm") || text.contains("ideation") || text.contains("inspiration");
    }
    
    // ========== PRIVATE IMPLEMENTATION METHODS ==========
    
    private void initializeCacheDirectory() {
        try {
            Path cachePath = Path.of(iconCacheDir);
            if (!Files.exists(cachePath)) {
                Files.createDirectories(cachePath);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize cache directory: " + e.getMessage());
        }
    }
    
    
    private int generateUniqueSpid(ParsedSlideData slideData, int slideNumber) {
        try {
            SPIDManager spidManager = SPIDManager.getInstance();
            return spidManager.allocateSpidForShape("picture", slideNumber, false, false, null);
        } catch (Exception e) {
            logger.warn("Failed to use SPIDManager for SPID allocation, falling back to simple approach: {}", e.getMessage());

            // Fallback: Find next available SPID starting from existing max + 1
            int maxSpid = 0;
            for (SlideShape shape : slideData.getShapeRegistry().getAllShapes()) {
                maxSpid = Math.max(maxSpid, shape.getSpid());
            }
            return maxSpid + 1;
        }
    }
    
    /**
     * Validates that the given directory contains a valid PPTX structure.
     * Checks for essential directories and files that should be present.
     */
    private boolean isValidPptxDirectory(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }
        
        // Check for essential PPTX directory structure
        File pptDir = new File(dir, "ppt");
        File slidesDir = new File(pptDir, "slides");
        File relsDir = new File(dir, "_rels");
        
        return pptDir.exists() && pptDir.isDirectory() &&
               slidesDir.exists() && slidesDir.isDirectory() &&
               relsDir.exists() && relsDir.isDirectory();
    }
    
    private void addIconAttribution(IconRepository.IconAsset iconAsset, int slideNumber) {
        try {
            // Build attribution text
            String iconUrl = getIconDisplayUrl(iconAsset);
            String attribution = iconAsset.getAttribution();
            String attributionText = String.format(
                "\n\n--- Icon Attribution ---\nIcon URL: %s\n%s\n",
                iconUrl,
                attribution
            );

            // When a PPTXDocument is available, store the attribution as a notes part
            if (pptxDocument != null) {
                addAttributionToPPTXDocument(slideNumber, attributionText, iconAsset.getName());
                return;
            }

            logger.warn("Icon attribution skipped: PPTXDocument not set for slide " + slideNumber);

        } catch (Exception e) {
            logger.error("Failed to add icon attribution: " + e.getMessage());
            // Don't fail the entire icon injection operation
        }
    }

    /**
     * Stores icon attribution in the PPTXDocument's in-memory notes slide part for the given slide.
     * Creates a minimal notes slide DOM if one does not already exist.
     */
    private void addAttributionToPPTXDocument(int slideNumber, String attributionText, String iconName) {
        try {
            // Determine the sequential notes number via the registry
            int notesSeqNum = (notesSlideRegistry != null)
                ? notesSlideRegistry.getNotesNumberForSlide(slideNumber)
                : -1;

            String notesPartName = (notesSeqNum > 0)
                ? "ppt/notesSlides/notesSlide" + notesSeqNum + ".xml"
                : null;

            org.w3c.dom.Document notesDoc = null;
            if (notesPartName != null) {
                notesDoc = pptxDocument.getXmlPart(notesPartName);
            }

            if (notesDoc == null) {
                // No existing notes part -- skip attribution to avoid complex DOM creation here;
                // the disk path handles this, but in-memory mode will just log a warning
                logger.warn("Icon attribution for slide " + slideNumber + " skipped in in-memory mode: " +
                    "no existing notes slide found (icon: " + iconName + ")");
                return;
            }

            // Append attribution text to the existing notes body
            javax.xml.xpath.XPath xpath = com.excudo.core.utils.XMLFactoryProvider.createXPath();
            org.w3c.dom.Element txBody = (org.w3c.dom.Element) xpath.evaluate(
                "//p:sp[p:nvSpPr/p:nvPr/p:ph[@type='body']]/p:txBody",
                notesDoc, javax.xml.xpath.XPathConstants.NODE);

            if (txBody != null) {
                org.w3c.dom.Element p = notesDoc.createElementNS(com.excudo.core.utils.XMLConstants.DRAWING_NS, "a:p");
                org.w3c.dom.Element r = notesDoc.createElementNS(com.excudo.core.utils.XMLConstants.DRAWING_NS, "a:r");
                org.w3c.dom.Element rPr = notesDoc.createElementNS(com.excudo.core.utils.XMLConstants.DRAWING_NS, "a:rPr");
                rPr.setAttribute("lang", "en-US");
                org.w3c.dom.Element t = notesDoc.createElementNS(com.excudo.core.utils.XMLConstants.DRAWING_NS, "a:t");
                t.setTextContent(attributionText);
                r.appendChild(rPr);
                r.appendChild(t);
                p.appendChild(r);
                txBody.appendChild(p);
                pptxDocument.putXmlPart(notesPartName, notesDoc);
                logger.info("Added attribution for " + iconName + " to slide " + slideNumber + " notes (in-memory)");
            }
        } catch (Exception e) {
            logger.error("Failed to add in-memory icon attribution: " + e.getMessage());
        }
    }
    
    private String getIconDisplayUrl(IconRepository.IconAsset iconAsset) {
        // For different sources, provide appropriate display URLs
        switch (iconAsset.getSource()) {
            case "devicon":
                return "https://devicons.github.io/devicon/icons/" + iconAsset.getName() + "/" + iconAsset.getName() + "-original.svg";
            case "freepik":
                return "Freepik Icon ID: " + iconAsset.getName();
            case "local":
                return "Local upload: " + iconAsset.getName();
            default:
                return iconAsset.getPath();
        }
    }
    
    private List<String> analyzeContentForConcepts(ParsedSlideData slideData) {
        List<String> concepts = new ArrayList<>();
        
        // Extract concepts from slide text content
        for (SlideShape shape : slideData.getShapeRegistry().getTextShapes()) {
            String text = shape.getTextContent();
            if (text != null && !text.trim().isEmpty()) {
                concepts.addAll(extractConceptsFromText(text));
            }
        }
        
        return concepts.stream().distinct().limit(5).toList(); // Max 5 concepts per slide
    }
    
    private List<String> extractConceptsFromText(String text) {
        List<String> concepts = new ArrayList<>();
        
        // Simple keyword extraction (can be enhanced with NLP)
        String[] words = text.toLowerCase()
            .replaceAll("[^a-zA-Z\\s]", "")
            .split("\\s+");
            
        // Filter for potential icon-worthy concepts
        for (String word : words) {
            if (word.length() > 3 && isIconWorthy(word)) {
                concepts.add(word);
            }
        }
        
        return concepts;
    }
    
    private boolean isIconWorthy(String word) {
        // Simple heuristic for icon-worthy concepts
        Set<String> iconCategories = Set.of(
            "computer", "science", "technology", "data", "network", "security",
            "programming", "algorithm", "database", "cloud", "mobile", "web",
            "education", "learning", "student", "teacher", "book", "presentation"
        );
        
        return iconCategories.contains(word.toLowerCase());
    }
    
    /**
     * Write the picture shape to the actual slide XML file using SlideXMLWriter.
     * This ensures the shape persists in the PowerPoint file for proper undo/redo support.
     */
    private void writeShapeToSlideXML(SlideShape iconShape, String relationshipId, int slideNumber) {
        try {
            // Get the slide XML file
            File slideFile = getSlideFile(slideNumber);
            if (slideFile == null || !slideFile.exists()) {
                logger.error("Slide file not found for slide " + slideNumber + " - shape will not persist");
                return;
            }
            
            // Parse the slide document
            SlideXMLParser parser = new SlideXMLParser();
            Document document = parser.parseSlideDocument(slideFile);
            
            // Create SlideXMLWriter with the parsed document
            SlideXMLWriter writer = new SlideXMLWriter(document);
            
            // Call addPictureShape to write the shape to XML
            writer.addPictureShape(
                iconShape.getSpid(),
                iconShape.getName(),
                relationshipId,
                iconShape.getGeometry()
            );
            
            // Save the document back to the slide file using SlideXMLWriter's writeXML method
            writer.writeXML(slideFile);
            
            logger.info("Successfully wrote picture shape to slide XML: SPID=" + iconShape.getSpid() + 
                       ", relationshipId=" + relationshipId);
            
        } catch (Exception e) {
            logger.error("Failed to write shape to slide XML for slide " + slideNumber + 
                        ": " + e.getMessage() + " - shape may not persist for undo operations");
            // Don't fail the entire operation, but log the issue
        }
    }
    
    /**
     * Performs complete OOXML image injection including file copying and relationship creation.
     * This is the critical method that was missing from the original implementation.
     */
    private ExecutionResult<SlideShape> performCompleteImageInjection(
            IconRepository.IconAsset iconAsset, ShapeGeometry position, 
            ParsedSlideData slideData, int slideNumber) {
        
        try {
            // 1. Copy image file to presentation media directory with target dimensions
            String mediaFileName = copyIconToMediaDirectory(iconAsset, slideNumber, 
                position.getWidth(), position.getHeight());
            if (mediaFileName == null) {
                logger.error("Failed to copy icon to media directory - no OOXML injection performed");
                return ExecutionResult.failure("image-injection", 
                    "Failed to copy image file to presentation media directory");
            }
            
            // 2. Create OOXML media relationship 
            String mediaTarget = "../media/" + mediaFileName;
            String relationshipId = createMediaRelationship(slideNumber, mediaTarget);
            if (relationshipId == null) {
                logger.error("Failed to create media relationship - no OOXML injection performed");
                return ExecutionResult.failure("image-injection", 
                    "Failed to create OOXML media relationship");
            }
            
            // 3. Create SlideShape with OOXML integration
            SlideShape iconShape = createOOXMLIntegratedIconShape(
                iconAsset, position, slideData, relationshipId, mediaFileName, slideNumber);
                
            // 4. Write the picture shape to the actual slide XML file
            logger.info("About to write shape to slide XML: SPID=" + iconShape.getSpid() + 
                       ", relationshipId=" + relationshipId + ", slideNumber=" + slideNumber);
            writeShapeToSlideXML(iconShape, relationshipId, slideNumber);
                
            // 5. Register the shape in the slide data
            slideData.getShapeRegistry().addShape(iconShape);
            
            logger.info("Successfully performed complete OOXML image injection: " + 
                       "file=" + mediaFileName + ", relationshipId=" + relationshipId + 
                       ", spid=" + iconShape.getSpid());
                       
            return ExecutionResult.success("image-injection", iconShape);
            
        } catch (Exception e) {
            logger.error("Complete OOXML image injection failed: " + e.getMessage());
            return ExecutionResult.failure("image-injection", 
                "OOXML image injection failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Copies the downloaded icon file to the presentation's media directory.
     * Returns the media file name or null if copy failed.
     * 
     * @param iconAsset the icon to copy
     * @param slideNumber the target slide number  
     * @param targetWidthEmu target width in EMUs for SVG conversion
     * @param targetHeightEmu target height in EMUs for SVG conversion
     */
    private String copyIconToMediaDirectory(IconRepository.IconAsset iconAsset, int slideNumber,
                                          long targetWidthEmu, long targetHeightEmu) {
        try {
            File sourceFile = new File(iconAsset.getPath());
            if (!sourceFile.exists()) {
                logger.error("Source icon file does not exist: " + iconAsset.getPath());
                return null;
            }

            // Extract file extension from source
            String sourceFileName = sourceFile.getName();
            String extension = "";
            int lastDotIndex = sourceFileName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                extension = sourceFileName.substring(lastDotIndex);
            }

            // Handle file format for PowerPoint compatibility
            String finalSourcePath = iconAsset.getPath();
            if (".svg".equalsIgnoreCase(extension)) {
                logger.info("Converting SVG to PNG for PowerPoint compatibility: " + sourceFileName);
                String pngPath = convertSvgToPng(iconAsset.getPath(), targetWidthEmu, targetHeightEmu);
                if (pngPath != null) {
                    finalSourcePath = pngPath;
                    extension = ".png";
                    logger.info("SVG conversion successful, using PNG: " + pngPath);
                } else {
                    logger.warn("SVG conversion failed, proceeding with original SVG (may not display properly)");
                }
            } else if (".png".equalsIgnoreCase(extension) || ".jpg".equalsIgnoreCase(extension) || ".jpeg".equalsIgnoreCase(extension)) {
                logger.info("Using " + extension.toUpperCase() + " image directly: " + sourceFileName);
            }

            // Create unique media file name
            String mediaFileName = "smartcontent_icon_slide" + slideNumber + "_" +
                                  iconAsset.getName().replaceAll("[^a-zA-Z0-9_]", "_") +
                                  "_" + System.currentTimeMillis() + extension;

            // Read the final source bytes
            File finalSourceFile = new File(finalSourcePath);
            byte[] mediaBytes = Files.readAllBytes(finalSourceFile.toPath());

            // PPTXDocument path: store as in-memory MediaElement
            if (pptxDocument != null) {
                String partName = "ppt/media/" + mediaFileName;
                String mimeType = guessMimeTypeForExtension(extension);
                com.excudo.core.model.MediaElement media =
                    new com.excudo.core.model.MediaElement(partName, mimeType, mediaBytes);
                pptxDocument.putMediaPart(media);

                // Register content type via ContentTypesModel
                com.excudo.core.model.ContentTypesModel ct = pptxDocument.getContentTypes();
                if (ct != null) {
                    ct.ensureMediaType(media);
                    pptxDocument.markContentTypesDirty();
                }

                logger.info("Added icon to PPTXDocument: " + partName);
                return mediaFileName;
            }

            // No PPTXDocument available -- cannot write media
            logger.error("Cannot inject icon: no PPTXDocument set on SmartContentEnhancer");
            return null;

        } catch (Exception e) {
            logger.error("Failed to inject icon: " + e.getMessage());
            return null;
        }
    }

    private static String guessMimeTypeForExtension(String extension) {
        String ext = extension.toLowerCase().replace(".", "");
        switch (ext) {
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "svg": return "image/svg+xml";
            default: return "application/octet-stream";
        }
    }
    
    /**
     * Creates OOXML media relationship via RelationshipManager.
     * Returns the relationship ID or null if creation failed.
     */
    private String createMediaRelationship(int slideNumber, String mediaTarget) {
        try {
            RelationshipManager relationshipManager = this.sharedRelationshipManager;

            if (relationshipManager == null) {
                logger.error("No RelationshipManager available -- cannot create media relationship for slide " + slideNumber);
                return null;
            }

            String mediaType = XMLConstants.RELATIONSHIP_TYPE_IMAGE;
            String relationshipId = relationshipManager.addMediaRelationship(
                slideNumber, mediaType, mediaTarget);

            logger.info("Created OOXML media relationship: " + relationshipId +
                       " -> " + mediaTarget + " (type: " + mediaType + ")");

            return relationshipId;

        } catch (Exception e) {
            logger.error("Failed to create media relationship: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Creates a SlideShape with proper OOXML integration including the relationship ID.
     * This replaces the simple createIconShape method.
     */
    private SlideShape createOOXMLIntegratedIconShape(
            IconRepository.IconAsset iconAsset, ShapeGeometry position, 
            ParsedSlideData slideData, String relationshipId, String mediaFileName, int slideNumber) {
        
        // Generate unique SPID for the new icon
        int spid = generateUniqueSpid(slideData, slideNumber);
        
        // Embed relationship ID in the name for later OOXML generation
        String shapeName = "Icon_" + iconAsset.getName().replaceAll("\\s+", "_") + 
                          "[rId:" + relationshipId + "]";
        
        // Store media filename in textContent field for retrieval during XML generation
        String shapeData = "OOXML_IMAGE:" + mediaFileName + ":" + relationshipId;
        
        // Create SlideShape with OOXML relationship data embedded
        return new SlideShape(
            spid,
            shapeName,
            SlideShape.ShapeType.PICTURE,
            shapeData, // Store OOXML data in textContent field
            position,
            null  // XML element will be generated during slide writing with embedded rId
        );
    }
    
    /**
     * Find optimal position with dynamic sizing - tries progressively smaller sizes if needed.
     * This ensures icons can always be placed even on crowded slides.
     */
    private ShapeGeometry findOptimalPositionWithSizing(long preferredWidth, long preferredHeight, 
                                                        IntelligentLayoutEngine.PlacementPreference preference) {
        // Size reduction steps: 100%, 80%, 60%, 40%, 25%
        double[] sizeFactors = {1.0, 0.8, 0.6, 0.4, 0.25};
        
        for (double factor : sizeFactors) {
            long adjustedWidth = (long) (preferredWidth * factor);
            long adjustedHeight = (long) (preferredHeight * factor);
            
            logger.info("Trying icon placement at " + (factor * 100) + "% size: " + 
                       (adjustedWidth/914400.0) + "x" + (adjustedHeight/914400.0) + " inches");
            
            ShapeGeometry position = layoutEngine.findOptimalPosition(adjustedWidth, adjustedHeight, preference);
            if (position != null) {
                logger.info("Successfully found position at " + (factor * 100) + "% of original size");
                return position;
            }
        }
        
        logger.warn("Failed to find position even at minimum size (25% of original)");
        return null;
    }
    
    // ========== CONFIGURATION METHODS ==========
    
    public void setFreepikApiKey(String apiKey) {
        iconRepository.setFreepikApiKey(apiKey);
        logger.info("Freepik API key configured");
    }
    
    /**
     * Convert SVG file to PNG format for PowerPoint compatibility.
     * Since PowerPoint has limited SVG support, we convert SVG files to PNG.
     * 
     * @param svgFilePath path to the SVG file
     * @param targetWidthEmu target width in EMUs
     * @param targetHeightEmu target height in EMUs
     */
    private String convertSvgToPng(String svgFilePath, long targetWidthEmu, long targetHeightEmu) {
        try {
            Path svgPath = Path.of(svgFilePath);
            if (!Files.exists(svgPath)) {
                logger.warn("SVG file not found: " + svgFilePath);
                return null;
            }
            
            // Generate PNG filename
            String pngFilePath = svgFilePath.replaceAll("\\.svg$", ".png");
            
            // Check if PNG already exists and is newer than SVG
            Path pngPath = Path.of(pngFilePath);
            if (Files.exists(pngPath) && Files.getLastModifiedTime(pngPath).compareTo(Files.getLastModifiedTime(svgPath)) >= 0) {
                logger.debug("Using existing PNG conversion: " + pngFilePath);
                return pngFilePath;
            }
            
            // Convert EMUs to pixels for PNG generation
            // Assuming 96 DPI: 1 inch = 914400 EMUs = 96 pixels
            int targetWidthPx = (int) Math.max(32, (targetWidthEmu * 96) / 914400); // Min 32px
            int targetHeightPx = (int) Math.max(32, (targetHeightEmu * 96) / 914400); // Min 32px
            
            // Use Apache Batik for proper SVG to PNG conversion
            BufferedImage image = convertSvgWithBatik(svgFilePath, targetWidthPx, targetHeightPx);
            
            logger.info("Converting SVG to PNG with dimensions: " + targetWidthPx + "x" + targetHeightPx + 
                       " pixels (target: " + (targetWidthEmu/914400.0) + "x" + (targetHeightEmu/914400.0) + " inches)");
            
            // Write PNG file
            ImageIO.write(image, "PNG", new File(pngFilePath));
            logger.info("Converted SVG to PNG: " + svgFilePath + " -> " + pngFilePath);
            
            return pngFilePath;
            
        } catch (Exception e) {
            logger.error("Failed to convert SVG to PNG: " + svgFilePath + " - " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Create a placeholder image for SVG conversion
     */
    private BufferedImage createPlaceholderImage(int width, int height, String text) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        // Enable antialiasing for better quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // Fill background with transparent
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        // Draw a simple icon placeholder
        g2d.setColor(new Color(70, 130, 180, 200)); // Steel blue with transparency
        g2d.fillRoundRect(width/4, height/4, width/2, height/2, 20, 20);
        
        // Add border
        g2d.setColor(new Color(70, 130, 180));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(width/4, height/4, width/2, height/2, 20, 20);
        
        // Add text if provided
        if (text != null && !text.trim().isEmpty()) {
            g2d.setColor(Color.WHITE);
            Font font = new Font("Arial", Font.BOLD, Math.min(width, height) / 8);
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            int x = (width - textWidth) / 2;
            int y = (height + textHeight / 2) / 2;
            g2d.drawString(text, x, y);
        }
        
        g2d.dispose();
        return image;
    }
    
    /**
     * Convert SVG to PNG using Apache Batik transcoder
     */
    private BufferedImage convertSvgWithBatik(String svgFilePath, int targetWidthPx, int targetHeightPx) {
        try {
            // Create PNG transcoder
            PNGTranscoder transcoder = new PNGTranscoder();
            
            // Set the transcoding hints
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) targetWidthPx);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) targetHeightPx);
            
            // Create transcoder input from SVG file
            TranscoderInput input = new TranscoderInput(new FileInputStream(svgFilePath));
            
            // Create output stream for PNG data
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            TranscoderOutput output = new TranscoderOutput(outputStream);
            
            // Perform the transcoding
            transcoder.transcode(input, output);
            
            // Convert byte array to BufferedImage
            byte[] pngData = outputStream.toByteArray();
            return ImageIO.read(new java.io.ByteArrayInputStream(pngData));
            
        } catch (Exception e) {
            logger.warn("Failed to convert SVG with Batik, falling back to placeholder: " + e.getMessage());
            // Fall back to placeholder if Batik conversion fails
            return createPlaceholderImage(targetWidthPx, targetHeightPx, "Exception (!)");
        }
    }
    
    // ========== HELPER CLASSES ==========
    
    public static class IconSuggestion {
        private final String concept;
        private final IconRepository.IconAsset iconAsset;
        private final double relevanceScore;
        
        public IconSuggestion(String concept, IconRepository.IconAsset iconAsset, double relevanceScore) {
            this.concept = concept;
            this.iconAsset = iconAsset;
            this.relevanceScore = relevanceScore;
        }
        
        public String getConcept() { return concept; }
        public IconRepository.IconAsset getIconAsset() { return iconAsset; }
        public double getRelevanceScore() { return relevanceScore; }
    }
    
    /**
     * Helper class for intelligent decorative shape recommendations.
     * Contains shape type, dimensions, placement preference, and rationale for intelligent shape selection.
     */
    private static class DecorativeShapeRecommendation {
        private final SlideShape.ShapeType shapeType;
        private final long width;
        private final long height;
        private final IntelligentLayoutEngine.PlacementPreference placementPreference;
        private final String rationale;
        
        public DecorativeShapeRecommendation(SlideShape.ShapeType shapeType, long width, long height,
                                           IntelligentLayoutEngine.PlacementPreference placementPreference,
                                           String rationale) {
            this.shapeType = shapeType;
            this.width = width;
            this.height = height;
            this.placementPreference = placementPreference;
            this.rationale = rationale;
        }
        
        public SlideShape.ShapeType getShapeType() { return shapeType; }
        public long getWidth() { return width; }
        public long getHeight() { return height; }
        public IntelligentLayoutEngine.PlacementPreference getPlacementPreference() { return placementPreference; }
        public String getRationale() { return rationale; }
    }
}
