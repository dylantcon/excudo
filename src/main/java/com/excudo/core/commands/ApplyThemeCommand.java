package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.themes.ThemeLoader;
import com.excudo.core.themes.LayoutDefinition;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.ThemeManager;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.OOXMLAttributeOrder;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.xml.writers.themes.ThemeXMLGenerator;
import org.w3c.dom.Document;

import java.io.File;
import java.util.List;

/**
 * GoF Command for applying a bundled theme to the current presentation.
 *
 * Execution flow:
 * 1. Look up ThemeDefinition from ThemeLoader
 * 2. Generate all XML files via ThemeXMLGenerator
 * 3. Write to extracted PPTX directory (theme, master, layouts, relationships)
 * 4. Update [Content_Types].xml if new parts added
 * 5. Reload ThemeManager to pick up new theme
 */
public class ApplyThemeCommand implements Command {

    private static final ComponentLogger logger = Logger.console();

    private final String themeId;
    private final PPTXOrchestrator orchestrator;
    private final CommandDisplay display;
    private boolean executed = false;

    public ApplyThemeCommand(String themeId, PPTXOrchestrator orchestrator, CommandDisplay display) {
        if (themeId == null || themeId.isBlank()) {
            throw new IllegalArgumentException("Theme ID must not be empty. Available: " + ThemeLoader.getAvailableIds());
        }
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        this.themeId = themeId.toLowerCase();
        this.orchestrator = orchestrator;
        // Display is optional -- null-safe via displayMessage helper
        this.display = display;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        try {
            if (!ThemeLoader.exists(themeId)) {
                throw new CommandExecutionException(getDescription(), "execute",
                        "Unknown theme: '" + themeId + "'. Available: " + ThemeLoader.getAvailableIds());
            }

            ThemeDefinition theme = ThemeLoader.get(themeId);
            java.util.Optional<com.excudo.core.orchestration.OrchestrationContext> ctxOpt = orchestrator.getContext();
            if (ctxOpt.isEmpty() || ctxOpt.get().getDocument() == null) {
                throw new CommandExecutionException(getDescription(), "execute",
                        "No presentation loaded. Use 'load' first.");
            }
            com.excudo.core.model.PPTXDocument pptxDocForTheme = ctxOpt.get().getDocument();
            File extractedDir = pptxDocForTheme.getExtractedDir();
            if (extractedDir == null) {
                throw new CommandExecutionException(getDescription(), "execute",
                        "Theme application requires an extracted directory. Not yet supported for in-memory-only presentations.");
            }

            ThemeXMLGenerator generator = new ThemeXMLGenerator();
            List<LayoutDefinition> layouts = theme.getLayouts();
            int masterNumber = 1;

            // Ensure directories exist
            ensureDirectoryExists(new File(extractedDir, "ppt/theme"));
            ensureDirectoryExists(new File(extractedDir, "ppt/slideMasters"));
            ensureDirectoryExists(new File(extractedDir, "ppt/slideMasters/_rels"));
            ensureDirectoryExists(new File(extractedDir, "ppt/slideLayouts"));
            ensureDirectoryExists(new File(extractedDir, "ppt/slideLayouts/_rels"));

            // 1. Write theme XML
            Document themeDoc = generator.generateThemeXML(theme);
            File themeFile = new File(extractedDir, "ppt/theme/theme" + masterNumber + ".xml");
            OOXMLAttributeOrder.serialize(themeDoc, themeFile);
            logger.info("Wrote theme: " + themeFile.getName());

            // 2. Write slide master XML
            Document masterDoc = generator.generateSlideMasterXML(theme);
            File masterFile = new File(extractedDir, "ppt/slideMasters/slideMaster" + masterNumber + ".xml");
            OOXMLAttributeOrder.serialize(masterDoc, masterFile);
            logger.info("Wrote slide master: " + masterFile.getName());

            // 3. Write slide master relationships
            Document masterRels = generator.generateSlideMasterRels(theme, masterNumber);
            File masterRelsFile = new File(extractedDir, "ppt/slideMasters/_rels/slideMaster" + masterNumber + ".xml.rels");
            OOXMLAttributeOrder.serialize(masterRels, masterRelsFile);

            // 4. Write all slide layouts and their relationships
            for (int i = 0; i < layouts.size(); i++) {
                LayoutDefinition layout = layouts.get(i);
                int layoutNum = i + 1;

                Document layoutDoc = generator.generateSlideLayoutXML(theme, layout);
                File layoutFile = new File(extractedDir, "ppt/slideLayouts/slideLayout" + layoutNum + ".xml");
                OOXMLAttributeOrder.serialize(layoutDoc, layoutFile);

                Document layoutRels = generator.generateSlideLayoutRels(masterNumber);
                File layoutRelsFile = new File(extractedDir, "ppt/slideLayouts/_rels/slideLayout" + layoutNum + ".xml.rels");
                OOXMLAttributeOrder.serialize(layoutRels, layoutRelsFile);
            }
            logger.info("Wrote " + layouts.size() + " slide layouts");

            // 5. Update [Content_Types].xml
            updateContentTypes(extractedDir, layouts.size());

            // 6. Reload ThemeManager
            ThemeManager.initialize(pptxDocForTheme);
            logger.info("Reloaded theme manager with new theme");

            executed = true;
            displayMsg("Applied theme '" + theme.getDisplayName() + "' successfully");
            displayMsg("  Colors: " + theme.getColorScheme().size() + " scheme colors");
            displayMsg("  Fonts: " + theme.getMajorFont() + " (headings), " + theme.getMinorFont() + " (body)");
            displayMsg("  Layouts: " + layouts.size() + " standard layouts");

        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to apply theme: " + e.getMessage(), e);
        }
    }

    private void updateContentTypes(File extractedDir, int layoutCount) throws Exception {
        File contentTypesFile = new File(extractedDir, "[Content_Types].xml");
        if (!contentTypesFile.exists()) {
            logger.warn("[Content_Types].xml not found -- skipping update");
            return;
        }

        Document doc = XMLFactoryProvider.parseDocument(contentTypesFile);
        org.w3c.dom.Element root = doc.getDocumentElement();

        // Collect existing overrides
        java.util.Set<String> existingParts = new java.util.HashSet<>();
        org.w3c.dom.NodeList overrides = root.getElementsByTagName("Override");
        for (int i = 0; i < overrides.getLength(); i++) {
            org.w3c.dom.Element override = (org.w3c.dom.Element) overrides.item(i);
            existingParts.add(override.getAttribute("PartName"));
        }

        String contentTypeNS = root.getNamespaceURI();

        // Ensure theme override exists
        addOverrideIfMissing(doc, root, existingParts, "/ppt/theme/theme1.xml",
                "application/vnd.openxmlformats-officedocument.theme+xml", contentTypeNS);

        // Ensure slide master override exists
        addOverrideIfMissing(doc, root, existingParts, "/ppt/slideMasters/slideMaster1.xml",
                "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml", contentTypeNS);

        // Ensure all layout overrides exist
        for (int i = 1; i <= layoutCount; i++) {
            addOverrideIfMissing(doc, root, existingParts, "/ppt/slideLayouts/slideLayout" + i + ".xml",
                    "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml", contentTypeNS);
        }

        OOXMLAttributeOrder.serialize(doc, contentTypesFile);
    }

    private void addOverrideIfMissing(Document doc, org.w3c.dom.Element root,
                                       java.util.Set<String> existingParts,
                                       String partName, String contentType, String ns) {
        if (!existingParts.contains(partName)) {
            org.w3c.dom.Element override = ns != null
                    ? doc.createElementNS(ns, "Override")
                    : doc.createElement("Override");
            override.setAttribute("PartName", partName);
            override.setAttribute("ContentType", contentType);
            root.appendChild(override);
            logger.info("Added [Content_Types] override: " + partName + " (was missing -- added via theme application)");
        }
    }

    private void displayMsg(String message) {
        if (display != null) {
            display.displayMessage(message);
        }
    }

    private void ensureDirectoryExists(File dir) {
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo",
                "Undo not supported for apply-theme (requires XML snapshot)");
    }

    @Override
    public boolean canUndo() {
        return false;
    }

    @Override
    public String getDescription() {
        return "Apply theme '" + themeId + "' to presentation";
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public String getThemeId() {
        return themeId;
    }
}
