package com.excudo.core.animations;

import com.excudo.core.model.AnimationType;
import com.excudo.core.animations.AnimationWriter;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
// Concrete factory imports moved to registerDefaultFactories() method to avoid circular dependencies
import java.util.*;

/**
 * Registry for managing AnimationWriter instances and routing animation creation requests
 * to the appropriate factory based on animation type.
 * 
 * Follows the same pattern as ShapeFactoryRegistry for consistency.
 * Provides a centralized point for animation factory management and lookup.
 *
 * Based on timing dump analysis, different animation types require different OOXML
 * element patterns, making factory-based creation essential for PowerPoint compatibility.
 */
public class AnimationFactoryRegistry {

    private static final ComponentLogger logger = Logger.getLogger(AnimationFactoryRegistry.class);

    private final Map<AnimationType, AnimationWriter> factoryMap;
    private final List<AnimationWriter> registeredFactories;
    
    /**
     * Create a registry with default factories.
     */
    public AnimationFactoryRegistry() {
        this.factoryMap = new HashMap<>();
        this.registeredFactories = new ArrayList<>();
        
        // Register default animation factories
        registerDefaultFactories();
    }
    
    /**
     * Register default animation factories for common PowerPoint animations.
     * Uses reflection to avoid circular dependencies during compilation.
     */
    private void registerDefaultFactories() {
        // Use reflection to avoid circular dependency during compilation
        try {
            // Register animation factories
            Class<?> appearClass = Class.forName("com.excudo.xml.writers.animations.concrete.AppearAnimationFactory");
            registerFactory((AnimationWriter) appearClass.getDeclaredConstructor().newInstance());
            
            Class<?> fadeClass = Class.forName("com.excudo.xml.writers.animations.concrete.FadeAnimationFactory");
            registerFactory((AnimationWriter) fadeClass.getDeclaredConstructor().newInstance());
            
            Class<?> wipeClass = Class.forName("com.excudo.xml.writers.animations.concrete.WipeAnimationFactory");
            registerFactory((AnimationWriter) wipeClass.getDeclaredConstructor().newInstance());
            
            Class<?> zoomClass = Class.forName("com.excudo.xml.writers.animations.concrete.ZoomAnimationFactory");
            registerFactory((AnimationWriter) zoomClass.getDeclaredConstructor().newInstance());
            
            Class<?> flyClass = Class.forName("com.excudo.xml.writers.animations.concrete.FlyAnimationFactory");
            registerFactory((AnimationWriter) flyClass.getDeclaredConstructor().newInstance());

            Class<?> filterClass = Class.forName("com.excudo.xml.writers.animations.concrete.FilterBasedAnimationFactory");
            registerFactory((AnimationWriter) filterClass.getDeclaredConstructor().newInstance());

            Class<?> rotationClass = Class.forName("com.excudo.xml.writers.animations.RotationAnimationFactory");
            registerFactory((AnimationWriter) rotationClass.getDeclaredConstructor().newInstance());

            Class<?> scaleClass = Class.forName("com.excudo.xml.writers.animations.ScaleAnimationFactory");
            registerFactory((AnimationWriter) scaleClass.getDeclaredConstructor().newInstance());

            Class<?> colorClass = Class.forName("com.excudo.xml.writers.animations.ColorAnimationFactory");
            registerFactory((AnimationWriter) colorClass.getDeclaredConstructor().newInstance());

            Class<?> emphasisClass = Class.forName("com.excudo.xml.writers.animations.EmphasisEffectFactory");
            registerFactory((AnimationWriter) emphasisClass.getDeclaredConstructor().newInstance());

            Class<?> compositeClass = Class.forName("com.excudo.xml.writers.animations.CompositeEntranceFactory");
            registerFactory((AnimationWriter) compositeClass.getDeclaredConstructor().newInstance());

            Class<?> motionClass = Class.forName("com.excudo.xml.writers.animations.MotionPathAnimationFactory");
            registerFactory((AnimationWriter) motionClass.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            // If reflection fails, the factories aren't available yet (compilation phase)
            logger.warn("Could not load default animation factories: {}", e.getMessage());
        }
    }
    
    /**
     * Register an animation writer/factory.
     * Maps each supported animation type to this factory.
     * 
     * @param factory The factory to register
     */
    public void registerFactory(AnimationWriter factory) {
        registeredFactories.add(factory);
        
        // Map each supported animation type to this factory
        for (AnimationType animationType : factory.getSupportedAnimationTypes()) {
            factoryMap.put(animationType, factory);
        }
    }
    
    /**
     * Get factory for specific animation type.
     * 
     * @param animationType The animation type
     * @return The factory that can create this animation type, or null if none found
     */
    public AnimationWriter getFactory(AnimationType animationType) {
        AnimationWriter factory = factoryMap.get(animationType);

        // If no specific factory found, try to find one that can handle it
        if (factory == null) {
            for (AnimationWriter registeredFactory : registeredFactories) {
                if (registeredFactory.supportsAnimationType(animationType)) {
                    // Cache the result for future lookups
                    factoryMap.put(animationType, registeredFactory);
                    factory = registeredFactory;
                    break;
                }
            }
        }

        return factory;
    }
    
    /**
     * Check if registry can handle the given animation type.
     * 
     * @param animationType The animation type to check
     * @return true if a factory can create this animation type
     */
    public boolean canCreateAnimation(AnimationType animationType) {
        return getFactory(animationType) != null;
    }
    
    /**
     * Get all registered factories.
     * 
     * @return List of registered factories (defensive copy)
     */
    public List<AnimationWriter> getAllFactories() {
        return new ArrayList<>(registeredFactories);
    }
    
    /**
     * Get all supported animation types across all factories.
     * 
     * @return Set of supported animation types
     */
    public Set<AnimationType> getSupportedAnimationTypes() {
        Set<AnimationType> supportedTypes = new HashSet<>();
        
        for (AnimationWriter factory : registeredFactories) {
            supportedTypes.addAll(Arrays.asList(factory.getSupportedAnimationTypes()));
        }
        
        return supportedTypes;
    }
    
    /**
     * Get default animation type for fallback scenarios.
     * Returns FADE as the most reliable animation type.
     * 
     * @return Default animation type
     */
    public AnimationType getDefaultAnimationType() {
        return AnimationType.FADE;
    }
    
    /**
     * Get factory for default animation type.
     * Guaranteed to return a valid factory if default factories are registered.
     * 
     * @return Factory for default animation type
     */
    public AnimationWriter getDefaultFactory() {
        AnimationWriter factory = getFactory(getDefaultAnimationType());
        if (factory == null) {
            throw new IllegalStateException("No factory available for default animation type: " + getDefaultAnimationType());
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
    
    // ========== DEBUGGING AND VALIDATION ==========
    
    /**
     * Get factory by OOXML pattern for debugging and validation.
     * 
     * @param pattern The OOXML pattern to search for (e.g., "p:animEffect + p:set")
     * @return Factory that generates this pattern, or null if none found
     */
    public AnimationWriter getFactoryByPattern(String pattern) {
        for (AnimationWriter factory : registeredFactories) {
            if (factory.getOoxmlPattern().equals(pattern)) {
                return factory;
            }
        }
        return null;
    }
    
    /**
     * Get all OOXML patterns supported by registered factories.
     * Useful for debugging and validation against timing dump analysis.
     * 
     * @return Set of OOXML patterns
     */
    public Set<String> getSupportedOoxmlPatterns() {
        Set<String> patterns = new HashSet<>();
        
        for (AnimationWriter factory : registeredFactories) {
            patterns.add(factory.getOoxmlPattern());
        }
        
        return patterns;
    }
    
    /**
     * Validate registry against timing dump analysis.
     * Ensures all discovered animation patterns have corresponding factories.
     * 
     * @return Validation report with missing patterns
     */
    public String validateAgainstTimingDumps() {
        StringBuilder report = new StringBuilder();
        report.append("Animation Factory Registry Validation Report\n");
        report.append("==========================================\n");
        report.append("Registered Factories: ").append(registeredFactories.size()).append("\n");
        report.append("Supported Animation Types: ").append(getSupportedAnimationTypes().size()).append("\n");
        report.append("Supported OOXML Patterns: ").append(getSupportedOoxmlPatterns().size()).append("\n\n");
        
        // List all supported patterns
        report.append("Supported Patterns:\n");
        for (String pattern : getSupportedOoxmlPatterns()) {
            report.append("  - ").append(pattern).append("\n");
        }
        
        // TODO: Add validation against known timing dump patterns
        // This would require loading the timing dump analysis results
        
        return report.toString();
    }
    
    /**
     * Get registry statistics for debugging.
     * 
     * @return Human-readable statistics string
     */
    public String getStatistics() {
        return String.format("AnimationFactoryRegistry: %d factories, %d animation types supported, %d OOXML patterns", 
                           registeredFactories.size(), 
                           getSupportedAnimationTypes().size(),
                           getSupportedOoxmlPatterns().size());
    }
}