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
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.xml.writers.themes.ThemeXMLGenerator;
import com.excudo.core.model.ContentTypesModel;
import com.excudo.core.model.PPTXDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Apply a bundled theme to the presentation.
 *
 * <p>Works on the in-memory {@link PPTXDocument} (scaffolded and loaded decks
 * alike). Each targeted slide master is restyled in place: its theme part,
 * master XML, and layout set are replaced by the theme's, and the slides
 * using that master are re-pointed at the new layout of the same type
 * (title, obj, twoObj, ...). By default every master is restyled; with
 * {@code --master N} only slideMasterN. Masters are never added or removed,
 * so presentation-level relationships are untouched.
 *
 * <p>Undoable: the affected parts are snapshotted before the change and
 * restored on undo.
 */
public class ApplyThemeCommand implements Command {

    static final Parameter<String> THEME_ID = Parameter.ofString("themeId")
        .description("Theme ID").llmName("themeId").required().build();
    static final Parameter<Integer> MASTER = Parameter.ofInt("master")
        .description("Only restyle this slide master (number N of slideMasterN); default: all masters")
        .required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Apply a bundled theme to the presentation (all slide masters, or one with --master)")
        .llmEnabled(true)
        .parameter(THEME_ID)
        .parameter(MASTER)
        .example("apply-theme minimal")
        .example("apply-theme corporate --master 2")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ApplyThemeCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ApplyThemeCommand(p.get(THEME_ID), p.opt(MASTER).orElse(null),
            ctx.orchestrator(), ctx.display());
    }

    private static final ComponentLogger logger = Logger.console();

    private static final String CT_THEME = "application/vnd.openxmlformats-officedocument.theme+xml";
    private static final String CT_MASTER = "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml";
    private static final String CT_LAYOUT = "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml";
    private static final String PNS = XMLConstants.PRESENTATION_NS;
    private static final String RNS = XMLConstants.RELATIONSHIPS_NS;
    private static final String PKG_REL = XMLConstants.PACKAGE_RELATIONSHIPS_NS;
    private static final long MIN_LAYOUT_ID = 2147483649L;

    private static final Pattern MASTER_PART = Pattern.compile("^ppt/slideMasters/slideMaster(\\d+)\\.xml$");
    private static final Pattern LAYOUT_PART = Pattern.compile("^ppt/slideLayouts/slideLayout(\\d+)\\.xml$");
    private static final Pattern THEME_PART = Pattern.compile("^ppt/theme/theme(\\d+)\\.xml$");
    private static final Pattern LAYOUT_TARGET = Pattern.compile("slideLayout(\\d+)\\.xml$");

    /** Parts apply-theme may change; snapshotted for undo. */
    private static final String[] SNAPSHOT_PREFIXES = {
        "ppt/theme/", "ppt/slideMasters/", "ppt/slideLayouts/", "ppt/slides/_rels/"
    };

    private final String themeId;
    private final Integer masterNumber;
    private final PPTXOrchestrator orchestrator;
    private final CommandDisplay display;
    private boolean executed = false;

    private PPTXDocument document;
    private Map<String, Document> snapshot;   // part -> copy taken before execute
    private Set<String> partsBefore;          // snapshot-prefix parts that existed before

    public ApplyThemeCommand(String themeId, PPTXOrchestrator orchestrator, CommandDisplay display) {
        this(themeId, null, orchestrator, display);
    }

    /**
     * @param masterNumber restyle only slideMasterN, or null for every master
     */
    public ApplyThemeCommand(String themeId, Integer masterNumber, PPTXOrchestrator orchestrator,
                             CommandDisplay display) {
        if (themeId == null || themeId.isBlank()) {
            throw new IllegalArgumentException("Theme ID must not be empty. Available: " + ThemeLoader.getAvailableIds());
        }
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        this.themeId = themeId.toLowerCase();
        this.masterNumber = masterNumber;
        this.orchestrator = orchestrator;
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
                        "No presentation loaded. Use 'load' or 'new-presentation' first.");
            }
            PPTXDocument doc = ctxOpt.get().getDocument();

            TreeSet<Integer> masters = partNumbers(doc, "ppt/slideMasters/slideMaster", MASTER_PART);
            if (masters.isEmpty()) {
                throw new CommandExecutionException(getDescription(), "execute", "The presentation has no slide master.");
            }
            List<Integer> targets = new ArrayList<>();
            if (masterNumber != null) {
                if (!masters.contains(masterNumber)) {
                    throw new CommandExecutionException(getDescription(), "execute",
                        "No slideMaster" + masterNumber + ". Masters: " + masters);
                }
                targets.add(masterNumber);
            } else {
                targets.addAll(masters);
            }

            takeSnapshot(doc);

            ThemeXMLGenerator generator = new ThemeXMLGenerator();
            int remapped = 0;
            int layoutCount = theme.getLayouts().size();
            for (int master : targets) {
                remapped += restyleMaster(doc, master, theme, generator);
            }
            doc.markContentTypesDirty();

            executed = true;
            String scope = masterNumber != null ? "slideMaster" + masterNumber
                : targets.size() == 1 ? "the presentation" : "all " + targets.size() + " slide masters";
            displayMsg("Applied theme '" + theme.getDisplayName() + "' to " + scope);
            displayMsg("  Colors: " + theme.getColorScheme().size() + " scheme colors");
            displayMsg("  Fonts: " + theme.getMajorFont() + " (headings), " + theme.getMinorFont() + " (body)");
            displayMsg("  Layouts: " + layoutCount + " per master");
            if (remapped > 0) {
                displayMsg("  Re-pointed " + remapped + " slide(s) to the matching layout in the new theme");
            }

        } catch (CommandExecutionException e) {
            rollbackIfPartial();
            throw e;
        } catch (Exception e) {
            rollbackIfPartial();
            throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to apply theme: " + e.getMessage(), e);
        }
    }

    /**
     * Replace one master's theme, master XML, and layouts with the theme's,
     * and re-point that master's slides.
     *
     * @return number of slides whose layout target changed
     */
    private int restyleMaster(PPTXDocument doc, int master, ThemeDefinition theme,
                              ThemeXMLGenerator generator) throws Exception {
        String masterPart = "ppt/slideMasters/slideMaster" + master + ".xml";
        String masterRelsPart = "ppt/slideMasters/_rels/slideMaster" + master + ".xml.rels";
        Document oldRels = doc.getXmlPart(masterRelsPart);

        // The master's current layouts (in master order) and their types
        List<Integer> oldLayouts = new ArrayList<>();
        String themePart = null;
        if (oldRels != null) {
            for (Element rel : relationships(oldRels)) {
                String type = rel.getAttribute("Type");
                if (XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT.equals(type)) {
                    int n = targetNumber(LAYOUT_TARGET, rel.getAttribute("Target"));
                    if (n > 0 && !oldLayouts.contains(n)) oldLayouts.add(n);
                } else if (XMLConstants.RELATIONSHIP_TYPE_THEME.equals(type) && themePart == null) {
                    themePart = resolveFromMaster(rel.getAttribute("Target"));
                }
            }
        }
        Map<Integer, String> oldTypes = new LinkedHashMap<>();
        for (int n : oldLayouts) {
            oldTypes.put(n, layoutType(doc.getXmlPart("ppt/slideLayouts/slideLayout" + n + ".xml")));
        }
        if (themePart == null) {
            TreeSet<Integer> themes = partNumbers(doc, "ppt/theme/theme", THEME_PART);
            themePart = "ppt/theme/theme" + (themes.isEmpty() ? 1 : themes.last() + 1) + ".xml";
        }

        // Layout part numbers: reuse this master's, then fresh numbers past every existing layout
        List<LayoutDefinition> layouts = theme.getLayouts();
        int nextFree = partNumbers(doc, "ppt/slideLayouts/slideLayout", LAYOUT_PART).stream()
            .mapToInt(Integer::intValue).max().orElse(0) + 1;
        List<Integer> newNums = new ArrayList<>();
        for (int i = 0; i < layouts.size(); i++) {
            newNums.add(i < oldLayouts.size() ? oldLayouts.get(i) : nextFree++);
        }

        // Theme
        doc.putXmlPart(themePart, generator.generateThemeXML(theme));

        // Layouts
        List<String> newTypes = new ArrayList<>();
        for (int i = 0; i < layouts.size(); i++) {
            int n = newNums.get(i);
            Document layoutDoc = generator.generateSlideLayoutXML(theme, layouts.get(i));
            newTypes.add(layoutType(layoutDoc));
            doc.putXmlPart("ppt/slideLayouts/slideLayout" + n + ".xml", layoutDoc);
            doc.putXmlPart("ppt/slideLayouts/_rels/slideLayout" + n + ".xml.rels",
                generator.generateSlideLayoutRels(master));
        }

        // Master rels: layouts rId1..rIdN in order, then the theme
        Document rels = XMLFactoryProvider.createDocument();
        Element relsRoot = rels.createElementNS(PKG_REL, "Relationships");
        rels.appendChild(relsRoot);
        for (int i = 0; i < newNums.size(); i++) {
            relsRoot.appendChild(relationship(rels, "rId" + (i + 1), XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT,
                "../slideLayouts/slideLayout" + newNums.get(i) + ".xml"));
        }
        relsRoot.appendChild(relationship(rels, "rId" + (newNums.size() + 1), XMLConstants.RELATIONSHIP_TYPE_THEME,
            "../theme/" + themePart.substring("ppt/theme/".length())));

        // Master XML with layout ids unique across every master in the deck
        Document masterDoc = generator.generateSlideMasterXML(theme);
        long nextId = nextLayoutIdValue(doc, master);
        Element lst = (Element) masterDoc.getElementsByTagNameNS(PNS, "sldLayoutIdLst").item(0);
        while (lst.hasChildNodes()) lst.removeChild(lst.getFirstChild());
        for (int i = 0; i < newNums.size(); i++) {
            Element id = masterDoc.createElementNS(PNS, "p:sldLayoutId");
            id.setAttribute("id", String.valueOf(nextId++));
            id.setAttributeNS(RNS, "r:id", "rId" + (i + 1));
            lst.appendChild(id);
        }
        doc.putXmlPart(masterRelsPart, rels);
        doc.putXmlPart(masterPart, masterDoc);

        int remapped = remapSlides(doc, oldLayouts, oldTypes, newNums, newTypes);

        // Old layouts of this master that the theme didn't reuse
        ContentTypesModel ct = doc.getContentTypes();
        for (int n : oldLayouts) {
            if (newNums.contains(n)) continue;
            doc.removeXmlPart("ppt/slideLayouts/slideLayout" + n + ".xml");
            doc.removeXmlPart("ppt/slideLayouts/_rels/slideLayout" + n + ".xml.rels");
            if (ct != null) ct.removeOverride("/ppt/slideLayouts/slideLayout" + n + ".xml");
        }
        if (ct != null) {
            ct.addOverride("/" + themePart, CT_THEME);
            ct.addOverride("/" + masterPart, CT_MASTER);
            for (int n : newNums) ct.addOverride("/ppt/slideLayouts/slideLayout" + n + ".xml", CT_LAYOUT);
        }
        return remapped;
    }

    /**
     * Re-point slides that used one of {@code oldLayouts} at the new layout of
     * the same type (by master slot, then by type, then content, then first).
     */
    private static int remapSlides(PPTXDocument doc, List<Integer> oldLayouts, Map<Integer, String> oldTypes,
                                   List<Integer> newNums, List<String> newTypes) {
        int changed = 0;
        for (int slide : doc.getSlideNumbers()) {
            String relsName = "ppt/slides/_rels/slide" + slide + ".xml.rels";
            Document rels = doc.getXmlPart(relsName);
            if (rels == null) continue;
            boolean dirty = false;
            for (Element rel : relationships(rels)) {
                if (!rel.getAttribute("Type").endsWith("/slideLayout")) continue;
                String target = rel.getAttribute("Target");
                int oldNum = targetNumber(LAYOUT_TARGET, target);
                int slot = oldLayouts.indexOf(oldNum);
                if (slot < 0) continue; // belongs to another master
                int newSlot = pickLayout(slot + 1, oldTypes.get(oldNum), newTypes);
                int newNum = newNums.get(newSlot - 1);
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

    /**
     * 1-based slot in {@code newTypes} for a slide whose old layout sat in
     * {@code oldSlot} with {@code oldType}: keep the slot when its type still
     * matches, else the first slot of that type, else the same slot, else the
     * content ("obj") layout, else the first.
     */
    static int pickLayout(int oldSlot, String oldType, List<String> newTypes) {
        if (oldType != null) {
            if (oldSlot <= newTypes.size() && oldType.equals(newTypes.get(oldSlot - 1))) return oldSlot;
            int sameType = newTypes.indexOf(oldType);
            if (sameType >= 0) return sameType + 1;
        }
        if (oldSlot <= newTypes.size()) return oldSlot;
        int content = newTypes.indexOf("obj");
        return content >= 0 ? content + 1 : 1;
    }

    /** One past the highest sldMasterId / sldLayoutId used anywhere except {@code skipMaster}. */
    private static long nextLayoutIdValue(PPTXDocument doc, int skipMaster) {
        long max = MIN_LAYOUT_ID - 1;
        for (int m : partNumbers(doc, "ppt/slideMasters/slideMaster", MASTER_PART)) {
            if (m == skipMaster) continue;
            max = Math.max(max, maxIdAttribute(doc.getXmlPart("ppt/slideMasters/slideMaster" + m + ".xml"), "sldLayoutId"));
        }
        max = Math.max(max, maxIdAttribute(doc.getPresentationXml(), "sldMasterId"));
        return max + 1;
    }

    private static long maxIdAttribute(Document d, String localName) {
        long max = 0;
        if (d == null) return max;
        NodeList nodes = d.getElementsByTagNameNS(PNS, localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            try {
                max = Math.max(max, Long.parseLong(((Element) nodes.item(i)).getAttribute("id")));
            } catch (NumberFormatException ignored) {
                // Malformed id: not ours to fix
            }
        }
        return max;
    }

    // ---------- undo ----------

    private void takeSnapshot(PPTXDocument doc) throws Exception {
        document = doc;
        snapshot = new LinkedHashMap<>();
        partsBefore = new TreeSet<>();
        for (String prefix : SNAPSHOT_PREFIXES) {
            for (String part : doc.getPartNamesByPrefix(prefix)) {
                Document dom = doc.getXmlPart(part);
                if (dom == null) continue; // media / binary parts are never touched
                partsBefore.add(part);
                snapshot.put(part, copy(dom));
            }
        }
        Document ct = doc.getContentTypesDoc();
        if (ct != null) snapshot.put("[Content_Types].xml", copy(ct));
    }

    private void restoreSnapshot() {
        for (String prefix : SNAPSHOT_PREFIXES) {
            for (String part : document.getPartNamesByPrefix(prefix)) {
                if (!partsBefore.contains(part) && document.getXmlPart(part) != null) {
                    document.removeXmlPart(part);
                }
            }
        }
        for (Map.Entry<String, Document> e : snapshot.entrySet()) {
            document.putXmlPart(e.getKey(), e.getValue());
        }
    }

    /** If execute failed midway, put the deck back as it was. */
    private void rollbackIfPartial() {
        if (snapshot != null && !executed) {
            try {
                restoreSnapshot();
            } catch (RuntimeException e) {
                logger.error("Failed to roll back partial apply-theme: " + e.getMessage());
            }
            snapshot = null;
        }
    }

    @Override
    public void undo() {
        if (!executed || snapshot == null) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME, "Nothing to undo");
        }
        restoreSnapshot();
        executed = false;
        displayMsg("Restored the theme, masters, and layouts from before applying '" + themeId + "'");
    }

    @Override
    public boolean canUndo() {
        return executed && snapshot != null;
    }

    @Override
    public String getDescription() {
        return "Apply theme '" + themeId + "' to "
            + (masterNumber == null ? "presentation" : "slideMaster" + masterNumber);
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public String getThemeId() {
        return themeId;
    }

    // ---------- helpers ----------

    private void displayMsg(String message) {
        if (display != null) display.displayMessage(message);
    }

    private static Document copy(Document source) throws Exception {
        Document copy = XMLFactoryProvider.createDocument();
        copy.setXmlStandalone(source.getXmlStandalone());
        copy.appendChild(copy.importNode(source.getDocumentElement(), true));
        return copy;
    }

    private static TreeSet<Integer> partNumbers(PPTXDocument doc, String prefix, Pattern pattern) {
        TreeSet<Integer> numbers = new TreeSet<>();
        for (String name : doc.getPartNamesByPrefix(prefix)) {
            Matcher m = pattern.matcher(name);
            if (m.matches()) numbers.add(Integer.parseInt(m.group(1)));
        }
        return numbers;
    }

    private static List<Element> relationships(Document relsDoc) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = relsDoc.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < nodes.getLength(); i++) out.add((Element) nodes.item(i));
        return out;
    }

    private static Element relationship(Document rels, String id, String type, String target) {
        Element rel = rels.createElementNS(PKG_REL, "Relationship");
        rel.setAttribute("Id", id);
        rel.setAttribute("Type", type);
        rel.setAttribute("Target", target);
        return rel;
    }

    private static int targetNumber(Pattern pattern, String target) {
        Matcher m = pattern.matcher(target);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** Part name for a target relative to ppt/slideMasters/. */
    private static String resolveFromMaster(String target) {
        if (target.startsWith("../")) return "ppt/" + target.substring(3);
        if (target.startsWith("/")) return target.substring(1);
        return "ppt/slideMasters/" + target;
    }

    /** The p:sldLayout/@type value; OOXML's default when absent is "cust". */
    private static String layoutType(Document layoutDoc) {
        if (layoutDoc == null) return null;
        String type = layoutDoc.getDocumentElement().getAttribute("type");
        return type.isEmpty() ? "cust" : type;
    }
}
