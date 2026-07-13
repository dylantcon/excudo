package com.excudo.view.rendering.shapes;

import com.excudo.core.color.ColorTransforms;
import com.excudo.core.geometry.GeometryDefinition;
import com.excudo.core.geometry.GeometryPath;
import com.excudo.core.geometry.GeometryResolver;
import com.excudo.core.geometry.PresetGeometryRegistry;
import com.excudo.core.metrics.MeasuredText;
import com.excudo.core.metrics.TextBodyExtractor;
import com.excudo.core.metrics.TextMeasurer;
import com.excudo.core.model.*;
import com.excudo.view.rendering.*;
import com.excudo.core.rendering.surface.RenderSurface;
import com.excudo.core.rendering.surface.SurfacePaint;
import com.excudo.view.rendering.text.TextPainter;
import javafx.geometry.Rectangle2D;

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
     * Endpoints of a straight connector within its bounding box, honoring
     * flipH/flipV. A connector runs along one diagonal of the box; the flip
     * flags pick which. Since no arrowhead is drawn, only the diagonal matters:
     * {@code flipH == flipV} -> the main diagonal {@code (minX,minY)-(maxX,maxY)};
     * exactly one flag set -> the anti-diagonal {@code (minX,maxY)-(maxX,minY)}.
     * Returns {@code {x1, y1, x2, y2}}.
     */
    static double[] connectorEndpoints(double minX, double minY, double maxX, double maxY,
                                       boolean flipH, boolean flipV) {
        if (flipH ^ flipV) {
            return new double[]{minX, maxY, maxX, minY}; // anti-diagonal (BL->TR)
        }
        return new double[]{minX, minY, maxX, maxY};     // main diagonal (TL->BR)
    }

    /** Apply line color, width, and dash pattern to the surface. */
    private static void applyLineStyle(RenderSurface surface, ShapeStyleExtractor.LineStyle line) {
        surface.setStroke(line.color());
        surface.setLineWidth(line.widthPixels());
        if (line.dashPattern() != null) {
            surface.setLineDashes(line.dashPattern());
        }
    }

    /**
     * The geometry definition for a shape: parsed custGeom first, then
     * the raw prstGeom name from the XML, then the ShapeType's preset
     * (programmatic shapes), then the spec default {@code rect} (shapes
     * with no geometry element at all, per ECMA-376).
     */
    private static GeometryDefinition definitionFor(SlideShape shape, ShapeGeometry geom) {
        if (geom.getCustomGeometry() != null) {
            return geom.getCustomGeometry();
        }
        String preset = geom.getPresetName();
        if (preset == null) {
            SlideShape.ShapeType type = shape.getType();
            preset = type.hasOoxmlPreset() ? type.getOoxmlPreset() : "rect";
            preset = ENUM_PRESET_ALIASES.getOrDefault(preset, preset);
        }
        return PresetGeometryRegistry.get(preset);
    }

    /** Replay a resolved path into the surface's path accumulator. */
    private static void trace(RenderSurface surface, GeometryResolver.ResolvedPath path,
                              double ox, double oy) {
        surface.beginPath();
        for (GeometryResolver.Segment seg : path.segments()) {
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
     * shape has a fill; stroked paths stroke at the line's width when
     * the outline is visible -- each in its opacity-scaled shadow color.
     */
    private static void shadowCopy(RenderSurface surface,
                                   GeometryResolver.ResolvedGeometry resolved,
                                   SurfacePaint.Solid fillShadow,
                                   SurfacePaint.Solid strokeShadow, boolean hasFill,
                                   ShapeStyleExtractor.LineStyle line,
                                   double ox, double oy) {
        if (hasFill && fillShadow.alpha() > 0) {
            surface.setFill(fillShadow);
            for (GeometryResolver.ResolvedPath p : resolved.paths()) {
                if (p.fill() != GeometryPath.FillMode.NONE) {
                    trace(surface, p, ox, oy);
                    surface.fillPath();
                }
            }
        }
        if (line.isVisible() && strokeShadow.alpha() > 0) {
            surface.setStroke(strokeShadow);
            surface.setLineWidth(line.widthPixels());
            for (GeometryResolver.ResolvedPath p : resolved.paths()) {
                // Skip paths whose area the fill shadow already covers.
                if (p.stroked() && !(hasFill && p.fill() != GeometryPath.FillMode.NONE)) {
                    trace(surface, p, ox, oy);
                    surface.strokePath();
                }
            }
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
     * lighten/darken blend toward white / scale toward black in
     * linearized sRGB via {@link ColorTransforms} (the PowerPoint-
     * calibrated tint/shade math); the -Less variants keep 80% instead
     * of 60%. Gradient stops transform per-stop; pattern and picture
     * fills pass through unchanged (no preset references them from a
     * modified path).
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
        // tint v keeps v of the color and blends (1-v) toward white;
        // shade v scales the linear channels by v.
        ColorTransforms.Modifier modifier = switch (mode) {
            case LIGHTEN -> new ColorTransforms.Modifier("tint", 60000);
            case LIGHTEN_LESS -> new ColorTransforms.Modifier("tint", 80000);
            case DARKEN -> new ColorTransforms.Modifier("shade", 60000);
            case DARKEN_LESS -> new ColorTransforms.Modifier("shade", 80000);
            case NORM, NONE -> null; // unreachable: filtered by callers
        };
        if (modifier == null) return s;
        String hex = String.format("#%02X%02X%02X", s.red(), s.green(), s.blue());
        ColorTransforms.ResolvedColor rc = ColorTransforms.apply(hex, List.of(modifier));
        SurfacePaint.Solid derived = SurfacePaint.Solid.fromHex(rc.hex());
        // keep the original alpha byte
        return new SurfacePaint.Solid((s.argb() & 0xFF000000) | (derived.argb() & 0x00FFFFFF));
    }

    @Override
    public void render(SlideShape shape, RenderingContext ctx, SlideRenderContext slideCtx) {
        ShapeGeometry geom = shape.getGeometry();
        if (geom == null || geom.getWidth() <= 0 || geom.getHeight() <= 0) return;

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

        // Connectors: draw as lines, not filled shapes
        SlideShape.ShapeType type = shape.getType();
        if (type == SlideShape.ShapeType.CONNECTION) {
            if (line.isVisible()) {
                applyLineStyle(surface, line);
                // A straight connector is stored as a bounding box plus flipH/flipV,
                // which select which diagonal the line runs along. Ignoring the flips
                // rendered every flipped connector mirrored.
                double[] e = connectorEndpoints(bounds.getMinX(), bounds.getMinY(),
                    bounds.getMaxX(), bounds.getMaxY(), geom.isFlipH(), geom.isFlipV());
                surface.strokeLine(e[0], e[1], e[2], e[3]);
                surface.setLineDashes((double[]) null);
            }
            if (rotDeg != 0) surface.restore();
            return;
        }

        GeometryDefinition def = definitionFor(shape, geom);
        GeometryResolver.ResolvedGeometry resolved = GeometryResolver.resolve(
            def, geom.getAdjustValues(), bounds.getWidth(), bounds.getHeight());
        double ox = bounds.getMinX(), oy = bounds.getMinY();
        boolean flip = geom.isFlipH() || geom.isFlipV();

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
                shadowCopy(surface, resolved,
                    shadow.color().withAlpha(fillTarget),
                    shadow.color().withAlpha(strokeTarget), hasFill, line, ox, oy);
            } else {
                // Draw the hard silhouette into a blur layer; the
                // backend composites it back through the gaussian.
                surface.beginBlurLayer(blur);
                shadowCopy(surface, resolved,
                    shadow.color().withAlpha(fillTarget),
                    shadow.color().withAlpha(strokeTarget), hasFill, line, ox, oy);
                surface.endBlurLayer();
            }
            surface.restore();
        }

        // Body pass: paths in definition order, fill then stroke per
        // path -- action buttons and 3D-ish presets (can, cube) layer
        // norm/lighten/darken paths back to front.
        if (flip) {
            surface.save();
            applyFlip(surface, bounds, geom.isFlipH(), geom.isFlipV());
        }
        boolean strokeStyled = false;
        for (GeometryResolver.ResolvedPath p : resolved.paths()) {
            if (hasFill && p.fill() != GeometryPath.FillMode.NONE) {
                surface.setFill(deriveFill(surfaceFill, p.fill()));
                trace(surface, p, ox, oy);
                surface.fillPath();
            }
            if (line.isVisible() && p.stroked()) {
                if (!strokeStyled) {
                    applyLineStyle(surface, line);
                    strokeStyled = true;
                }
                trace(surface, p, ox, oy);
                surface.strokePath();
            }
        }
        if (strokeStyled) {
            surface.setLineDashes((double[]) null);
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
