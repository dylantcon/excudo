package com.excudo.core.geometry;

import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;

import java.util.*;
import java.util.stream.*;

public class ShapeSelector {

    private ShapeSelector() {}

    public static List<SlideShape> resolve(String selector, ShapeRegistry registry) {
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("Selector must not be null or empty");
        }

        String trimmed = selector.trim();
        Set<Integer> seen = new LinkedHashSet<>();
        List<SlideShape> result = new ArrayList<>();

        String[] parts = trimmed.split(",");
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) continue;

            List<SlideShape> matched = resolveToken(token, registry);
            for (SlideShape shape : matched) {
                if (seen.add(shape.getSpid())) {
                    result.add(shape);
                }
            }
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException(buildNoMatchError(trimmed, registry));
        }

        result.sort(Comparator.comparingInt(SlideShape::getSpid));
        return result;
    }

    private static List<SlideShape> resolveToken(String token, ShapeRegistry registry) {
        if (token.equalsIgnoreCase("all")) {
            return registry.getAllShapes().stream()
                    .filter(s -> s.getSpid() != 1)
                    .collect(Collectors.toList());
        }

        if (token.startsWith("type:")) {
            String typeName = token.substring(5);
            SlideShape.ShapeType shapeType = parseShapeType(typeName);
            return registry.getShapesByType(shapeType);
        }

        if (token.startsWith("text:")) {
            String pattern = token.substring(5);
            if ("*".equals(pattern)) {
                return registry.getTextShapes();
            }
            return registry.getTextShapes().stream()
                    .filter(s -> globMatch(pattern, s.getTextContent()))
                    .collect(Collectors.toList());
        }

        if (token.startsWith("name:")) {
            String pattern = token.substring(5);
            return registry.getAllShapes().stream()
                    .filter(s -> s.getName() != null && globMatch(pattern, s.getName()))
                    .collect(Collectors.toList());
        }

        try {
            int spid = Integer.parseInt(token);
            SlideShape shape = registry.getShape(spid);
            if (shape != null) {
                return List.of(shape);
            }
            return List.of();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid selector token: '" + token + "'. "
                    + "Expected: SPID number, 'all', 'type:<ShapeType>', 'text:*', or 'name:<glob>'");
        }
    }

    private static SlideShape.ShapeType parseShapeType(String name) {
        for (SlideShape.ShapeType type : SlideShape.ShapeType.values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        String available = Arrays.stream(SlideShape.ShapeType.values())
                .map(t -> t.name().toLowerCase())
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(
                "Unknown shape type: '" + name + "'. Available types: " + available);
    }

    private static boolean globMatch(String pattern, String value) {
        if (value == null) return false;
        String regex = "(?i)" + pattern.replace("*", ".*");
        return value.matches(regex);
    }

    private static String buildNoMatchError(String selector, ShapeRegistry registry) {
        StringBuilder sb = new StringBuilder();
        sb.append("Selector '").append(selector).append("' matched 0 shapes. ");
        sb.append("Available shapes: ");
        List<SlideShape> all = registry.getAllShapes();
        if (all.isEmpty()) {
            sb.append("(none)");
        } else {
            sb.append(all.stream()
                    .map(s -> String.format("spid=%d name='%s' type=%s",
                            s.getSpid(), s.getName(), s.getType().name().toLowerCase()))
                    .collect(Collectors.joining("; ")));
        }
        return sb.toString();
    }
}
