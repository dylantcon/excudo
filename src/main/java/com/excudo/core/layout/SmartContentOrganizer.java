package com.excudo.core.layout;

import com.excudo.core.model.*;
import java.util.*;

/**
 * Intelligently reorganizes slide content for better visual hierarchy
 */
public class SmartContentOrganizer {
    
    private final SlideLayoutAnalyzer layoutAnalyzer;
    
    public SmartContentOrganizer() {
        this.layoutAnalyzer = new SlideLayoutAnalyzer();
    }
    
    /**
     * Reorganization strategies
     */
    public enum ReorganizationStrategy {
        GRID_LAYOUT("Arrange elements in a grid pattern"),
        HIERARCHICAL("Title at top, content below in hierarchy"),
        BALANCED("Balance elements across left and right"),
        CENTERED("Center all content with proper spacing"),
        FLOW_LAYOUT("Flow elements like text, wrapping as needed"),
        PRESENTATION_ZEN("Minimal, focused layout with lots of whitespace");
        
        private final String description;
        
        ReorganizationStrategy(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * Analyze content and suggest best reorganization strategy
     */
    public ReorganizationStrategy suggestStrategy(ParsedSlideData slideData) {
        ShapeRegistry registry = slideData.getShapeRegistry();
        
        // Count different types of content
        int textBoxes = 0;
        int images = 0;
        int charts = 0;
        boolean hasTitle = false;
        
        for (ShapeData shape : registry.getAllShapes()) {
            if (shape.getShapeName().toLowerCase().contains("title")) {
                hasTitle = true;
            }
            // Count shape types (simplified for example)
            if (shape.getTextContent() != null && !shape.getTextContent().isEmpty()) {
                textBoxes++;
            }
        }
        
        // Suggest strategy based on content
        if (textBoxes > 4) {
            return ReorganizationStrategy.GRID_LAYOUT;
        } else if (hasTitle && textBoxes <= 3) {
            return ReorganizationStrategy.HIERARCHICAL;
        } else if (textBoxes == 2) {
            return ReorganizationStrategy.BALANCED;
        } else {
            return ReorganizationStrategy.CENTERED;
        }
    }
    
    /**
     * Reorganize slide content using specified strategy
     */
    public List<ShapeTransformation> reorganizeContent(ParsedSlideData slideData, 
                                                       ReorganizationStrategy strategy) {
        List<ShapeTransformation> transformations = new ArrayList<>();
        
        switch (strategy) {
            case GRID_LAYOUT:
                transformations = createGridLayout(slideData);
                break;
            case HIERARCHICAL:
                transformations = createHierarchicalLayout(slideData);
                break;
            case BALANCED:
                transformations = createBalancedLayout(slideData);
                break;
            case CENTERED:
                transformations = createCenteredLayout(slideData);
                break;
            case FLOW_LAYOUT:
                transformations = createFlowLayout(slideData);
                break;
            case PRESENTATION_ZEN:
                transformations = createZenLayout(slideData);
                break;
        }
        
        return transformations;
    }
    
    /**
     * Create grid layout transformations
     */
    private List<ShapeTransformation> createGridLayout(ParsedSlideData slideData) {
        List<ShapeTransformation> transformations = new ArrayList<>();
        List<ShapeData> shapes = new ArrayList<>(slideData.getShapeRegistry().getAllShapes());
        
        // Remove title from grid
        shapes.removeIf(s -> s.getShapeName().toLowerCase().contains("title"));
        
        if (shapes.isEmpty()) return transformations;
        
        // Calculate grid dimensions
        int count = shapes.size();
        int cols = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / cols);
        
        // Grid parameters
        long margin = 457200L; // 0.5 inch
        long slideWidth = 9144000L;
        long slideHeight = 6858000L;
        long titleHeight = 1470025L; // Reserve space for title
        
        long cellWidth = (slideWidth - margin * (cols + 1)) / cols;
        long cellHeight = (slideHeight - titleHeight - margin * (rows + 1)) / rows;
        
        // Position shapes in grid
        for (int i = 0; i < shapes.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            
            long x = margin + col * (cellWidth + margin);
            long y = titleHeight + margin + row * (cellHeight + margin);
            
            ShapeData shape = shapes.get(i);
            ShapeGeometry newGeometry = new ShapeGeometry(x, y, cellWidth, cellHeight);
            
            transformations.add(new ShapeTransformation(
                shape.getSpid(),
                shape.getGeometry(),
                newGeometry,
                "Grid position " + (i + 1)
            ));
        }
        
        return transformations;
    }
    
    /**
     * Create hierarchical layout transformations
     */
    private List<ShapeTransformation> createHierarchicalLayout(ParsedSlideData slideData) {
        List<ShapeTransformation> transformations = new ArrayList<>();
        ShapeRegistry registry = slideData.getShapeRegistry();
        
        // Standard positions
        long margin = 457200L;
        long slideWidth = 9144000L;
        long contentWidth = slideWidth - 2 * margin;
        
        // Position title
        ShapeData title = findTitle(registry);
        if (title != null) {
            ShapeGeometry titleGeometry = new ShapeGeometry(
                margin, margin, contentWidth, 1470025L
            );
            transformations.add(new ShapeTransformation(
                title.getSpid(), title.getGeometry(), titleGeometry, "Title position"
            ));
        }
        
        // Position content shapes
        long yPosition = 2000000L; // Start below title
        long contentHeight = 1000000L; // Default height
        
        for (ShapeData shape : registry.getAllShapes()) {
            if (shape != title && shape.getGeometry() != null) {
                ShapeGeometry newGeometry = new ShapeGeometry(
                    margin, yPosition, contentWidth, contentHeight
                );
                transformations.add(new ShapeTransformation(
                    shape.getSpid(), shape.getGeometry(), newGeometry, 
                    "Hierarchical content position"
                ));
                yPosition += contentHeight + margin / 2;
            }
        }
        
        return transformations;
    }
    
    /**
     * Create balanced (two-column) layout
     */
    private List<ShapeTransformation> createBalancedLayout(ParsedSlideData slideData) {
        List<ShapeTransformation> transformations = new ArrayList<>();
        List<ShapeData> shapes = new ArrayList<>(slideData.getShapeRegistry().getAllShapes());
        
        // Remove title
        ShapeData title = findTitle(slideData.getShapeRegistry());
        shapes.remove(title);
        
        if (shapes.size() != 2) return transformations;
        
        long margin = 457200L;
        long slideWidth = 9144000L;
        long columnWidth = (slideWidth - 3 * margin) / 2;
        long contentHeight = 4500000L;
        long yPosition = 2000000L;
        
        // Left column
        ShapeGeometry leftGeometry = new ShapeGeometry(
            margin, yPosition, columnWidth, contentHeight
        );
        transformations.add(new ShapeTransformation(
            shapes.get(0).getSpid(), 
            shapes.get(0).getGeometry(), 
            leftGeometry, 
            "Left column"
        ));
        
        // Right column
        ShapeGeometry rightGeometry = new ShapeGeometry(
            margin * 2 + columnWidth, yPosition, columnWidth, contentHeight
        );
        transformations.add(new ShapeTransformation(
            shapes.get(1).getSpid(), 
            shapes.get(1).getGeometry(), 
            rightGeometry, 
            "Right column"
        ));
        
        return transformations;
    }
    
    /**
     * Create centered layout
     */
    private List<ShapeTransformation> createCenteredLayout(ParsedSlideData slideData) {
        List<ShapeTransformation> transformations = new ArrayList<>();
        List<ShapeData> shapes = new ArrayList<>(slideData.getShapeRegistry().getAllShapes());
        
        long slideWidth = 9144000L;
        long slideHeight = 6858000L;
        long totalHeight = 0;
        long margin = 457200L;
        
        // Calculate total height needed
        for (ShapeData shape : shapes) {
            if (shape.getGeometry() != null) {
                totalHeight += shape.getGeometry().getHeight() + margin;
            }
        }
        totalHeight -= margin; // Remove last margin
        
        // Start position (vertically centered)
        long yPosition = (slideHeight - totalHeight) / 2;
        
        for (ShapeData shape : shapes) {
            if (shape.getGeometry() != null) {
                long width = shape.getGeometry().getWidth();
                long height = shape.getGeometry().getHeight();
                long xPosition = (slideWidth - width) / 2;
                
                ShapeGeometry centeredGeometry = new ShapeGeometry(
                    xPosition, yPosition, width, height
                );
                
                transformations.add(new ShapeTransformation(
                    shape.getSpid(),
                    shape.getGeometry(),
                    centeredGeometry,
                    "Centered position"
                ));
                
                yPosition += height + margin;
            }
        }
        
        return transformations;
    }
    
    /**
     * Create flow layout (like text flow)
     */
    private List<ShapeTransformation> createFlowLayout(ParsedSlideData slideData) {
        // Similar implementation to grid but with dynamic sizing
        return createGridLayout(slideData); // Simplified for now
    }
    
    /**
     * Create zen layout (minimal, lots of whitespace)
     */
    private List<ShapeTransformation> createZenLayout(ParsedSlideData slideData) {
        List<ShapeTransformation> transformations = new ArrayList<>();
        List<ShapeData> shapes = new ArrayList<>(slideData.getShapeRegistry().getAllShapes());
        
        if (shapes.isEmpty()) return transformations;
        
        // Keep only the most important shape (largest text content)
        ShapeData mainShape = shapes.stream()
            .filter(s -> s.getTextContent() != null)
            .max(Comparator.comparingInt(s -> s.getTextContent().length()))
            .orElse(shapes.get(0));
        
        // Center it with generous margins
        long slideWidth = 9144000L;
        long slideHeight = 6858000L;
        long width = slideWidth / 2;
        long height = 2000000L; // Fixed height for zen look
        
        ShapeGeometry zenGeometry = new ShapeGeometry(
            (slideWidth - width) / 2,
            (slideHeight - height) / 2,
            width,
            height
        );
        
        transformations.add(new ShapeTransformation(
            mainShape.getSpid(),
            mainShape.getGeometry(),
            zenGeometry,
            "Zen centered position"
        ));
        
        // Hide other shapes (move off-slide)
        for (ShapeData shape : shapes) {
            if (shape != mainShape) {
                transformations.add(new ShapeTransformation(
                    shape.getSpid(),
                    shape.getGeometry(),
                    new ShapeGeometry(-1000000L, -1000000L, 1L, 1L),
                    "Hidden for zen layout"
                ));
            }
        }
        
        return transformations;
    }
    
    private ShapeData findTitle(ShapeRegistry registry) {
        return registry.getAllShapes().stream()
            .filter(s -> s.getShapeName().toLowerCase().contains("title"))
            .findFirst()
            .orElse(null);
    }
}

class ShapeTransformation {
    private final int spid;
    private final ShapeGeometry oldGeometry;
    private final ShapeGeometry newGeometry;
    private final String description;
    
    public ShapeTransformation(int spid, ShapeGeometry oldGeometry, 
                              ShapeGeometry newGeometry, String description) {
        this.spid = spid;
        this.oldGeometry = oldGeometry;
        this.newGeometry = newGeometry;
        this.description = description;
    }
    
    public int getSpid() { return spid; }
    public ShapeGeometry getOldGeometry() { return oldGeometry; }
    public ShapeGeometry getNewGeometry() { return newGeometry; }
    public String getDescription() { return description; }
}