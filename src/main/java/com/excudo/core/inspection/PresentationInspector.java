package com.excudo.core.inspection;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.LayoutManager;
import com.excudo.core.model.LayoutInfo;
import com.excudo.console.utils.ConsoleCommandValidator;

import java.util.List;
import java.util.Optional;

/**
 * Utility class for presentation-level inspection operations.
 * Centralizes presentation metadata, layout, and high-level presentation
 * information gathering to reduce code duplication in AbstractConsoleEngine.
 */
public class PresentationInspector {

    /**
     * Result class containing layout information or error details
     */
    public static class LayoutInspectionResult {
        private final boolean success;
        private final List<LayoutInfo> layouts;
        private final String errorMessage;

        private LayoutInspectionResult(boolean success, List<LayoutInfo> layouts, String errorMessage) {
            this.success = success;
            this.layouts = layouts;
            this.errorMessage = errorMessage;
        }

        public static LayoutInspectionResult success(List<LayoutInfo> layouts) {
            return new LayoutInspectionResult(true, layouts, null);
        }

        public static LayoutInspectionResult failure(String errorMessage) {
            return new LayoutInspectionResult(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public List<LayoutInfo> getLayouts() {
            return layouts;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Get available layouts from the presentation
     */
    public static LayoutInspectionResult getAvailableLayouts(PPTXOrchestrator orchestrator) {
        try {
            var context = orchestrator.getContext();
            if (context.isEmpty()) {
                return LayoutInspectionResult.failure("No presentation context available");
            }

            var layoutManager = context.get().getLayoutManager();
            if (layoutManager == null) {
                return LayoutInspectionResult.failure("No LayoutManager available for layout inspection");
            }
            var layouts = layoutManager.getAvailableLayouts();

            return LayoutInspectionResult.success(layouts);
        } catch (Exception e) {
            return LayoutInspectionResult.failure("Failed to get layouts: " + e.getMessage());
        }
    }

    /**
     * Get presentation context information for display
     */
    public static String getPresentationContextSummary(PPTXOrchestrator orchestrator) {
        var context = orchestrator.getContext();
        if (context.isEmpty()) {
            return "No presentation loaded";
        }

        var metadata = orchestrator.getPresentationMetadata();
        return String.format("Loaded: %s (%d slides)",
            metadata.getTitle(), metadata.getSlideCount());
    }

    /**
     * Check if presentation is loaded and has valid context
     */
    public static boolean isPresentationLoaded(PPTXOrchestrator orchestrator) {
        return orchestrator != null && !orchestrator.getContext().isEmpty();
    }

    /**
     * Get presentation metadata in a safe way
     */
    public static Optional<com.excudo.core.orchestration.PresentationMetadata>
            getPresentationMetadata(PPTXOrchestrator orchestrator) {
        if (!isPresentationLoaded(orchestrator)) {
            return Optional.empty();
        }

        try {
            return Optional.of(orchestrator.getPresentationMetadata());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Validate that the orchestrator has a valid presentation context
     */
    public static ConsoleCommandValidator.ValidationResult validatePresentationContext(PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            return ConsoleCommandValidator.ValidationResult.failure("No orchestrator available");
        }

        var context = orchestrator.getContext();
        if (context.isEmpty()) {
            return ConsoleCommandValidator.ValidationResult.failure("No presentation context available");
        }

        return ConsoleCommandValidator.ValidationResult.success();
    }
}
