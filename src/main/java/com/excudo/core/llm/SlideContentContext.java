package com.excudo.core.llm;

import com.excudo.core.model.*;
import java.util.*;

/**
 * Provides detailed slide content context to LLMs for fine-grained editing
 * Complements PresentationContext with shape-level and animation-level detail
 */
public class SlideContentContext {
    
    private final int slideNumber;
    private final ParsedSlideData slideData;
    
    public SlideContentContext(int slideNumber, ParsedSlideData slideData) {
        this.slideNumber = slideNumber;
        this.slideData = slideData;
    }
    
    /**
     * Generate LLM-friendly JSON representation of slide content
     */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"slideNumber\": ").append(slideNumber).append(",\n");
        
        // Add shapes
        List<SlideShape> shapes = slideData.getShapeRegistry().getAllShapes();
        if (shapes.isEmpty()) {
            json.append("  \"shapes\": [],\n");
        } else {
            json.append("  \"shapes\": [\n");
            for (int i = 0; i < shapes.size(); i++) {
                SlideShape shape = shapes.get(i);
                json.append(shapeToJson(shape, "    "));
                if (i < shapes.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ],\n");
        }
        
        // Add animations
        List<AnimationBinding> animations = slideData.getAnimationBindings();
        if (animations.isEmpty()) {
            json.append("  \"animations\": [],\n");
        } else {
            json.append("  \"animations\": [\n");
            for (int i = 0; i < animations.size(); i++) {
                AnimationBinding anim = animations.get(i);
                json.append(animationToJson(anim, "    "));
                if (i < animations.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ],\n");
        }
        
        // Add animation sequence info
        json.append("  \"animationSequence\": ");
        json.append(animationSequenceToJson());
        json.append(",\n");
        
        // Add full timing tree hierarchy
        json.append("  \"timingTree\": ");
        json.append(timingTreeToJson());
        json.append(",\n");
        
        // Add shape hierarchy and organization
        json.append("  \"shapeHierarchy\": ");
        json.append(shapeHierarchyToJson());
        json.append(",\n");
        
        // Add capabilities
        json.append("  \"capabilities\": {\n");
        json.append("    \"shapeOperations\": [\"add-shape\", \"update-text\", \"update-geometry\", \"delete-shape\"],\n");
        json.append("    \"animationOperations\": [\"add-animation\", \"update-animation\", \"remove-animation\"],\n");
        json.append("    \"animationTypes\": [\"appear\", \"fade\", \"fly\", \"wipe\", \"split\", \"zoom\"]\n");
        json.append("  }\n");
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Generate natural language description for LLM understanding
     */
    public String toNaturalLanguage() {
        StringBuilder description = new StringBuilder();
        description.append(String.format("Slide %d contains:\n", slideNumber));
        
        // Describe shapes
        List<SlideShape> textShapes = slideData.getShapeRegistry().getTextShapes();
        if (!textShapes.isEmpty()) {
            description.append("\nText elements:\n");
            for (SlideShape shape : textShapes) {
                description.append(String.format("- Shape '%s' (ID: %d): \"%s\"\n", 
                    shape.getName(), shape.getSpid(), 
                    truncateText(shape.getTextContent(), 50)));
            }
        }
        
        // Describe animations and timing structure
        if (!slideData.getAnimationBindings().isEmpty()) {
            description.append(String.format("\n%d animations configured:\n", 
                slideData.getAnimationBindings().size()));
            
            // Describe timing tree structure
            TimingTree tree = slideData.getTimingTree();
            if (tree.getRootNode() != null) {
                int totalNodes = countNodesRecursive(tree.getRootNode());
                int clickTriggers = countClickTriggers(tree.getRootNode());
                int maxDepth = calculateMaxDepth(tree.getRootNode(), 0);
                
                description.append(String.format("\nTiming structure: %d nodes, %d click triggers, depth %d\n",
                    totalNodes, clickTriggers, maxDepth));
                
                if (clickTriggers > 0) {
                    description.append("Animation grouping:\n");
                    describeTimingStructure(tree.getRootNode(), description, 0);
                }
            }
            
            // Describe animation sequence
            Map<Integer, List<AnimationInfo>> animationSequence = analyzeAnimationSequence();
            if (!animationSequence.isEmpty()) {
                description.append("\nAnimation sequence:\n");
                for (Map.Entry<Integer, List<AnimationInfo>> entry : animationSequence.entrySet()) {
                    description.append(String.format("- Click %d: %d effect(s)\n", 
                        entry.getKey(), entry.getValue().size()));
                    for (AnimationInfo info : entry.getValue()) {
                        description.append(String.format("  - %s animation on shape %d (%s)\n",
                            info.animationType, info.targetSpid, info.shapeName));
                    }
                }
            }
        }
        
        return description.toString();
    }
    
    /**
     * Convert shape to JSON string
     */
    private String shapeToJson(SlideShape shape, String indent) {
        StringBuilder json = new StringBuilder();
        json.append(indent).append("{\n");
        json.append(indent).append("  \"spid\": ").append(shape.getSpid()).append(",\n");
        json.append(indent).append("  \"name\": \"").append(escapeJson(shape.getName())).append("\",\n");
        json.append(indent).append("  \"type\": \"").append(shape.getType()).append("\"");
        
        // Add editability guidance for placeholders and content shapes
        if (isEditableContentArea(shape)) {
            json.append(",\n");
            json.append(indent).append("  \"editable\": true,\n");
            json.append(indent).append("  \"purpose\": \"").append(getShapePurpose(shape)).append("\"");
        }
        
        if (shape.hasText()) {
            json.append(",\n");
            json.append(indent).append("  \"text\": \"").append(escapeJson(shape.getTextContent())).append("\"");
        }
        
        if (shape.getGeometry() != null) {
            json.append(",\n");
            json.append(indent).append("  \"geometry\": {\n");
            json.append(indent).append("    \"x\": ").append(shape.getGeometry().getX()).append(",\n");
            json.append(indent).append("    \"y\": ").append(shape.getGeometry().getY()).append(",\n");
            json.append(indent).append("    \"width\": ").append(shape.getGeometry().getWidth()).append(",\n");
            json.append(indent).append("    \"height\": ").append(shape.getGeometry().getHeight()).append("\n");
            json.append(indent).append("  }");
        }
        
        json.append("\n").append(indent).append("}");
        return json.toString();
    }
    
    /**
     * Determine if a shape is an editable content area that should be targeted for edit-content operations
     */
    private boolean isEditableContentArea(SlideShape shape) {
        // Placeholders are designed to be edited
        if (shape.getType() == SlideShape.ShapeType.PLACEHOLDER) {
            return true;
        }
        
        // Content shapes with generic names are likely content areas
        String name = shape.getName().toLowerCase();
        if (name.contains("content") || name.contains("body") || name.contains("text")) {
            return true;
        }
        
        // Large text shapes in content area likely meant for editing
        if (shape.hasText() && shape.getGeometry() != null) {
            long area = shape.getGeometry().getWidth() * shape.getGeometry().getHeight();
            // Content area is typically large (> 2 million EMUs)
            return area > 2000000;
        }
        
        return false;
    }
    
    /**
     * Get the purpose description for a shape to guide LLM targeting
     */
    private String getShapePurpose(SlideShape shape) {
        if (shape.getType() == SlideShape.ShapeType.PLACEHOLDER) {
            String name = shape.getName().toLowerCase();
            if (name.contains("title")) {
                return "title placeholder - edit with edit-content operation";
            } else if (name.contains("content") || name.contains("body")) {
                return "content placeholder - edit with edit-content operation";
            }
            return "placeholder - edit with edit-content operation";
        }
        
        if (shape.hasText()) {
            return "text shape - edit with edit-content operation instead of creating new shapes";
        }
        
        return "editable content area";
    }
    
    /**
     * Convert animation to JSON string
     */
    private String animationToJson(AnimationBinding binding, String indent) {
        StringBuilder json = new StringBuilder();
        json.append(indent).append("{\n");
        json.append(indent).append("  \"targetSpid\": ").append(binding.getTargetSpid()).append(",\n");
        json.append(indent).append("  \"animationType\": \"").append(binding.getAnimationType()).append("\",\n");
        json.append(indent).append("  \"transition\": \"").append(binding.getTransition()).append("\"");
        
        // Include shape name for context
        SlideShape targetShape = slideData.getShapeRegistry().getShape(binding.getTargetSpid());
        if (targetShape != null) {
            json.append(",\n");
            json.append(indent).append("  \"targetShapeName\": \"").append(escapeJson(targetShape.getName())).append("\"");
        }
        
        if (binding.getDuration() != null) {
            json.append(",\n");
            json.append(indent).append("  \"duration\": \"").append(escapeJson(binding.getDuration())).append("\"");
        }
        
        if (binding.getDelay() != null) {
            json.append(",\n");
            json.append(indent).append("  \"delay\": \"").append(escapeJson(binding.getDelay())).append("\"");
        }
        
        if (binding.getFilter() != null) {
            json.append(",\n");
            json.append(indent).append("  \"filter\": \"").append(escapeJson(binding.getFilter())).append("\"");
        }
        
        json.append("\n").append(indent).append("}");
        return json.toString();
    }
    
    /**
     * Generate JSON for animation sequence
     */
    private String animationSequenceToJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        
        Map<Integer, List<AnimationInfo>> sequence = analyzeAnimationSequence();
        
        // Add click count
        json.append("    \"totalClicks\": ").append(sequence.size()).append(",\n");
        json.append("    \"clicks\": [\n");
        
        int clickCount = 0;
        for (Map.Entry<Integer, List<AnimationInfo>> entry : sequence.entrySet()) {
            json.append("      {\n");
            json.append("        \"clickNumber\": ").append(entry.getKey()).append(",\n");
            json.append("        \"animations\": [\n");
            
            List<AnimationInfo> anims = entry.getValue();
            for (int i = 0; i < anims.size(); i++) {
                AnimationInfo info = anims.get(i);
                json.append("          {\n");
                json.append("            \"targetSpid\": ").append(info.targetSpid).append(",\n");
                json.append("            \"animationType\": \"").append(info.animationType).append("\",\n");
                json.append("            \"shapeName\": \"").append(escapeJson(info.shapeName)).append("\"\n");
                json.append("          }");
                if (i < anims.size() - 1) json.append(",");
                json.append("\n");
            }
            
            json.append("        ]\n");
            json.append("      }");
            if (++clickCount < sequence.size()) json.append(",");
            json.append("\n");
        }
        
        json.append("    ]\n");
        json.append("  }");
        return json.toString();
    }
    
    /**
     * Analyze animation sequence based on timing tree structure
     */
    private Map<Integer, List<AnimationInfo>> analyzeAnimationSequence() {
        Map<Integer, List<AnimationInfo>> sequence = new TreeMap<>();
        
        TimingTree tree = slideData.getTimingTree();
        if (tree.getRootNode() == null) {
            return sequence;
        }
        
        // Find click triggers in the timing tree
        List<TimingNode> clickNodes = new ArrayList<>();
        findClickTriggersRecursive(tree.getRootNode(), clickNodes);
        
        // If we have animations but no click triggers, assume they're all on click 1
        if (clickNodes.isEmpty() && !slideData.getAnimationBindings().isEmpty()) {
            List<AnimationInfo> click1Animations = new ArrayList<>();
            for (AnimationBinding binding : slideData.getAnimationBindings()) {
                SlideShape shape = slideData.getShapeRegistry().getShape(binding.getTargetSpid());
                String shapeName = shape != null ? shape.getName() : "Unknown";
                click1Animations.add(new AnimationInfo(
                    binding.getTargetSpid(), 
                    binding.getAnimationType().toString(), 
                    shapeName
                ));
            }
            sequence.put(1, click1Animations);
            return sequence;
        }
        
        // Assign animations to clicks based on their order
        // This is a simplified approach - in complex presentations,
        // we'd need to parse the actual timing relationships
        int clickNumber = 1;
        int animsPerClick = Math.max(1, slideData.getAnimationBindings().size() / Math.max(1, clickNodes.size()));
        int animIndex = 0;
        
        for (AnimationBinding binding : slideData.getAnimationBindings()) {
            if (animIndex > 0 && animIndex % animsPerClick == 0 && clickNumber < clickNodes.size()) {
                clickNumber++;
            }
            
            List<AnimationInfo> clickAnims = sequence.computeIfAbsent(clickNumber, k -> new ArrayList<>());
            SlideShape shape = slideData.getShapeRegistry().getShape(binding.getTargetSpid());
            String shapeName = shape != null ? shape.getName() : "Unknown";
            clickAnims.add(new AnimationInfo(
                binding.getTargetSpid(), 
                binding.getAnimationType().toString(), 
                shapeName
            ));
            
            animIndex++;
        }
        
        return sequence;
    }
    
    /**
     * Find click trigger nodes recursively
     */
    private void findClickTriggersRecursive(TimingNode node, List<TimingNode> clickNodes) {
        if (node.isClickTrigger()) {
            clickNodes.add(node);
        }
        for (TimingNode child : node.getChildren()) {
            findClickTriggersRecursive(child, clickNodes);
        }
    }
    
    /**
     * Serialize the full TimingTree hierarchy to JSON
     */
    private String timingTreeToJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        
        TimingTree tree = slideData.getTimingTree();
        if (tree.getRootNode() != null) {
            json.append("    \"rootNode\": ");
            json.append(timingNodeToJson(tree.getRootNode(), "    "));
            json.append(",\n");
        }
        
        // Add timing tree metadata
        json.append("    \"metadata\": {\n");
        json.append("      \"totalNodes\": ").append(countNodesRecursive(tree.getRootNode())).append(",\n");
        json.append("      \"clickTriggers\": ").append(countClickTriggers(tree.getRootNode())).append(",\n");
        json.append("      \"maxDepth\": ").append(calculateMaxDepth(tree.getRootNode(), 0)).append("\n");
        json.append("    }\n");
        
        json.append("  }");
        return json.toString();
    }
    
    /**
     * Serialize a single TimingNode and its children to JSON
     */
    private String timingNodeToJson(TimingNode node, String indent) {
        if (node == null) return "null";
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        
        // Node ID
        json.append(indent).append("  \"nodeId\": ").append(node.getNodeId()).append(",\n");
        
        // Node type
        json.append(indent).append("  \"nodeType\": \"").append(node.getNodeType()).append("\",\n");
        
        // Delay
        if (node.getDelay() != null) {
            json.append(indent).append("  \"delay\": \"").append(escapeJson(node.getDelay())).append("\",\n");
        }
        
        // Duration
        if (node.getDuration() != null) {
            json.append(indent).append("  \"duration\": \"").append(escapeJson(node.getDuration())).append("\",\n");
        }
        
        // Is click trigger
        json.append(indent).append("  \"isClickTrigger\": ").append(node.isClickTrigger()).append(",\n");
        
        // Animation bindings associated with this node
        List<AnimationBinding> nodeAnimations = findAnimationsForNode(node);
        if (!nodeAnimations.isEmpty()) {
            json.append(indent).append("  \"animations\": [\n");
            for (int i = 0; i < nodeAnimations.size(); i++) {
                AnimationBinding anim = nodeAnimations.get(i);
                json.append(indent).append("    {\n");
                json.append(indent).append("      \"targetSpid\": ").append(anim.getTargetSpid()).append(",\n");
                json.append(indent).append("      \"animationType\": \"").append(anim.getAnimationType()).append("\",\n");
                json.append(indent).append("      \"transition\": \"").append(anim.getTransition()).append("\"\n");
                json.append(indent).append("    }");
                if (i < nodeAnimations.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append(indent).append("  ],\n");
        }
        
        // Children
        json.append(indent).append("  \"children\": [\n");
        List<TimingNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            json.append(indent).append("    ");
            json.append(timingNodeToJson(children.get(i), indent + "    "));
            if (i < children.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append(indent).append("  ]\n");
        
        json.append(indent).append("}");
        return json.toString();
    }
    
    /**
     * Find animations associated with a specific timing node
     */
    private List<AnimationBinding> findAnimationsForNode(TimingNode node) {
        // This is a simplified implementation - in reality, we'd need to
        // match animations to nodes based on their XML relationships
        List<AnimationBinding> nodeAnimations = new ArrayList<>();
        
        // For now, we'll associate animations based on timing patterns
        // This would need to be enhanced with proper XML parsing
        for (AnimationBinding anim : slideData.getAnimationBindings()) {
            // Simple heuristic: if delays match, they might be related
            if (node.getDelay() != null && node.getDelay().equals(anim.getDelay())) {
                nodeAnimations.add(anim);
            }
        }
        
        return nodeAnimations;
    }
    
    /**
     * Count total nodes in the timing tree
     */
    private int countNodesRecursive(TimingNode node) {
        if (node == null) return 0;
        int count = 1;
        for (TimingNode child : node.getChildren()) {
            count += countNodesRecursive(child);
        }
        return count;
    }
    
    /**
     * Count click trigger nodes in the timing tree
     */
    private int countClickTriggers(TimingNode node) {
        if (node == null) return 0;
        int count = node.isClickTrigger() ? 1 : 0;
        for (TimingNode child : node.getChildren()) {
            count += countClickTriggers(child);
        }
        return count;
    }
    
    /**
     * Calculate maximum depth of the timing tree
     */
    private int calculateMaxDepth(TimingNode node, int currentDepth) {
        if (node == null) return currentDepth;
        int maxChildDepth = currentDepth;
        for (TimingNode child : node.getChildren()) {
            maxChildDepth = Math.max(maxChildDepth, calculateMaxDepth(child, currentDepth + 1));
        }
        return maxChildDepth;
    }
    
    /**
     * Serialize shape hierarchy and organization to JSON
     */
    private String shapeHierarchyToJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        
        // Group shapes by type
        json.append("    \"byType\": {\n");
        Map<SlideShape.ShapeType, List<SlideShape>> byType = new HashMap<>();
        for (SlideShape.ShapeType type : SlideShape.ShapeType.values()) {
            List<SlideShape> shapesOfType = slideData.getShapeRegistry().getShapesByType(type);
            if (!shapesOfType.isEmpty()) {
                byType.put(type, shapesOfType);
            }
        }
        
        int typeCount = 0;
        for (Map.Entry<SlideShape.ShapeType, List<SlideShape>> entry : byType.entrySet()) {
            json.append("      \"").append(entry.getKey()).append("\": [\n");
            List<SlideShape> shapes = entry.getValue();
            for (int i = 0; i < shapes.size(); i++) {
                SlideShape shape = shapes.get(i);
                json.append("        {\n");
                json.append("          \"spid\": ").append(shape.getSpid()).append(",\n");
                json.append("          \"name\": \"").append(escapeJson(shape.getName())).append("\",\n");
                json.append("          \"hasText\": ").append(shape.hasText());
                if (shape.hasText()) {
                    json.append(",\n          \"text\": \"").append(escapeJson(truncateText(shape.getTextContent(), 50))).append("\"");
                }
                json.append("\n        }");
                if (i < shapes.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("      ]");
            if (++typeCount < byType.size()) json.append(",");
            json.append("\n");
        }
        json.append("    },\n");
        
        // Group shapes by position
        json.append("    \"byPosition\": {\n");
        Map<String, List<SlideShape>> byPosition = new HashMap<>();
        for (SlideShape shape : slideData.getShapeRegistry().getAllShapes()) {
            if (shape.getGeometry() != null) {
                String position = categorizeShapePosition(shape.getGeometry());
                byPosition.computeIfAbsent(position, k -> new ArrayList<>()).add(shape);
            }
        }
        
        int posCount = 0;
        for (Map.Entry<String, List<SlideShape>> entry : byPosition.entrySet()) {
            json.append("      \"").append(entry.getKey()).append("\": [");
            List<SlideShape> shapes = entry.getValue();
            for (int i = 0; i < shapes.size(); i++) {
                json.append(shapes.get(i).getSpid());
                if (i < shapes.size() - 1) json.append(", ");
            }
            json.append("]");
            if (++posCount < byPosition.size()) json.append(",");
            json.append("\n");
        }
        json.append("    },\n");
        
        // Logical groupings
        json.append("    \"logicalGroups\": [\n");
        List<LogicalGroup> groups = identifyLogicalGroupsForSlide();
        for (int i = 0; i < groups.size(); i++) {
            LogicalGroup group = groups.get(i);
            json.append("      {\n");
            json.append("        \"groupId\": \"").append(group.groupId).append("\",\n");
            json.append("        \"description\": \"").append(group.description).append("\",\n");
            json.append("        \"shapes\": [");
            for (int j = 0; j < group.spids.size(); j++) {
                json.append(group.spids.get(j));
                if (j < group.spids.size() - 1) json.append(", ");
            }
            json.append("]\n");
            json.append("      }");
            if (i < groups.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("    ]\n");
        
        json.append("  }");
        return json.toString();
    }
    
    /**
     * Categorize a shape's position on the slide
     */
    private String categorizeShapePosition(ShapeGeometry geometry) {
        // Convert EMUs to approximate percentages for positioning
        long slideWidth = 9144000;  // 10 inches in EMUs
        long slideHeight = 6858000; // 7.5 inches in EMUs
        
        double xPercent = (double) geometry.getX() / slideWidth;
        double yPercent = (double) geometry.getY() / slideHeight;
        
        String horizontal = xPercent < 0.33 ? "left" : (xPercent < 0.67 ? "center" : "right");
        String vertical = yPercent < 0.33 ? "top" : (yPercent < 0.67 ? "middle" : "bottom");
        
        return vertical + "-" + horizontal;
    }
    
    /**
     * Identify logical groupings for this slide
     */
    private List<LogicalGroup> identifyLogicalGroupsForSlide() {
        List<LogicalGroup> groups = new ArrayList<>();
        List<SlideShape> allShapes = slideData.getShapeRegistry().getAllShapes();
        
        // Title group
        SlideShape titleShape = findTitleShape(allShapes);
        if (titleShape != null) {
            List<Integer> titleGroupSpids = new ArrayList<>();
            titleGroupSpids.add(titleShape.getSpid());
            
            // Look for subtitle
            SlideShape subtitleShape = findSubtitleShape(allShapes, titleShape);
            if (subtitleShape != null) {
                titleGroupSpids.add(subtitleShape.getSpid());
            }
            
            groups.add(new LogicalGroup("title-group", "Title and subtitle elements", titleGroupSpids));
        }
        
        // Content group (non-title text)
        List<Integer> contentSpids = allShapes.stream()
            .filter(s -> s.hasText() && !isLikelyTitle(s))
            .map(SlideShape::getSpid)
            .toList();
        if (!contentSpids.isEmpty()) {
            groups.add(new LogicalGroup("content-group", "Main content shapes", contentSpids));
        }
        
        // Visual group (images and non-text shapes)
        List<Integer> visualSpids = allShapes.stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.PICTURE || 
                        (s.getType() == SlideShape.ShapeType.RECTANGLE && !s.hasText()))
            .map(SlideShape::getSpid)
            .toList();
        if (!visualSpids.isEmpty()) {
            groups.add(new LogicalGroup("visual-group", "Visual and decorative elements", visualSpids));
        }
        
        return groups;
    }
    
    /**
     * Find the most likely title shape
     */
    private SlideShape findTitleShape(List<SlideShape> shapes) {
        return shapes.stream()
            .filter(this::isLikelyTitle)
            .min((a, b) -> {
                if (a.getGeometry() != null && b.getGeometry() != null) {
                    return Long.compare(a.getGeometry().getY(), b.getGeometry().getY());
                }
                return 0;
            })
            .orElse(null);
    }
    
    /**
     * Find the most likely subtitle shape
     */
    private SlideShape findSubtitleShape(List<SlideShape> shapes, SlideShape titleShape) {
        if (titleShape == null || titleShape.getGeometry() == null) return null;
        
        return shapes.stream()
            .filter(s -> s != titleShape && s.hasText() && s.getGeometry() != null)
            .filter(s -> s.getGeometry().getY() > titleShape.getGeometry().getY())
            .filter(s -> Math.abs(s.getGeometry().getX() - titleShape.getGeometry().getX()) < 1000000)
            .min((a, b) -> Long.compare(a.getGeometry().getY(), b.getGeometry().getY()))
            .orElse(null);
    }
    
    /**
     * Heuristic to identify title-like shapes
     */
    private boolean isLikelyTitle(SlideShape shape) {
        if (!shape.hasText()) return false;
        
        String name = shape.getName();
        if (name != null) {
            String lowerName = name.toLowerCase();
            if (lowerName.contains("title") || lowerName.contains("header")) {
                return true;
            }
        }
        
        String text = shape.getTextContent();
        if (text != null) {
            String lowerText = text.toLowerCase();
            if (lowerText.contains("click to add title") || 
                (lowerText.contains("title") && text.length() < 50)) {
                return true;
            }
        }
        
        if (shape.getGeometry() != null) {
            return shape.getGeometry().getY() < 2000000; // Top ~2.2 inches
        }
        
        return false;
    }
    
    /**
     * Helper class for logical groups
     */
    private static class LogicalGroup {
        final String groupId;
        final String description;
        final List<Integer> spids;
        
        LogicalGroup(String groupId, String description, List<Integer> spids) {
            this.groupId = groupId;
            this.description = description;
            this.spids = spids;
        }
    }
    
    /**
     * Describe the timing structure in natural language
     */
    private void describeTimingStructure(TimingNode node, StringBuilder description, int depth) {
        if (node == null) return;
        
        String indent = "  ".repeat(depth);
        
        if (node.isClickTrigger()) {
            description.append(String.format("%s- Click trigger (node %s)\n", indent, node.getNodeId()));
            if (node.getDelay() != null) {
                description.append(String.format("%s  Delay: %s\n", indent, node.getDelay()));
            }
            if (node.getDuration() != null) {
                description.append(String.format("%s  Duration: %s\n", indent, node.getDuration()));
            }
            
            // List animations for this click
            List<AnimationBinding> nodeAnimations = findAnimationsForNode(node);
            if (!nodeAnimations.isEmpty()) {
                description.append(String.format("%s  Animations: %d\n", indent, nodeAnimations.size()));
                for (AnimationBinding anim : nodeAnimations) {
                    SlideShape shape = slideData.getShapeRegistry().getShape(anim.getTargetSpid());
                    String shapeName = shape != null ? shape.getName() : "Unknown";
                    description.append(String.format("%s    - %s on %s (SPID %d)\n", 
                        indent, anim.getAnimationType(), shapeName, anim.getTargetSpid()));
                }
            }
        } else if (depth == 0) {
            description.append(String.format("%s- Root timing node (node %s, type: %s)\n", 
                indent, node.getNodeId(), node.getNodeType()));
        } else {
            description.append(String.format("%s- Timing node %s (type: %s)\n", 
                indent, node.getNodeId(), node.getNodeType()));
            if (node.getDelay() != null && !node.getDelay().equals("0")) {
                description.append(String.format("%s  Delay: %s\n", indent, node.getDelay()));
            }
        }
        
        // Recursively describe children
        for (TimingNode child : node.getChildren()) {
            describeTimingStructure(child, description, depth + 1);
        }
    }
    
    /**
     * Helper class to store animation info
     */
    private static class AnimationInfo {
        final int targetSpid;
        final String animationType;
        final String shapeName;
        
        AnimationInfo(int targetSpid, String animationType, String shapeName) {
            this.targetSpid = targetSpid;
            this.animationType = animationType;
            this.shapeName = shapeName;
        }
    }
    
    /**
     * Escape JSON string
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * Truncate text for display
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    // Getters
    public int getSlideNumber() { return slideNumber; }
    public ParsedSlideData getSlideData() { return slideData; }
}