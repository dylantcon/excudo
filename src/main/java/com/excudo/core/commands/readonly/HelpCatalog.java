package com.excudo.core.commands.readonly;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.parsing.CommandRegistry;
import com.excudo.core.parsing.CommandSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Help text generated from the live command registry, so the help output
 * cannot drift from the commands that actually parse. Each registered command
 * is filed into a category by its package and name; anything unmatched lands
 * in "Other" rather than disappearing.
 */
public final class HelpCatalog {

    public enum Category {
        FILES("Files", "files", "file", "getting-started", "start"),
        SESSIONS("Sessions", "sessions", "session"),
        SLIDES("Slides", "slides", "slide"),
        SHAPES("Shapes & text", "shapes", "shape", "text"),
        ANIMATIONS("Animations & transitions", "animations", "animation", "transitions", "transition"),
        THEMES("Themes", "themes", "theme"),
        LAYOUTS("Layouts", "layouts", "layout"),
        MASTERS("Masters", "masters", "master"),
        NOTES("Notes", "notes"),
        HISTORY("History", "undo-redo"),
        INSPECTION("Inspection & debugging", "inspection", "inspect", "debug", "debugging", "xml"),
        AI("AI", "ai", "smart", "smartcontent", "agent"),
        OTHER("Other", "other");

        private final String title;
        private final List<String> topics;

        Category(String title, String... topics) {
            this.title = title;
            this.topics = List.of(topics);
        }

        public String title() { return title; }
        public List<String> topics() { return topics; }

        public static Category fromTopic(String topic) {
            String t = topic.toLowerCase(Locale.ROOT);
            for (Category c : values()) {
                if (c.topics.contains(t)) return c;
            }
            return null;
        }
    }

    private static final int WRAP = 88;

    private HelpCatalog() {}

    /** Canonical command names grouped by category, each group sorted. */
    public static Map<Category, List<String>> commandsByCategory() {
        Map<Category, List<String>> grouped = new LinkedHashMap<>();
        for (Category c : Category.values()) grouped.put(c, new ArrayList<>());
        for (String name : new TreeSet<>(CommandRegistry.getCommandNames())) {
            grouped.get(categorize(name)).add(name);
        }
        grouped.values().removeIf(List::isEmpty);
        return grouped;
    }

    static Category categorize(String name) {
        Class<? extends Command> cls = CommandClassRegistry.getCommandClass(name);
        String pkg = cls == null ? "" : cls.getPackageName();

        if (name.startsWith("session-")) return Category.SESSIONS;
        if (name.equals("llm") || name.startsWith("llm-")) return Category.AI;
        if (name.equals("undo") || name.equals("redo") || name.equals("history")) return Category.HISTORY;
        if (name.contains("animation") || name.contains("transition")) return Category.ANIMATIONS;
        if (name.contains("theme") || name.equals("set-object-defaults")) return Category.THEMES;
        if (name.contains("layout") || name.contains("placeholder")) return Category.LAYOUTS;
        if (name.contains("master")) return Category.MASTERS;
        if (name.contains("notes")) return Category.NOTES;
        if (name.equals("new-presentation") || name.equals("load") || name.equals("save")
                || name.equals("help")) {
            return Category.FILES;
        }
        if (name.equals("inject-icon") || name.equals("enhanced-content") || name.equals("icon")) {
            return Category.AI;
        }
        if (pkg.endsWith(".mutating.deck") || name.endsWith("-slide") || name.endsWith("-slides")
                || name.equals("clear-slide-content")) {
            return Category.SLIDES;
        }
        if (pkg.endsWith(".mutating.slide")) return Category.SHAPES;
        if (pkg.endsWith(".readonly")) return Category.INSPECTION;
        return Category.OTHER;
    }

    /** Compact overview: every command, grouped, plus how to dig deeper. */
    public static List<String> overview() {
        List<String> out = new ArrayList<>();
        out.add("Excudo commands. 'help <command>' shows usage; 'help <category>' lists a group.");
        out.add("");
        for (Map.Entry<Category, List<String>> e : commandsByCategory().entrySet()) {
            String label = String.format("  %-26s", e.getKey().title());
            out.addAll(wrap(label, e.getValue()));
        }
        out.add("");
        out.add("Arrange mode (AI agentic editing):");
        out.add("  arrange        Enter arrange mode: type a request, blank line to send.");
        out.add("                 Inside it, /exit leaves and /<command> runs a console command.");
        out.add("  arrange mcp    Start the in-process MCP HTTP server.");
        out.add("");
        out.add("Categories: " + categoryTopicList());
        out.add("Two-word forms work too: 'session create', 'llm config show', 'list slides'.");
        out.addAll(wrap("Aliases: ", aliasPairs()));
        return out;
    }

    /** Detailed listing for one category, or {@code null} if the topic names none. */
    public static List<String> categoryHelp(String topic) {
        Category category = Category.fromTopic(topic);
        if (category == null) return null;
        List<String> names = commandsByCategory().get(category);
        List<String> out = new ArrayList<>();
        out.add(category.title() + ":");
        out.add("");
        if (names == null || names.isEmpty()) {
            out.add("  (no commands)");
            return out;
        }
        int width = names.stream().mapToInt(String::length).max().orElse(10) + 2;
        for (String name : names) {
            CommandSchema schema = CommandRegistry.getSchema(name);
            String desc = schema == null ? "" : schema.getDescription();
            List<String> aliases = CommandRegistry.getAliases(name);
            String aliasNote = aliases.isEmpty() ? "" : "  (alias: " + String.join(", ", aliases) + ")";
            out.add(String.format("  %-" + width + "s%s%s", name, desc, aliasNote));
        }
        out.add("");
        out.add("Use 'help <command>' for parameters and examples.");
        return out;
    }

    /**
     * Full usage for a command name or alias (any case), or {@code null} if
     * it names no command.
     */
    public static List<String> commandHelp(String nameOrAlias) {
        String canonical = CommandRegistry.resolveName(nameOrAlias);
        if (canonical == null) return null;
        CommandSchema schema = CommandRegistry.getSchema(canonical);
        List<String> out = new ArrayList<>(List.of(schema.generateHelp().split("\n", -1)));
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) out.remove(out.size() - 1);
        List<String> aliases = CommandRegistry.getAliases(canonical);
        if (!aliases.isEmpty()) {
            out.add("");
            out.add("Aliases: " + String.join(", ", aliases));
        }
        return out;
    }

    private static String categoryTopicList() {
        List<String> topics = new ArrayList<>();
        for (Category c : commandsByCategory().keySet()) topics.add(c.topics().get(0));
        return String.join(", ", topics);
    }

    private static List<String> aliasPairs() {
        List<String> pairs = new ArrayList<>();
        new TreeMap<>(CommandRegistry.getAllAliases())
            .forEach((alias, name) -> pairs.add(alias + " -> " + name));
        return pairs;
    }

    private static List<String> wrap(String label, List<String> words) {
        List<String> lines = new ArrayList<>();
        String indent = " ".repeat(label.length());
        StringBuilder line = new StringBuilder(label);
        boolean first = true;
        for (String w : words) {
            String piece = first ? w : ", " + w;
            if (!first && line.length() + piece.length() > WRAP) {
                lines.add(line.append(",").toString());
                line = new StringBuilder(indent).append(w);
            } else {
                line.append(piece);
            }
            first = false;
        }
        lines.add(line.toString());
        return lines;
    }
}
