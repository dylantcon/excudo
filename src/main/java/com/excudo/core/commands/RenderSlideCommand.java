package com.excudo.core.commands;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.ThemeLoader;

import java.io.File;

/**
 * Renders a slide to a PNG image file.
 * Uses a SlideRenderFunction to avoid compile-time dependency on the view layer.
 */
public class RenderSlideCommand implements Command {

    /**
     * Functional interface for slide rendering, implemented by the view layer.
     */
    @FunctionalInterface
    public interface SlideRenderFunction {
        void render(PPTXDocument doc, int slideNumber, File outputFile,
                    int width, int height, ThemeDefinition theme,
                    java.util.Map<String, String> clrMap,
                    String backgroundColorHex,
                    java.util.Map<String, com.excudo.core.themes.TextLevelStyle[]> masterStyles) throws Exception;
    }

    private final PPTXOrchestrator orchestrator;
    private final int slideNumber;
    private final String outputPath;
    private final int width;
    private final int height;
    private final SlideRenderFunction renderFunction;
    private boolean executed = false;

    public RenderSlideCommand(PPTXOrchestrator orchestrator,
                              int slideNumber, String outputPath, int width, int height,
                              SlideRenderFunction renderFunction) {
        this.orchestrator = orchestrator;
        this.slideNumber = slideNumber;
        this.outputPath = outputPath;
        this.width = width;
        this.height = height;
        this.renderFunction = renderFunction;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Already executed");
        }

        try {
            var context = orchestrator.getContext()
                .orElseThrow(() -> new IllegalStateException("No presentation loaded"));
            PPTXDocument doc = context.getDocument();

            // Resolve theme
            ThemeDefinition theme = null;
            String themeId = (String) context.getContextData().get("themeId");
            if (themeId != null) {
                try { theme = ThemeLoader.get(themeId); } catch (Exception ignored) {}
            }

            java.util.Map<String, String> clrMap = orchestrator.getClrMap();
            String bgHex = orchestrator.getBackgroundColorHex(slideNumber);
            var masterStyles = orchestrator.getMasterStyles();
            File output = new File(outputPath);
            renderFunction.render(doc, slideNumber, output, width, height, theme, clrMap, bgHex, masterStyles);
            executed = true;
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to render slide: " + cause.getClass().getSimpleName() + ": " + cause.getMessage(), e);
        }
    }

    @Override
    public void undo() {}

    @Override
    public boolean canUndo() { return false; }

    @Override
    public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return String.format("Render slide %d to %s (%dx%d)", slideNumber, outputPath, width, height);
    }
}
