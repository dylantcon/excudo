package com.excudo.core.llm;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.JsonHelper;
import com.excudo.core.utils.Logger;
import com.google.gson.JsonObject;

/**
 * Bridge between the mermaid-ooxml library and the Excudo orchestrator.
 * Parses mermaid syntax, lays out the diagram, and creates native OOXML shapes
 * with bound connectors via the PPTXOrchestrator.
 *
 * This is the ONLY file in the mermaid pipeline that imports Excudo types.
 */
public class MermaidDiagramTool {

    private static final ComponentLogger logger = Logger.llm();
    private final PPTXOrchestrator orchestrator;

    public MermaidDiagramTool(PPTXOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * @deprecated The orchestration moved into
     * {@link com.excudo.core.commands.mutating.slide.CreateMermaidDiagramCommand}
     * so the compound primitive participates in the GoF Command pipeline
     * (atomic rollback on partial failure + free user-initiated undo via
     * CommandInvoker). The MCP {@code create_diagram} tool now dispatches
     * the Command directly via {@code ToolDispatcher.handleCreateDiagram};
     * this thin adapter stays for any direct callers.
     */
    @Deprecated
    public String createMermaidDiagram(String toolInput) {
        // Thin adapter: parse JSON, build the Command, run it. The
        // orchestration body lives on
        // CreateMermaidDiagramCommand which provides atomic rollback
        // on partial failure + free undo via CommandInvoker.
        try {
            int slideNumber = extractInt(toolInput, "slideNumber");
            String mermaidText = extractString(toolInput, "mermaid");
            if (mermaidText == null || mermaidText.isBlank()) {
                return "Error: 'mermaid' field is required and must contain valid mermaid syntax";
            }
            Long xOrNull = jsonHasKey(toolInput, "x") ? extractLong(toolInput, "x", 0L) : null;
            Long yOrNull = jsonHasKey(toolInput, "y") ? extractLong(toolInput, "y", 0L) : null;
            Long wOrNull = jsonHasKey(toolInput, "width")  ? extractLong(toolInput, "width",  0L) : null;
            Long hOrNull = jsonHasKey(toolInput, "height") ? extractLong(toolInput, "height", 0L) : null;

            com.excudo.core.commands.mutating.slide.CreateMermaidDiagramCommand cmd =
                new com.excudo.core.commands.mutating.slide.CreateMermaidDiagramCommand(
                    slideNumber, mermaidText, xOrNull, yOrNull, wOrNull, hOrNull, orchestrator);
            cmd.execute();
            return cmd.getResultSummary();
        } catch (com.excudo.core.commands.CommandExecutionException e) {
            return "Error creating mermaid diagram: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error creating mermaid diagram", e);
            return "Error creating mermaid diagram: " + e.getMessage();
        }
    }

    private static boolean jsonHasKey(String json, String key) {
        try { return JsonHelper.parseObject(json).has(key); }
        catch (Exception e) { return false; }
    }

    // --- JSON extraction helpers ---

    private int extractInt(String json, String key) {
        try {
            JsonObject obj = JsonHelper.parseObject(json);
            return JsonHelper.getInt(obj, key, 1);
        } catch (Exception e) { return 1; }
    }

    private long extractLong(String json, String key, long defaultValue) {
        try {
            JsonObject obj = JsonHelper.parseObject(json);
            return JsonHelper.getLong(obj, key, defaultValue);
        } catch (Exception e) { return defaultValue; }
    }

    private String extractString(String json, String key) {
        try {
            JsonObject obj = JsonHelper.parseObject(json);
            return JsonHelper.getString(obj, key);
        } catch (Exception e) { return null; }
    }
}
