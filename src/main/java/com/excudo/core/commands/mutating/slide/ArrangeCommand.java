package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.geometry.ArrangeEngine;
import com.excudo.core.geometry.ShapeSelector;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.results.ExecutionResult;
import java.util.List;
import java.util.ArrayList;

public class ArrangeCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<String> OPERATION = Parameter.ofString("operation")
        .description("Arrange operation")
        .validValues("align-left", "align-right", "align-top", "align-bottom",
            "align-center-h", "align-center-v", "distribute-h", "distribute-v",
            "match-width", "match-height", "match-size", "center-on-slide", "snap-to-grid")
        .required().build();
    static final Parameter<String> TARGETS = Parameter.ofString("targets")
        .description("Shape selector: SPIDs (2,3,4), all, type:rectangle, text:*, name:Title*")
        .required().build();
    static final Parameter<Integer> ANCHOR = Parameter.ofInt("anchor")
        .spid().description("Anchor shape SPID for alignment/match reference").required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Arrange shapes on a slide (align, distribute, match, center, snap)")
        .llmEnabled(true)
        .llmDescription("Arrange shapes: align, distribute, match, center, snap.")
        .parameter(SLIDE).parameter(OPERATION).parameter(TARGETS).parameter(ANCHOR)
        .example("arrange 1 align-left 2,3,4,5")
        .example("arrange 1 distribute-h type:rectangle")
        .example("arrange 1 match-width all --anchor 3")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ArrangeCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ArrangeCommand(p.get(SLIDE), p.get(OPERATION), p.get(TARGETS),
            p.opt(ANCHOR).orElse(null), ctx.orchestrator());
    }


    private final int slideNumber;
    private final String operation;    // e.g. "align-left", "distribute-h"
    private final String targets;      // selector string e.g. "2,3,4,5" or "all" or "type:rectangle"
    private final Integer anchorSpid;  // optional anchor shape SPID
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private List<ArrangeEngine.ArrangeResult> results = null;

    public ArrangeCommand(int slideNumber, String operation, String targets,
                          Integer anchorSpid, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        if (operation == null || operation.isEmpty()) throw new IllegalArgumentException("Operation cannot be null/empty");
        if (targets == null || targets.isEmpty()) throw new IllegalArgumentException("Targets cannot be null/empty");
        this.slideNumber = slideNumber;
        this.operation = operation;
        this.targets = targets;
        this.anchorSpid = anchorSpid;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) throw new CommandExecutionException(getDescription(), "execute", "Already executed");

        // 1. Get shape registry
        ExecutionResult<ShapeRegistry> registryResult = orchestrator.getShapeRegistry(slideNumber);
        if (!registryResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to get shape registry: " + registryResult.getMessage());
        }
        ShapeRegistry registry = registryResult.getData().orElse(null);
        if (registry == null) {
            throw new CommandExecutionException(getDescription(), "execute", "Shape registry is empty");
        }

        // 2. Resolve targets via ShapeSelector
        List<SlideShape> selectedShapes = ShapeSelector.resolve(targets, registry);

        // 3. Compute new geometries via ArrangeEngine
        results = ArrangeEngine.execute(operation, selectedShapes, anchorSpid);

        // 4. Apply all changes
        for (ArrangeEngine.ArrangeResult result : results) {
            ExecutionResult<Void> updateResult = orchestrator.updateShapeGeometry(
                slideNumber, result.spid(), result.updated());
            if (!updateResult.isSuccess()) {
                throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to update shape " + result.spid() + ": " + updateResult.getMessage());
            }
        }

        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) throw new CommandExecutionException(getDescription(), UndoCommand.NAME, "Not executed");
        if (!canUndo()) throw new CommandExecutionException(getDescription(), UndoCommand.NAME, "Cannot undo");

        // Restore all original geometries (reverse order for safety)
        for (int i = results.size() - 1; i >= 0; i--) {
            ArrangeEngine.ArrangeResult result = results.get(i);
            ExecutionResult<Void> restoreResult = orchestrator.updateShapeGeometry(
                slideNumber, result.spid(), result.original());
            if (!restoreResult.isSuccess()) {
                throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                    "Failed to restore shape " + result.spid() + ": " + restoreResult.getMessage());
            }
        }

        executed = false;
    }

    @Override
    public boolean canUndo() {
        return executed && results != null && !results.isEmpty();
    }

    @Override
    public String getDescription() {
        String desc = "Arrange " + operation + " on slide " + slideNumber + " targets=" + targets;
        if (anchorSpid != null) desc += " anchor=" + anchorSpid;
        return desc;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public String getOperation() { return operation; }
    public List<ArrangeEngine.ArrangeResult> getResults() { return results; }
}
