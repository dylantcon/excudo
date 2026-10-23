package com.excudo.core.commands.mutating.theme;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.themes.ThemeLoader;
import com.excudo.core.themes.LayoutDefinition;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.ThemeManager;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.excudo.xml.writers.themes.ThemeXMLGenerator;
import com.excudo.core.model.ContentTypesModel;
import com.excudo.core.model.PPTXDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * GoF Command for applying a bundled theme to the current presentation.
 *
 * Execution flow:
 * 1. Look up ThemeDefinition from ThemeLoader
 * 2. Generate all XML files via ThemeXMLGenerator
 * 3. Replace theme, master, and layout parts in the in-memory PPTXDocument
 *    (works for scaffolded and loaded decks; flush/save persist it)
 * 4. Re-point slides at the new layout of the same type; drop surplus layouts
 * 5. Update [Content_Types].xml and reload ThemeManager
 */
public class ApplyThemeCommand implements Command {

    static final Parameter<String> THEME_ID = Parameter.ofString("themeId")
        .description("Theme ID").llmName("themeId").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Apply a bundled theme to the presentation")
        .llmEnabled(true)
        .parameter(THEME_ID)
        .example("apply-theme minimal")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ApplyThemeCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ApplyThemeCommand(p.get(THEME_ID), ctx.orchestrator(), ctx.requireDisplay());
    }


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

    private static final String LAYOUT_PREFIX = "ppt/slideLayouts/slideLayout";
    private static final String LAYOUT_REL_TYPE_SUFFIX = "/slideLayout";
    private static final String CT_THEME = "application/vnd.openxmlformats-officedocument.theme+xml";
    private static final String CT_MASTER = "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml";
    private static final String CT_LAYOUT = "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml";

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
                        "No presentation loaded. Use 'load' or 'new-presentation' first.");
            }
            PPTXDocument doc = ctxOpt.get().getDocument();

            // The generated master owns slideLayout1..N; with a second master
            // those numbers may belong to it, so refuse rather than clobber.
            int masterCount = 0;
            for (String part : doc.getPartNamesByPrefix("ppt/slideMasters/slideMaster")) {
                if (part.endsWith(".xml")) masterCount++;
            }
            if (masterCount > 1) {
                throw new CommandExecutionException(getDescription(), "execute",
                        "apply-theme supports decks with a single slide master; this deck has " + masterCount + ".");
            }

            // Old layout types, so slides can be re-pointed at the matching new layout.
            Map<Integer, String> oldLayoutTypes = new TreeMap<>();
            for (String part : doc.getPartNamesByPrefix(LAYOUT_PREFIX)) {
                int n = layoutNumber(part);
                if (n > 0) oldLayoutTypes.put(n, layoutType(doc.getXmlPart(part)));
            }

            ThemeXMLGenerator generator = new ThemeXMLGenerator();
            List<LayoutDefinition> layouts = theme.getLayouts();
            int masterNumber = 1;

            doc.putXmlPart("ppt/theme/theme" + masterNumber + ".xml", generator.generateThemeXML(theme));
            doc.putXmlPart("ppt/slideMasters/slideMaster" + masterNumber + ".xml",
                    generator.generateSlideMasterXML(theme));
            doc.putXmlPart("ppt/slideMasters/_rels/slideMaster" + masterNumber + ".xml.rels",
                    generator.generateSlideMasterRels(theme, masterNumber));

            List<String> newLayoutTypes = new ArrayList<>();
            for (int i = 0; i < layouts.size(); i++) {
                int layoutNum = i + 1;
                Document layoutDoc = generator.generateSlideLayoutXML(theme, layouts.get(i));
                newLayoutTypes.add(layoutType(layoutDoc));
                doc.putXmlPart(LAYOUT_PREFIX + layoutNum + ".xml", layoutDoc);
                doc.putXmlPart("ppt/slideLayouts/_rels/slideLayout" + layoutNum + ".xml.rels",
                        generator.generateSlideLayoutRels(masterNumber));
            }

            int remapped = remapSlideLayouts(doc, oldLayoutTypes, newLayoutTypes);

            // Drop old layouts beyond the new theme's count.
            ContentTypesModel contentTypes = doc.getContentTypes();
            for (int n : oldLayoutTypes.keySet()) {
                if (n <= layouts.size()) continue;
                doc.removeXmlPart(LAYOUT_PREFIX + n + ".xml");
                doc.removeXmlPart("ppt/slideLayouts/_rels/slideLayout" + n + ".xml.rels");
                if (contentTypes != null) contentTypes.removeOverride("/" + LAYOUT_PREFIX + n + ".xml");
            }

            if (contentTypes != null) {
                contentTypes.addOverride("/ppt/theme/theme" + masterNumber + ".xml", CT_THEME);
                contentTypes.addOverride("/ppt/slideMasters/slideMaster" + masterNumber + ".xml", CT_MASTER);
                for (int i = 1; i <= layouts.size(); i++) {
                    contentTypes.addOverride("/" + LAYOUT_PREFIX + i + ".xml", CT_LAYOUT);
                }
                doc.markContentTypesDirty();
            }

            ThemeManager.initialize(doc);

            executed = true;
            displayMsg("Applied theme '" + theme.getDisplayName() + "' successfully");
            displayMsg("  Colors: " + theme.getColorScheme().size() + " scheme colors");
            displayMsg("  Fonts: " + theme.getMajorFont() + " (headings), " + theme.getMinorFont() + " (body)");
            displayMsg("  Layouts: " + layouts.size() + " standard layouts");
            if (remapped > 0) {
                displayMsg("  Re-pointed " + remapped + " slide(s) to the matching layout in the new theme");
            }

        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to apply theme: " + e.getMessage(), e);
        }
    }

    /**
     * Point each slide's layout relationship at the new layout of the same
     * type (title, obj, twoObj, ...). Keeps the slot number when its type
     * still matches; falls back to the content layout, then layout 1.
     *
     * @return number of slides whose layout target changed
     */
    private static int remapSlideLayouts(PPTXDocument doc, Map<Integer, String> oldTypes,
                                         List<String> newTypes) {
        int changed = 0;
        for (int slide : doc.getSlideNumbers()) {
            String relsName = "ppt/slides/_rels/slide" + slide + ".xml.rels";
            Document rels = doc.getXmlPart(relsName);
            if (rels == null) continue;
            NodeList relNodes = rels.getElementsByTagNameNS("*", "Relationship");
            boolean dirty = false;
            for (int i = 0; i < relNodes.getLength(); i++) {
                Element rel = (Element) relNodes.item(i);
                if (!rel.getAttribute("Type").endsWith(LAYOUT_REL_TYPE_SUFFIX)) continue;
                String target = rel.getAttribute("Target");
                int oldNum = layoutNumber("ppt/slideLayouts/" + target.substring(target.lastIndexOf('/') + 1));
                if (oldNum <= 0) continue;
                int newNum = pickLayout(oldNum, oldTypes.get(oldNum), newTypes);
                if (newNum != oldNum) {
                    rel.setAttribute("Target", "../slideLayouts/slideLayout" + newNum + ".xml");
                    dirty = true;
                }
            }
            if (dirty) {
                doc.markDirty(relsName);
                changed++;
            }
        }
        return changed;
    }

    static int pickLayout(int oldNum, String oldType, List<String> newTypes) {
        if (oldType != null) {
            if (oldNum <= newTypes.size() && oldType.equals(newTypes.get(oldNum - 1))) return oldNum;
            int sameType = newTypes.indexOf(oldType);
            if (sameType >= 0) return sameType + 1;
        }
        if (oldNum <= newTypes.size()) return oldNum;
        int content = newTypes.indexOf("obj");
        return content >= 0 ? content + 1 : 1;
    }

    /** Layout number from "ppt/slideLayouts/slideLayoutN.xml", or -1. */
    private static int layoutNumber(String partName) {
        if (!partName.startsWith(LAYOUT_PREFIX) || !partName.endsWith(".xml")) return -1;
        try {
            return Integer.parseInt(partName.substring(LAYOUT_PREFIX.length(), partName.length() - 4));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The p:sldLayout/@type value; OOXML's default when absent is "cust". */
    private static String layoutType(Document layoutDoc) {
        if (layoutDoc == null) return null;
        String type = layoutDoc.getDocumentElement().getAttribute("type");
        return type.isEmpty() ? "cust" : type;
    }

    private void displayMsg(String message) {
        if (display != null) {
            display.displayMessage(message);
        }
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
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
