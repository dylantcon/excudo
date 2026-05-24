package com.excudo.console.utils;

import com.excudo.core.model.SlideShape;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.ParagraphMetadata;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;

/**
 * Utility class for console output formatting.
 * Centralizes display and help system methods to eliminate code duplication
 * in AbstractConsoleEngine.
 */
public class ConsoleOutputFormatter {

    /**
     * Generate help text for a specific command
     */
    public static String getCommandHelp(String command) {
        return switch (command.toLowerCase()) {
            case "new" -> "new [themeId] - Create a new presentation from scratch (default: minimal)";
            case "load" -> "load <file.pptx> - Load a PowerPoint presentation";
            case "save" -> "save [file.pptx] - Save the current presentation";
            case "list" -> "list - Show all slides in the presentation";
            case "show" -> "show <slide#> - Display detailed slide information";
            case "create" -> "create <position> [title] [layout] - Create a new slide";
            case "delete" -> "delete <slide#> - Delete a slide";
            case "llm" -> "llm <subcommand> - AI-powered editing commands";
            case "session" -> "session <create|list|info|close|switch> - Manage presentation sessions";
            case "inject-icon" -> "inject <slide> <query> - Inject icon into slide using SmartContent";
            case "enhanced-content" -> "enhance <slide> [query] - Enhance slide with SmartContent icons";
            case "icon" -> "icon <search|upload|list|sources> - Icon management and library";
            case "list-layouts" -> "list-layouts [themeId] - Show slide layouts (from presentation or theme)";
            case "list-spids" -> "list-spids <slide#> - List all SPIDs on a slide";
            case "list-animations" -> "list-animations <slide#> - Show Animation Pane view for a slide";
            case "list-animation-types" -> "list-animation-types - List all available animation types";
            case "list-shape-types" -> "list-shape-types - List all available shape types by category";
            case "dump-timing" -> "dump-timing <slide#|range|all> - Dump timing XML for OOXML analysis";
            case "dump-shape" -> "dump-shape <slide#|range|all> - Dump shape XML for formatting analysis";
            case "show-shape" -> "show-shape <slide#> <spid> - Show detailed shape information";
            case "edit-content" -> "edit-content <slide#> <spid> <text> - Edit text content of a shape";
            case "add-shape" -> "add-shape <slide#> <type> <text> <x> <y> <w> <h> [--fill-color HEX] [--line-color HEX] - Add new shape";
            case "remove-shape" -> "remove-shape <slide#> <spid> - Remove a shape from a slide";
            case "edit-bullet" -> "edit-bullet <slide#> <spid> <operation> <index> [text] - Edit bullet points (add/edit/remove/reorder)";
            case "add-animation" -> "add-animation <slide#> <spid> <type> <direction> <trigger> - Add animation to shape";
            case "remove-animation" -> "remove-animation <slide#> <id> - Remove animation (id from list-animations)";
            case "update-animation" -> "update-animation <slide#> <id> <property> <value> - Update animation (id from list-animations)";
            case "list-notes" -> "list-notes [slide#] - Show notes content for all slides or specific slide";
            case "list-themes" -> "list-themes - List available bundled themes";
            case "show-theme" -> "show-theme <themeId> - Display complete theme details";
            case "create-theme" -> "create-theme <id> [baseTheme] [displayName] - Create theme from existing";
            case "edit-theme" -> "edit-theme <themeId> <property> <value> - Edit a custom theme property";
            case "delete-theme" -> "delete-theme <themeId> - Delete a custom theme";
            case "apply-theme" -> "apply-theme <themeId> - Apply a bundled theme (minimal, corporate, academic)";
            case "undo" -> "undo - Undo the last executed command";
            case "redo" -> "redo - Redo the last undone command";
            case "history" -> "history - Show executed command history and redo stack";
            default -> "Unknown command: " + command;
        };
    }

    /**
     * Generate compact help overview with category hints.
     * Users type 'help <category>' for full detail.
     */
    public static List<String> generateHelpText() {
        List<String> help = new ArrayList<>();

        help.add("Available Commands (use 'help <topic>' for details):");
        help.add("");
        help.add("  Getting Started    new, load, save, list, quit");
        help.add("  Slides             show, create, delete, list-notes, list-layouts");
        help.add("  Shapes             list-spids, show-shape, add-shape, remove-shape,");
        help.add("                     edit-content, edit-bullet, list-shape-types");
        help.add("  Animations         list-animations, list-animation-types, add-animation,");
        help.add("                     remove-animation, update-animation");
        help.add("  Themes             list-themes, show-theme, apply-theme, create-theme,");
        help.add("                     edit-theme, delete-theme");
        help.add("  XML Debugging      dump-shape, dump-timing");
        help.add("  AI & Smart Content llm, inject, enhance");
        help.add("  Session & History  session, undo, redo, history");
        help.add("");
        help.add("Topics: shapes, slides, animations, themes, debug, ai, session");
        help.add("Also:   help <command-name>  |  help animations  |  help add-animation");

        return help;
    }

    /**
     * Generate help for Getting Started commands.
     */
    public static List<String> generateGettingStartedHelp() {
        List<String> help = new ArrayList<>();
        help.add("Getting Started:");
        help.add("");
        help.add("  " + getCommandHelp("new"));
        help.add("  " + getCommandHelp("load"));
        help.add("  " + getCommandHelp("save"));
        help.add("  " + getCommandHelp("list"));
        help.add("  quit / exit - Exit the console");
        help.add("");
        help.add("Examples:");
        help.add("  new corporate");
        help.add("  load presentation.pptx");
        help.add("  save output.pptx");
        return help;
    }

    /**
     * Generate help for Slide commands.
     */
    public static List<String> generateSlideHelp() {
        List<String> help = new ArrayList<>();
        help.add("Slide Operations:");
        help.add("");
        help.add("  " + getCommandHelp("show"));
        help.add("  " + getCommandHelp("create"));
        help.add("  " + getCommandHelp("delete"));
        help.add("  " + getCommandHelp("list-notes"));
        help.add("  " + getCommandHelp("list-layouts"));
        help.add("");
        help.add("Examples:");
        help.add("  show 1");
        help.add("  create 3 \"New Content Slide\" slideLayout5");
        help.add("  list-layouts corporate");
        help.add("  list-notes 2");
        return help;
    }

    /**
     * Generate help for Shape commands.
     */
    public static List<String> generateShapeHelp() {
        List<String> help = new ArrayList<>();
        help.add("Shape Operations:");
        help.add("");
        help.add("  " + getCommandHelp("list-spids"));
        help.add("  " + getCommandHelp("show-shape"));
        help.add("  " + getCommandHelp("add-shape"));
        help.add("  " + getCommandHelp("remove-shape"));
        help.add("  " + getCommandHelp("edit-content"));
        help.add("  " + getCommandHelp("edit-bullet"));
        help.add("  " + getCommandHelp("list-shape-types"));
        help.add("");
        help.add("Examples:");
        help.add("  list-spids 1");
        help.add("  show-shape 1 2");
        help.add("  add-shape 1 RECTANGLE \"Hello\" 100 100 200 100");
        help.add("  add-shape 1 ELLIPSE \"Styled\" 50 50 150 150 --fill-color FF0000 --line-color 000000");
        help.add("  edit-content 1 2 \"Updated title text\"");
        help.add("  edit-bullet 1 3 add -1 \"New bullet point\"");
        help.add("  edit-bullet 1 3 edit 0 \"Updated text\"");
        help.add("  edit-bullet 1 3 remove 2");
        help.add("  remove-shape 1 5");
        return help;
    }

    /**
     * Generate help for Animation commands.
     */
    public static List<String> generateAnimationHelp() {
        List<String> help = new ArrayList<>();
        help.add("Animation Operations:");
        help.add("");
        help.add("  " + getCommandHelp("list-animations"));
        help.add("  " + getCommandHelp("list-animation-types"));
        help.add("  " + getCommandHelp("add-animation"));
        help.add("  " + getCommandHelp("remove-animation"));
        help.add("  " + getCommandHelp("update-animation"));
        help.add("");
        help.add("Examples:");
        help.add("  list-animations 1");
        help.add("  add-animation 1 2 fade in click");
        help.add("  add-animation 1 2 fly-in-left in with-previous 1000");
        help.add("  remove-animation 1 15");
        help.add("  update-animation 1 15 duration 1000");
        help.add("");
        help.add("Use 'help add-animation' for parameter details.");
        help.add("Use 'help animations' or 'list-animation-types' for all animation types.");
        return help;
    }

    /**
     * Generate help for Theme commands.
     */
    public static List<String> generateThemeHelp() {
        List<String> help = new ArrayList<>();
        help.add("Theme Operations:");
        help.add("");
        help.add("  " + getCommandHelp("list-themes"));
        help.add("  " + getCommandHelp("show-theme"));
        help.add("  " + getCommandHelp("apply-theme"));
        help.add("  " + getCommandHelp("create-theme"));
        help.add("  " + getCommandHelp("edit-theme"));
        help.add("  " + getCommandHelp("delete-theme"));
        help.add("");
        help.add("Examples:");
        help.add("  list-themes");
        help.add("  show-theme corporate");
        help.add("  apply-theme corporate");
        help.add("  create-theme my-theme corporate \"My Custom Theme\"");
        help.add("  edit-theme my-theme color.accent1 FF5733");
        help.add("  edit-theme my-theme majorFont Georgia");
        return help;
    }

    /**
     * Generate help for XML Debugging commands.
     */
    public static List<String> generateDebugHelp() {
        List<String> help = new ArrayList<>();
        help.add("XML Debugging:");
        help.add("");
        help.add("  " + getCommandHelp("dump-shape"));
        help.add("  " + getCommandHelp("dump-timing"));
        help.add("");
        help.add("Examples:");
        help.add("  dump-shape 1");
        help.add("  dump-shape 1-3");
        help.add("  dump-shape all");
        help.add("  dump-timing 1");
        help.add("  dump-timing 1-5");
        help.add("  dump-timing all");
        return help;
    }

    /**
     * Generate help for AI & Smart Content commands.
     */
    public static List<String> generateAIHelp() {
        List<String> help = new ArrayList<>();
        help.add("AI & Smart Content:");
        help.add("");
        help.add("  " + getCommandHelp("llm"));
        help.add("  " + getCommandHelp("inject-icon"));
        help.add("  " + getCommandHelp("enhanced-content"));
        help.add("");
        help.add("Examples:");
        help.add("  llm edit add fade animations to all titles");
        help.add("  inject 1 computer");
        help.add("  enhance 1");
        return help;
    }

    /**
     * Generate help for Session & History commands.
     */
    public static List<String> generateSessionHelp() {
        List<String> help = new ArrayList<>();
        help.add("Session & History:");
        help.add("");
        help.add("  " + getCommandHelp("session"));
        help.add("  " + getCommandHelp("undo"));
        help.add("  " + getCommandHelp("redo"));
        help.add("  " + getCommandHelp("history"));
        help.add("");
        help.add("Examples:");
        help.add("  session create presentation.pptx");
        help.add("  session list");
        help.add("  session info session-123");
        help.add("  session switch session-456");
        help.add("  undo");
        help.add("  redo");
        help.add("  history");
        return help;
    }

    /**
     * Format slide shape information for display
     */
    public static List<String> formatShapeDetails(SlideShape shape) {
        List<String> details = new ArrayList<>();

        details.add("=== Shape Details ===");
        details.add("SPID: " + shape.getSpid());
        details.add("Type: " + (shape.getType() != null ? shape.getType() : "unknown"));
        details.add("Name: " + (shape.getName() != null ? shape.getName() : "unnamed"));

        if (shape.getGeometry() != null) {
            var geom = shape.getGeometry();
            details.add("Position: (" + geom.getX() + ", " + geom.getY() + ")");
            details.add("Size: " + geom.getWidth() + " x " + geom.getHeight());
        }

        // Display text content with bullet point structure
        String textContent = shape.getTextContent();
        if (textContent != null && !textContent.trim().isEmpty()) {
            details.add("Text Content:");
            details.add("  Raw: " + textContent);

            // Use existing paragraph metadata if available, otherwise parse from text content
            ParagraphMetadata paragraphMeta = shape.getParagraphMetadata();
            if (paragraphMeta == null) {
                paragraphMeta = com.excudo.utils.ParagraphMetadataParser.parseTextContent(textContent);
            }

            if (paragraphMeta.getParagraphCount() > 0) {
                details.add("  Paragraph Structure (" + paragraphMeta.getParagraphCount() + " paragraphs):");

                for (int i = 0; i < paragraphMeta.getParagraphCount(); i++) {
                    String content = paragraphMeta.getParagraphContent(i);

                    if (paragraphMeta.isParagraphBullet(i)) {
                        String bulletMarker = paragraphMeta.getBulletMarker(i);
                        details.add("    " + (i + 1) + ". [BULLET] " + bulletMarker + " " + content);
                    } else {
                        details.add("    " + (i + 1) + ". [PLAIN] " + content);
                    }
                }

                List<String> bulletPoints = paragraphMeta.getBulletPointsOnly();
                if (!bulletPoints.isEmpty()) {
                    details.add("  Bullet Points Only (" + bulletPoints.size() + " bullets):");
                    for (int i = 0; i < bulletPoints.size(); i++) {
                        details.add("    - " + bulletPoints.get(i));
                    }
                }
            }
        } else {
            details.add("Text Content: none");
        }

        return details;
    }

    /**
     * Format animation information for display
     */
    public static List<String> formatAnimations(List<AnimationBinding> animationBindings) {
        List<String> formatted = new ArrayList<>();

        if (animationBindings.isEmpty()) {
            formatted.add("  No animations found");
            return formatted;
        }

        // Group animations by animation group (timing context)
        Map<String, List<AnimationBinding>> byAnimationGroup = new TreeMap<>();

        for (var binding : animationBindings) {
            String animationGroup = binding.getAnimationGroup() != null ? binding.getAnimationGroup() : "on-click";
            byAnimationGroup.computeIfAbsent(animationGroup, k -> new ArrayList<>()).add(binding);
        }

        // Display animations grouped by animation group
        int displayClickNumber = 1;
        for (var entry : byAnimationGroup.entrySet()) {
            String groupType = entry.getKey();
            formatted.add("");

            // Convert animation group to display label
            switch (groupType) {
                case "on-click":
                    formatted.add("  Click " + displayClickNumber + ":");
                    displayClickNumber++;
                    break;
                case "with-previous":
                    formatted.add("  With Previous:");
                    break;
                case "after-previous":
                    formatted.add("  After Previous:");
                    break;
                default:
                    formatted.add("  " + groupType + ":");
                    break;
            }

            for (var binding : entry.getValue()) {
                String animType = binding.getAnimationTypeName();
                String direction = binding.isEntranceAnimation() ? "in" :
                                 binding.isExitAnimation() ? "out" : "emphasis";
                int targetSpid = binding.getTargetSpid();
                String shapeName = "SPID " + targetSpid;
                String timing = binding.getAnimationGroup() != null ? binding.getAnimationGroup() : "on-click";
                String nodeId = binding.getTimingNodeId() >= 0 ? "  id:" + binding.getTimingNodeId() : "";

                formatted.add(String.format("    - %s %s: %s [%s]%s",
                    animType,
                    direction,
                    shapeName,
                    timing,
                    nodeId
                ));
            }
        }

        return formatted;
    }

    /**
     * Format shape list for SPID display
     */
    public static List<String> formatShapeList(List<SlideShape> shapes, int slideNumber) {
        List<String> formatted = new ArrayList<>();

        formatted.add("");
        formatted.add("SPIDs on slide " + slideNumber + " (" + shapes.size() + " shapes):");

        for (var shape : shapes) {
            String type = shape.getType() != null ? shape.getType().toString() : "unknown";
            String name = shape.getName() != null ? shape.getName() : "unnamed";
            formatted.add(String.format("  SPID %-3d %-15s %s",
                shape.getSpid(),
                type,
                name
            ));
        }

        return formatted;
    }

    /**
     * Format layout list for display
     */
    public static List<String> formatLayoutList(List<LayoutInfo> layouts) {
        List<String> formatted = new ArrayList<>();

        formatted.add("");
        formatted.add("Available Layouts (" + layouts.size() + " total):");

        for (var layout : layouts) {
            formatted.add(String.format("  %-15s %-25s %s",
                layout.getLayoutId(),
                layout.getName(),
                layout.getPlaceholderSummary()
            ));
        }

        return formatted;
    }
}
