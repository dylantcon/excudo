package com.excudo.view.rendering.shapes;

import com.excudo.core.geometry.GeometryDefinition;
import com.excudo.core.geometry.GeometryPath;
import com.excudo.core.geometry.GeometryResolver;
import com.excudo.core.geometry.PresetGeometryRegistry;
import com.excudo.core.metrics.MeasuredText;
import com.excudo.core.metrics.TextBodyExtractor;
import com.excudo.core.metrics.TextMeasurer;
import com.excudo.core.model.*;
import com.excudo.view.rendering.*;
import com.excudo.core.rendering.lines.CompoundStroke;
import com.excudo.core.rendering.lines.LineEnd;
import com.excudo.core.rendering.lines.LineEndGeometry;
import com.excudo.core.rendering.lines.PathEnds;
import com.excudo.core.rendering.surface.RenderSurface;
import com.excudo.core.rendering.surface.StrokeCap;
import com.excudo.core.rendering.surface.StrokeJoin;
import com.excudo.core.rendering.surface.SurfacePaint;
import com.excudo.view.rendering.text.TextPainter;
import javafx.geometry.Rectangle2D;

import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders geometric shapes through the ECMA-376 geometry engine: every
 * preset resolves via {@link PresetGeometryRegistry} and inline custom
 * geometry via the parsed {@code a:custGeom} payload; both evaluate
 * through {@link GeometryResolver} into device-space paths. There is no
 * approximate path table and no bounding-box fallback -- an unknown
 * preset throws.
 *
 * <p>Transform order matches PowerPoint: the shape is mirrored about
 * its center first (a:xfrm/@flipH/@flipV), then rotated about the
 * center (@rot). Text is painted under the rotation but NOT under the
 * flip -- PowerPoint never mirrors text.
 */
public class GeometricShapeRenderer implements ModelShapeRenderer {

    /**
     * ShapeType enum presets that are not ECMA-376 names. Only reachable
     * for programmatically-built shapes whose geometry payload carries
     * no raw preset name; XML-parsed shapes always use the payload.
     */
    private static final Map<String, String> ENUM_PRESET_ALIASES = Map.of(
        "explosion1", "irregularSeal1",
        "explosion2", "irregularSeal2");

    /**
     * The geometry definition for a shape: parsed custGeom first, then
     * the raw prstGeom name from the XML, then the ShapeType's preset
     * (programmatic shapes), then the spec default -- {@code line} for
     * connectors (a p:cxnSp with no prstGeom is a straight connector),
     * {@code rect} for everything else, per ECMA-376.
     */
    private static GeometryDefinition definitionFor(SlideShape shape, ShapeGeometry geom) {
        if (geom.getCustomGeometry() != null) {
            return geom.getCustomGeometry();
        }
        String preset = geom.getPresetName();
        if (preset == null) {
            SlideShape.ShapeType type = shape.getType();
            if (type == SlideShape.ShapeType.CONNECTION) {
                preset = "line";
            } else {
                preset = type.hasOoxmlPreset() ? type.getOoxmlPreset() : "rect";
                preset = ENUM_PRESET_ALIASES.getOrDefault(preset, preset);
            }
        }
        return PresetGeometryRegistry.get(preset);
    }

    /** Replay a segment list into the surface's path accumulator. */
    private static void trace(RenderSurface surface, List<GeometryResolver.Segment> segments,
                              double ox, double oy) {
        surface.beginPath();
        for (GeometryResolver.Segment seg : segments) {
            switch (seg) {
                case GeometryResolver.Move m -> surface.moveTo(ox + m.x(), oy + m.y());
                case GeometryResolver.Line l -> surface.lineTo(ox + l.x(), oy + l.y());
                case GeometryResolver.Cubic c -> surface.bezierTo(
                    ox + c.x1(), oy + c.y1(), ox + c.x2(), oy + c.y2(),
                    ox + c.x3(), oy + c.y3());
                case GeometryResolver.Close ignored -> surface.closePath();
            }
        }
    }

    /** Replay an AWT area (compound-line rings) into the surface. */
    private static void traceArea(RenderSurface surface, Area area, double ox, double oy) {
        surface.beginPath();
        double[] c = new double[6];
        for (PathIterator it = area.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> surface.moveTo(ox + c[0], oy + c[1]);
                case PathIterator.SEG_LINETO -> surface.lineTo(ox + c[0], oy + c[1]);
                case PathIterator.SEG_QUADTO -> surface.quadTo(ox + c[0], oy + c[1],
                    ox + c[2], oy + c[3]);
                case PathIterator.SEG_CUBICTO -> surface.bezierTo(ox + c[0], oy + c[1],
                    ox + c[2], oy + c[3], ox + c[4], oy + c[5]);
                case PathIterator.SEG_CLOSE -> surface.closePath();
                default -> throw new IllegalStateException("unknown path segment");
            }
        }
    }

    // ========== stroke plan (trims, arrowheads, compound rings) ==========

    /** One stroked path; {@code fillsToo} tags paths the fill pass paints. */
    private record PlannedStroke(List<GeometryResolver.Segment> segments, boolean fillsToo) {}

    /**
     * Everything the stroke pass draws: the (possibly end-trimmed)
     * stroked paths plus the arrowhead decorations. Computed once and
     * replayed identically by the body and shadow passes.
     */
    private record StrokePlan(List<PlannedStroke> strokes,
                              List<LineEndGeometry.Decoration> decorations) {}

    /**
     * Trim head/tail arrowheads into the stroked paths. The head
     * attaches to the first stroked path, the tail to the last
     * (ECMA-376 line ends decorate the line's begin and end).
     */
    private static StrokePlan buildStrokePlan(GeometryResolver.ResolvedGeometry resolved,
                                              ShapeStyleExtractor.LineStyle line) {
        List<GeometryResolver.ResolvedPath> stroked = resolved.paths().stream()
            .filter(GeometryResolver.ResolvedPath::stroked).toList();
        List<PlannedStroke> strokes = new ArrayList<>(stroked.size());
        List<LineEndGeometry.Decoration> decorations = new ArrayList<>(2);
        for (int i = 0; i < stroked.size(); i++) {
            GeometryResolver.ResolvedPath p = stroked.get(i);
            List<GeometryResolver.Segment> segments = p.segments();
            if (line.hasEnds()) {
                LineEnd head = i == 0 ? line.headEnd() : LineEnd.NONE;
                LineEnd tail = i == stroked.size() - 1 ? line.tailEnd() : LineEnd.NONE;
                PathEnds.Applied applied = PathEnds.apply(segments, head, tail,
                    line.widthPixels());
                segments = applied.segments();
                if (applied.head() != null) decorations.add(applied.head());
                if (applied.tail() != null) decorations.add(applied.tail());
            }
            strokes.add(new PlannedStroke(segments,
                p.fill() != GeometryPath.FillMode.NONE));
        }
        return new StrokePlan(strokes, decorations);
    }

    /**
     * Draw a stroke plan in the given paint: dashed/capped/joined
     * strokes (or compound rings), then arrowhead decorations -- filled
     * polygons, except the open-arrow arms which stroke at the line
     * width with round caps and joins. {@code skipFilledPaths} lets the
     * shadow pass omit outlines whose area its fill copy already covers.
     */
    private static void drawStrokePlan(RenderSurface surface, StrokePlan plan,
                                       ShapeStyleExtractor.LineStyle line, SurfacePaint paint,
                                       double ox, double oy, boolean skipFilledPaths) {
        surface.setStroke(paint);
        surface.setLineWidth(line.widthPixels());
        surface.setLineCap(line.cap());
        surface.setLineJoin(line.join());
        surface.setMiterLimit(line.miterLimit());
        boolean compound = CompoundStroke.isCompound(line.compound());

        for (PlannedStroke stroke : plan.strokes()) {
            if (skipFilledPaths && stroke.fillsToo()) continue;
            if (compound) {
                Area rings = CompoundStroke.rings(stroke.segments(),
                    line.widthPixels(), line.compound());
                if (rings != null) {
                    surface.setFill(paint);
                    traceArea(surface, rings, ox, oy);
                    surface.fillPath();
                    continue;
                }
                // unsupported cmpd/path combination: plain stroke below
            }
            if (line.dashPattern() != null) {
                surface.setLineDashes(line.dashPattern());
            }
            trace(surface, stroke.segments(), ox, oy);
            surface.strokePath();
            if (line.dashPattern() != null) {
                surface.setLineDashes((double[]) null);
            }
        }

        for (LineEndGeometry.Decoration decoration : plan.decorations()) {
            if (decoration.strokedArms()) {
                surface.setLineCap(StrokeCap.ROUND);
                surface.setLineJoin(StrokeJoin.ROUND);
                trace(surface, decoration.outline(), ox, oy);
                surface.strokePath();
                surface.setLineCap(line.cap());
                surface.setLineJoin(line.join());
            } else {
                surface.setFill(paint);
                trace(surface, decoration.outline(), ox, oy);
                surface.fillPath();
            }
        }
    }

    /** Opacity of the paint the shadow strength scales with. */
    private static double paintOpacity(SurfacePaint paint) {
        return switch (paint) {
            case SurfacePaint.Solid s -> s.alpha() / 255.0;
            case SurfacePaint.LinearGradient lg -> avgStopOpacity(lg.stops());
            case SurfacePaint.RadialGradient rg -> avgStopOpacity(rg.stops());
            case SurfacePaint.Transparent ignored -> 0.0;
            default -> 1.0; // pattern/picture fills read as opaque content
        };
    }

    private static double avgStopOpacity(List<SurfacePaint.LinearGradient.Stop> stops) {
        if (stops.isEmpty()) return 1.0;
        double sum = 0;
        for (SurfacePaint.LinearGradient.Stop s : stops) sum += s.color().alpha();
        return sum / (stops.size() * 255.0);
    }

    /**
     * One shadow copy at the given origin: fillable paths fill when the
     * shape has a fill; the stroke plan (dashes, trims, arrowheads)
     * replays when the outline is visible -- each in its opacity-scaled
     * shadow color.
     */
    private static void shadowCopy(RenderSurface surface,
                                   GeometryResolver.ResolvedGeometry resolved,
                                   StrokePlan plan,
                                   SurfacePaint.Solid fillShadow,
                                   SurfacePaint.Solid strokeShadow, boolean hasFill,
                                   ShapeStyleExtractor.LineStyle line,
                                   double ox, double oy) {
        if (hasFill && fillShadow.alpha() > 0) {
            surface.setFill(fillShadow);
            for (GeometryResolver.ResolvedPath p : resolved.paths()) {
                if (p.fill() != GeometryPath.FillMode.NONE) {
                    trace(surface, p.segments(), ox, oy);
                    surface.fillPath();
                }
            }
        }
        if (plan != null && strokeShadow.alpha() > 0) {
            // Skip outlines whose area the fill shadow already covers.
            drawStrokePlan(surface, plan, line, strokeShadow, ox, oy, hasFill);
        }
    }

    /**
     * Mirror about the shape center. Composed BEFORE rotation on the
     * surface (surface transforms concatenate), so drawing commands are
     * flipped first, then rotated -- PowerPoint's order.
     */
    private static void applyFlip(RenderSurface surface, Rectangle2D bounds,
                                  boolean flipH, boolean flipV) {
        double cx = bounds.getMinX() + bounds.getWidth() / 2;
        double cy = bounds.getMinY() + bounds.getHeight() / 2;
        surface.translate(cx, cy);
        surface.scale(flipH ? -1 : 1, flipV ? -1 : 1);
        surface.translate(-cx, -cy);
    }

    /**
     * Derive the paint for a path's fill mode from the shape fill.
     * PowerPoint applies these facet modes as plain sRGB byte
     * arithmetic -- NOT the linearized tint/shade math of a:tint/
     * a:shade color modifiers. Calibrated from the preset-shapes-basic
     * ground truth (bevel facets over the themed gradient): darken is
     * c*0.6 and lighten is c*0.6 + 0.4*255 per channel, exact to +/-1
     * across every sample; the -Less variants keep 0.8. Gradient stops
     * transform per-stop; pattern and picture fills pass through
     * unchanged (no preset references them from a modified path).
     */
    private static SurfacePaint deriveFill(SurfacePaint paint, GeometryPath.FillMode mode) {
        if (mode == GeometryPath.FillMode.NORM) return paint;
        return switch (paint) {
            case SurfacePaint.Solid s -> modifySolid(s, mode);
            case SurfacePaint.LinearGradient lg -> new SurfacePaint.LinearGradient(
                lg.startX(), lg.startY(), lg.endX(), lg.endY(), modifyStops(lg.stops(), mode));
            case SurfacePaint.RadialGradient rg -> new SurfacePaint.RadialGradient(
                rg.centerX(), rg.centerY(), rg.focusX(), rg.focusY(), rg.geometry(),
                modifyStops(rg.stops(), mode));
            default -> paint;
        };
    }

    private static List<SurfacePaint.LinearGradient.Stop> modifyStops(
            List<SurfacePaint.LinearGradient.Stop> stops, GeometryPath.FillMode mode) {
        List<SurfacePaint.LinearGradient.Stop> out = new ArrayList<>(stops.size());
        for (SurfacePaint.LinearGradient.Stop stop : stops) {
            out.add(new SurfacePaint.LinearGradient.Stop(
                stop.position(), modifySolid(stop.color(), mode)));
        }
        return out;
    }

    private static SurfacePaint.Solid modifySolid(SurfacePaint.Solid s,
                                                  GeometryPath.FillMode mode) {
        // keep of the base color, blended toward white (lighten) or
        // black (darken) in sRGB byte space -- see deriveFill's javadoc.
        double keep = switch (mode) {
            case LIGHTEN, DARKEN -> 0.6;
            case LIGHTEN_LESS, DARKEN_LESS -> 0.8;
            case NORM, NONE -> 1.0; // unreachable: filtered by callers
        };
        if (keep == 1.0) return s;
        boolean towardWhite = mode == GeometryPath.FillMode.LIGHTEN
            || mode == GeometryPath.FillMode.LIGHTEN_LESS;
        double add = towardWhite ? 255 * (1 - keep) : 0;
        int r = (int) Math.round(s.red() * keep + add);
        int g = (int) Math.round(s.green() * keep + add);
        int b = (int) Math.round(s.blue() * keep + add);
        // keep the original alpha byte
        return new SurfacePaint.Solid((s.argb() & 0xFF000000) | (r << 16) | (g << 8) | b);
    }

    @Override
    public void render(SlideShape shape, RenderingContext ctx, SlideRenderContext slideCtx) {
        ShapeGeometry geom = shape.getGeometry();
        // A zero extent along ONE axis is a valid line shape (every
        // horizontal/vertical connector python-pptx authors has cy=0 or
        // cx=0) -- its stroke must still paint. Only a fully collapsed
        // box draws nothing.
        if (geom == null || geom.getWidth() < 0 || geom.getHeight() < 0
            || (geom.getWidth() == 0 && geom.getHeight() == 0)) return;

        CoordinateMapper mapper = ctx.getZoomedCoordinateMapper();
        Rectangle2D bounds = mapper.mapToCanvas(geom.getX(), geom.getY(),
            geom.getWidth(), geom.getHeight());

        RenderSurface surface = ctx.getSurface();

        // Apply rotation around shape center
        double rotDeg = geom.getRotationDegrees();
        if (rotDeg != 0) {
            double cx = bounds.getMinX() + bounds.getWidth() / 2;
            double cy = bounds.getMinY() + bounds.getHeight() / 2;
            surface.save();
            surface.translate(cx, cy);
            surface.rotate(rotDeg);
            surface.translate(-cx, -cy);
        }

        SurfacePaint surfaceFill = ShapeStyleExtractor.resolveFillColor(shape, slideCtx, surface);
        ShapeStyleExtractor.LineStyle line = ShapeStyleExtractor.resolveLineStyle(shape, slideCtx);
        boolean hasFill = surfaceFill != SurfacePaint.Transparent.INSTANCE;

        // Connectors flow through the geometry engine like any shape:
        // their presets (line, bentConnector2..5, curvedConnector2..5)
        // are in the catalogue and their stroke-only pathLst plus the
        // fillRef idx=0 no-fill handles the rest.
        GeometryDefinition def = definitionFor(shape, geom);
        GeometryResolver.ResolvedGeometry resolved = GeometryResolver.resolve(
            def, geom.getAdjustValues(), bounds.getWidth(), bounds.getHeight());
        double ox = bounds.getMinX(), oy = bounds.getMinY();
        boolean flip = geom.isFlipH() || geom.isFlipV();

        StrokePlan plan = line.isVisible() ? buildStrokePlan(resolved, line) : null;

        // Shadow pass: the shadow follows what the shape actually
        // paints -- the filled silhouette when there is a fill, and the
        // stroked outline when there is only a line (a noFill triangle
        // shadows its border, not its interior). Offset in unflipped
        // device space: the offset direction must not mirror.
        ShapeStyleExtractor.ShadowStyle shadow = ShapeStyleExtractor.resolveShadow(shape, slideCtx);
        if (shadow != null && (hasFill || line.isVisible())) {
            // PowerPoint derives the shadow from the shape's RENDERED
            // alpha: a 25%-alpha fill casts a 25%-strength shadow
            // (verified against the fills-solid-theme ground truth).
            double baseAlpha = shadow.color().alpha() / 255.0;
            double fillTarget = baseAlpha * paintOpacity(surfaceFill);
            double strokeTarget = baseAlpha * line.color().alpha() / 255.0;

            surface.save();
            surface.translate(shadow.offsetX(), shadow.offsetY());
            if (flip) applyFlip(surface, bounds, geom.isFlipH(), geom.isFlipV());
            double blur = shadow.blurPx();
            if (blur <= 0.5) {
                shadowCopy(surface, resolved, plan,
                    shadow.color().withAlpha(fillTarget),
                    shadow.color().withAlpha(strokeTarget), hasFill, line, ox, oy);
            } else {
                // Draw the hard silhouette into a blur layer; the
                // backend composites it back through the gaussian.
                surface.beginBlurLayer(blur);
                shadowCopy(surface, resolved, plan,
                    shadow.color().withAlpha(fillTarget),
                    shadow.color().withAlpha(strokeTarget), hasFill, line, ox, oy);
                surface.endBlurLayer();
            }
            surface.restore();
        }

        // Body: ALL fillable paths first (definition order -- action
        // buttons and 3D-ish presets layer norm/lighten/darken back to
        // front), then ALL strokes on top. PowerPoint never buries a
        // stroke-only sub-path under a later fill (chartStar's internal
        // lines sit over its filled square in the truth render).
        if (flip) {
            surface.save();
            applyFlip(surface, bounds, geom.isFlipH(), geom.isFlipV());
        }
        if (hasFill) {
            for (GeometryResolver.ResolvedPath p : resolved.paths()) {
                if (p.fill() != GeometryPath.FillMode.NONE) {
                    surface.setFill(deriveFill(surfaceFill, p.fill()));
                    trace(surface, p.segments(), ox, oy);
                    surface.fillPath();
                }
            }
        }
        if (plan != null) {
            drawStrokePlan(surface, plan, line, line.color(), ox, oy, false);
        }
        if (flip) {
            surface.restore();
        }

        // Paint text if the shape has any. Text sits under the rotation
        // transform but NOT under the flip: PowerPoint mirrors geometry,
        // never glyphs.
        if (shape.hasText() && shape.getXmlElement() != null) {
            try {
                TextBody textBody = TextBodyExtractor.extractFromShape(shape.getXmlElement());
                if (textBody != null && !textBody.getParagraphs().isEmpty()) {
                    long widthEmu = geom.getWidth();
                    // Non-placeholder shapes inherit from their own
                    // lstStyle + presentation defaultTextStyle. The same
                    // source feeds measurement and painting so the two
                    // can never disagree.
                    com.excudo.core.metrics.TextStyleSource styles =
                        com.excudo.view.rendering.text.LstStyleResolver.forShape(
                            slideCtx, null, null, shape.getXmlElement());
                    MeasuredText measured = TextMeasurer.measure(textBody, widthEmu, styles);
                    TextPainter.paint(textBody, measured, bounds, ctx, slideCtx, null, styles);
                }
            } catch (Exception e) {
                // Non-critical -- shape renders, text doesn't
            }
        }

        // Restore graphics state after rotation
        if (rotDeg != 0) {
            surface.restore();
        }
    }

    @Override
    public boolean canRender(SlideShape.ShapeType type) {
        // Catch-all for geometric shapes. GROUP shapes are not rendered directly --
        // their children are already in the flat registry with transformed coordinates.
        return type != SlideShape.ShapeType.PLACEHOLDER
            && type != SlideShape.ShapeType.PICTURE
            && type != SlideShape.ShapeType.GROUP;
    }
}
