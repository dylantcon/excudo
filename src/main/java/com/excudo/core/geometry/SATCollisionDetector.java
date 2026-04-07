package com.excudo.core.geometry;

import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import java.util.*;

/**
 * Separating Axis Theorem (SAT) collision detector for complex shape overlap detection.
 * 
 * SAT is used to determine if two convex polygons are overlapping by testing if there
 * exists a separating axis between them. If no separating axis is found, the polygons
 * are colliding.
 * 
 * This is essential for complex PowerPoint shapes like stars, arrows, and custom geometry
 * where simple bounding box collision detection is insufficient.
 */
public class SATCollisionDetector {
    
    private final ShapeToPolygonConverter polygonConverter;
    
    public SATCollisionDetector() {
        this.polygonConverter = new ShapeToPolygonConverter();
    }
    
    /**
     * Check if two shapes are colliding using SAT algorithm
     */
    public boolean areShapesColliding(SlideShape shape1, SlideShape shape2) {
        if (shape1 == null || shape2 == null || 
            shape1.getGeometry() == null || shape2.getGeometry() == null) {
            return false;
        }
        
        // For simple rectangular shapes, use optimized bounding box check
        if (!shape1.getType().requiresSATCollision() && !shape2.getType().requiresSATCollision()) {
            return boundingBoxCollision(shape1.getGeometry(), shape2.getGeometry());
        }
        
        // Convert shapes to polygons
        Polygon polygon1 = polygonConverter.convertToPolygon(shape1);
        Polygon polygon2 = polygonConverter.convertToPolygon(shape2);
        
        if (polygon1 == null || polygon2 == null) {
            // Fallback to bounding box if conversion failed
            return boundingBoxCollision(shape1.getGeometry(), shape2.getGeometry());
        }
        
        return satCollisionCheck(polygon1, polygon2);
    }
    
    /**
     * Check collision between shape and a rectangular region (for layout engine)
     */
    public boolean isShapeCollidingWithRegion(SlideShape shape, long x, long y, long width, long height) {
        if (shape == null || shape.getGeometry() == null) {
            return false;
        }
        
        // Create a rectangular "shape" for the region
        ShapeGeometry regionGeometry = new ShapeGeometry(x, y, width, height);
        
        if (!shape.getType().requiresSATCollision()) {
            return boundingBoxCollision(shape.getGeometry(), regionGeometry);
        }
        
        // Convert shape to polygon and region to rectangle polygon
        Polygon shapePolygon = polygonConverter.convertToPolygon(shape);
        Polygon regionPolygon = createRectanglePolygon(x, y, width, height);
        
        if (shapePolygon == null) {
            return boundingBoxCollision(shape.getGeometry(), regionGeometry);
        }
        
        return satCollisionCheck(shapePolygon, regionPolygon);
    }
    
    /**
     * Core SAT collision detection algorithm
     */
    private boolean satCollisionCheck(Polygon polygon1, Polygon polygon2) {
        // Get all axes to test (perpendicular to edges of both polygons)
        List<Vector2D> axes = new ArrayList<>();
        axes.addAll(getPolygonAxes(polygon1));
        axes.addAll(getPolygonAxes(polygon2));
        
        // Test each axis
        for (Vector2D axis : axes) {
            ProjectionInterval proj1 = projectPolygonOnAxis(polygon1, axis);
            ProjectionInterval proj2 = projectPolygonOnAxis(polygon2, axis);
            
            // If projections don't overlap on this axis, shapes are separated
            if (!proj1.overlaps(proj2)) {
                return false; // No collision
            }
        }
        
        return true; // All axes overlap, collision detected
    }
    
    /**
     * Get all axes (edge normals) for a polygon
     */
    private List<Vector2D> getPolygonAxes(Polygon polygon) {
        List<Vector2D> axes = new ArrayList<>();
        List<Vector2D> vertices = polygon.getVertices();
        
        for (int i = 0; i < vertices.size(); i++) {
            Vector2D current = vertices.get(i);
            Vector2D next = vertices.get((i + 1) % vertices.size());
            
            // Get edge vector
            Vector2D edge = next.subtract(current);
            
            // Get perpendicular (normal) to the edge
            Vector2D normal = new Vector2D(-edge.getY(), edge.getX()).normalize();
            axes.add(normal);
        }
        
        return axes;
    }
    
    /**
     * Project a polygon onto an axis and return the interval
     */
    private ProjectionInterval projectPolygonOnAxis(Polygon polygon, Vector2D axis) {
        List<Vector2D> vertices = polygon.getVertices();
        
        double min = vertices.get(0).dot(axis);
        double max = min;
        
        for (int i = 1; i < vertices.size(); i++) {
            double projection = vertices.get(i).dot(axis);
            min = Math.min(min, projection);
            max = Math.max(max, projection);
        }
        
        return new ProjectionInterval(min, max);
    }
    
    /**
     * Simple bounding box collision for rectangular shapes
     */
    private boolean boundingBoxCollision(ShapeGeometry geo1, ShapeGeometry geo2) {
        return !(geo1.getX() + geo1.getWidth() <= geo2.getX() ||
                geo2.getX() + geo2.getWidth() <= geo1.getX() ||
                geo1.getY() + geo1.getHeight() <= geo2.getY() ||
                geo2.getY() + geo2.getHeight() <= geo1.getY());
    }
    
    /**
     * Create a rectangular polygon for region testing
     */
    private Polygon createRectanglePolygon(long x, long y, long width, long height) {
        List<Vector2D> vertices = Arrays.asList(
            new Vector2D(x, y),                    // Top-left
            new Vector2D(x + width, y),            // Top-right
            new Vector2D(x + width, y + height),   // Bottom-right
            new Vector2D(x, y + height)            // Bottom-left
        );
        return new Polygon(vertices);
    }
    
    // ========== INNER CLASSES ==========
    
    /**
     * 2D Vector for geometric calculations
     */
    public static class Vector2D {
        private final double x;
        private final double y;
        
        public Vector2D(double x, double y) {
            this.x = x;
            this.y = y;
        }
        
        public double getX() { return x; }
        public double getY() { return y; }
        
        public Vector2D add(Vector2D other) {
            return new Vector2D(x + other.x, y + other.y);
        }
        
        public Vector2D subtract(Vector2D other) {
            return new Vector2D(x - other.x, y - other.y);
        }
        
        public Vector2D multiply(double scalar) {
            return new Vector2D(x * scalar, y * scalar);
        }
        
        public double dot(Vector2D other) {
            return x * other.x + y * other.y;
        }
        
        public double magnitude() {
            return Math.sqrt(x * x + y * y);
        }
        
        public Vector2D normalize() {
            double mag = magnitude();
            if (mag == 0) return new Vector2D(0, 0);
            return new Vector2D(x / mag, y / mag);
        }
        
        @Override
        public String toString() {
            return String.format("Vector2D(%.2f, %.2f)", x, y);
        }
    }
    
    /**
     * Represents a polygon as a list of vertices
     */
    public static class Polygon {
        private final List<Vector2D> vertices;
        
        public Polygon(List<Vector2D> vertices) {
            this.vertices = new ArrayList<>(vertices);
        }
        
        public List<Vector2D> getVertices() {
            return Collections.unmodifiableList(vertices);
        }
        
        public int getVertexCount() {
            return vertices.size();
        }
        
        /**
         * Get the centroid of the polygon
         */
        public Vector2D getCentroid() {
            double sumX = 0, sumY = 0;
            for (Vector2D vertex : vertices) {
                sumX += vertex.getX();
                sumY += vertex.getY();
            }
            return new Vector2D(sumX / vertices.size(), sumY / vertices.size());
        }
        
        /**
         * Get the bounding box of the polygon
         */
        public BoundingBox getBoundingBox() {
            if (vertices.isEmpty()) {
                return new BoundingBox(0, 0, 0, 0);
            }
            
            double minX = vertices.get(0).getX();
            double maxX = minX;
            double minY = vertices.get(0).getY();
            double maxY = minY;
            
            for (Vector2D vertex : vertices) {
                minX = Math.min(minX, vertex.getX());
                maxX = Math.max(maxX, vertex.getX());
                minY = Math.min(minY, vertex.getY());
                maxY = Math.max(maxY, vertex.getY());
            }
            
            return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
        }
    }
    
    /**
     * Projection interval for SAT algorithm
     */
    private static class ProjectionInterval {
        private final double min;
        private final double max;
        
        public ProjectionInterval(double min, double max) {
            this.min = min;
            this.max = max;
        }
        
        public boolean overlaps(ProjectionInterval other) {
            return !(max <= other.min || other.max <= min);
        }
        
        public double getMin() { return min; }
        public double getMax() { return max; }
    }
    
    /**
     * Bounding box representation
     */
    public static class BoundingBox {
        private final double x, y, width, height;
        
        public BoundingBox(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        
        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        
        public double getRight() { return x + width; }
        public double getBottom() { return y + height; }
    }
}