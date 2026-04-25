package com.excudo.core.model;

import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.PlaceholderGeometry;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.util.*;

/**
 * Manages PowerPoint slide layouts and provides dynamic layout information to the LLM.
 * Parses slide layout XML files to determine placeholder capabilities and SPID allocation patterns.
 */
public class LayoutManager {

    private static final ComponentLogger logger = Logger.getLogger(LayoutManager.class);

    private final DocumentBuilder documentBuilder;
    private final XPath xpath;
    private final Map<String, LayoutInfo> layoutCache;

    /**
     * Construct from pre-parsed layout data (no disk access needed).
     * Used by the fully in-memory PPTXDocument path.
     */
    public LayoutManager(Map<String, LayoutInfo> preloadedLayouts) throws XMLParsingException {
        this.layoutCache = new HashMap<>(preloadedLayouts);
        try {
            this.documentBuilder = XMLFactoryProvider.createDocumentBuilder();
            this.xpath = XMLFactoryProvider.createXPath();
        } catch (ParserConfigurationException e) {
            throw new XMLParsingException("Failed to initialize LayoutManager", e);
        }
    }
    
    /**
     * Get all available slide layouts with their capabilities.
     * This provides dynamic context to the LLM about layout options.
     * 
     * @return List of LayoutInfo objects describing available layouts
     */
    public List<LayoutInfo> getAvailableLayouts() throws XMLParsingException {
        List<LayoutInfo> sorted = new ArrayList<>(layoutCache.values());
        sorted.sort(Comparator.comparingInt(l -> {
            String id = l.getLayoutId();
            try {
                return Integer.parseInt(id.replaceAll("\\D+", ""));
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }));
        return sorted;
    }
    
    /**
     * Get layout information by layout ID.
     * Uses caching for performance.
     * 
     * @param layoutId The layout ID (e.g., "slideLayout1")
     * @return LayoutInfo or null if not found
     */
    public LayoutInfo getLayoutInfo(String layoutId) throws XMLParsingException {
        return layoutCache.get(layoutId);
    }

    /**
     * Resolve a layout ID that may be an exact ID ("slideLayout2"), a display name
     * ("Title, Content"), or a fuzzy/kebab-case variant ("title-content", "two_content").
     * Returns the canonical layout ID or null if no match found.
     */
    public String resolveLayoutId(String input) throws XMLParsingException {
        if (input == null || input.trim().isEmpty()) return null;
        input = input.trim();

        // Exact match
        LayoutInfo exact = getLayoutInfo(input);
        if (exact != null) return input;

        // Fuzzy match using Levenshtein distance against layout names and IDs
        List<LayoutInfo> layouts = getAvailableLayouts();
        String normalized = normalize(input);

        String bestId = null;
        int bestDistance = Integer.MAX_VALUE;

        for (LayoutInfo layout : layouts) {
            // Compare against normalized layout name
            int d = levenshtein(normalized, normalize(layout.getName()));
            if (d < bestDistance) {
                bestDistance = d;
                bestId = layout.getLayoutId();
            }
            // Also compare against layout ID itself
            int d2 = levenshtein(normalized, normalize(layout.getLayoutId()));
            if (d2 < bestDistance) {
                bestDistance = d2;
                bestId = layout.getLayoutId();
            }
        }

        // Accept if distance is within ~40% of the input length (generous threshold)
        int threshold = Math.max(3, normalized.length() * 2 / 5);
        if (bestId != null && bestDistance <= threshold) {
            logger.debug("Resolved layout ID '{}' -> '{}' (distance {})", input, bestId, bestDistance);
            return bestId;
        }

        logger.warn("Could not resolve layout ID '{}', available: {}", input,
            layouts.stream().map(l -> l.getLayoutId() + "=" + l.getName())
                   .collect(java.util.stream.Collectors.joining(", ")));
        return null;
    }

    /**
     * Normalize a string for fuzzy comparison: lowercase, strip punctuation and filler.
     */
    private static String normalize(String s) {
        return s.toLowerCase().replaceAll("[_\\-,./()]+", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Levenshtein edit distance between two strings.
     */
    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }

    /**
     * Clear cached layouts so they are re-parsed from disk on next access.
     * Call after theme application which overwrites layout files.
     */
    public void reload() {
        layoutCache.clear();
    }

    /**
     * Generate LLM-friendly context about available layouts with SPID predictions.
     * This enables precognizant behavior - the LLM can predict exact SPIDs before slide creation.
     * 
     * @return Formatted string describing available layouts with SPID allocation predictions
     */
    public String generateLayoutContext() throws XMLParsingException {
        List<LayoutInfo> layouts = getAvailableLayouts();
        
        if (layouts.isEmpty()) {
            return "No custom layouts available. Use slideType parameter for basic layout options.";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("AVAILABLE SLIDE LAYOUTS WITH SPID PREDICTIONS:\n");
        context.append("Use these layouts in 'layoutId' parameter for precise SPID targeting:\n\n");
        
        for (LayoutInfo layout : layouts) {
            context.append("- ").append(layout.getLayoutId()).append(": ");
            context.append(layout.getName()).append("\n");
            context.append("  Placeholders: ").append(layout.getPlaceholderSummary()).append("\n");
            
            // Generate SPID allocation prediction
            String spidPrediction = generateSPIDPrediction(layout);
            context.append("  Predicted SPIDs: ").append(spidPrediction).append("\n");
            
            if (layout.getDescription() != null && !layout.getDescription().isEmpty()) {
                context.append("  Description: ").append(layout.getDescription()).append("\n");
            }
            context.append("\n");
        }
        
        context.append("SPID ALLOCATION RULES (for precognizant targeting):\n");
        context.append("- SPID 1: Group shape (always present, structural)\n");
        context.append("- SPID 2: Title placeholder (if layout has title)\n"); 
        context.append("- SPID 3+: Content placeholders (sequential order)\n");
        context.append("- Use predicted SPIDs for edit-content and add-animation operations\n");
        context.append("- Target existing placeholders instead of creating new shapes\n\n");

        context.append("PRECOGNIZANT WORKFLOW EXAMPLE:\n");
        context.append("1. Choose layoutId → Know exact SPID allocation\n");
        context.append("2. create → Creates predictable placeholder structure\n");
        context.append("3. edit-content → Target predicted SPIDs directly\n");
        context.append("4. add-animation → Animate predicted SPIDs in sequence\n");
        
        return context.toString();
    }
    
    /**
     * Generate SPID allocation prediction for a specific layout.
     * This enables the LLM to know exactly what SPIDs will exist after slide creation.
     * 
     * @param layout The layout to analyze
     * @return SPID prediction string (e.g., "SPID 2 (title), SPID 3 (content)")
     */
    private String generateSPIDPrediction(LayoutInfo layout) {
        StringBuilder spids = new StringBuilder();
        spids.append("SPID 1 (group)");
        
        int nextSpid = 2;
        
        if (layout.hasTitlePlaceholder()) {
            spids.append(", SPID ").append(nextSpid).append(" (title)");
            nextSpid++;
        }
        
        int contentCount = layout.getContentPlaceholderCount();
        if (contentCount > 0) {
            for (int i = 0; i < contentCount; i++) {
                spids.append(", SPID ").append(nextSpid).append(" (content");
                if (contentCount > 1) {
                    spids.append(" ").append(i + 1);
                }
                spids.append(")");
                nextSpid++;
            }
        }
        
        return spids.toString();
    }
    
    /**
    
    /**
     * Parse placeholder geometries from layout XML document.
     * Extracts position and size information from <a:xfrm> elements for content placeholders.
     * 
     * @param document The layout XML document
     * @return List of PlaceholderGeometry objects with position and size data
     * @throws XPathExpressionException If XPath evaluation fails
     */
    private List<PlaceholderGeometry> parsePlaceholderGeometries(Document document) throws XPathExpressionException {
        List<PlaceholderGeometry> geometries = new ArrayList<>();

        // Find ALL placeholders (with idx OR type attributes)
        String xpathQuery = "//p:ph";
        NodeList placeholderNodes = (NodeList) xpath.evaluate(xpathQuery, document, XPathConstants.NODESET);

        for (int i = 0; i < placeholderNodes.getLength(); i++) {
            Element placeholderElement = (Element) placeholderNodes.item(i);
            String placeholderIndex = placeholderElement.getAttribute("idx");
            String placeholderType = placeholderElement.getAttribute("type");

            // Use idx if available, otherwise use type as the key (for title/subtitle)
            String key;
            if (placeholderIndex != null && !placeholderIndex.isEmpty()) {
                key = placeholderIndex;
            } else if (placeholderType != null && !placeholderType.isEmpty()) {
                key = "type:" + placeholderType;
            } else {
                continue;
            }

            Node shapeNode = findAncestorShape(placeholderElement);
            if (shapeNode != null) {
                PlaceholderGeometry geometry = extractGeometryFromShape(shapeNode, key, placeholderType);
                if (geometry != null) {
                    geometries.add(geometry);
                }
            }
        }

        return geometries;
    }
    
    /**
     * Find the ancestor p:sp (shape) element containing the placeholder.
     * 
     * @param placeholderElement The p:ph element
     * @return The containing p:sp element, or null if not found
     */
    private Node findAncestorShape(Element placeholderElement) {
        Node current = placeholderElement.getParentNode();
        while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) current;
            if ("p:sp".equals(element.getTagName())) {
                return element;
            }
            current = current.getParentNode();
        }
        return null;
    }
    
    /**
     * Extract geometry information from a shape element's <a:xfrm> transform.
     * 
     * @param shapeNode The p:sp element containing the shape
     * @param placeholderIndex The placeholder index for identification
     * @return PlaceholderGeometry with extracted position and size, or null if not found
     */
    private PlaceholderGeometry extractGeometryFromShape(Node shapeNode, String placeholderIndex,
                                                          String placeholderType) {
        try {
            // Find the a:xfrm element within the shape's properties
            String xfrmQuery = ".//a:xfrm";
            Node xfrmNode = (Node) xpath.evaluate(xfrmQuery, shapeNode, XPathConstants.NODE);

            if (xfrmNode == null) {
                return null;
            }

            // Extract offset (position) values
            String offsetQuery = "./a:off";
            Node offsetNode = (Node) xpath.evaluate(offsetQuery, xfrmNode, XPathConstants.NODE);
            long x = 0, y = 0;
            if (offsetNode != null && offsetNode.getNodeType() == Node.ELEMENT_NODE) {
                Element offsetElement = (Element) offsetNode;
                x = parseEmuValue(offsetElement.getAttribute("x"));
                y = parseEmuValue(offsetElement.getAttribute("y"));
            }

            // Extract extent (size) values
            String extentQuery = "./a:ext";
            Node extentNode = (Node) xpath.evaluate(extentQuery, xfrmNode, XPathConstants.NODE);
            long width = 0, height = 0;
            if (extentNode != null && extentNode.getNodeType() == Node.ELEMENT_NODE) {
                Element extentElement = (Element) extentNode;
                width = parseEmuValue(extentElement.getAttribute("cx"));
                height = parseEmuValue(extentElement.getAttribute("cy"));
            }

            return new PlaceholderGeometry(x, y, width, height, placeholderIndex, placeholderType);

        } catch (XPathExpressionException e) {
            logger.warn("Failed to extract geometry for placeholder {}: {}", placeholderIndex, e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse EMU (English Metric Units) value from string attribute.
     * 
     * @param emuString The EMU value as string
     * @return The EMU value as long, or 0 if parsing fails
     */
    private long parseEmuValue(String emuString) {
        if (emuString == null || emuString.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(emuString);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Parse layout name from the XML document.
     */
    private String parseLayoutName(Document document) throws XPathExpressionException {
        // Try to get name from cSld element
        Node nameNode = (Node) xpath.evaluate("//p:cSld/@name", document, XPathConstants.NODE);
        if (nameNode != null) {
            return nameNode.getNodeValue();
        }
        
        // Fallback: try to infer from placeholders
        if (hasPlaceholderType(document, "title", "ctrTitle")) {
            if (hasPlaceholderType(document, "body", "obj")) {
                return "Title and Content";
            } else {
                return "Title Only";
            }
        } else if (hasPlaceholderType(document, "body", "obj")) {
            return "Content Only";
        } else {
            return "Blank";
        }
    }
    
    /**
     * Detect the actual OOXML type attribute for the title placeholder in this layout.
     * Returns "ctrTitle" for title slide layouts, "title" for standard layouts, or null if no title.
     */
    private String detectTitlePlaceholderType(Document document) throws XPathExpressionException {
        if (hasPlaceholderType(document, "ctrTitle")) {
            return "ctrTitle";
        } else if (hasPlaceholderType(document, "title")) {
            return "title";
        }
        return null;
    }

    /**
     * Check if layout has placeholder of specified type(s).
     */
    private boolean hasPlaceholderType(Document document, String... types) throws XPathExpressionException {
        return countPlaceholderType(document, types) > 0;
    }
    
    /**
     * Count how many placeholders of specified type(s) exist in layout.
     * This is crucial for SPID allocation - we need to know how many SPIDs
     * are reserved for layout placeholders.
     */
    private int countPlaceholderType(Document document, String... types) throws XPathExpressionException {
        int totalCount = 0;
        for (String type : types) {
            // More specific XPath: look for p:ph elements with exact type match
            String xpathQuery = "//p:ph[@type='" + type + "']";
            NodeList placeholders = (NodeList) xpath.evaluate(xpathQuery, document, XPathConstants.NODESET);
            
            // Additional validation: verify these are actually p:ph elements
            for (int i = 0; i < placeholders.getLength(); i++) {
                Node node = placeholders.item(i);
            }
            
            totalCount += placeholders.getLength();
        }
        return totalCount;
    }
    
    /**
     * Check if layout has indexed content placeholders (placeholders with idx attribute but no explicit type).
     * In PowerPoint OOXML, content placeholders often use <p:ph idx="1"/> without type="body".
     * According to Microsoft docs, placeholders without type default to "body" (content) type.
     */
    private boolean hasIndexedContentPlaceholders(Document document) throws XPathExpressionException {
        return countIndexedContentPlaceholders(document) > 0;
    }
    
    /**
     * Count indexed content placeholders.
     * Based on actual OOXML structure:
     * - Title placeholders: <p:ph type="title"/> (no idx)
     * - Content placeholders: <p:ph idx="1"/> (no type - defaults to content/body)
     * - Other explicit types: <p:ph type="body"/>, <p:ph type="obj"/>, etc.
     */
    private int countIndexedContentPlaceholders(Document document) throws XPathExpressionException {
        // First, check if ANY p:ph elements exist at all
        String checkQuery = "//p:ph";
        NodeList allPlaceholders = (NodeList) xpath.evaluate(checkQuery, document, XPathConstants.NODESET);
        
        if (allPlaceholders.getLength() == 0) {
            return 0;
        }
        
        // Find placeholders with idx attribute but no type attribute
        // These are content placeholders by OOXML default behavior
        String xpathQuery = "//p:ph[@idx and not(@type)]";
        NodeList placeholders = (NodeList) xpath.evaluate(xpathQuery, document, XPathConstants.NODESET);
        
        // Only count placeholders that have idx but NO explicit type.
        // Placeholders with explicit type (body, obj) are already counted by countPlaceholderType().
        return placeholders.getLength();
    }

    /**
     * Get the number of content placeholders in a layout.
     * This tells us how many SPIDs are reserved for layout content.
     */
    public int getContentPlaceholderCount(String layoutId) throws XMLParsingException {
        LayoutInfo cached = layoutCache.get(layoutId);
        return cached != null ? cached.getContentPlaceholderCount() : 0;
    }
    
    /**
     * Generate human-readable description based on placeholder analysis.
     */
    private String generateLayoutDescription(boolean hasTitle, boolean hasContent, boolean hasSubtitle) {
        if (hasTitle && hasContent && hasSubtitle) {
            return "Full layout with title, subtitle, and content areas";
        } else if (hasTitle && hasContent) {
            return "Standard layout with title and content";
        } else if (hasTitle && hasSubtitle) {
            return "Title slide with subtitle";
        } else if (hasTitle) {
            return "Title-only layout";
        } else if (hasContent) {
            return "Content-only layout (no title)";
        } else {
            return "Blank layout with no predefined placeholders";
        }
    }
    
    /**
     * Find the best layout for a given slideType by semantically matching 
     * slideType requirements to actual SlideMaster layout capabilities.
     * 
     * @param slideType The generic slide type (e.g., "CONTENT", "TITLE")
     * @return LayoutInfo for the best matching layout, or null if none found
     */
    public LayoutInfo findLayoutForSlideType(String slideType) throws XMLParsingException {
        List<LayoutInfo> layouts = getAvailableLayouts();
        
        if (layouts.isEmpty()) {
            return null;
        }
        
        // Semantic mapping from slideType to layout requirements
        switch (slideType.toUpperCase()) {
            case "CONTENT":
                // Prefer content-only layouts, fallback to title+content
                return layouts.stream()
                    .filter(l -> !l.hasTitlePlaceholder() && l.hasContentPlaceholder())
                    .findFirst()
                    .orElse(layouts.stream()
                        .filter(l -> l.hasTitlePlaceholder() && l.hasContentPlaceholder())
                        .findFirst()
                        .orElse(layouts.get(0)));
                        
            case "TITLE":
            case "SECTION_HEADER":
                // Prefer title-only layouts, fallback to title+subtitle
                return layouts.stream()
                    .filter(l -> l.hasTitlePlaceholder() && !l.hasContentPlaceholder())
                    .findFirst()
                    .orElse(layouts.stream()
                        .filter(l -> l.hasTitlePlaceholder() && l.hasSubtitlePlaceholder())
                        .findFirst()
                        .orElse(layouts.get(0)));
                        
            case "TITLE_CONTENT":
            case "TWO_COLUMN":
            case "PICTURE_CAPTION":
                // Prefer title+content layouts
                return layouts.stream()
                    .filter(l -> l.hasTitlePlaceholder() && l.hasContentPlaceholder())
                    .findFirst()
                    .orElse(layouts.get(0));
                    
            case "BLANK":
                // Prefer blank layouts, fallback to any
                return layouts.stream()
                    .filter(l -> !l.hasTitlePlaceholder() && !l.hasContentPlaceholder() && !l.hasSubtitlePlaceholder())
                    .findFirst()
                    .orElse(layouts.get(0));
                    
            default:
                // Default to first available layout
                return layouts.get(0);
        }
    }
    
    /**
     * Generate LLM context that includes slideType to layout mapping.
     * This helps the LLM understand which layouts will be chosen for generic slideTypes.
     * 
     * @return Formatted context including slideType mappings
     */
    public String generateSlideTypeMappingContext() throws XMLParsingException {
        StringBuilder context = new StringBuilder();
        
        context.append("SLIDETYPE TO LAYOUT MAPPING:\n");
        context.append("When you use generic slideType parameters, these layouts will be automatically selected:\n");
        context.append("(User-addressable SPIDs only - group shapes are handled internally)\n\n");
        
        String[] commonSlideTypes = {"CONTENT", "TITLE", "TITLE_CONTENT", "SECTION_HEADER", "BLANK"};
        
        for (String slideType : commonSlideTypes) {
            LayoutInfo layout = findLayoutForSlideType(slideType);
            if (layout != null) {
                context.append("- slideType=\"").append(slideType).append("\" → ");
                context.append(layout.getLayoutId()).append(" (").append(layout.getName()).append(")\n");
                context.append("  Placeholders: ").append(layout.getPlaceholderSummary()).append("\n");
                context.append("  User SPIDs: ");
                if (layout.hasTitlePlaceholder()) {
                    context.append("Title=2, Content=3,4,5...");
                } else {
                    context.append("Content=3,4,5... (no title placeholder)");
                }
                context.append("\n\n");
            }
        }
        
        context.append("IMPORTANT: Content shapes always enumerate from SPID 3 upward,\n");
        context.append("regardless of whether the layout has a title placeholder at SPID 2.\n");
        
        return context.toString();
    }
}