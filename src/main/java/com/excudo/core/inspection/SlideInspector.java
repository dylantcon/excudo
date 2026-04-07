package com.excudo.core.inspection;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.TimingTree;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.results.ExecutionResult;
import com.excudo.xml.parsers.SlideXMLParser;
import com.excudo.console.utils.ConsoleCommandValidator;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for slide inspection operations.
 * Centralizes repetitive slide file parsing and data extraction logic
 * to eliminate DRY violations in AbstractConsoleEngine.
 */
public class SlideInspector {

    /**
     * Result class containing parsed slide data or error information
     */
    public static class SlideInspectionResult {
        private final boolean success;
        private final ParsedSlideData slideData;
        private final String errorMessage;

        private SlideInspectionResult(boolean success, ParsedSlideData slideData, String errorMessage) {
            this.success = success;
            this.slideData = slideData;
            this.errorMessage = errorMessage;
        }

        public static SlideInspectionResult success(ParsedSlideData slideData) {
            return new SlideInspectionResult(true, slideData, null);
        }

        public static SlideInspectionResult failure(String errorMessage) {
            return new SlideInspectionResult(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public ParsedSlideData getSlideData() {
            return slideData;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Parse slide data from orchestrator for a given slide number.
     * Uses the orchestrator's getSlideData API (interface method).
     */
    public static SlideInspectionResult parseSlideData(PPTXOrchestrator orchestrator, int slideNumber) {
        try {
            ExecutionResult<ParsedSlideData> result = orchestrator.getSlideData(slideNumber);
            if (result.isSuccess() && result.getData().isPresent()) {
                return SlideInspectionResult.success(result.getData().get());
            }
            return SlideInspectionResult.failure(
                result.getMessage() != null ? result.getMessage() : "Slide " + slideNumber + " not found");
        } catch (Exception e) {
            return SlideInspectionResult.failure("Failed to get slide data: " + e.getMessage());
        }
    }

    /**
     * Parse slide data from slide file
     */
    public static SlideInspectionResult parseSlideData(File slideFile) {
        try {
            var parser = new SlideXMLParser();
            var slideData = parser.parseSlide(slideFile);
            return SlideInspectionResult.success(slideData);
        } catch (Exception e) {
            return SlideInspectionResult.failure("Failed to parse slide data: " + e.getMessage());
        }
    }

    /**
     * Parse slide data with document parsing (alternative method)
     */
    public static SlideInspectionResult parseSlideDataWithDocument(File slideFile) {
        try {
            var parser = new SlideXMLParser();
            var slideData = parser.parseSlide(parser.parseSlideDocument(slideFile));
            return SlideInspectionResult.success(slideData);
        } catch (Exception e) {
            return SlideInspectionResult.failure("Failed to parse slide data: " + e.getMessage());
        }
    }

    /**
     * Get all shapes from a slide
     */
    public static List<SlideShape> getSlideShapes(PPTXOrchestrator orchestrator, int slideNumber) {
        SlideInspectionResult result = parseSlideData(orchestrator, slideNumber);
        if (result.isSuccess()) {
            return result.getSlideData().getShapeRegistry().getAllShapes();
        }
        return List.of();
    }

    /**
     * Get a specific shape by SPID from a slide
     */
    public static Optional<SlideShape> getSlideShape(PPTXOrchestrator orchestrator, int slideNumber, int spid) {
        SlideInspectionResult result = parseSlideData(orchestrator, slideNumber);
        if (result.isSuccess()) {
            SlideShape shape = result.getSlideData().getShapeRegistry().getShape(spid);
            return Optional.ofNullable(shape);
        }
        return Optional.empty();
    }

    /**
     * Get all animation bindings from a slide
     */
    public static List<AnimationBinding> getSlideAnimations(PPTXOrchestrator orchestrator, int slideNumber) {
        SlideInspectionResult result = parseSlideData(orchestrator, slideNumber);
        if (result.isSuccess()) {
            return result.getSlideData().getAnimationBindings();
        }
        return List.of();
    }

    /**
     * Get timing tree from a slide
     */
    public static Optional<TimingTree> getSlideTimingTree(PPTXOrchestrator orchestrator, int slideNumber) {
        SlideInspectionResult result = parseSlideData(orchestrator, slideNumber);
        if (result.isSuccess()) {
            TimingTree timingTree = result.getSlideData().getTimingTree();
            return Optional.ofNullable(timingTree);
        }
        return Optional.empty();
    }

    /**
     * Validate that a slide exists and can be parsed
     */
    public static ConsoleCommandValidator.ValidationResult validateSlideExists(PPTXOrchestrator orchestrator, int slideNumber) {
        var slideMetadata = orchestrator.getSlideMetadata(slideNumber);
        if (slideMetadata.isEmpty()) {
            return ConsoleCommandValidator.ValidationResult.failure("Slide " + slideNumber + " not found");
        }

        SlideInspectionResult result = parseSlideData(orchestrator, slideNumber);
        if (!result.isSuccess()) {
            return ConsoleCommandValidator.ValidationResult.failure(result.getErrorMessage());
        }

        return ConsoleCommandValidator.ValidationResult.success();
    }

    /**
     * Validate that a shape exists on a slide
     */
    public static ConsoleCommandValidator.ValidationResult validateShapeExists(PPTXOrchestrator orchestrator,
                                                                              int slideNumber, int spid) {
        ConsoleCommandValidator.ValidationResult slideValidation = validateSlideExists(orchestrator, slideNumber);
        if (!slideValidation.isValid()) {
            return slideValidation;
        }

        Optional<SlideShape> shape = getSlideShape(orchestrator, slideNumber, spid);
        if (shape.isEmpty()) {
            return ConsoleCommandValidator.ValidationResult.failure("SPID " + spid + " not found on slide " + slideNumber);
        }

        return ConsoleCommandValidator.ValidationResult.success();
    }
}
