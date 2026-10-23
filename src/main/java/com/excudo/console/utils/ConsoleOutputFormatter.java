package com.excudo.console.utils;

import com.excudo.core.commands.readonly.HelpCatalog;

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
     * Usage text for a command name or alias, generated from its schema.
     * Returns "Unknown command: X" when nothing matches.
     */
    public static String getCommandHelp(String command) {
        List<String> lines = HelpCatalog.commandHelp(command);
        return lines == null ? "Unknown command: " + command : String.join("\n", lines);
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
