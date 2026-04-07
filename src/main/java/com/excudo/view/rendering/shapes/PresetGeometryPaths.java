package com.excudo.view.rendering.shapes;

import javafx.scene.canvas.GraphicsContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps OOXML preset geometry names to JavaFX Canvas path drawing functions.
 * Covers the most commonly used shapes; unknown presets fall back to bounding-box rect.
 *
 * Each drawer traces a path using gc.beginPath()/moveTo()/lineTo()/etc. but does NOT
 * call fill() or stroke() -- the caller handles that.
 */
public final class PresetGeometryPaths {

    @FunctionalInterface
    public interface ShapePathDrawer {
        void draw(GraphicsContext gc, double x, double y, double w, double h);
    }

    private static final Map<String, ShapePathDrawer> PATHS = new HashMap<>();

    static {
        // --- Polygons ---
        PATHS.put("triangle", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0.5,0, 1,1, 0,1}));

        PATHS.put("isosTriangle", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0.5,0, 1,1, 0,1}));

        PATHS.put("rtTriangle", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0,0, 1,1, 0,1}));

        PATHS.put("diamond", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0.5,0, 1,0.5, 0.5,1, 0,0.5}));

        PATHS.put("parallelogram", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0.25,0, 1,0, 0.75,1, 0,1}));

        PATHS.put("trapezoid", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0.2,0, 0.8,0, 1,1, 0,1}));

        PATHS.put("pentagon", (gc, x, y, w, h) -> drawRegularPolygon(gc, x, y, w, h, 5, -Math.PI / 2));
        PATHS.put("hexagon", (gc, x, y, w, h) -> drawRegularPolygon(gc, x, y, w, h, 6, 0));
        PATHS.put("heptagon", (gc, x, y, w, h) -> drawRegularPolygon(gc, x, y, w, h, 7, -Math.PI / 2));
        PATHS.put("octagon", (gc, x, y, w, h) -> drawRegularPolygon(gc, x, y, w, h, 8, Math.PI / 8));
        PATHS.put("decagon", (gc, x, y, w, h) -> drawRegularPolygon(gc, x, y, w, h, 10, -Math.PI / 2));
        PATHS.put("dodecagon", (gc, x, y, w, h) -> drawRegularPolygon(gc, x, y, w, h, 12, -Math.PI / 2));

        // --- Arrows ---
        // Arrow shaft = 40% height centered, head = full height
        PATHS.put("rightArrow", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{
                0,0.3, 0.7,0.3, 0.7,0, 1,0.5, 0.7,1, 0.7,0.7, 0,0.7}));

        PATHS.put("leftArrow", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{
                0,0.5, 0.3,0, 0.3,0.3, 1,0.3, 1,0.7, 0.3,0.7, 0.3,1}));

        PATHS.put("upArrow", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{
                0.5,0, 1,0.3, 0.7,0.3, 0.7,1, 0.3,1, 0.3,0.3, 0,0.3}));

        PATHS.put("downArrow", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{
                0.3,0, 0.7,0, 0.7,0.7, 1,0.7, 0.5,1, 0,0.7, 0.3,0.7}));

        PATHS.put("leftRightArrow", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{
                0,0.5, 0.2,0, 0.2,0.3, 0.8,0.3, 0.8,0, 1,0.5, 0.8,1, 0.8,0.7, 0.2,0.7, 0.2,1}));

        PATHS.put("upDownArrow", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{
                0.5,0, 1,0.2, 0.7,0.2, 0.7,0.8, 1,0.8, 0.5,1, 0,0.8, 0.3,0.8, 0.3,0.2, 0,0.2}));

        PATHS.put("notchedRightArrow", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{
                0,0.3, 0.7,0.3, 0.7,0, 1,0.5, 0.7,1, 0.7,0.7, 0,0.7, 0.1,0.5}));

        PATHS.put("chevron", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{
                0,0, 0.8,0, 1,0.5, 0.8,1, 0,1, 0.2,0.5}));

        PATHS.put("homePlate", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{
                0,0, 0.85,0, 1,0.5, 0.85,1, 0,1}));

        // --- Stars ---
        PATHS.put("star4", (gc, x, y, w, h) -> drawStar(gc, x, y, w, h, 4, 0.4));
        PATHS.put("star5", (gc, x, y, w, h) -> drawStar(gc, x, y, w, h, 5, 0.4));
        PATHS.put("star6", (gc, x, y, w, h) -> drawStar(gc, x, y, w, h, 6, 0.4));
        PATHS.put("star7", (gc, x, y, w, h) -> drawStar(gc, x, y, w, h, 7, 0.4));
        PATHS.put("star8", (gc, x, y, w, h) -> drawStar(gc, x, y, w, h, 8, 0.4));
        PATHS.put("star10", (gc, x, y, w, h) -> drawStar(gc, x, y, w, h, 10, 0.4));
        PATHS.put("star12", (gc, x, y, w, h) -> drawStar(gc, x, y, w, h, 12, 0.4));
        PATHS.put("star16", (gc, x, y, w, h) -> drawStar(gc, x, y, w, h, 16, 0.4));
        PATHS.put("star24", (gc, x, y, w, h) -> drawStar(gc, x, y, w, h, 24, 0.4));
        PATHS.put("star32", (gc, x, y, w, h) -> drawStar(gc, x, y, w, h, 32, 0.4));

        // --- Flowchart ---
        PATHS.put("flowChartProcess", null); // rect -- handled by default
        PATHS.put("flowChartDecision", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0.5,0, 1,0.5, 0.5,1, 0,0.5}));

        PATHS.put("flowChartTerminator", (gc, x, y, w, h) -> {
            // Stadium/pill shape: rect with semicircle ends
            double r = h / 2;
            gc.beginPath();
            gc.moveTo(x + r, y);
            gc.lineTo(x + w - r, y);
            gc.arc(x + w - r, y + r, r, r, 90, -180);
            gc.lineTo(x + r, y + h);
            gc.arc(x + r, y + r, r, r, 270, -180);
            gc.closePath();
        });

        PATHS.put("flowChartInputOutput", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0.2,0, 1,0, 0.8,1, 0,1}));

        PATHS.put("flowChartPredefinedProcess", (gc, x, y, w, h) -> {
            // Rect with vertical lines at 10% from each side
            gc.beginPath();
            gc.moveTo(x, y); gc.lineTo(x + w, y); gc.lineTo(x + w, y + h);
            gc.lineTo(x, y + h); gc.closePath();
            // The inner lines are drawn separately as part of stroke
        });

        PATHS.put("flowChartDocument", (gc, x, y, w, h) -> {
            // Rect with wavy bottom
            gc.beginPath();
            gc.moveTo(x, y);
            gc.lineTo(x + w, y);
            gc.lineTo(x + w, y + h * 0.85);
            gc.bezierCurveTo(x + w * 0.75, y + h, x + w * 0.5, y + h * 0.7,
                x + w * 0.25, y + h * 0.85);
            gc.bezierCurveTo(x + w * 0.1, y + h * 0.9, x, y + h * 0.95, x, y + h * 0.85);
            gc.closePath();
        });

        PATHS.put("flowChartManualInput", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0,0.2, 1,0, 1,1, 0,1}));

        PATHS.put("flowChartPreparation", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0.2,0, 0.8,0, 1,0.5, 0.8,1, 0.2,1, 0,0.5}));

        PATHS.put("flowChartExtract", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0.5,0, 1,1, 0,1}));

        PATHS.put("flowChartMerge", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0,0, 1,0, 0.5,1}));

        PATHS.put("flowChartOffpageConnector", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0,0, 1,0, 1,0.7, 0.5,1, 0,0.7}));

        PATHS.put("flowChartConnector", null); // ellipse -- delegate to "ellipse" case

        // --- Misc common shapes ---
        PATHS.put("heart", (gc, x, y, w, h) -> {
            gc.beginPath();
            gc.moveTo(x + w * 0.5, y + h * 0.3);
            gc.bezierCurveTo(x + w * 0.5, y, x, y, x, y + h * 0.35);
            gc.bezierCurveTo(x, y + h * 0.65, x + w * 0.5, y + h * 0.85, x + w * 0.5, y + h);
            gc.bezierCurveTo(x + w * 0.5, y + h * 0.85, x + w, y + h * 0.65, x + w, y + h * 0.35);
            gc.bezierCurveTo(x + w, y, x + w * 0.5, y, x + w * 0.5, y + h * 0.3);
            gc.closePath();
        });

        PATHS.put("plus", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{
                0.35,0, 0.65,0, 0.65,0.35, 1,0.35, 1,0.65, 0.65,0.65,
                0.65,1, 0.35,1, 0.35,0.65, 0,0.65, 0,0.35, 0.35,0.35}));

        PATHS.put("cross", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{
                0.35,0, 0.65,0, 0.65,0.35, 1,0.35, 1,0.65, 0.65,0.65,
                0.65,1, 0.35,1, 0.35,0.65, 0,0.65, 0,0.35, 0.35,0.35}));

        PATHS.put("frame", (gc, x, y, w, h) -> {
            // Outer rect - inner rect (12.5% border)
            double b = Math.min(w, h) * 0.125;
            gc.beginPath();
            // Outer clockwise
            gc.moveTo(x, y); gc.lineTo(x + w, y); gc.lineTo(x + w, y + h); gc.lineTo(x, y + h); gc.closePath();
            // Inner counter-clockwise (creates hole via even-odd rule)
            gc.moveTo(x + b, y + b); gc.lineTo(x + b, y + h - b);
            gc.lineTo(x + w - b, y + h - b); gc.lineTo(x + w - b, y + b); gc.closePath();
        });

        PATHS.put("donut", null); // Special case -- two ellipses, needs custom rendering

        PATHS.put("cube", (gc, x, y, w, h) -> {
            double d = Math.min(w, h) * 0.2;
            gc.beginPath();
            // Front face
            gc.moveTo(x, y + d); gc.lineTo(x + w - d, y + d);
            gc.lineTo(x + w - d, y + h); gc.lineTo(x, y + h); gc.closePath();
            // Top face
            gc.moveTo(x, y + d); gc.lineTo(x + d, y); gc.lineTo(x + w, y);
            gc.lineTo(x + w - d, y + d); gc.closePath();
            // Right face
            gc.moveTo(x + w - d, y + d); gc.lineTo(x + w, y); gc.lineTo(x + w, y + h - d);
            gc.lineTo(x + w - d, y + h); gc.closePath();
        });

        PATHS.put("can", (gc, x, y, w, h) -> {
            // Cylinder: rect body with ellipse top and bottom
            double ey = h * 0.1; // ellipse height
            gc.beginPath();
            gc.moveTo(x, y + ey);
            gc.lineTo(x, y + h - ey);
            gc.bezierCurveTo(x, y + h, x + w, y + h, x + w, y + h - ey);
            gc.lineTo(x + w, y + ey);
            gc.bezierCurveTo(x + w, y, x, y, x, y + ey);
            gc.closePath();
        });

        // Explosion/Burst shapes
        PATHS.put("irregularSeal1", (gc, x, y, w, h) -> drawStar(gc, x, y, w, h, 8, 0.3));
        PATHS.put("irregularSeal2", (gc, x, y, w, h) -> drawStar(gc, x, y, w, h, 12, 0.3));

        // --- Callouts ---
        PATHS.put("wedgeRectCallout", (gc, x, y, w, h) -> {
            gc.beginPath();
            gc.moveTo(x, y); gc.lineTo(x + w, y); gc.lineTo(x + w, y + h * 0.75);
            gc.lineTo(x + w * 0.55, y + h * 0.75);
            gc.lineTo(x + w * 0.1, y + h); // callout point
            gc.lineTo(x + w * 0.45, y + h * 0.75);
            gc.lineTo(x, y + h * 0.75); gc.closePath();
        });

        PATHS.put("wedgeRoundRectCallout", (gc, x, y, w, h) -> {
            double r = Math.min(w, h) * 0.08;
            double bodyH = h * 0.75;
            gc.beginPath();
            gc.moveTo(x + r, y);
            gc.lineTo(x + w - r, y);
            gc.arcTo(x + w, y, x + w, y + r, r);
            gc.lineTo(x + w, y + bodyH - r);
            gc.arcTo(x + w, y + bodyH, x + w - r, y + bodyH, r);
            gc.lineTo(x + w * 0.55, y + bodyH);
            gc.lineTo(x + w * 0.1, y + h);
            gc.lineTo(x + w * 0.45, y + bodyH);
            gc.lineTo(x + r, y + bodyH);
            gc.arcTo(x, y + bodyH, x, y + bodyH - r, r);
            gc.lineTo(x, y + r);
            gc.arcTo(x, y, x + r, y, r);
            gc.closePath();
        });

        PATHS.put("cloudCallout", (gc, x, y, w, h) ->
            drawPolygon(gc, x, y, w, h, new double[]{0.5,0, 1,0.5, 0.5,1, 0,0.5}));

        // --- Action buttons ---
        PATHS.put("actionButtonHome", (gc, x, y, w, h) -> drawRectPath(gc, x, y, w, h));
        PATHS.put("actionButtonHelp", (gc, x, y, w, h) -> drawRectPath(gc, x, y, w, h));
        PATHS.put("actionButtonBlank", (gc, x, y, w, h) -> drawRectPath(gc, x, y, w, h));

        // Ribbon shapes
        PATHS.put("ribbon", (gc, x, y, w, h) -> {
            gc.beginPath();
            gc.moveTo(x, y + h * 0.3); gc.lineTo(x + w * 0.1, y + h * 0.15);
            gc.lineTo(x + w * 0.1, y); gc.lineTo(x + w * 0.9, y);
            gc.lineTo(x + w * 0.9, y + h * 0.15); gc.lineTo(x + w, y + h * 0.3);
            gc.lineTo(x + w * 0.9, y + h * 0.45); gc.lineTo(x + w * 0.9, y + h);
            gc.lineTo(x + w * 0.5, y + h * 0.85);
            gc.lineTo(x + w * 0.1, y + h); gc.lineTo(x + w * 0.1, y + h * 0.45);
            gc.closePath();
        });

        PATHS.put("ribbon2", (gc, x, y, w, h) -> {
            gc.beginPath();
            gc.moveTo(x, y); gc.lineTo(x + w * 0.1, y + h * 0.15);
            gc.lineTo(x + w * 0.1, y + h * 0.3); gc.lineTo(x + w * 0.9, y + h * 0.3);
            gc.lineTo(x + w * 0.9, y + h * 0.15); gc.lineTo(x + w, y);
            gc.lineTo(x + w * 0.9, y + h * 0.6); gc.lineTo(x + w * 0.9, y + h);
            gc.lineTo(x + w * 0.5, y + h * 0.85);
            gc.lineTo(x + w * 0.1, y + h); gc.lineTo(x + w * 0.1, y + h * 0.6);
            gc.closePath();
        });
    }

    /**
     * Get the path drawer for a preset name, or null if not mapped (use rect fallback).
     */
    public static ShapePathDrawer get(String presetName) {
        if (!PATHS.containsKey(presetName)) return null;
        return PATHS.get(presetName); // may be null for shapes that delegate to existing cases
    }

    // ========== HELPERS ==========

    /**
     * Draw a polygon from normalized coordinates (0-1 range) scaled to bounds.
     * coords = [x0,y0, x1,y1, x2,y2, ...]
     */
    private static void drawPolygon(GraphicsContext gc, double x, double y, double w, double h,
                                     double[] coords) {
        gc.beginPath();
        gc.moveTo(x + coords[0] * w, y + coords[1] * h);
        for (int i = 2; i < coords.length; i += 2) {
            gc.lineTo(x + coords[i] * w, y + coords[i + 1] * h);
        }
        gc.closePath();
    }

    /**
     * Draw a regular n-sided polygon inscribed in the bounding box.
     */
    private static void drawRegularPolygon(GraphicsContext gc, double x, double y,
                                            double w, double h, int sides, double startAngle) {
        double cx = x + w / 2;
        double cy = y + h / 2;
        double rx = w / 2;
        double ry = h / 2;

        gc.beginPath();
        for (int i = 0; i < sides; i++) {
            double angle = startAngle + i * 2 * Math.PI / sides;
            double px = cx + rx * Math.cos(angle);
            double py = cy + ry * Math.sin(angle);
            if (i == 0) gc.moveTo(px, py);
            else gc.lineTo(px, py);
        }
        gc.closePath();
    }

    /**
     * Draw a star with n points and given inner radius ratio.
     */
    private static void drawStar(GraphicsContext gc, double x, double y,
                                  double w, double h, int points, double innerRatio) {
        double cx = x + w / 2;
        double cy = y + h / 2;
        double outerRx = w / 2;
        double outerRy = h / 2;
        double innerRx = outerRx * innerRatio;
        double innerRy = outerRy * innerRatio;
        double startAngle = -Math.PI / 2;

        gc.beginPath();
        for (int i = 0; i < points * 2; i++) {
            double angle = startAngle + i * Math.PI / points;
            boolean outer = (i % 2 == 0);
            double rx = outer ? outerRx : innerRx;
            double ry = outer ? outerRy : innerRy;
            double px = cx + rx * Math.cos(angle);
            double py = cy + ry * Math.sin(angle);
            if (i == 0) gc.moveTo(px, py);
            else gc.lineTo(px, py);
        }
        gc.closePath();
    }

    /**
     * Simple rect path (for action buttons etc. that are just rectangles).
     */
    private static void drawRectPath(GraphicsContext gc, double x, double y, double w, double h) {
        gc.beginPath();
        gc.moveTo(x, y); gc.lineTo(x + w, y); gc.lineTo(x + w, y + h);
        gc.lineTo(x, y + h); gc.closePath();
    }

    private PresetGeometryPaths() {}
}
