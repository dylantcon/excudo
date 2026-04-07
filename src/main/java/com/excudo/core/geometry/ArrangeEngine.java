package com.excudo.core.geometry;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;

import java.util.*;
import java.util.stream.*;

public class ArrangeEngine {

    public record ArrangeResult(int spid, ShapeGeometry original, ShapeGeometry updated) {}

    public static final long SLIDE_WIDTH = 12192000L;
    public static final long SLIDE_HEIGHT = 6858000L;
    public static final long GRID_SNAP = 914400L;

    private static final List<String> VALID_OPERATIONS = List.of(
            "align-left", "align-right", "align-top", "align-bottom",
            "align-center-h", "align-center-v",
            "distribute-h", "distribute-v",
            "match-width", "match-height", "match-size",
            "center-on-slide", "snap-to-grid"
    );

    private ArrangeEngine() {}

    // --- Alignment ---

    public static List<ArrangeResult> alignLeft(List<SlideShape> shapes, Integer anchorSpid) {
        long target;
        if (anchorSpid != null) {
            target = findAnchor(shapes, anchorSpid).getGeometry().getX();
        } else {
            target = shapes.stream().mapToLong(s -> s.getGeometry().getX()).min().orElse(0);
        }
        return shapes.stream()
                .map(s -> makeResult(s, target, s.getGeometry().getY(),
                        s.getGeometry().getWidth(), s.getGeometry().getHeight()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<ArrangeResult> alignRight(List<SlideShape> shapes, Integer anchorSpid) {
        long target;
        if (anchorSpid != null) {
            ShapeGeometry ag = findAnchor(shapes, anchorSpid).getGeometry();
            target = ag.getX() + ag.getWidth();
        } else {
            target = shapes.stream()
                    .mapToLong(s -> s.getGeometry().getX() + s.getGeometry().getWidth())
                    .max().orElse(0);
        }
        return shapes.stream()
                .map(s -> makeResult(s, target - s.getGeometry().getWidth(), s.getGeometry().getY(),
                        s.getGeometry().getWidth(), s.getGeometry().getHeight()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<ArrangeResult> alignTop(List<SlideShape> shapes, Integer anchorSpid) {
        long target;
        if (anchorSpid != null) {
            target = findAnchor(shapes, anchorSpid).getGeometry().getY();
        } else {
            target = shapes.stream().mapToLong(s -> s.getGeometry().getY()).min().orElse(0);
        }
        return shapes.stream()
                .map(s -> makeResult(s, s.getGeometry().getX(), target,
                        s.getGeometry().getWidth(), s.getGeometry().getHeight()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<ArrangeResult> alignBottom(List<SlideShape> shapes, Integer anchorSpid) {
        long target;
        if (anchorSpid != null) {
            ShapeGeometry ag = findAnchor(shapes, anchorSpid).getGeometry();
            target = ag.getY() + ag.getHeight();
        } else {
            target = shapes.stream()
                    .mapToLong(s -> s.getGeometry().getY() + s.getGeometry().getHeight())
                    .max().orElse(0);
        }
        return shapes.stream()
                .map(s -> makeResult(s, s.getGeometry().getX(), target - s.getGeometry().getHeight(),
                        s.getGeometry().getWidth(), s.getGeometry().getHeight()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<ArrangeResult> alignCenterH(List<SlideShape> shapes, Integer anchorSpid) {
        long centerTarget;
        if (anchorSpid != null) {
            ShapeGeometry ag = findAnchor(shapes, anchorSpid).getGeometry();
            centerTarget = ag.getX() + ag.getWidth() / 2;
        } else {
            centerTarget = (long) shapes.stream()
                    .mapToLong(s -> s.getGeometry().getX() + s.getGeometry().getWidth() / 2)
                    .average().orElse(0);
        }
        return shapes.stream()
                .map(s -> makeResult(s, centerTarget - s.getGeometry().getWidth() / 2,
                        s.getGeometry().getY(),
                        s.getGeometry().getWidth(), s.getGeometry().getHeight()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<ArrangeResult> alignCenterV(List<SlideShape> shapes, Integer anchorSpid) {
        long centerTarget;
        if (anchorSpid != null) {
            ShapeGeometry ag = findAnchor(shapes, anchorSpid).getGeometry();
            centerTarget = ag.getY() + ag.getHeight() / 2;
        } else {
            centerTarget = (long) shapes.stream()
                    .mapToLong(s -> s.getGeometry().getY() + s.getGeometry().getHeight() / 2)
                    .average().orElse(0);
        }
        return shapes.stream()
                .map(s -> makeResult(s, s.getGeometry().getX(),
                        centerTarget - s.getGeometry().getHeight() / 2,
                        s.getGeometry().getWidth(), s.getGeometry().getHeight()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // --- Distribution ---

    public static List<ArrangeResult> distributeH(List<SlideShape> shapes) {
        requireMinShapes(shapes, 3, "distribute-h");
        List<SlideShape> sorted = shapes.stream()
                .sorted(Comparator.comparingLong(s -> s.getGeometry().getX()))
                .collect(Collectors.toList());

        SlideShape first = sorted.get(0);
        SlideShape last = sorted.get(sorted.size() - 1);
        long totalSpan = last.getGeometry().getX()
                - first.getGeometry().getX() - first.getGeometry().getWidth();
        long interiorWidthSum = sorted.subList(1, sorted.size() - 1).stream()
                .mapToLong(s -> s.getGeometry().getWidth()).sum();
        long totalGap = totalSpan - interiorWidthSum;
        long gap = totalGap / (sorted.size() - 1);

        List<ArrangeResult> results = new ArrayList<>();
        long currentX = first.getGeometry().getX() + first.getGeometry().getWidth() + gap;
        for (int i = 1; i < sorted.size() - 1; i++) {
            SlideShape s = sorted.get(i);
            ArrangeResult r = makeResult(s, currentX, s.getGeometry().getY(),
                    s.getGeometry().getWidth(), s.getGeometry().getHeight());
            if (r != null) results.add(r);
            currentX += s.getGeometry().getWidth() + gap;
        }
        return results;
    }

    public static List<ArrangeResult> distributeV(List<SlideShape> shapes) {
        requireMinShapes(shapes, 3, "distribute-v");
        List<SlideShape> sorted = shapes.stream()
                .sorted(Comparator.comparingLong(s -> s.getGeometry().getY()))
                .collect(Collectors.toList());

        SlideShape first = sorted.get(0);
        SlideShape last = sorted.get(sorted.size() - 1);
        long totalSpan = last.getGeometry().getY()
                - first.getGeometry().getY() - first.getGeometry().getHeight();
        long interiorHeightSum = sorted.subList(1, sorted.size() - 1).stream()
                .mapToLong(s -> s.getGeometry().getHeight()).sum();
        long totalGap = totalSpan - interiorHeightSum;
        long gap = totalGap / (sorted.size() - 1);

        List<ArrangeResult> results = new ArrayList<>();
        long currentY = first.getGeometry().getY() + first.getGeometry().getHeight() + gap;
        for (int i = 1; i < sorted.size() - 1; i++) {
            SlideShape s = sorted.get(i);
            ArrangeResult r = makeResult(s, s.getGeometry().getX(), currentY,
                    s.getGeometry().getWidth(), s.getGeometry().getHeight());
            if (r != null) results.add(r);
            currentY += s.getGeometry().getHeight() + gap;
        }
        return results;
    }

    // --- Match ---

    public static List<ArrangeResult> matchWidth(List<SlideShape> shapes, Integer anchorSpid) {
        long targetWidth = getAnchorOrFirst(shapes, anchorSpid).getGeometry().getWidth();
        return shapes.stream()
                .filter(s -> anchorSpid == null || s.getSpid() != anchorSpid)
                .map(s -> makeResult(s, s.getGeometry().getX(), s.getGeometry().getY(),
                        targetWidth, s.getGeometry().getHeight()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<ArrangeResult> matchHeight(List<SlideShape> shapes, Integer anchorSpid) {
        long targetHeight = getAnchorOrFirst(shapes, anchorSpid).getGeometry().getHeight();
        return shapes.stream()
                .filter(s -> anchorSpid == null || s.getSpid() != anchorSpid)
                .map(s -> makeResult(s, s.getGeometry().getX(), s.getGeometry().getY(),
                        s.getGeometry().getWidth(), targetHeight))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<ArrangeResult> matchSize(List<SlideShape> shapes, Integer anchorSpid) {
        ShapeGeometry anchor = getAnchorOrFirst(shapes, anchorSpid).getGeometry();
        long targetWidth = anchor.getWidth();
        long targetHeight = anchor.getHeight();
        return shapes.stream()
                .filter(s -> anchorSpid == null || s.getSpid() != anchorSpid)
                .map(s -> makeResult(s, s.getGeometry().getX(), s.getGeometry().getY(),
                        targetWidth, targetHeight))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // --- Convenience ---

    public static List<ArrangeResult> centerOnSlide(List<SlideShape> shapes) {
        return shapes.stream()
                .map(s -> makeResult(s,
                        (SLIDE_WIDTH - s.getGeometry().getWidth()) / 2,
                        (SLIDE_HEIGHT - s.getGeometry().getHeight()) / 2,
                        s.getGeometry().getWidth(), s.getGeometry().getHeight()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<ArrangeResult> snapToGrid(List<SlideShape> shapes) {
        return shapes.stream()
                .map(s -> {
                    long snappedX = Math.round((double) s.getGeometry().getX() / GRID_SNAP) * GRID_SNAP;
                    long snappedY = Math.round((double) s.getGeometry().getY() / GRID_SNAP) * GRID_SNAP;
                    return makeResult(s, snappedX, snappedY,
                            s.getGeometry().getWidth(), s.getGeometry().getHeight());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // --- Dispatcher ---

    public static List<ArrangeResult> execute(String operation, List<SlideShape> shapes, Integer anchorSpid) {
        return switch (operation) {
            case "align-left" -> alignLeft(shapes, anchorSpid);
            case "align-right" -> alignRight(shapes, anchorSpid);
            case "align-top" -> alignTop(shapes, anchorSpid);
            case "align-bottom" -> alignBottom(shapes, anchorSpid);
            case "align-center-h" -> alignCenterH(shapes, anchorSpid);
            case "align-center-v" -> alignCenterV(shapes, anchorSpid);
            case "distribute-h" -> distributeH(shapes);
            case "distribute-v" -> distributeV(shapes);
            case "match-width" -> matchWidth(shapes, anchorSpid);
            case "match-height" -> matchHeight(shapes, anchorSpid);
            case "match-size" -> matchSize(shapes, anchorSpid);
            case "center-on-slide" -> centerOnSlide(shapes);
            case "snap-to-grid" -> snapToGrid(shapes);
            default -> throw new IllegalArgumentException(
                    "Unknown operation: '" + operation + "'. Valid operations: "
                    + String.join(", ", VALID_OPERATIONS));
        };
    }

    // --- Internal helpers ---

    private static ArrangeResult makeResult(SlideShape shape, long x, long y, long width, long height) {
        ShapeGeometry original = shape.getGeometry();
        if (original.getX() == x && original.getY() == y
                && original.getWidth() == width && original.getHeight() == height) {
            return null;
        }
        ShapeGeometry updated = new ShapeGeometry(x, y, width, height);
        return new ArrangeResult(shape.getSpid(), original, updated);
    }

    private static SlideShape findAnchor(List<SlideShape> shapes, int anchorSpid) {
        return shapes.stream()
                .filter(s -> s.getSpid() == anchorSpid)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Anchor spid " + anchorSpid + " not found in provided shapes"));
    }

    private static SlideShape getAnchorOrFirst(List<SlideShape> shapes, Integer anchorSpid) {
        if (anchorSpid != null) {
            return findAnchor(shapes, anchorSpid);
        }
        return shapes.get(0);
    }

    private static void requireMinShapes(List<SlideShape> shapes, int min, String operation) {
        if (shapes.size() < min) {
            throw new IllegalArgumentException(
                    operation + " requires at least " + min + " shapes, got " + shapes.size());
        }
    }
}
