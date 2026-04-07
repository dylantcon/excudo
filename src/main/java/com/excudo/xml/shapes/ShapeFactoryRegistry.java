package com.excudo.xml.shapes;

import com.excudo.core.model.SlideShape;
import java.util.*;

/**
 * Registry for managing ShapeFactory instances and routing shape creation requests
 * to the appropriate factory based on shape type.
 * 
 * Follows the same pattern as ShapeRendererFactory for consistency.
 * Provides a centralized point for shape factory management and lookup.
 */
public class ShapeFactoryRegistry {
    
    private final Map<SlideShape.ShapeType, ShapeFactory> factoryMap;
    private final List<ShapeFactory> registeredFactories;
    
    /**
     * Create a registry with default factories.
     */
    public ShapeFactoryRegistry() {
        this.factoryMap = new HashMap<>();
        this.registeredFactories = new ArrayList<>();
        
        // Register default shape factories
        registerDefaultFactories();
    }
    
    /**
     * Register default shape factories for common PowerPoint shapes.
     */
    private void registerDefaultFactories() {
        // Register basic shape factory for most common shapes
        registerFactory(new BasicShapeFactory());
        
        // TODO: Register specialized factories as they are implemented
        // registerFactory(new AdjustableShapeFactory()); // for rounded rects, etc.
        // registerFactory(new ArrowShapeFactory()); // if arrows need special handling
    }
    
    /**
     * Register a shape factory.
     * Maps each supported shape type to this factory.
     * 
     * @param factory The factory to register
     */
    public void registerFactory(ShapeFactory factory) {
        registeredFactories.add(factory);
        
        // Map each supported shape type to this factory
        for (SlideShape.ShapeType shapeType : factory.getSupportedShapeTypes()) {
            factoryMap.put(shapeType, factory);
        }
    }
    
    /**
     * Get factory for specific shape type.
     * 
     * @param shapeType The shape type
     * @return The factory that can create this shape type, or null if none found
     */
    public ShapeFactory getFactory(SlideShape.ShapeType shapeType) {
        ShapeFactory factory = factoryMap.get(shapeType);
        
        // If no specific factory found, try to find one that can handle it
        if (factory == null) {
            for (ShapeFactory registeredFactory : registeredFactories) {
                if (registeredFactory.supportsShapeType(shapeType)) {
                    // Cache the result for future lookups
                    factoryMap.put(shapeType, registeredFactory);
                    factory = registeredFactory;
                    break;
                }
            }
        }
        
        return factory;
    }
    
    /**
     * Check if registry can handle the given shape type.
     * 
     * @param shapeType The shape type to check
     * @return true if a factory can create this shape type
     */
    public boolean canCreateShape(SlideShape.ShapeType shapeType) {
        return getFactory(shapeType) != null;
    }
    
    /**
     * Get all registered factories.
     * 
     * @return List of registered factories (defensive copy)
     */
    public List<ShapeFactory> getAllFactories() {
        return new ArrayList<>(registeredFactories);
    }
    
    /**
     * Get all supported shape types across all factories.
     * 
     * @return Set of supported shape types
     */
    public Set<SlideShape.ShapeType> getSupportedShapeTypes() {
        Set<SlideShape.ShapeType> supportedTypes = new HashSet<>();
        
        for (ShapeFactory factory : registeredFactories) {
            supportedTypes.addAll(Arrays.asList(factory.getSupportedShapeTypes()));
        }
        
        return supportedTypes;
    }
    
    /**
     * Get default shape type for fallback scenarios.
     * Returns RECTANGLE as the most basic shape type.
     * 
     * @return Default shape type
     */
    public SlideShape.ShapeType getDefaultShapeType() {
        return SlideShape.ShapeType.RECTANGLE;
    }
    
    /**
     * Get factory for default shape type.
     * Guaranteed to return a valid factory.
     * 
     * @return Factory for default shape type
     */
    public ShapeFactory getDefaultFactory() {
        ShapeFactory factory = getFactory(getDefaultShapeType());
        if (factory == null) {
            throw new IllegalStateException("No factory available for default shape type: " + getDefaultShapeType());
        }
        return factory;
    }
    
    /**
     * Clear all registered factories.
     * Used primarily for testing.
     */
    public void clear() {
        factoryMap.clear();
        registeredFactories.clear();
    }
    
    /**
     * Get registry statistics for debugging.
     * 
     * @return Human-readable statistics string
     */
    public String getStatistics() {
        return String.format("ShapeFactoryRegistry: %d factories, %d shape types supported", 
                           registeredFactories.size(), 
                           getSupportedShapeTypes().size());
    }
}