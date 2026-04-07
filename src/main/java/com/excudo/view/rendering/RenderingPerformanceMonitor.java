package com.excudo.view.rendering;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance monitoring system for the rendering engine.
 * Tracks rendering times, memory usage, cache performance, and bottlenecks.
 */
public class RenderingPerformanceMonitor {
    
    // ========== PERFORMANCE COUNTERS ==========
    
    private final AtomicLong totalRenders = new AtomicLong(0);
    private final AtomicLong totalRenderingTime = new AtomicLong(0);
    private final AtomicLong totalShapesRendered = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    // ========== TIMING TRACKING ==========
    
    private final Map<String, Long> renderStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> stageStartTimes = new ConcurrentHashMap<>();
    private final Map<RenderingStage, AtomicLong> stageTotalTimes = new ConcurrentHashMap<>();
    private final Queue<RenderingEvent> recentEvents = new ArrayDeque<>();
    
    // ========== PERFORMANCE HISTORY ==========
    
    private final int MAX_HISTORY_SIZE = 1000;
    private final List<PerformanceSnapshot> performanceHistory = new ArrayList<>();
    private final Map<String, StagePerformance> stagePerformance = new ConcurrentHashMap<>();
    
    // ========== BOTTLENECK DETECTION ==========
    
    private final Map<String, BottleneckInfo> bottlenecks = new ConcurrentHashMap<>();
    private double slowRenderThreshold = 1000.0; // 1 second
    private double slowStageThreshold = 500.0;   // 0.5 seconds
    
    public enum RenderingStage {
        BACKGROUND("Background"),
        SHAPES("Shapes"),
        TEXT("Text"),
        EFFECTS("Effects"),
        OVERLAYS("Overlays"),
        DEBUG("Debug");
        
        private final String displayName;
        
        RenderingStage(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    public RenderingPerformanceMonitor() {
        // Initialize stage performance tracking
        for (RenderingStage stage : RenderingStage.values()) {
            stageTotalTimes.put(stage, new AtomicLong(0));
            stagePerformance.put(stage.name(), new StagePerformance(stage));
        }
    }
    
    // ========== RENDER LIFECYCLE TRACKING ==========
    
    /**
     * Start tracking a render operation
     */
    public void startRender(String renderKey) {
        long startTime = System.nanoTime();
        renderStartTimes.put(renderKey, startTime);
        totalRenders.incrementAndGet();
        
        logEvent(new RenderingEvent(renderKey, RenderingEventType.RENDER_START, startTime));
    }
    
    /**
     * End tracking a render operation
     */
    public void endRender() {
        endRender(getCurrentRenderKey());
    }
    
    /**
     * End tracking a specific render operation
     */
    public void endRender(String renderKey) {
        Long startTime = renderStartTimes.remove(renderKey);
        if (startTime != null) {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            long durationMs = duration / 1_000_000;
            
            totalRenderingTime.addAndGet(duration);
            
            // Check for slow renders
            if (durationMs > slowRenderThreshold) {
                recordBottleneck("slow_render", renderKey, durationMs);
            }
            
            // Create performance snapshot
            PerformanceSnapshot snapshot = new PerformanceSnapshot(
                renderKey, startTime, endTime, duration
            );
            addPerformanceSnapshot(snapshot);
            
            logEvent(new RenderingEvent(renderKey, RenderingEventType.RENDER_END, endTime, duration));
        }
    }
    
    /**
     * Start tracking a rendering stage
     */
    public void startStage(RenderingStage stage) {
        String stageKey = getCurrentRenderKey() + "_" + stage.name();
        long startTime = System.nanoTime();
        stageStartTimes.put(stageKey, startTime);
        
        StagePerformance stagePerf = stagePerformance.get(stage.name());
        if (stagePerf != null) {
            stagePerf.incrementCount();
        }
        
        logEvent(new RenderingEvent(stageKey, RenderingEventType.STAGE_START, startTime));
    }
    
    /**
     * End tracking a rendering stage
     */
    public void endStage(RenderingStage stage) {
        String stageKey = getCurrentRenderKey() + "_" + stage.name();
        Long startTime = stageStartTimes.remove(stageKey);
        
        if (startTime != null) {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            long durationMs = duration / 1_000_000;
            
            // Update stage totals
            stageTotalTimes.get(stage).addAndGet(duration);
            
            // Update stage performance
            StagePerformance stagePerf = stagePerformance.get(stage.name());
            if (stagePerf != null) {
                stagePerf.addDuration(duration);
            }
            
            // Check for slow stages
            if (durationMs > slowStageThreshold) {
                recordBottleneck("slow_stage", stage.name(), durationMs);
            }
            
            logEvent(new RenderingEvent(stageKey, RenderingEventType.STAGE_END, endTime, duration));
        }
    }
    
    // ========== CACHE PERFORMANCE TRACKING ==========
    
    /**
     * Record cache hit
     */
    public void recordCacheHit(String cacheType) {
        cacheHits.incrementAndGet();
        logEvent(new RenderingEvent(cacheType, RenderingEventType.CACHE_HIT, System.nanoTime()));
    }
    
    /**
     * Record cache miss
     */
    public void recordCacheMiss(String cacheType) {
        cacheMisses.incrementAndGet();
        logEvent(new RenderingEvent(cacheType, RenderingEventType.CACHE_MISS, System.nanoTime()));
    }
    
    /**
     * Record shape rendering
     */
    public void recordShapeRendered() {
        totalShapesRendered.incrementAndGet();
    }
    
    /**
     * Record multiple shapes rendered
     */
    public void recordShapesRendered(int count) {
        totalShapesRendered.addAndGet(count);
    }
    
    // ========== PERFORMANCE ANALYSIS ==========
    
    /**
     * Get current performance statistics
     */
    public PerformanceStatistics getCurrentStatistics() {
        long totalRenders = this.totalRenders.get();
        long totalTime = this.totalRenderingTime.get();
        long totalShapes = this.totalShapesRendered.get();
        long hits = this.cacheHits.get();
        long misses = this.cacheMisses.get();
        
        return new PerformanceStatistics(
            totalRenders,
            totalTime,
            totalShapes,
            hits,
            misses,
            calculateAverageRenderTime(),
            calculateCacheHitRatio(),
            getCurrentThroughput()
        );
    }
    
    /**
     * Calculate average render time in milliseconds
     */
    public double calculateAverageRenderTime() {
        long renders = totalRenders.get();
        if (renders == 0) return 0.0;
        
        return (double) totalRenderingTime.get() / renders / 1_000_000; // Convert to ms
    }
    
    /**
     * Calculate cache hit ratio
     */
    public double calculateCacheHitRatio() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        
        return total > 0 ? (double) hits / total : 0.0;
    }
    
    /**
     * Calculate current rendering throughput (shapes per second)
     */
    public double getCurrentThroughput() {
        if (performanceHistory.isEmpty()) return 0.0;
        
        // Calculate throughput over last 10 seconds
        long now = System.nanoTime();
        long tenSecondsAgo = now - (10L * 1_000_000_000L);
        
        long shapesInWindow = 0;
        for (int i = performanceHistory.size() - 1; i >= 0; i--) {
            PerformanceSnapshot snapshot = performanceHistory.get(i);
            if (snapshot.getStartTime() < tenSecondsAgo) break;
            shapesInWindow += snapshot.getShapeCount();
        }
        
        return shapesInWindow / 10.0; // shapes per second
    }
    
    /**
     * Get stage performance breakdown
     */
    public Map<RenderingStage, StagePerformanceInfo> getStagePerformance() {
        Map<RenderingStage, StagePerformanceInfo> breakdown = new HashMap<>();
        
        for (RenderingStage stage : RenderingStage.values()) {
            StagePerformance perf = stagePerformance.get(stage.name());
            long totalTime = stageTotalTimes.get(stage).get();
            
            if (perf != null) {
                breakdown.put(stage, new StagePerformanceInfo(
                    stage,
                    perf.getExecutionCount(),
                    totalTime,
                    perf.getAverageDuration(),
                    perf.getMinDuration(),
                    perf.getMaxDuration()
                ));
            }
        }
        
        return breakdown;
    }
    
    /**
     * Get performance bottlenecks
     */
    public List<BottleneckInfo> getBottlenecks() {
        return new ArrayList<>(bottlenecks.values());
    }
    
    /**
     * Get recent performance events
     */
    public List<RenderingEvent> getRecentEvents(int maxEvents) {
        synchronized (recentEvents) {
            List<RenderingEvent> events = new ArrayList<>(recentEvents);
            return events.subList(Math.max(0, events.size() - maxEvents), events.size());
        }
    }
    
    // ========== PERFORMANCE OPTIMIZATION RECOMMENDATIONS ==========
    
    /**
     * Generate performance recommendations
     */
    public List<PerformanceRecommendation> generateRecommendations() {
        List<PerformanceRecommendation> recommendations = new ArrayList<>();
        
        // Check cache hit ratio
        double cacheRatio = calculateCacheHitRatio();
        if (cacheRatio < 0.8) {
            recommendations.add(new PerformanceRecommendation(
                RecommendationType.CACHING,
                "Cache hit ratio is low (" + String.format("%.1f%%", cacheRatio * 100) + "). Consider increasing cache size.",
                RecommendationPriority.HIGH
            ));
        }
        
        // Check average render time
        double avgRenderTime = calculateAverageRenderTime();
        if (avgRenderTime > slowRenderThreshold) {
            recommendations.add(new PerformanceRecommendation(
                RecommendationType.PERFORMANCE,
                "Average render time is high (" + String.format("%.1fms", avgRenderTime) + "). Consider optimizing geometry processing.",
                RecommendationPriority.MEDIUM
            ));
        }
        
        // Check stage performance
        Map<RenderingStage, StagePerformanceInfo> stagePerf = getStagePerformance();
        for (Map.Entry<RenderingStage, StagePerformanceInfo> entry : stagePerf.entrySet()) {
            StagePerformanceInfo info = entry.getValue();
            if (info.getAverageDuration() / 1_000_000 > slowStageThreshold) {
                recommendations.add(new PerformanceRecommendation(
                    RecommendationType.STAGE_OPTIMIZATION,
                    "Stage '" + entry.getKey().getDisplayName() + "' is slow (avg: " + 
                    String.format("%.1fms", info.getAverageDuration() / 1_000_000) + ").",
                    RecommendationPriority.MEDIUM
                ));
            }
        }
        
        return recommendations;
    }
    
    // ========== UTILITY METHODS ==========
    
    private String getCurrentRenderKey() {
        // Return most recent render key or generate default
        return "render_" + System.nanoTime();
    }
    
    private void addPerformanceSnapshot(PerformanceSnapshot snapshot) {
        synchronized (performanceHistory) {
            performanceHistory.add(snapshot);
            
            // Maintain history size limit
            while (performanceHistory.size() > MAX_HISTORY_SIZE) {
                performanceHistory.remove(0);
            }
        }
    }
    
    private void recordBottleneck(String type, String context, long durationMs) {
        String key = type + "_" + context;
        BottleneckInfo existing = bottlenecks.get(key);
        
        if (existing == null) {
            bottlenecks.put(key, new BottleneckInfo(type, context, durationMs, 1));
        } else {
            existing.incrementOccurrences();
            existing.updateMaxDuration(durationMs);
        }
    }
    
    private void logEvent(RenderingEvent event) {
        synchronized (recentEvents) {
            recentEvents.offer(event);
            
            // Maintain queue size
            while (recentEvents.size() > 100) {
                recentEvents.poll();
            }
        }
    }
    
    // ========== CONFIGURATION ==========
    
    public void setSlowRenderThreshold(double thresholdMs) {
        this.slowRenderThreshold = thresholdMs;
    }
    
    public void setSlowStageThreshold(double thresholdMs) {
        this.slowStageThreshold = thresholdMs;
    }
    
    // ========== RESET AND CLEANUP ==========
    
    /**
     * Reset all performance counters
     */
    public void reset() {
        totalRenders.set(0);
        totalRenderingTime.set(0);
        totalShapesRendered.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        
        renderStartTimes.clear();
        stageStartTimes.clear();
        
        for (AtomicLong counter : stageTotalTimes.values()) {
            counter.set(0);
        }
        
        for (StagePerformance perf : stagePerformance.values()) {
            perf.reset();
        }
        
        synchronized (performanceHistory) {
            performanceHistory.clear();
        }
        
        bottlenecks.clear();
        
        synchronized (recentEvents) {
            recentEvents.clear();
        }
    }
    
    // ========== NESTED CLASSES ==========
    
    public static class PerformanceSnapshot {
        private final String renderKey;
        private final long startTime;
        private final long endTime;
        private final long duration;
        private int shapeCount = 0;
        
        public PerformanceSnapshot(String renderKey, long startTime, long endTime, long duration) {
            this.renderKey = renderKey;
            this.startTime = startTime;
            this.endTime = endTime;
            this.duration = duration;
        }
        
        public String getRenderKey() { return renderKey; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public long getDuration() { return duration; }
        public int getShapeCount() { return shapeCount; }
        public void setShapeCount(int count) { this.shapeCount = count; }
    }
    
    public static class StagePerformance {
        private final RenderingStage stage;
        private long executionCount = 0;
        private long totalDuration = 0;
        private long minDuration = Long.MAX_VALUE;
        private long maxDuration = 0;
        
        public StagePerformance(RenderingStage stage) {
            this.stage = stage;
        }
        
        public synchronized void incrementCount() {
            executionCount++;
        }
        
        public synchronized void addDuration(long duration) {
            totalDuration += duration;
            minDuration = Math.min(minDuration, duration);
            maxDuration = Math.max(maxDuration, duration);
        }
        
        public synchronized void reset() {
            executionCount = 0;
            totalDuration = 0;
            minDuration = Long.MAX_VALUE;
            maxDuration = 0;
        }
        
        public long getExecutionCount() { return executionCount; }
        public long getTotalDuration() { return totalDuration; }
        public long getMinDuration() { return minDuration == Long.MAX_VALUE ? 0 : minDuration; }
        public long getMaxDuration() { return maxDuration; }
        
        public double getAverageDuration() {
            return executionCount > 0 ? (double) totalDuration / executionCount : 0.0;
        }
    }
    
    public static class StagePerformanceInfo {
        private final RenderingStage stage;
        private final long executionCount;
        private final long totalDuration;
        private final double averageDuration;
        private final long minDuration;
        private final long maxDuration;
        
        public StagePerformanceInfo(RenderingStage stage, long executionCount, long totalDuration,
                                  double averageDuration, long minDuration, long maxDuration) {
            this.stage = stage;
            this.executionCount = executionCount;
            this.totalDuration = totalDuration;
            this.averageDuration = averageDuration;
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
        }
        
        public RenderingStage getStage() { return stage; }
        public long getExecutionCount() { return executionCount; }
        public long getTotalDuration() { return totalDuration; }
        public double getAverageDuration() { return averageDuration; }
        public long getMinDuration() { return minDuration; }
        public long getMaxDuration() { return maxDuration; }
    }
    
    public static class PerformanceStatistics {
        private final long totalRenders;
        private final long totalRenderingTime;
        private final long totalShapesRendered;
        private final long cacheHits;
        private final long cacheMisses;
        private final double averageRenderTime;
        private final double cacheHitRatio;
        private final double currentThroughput;
        
        public PerformanceStatistics(long totalRenders, long totalRenderingTime, long totalShapesRendered,
                                   long cacheHits, long cacheMisses, double averageRenderTime,
                                   double cacheHitRatio, double currentThroughput) {
            this.totalRenders = totalRenders;
            this.totalRenderingTime = totalRenderingTime;
            this.totalShapesRendered = totalShapesRendered;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.averageRenderTime = averageRenderTime;
            this.cacheHitRatio = cacheHitRatio;
            this.currentThroughput = currentThroughput;
        }
        
        public long getTotalRenders() { return totalRenders; }
        public long getTotalRenderingTime() { return totalRenderingTime; }
        public long getTotalShapesRendered() { return totalShapesRendered; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public double getAverageRenderTime() { return averageRenderTime; }
        public double getCacheHitRatio() { return cacheHitRatio; }
        public double getCurrentThroughput() { return currentThroughput; }
    }
    
    public static class BottleneckInfo {
        private final String type;
        private final String context;
        private long maxDuration;
        private int occurrences;
        
        public BottleneckInfo(String type, String context, long maxDuration, int occurrences) {
            this.type = type;
            this.context = context;
            this.maxDuration = maxDuration;
            this.occurrences = occurrences;
        }
        
        public void incrementOccurrences() { occurrences++; }
        public void updateMaxDuration(long duration) { maxDuration = Math.max(maxDuration, duration); }
        
        public String getType() { return type; }
        public String getContext() { return context; }
        public long getMaxDuration() { return maxDuration; }
        public int getOccurrences() { return occurrences; }
    }
    
    public static class RenderingEvent {
        private final String key;
        private final RenderingEventType type;
        private final long timestamp;
        private final long duration;
        
        public RenderingEvent(String key, RenderingEventType type, long timestamp) {
            this(key, type, timestamp, 0);
        }
        
        public RenderingEvent(String key, RenderingEventType type, long timestamp, long duration) {
            this.key = key;
            this.type = type;
            this.timestamp = timestamp;
            this.duration = duration;
        }
        
        public String getKey() { return key; }
        public RenderingEventType getType() { return type; }
        public long getTimestamp() { return timestamp; }
        public long getDuration() { return duration; }
    }
    
    public enum RenderingEventType {
        RENDER_START,
        RENDER_END,
        STAGE_START,
        STAGE_END,
        CACHE_HIT,
        CACHE_MISS
    }
    
    public static class PerformanceRecommendation {
        private final RecommendationType type;
        private final String description;
        private final RecommendationPriority priority;
        
        public PerformanceRecommendation(RecommendationType type, String description, RecommendationPriority priority) {
            this.type = type;
            this.description = description;
            this.priority = priority;
        }
        
        public RecommendationType getType() { return type; }
        public String getDescription() { return description; }
        public RecommendationPriority getPriority() { return priority; }
    }
    
    public enum RecommendationType {
        CACHING,
        PERFORMANCE,
        STAGE_OPTIMIZATION,
        MEMORY
    }
    
    public enum RecommendationPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}