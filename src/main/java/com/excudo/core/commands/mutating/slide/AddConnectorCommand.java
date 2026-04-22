package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for adding connector shapes (p:cxnSp) to slides.
 *
 * Connectors in OOXML use p:cxnSp elements (not p:sp) and can optionally
 * bind to connection points on other shapes via start/end SPID and index.
 */
public class AddConnectorCommand implements Command {

    private final int slideNumber;
    private final String connectorType; // line, straight, elbow, curved
    private final ShapeGeometry geometry;
    private final String headEnd; // none, triangle, arrow, etc.
    private final String tailEnd;
    private final String lineColor; // hex or scheme
    private final String lineStyle; // null/solid/dash
    private final Integer startSpid; // connection start shape
    private final Integer startIdx;  // connection point index
    private final Integer endSpid;   // connection end shape
    private final Integer endIdx;
    private final String customPath; // for freeform connectors
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private Integer createdSpid = null;

    public AddConnectorCommand(int slideNumber, String connectorType, ShapeGeometry geometry,
                               String headEnd, String tailEnd, String lineColor, String lineStyle,
                               Integer startSpid, Integer startIdx, Integer endSpid, Integer endIdx,
                               String customPath, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        if (geometry == null) throw new IllegalArgumentException("Geometry cannot be null");
        this.slideNumber = slideNumber;
        this.connectorType = connectorType != null ? connectorType : "line";
        this.geometry = geometry;
        this.headEnd = headEnd;
        this.tailEnd = tailEnd;
        this.lineColor = lineColor;
        this.lineStyle = lineStyle;
        this.startSpid = startSpid;
        this.startIdx = startIdx;
        this.endSpid = endSpid;
        this.endIdx = endIdx;
        this.customPath = customPath;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) throw new CommandExecutionException(getDescription(), "execute", "Already executed");
        try {
            ExecutionResult<Integer> result = orchestrator.addConnector(slideNumber, connectorType, geometry,
                headEnd, tailEnd, lineColor, lineStyle, startSpid, startIdx, endSpid, endIdx, customPath);
            if (result.isSuccess()) {
                createdSpid = result.getData().orElse(null);
                executed = true;
            } else {
                throw new CommandExecutionException(getDescription(), "execute", "Failed: " + result.getMessage());
            }
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", e.getMessage(), e);
        }
    }

    @Override
    public void undo() {
        if (!executed || !canUndo()) throw new CommandExecutionException(getDescription(), "undo", "Cannot undo");
        try {
            orchestrator.removeShape(slideNumber, createdSpid);
            executed = false;
            createdSpid = null;
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "undo", e.getMessage(), e);
        }
    }

    @Override
    public boolean canUndo() { return executed && createdSpid != null; }

    @Override
    public String getDescription() {
        return String.format("Add %s connector on slide %d at (%d,%d)", connectorType, slideNumber, geometry.getX(), geometry.getY());
    }

    @Override
    public boolean isExecuted() { return executed; }

    public Integer getCreatedSpid() { return createdSpid; }
}
